// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.hypervisor.kvm.storage;

import static com.cloud.utils.NumbersUtil.toHumanReadableSize;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.cloud.agent.properties.AgentProperties;
import com.cloud.agent.properties.AgentPropertiesFileHandler;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.utils.cryptsetup.KeyFile;
import org.apache.cloudstack.utils.qemu.QemuImageOptions;
import org.apache.cloudstack.utils.qemu.QemuImg;
import org.apache.cloudstack.utils.qemu.QemuImg.PhysicalDiskFormat;
import org.apache.cloudstack.utils.qemu.QemuImgException;
import org.apache.cloudstack.utils.qemu.QemuImgFile;
import org.apache.cloudstack.utils.qemu.QemuObject;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.libvirt.Connect;
import org.libvirt.LibvirtException;
import org.libvirt.Secret;
import org.libvirt.StoragePool;
import org.libvirt.StoragePoolInfo.StoragePoolState;
import org.libvirt.StorageVol;

import com.ceph.rados.IoCTX;
import com.ceph.rados.Rados;
import com.ceph.rados.exceptions.ErrorCode;
import com.ceph.rados.exceptions.RadosException;
import com.ceph.rbd.Rbd;
import com.ceph.rbd.RbdException;
import com.ceph.rbd.RbdImage;
import com.ceph.rbd.jna.RbdImageInfo;
import com.ceph.rbd.jna.RbdSnapInfo;
import com.cloud.exception.InternalErrorException;
import com.cloud.hypervisor.kvm.resource.LibvirtConnection;
import com.cloud.hypervisor.kvm.resource.LibvirtSecretDef;
import com.cloud.hypervisor.kvm.resource.LibvirtSecretDef.Usage;
import com.cloud.hypervisor.kvm.resource.LibvirtStoragePoolDef;
import com.cloud.hypervisor.kvm.resource.LibvirtStoragePoolDef.AuthenticationType;
import com.cloud.hypervisor.kvm.resource.LibvirtStoragePoolDef.PoolType;
import com.cloud.hypervisor.kvm.resource.LibvirtStoragePoolXMLParser;
import com.cloud.hypervisor.kvm.resource.LibvirtStorageVolumeDef;
import com.cloud.hypervisor.kvm.resource.LibvirtStorageVolumeDef.VolumeFormat;
import com.cloud.hypervisor.kvm.resource.LibvirtStorageVolumeXMLParser;
import com.cloud.storage.Storage;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.StorageLayer;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;

public class LibvirtStorageAdaptor implements StorageAdaptor {
    protected Logger logger = LogManager.getLogger(getClass());
    private StorageLayer _storageLayer;
    private String _mountPoint = "/mnt";
    private String _manageSnapshotPath;
    private static final ConcurrentHashMap<String, Integer> storagePoolRefCounts = new ConcurrentHashMap<>();

    private String rbdTemplateSnapName = "cloudstack-base-snap";
    private static final int RBD_FEATURE_LAYERING = 1;
    private static final int RBD_FEATURE_EXCLUSIVE_LOCK = 4;
    private static final int RBD_FEATURE_OBJECT_MAP = 8;
    private static final int RBD_FEATURE_FAST_DIFF = 16;
    private static final int RBD_FEATURE_DEEP_FLATTEN = 32;
    public static final int RBD_FEATURES = RBD_FEATURE_LAYERING + RBD_FEATURE_EXCLUSIVE_LOCK + RBD_FEATURE_OBJECT_MAP + RBD_FEATURE_FAST_DIFF + RBD_FEATURE_DEEP_FLATTEN;
    private int rbdOrder = 0; /* Order 0 means 4MB blocks (the default) */

    private static final Set<StoragePoolType> poolTypesThatEnableCreateDiskFromTemplateBacking = new HashSet<>(Arrays.asList(StoragePoolType.NetworkFilesystem,
      StoragePoolType.Filesystem));

    public LibvirtStorageAdaptor(StorageLayer storage) {
        _storageLayer = storage;
        _manageSnapshotPath = Script.findScript("scripts/storage/qcow2/", "managesnapshot.sh");
    }

    @Override
    public boolean createFolder(String uuid, String path) {
        return createFolder(uuid, path, null);
    }

    @Override
    public boolean createFolder(String uuid, String path, String localPath) {
        String mountPoint = _mountPoint + File.separator + uuid;

        if (localPath != null) {
            logger.debug(String.format("Pool [%s] is of type local or shared mount point; therefore, we will use the local path [%s] to create the folder [%s] (if it does not"
                    + " exist).", uuid, localPath, path));

            mountPoint = localPath;
        }

        File f = new File(mountPoint + File.separator + path);
        if (!f.exists()) {
            f.mkdirs();
        }
        return true;
    }

    @Override
    public KVMPhysicalDisk createDiskFromTemplateBacking(KVMPhysicalDisk template, String name, PhysicalDiskFormat format, long size,
                                                         KVMStoragePool destPool, int timeout, byte[] passphrase) {
        String volumeDesc = String.format("volume [%s], with template backing [%s], in pool [%s] (%s), with size [%s] and encryption is %s", name, template.getName(), destPool.getUuid(),
          destPool.getType(), size, passphrase != null && passphrase.length > 0);

        if (!poolTypesThatEnableCreateDiskFromTemplateBacking.contains(destPool.getType())) {
            logger.info(String.format("Skipping creation of %s due to pool type is none of the following types %s.", volumeDesc, poolTypesThatEnableCreateDiskFromTemplateBacking.stream()
              .map(type -> type.toString()).collect(Collectors.joining(", "))));

            return null;
        }

        if (format != PhysicalDiskFormat.QCOW2) {
            logger.info(String.format("Skipping creation of %s due to format [%s] is not [%s].", volumeDesc, format, PhysicalDiskFormat.QCOW2));
            return null;
        }

        logger.info(String.format("Creating %s.", volumeDesc));

        String destPoolLocalPath = destPool.getLocalPath();
        String destPath = String.format("%s%s%s", destPoolLocalPath, destPoolLocalPath.endsWith("/") ? "" : "/", name);

        Map<String, String> options = new HashMap<>();
        List<QemuObject> passphraseObjects = new ArrayList<>();
        try (KeyFile keyFile = new KeyFile(passphrase)) {
            QemuImgFile destFile = new QemuImgFile(destPath, format);
            destFile.setSize(size);
            QemuImgFile backingFile = new QemuImgFile(template.getPath(), template.getFormat());

            if (keyFile.isSet()) {
                passphraseObjects.add(QemuObject.prepareSecretForQemuImg(format, QemuObject.EncryptFormat.LUKS, keyFile.toString(), "sec0", options));
            }
            logger.debug(String.format("Passphrase is staged to keyFile: %s", keyFile.isSet()));

            QemuImg qemu = new QemuImg(timeout);
            qemu.create(destFile, backingFile, options, passphraseObjects);
        } catch (QemuImgException | LibvirtException | IOException e) {
            // why don't we throw an exception here? I guess we fail to find the volume later and that results in a failure returned?
            logger.error(String.format("Failed to create %s in [%s] due to [%s].", volumeDesc, destPath, e.getMessage()), e);
        }

        return null;
    }

    /**
     * Checks if downloaded template is extractable
     * @return true if it should be extracted, false if not
     */
    public static boolean isTemplateExtractable(String templatePath) {
        String type = Script.runSimpleBashScript("file " + templatePath + " | awk -F' ' '{print $2}'");
        return type.equalsIgnoreCase("bzip2") || type.equalsIgnoreCase("gzip") || type.equalsIgnoreCase("zip");
    }

    /**
     * Return extract command to execute given downloaded file
     * @param downloadedTemplateFile
     * @param templateUuid
     */
    public static String getExtractCommandForDownloadedFile(String downloadedTemplateFile, String templateUuid) {
        if (downloadedTemplateFile.endsWith(".zip")) {
            return "unzip -p " + downloadedTemplateFile + " | cat > " + templateUuid;
        } else if (downloadedTemplateFile.endsWith(".bz2")) {
            return "bunzip2 -c " + downloadedTemplateFile + " > " + templateUuid;
        } else if (downloadedTemplateFile.endsWith(".gz")) {
            return "gunzip -c " + downloadedTemplateFile + " > " + templateUuid;
        } else {
            throw new CloudRuntimeException("Unable to extract template " + downloadedTemplateFile);
        }
    }

    /**
     * Extract downloaded template into installPath, remove compressed file
     */
    public static void extractDownloadedTemplate(String downloadedTemplateFile, KVMStoragePool destPool, String destinationFile) {
        String extractCommand = getExtractCommandForDownloadedFile(downloadedTemplateFile, destinationFile);
        Script.runSimpleBashScript(extractCommand);
        Script.runSimpleBashScript("rm -f " + downloadedTemplateFile);
    }

    @Override
    public KVMPhysicalDisk createTemplateFromDirectDownloadFile(String templateFilePath, String destTemplatePath, KVMStoragePool destPool, Storage.ImageFormat format, int timeout) {
        File sourceFile = new File(templateFilePath);
        if (!sourceFile.exists()) {
            throw new CloudRuntimeException("Direct download template file " + sourceFile + " does not exist on this host");
        }
        String templateUuid = UUID.randomUUID().toString();
        if (Storage.ImageFormat.ISO.equals(format)) {
            templateUuid += ".iso";
        }
        String destinationFile = destPool.getLocalPath() + File.separator + templateUuid;

        if (destPool.getType() == StoragePoolType.NetworkFilesystem || destPool.getType() == StoragePoolType.Filesystem
            || destPool.getType() == StoragePoolType.SharedMountPoint) {
            if (!Storage.ImageFormat.ISO.equals(format) && isTemplateExtractable(templateFilePath)) {
                extractDownloadedTemplate(templateFilePath, destPool, destinationFile);
            } else {
                Script.runSimpleBashScript("mv " + templateFilePath + " " + destinationFile);
            }
        } else if (destPool.getType() == StoragePoolType.RBD) {
            String temporaryExtractFilePath = sourceFile.getParent() + File.separator + templateUuid;
            extractDownloadedTemplate(templateFilePath, destPool, temporaryExtractFilePath);
            createTemplateOnRBDFromDirectDownloadFile(temporaryExtractFilePath, templateUuid, destPool, timeout);
        }
        return destPool.getPhysicalDisk(templateUuid);
    }

    private void createTemplateOnRBDFromDirectDownloadFile(String srcTemplateFilePath, String templateUuid, KVMStoragePool destPool, int timeout) {
        try {
            QemuImg.PhysicalDiskFormat srcFileFormat = QemuImg.PhysicalDiskFormat.QCOW2;
            QemuImgFile srcFile = new QemuImgFile(srcTemplateFilePath, srcFileFormat);
            QemuImg qemu = new QemuImg(timeout);
            Map<String, String> info = qemu.info(srcFile);
            Long virtualSize = Long.parseLong(info.get(QemuImg.VIRTUAL_SIZE));
            KVMPhysicalDisk destDisk = new KVMPhysicalDisk(destPool.getSourceDir() + "/" + templateUuid, templateUuid, destPool);
            destDisk.setFormat(PhysicalDiskFormat.RAW);
            destDisk.setSize(virtualSize);
            destDisk.setVirtualSize(virtualSize);
            QemuImgFile destFile = new QemuImgFile(KVMPhysicalDisk.RBDStringBuilder(destPool, destDisk.getPath()));
            destFile.setFormat(PhysicalDiskFormat.RAW);
            qemu.convert(srcFile, destFile);
        } catch (LibvirtException | QemuImgException e) {
            String err = String.format("Error creating template from direct download file on pool %s: %s", destPool.getUuid(), e.getMessage());
            logger.error(err, e);
            throw new CloudRuntimeException(err, e);
        }
    }

    public StorageVol getVolume(StoragePool pool, String volName) {
        StorageVol vol = null;

        try {
            vol = pool.storageVolLookupByName(volName);
        } catch (LibvirtException e) {
            logger.debug("Could not find volume " + volName + ": " + e.getMessage());
        }

        /**
         * The volume was not found in the storage pool
         * This can happen when a volume has just been created on a different host and
         * since then the libvirt storage pool has not been refreshed.
         */
        if (vol == null) {
            try {
                logger.debug("Refreshing storage pool " + pool.getName());
                refreshPool(pool);
            } catch (LibvirtException e) {
                logger.debug("Failed to refresh storage pool: " + e.getMessage());
            }

            try {
                vol = pool.storageVolLookupByName(volName);
                logger.debug("Found volume " + volName + " in storage pool " + pool.getName() + " after refreshing the pool");
            } catch (LibvirtException e) {
                throw new CloudRuntimeException("Could not find volume " + volName + ": " + e.getMessage());
            }
        }

        return vol;
    }

    public StorageVol createVolume(Connect conn, StoragePool pool, String uuid, long size, VolumeFormat format) throws LibvirtException {
        LibvirtStorageVolumeDef volDef = new LibvirtStorageVolumeDef(UUID.randomUUID().toString(), size, format, null, null);
        logger.debug(volDef.toString());

        return pool.storageVolCreateXML(volDef.toString(), 0);
    }

    public void storagePoolRefresh(StoragePool pool) {
        try {
            synchronized (getStoragePool(pool.getUUIDString())) {
                refreshPool(pool);
            }
        } catch (LibvirtException e) {
            logger.debug("refresh storage pool failed: " + e.toString());
        }
    }

    private void checkNetfsStoragePoolMounted(String uuid) {
        String targetPath = _mountPoint + File.separator + uuid;
        int mountpointResult = Script.runSimpleBashScriptForExitValue("mountpoint -q " + targetPath);
        if (mountpointResult != 0) {
            String errMsg = String.format("libvirt failed to mount storage pool %s at %s", uuid, targetPath);
            logger.error(errMsg);
            throw new CloudRuntimeException(errMsg);
        }
    }

    private StoragePool createNetfsStoragePool(PoolType fsType, Connect conn, String uuid, String host, String path, List<String> nfsMountOpts) throws LibvirtException {
        String targetPath = _mountPoint + File.separator + uuid;
        LibvirtStoragePoolDef spd = new LibvirtStoragePoolDef(fsType, uuid, uuid, host, path, targetPath, nfsMountOpts);
        _storageLayer.mkdir(targetPath);
        StoragePool sp = null;
        try {
            logger.debug(spd.toString());
            // check whether the pool is already mounted
            int mountpointResult = Script.runSimpleBashScriptForExitValue("mountpoint -q " + targetPath);
            // if the pool is mounted, try to unmount it
            if(mountpointResult == 0) {
                logger.info("Attempting to unmount old mount at " + targetPath);
                String result = Script.runSimpleBashScript("umount -l " + targetPath);
                if (result == null) {
                    logger.info("Succeeded in unmounting " + targetPath);
                } else {
                    logger.error("Failed in unmounting storage");
                }
            }

            sp = conn.storagePoolCreateXML(spd.toString(), 0);
            return sp;
        } catch (LibvirtException e) {
            logger.error(e.toString());
            throw e;
        }
    }

    private StoragePool createSharedStoragePool(Connect conn, String uuid, String host, String path) {
        String mountPoint = path;
        if (!_storageLayer.exists(mountPoint)) {
            logger.error(mountPoint + " does not exists. Check local.storage.path in agent.properties.");
            return null;
        }
        LibvirtStoragePoolDef spd = new LibvirtStoragePoolDef(PoolType.DIR, uuid, uuid, host, path, path);
        StoragePool sp = null;
        try {
            logger.debug(spd.toString());
            sp = conn.storagePoolCreateXML(spd.toString(), 0);
            return sp;
        } catch (LibvirtException e) {
            logger.error(e.toString());
            if (sp != null) {
                try {
                    if (sp.isPersistent() == 1) {
                        sp.destroy();
                        sp.undefine();
                    } else {
                        sp.destroy();
                    }
                    sp.free();
                } catch (LibvirtException l) {
                    logger.debug("Failed to define shared mount point storage pool with: " + l.toString());
                }
            }
            return null;
        }
    }

    private StoragePool createCLVMStoragePool(Connect conn, String uuid, String host, String path) {

        String volgroupPath = "/dev/" + path;
        String volgroupName = path;
        volgroupName = volgroupName.replaceFirst("/", "");

        LibvirtStoragePoolDef spd = new LibvirtStoragePoolDef(PoolType.LOGICAL, volgroupName, uuid, host, volgroupPath, volgroupPath);
        StoragePool sp = null;
        try {
            logger.debug(spd.toString());
            sp = conn.storagePoolCreateXML(spd.toString(), 0);
            return sp;
        } catch (LibvirtException e) {
            logger.error(e.toString());
            if (sp != null) {
                try {
                    if (sp.isPersistent() == 1) {
                        sp.destroy();
                        sp.undefine();
                    } else {
                        sp.destroy();
                    }
                    sp.free();
                } catch (LibvirtException l) {
                    logger.debug("Failed to define clvm storage pool with: " + l.toString());
                }
            }
            return null;
        }

    }

    private List<String> getNFSMountOptsFromDetails(StoragePoolType type, Map<String, String> details) {
        List<String> nfsMountOpts = null;
        if (!type.equals(StoragePoolType.NetworkFilesystem) || details == null) {
            return nfsMountOpts;
        }
        if (details.containsKey(ApiConstants.NFS_MOUNT_OPTIONS)) {
            nfsMountOpts = Arrays.asList(details.get(ApiConstants.NFS_MOUNT_OPTIONS).replaceAll("\\s", "").split(","));
        }
        return nfsMountOpts;
    }

    private boolean destroyStoragePoolOnNFSMountOptionsChange(StoragePool sp, Connect conn, List<String> nfsMountOpts) {
        try {
            LibvirtStoragePoolDef poolDef = getStoragePoolDef(conn, sp);
            Set poolNfsMountOpts = poolDef.getNfsMountOpts();
            boolean mountOptsDiffer = false;
            if (poolNfsMountOpts.size() != nfsMountOpts.size()) {
                mountOptsDiffer = true;
            } else {
                for (String nfsMountOpt : nfsMountOpts) {
                    if (!poolNfsMountOpts.contains(nfsMountOpt)) {
                        mountOptsDiffer = true;
                        break;
                    }
                }
            }
            if (mountOptsDiffer) {
                sp.destroy();
                return true;
            }
        } catch (LibvirtException e) {
            logger.error("Failure in destroying the pre-existing storage pool for changing the NFS mount options" + e);
        }
        return false;
    }

    private StoragePool createRBDStoragePool(Connect conn, String uuid, String host, int port, String userInfo, String path) {

        LibvirtStoragePoolDef spd;
        StoragePool sp = null;
        Secret s = null;

        String[] userInfoTemp = userInfo.split(":");
        if (userInfoTemp.length == 2) {
            LibvirtSecretDef sd = new LibvirtSecretDef(Usage.CEPH, uuid);

            sd.setCephName(userInfoTemp[0] + "@" + host + ":" + port + "/" + path);

            try {
                logger.debug(sd.toString());
                s = conn.secretDefineXML(sd.toString());
                s.setValue(Base64.decodeBase64(userInfoTemp[1]));
            } catch (LibvirtException e) {
                logger.error("Failed to define the libvirt secret: " + e.toString());
                if (s != null) {
                    try {
                        s.undefine();
                        s.free();
                    } catch (LibvirtException l) {
                        logger.error("Failed to undefine the libvirt secret: " + l.toString());
                    }
                }
                return null;
            }
            spd = new LibvirtStoragePoolDef(PoolType.RBD, uuid, uuid, host, port, path, userInfoTemp[0], AuthenticationType.CEPH, uuid);
        } else {
            spd = new LibvirtStoragePoolDef(PoolType.RBD, uuid, uuid, host, port, path, "");
        }

        try {
            logger.debug(spd.toString());
            sp = conn.storagePoolCreateXML(spd.toString(), 0);
            return sp;
        } catch (LibvirtException e) {
            logger.error("Failed to create RBD storage pool: " + e.toString());
            if (sp != null) {
                try {
                    if (sp.isPersistent() == 1) {
                        sp.destroy();
                        sp.undefine();
                    } else {
                        sp.destroy();
                    }
                    sp.free();
                } catch (LibvirtException l) {
                    logger.error("Failed to undefine RBD storage pool: " + l.toString());
                }
            }

            if (s != null) {
                try {
                    logger.error("Failed to create the RBD storage pool, cleaning up the libvirt secret");
                    s.undefine();
                    s.free();
                } catch (LibvirtException se) {
                    logger.error("Failed to remove the libvirt secret: " + se.toString());
                }
            }

            return null;
        }
    }

    public StorageVol copyVolume(StoragePool destPool, LibvirtStorageVolumeDef destVol, StorageVol srcVol, int timeout) throws LibvirtException {
        StorageVol vol = destPool.storageVolCreateXML(destVol.toString(), 0);
        String srcPath = srcVol.getKey();
        String destPath = vol.getKey();
        Script.runSimpleBashScript("cp " + srcPath + " " + destPath, timeout);
        return vol;
    }

    public boolean copyVolume(String srcPath, String destPath, String volumeName, int timeout) throws InternalErrorException {
        _storageLayer.mkdirs(destPath);
        if (!_storageLayer.exists(srcPath)) {
            throw new InternalErrorException("volume:" + srcPath + " is not exits");
        }
        String result = Script.runSimpleBashScript("cp " + srcPath + " " + destPath + File.separator + volumeName, timeout);
        return result == null;
    }

    public LibvirtStoragePoolDef getStoragePoolDef(Connect conn, StoragePool pool) throws LibvirtException {
        String poolDefXML = pool.getXMLDesc(0);
        LibvirtStoragePoolXMLParser parser = new LibvirtStoragePoolXMLParser();
        return parser.parseStoragePoolXML(poolDefXML);
    }

    public LibvirtStorageVolumeDef getStorageVolumeDef(Connect conn, StorageVol vol) throws LibvirtException {
        String volDefXML = vol.getXMLDesc(0);
        LibvirtStorageVolumeXMLParser parser = new LibvirtStorageVolumeXMLParser();
        return parser.parseStorageVolumeXML(volDefXML);
    }

    @Override
    public StoragePoolType getStoragePoolType() {
        // This is mapped manually in KVMStoragePoolManager
        return  null;
    }

    @Override
    public KVMStoragePool getStoragePool(String uuid) {
        return this.getStoragePool(uuid, false);
    }

    protected void updateLocalPoolIops(LibvirtStoragePool pool) {
        if (!StoragePoolType.Filesystem.equals(pool.getType()) || StringUtils.isBlank(pool.getLocalPath())) {
            return;
        }
        logger.trace("Updating used IOPS for pool: {}", pool.getName());

        // Run script to get data
        List<String[]> commands = new ArrayList<>();
        commands.add(new String[]{
                Script.getExecutableAbsolutePath("bash"),
                "-c",
                String.format(
                        "%s %s | %s 'NR==2 {print $1}'",
                        Script.getExecutableAbsolutePath("df"),
                        pool.getLocalPath(),
                        Script.getExecutableAbsolutePath("awk")
                )
        });
        String result = Script.executePipedCommands(commands, 1000).second();
        if (StringUtils.isBlank(result)) {
            return;
        }
        result = result.trim();
        commands.add(new String[]{
                Script.getExecutableAbsolutePath("bash"),
                "-c",
                String.format(
                        "%s -z %s 1 2 | %s 'NR==7 {print $2}'",
                        Script.getExecutableAbsolutePath("iostat"),
                        result,
                        Script.getExecutableAbsolutePath("awk")
                )
        });
        result = Script.executePipedCommands(commands, 10000).second();
        logger.trace("Pool used IOPS result: {}", result);
        if (StringUtils.isBlank(result)) {
            return;
        }
        try {
            double doubleValue = Double.parseDouble(result);
            pool.setUsedIops((long) doubleValue);
            logger.debug("Updated used IOPS: {} for pool: {}", pool.getUsedIops(), pool.getName());
        } catch (NumberFormatException e) {
            logger.warn(String.format("Unable to parse retrieved used IOPS: %s for pool: %s", result,
                    pool.getName()));
        }
    }

    @Override
    public KVMStoragePool getStoragePool(String uuid, boolean refreshInfo) {
        logger.info("Trying to fetch storage pool " + uuid + " from libvirt");
        StoragePool storage = null;
        try {
            Connect conn = LibvirtConnection.getConnection();
            storage = conn.storagePoolLookupByUUIDString(uuid);

            if (storage.getInfo().state != StoragePoolState.VIR_STORAGE_POOL_RUNNING) {
                logger.warn("Storage pool " + uuid + " is not in running state. Attempting to start it.");
                storage.create(0);
            }
            LibvirtStoragePoolDef spd = getStoragePoolDef(conn, storage);
            if (spd == null) {
                throw new CloudRuntimeException("Unable to parse the storage pool definition for storage pool " + uuid);
            }
            StoragePoolType type = null;
            if (spd.getPoolType() == LibvirtStoragePoolDef.PoolType.NETFS) {
                type = StoragePoolType.NetworkFilesystem;
            } else if (spd.getPoolType() == LibvirtStoragePoolDef.PoolType.DIR) {
                type = StoragePoolType.Filesystem;
            } else if (spd.getPoolType() == LibvirtStoragePoolDef.PoolType.RBD) {
                type = StoragePoolType.RBD;
            } else if (spd.getPoolType() == LibvirtStoragePoolDef.PoolType.LOGICAL) {
                type = StoragePoolType.CLVM;
            } else if (spd.getPoolType() == LibvirtStoragePoolDef.PoolType.GLUSTERFS) {
                type = StoragePoolType.Gluster;
            } else if (spd.getPoolType() == LibvirtStoragePoolDef.PoolType.POWERFLEX) {
                type = StoragePoolType.PowerFlex;
            }

            LibvirtStoragePool pool = new LibvirtStoragePool(uuid, storage.getName(), type, this, storage);

            if (pool.getType() != StoragePoolType.RBD && pool.getType() != StoragePoolType.PowerFlex)
                pool.setLocalPath(spd.getTargetPath());
            else
                pool.setLocalPath("");

            if (pool.getType() == StoragePoolType.RBD
                    || pool.getType() == StoragePoolType.Gluster) {
                pool.setSourceHost(spd.getSourceHost());
                pool.setSourcePort(spd.getSourcePort());
                pool.setSourceDir(spd.getSourceDir());
                String authUsername = spd.getAuthUserName();
                if (authUsername != null) {
                    Secret secret = conn.secretLookupByUUIDString(spd.getSecretUUID());
                    String secretValue = new String(Base64.encodeBase64(secret.getByteValue()), Charset.defaultCharset());
                    pool.setAuthUsername(authUsername);
                    pool.setAuthSecret(secretValue);
                }
            }

            /**
             * On large (RBD) storage pools it can take up to a couple of minutes
             * for libvirt to refresh the pool.
             *
             * Refreshing a storage pool means that libvirt will have to iterate the whole pool
             * and fetch information of each volume in there
             *
             * It is not always required to refresh a pool. So we can control if we want to or not
             *
             * By default only the getStorageStats call in the LibvirtComputingResource will ask to
             * refresh the pool
             */
            if (refreshInfo) {
                logger.info("Asking libvirt to refresh storage pool " + uuid);
                pool.refresh();
            }
            pool.setCapacity(storage.getInfo().capacity);
            pool.setUsed(storage.getInfo().allocation);
            updateLocalPoolIops(pool);
            pool.setAvailable(storage.getInfo().available);

            logger.debug("Successfully refreshed pool " + uuid +
                           " Capacity: " + toHumanReadableSize(storage.getInfo().capacity) +
                           " Used: " + toHumanReadableSize(storage.getInfo().allocation) +
                           " Available: " + toHumanReadableSize(storage.getInfo().available));

            return pool;
        } catch (LibvirtException e) {
            logger.debug("Could not find storage pool " + uuid + " in libvirt");
            throw new CloudRuntimeException(e.toString(), e);
        }
    }

    @Override
    public KVMPhysicalDisk getPhysicalDisk(String volumeUuid, KVMStoragePool pool) {
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool)pool;

        try {
            StorageVol vol = getVolume(libvirtPool.getPool(), volumeUuid);
            KVMPhysicalDisk disk;
            LibvirtStorageVolumeDef voldef = getStorageVolumeDef(libvirtPool.getPool().getConnect(), vol);
            disk = new KVMPhysicalDisk(vol.getPath(), vol.getName(), pool);
            disk.setSize(vol.getInfo().allocation);
            disk.setVirtualSize(vol.getInfo().capacity);

            /**
             * libvirt returns format = 'unknow', so we have to force
             * the format to RAW for RBD storage volumes
             */
            if (pool.getType() == StoragePoolType.RBD) {
                disk.setFormat(PhysicalDiskFormat.RAW);
            } else if (voldef.getFormat() == null) {
                File diskDir = new File(disk.getPath());
                if (diskDir.exists() && diskDir.isDirectory()) {
                    disk.setFormat(PhysicalDiskFormat.DIR);
                } else if (volumeUuid.endsWith("tar") || volumeUuid.endsWith(("TAR"))) {
                    disk.setFormat(PhysicalDiskFormat.TAR);
                } else if (volumeUuid.endsWith("raw") || volumeUuid.endsWith(("RAW"))) {
                    disk.setFormat(PhysicalDiskFormat.RAW);
                } else {
                    disk.setFormat(pool.getDefaultFormat());
                }
            } else if (voldef.getFormat() == LibvirtStorageVolumeDef.VolumeFormat.QCOW2) {
                disk.setFormat(PhysicalDiskFormat.QCOW2);
            } else if (voldef.getFormat() == LibvirtStorageVolumeDef.VolumeFormat.RAW) {
                disk.setFormat(PhysicalDiskFormat.RAW);
            }
            return disk;
        } catch (LibvirtException e) {
            logger.debug("Failed to get physical disk:", e);
            throw new CloudRuntimeException(e.toString());
        }
    }

    /**
     * adjust refcount
     */
    private int adjustStoragePoolRefCount(String uuid, int adjustment) {
        final String mutexKey = storagePoolRefCounts.keySet().stream()
                .filter(k -> k.equals(uuid))
                .findFirst()
                .orElse(uuid);
        synchronized (mutexKey) {
            // some access on the storagePoolRefCounts.key(mutexKey) element
            int refCount = storagePoolRefCounts.computeIfAbsent(mutexKey, k -> 0);
            refCount += adjustment;
            if (refCount < 1) {
                storagePoolRefCounts.remove(mutexKey);
            } else {
                storagePoolRefCounts.put(mutexKey, refCount);
            }
            return refCount;
        }
    }
    /**
     * Thread-safe increment storage pool usage refcount
     * @param uuid UUID of the storage pool to increment the count
     */
    private void incStoragePoolRefCount(String uuid) {
        adjustStoragePoolRefCount(uuid, 1);
    }
    /**
     * Thread-safe decrement storage pool usage refcount for the given uuid and return if storage pool still in use.
     * @param uuid UUID of the storage pool to decrement the count
     * @return true if the storage pool is still used, else false.
     */
    private boolean decStoragePoolRefCount(String uuid) {
        return adjustStoragePoolRefCount(uuid, -1) > 0;
    }

    @Override
    public KVMStoragePool createStoragePool(String name, String host, int port, String path, String userInfo, StoragePoolType type, Map<String, String> details, boolean isPrimaryStorage) {
        logger.info("Attempting to create storage pool " + name + " (" + type.toString() + ") in libvirt");

        StoragePool sp = null;
        Connect conn = null;
        try {
            conn = LibvirtConnection.getConnection();
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }

        try {
            sp = conn.storagePoolLookupByUUIDString(name);
            if (sp != null && sp.isActive() == 0) {
                sp.undefine();
                sp = null;
                logger.info("Found existing defined storage pool " + name + ". It wasn't running, so we undefined it.");
            }
            if (sp != null) {
                logger.info("Found existing defined storage pool " + name + ", using it.");
            }
        } catch (LibvirtException e) {
            sp = null;
            logger.warn("Storage pool " + name + " was not found running in libvirt. Need to create it.");
        }

        // libvirt strips trailing slashes off of path, we will too in order to match
        // existing paths
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (sp == null) {
            // see if any existing pool by another name is using our storage path.
            // if anyone is, undefine the pool so we can define it as requested.
            // This should be safe since a pool in use can't be removed, and no
            // volumes are affected by unregistering the pool with libvirt.
            logger.info("Didn't find an existing storage pool " + name + " by UUID, checking for pools with duplicate paths");

            try {
                String[] poolnames = conn.listStoragePools();
                for (String poolname : poolnames) {
                    logger.debug("Checking path of existing pool " + poolname + " against pool we want to create");
                    StoragePool p = conn.storagePoolLookupByName(poolname);
                    LibvirtStoragePoolDef pdef = getStoragePoolDef(conn, p);
                    if (pdef == null) {
                        throw new CloudRuntimeException("Unable to parse the storage pool definition for storage pool " + poolname);
                    }

                    String targetPath = pdef.getTargetPath();
                    if (targetPath != null && targetPath.equals(path)) {
                        logger.debug("Storage pool utilizing path '" + path + "' already exists as pool " + poolname +
                                ", undefining so we can re-define with correct name " + name);
                        if (p.isPersistent() == 1) {
                            p.destroy();
                            p.undefine();
                        } else {
                            p.destroy();
                        }
                    }
                }
            } catch (LibvirtException e) {
                logger.error("Failure in attempting to see if an existing storage pool might be using the path of the pool to be created:" + e);
            }
        }

        List<String> nfsMountOpts = getNFSMountOptsFromDetails(type, details);
        if (sp != null && CollectionUtils.isNotEmpty(nfsMountOpts) &&
            destroyStoragePoolOnNFSMountOptionsChange(sp, conn, nfsMountOpts)) {
            sp = null;
        }

        if (sp == null) {

            logger.debug("Attempting to create storage pool " + name);

            if (type == StoragePoolType.NetworkFilesystem) {
                try {
                    sp = createNetfsStoragePool(PoolType.NETFS, conn, name, host, path, nfsMountOpts);
                } catch (LibvirtException e) {
                    logger.error("Failed to create netfs mount: " + host + ":" + path , e);
                    logger.error(e.getStackTrace());
                    throw new CloudRuntimeException(e.toString());
                }
            } else if (type == StoragePoolType.Gluster) {
                try {
                    sp = createNetfsStoragePool(PoolType.GLUSTERFS, conn, name, host, path, null);
                } catch (LibvirtException e) {
                    logger.error("Failed to create glusterfs mount: " + host + ":" + path , e);
                    logger.error(e.getStackTrace());
                    throw new CloudRuntimeException(e.toString());
                }
            } else if (type == StoragePoolType.SharedMountPoint || type == StoragePoolType.Filesystem) {
                sp = createSharedStoragePool(conn, name, host, path);
            } else if (type == StoragePoolType.RBD) {
                sp = createRBDStoragePool(conn, name, host, port, userInfo, path);
            } else if (type == StoragePoolType.CLVM) {
                sp = createCLVMStoragePool(conn, name, host, path);
            }
        }

        if (sp == null) {
            throw new CloudRuntimeException("Failed to create storage pool: " + name);
        }

        try {
            if (!isPrimaryStorage) {
                // only ref count storage pools for secondary storage, as primary storage is assumed
                // to be always mounted, as long the primary storage isn't fully deleted.
                incStoragePoolRefCount(name);
            }

            if (sp.isActive() == 0) {
                logger.debug("Attempting to activate pool " + name);
                sp.create(0);
            }

            if (type == StoragePoolType.NetworkFilesystem) {
                checkNetfsStoragePoolMounted(name);
            }

            return getStoragePool(name);
        } catch (LibvirtException e) {
            decStoragePoolRefCount(name);
            String error = e.toString();
            if (error.contains("Storage source conflict")) {
                throw new CloudRuntimeException("A pool matching this location already exists in libvirt, " +
                        " but has a different UUID/Name. Cannot create new pool without first " + " removing it. Check for inactive pools via 'virsh pool-list --all'. " +
                        error);
            } else {
                throw new CloudRuntimeException(error);
            }
        }
    }

    private boolean destroyStoragePool(Connect conn, String uuid) throws LibvirtException {
        StoragePool sp;
        try {
            sp = conn.storagePoolLookupByUUIDString(uuid);
        } catch (LibvirtException exc) {
            logger.warn("Storage pool " + uuid + " doesn't exist in libvirt. Assuming it is already removed");
            logger.warn(exc.getStackTrace());
            return true;
        }

        if (sp != null) {
            if (sp.isPersistent() == 1) {
                sp.destroy();
                sp.undefine();
            } else {
                sp.destroy();
            }
            sp.free();

            return true;
        } else {
            logger.warn("Storage pool " + uuid + " doesn't exist in libvirt. Assuming it is already removed");
            return false;
        }
    }

    private boolean destroyStoragePoolHandleException(Connect conn, String uuid)
    {
        try {
            return destroyStoragePool(conn, uuid);
        } catch (LibvirtException e) {
            logger.error(String.format("Failed to destroy libvirt pool %s: %s", uuid, e));
        }
        return false;
    }

    @Override
    public boolean deleteStoragePool(String uuid) {
        logger.info("Attempting to remove storage pool " + uuid + " from libvirt");

        // decrement and check if storage pool still in use
        if (decStoragePoolRefCount(uuid)) {
            logger.info(String.format("deleteStoragePool: Storage pool %s still in use", uuid));
            return true;
        }

        Connect conn = null;
        try {
            conn = LibvirtConnection.getConnection();
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }

        Secret s = null;

        /*
         * Some storage pools, like RBD also have 'secret' information stored in libvirt
         * Destroy them if they exist
         */
        try {
            s = conn.secretLookupByUUIDString(uuid);
        } catch (LibvirtException e) {
            logger.info("Storage pool " + uuid + " has no corresponding secret. Not removing any secret.");
        }

        try {
            destroyStoragePool(conn, uuid);
            if (s != null) {
                s.undefine();
                s.free();
            }

            logger.info("Storage pool " + uuid + " was successfully removed from libvirt.");

            return true;
        } catch (LibvirtException e) {
            // handle ebusy error when pool is quickly destroyed
            if (e.toString().contains("exit status 16")) {
                String targetPath = _mountPoint + File.separator + uuid;
                    logger.error("deleteStoragePool removed pool from libvirt, but libvirt had trouble unmounting the pool. Trying umount location " + targetPath +
                        " again in a few seconds");
                String result = Script.runSimpleBashScript("sleep 5 && umount " + targetPath);
                if (result == null) {
                    logger.info("Succeeded in unmounting " + targetPath);
                    destroyStoragePoolHandleException(conn, uuid);
                    return true;
                }
                logger.error("Failed to unmount " + targetPath);
            }
            throw new CloudRuntimeException(e.toString(), e);
        }
    }

    /**
     * Creates a physical disk depending on the {@link StoragePoolType}:
     * <ul>
     *     <li>
     *         <b>{@link StoragePoolType#RBD}</b>
     *         <ul>
     *             <li>
     *                 If it is an erasure code pool, utilizes QemuImg to create the physical disk through the method
     *             {@link LibvirtStorageAdaptor#createPhysicalDiskByQemuImg(String, KVMStoragePool, PhysicalDiskFormat, Storage.ProvisioningType, long, byte[])}
     *             </li>
     *             <li>
     *                 Otherwise, utilize Libvirt to create the physical disk through the method
     *                 {@link LibvirtStorageAdaptor#createPhysicalDiskByLibVirt(String, KVMStoragePool, PhysicalDiskFormat, Storage.ProvisioningType, long)}
     *             </li>
     *         </ul>
     *     </li>
     *     <li>
     *         {@link StoragePoolType#NetworkFilesystem} and {@link StoragePoolType#Filesystem}
     *         <ul>
     *             <li>
     *                 If the format is {@link PhysicalDiskFormat#QCOW2} or {@link PhysicalDiskFormat#RAW}, utilizes QemuImg to create the physical disk through the method
     *             {@link LibvirtStorageAdaptor#createPhysicalDiskByQemuImg(String, KVMStoragePool, PhysicalDiskFormat, Storage.ProvisioningType, long, byte[])}
     *             </li>
     *             <li>
     *                 If the format is {@link PhysicalDiskFormat#DIR} or {@link PhysicalDiskFormat#TAR}, utilize Libvirt to create the physical disk through the method
     *                 {@link LibvirtStorageAdaptor#createPhysicalDiskByLibVirt(String, KVMStoragePool, PhysicalDiskFormat, Storage.ProvisioningType, long)}
     *             </li>
     *         </ul>
     *     </li>
     *     <li>
     *         For the rest of the {@link StoragePoolType} types, utilizes the Libvirt method
     *         {@link LibvirtStorageAdaptor#createPhysicalDiskByLibVirt(String, KVMStoragePool, PhysicalDiskFormat, Storage.ProvisioningType, long)}
     *     </li>
     * </ul>
     */
    @Override
    public KVMPhysicalDisk createPhysicalDisk(String name, KVMStoragePool pool,
            PhysicalDiskFormat format, Storage.ProvisioningType provisioningType, long size, byte[] passphrase) {

        logger.info("Attempting to create volume {} ({}) in pool {} with size {}", name, pool.getType().toString(), pool.getUuid(), toHumanReadableSize(size));

        StoragePoolType poolType = pool.getType();
        if (StoragePoolType.RBD.equals(poolType)) {
            Map<String, String> details = pool.getDetails();
            String dataPool = (details == null) ? null : details.get(KVMPhysicalDisk.RBD_DEFAULT_DATA_POOL);

            return (dataPool == null) ?  createPhysicalDiskByLibVirt(name, pool, PhysicalDiskFormat.RAW, provisioningType, size) :
                    createPhysicalDiskByQemuImg(name, pool, PhysicalDiskFormat.RAW, provisioningType, size, passphrase);
        } else if (StoragePoolType.NetworkFilesystem.equals(poolType) || StoragePoolType.Filesystem.equals(poolType)) {
            switch (format) {
                case QCOW2:
                case RAW:
                    return createPhysicalDiskByQemuImg(name, pool, format, provisioningType, size, passphrase);
                case DIR:
                case TAR:
                    return createPhysicalDiskByLibVirt(name, pool, format, provisioningType, size);
                default:
                    throw new CloudRuntimeException("Unexpected disk format is specified.");
            }
        } else {
            return createPhysicalDiskByLibVirt(name, pool, format, provisioningType, size);
        }
    }

    private KVMPhysicalDisk createPhysicalDiskByLibVirt(String name, KVMStoragePool pool,
            PhysicalDiskFormat format, Storage.ProvisioningType provisioningType, long size) {
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool) pool;
        StoragePool virtPool = libvirtPool.getPool();
        LibvirtStorageVolumeDef.VolumeFormat libvirtformat = LibvirtStorageVolumeDef.VolumeFormat.getFormat(format);

        String volPath = null;
        String volName = null;
        long volAllocation = 0;
        long volCapacity = 0;

        LibvirtStorageVolumeDef volDef = new LibvirtStorageVolumeDef(name,
                size, libvirtformat, null, null);
        logger.debug(volDef.toString());
        try {
            StorageVol vol = virtPool.storageVolCreateXML(volDef.toString(), 0);
            volPath = vol.getPath();
            volName = vol.getName();
            volAllocation = vol.getInfo().allocation;
            volCapacity = vol.getInfo().capacity;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }

        KVMPhysicalDisk disk = new KVMPhysicalDisk(volPath, volName, pool);
        disk.setFormat(format);
        disk.setSize(volAllocation);
        disk.setVirtualSize(volCapacity);
        return disk;
    }


    private KVMPhysicalDisk createPhysicalDiskByQemuImg(String name, KVMStoragePool pool, PhysicalDiskFormat format, Storage.ProvisioningType provisioningType, long size,
                                                        byte[] passphrase) {
        String volPath;
        String volName = name;
        long virtualSize = 0;
        long actualSize = 0;
        QemuObject.EncryptFormat encryptFormat = null;
        List<QemuObject> passphraseObjects = new ArrayList<>();
        final int timeout = 0;
        QemuImgFile destFile;

        if (StoragePoolType.RBD.equals(pool.getType())) {
            volPath = pool.getSourceDir() + File.separator + name;
            destFile = new QemuImgFile(KVMPhysicalDisk.RBDStringBuilder(pool, volPath));
        } else {
            volPath = pool.getLocalPath() + File.separator + name;
            destFile = new QemuImgFile(volPath);
        }

        destFile.setFormat(format);
        destFile.setSize(size);
        Map<String, String> options = new HashMap<String, String>();
        if (List.of(StoragePoolType.NetworkFilesystem, StoragePoolType.Filesystem).contains(pool.getType())) {
            options.put("preallocation", QemuImg.PreallocationType.getPreallocationType(provisioningType).toString());
        }

        try (KeyFile keyFile = new KeyFile(passphrase)) {
            QemuImg qemu = new QemuImg(timeout);
            if (keyFile.isSet()) {
                passphraseObjects.add(QemuObject.prepareSecretForQemuImg(format, QemuObject.EncryptFormat.LUKS, keyFile.toString(), "sec0", options));

                // make room for encryption header on raw format, use LUKS
                if (format == PhysicalDiskFormat.RAW) {
                    destFile.setSize(destFile.getSize() - (16<<20));
                    destFile.setFormat(PhysicalDiskFormat.LUKS);
                }

                encryptFormat = QemuObject.EncryptFormat.LUKS;
            }
            qemu.create(destFile, null, options, passphraseObjects);
            Map<String, String> info = qemu.info(destFile);
            virtualSize = Long.parseLong(info.get(QemuImg.VIRTUAL_SIZE));
            actualSize = new File(destFile.getFileName()).length();
        } catch (QemuImgException | LibvirtException | IOException e) {
            throw new CloudRuntimeException(String.format("Failed to create %s due to a failed execution of qemu-img", volPath), e);
        }

        KVMPhysicalDisk disk = new KVMPhysicalDisk(volPath, volName, pool);
        disk.setFormat(format);
        disk.setSize(actualSize);
        disk.setVirtualSize(virtualSize);
        disk.setQemuEncryptFormat(encryptFormat);
        return disk;
    }

    @Override
    public boolean connectPhysicalDisk(String name, KVMStoragePool pool, Map<String, String> details, boolean isVMMigrate) {
        // this is for managed storage that needs to prep disks prior to use
        return true;
    }

    @Override
    public boolean disconnectPhysicalDisk(String uuid, KVMStoragePool pool) {
        // this is for managed storage that needs to cleanup disks after use
        return true;
    }

    @Override
    public boolean disconnectPhysicalDisk(Map<String, String> volumeToDisconnect) {
        // this is for managed storage that needs to cleanup disks after use
        return false;
    }

    @Override
    public boolean disconnectPhysicalDiskByPath(String localPath) {
        // we've only ever cleaned up ISOs that are NFS mounted
        String poolUuid = null;
        if (localPath != null && localPath.startsWith(_mountPoint) && localPath.endsWith(".iso")) {
            String[] token = localPath.split("/");

            if (token.length > 3) {
                poolUuid = token[2];
            }
        } else {
            return false;
        }

        if (poolUuid == null) {
            return false;
        }

        try {
            Connect conn = LibvirtConnection.getConnection();

            conn.storagePoolLookupByUUIDString(poolUuid);

            deleteStoragePool(poolUuid);

            return true;
        } catch (LibvirtException ex) {
            return false;
        } catch (CloudRuntimeException ex) {
            return false;
        }
    }

    @Override
    public boolean deletePhysicalDisk(String uuid, KVMStoragePool pool, Storage.ImageFormat format) {

        logger.info("Attempting to remove volume " + uuid + " from pool " + pool.getUuid());

        /**
         * RBD volume can have snapshots and while they exist libvirt
         * can't remove the RBD volume
         *
         * We have to remove those snapshots first
         */
        if (pool.getType() == StoragePoolType.RBD) {
            try {
                logger.info("Unprotecting and Removing RBD snapshots of image " + pool.getSourceDir() + "/" + uuid + " prior to removing the image");

                Rados r = new Rados(pool.getAuthUserName());
                r.confSet("mon_host", pool.getSourceHost() + ":" + pool.getSourcePort());
                r.confSet("key", pool.getAuthSecret());
                r.confSet("client_mount_timeout", "30");
                r.connect();
                logger.debug("Successfully connected to Ceph cluster at " + r.confGet("mon_host"));

                IoCTX io = r.ioCtxCreate(pool.getSourceDir());
                Rbd rbd = new Rbd(io);
                RbdImage image = rbd.open(uuid);
                logger.debug("Fetching list of snapshots of RBD image " + pool.getSourceDir() + "/" + uuid);
                List<RbdSnapInfo> snaps = image.snapList();
                try {
                    for (RbdSnapInfo snap : snaps) {
                        if (image.snapIsProtected(snap.name)) {
                            logger.debug("Unprotecting snapshot " + pool.getSourceDir() + "/" + uuid + "@" + snap.name);
                            image.snapUnprotect(snap.name);
                        } else {
                            logger.debug("Snapshot " + pool.getSourceDir() + "/" + uuid + "@" + snap.name + " is not protected.");
                        }
                        logger.debug("Removing snapshot " + pool.getSourceDir() + "/" + uuid + "@" + snap.name);
                        image.snapRemove(snap.name);
                    }
                    logger.info("Successfully unprotected and removed any remaining snapshots (" + snaps.size() + ") of "
                        + pool.getSourceDir() + "/" + uuid + " Continuing to remove the RBD image");
                } catch (RbdException e) {
                    logger.error("Failed to remove snapshot with exception: " + e.toString() +
                        ", RBD error: " + ErrorCode.getErrorMessage(e.getReturnValue()));
                    throw new CloudRuntimeException(e.toString() + " - " + ErrorCode.getErrorMessage(e.getReturnValue()));
                } finally {
                    logger.debug("Closing image and destroying context");
                    rbd.close(image);
                    r.ioCtxDestroy(io);
                }
            } catch (RadosException e) {
                logger.error("Failed to remove snapshot with exception: " + e.toString() +
                    ", RBD error: " + ErrorCode.getErrorMessage(e.getReturnValue()));
                throw new CloudRuntimeException(e.toString() + " - " + ErrorCode.getErrorMessage(e.getReturnValue()));
            } catch (RbdException e) {
                logger.error("Failed to remove snapshot with exception: " + e.toString() +
                    ", RBD error: " + ErrorCode.getErrorMessage(e.getReturnValue()));
                throw new CloudRuntimeException(e.toString() + " - " + ErrorCode.getErrorMessage(e.getReturnValue()));
            }
        }

        LibvirtStoragePool libvirtPool = (LibvirtStoragePool)pool;
        try {
            StorageVol vol = getVolume(libvirtPool.getPool(), uuid);
            logger.debug("Instructing libvirt to remove volume " + uuid + " from pool " + pool.getUuid());
            if(Storage.ImageFormat.DIR.equals(format)){
                deleteDirVol(libvirtPool, vol);
            } else {
                deleteVol(libvirtPool, vol);
            }
            vol.free();
            return true;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }
    }

    /**
     * This function copies a physical disk from Secondary Storage to Primary Storage
     * or from Primary to Primary Storage
     *
     * The first time a template is deployed in Primary Storage it will be copied from
     * Secondary to Primary.
     *
     * If it has been created on Primary Storage, it will be copied on the Primary Storage
     */
    @Override
    public KVMPhysicalDisk createDiskFromTemplate(KVMPhysicalDisk template,
            String name, PhysicalDiskFormat format, Storage.ProvisioningType provisioningType, long size, KVMStoragePool destPool, int timeout, byte[] passphrase) {

        logger.info("Creating volume " + name + " from template " + template.getName() + " in pool " + destPool.getUuid() +
                " (" + destPool.getType().toString() + ") with size " + toHumanReadableSize(size));

        KVMPhysicalDisk disk = null;

        if (destPool.getType() == StoragePoolType.RBD) {
            disk = createDiskFromTemplateOnRBD(template, name, format, provisioningType, size, destPool, timeout);
        } else {
            try (KeyFile keyFile = new KeyFile(passphrase)){
                String newUuid = name;
                List<QemuObject> passphraseObjects = new ArrayList<>();
                disk = destPool.createPhysicalDisk(newUuid, format, provisioningType, template.getVirtualSize(), passphrase);
                if (disk == null) {
                    throw new CloudRuntimeException("Failed to create disk from template " + template.getName());
                }

                if (template.getFormat() == PhysicalDiskFormat.TAR) {
                    Script.runSimpleBashScript("tar -x -f " + template.getPath() + " -C " + disk.getPath(), timeout); // TO BE FIXED to aware provisioningType
                } else if (template.getFormat() == PhysicalDiskFormat.DIR) {
                    Script.runSimpleBashScript("mkdir -p " + disk.getPath());
                    Script.runSimpleBashScript("chmod 755 " + disk.getPath());
                    Script.runSimpleBashScript("tar -x -f " + template.getPath() + "/*.tar -C " + disk.getPath(), timeout);
                } else if (format == PhysicalDiskFormat.QCOW2) {
                    QemuImg qemu = new QemuImg(timeout);
                    QemuImgFile destFile = new QemuImgFile(disk.getPath(), format);
                    if (size > template.getVirtualSize()) {
                        destFile.setSize(size);
                    } else {
                        destFile.setSize(template.getVirtualSize());
                    }
                    Map<String, String> options = new HashMap<String, String>();
                    options.put("preallocation", QemuImg.PreallocationType.getPreallocationType(provisioningType).toString());


                    if (keyFile.isSet()) {
                        passphraseObjects.add(QemuObject.prepareSecretForQemuImg(format, QemuObject.EncryptFormat.LUKS, keyFile.toString(), "sec0", options));
                        disk.setQemuEncryptFormat(QemuObject.EncryptFormat.LUKS);
                    }

                    QemuImgFile srcFile = new QemuImgFile(template.getPath(), template.getFormat());
                    Boolean createFullClone = AgentPropertiesFileHandler.getPropertyValue(AgentProperties.CREATE_FULL_CLONE);
                    switch(provisioningType){
                    case THIN:
                        logger.info("Creating volume [{}] {} backing file [{}] as the property [{}] is [{}].", destFile.getFileName(), createFullClone ? "without" : "with",
                                template.getPath(), AgentProperties.CREATE_FULL_CLONE.getName(), createFullClone);
                        if (createFullClone) {
                            qemu.convert(srcFile, destFile, options, passphraseObjects, null, false);
                        } else {
                            qemu.create(destFile, srcFile, options, passphraseObjects);
                        }
                        break;
                    case SPARSE:
                    case FAT:
                        srcFile = new QemuImgFile(template.getPath(), template.getFormat());
                        qemu.convert(srcFile, destFile, options, passphraseObjects, null, false);
                        break;
                    }
                } else if (format == PhysicalDiskFormat.RAW) {
                    PhysicalDiskFormat destFormat = PhysicalDiskFormat.RAW;
                    Map<String, String> options = new HashMap<String, String>();

                    if (keyFile.isSet()) {
                        destFormat = PhysicalDiskFormat.LUKS;
                        disk.setQemuEncryptFormat(QemuObject.EncryptFormat.LUKS);
                        passphraseObjects.add(QemuObject.prepareSecretForQemuImg(destFormat, QemuObject.EncryptFormat.LUKS, keyFile.toString(), "sec0", options));
                    }

                    QemuImgFile sourceFile = new QemuImgFile(template.getPath(), template.getFormat());
                    QemuImgFile destFile = new QemuImgFile(disk.getPath(), destFormat);
                    if (size > template.getVirtualSize()) {
                        destFile.setSize(size);
                    } else {
                        destFile.setSize(template.getVirtualSize());
                    }
                    QemuImg qemu = new QemuImg(timeout);
                    qemu.convert(sourceFile, destFile, options, passphraseObjects, null, false);
                }
            } catch (QemuImgException | LibvirtException | IOException e) {
                throw new CloudRuntimeException(String.format("Failed to create %s due to a failed execution of qemu-img", name), e);
            }
        }

        return disk;
    }

    private KVMPhysicalDisk createDiskFromTemplateOnRBD(KVMPhysicalDisk template,
            String name, PhysicalDiskFormat format, Storage.ProvisioningType provisioningType, long size, KVMStoragePool destPool, int timeout){

        /*
            With RBD you can't run qemu-img convert with an existing RBD image as destination
            qemu-img will exit with the error that the destination already exists.
            So for RBD we don't create the image, but let qemu-img do that for us.

            We then create a KVMPhysicalDisk object that we can return
         */

        KVMStoragePool srcPool = template.getPool();
        KVMPhysicalDisk disk = null;
        String newUuid = name;

        format = PhysicalDiskFormat.RAW;
        disk = new KVMPhysicalDisk(destPool.getSourceDir() + "/" + newUuid, newUuid, destPool);
        disk.setFormat(format);
        if (size > template.getVirtualSize()) {
            disk.setSize(size);
            disk.setVirtualSize(size);
        } else {
            // leave these as they were if size isn't applicable
            disk.setSize(template.getVirtualSize());
            disk.setVirtualSize(disk.getSize());
        }


        QemuImgFile srcFile;
        QemuImgFile destFile = new QemuImgFile(KVMPhysicalDisk.RBDStringBuilder(destPool, disk.getPath()));
        destFile.setFormat(format);

        if (srcPool.getType() != StoragePoolType.RBD) {
            srcFile = new QemuImgFile(template.getPath(), template.getFormat());
            try{
                QemuImg qemu = new QemuImg(timeout);
                qemu.convert(srcFile, destFile);
            } catch (QemuImgException | LibvirtException e) {
                logger.error("Failed to create " + disk.getPath() +
                        " due to a failed executing of qemu-img: " + e.getMessage());
            }
        } else {

            /**
             * We have to find out if the source file is in the same RBD pool and has
             * RBD format 2 before we can do a layering/clone operation on the RBD image
             *
             * This will be the case when the template is already on Primary Storage and
             * we want to copy it
             */

            try {
                if ((srcPool.getSourceHost().equals(destPool.getSourceHost())) && (srcPool.getSourceDir().equals(destPool.getSourceDir()))) {
                    /* We are on the same Ceph cluster, but we require RBD format 2 on the source image */
                    logger.debug("Trying to perform a RBD clone (layering) since we are operating in the same storage pool");

                    Rados r = new Rados(srcPool.getAuthUserName());
                    r.confSet("mon_host", srcPool.getSourceHost() + ":" + srcPool.getSourcePort());
                    r.confSet("key", srcPool.getAuthSecret());
                    r.confSet("client_mount_timeout", "30");
                    r.connect();
                    logger.debug("Successfully connected to Ceph cluster at " + r.confGet("mon_host"));

                    IoCTX io = r.ioCtxCreate(srcPool.getSourceDir());
                    Rbd rbd = new Rbd(io);
                    RbdImage srcImage = rbd.open(template.getName());

                    if (srcImage.isOldFormat()) {
                        /* The source image is RBD format 1, we have to do a regular copy */
                        logger.debug("The source image " + srcPool.getSourceDir() + "/" + template.getName() +
                                " is RBD format 1. We have to perform a regular copy (" + toHumanReadableSize(disk.getVirtualSize()) + " bytes)");

                        rbd.create(disk.getName(), disk.getVirtualSize(), RBD_FEATURES, rbdOrder);
                        RbdImage destImage = rbd.open(disk.getName());

                        logger.debug("Starting to copy " + srcImage.getName() +  " to " + destImage.getName() + " in Ceph pool " + srcPool.getSourceDir());
                        rbd.copy(srcImage, destImage);

                        logger.debug("Finished copying " + srcImage.getName() +  " to " + destImage.getName() + " in Ceph pool " + srcPool.getSourceDir());
                        rbd.close(destImage);
                    } else {
                        logger.debug("The source image " + srcPool.getSourceDir() + "/" + template.getName()
                                + " is RBD format 2. We will perform a RBD clone using snapshot "
                                + rbdTemplateSnapName);
                        /* The source image is format 2, we can do a RBD snapshot+clone (layering) */


                        logger.debug("Checking if RBD snapshot " + srcPool.getSourceDir() + "/" + template.getName()
                                + "@" + rbdTemplateSnapName + " exists prior to attempting a clone operation.");

                        List<RbdSnapInfo> snaps = srcImage.snapList();
                        logger.debug("Found " + snaps.size() +  " snapshots on RBD image " + srcPool.getSourceDir() + "/" + template.getName());
                        boolean snapFound = false;
                        for (RbdSnapInfo snap : snaps) {
                            if (rbdTemplateSnapName.equals(snap.name)) {
                                logger.debug("RBD snapshot " + srcPool.getSourceDir() + "/" + template.getName()
                                        + "@" + rbdTemplateSnapName + " already exists.");
                                snapFound = true;
                                break;
                            }
                        }

                        if (!snapFound) {
                            logger.debug("Creating RBD snapshot " + rbdTemplateSnapName + " on image " + name);
                            srcImage.snapCreate(rbdTemplateSnapName);
                            logger.debug("Protecting RBD snapshot " + rbdTemplateSnapName + " on image " + name);
                            srcImage.snapProtect(rbdTemplateSnapName);
                        }

                        rbd.clone(template.getName(), rbdTemplateSnapName, io, disk.getName(), RBD_FEATURES, rbdOrder);
                        logger.debug("Successfully cloned " + template.getName() + "@" + rbdTemplateSnapName + " to " + disk.getName());
                        /* We also need to resize the image if the VM was deployed with a larger root disk size */
                        if (disk.getVirtualSize() > template.getVirtualSize()) {
                            RbdImage diskImage = rbd.open(disk.getName());
                            diskImage.resize(disk.getVirtualSize());
                            rbd.close(diskImage);
                            logger.debug("Resized " + disk.getName() + " to " + toHumanReadableSize(disk.getVirtualSize()));
                        }

                    }

                    rbd.close(srcImage);
                    r.ioCtxDestroy(io);
                } else {
                    /* The source pool or host is not the same Ceph cluster, we do a simple copy with Qemu-Img */
                    logger.debug("Both the source and destination are RBD, but not the same Ceph cluster. Performing a copy");

                    Rados rSrc = new Rados(srcPool.getAuthUserName());
                    rSrc.confSet("mon_host", srcPool.getSourceHost() + ":" + srcPool.getSourcePort());
                    rSrc.confSet("key", srcPool.getAuthSecret());
                    rSrc.confSet("client_mount_timeout", "30");
                    rSrc.connect();
                    logger.debug("Successfully connected to source Ceph cluster at " + rSrc.confGet("mon_host"));

                    Rados rDest = new Rados(destPool.getAuthUserName());
                    rDest.confSet("mon_host", destPool.getSourceHost() + ":" + destPool.getSourcePort());
                    rDest.confSet("key", destPool.getAuthSecret());
                    rDest.confSet("client_mount_timeout", "30");
                    rDest.connect();
                    logger.debug("Successfully connected to source Ceph cluster at " + rDest.confGet("mon_host"));

                    IoCTX sIO = rSrc.ioCtxCreate(srcPool.getSourceDir());
                    Rbd sRbd = new Rbd(sIO);

                    IoCTX dIO = rDest.ioCtxCreate(destPool.getSourceDir());
                    Rbd dRbd = new Rbd(dIO);

                    logger.debug("Creating " + disk.getName() + " on the destination cluster " + rDest.confGet("mon_host") + " in pool " +
                            destPool.getSourceDir());
                    dRbd.create(disk.getName(), disk.getVirtualSize(), RBD_FEATURES, rbdOrder);

                    RbdImage srcImage = sRbd.open(template.getName());
                    RbdImage destImage = dRbd.open(disk.getName());

                    logger.debug("Copying " + template.getName() + " from Ceph cluster " + rSrc.confGet("mon_host") + " to " + disk.getName()
                            + " on cluster " + rDest.confGet("mon_host"));
                    sRbd.copy(srcImage, destImage);

                    sRbd.close(srcImage);
                    dRbd.close(destImage);

                    rSrc.ioCtxDestroy(sIO);
                    rDest.ioCtxDestroy(dIO);
                }
            } catch (RadosException e) {
                logger.error("Failed to perform a RADOS action on the Ceph cluster, the error was: " + e.getMessage());
                disk = null;
            } catch (RbdException e) {
                logger.error("Failed to perform a RBD action on the Ceph cluster, the error was: " + e.getMessage());
                disk = null;
            }
        }
        return disk;
    }

    @Override
    public KVMPhysicalDisk createTemplateFromDisk(KVMPhysicalDisk disk, String name, PhysicalDiskFormat format, long size, KVMStoragePool destPool) {
        return null;
    }

    @Override
    public List<KVMPhysicalDisk> listPhysicalDisks(String storagePoolUuid, KVMStoragePool pool) {
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool)pool;
        StoragePool virtPool = libvirtPool.getPool();
        List<KVMPhysicalDisk> disks = new ArrayList<KVMPhysicalDisk>();
        try {
            String[] vols = virtPool.listVolumes();
            for (String volName : vols) {
                KVMPhysicalDisk disk = getPhysicalDisk(volName, pool);
                disks.add(disk);
            }
            return disks;
        } catch (LibvirtException e) {
            throw new CloudRuntimeException(e.toString());
        }
    }

    @Override
    public KVMPhysicalDisk copyPhysicalDisk(KVMPhysicalDisk disk, String name, KVMStoragePool destPool, int timeout) {
        return copyPhysicalDisk(disk, name, destPool, timeout, null, null, null);
    }

    /**
     * This copies a volume from Primary Storage to Secondary Storage
     *
     * In theory it could also do it the other way around, but the current implementation
     * in ManagementServerImpl shows that the destPool is always a Secondary Storage Pool
     */
    @Override
    public KVMPhysicalDisk copyPhysicalDisk(KVMPhysicalDisk disk, String name, KVMStoragePool destPool, int timeout, byte[] srcPassphrase, byte[] dstPassphrase, Storage.ProvisioningType provisioningType) {

        /**
            With RBD you can't run qemu-img convert with an existing RBD image as destination
            qemu-img will exit with the error that the destination already exists.
            So for RBD we don't create the image, but let qemu-img do that for us.

            We then create a KVMPhysicalDisk object that we can return

            It is however very unlikely that the destPool will be RBD, since it isn't supported
            for Secondary Storage
         */

        KVMStoragePool srcPool = disk.getPool();
        /* Linstor images are always stored as RAW, but Linstor uses qcow2 in DB,
           to support snapshots(backuped) as qcow2 files. */
        PhysicalDiskFormat sourceFormat = srcPool.getType() != StoragePoolType.Linstor ?
                disk.getFormat() : PhysicalDiskFormat.RAW;
        String sourcePath = disk.getPath();

        KVMPhysicalDisk newDisk;
        logger.debug("copyPhysicalDisk: disk size:" + toHumanReadableSize(disk.getSize()) + ", virtualsize:" + toHumanReadableSize(disk.getVirtualSize())+" format:"+disk.getFormat());
        if (destPool.getType() != StoragePoolType.RBD) {
            if (disk.getFormat() == PhysicalDiskFormat.TAR) {
                newDisk = destPool.createPhysicalDisk(name, PhysicalDiskFormat.DIR, Storage.ProvisioningType.THIN, disk.getVirtualSize(), null);
            } else {
                newDisk = destPool.createPhysicalDisk(name, Storage.ProvisioningType.THIN, disk.getVirtualSize(), null);
            }
        } else {
            newDisk = new KVMPhysicalDisk(destPool.getSourceDir() + "/" + name, name, destPool);
            newDisk.setFormat(PhysicalDiskFormat.RAW);
            newDisk.setSize(disk.getVirtualSize());
            newDisk.setVirtualSize(disk.getSize());
        }

        String destPath = newDisk.getPath();
        PhysicalDiskFormat destFormat = newDisk.getFormat();

        QemuImg qemu;

        try {
            qemu = new QemuImg(timeout);
        } catch (QemuImgException | LibvirtException ex ) {
            throw new CloudRuntimeException("Failed to create qemu-img command", ex);
        }
        QemuImgFile srcFile = null;
        QemuImgFile destFile = null;

        if ((srcPool.getType() != StoragePoolType.RBD) && (destPool.getType() != StoragePoolType.RBD)) {
            if(sourceFormat == PhysicalDiskFormat.TAR && destFormat == PhysicalDiskFormat.DIR) { //LXC template
                Script.runSimpleBashScript("cp "+ sourcePath + " " + destPath);
            } else if (sourceFormat == PhysicalDiskFormat.TAR) {
                Script.runSimpleBashScript("tar -x -f " + sourcePath + " -C " + destPath, timeout);
            } else if (sourceFormat == PhysicalDiskFormat.DIR) {
                Script.runSimpleBashScript("mkdir -p " + destPath);
                Script.runSimpleBashScript("chmod 755 " + destPath);
                Script.runSimpleBashScript("cp -p -r " + sourcePath + "/* " + destPath, timeout);
            } else {
                srcFile = new QemuImgFile(sourcePath, sourceFormat);
                try {
                    Map<String, String> info = qemu.info(srcFile);
                    String backingFile = info.get(QemuImg.BACKING_FILE);
                    // qcow2 templates can just be copied into place
                    if (sourceFormat.equals(destFormat) && backingFile == null && sourcePath.endsWith(".qcow2")) {
                        String result = Script.runSimpleBashScript("cp -f " + sourcePath + " " + destPath, timeout);
                        if (result != null) {
                            throw new CloudRuntimeException("Failed to create disk: " + result);
                        }
                    } else {
                        destFile = new QemuImgFile(destPath, destFormat);
                        try {
                            boolean isQCOW2 = PhysicalDiskFormat.QCOW2.equals(sourceFormat);
                            qemu.convert(srcFile, destFile, null, null, new QemuImageOptions(srcFile.getFormat(), srcFile.getFileName(), null),
                                    null, false, isQCOW2);
                            Map<String, String> destInfo = qemu.info(destFile);
                            Long virtualSize = Long.parseLong(destInfo.get(QemuImg.VIRTUAL_SIZE));
                            newDisk.setVirtualSize(virtualSize);
                            newDisk.setSize(virtualSize);
                        } catch (QemuImgException e) {
                            logger.error("Failed to convert [{}] to [{}] due to: [{}].", srcFile.getFileName(), destFile.getFileName(), e.getMessage(), e);
                            newDisk = null;
                        }
                    }
                } catch (QemuImgException e) {
                    logger.error("Failed to fetch the information of file " + srcFile.getFileName() + " the error was: " + e.getMessage());
                    newDisk = null;
                }
            }
        } else if ((srcPool.getType() != StoragePoolType.RBD) && (destPool.getType() == StoragePoolType.RBD)) {
            /**
             * Using qemu-img we copy the QCOW2 disk to RAW (on RBD) directly.
             * To do so it's mandatory that librbd on the system is at least 0.67.7 (Ceph Dumpling)
             */
            logger.debug("The source image is not RBD, but the destination is. We will convert into RBD format 2");
            try {
                srcFile = new QemuImgFile(sourcePath, sourceFormat);
                String rbdDestPath = destPool.getSourceDir() + "/" + name;
                String rbdDestFile = KVMPhysicalDisk.RBDStringBuilder(destPool, rbdDestPath);
                destFile = new QemuImgFile(rbdDestFile, destFormat);

                logger.debug("Starting copy from source image " + srcFile.getFileName() + " to RBD image " + rbdDestPath);
                qemu.convert(srcFile, destFile);
                logger.debug("Successfully converted source image " + srcFile.getFileName() + " to RBD image " + rbdDestPath);

                /* We have to stat the RBD image to see how big it became afterwards */
                Rados r = new Rados(destPool.getAuthUserName());
                r.confSet("mon_host", destPool.getSourceHost() + ":" + destPool.getSourcePort());
                r.confSet("key", destPool.getAuthSecret());
                r.confSet("client_mount_timeout", "30");
                r.connect();
                logger.debug("Successfully connected to Ceph cluster at " + r.confGet("mon_host"));

                IoCTX io = r.ioCtxCreate(destPool.getSourceDir());
                Rbd rbd = new Rbd(io);

                RbdImage image = rbd.open(name);
                RbdImageInfo rbdInfo = image.stat();
                newDisk.setSize(rbdInfo.size);
                newDisk.setVirtualSize(rbdInfo.size);
                logger.debug("After copy the resulting RBD image " + rbdDestPath + " is " + toHumanReadableSize(rbdInfo.size) + " bytes long");
                rbd.close(image);

                r.ioCtxDestroy(io);
            } catch (QemuImgException | LibvirtException e) {
                String srcFilename = srcFile != null ? srcFile.getFileName() : null;
                String destFilename = destFile != null ? destFile.getFileName() : null;
                logger.error(String.format("Failed to convert from %s to %s the error was: %s", srcFilename, destFilename, e.getMessage()));
                newDisk = null;
            } catch (RadosException e) {
                logger.error("A Ceph RADOS operation failed (" + e.getReturnValue() + "). The error was: " + e.getMessage());
                newDisk = null;
            } catch (RbdException e) {
                logger.error("A Ceph RBD operation failed (" + e.getReturnValue() + "). The error was: " + e.getMessage());
                newDisk = null;
            }
        } else {
            /**
                We let Qemu-Img do the work here. Although we could work with librbd and have that do the cloning
                it doesn't benefit us. It's better to keep the current code in place which works
             */
            srcFile = new QemuImgFile(KVMPhysicalDisk.RBDStringBuilder(srcPool, sourcePath));
            srcFile.setFormat(sourceFormat);
            destFile = new QemuImgFile(destPath);
            destFile.setFormat(destFormat);

            try {
                qemu.convert(srcFile, destFile);
            } catch (QemuImgException | LibvirtException e) {
                logger.error("Failed to convert " + srcFile.getFileName() + " to " + destFile.getFileName() + " the error was: " + e.getMessage());
                newDisk = null;
            }
        }

        if (newDisk == null) {
            throw new CloudRuntimeException("Failed to copy " + disk.getPath() + " to " + name);
        }

        return newDisk;
    }

    @Override
    public boolean refresh(KVMStoragePool pool) {
        LibvirtStoragePool libvirtPool = (LibvirtStoragePool)pool;
        StoragePool virtPool = libvirtPool.getPool();
        try {
            refreshPool(virtPool);
        } catch (LibvirtException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean deleteStoragePool(KVMStoragePool pool) {
        return deleteStoragePool(pool.getUuid());
    }

    private void refreshPool(StoragePool pool) throws LibvirtException {
        pool.refresh(0);
        return;
    }

    private void deleteVol(LibvirtStoragePool pool, StorageVol vol) throws LibvirtException {
        vol.delete(0);
    }

    private void deleteDirVol(LibvirtStoragePool pool, StorageVol vol) throws LibvirtException {
        Script.runSimpleBashScript("rm -r --interactive=never " + vol.getPath());
    }
}
