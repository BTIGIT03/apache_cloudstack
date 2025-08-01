/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.datastore.driver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import com.cloud.storage.dao.SnapshotDetailsVO;
import org.apache.cloudstack.engine.subsystem.api.storage.ChapInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.CopyCommandResult;
import org.apache.cloudstack.engine.subsystem.api.storage.CreateCmdResult;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPoint;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPointSelector;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreDriver;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeDataFactory;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.framework.async.AsyncCompletionCallback;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.resourcedetail.DiskOfferingDetailVO;
import org.apache.cloudstack.resourcedetail.dao.DiskOfferingDetailsDao;
import org.apache.cloudstack.storage.RemoteHostEndPoint;
import org.apache.cloudstack.storage.command.CommandResult;
import org.apache.cloudstack.storage.command.CopyCmdAnswer;
import org.apache.cloudstack.storage.command.CreateObjectAnswer;
import org.apache.cloudstack.storage.command.StorageSubSystemCommand;
import org.apache.cloudstack.storage.datastore.api.StorPoolSnapshotDef;
import org.apache.cloudstack.storage.datastore.api.StorPoolVolumeDef;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreVO;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailVO;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailsDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.apache.cloudstack.storage.datastore.util.StorPoolHelper;
import org.apache.cloudstack.storage.datastore.util.StorPoolUtil;
import org.apache.cloudstack.storage.datastore.util.StorPoolUtil.SpApiResponse;
import org.apache.cloudstack.storage.datastore.util.StorPoolUtil.SpConnectionDesc;
import org.apache.cloudstack.storage.snapshot.StorPoolConfigurationManager;
import org.apache.cloudstack.storage.to.PrimaryDataStoreTO;
import org.apache.cloudstack.storage.to.SnapshotObjectTO;
import org.apache.cloudstack.storage.to.TemplateObjectTO;
import org.apache.cloudstack.storage.to.VolumeObjectTO;
import org.apache.cloudstack.storage.volume.VolumeObject;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.storage.ResizeVolumeAnswer;
import com.cloud.agent.api.storage.StorPoolBackupSnapshotCommand;
import com.cloud.agent.api.storage.StorPoolBackupTemplateFromSnapshotCommand;
import com.cloud.agent.api.storage.StorPoolCopyVolumeToSecondaryCommand;
import com.cloud.agent.api.storage.StorPoolDownloadTemplateCommand;
import com.cloud.agent.api.storage.StorPoolDownloadVolumeCommand;
import com.cloud.agent.api.storage.StorPoolResizeVolumeCommand;
import com.cloud.agent.api.storage.StorPoolSetVolumeEncryptionAnswer;
import com.cloud.agent.api.storage.StorPoolSetVolumeEncryptionCommand;
import com.cloud.agent.api.to.DataObjectType;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.DataTO;
import com.cloud.agent.api.to.StorageFilerTO;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.kvm.storage.StorPoolStorageAdaptor;
import com.cloud.offering.DiskOffering;
import com.cloud.server.ResourceTag;
import com.cloud.server.ResourceTag.ResourceObjectType;
import com.cloud.service.ServiceOfferingDetailsVO;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.service.dao.ServiceOfferingDetailsDao;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.ResizeVolumePayload;
import com.cloud.storage.Snapshot;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePool;
import com.cloud.storage.VMTemplateDetailVO;
import com.cloud.storage.VMTemplateStoragePoolVO;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeDetailVO;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.SnapshotDetailsDao;
import com.cloud.storage.dao.StoragePoolHostDao;
import com.cloud.storage.dao.VMTemplateDetailsDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.dao.VolumeDetailsDao;
import com.cloud.tags.dao.ResourceTagDao;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StorPoolPrimaryDataStoreDriver implements PrimaryDataStoreDriver {

    protected Logger logger = LogManager.getLogger(getClass());

    @Inject
    private VolumeDao volumeDao;
    @Inject
    private StorageManager storageMgr;
    @Inject
    private PrimaryDataStoreDao primaryStoreDao;
    @Inject
    private EndPointSelector selector;
    @Inject
    private ConfigurationDao configDao;
    @Inject
    private TemplateDataStoreDao vmTemplateDataStoreDao;
    @Inject
    private VMInstanceDao vmInstanceDao;
    @Inject
    private ClusterDao clusterDao;
    @Inject
    private HostDao hostDao;
    @Inject
    private ResourceTagDao resourceTagDao;
    @Inject
    private SnapshotDetailsDao snapshotDetailsDao;
    @Inject
    private SnapshotDao snapshotDao;
    @Inject
    private SnapshotDataStoreDao snapshotDataStoreDao;
    @Inject
    private VolumeDetailsDao volumeDetailsDao;
    @Inject
    private VMTemplateDetailsDao vmTemplateDetailsDao;
    @Inject
    private StoragePoolDetailsDao storagePoolDetailsDao;
    @Inject
    private StoragePoolHostDao storagePoolHostDao;
    @Inject
    DataStoreManager dataStoreManager;
    @Inject
    private DiskOfferingDetailsDao diskOfferingDetailsDao;
    @Inject
    private ServiceOfferingDetailsDao serviceOfferingDetailDao;
    @Inject
    private ServiceOfferingDao serviceOfferingDao;
    @Inject
    private VolumeDataFactory volumeDataFactory;

    private SnapshotDataStoreVO getSnapshotImageStoreRef(long snapshotId, long zoneId) {
        List<SnapshotDataStoreVO> snaps = snapshotDataStoreDao.listReadyBySnapshot(snapshotId, DataStoreRole.Image);
        for (SnapshotDataStoreVO ref : snaps) {
            if (zoneId == dataStoreManager.getStoreZoneId(ref.getDataStoreId(), ref.getRole())) {
                return ref;
            }
        }
        return null;
    }

    @Override
    public Map<String, String> getCapabilities() {
        return null;
    }

    @Override
    public DataTO getTO(DataObject data) {
        return null;
    }

    @Override
    public DataStoreTO getStoreTO(DataStore store) {
        return null;
    }

    @Override
    public long getUsedBytes(StoragePool storagePool) {
        return 0;
    }

    @Override
    public long getUsedIops(StoragePool storagePool) {
        return 0;
    }

    @Override
    public boolean grantAccess(DataObject data, Host host, DataStore dataStore) {
        return false;
    }

    @Override
    public void revokeAccess(DataObject data, Host host, DataStore dataStore) {
        if (DataObjectType.VOLUME == data.getType()) {
            final VolumeVO volume = volumeDao.findById(data.getId());
            if (volume.getInstanceId() == null) {
                StorPoolUtil.spLog("Removing tags from detached volume=%s", volume.toString());
                SpConnectionDesc conn = StorPoolUtil.getSpConnection(dataStore.getUuid(), dataStore.getId(), storagePoolDetailsDao, primaryStoreDao);
                StorPoolUtil.volumeRemoveTags(StorPoolStorageAdaptor.getVolumeNameFromPath(volume.getPath(), true), conn);
            }
        }
    }

    private void updateStoragePool(final long poolId, final long deltaUsedBytes) {
        StoragePoolVO storagePool = primaryStoreDao.findById(poolId);
        final long capacity = storagePool.getCapacityBytes();
        final long used = storagePool.getUsedBytes() + deltaUsedBytes;

        storagePool.setUsedBytes(used < 0 ? 0 : (used > capacity ? capacity : used));
        primaryStoreDao.update(poolId, storagePool);
    }

    private String getVMInstanceUUID(Long id) {
        return id != null ? vmInstanceDao.findById(id).getUuid() : null;
    }

    protected void _completeResponse(final CreateObjectAnswer answer, final String err, final AsyncCompletionCallback<CommandResult> callback)
    {
        final CreateCmdResult res = new CreateCmdResult(null, answer);
        res.setResult(err);
        callback.complete(res);
    }

    protected void completeResponse(final DataTO result, final AsyncCompletionCallback<CommandResult> callback)
    {
        _completeResponse(new CreateObjectAnswer(result), null, callback);
    }

    protected void completeResponse(final String err, final AsyncCompletionCallback<CommandResult> callback)
    {
        _completeResponse(new CreateObjectAnswer(err), err, callback);
    }

    @Override
    public long getDataObjectSizeIncludingHypervisorSnapshotReserve(DataObject dataObject, StoragePool pool) {
        return dataObject.getSize();
    }

    @Override
    public long getBytesRequiredForTemplate(TemplateInfo templateInfo, StoragePool storagePool) {
        return 0;
    }

    @Override
    public ChapInfo getChapInfo(DataObject dataObject) {
        return null;
    }

    @Override
    public void createAsync(DataStore dataStore, DataObject data, AsyncCompletionCallback<CreateCmdResult> callback) {
        String path = null;
        Answer answer;
        String tier = null;
        String template = null;
        if (data.getType() == DataObjectType.VOLUME) {
            try {
                VolumeInfo vinfo = (VolumeInfo)data;
                String name = vinfo.getUuid();
                Long size = vinfo.getPassphraseId() == null ? vinfo.getSize() : vinfo.getSize() + 2097152;
                Long vmId = vinfo.getInstanceId();

                SpConnectionDesc conn = StorPoolUtil.getSpConnection(dataStore.getUuid(), dataStore.getId(), storagePoolDetailsDao, primaryStoreDao);

                if (vinfo.getDiskOfferingId() != null) {
                    tier = getTierFromOfferingDetail(vinfo.getDiskOfferingId());
                    if (tier == null) {
                        template = getTemplateFromOfferingDetail(vinfo.getDiskOfferingId());
                    }
                }

                SpApiResponse resp = createStorPoolVolume(template, tier, vinfo, name, size, vmId, conn);
                if (resp.getError() == null) {
                    String volumeName = StorPoolUtil.getNameFromResponse(resp, false);
                    path = StorPoolUtil.devPath(volumeName);

                    updateVolume(dataStore, path, vinfo);

                    if (vinfo.getPassphraseId() != null) {
                        VolumeObjectTO volume = updateVolumeObjectTO(vinfo, resp);
                        answer = createEncryptedVolume(dataStore, data, vinfo, size, volume, null, true);
                    } else {
                        answer = new Answer(null, true, null);
                    }
                    updateStoragePool(dataStore.getId(), size);
                    StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriver.createAsync volume: name=%s, uuid=%s, isAttached=%s vm=%s, payload=%s, template: %s", volumeName, vinfo.getUuid(), vinfo.isAttachedVM(), vinfo.getAttachedVmName(), vinfo.getpayload(), conn.getTemplateName());
                } else {
                    answer = new Answer(null, false, String.format("Could not create StorPool volume %s. Error: %s", name, resp.getError()));
                }
            } catch (Exception e) {
                answer = new Answer(null, false, String.format("Could not create volume due to %s", e.getMessage()));
            }
        } else {
            answer = new Answer(null, false, String.format("Invalid object type \"%s\"  passed to createAsync", data.getType()));
        }
        CreateCmdResult res = new CreateCmdResult(path, answer);
        res.setResult(answer.getDetails());
        if (callback != null) {
            callback.complete(res);
        }
    }

    private SpApiResponse createStorPoolVolume(String template, String tier, VolumeInfo vinfo, String name, Long size,
            Long vmId, SpConnectionDesc conn) {
        SpApiResponse resp = new SpApiResponse();
        Map<String, String> tags = StorPoolHelper.addStorPoolTags(name, getVMInstanceUUID(vmId), "volume", getVcPolicyTag(vmId), tier);
        if (vinfo.getDeviceId() != null) {
            tags.put("disk", vinfo.getDeviceId().toString());
        }
        if (template == null) {
            template = conn.getTemplateName();
        }
        StorPoolVolumeDef volume = new StorPoolVolumeDef(null, size, tags, null, vinfo.getMaxIops(), template, null, null, null);
        resp = StorPoolUtil.volumeCreate(volume, conn);
        return resp;
    }

    private void updateVolume(DataStore dataStore, String path, VolumeInfo vinfo) {
        VolumeVO volume = volumeDao.findById(vinfo.getId());
        volume.setPoolId(dataStore.getId());
        volume.setPath(path);
        volume.setPoolType(StoragePoolType.StorPool);
        volumeDao.update(volume.getId(), volume);
    }

    private StorPoolSetVolumeEncryptionAnswer createEncryptedVolume(DataStore dataStore, DataObject data, VolumeInfo vinfo, Long size, VolumeObjectTO volume, String parentName, boolean isDataDisk) {
        StorPoolSetVolumeEncryptionAnswer ans;
        EndPoint ep = null;
        if (parentName == null) {
            ep = selector.select(data, vinfo.getPassphraseId() != null);
        } else {
            Long clusterId = StorPoolHelper.findClusterIdByGlobalId(parentName, clusterDao);
            if (clusterId == null) {
                ep = selector.select(data, vinfo.getPassphraseId() != null);
            } else {
                List<HostVO> hosts = hostDao.findByClusterIdAndEncryptionSupport(clusterId);
                ep = CollectionUtils.isNotEmpty(hosts) ? RemoteHostEndPoint.getHypervisorHostEndPoint(hosts.get(0)) : ep;
            }
        }
        if (ep == null) {
            ans = new StorPoolSetVolumeEncryptionAnswer(null, false, "Could not find a host with volume encryption");
        } else {
            StorPoolSetVolumeEncryptionCommand cmd = new StorPoolSetVolumeEncryptionCommand(volume, parentName, isDataDisk);
            ans = (StorPoolSetVolumeEncryptionAnswer) ep.sendMessage(cmd);
            if (ans.getResult()) {
                updateStoragePool(dataStore.getId(), size);
            }
        }
        return ans;
    }

    @Override
    public void resize(DataObject data, AsyncCompletionCallback<CreateCmdResult> callback) {
        String path = null;
        String err = null;

        if (data.getType() == DataObjectType.VOLUME) {
            VolumeObject vol = (VolumeObject)data;
            path = vol.getPath();

            err = resizeVolume(data, path, vol);
        } else {
            err = String.format("Invalid object type \"%s\"  passed to resize", data.getType());
        }

        CreateCmdResult res = new CreateCmdResult(path, new Answer(null, err != null, err));
        res.setResult(err);
        callback.complete(res);
    }

    private String resizeVolume(DataObject data, String path, VolumeObject vol) {
        String err = null;
        ResizeVolumePayload payload = (ResizeVolumePayload)vol.getpayload();
        boolean needResize = vol.getSize() != payload.newSize;

        final String name = StorPoolStorageAdaptor.getVolumeNameFromPath(path, true);
        final long oldSize = vol.getSize();
        Long oldMaxIops = vol.getMaxIops();

        try {
            SpConnectionDesc conn = StorPoolUtil.getSpConnection(data.getDataStore().getUuid(), data.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);

            err = updateStorPoolVolume(vol, payload, conn);
            if (err == null && needResize) {
                err = notifyQemuForTheNewSize(data, err, vol, payload);
            }
            if (err != null) {
                // try restoring volume to its initial size
                SpApiResponse response = StorPoolUtil.volumeUpdate(name, oldSize, true, oldMaxIops, conn);
                if (response.getError() != null) {
                    logger.debug(String.format("Could not resize StorPool volume %s back to its original size. Error: %s", name, response.getError()));
                }
            } else {
                updateVolumeWithTheNewSize(vol, payload);
            }
        } catch (Exception e) {
            logger.debug("sending resize command failed", e);
            err = e.toString();
        }
        return err;
    }

    private void updateVolumeWithTheNewSize(VolumeObject vol, ResizeVolumePayload payload) {
        vol.setSize(payload.newSize);
        vol.update();
        if (payload.newMaxIops != null) {
            VolumeVO volume = volumeDao.findById(vol.getId());
            volume.setMaxIops(payload.newMaxIops);
            volumeDao.update(volume.getId(), volume);
        }
        updateStoragePool(vol.getPoolId(), payload.newSize - vol.getSize());
    }

    private String notifyQemuForTheNewSize(DataObject data, String err, VolumeObject vol, ResizeVolumePayload payload)
            throws StorageUnavailableException {
        StoragePool pool = (StoragePool)data.getDataStore();

        StorPoolResizeVolumeCommand resizeCmd = new StorPoolResizeVolumeCommand(vol.getPath(), new StorageFilerTO(pool), vol.getSize(), payload.newSize, payload.shrinkOk,
                payload.instanceName, payload.hosts == null ? false : true);
        ResizeVolumeAnswer answer = (ResizeVolumeAnswer) storageMgr.sendToPool(pool, payload.hosts, resizeCmd);

        if (answer == null || !answer.getResult()) {
            err = answer != null ? answer.getDetails() : "return a null answer, resize failed for unknown reason";
        }
        return err;
    }

    private String updateStorPoolVolume(VolumeObject vol, ResizeVolumePayload payload, SpConnectionDesc conn) {
        String err = null;
        String name = StorPoolStorageAdaptor.getVolumeNameFromPath(vol.getPath(), true);
        Long newDiskOfferingId = payload.getNewDiskOfferingId();
        String tier = null;
        String template = null;
        if (newDiskOfferingId != null) {
            tier = getTierFromOfferingDetail(newDiskOfferingId);
            if (tier == null) {
                template = getTemplateFromOfferingDetail(newDiskOfferingId);
            }
        }
        SpApiResponse resp = new SpApiResponse();
        if (tier != null || template != null) {
            resp = updateVolumeByStorPoolQoS(payload, conn, name, tier, template);
        } else {
            resp = updateVolumeByOffering(vol, payload, conn, name);
        }
        if (resp.getError() != null) {
            err = String.format("Could not resize StorPool volume %s. Error: %s", name, resp.getError());
        }
        return err;
    }

    private static SpApiResponse updateVolumeByStorPoolQoS(ResizeVolumePayload payload, SpConnectionDesc conn, String name, String tier, String template) {
        Map<String, String> tags = StorPoolHelper.addStorPoolTags(null, null, null, null, tier);
        StorPoolVolumeDef spVolume = new StorPoolVolumeDef(name, payload.newSize, tags, null, null, template, null, null,
                payload.shrinkOk);
        return StorPoolUtil.volumeUpdate(spVolume, conn);
    }

    private static SpApiResponse updateVolumeByOffering(VolumeObject vol, ResizeVolumePayload payload, SpConnectionDesc conn, String name) {
        long maxIops = payload.newMaxIops == null ? Long.valueOf(0) : payload.newMaxIops;

        StorPoolVolumeDef spVolume = new StorPoolVolumeDef(name, payload.newSize, null, null, maxIops, null, null, null,
                payload.shrinkOk);
        StorPoolUtil.spLog(
                "StorpoolPrimaryDataStoreDriverImpl.resize: name=%s, uuid=%s, oldSize=%d, newSize=%s, shrinkOk=%s, maxIops=%s",
                name, vol.getUuid(), vol.getSize(), payload.newSize, payload.shrinkOk, maxIops);
        return StorPoolUtil.volumeUpdate(spVolume, conn);
    }

    @Override
    public void deleteAsync(DataStore dataStore, DataObject data, AsyncCompletionCallback<CommandResult> callback) {
        String err = null;
        if (data.getType() == DataObjectType.VOLUME) {
            VolumeInfo vinfo = (VolumeInfo)data;
            String name = StorPoolStorageAdaptor.getVolumeNameFromPath(vinfo.getPath(), true);
            StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriver.deleteAsync delete volume: name=%s, uuid=%s, isAttached=%s vm=%s, payload=%s dataStore=%s", name, vinfo.getUuid(), vinfo.isAttachedVM(), vinfo.getAttachedVmName(), vinfo.getpayload(), dataStore.getUuid());
            if (name == null) {
                name = vinfo.getUuid();
            }
            try {
                SpConnectionDesc conn = StorPoolUtil.getSpConnection(dataStore.getUuid(), dataStore.getId(), storagePoolDetailsDao, primaryStoreDao);
                tryToSnapshotVolumeBeforeDelete(vinfo, dataStore, name, conn);
                SpApiResponse resp = StorPoolUtil.volumeDelete(name, conn);
                if (resp.getError() == null) {
                    updateStoragePool(dataStore.getId(), - vinfo.getSize());
                    VolumeDetailVO detail = volumeDetailsDao.findDetail(vinfo.getId(), StorPoolUtil.SP_PROVIDER_NAME);
                    if (detail != null) {
                        volumeDetailsDao.remove(detail.getId());
                    }
                } else {
                    if (!resp.getError().getName().equalsIgnoreCase("objectDoesNotExist")) {
                        err = String.format("Could not delete StorPool volume %s. Error: %s", name, resp.getError());
                    }
                }
            } catch (Exception e) {
                err = String.format("Could not delete volume due to %s", e.getMessage());
            }
        } else {
            err = String.format("Invalid DataObjectType \"%s\" passed to deleteAsync", data.getType());
        }

        if (err != null) {
            logger.error(err);
            StorPoolUtil.spLog(err);
        }

        CommandResult res = new CommandResult();
        res.setResult(err);
        callback.complete(res);
    }

    private void tryToSnapshotVolumeBeforeDelete(VolumeInfo vinfo, DataStore dataStore, String name, SpConnectionDesc conn) {
        Integer deleteAfter = StorPoolConfigurationManager.DeleteAfterInterval.valueIn(dataStore.getId());
        if (deleteAfter != null && deleteAfter > 0 && vinfo.getPassphraseId() == null) {
            createTemporarySnapshot(vinfo, name, deleteAfter, conn);
        } else {
            StorPoolUtil.spLog("The volume [%s] is not marked to be snapshot. Check the global setting `storpool.delete.after.interval` or the volume is encrypted [%s]", name, deleteAfter, vinfo.getPassphraseId() != null);
        }
    }

    private void createTemporarySnapshot(VolumeInfo vinfo, String name, Integer deleteAfter, SpConnectionDesc conn) {
        Map<String, String> tags = new HashMap<>();
        tags.put("cs", StorPoolUtil.DELAY_DELETE);
        StorPoolSnapshotDef snapshot = new StorPoolSnapshotDef(name, deleteAfter, tags);
        StorPoolUtil.spLog("Creating backup snapshot before delete the volume [%s]", vinfo.getName());
        SpApiResponse snapshotResponse = StorPoolUtil.volumeSnapshot(snapshot, conn);
        if (snapshotResponse.getError() == null) {
            String snapshotName = StorPoolUtil.getSnapshotNameFromResponse(snapshotResponse, false, StorPoolUtil.GLOBAL_ID);
            String snapshotPath = StorPoolUtil.devPath(snapshotName);
            SnapshotVO snapshotVo = createSnapshotVo(vinfo, snapshotName);
            createSnapshotOnPrimaryVo(vinfo, snapshotVo, snapshotPath);
            SnapshotDetailsVO snapshotDetails = new SnapshotDetailsVO(snapshotVo.getId(), StorPoolUtil.SP_DELAY_DELETE, "~" + snapshotName, true);
            snapshotDetailsDao.persist(snapshotDetails);
        }
    }

    private void createSnapshotOnPrimaryVo(VolumeInfo vinfo, SnapshotVO snapshotVo, String snapshotPath) {
        SnapshotDataStoreVO snapshotOnPrimaryVo = new SnapshotDataStoreVO();
        snapshotOnPrimaryVo.setSnapshotId(snapshotVo.getId());
        snapshotOnPrimaryVo.setDataStoreId(vinfo.getDataCenterId());
        snapshotOnPrimaryVo.setRole(vinfo.getDataStore().getRole());
        snapshotOnPrimaryVo.setVolumeId(vinfo.getId());
        snapshotOnPrimaryVo.setSize(vinfo.getSize());
        snapshotOnPrimaryVo.setPhysicalSize(vinfo.getSize());
        snapshotOnPrimaryVo.setInstallPath(snapshotPath);
        snapshotOnPrimaryVo.setState(ObjectInDataStoreStateMachine.State.Ready);
        snapshotDataStoreDao.persist(snapshotOnPrimaryVo);
    }

    private SnapshotVO createSnapshotVo(VolumeInfo vinfo, String snapshotName) {
        SnapshotVO snapshotVo = new SnapshotVO(vinfo.getDataCenterId(), vinfo.getAccountId(), vinfo.getDomainId(), vinfo.getId(),
                vinfo.getDiskOfferingId(), snapshotName,
                (short)Snapshot.Type.RECURRING.ordinal(), Snapshot.Type.RECURRING.name(),
                vinfo.getSize(), vinfo.getMinIops(), vinfo.getMaxIops(), vinfo.getHypervisorType(), Snapshot.LocationType.PRIMARY);
        snapshotVo.setState(com.cloud.storage.Snapshot.State.BackedUp);
        snapshotVo = snapshotDao.persist(snapshotVo);
        return snapshotVo;
    }

    private void logDataObject(final String pref, DataObject data) {
        final DataStore dstore = data.getDataStore();
        String name = null;
        Long size = null;

        if (data.getType() == DataObjectType.VOLUME) {
            VolumeInfo vinfo = (VolumeInfo)data;
            name = vinfo.getName();
            size = vinfo.getSize();
        } else if (data.getType() == DataObjectType.SNAPSHOT) {
            SnapshotInfo sinfo = (SnapshotInfo)data;
            name = sinfo.getName();
            size = sinfo.getSize();
        } else if (data.getType() == DataObjectType.TEMPLATE) {
            TemplateInfo tinfo = (TemplateInfo)data;
            name = tinfo.getName();
            size = tinfo.getSize();
        }

        StorPoolUtil.spLog("%s: name=%s, size=%s, uuid=%s, type=%s, dstore=%s:%s:%s", pref, name, size, data.getUuid(), data.getType(), dstore.getUuid(), dstore.getName(), dstore.getRole());
    }

    @Override
    public boolean canCopy(DataObject srcData, DataObject dstData) {
        return true;
    }

    @Override
    public void copyAsync(DataObject srcData, DataObject dstData, AsyncCompletionCallback<CopyCommandResult> callback) {
        StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriverImpl.copyAsnc:");
        logDataObject("SRC", srcData);
        logDataObject("DST", dstData);

        final DataObjectType srcType = srcData.getType();
        final DataObjectType dstType = dstData.getType();
        String err = null;
        Answer answer = null;
        StorageSubSystemCommand cmd = null;

        try {
            if (srcType == DataObjectType.SNAPSHOT && dstType == DataObjectType.VOLUME) {
                SnapshotInfo sinfo = (SnapshotInfo)srcData;
                final String snapshotName = StorPoolHelper.getSnapshotName(srcData.getId(), srcData.getUuid(), snapshotDataStoreDao, snapshotDetailsDao);

                VolumeInfo vinfo = (VolumeInfo)dstData;
                final String volumeName = vinfo.getUuid();
                final Long size = vinfo.getSize();

                SpConnectionDesc conn = StorPoolUtil.getSpConnection(vinfo.getDataStore().getUuid(), vinfo.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);

                StorPoolVolumeDef spVolume = createVolumeWithTags(sinfo, snapshotName, vinfo, volumeName, size, conn);
                SpApiResponse resp = StorPoolUtil.volumeCreate(spVolume, conn);
                if (resp.getError() == null) {
                    updateStoragePool(dstData.getDataStore().getId(), size);

                    VolumeObjectTO to = (VolumeObjectTO)dstData.getTO();
                    to.setPath(StorPoolUtil.devPath(StorPoolUtil.getNameFromResponse(resp, false)));
                    to.setSize(size);

                    answer = new CopyCmdAnswer(to);
                    StorPoolUtil.spLog("Created volume=%s with uuid=%s from snapshot=%s with uuid=%s", StorPoolUtil.getNameFromResponse(resp, false), to.getUuid(), snapshotName, sinfo.getUuid());
                } else if (resp.getError().getName().equals("objectDoesNotExist")) {
                    //check if snapshot is on secondary storage
                    StorPoolUtil.spLog("Snapshot %s does not exists on StorPool, will try to create a volume from a snapshot on secondary storage", snapshotName);
                    SnapshotDataStoreVO snap = getSnapshotImageStoreRef(sinfo.getId(), vinfo.getDataCenterId());
                    SnapshotDetailsVO snapshotDetail = snapshotDetailsDao.findDetail(sinfo.getId(), StorPoolUtil.SP_DELAY_DELETE);
                    if (snapshotDetail != null) {
                        answer = new Answer(cmd, false, String.format("Could not create volume from snapshot due to: %s. The snapshot was created with the delayDelete option.", resp.getError()));
                    } else if (snap != null && StorPoolStorageAdaptor.getVolumeNameFromPath(snap.getInstallPath(), false) == null) {
                        spVolume.setParent(null);
                        SpApiResponse emptyVolumeCreateResp = StorPoolUtil.volumeCreate(spVolume, conn);
                        if (emptyVolumeCreateResp.getError() == null) {
                            answer = createVolumeFromSnapshot(srcData, dstData, size, emptyVolumeCreateResp);
                        } else {
                            answer = new Answer(cmd, false, String.format("Could not create Storpool volume %s from snapshot %s. Error: %s", volumeName, snapshotName, emptyVolumeCreateResp.getError()));
                        }
                    } else {
                        answer = new Answer(cmd, false, String.format("The snapshot %s does not exists neither on primary, neither on secondary storage. Cannot create volume from snapshot", snapshotName));
                    }
                } else {
                    answer = new Answer(cmd, false, String.format("Could not create Storpool volume %s from snapshot %s. Error: %s", volumeName, snapshotName, resp.getError()));
                }
            } else if (srcType == DataObjectType.SNAPSHOT && dstType == DataObjectType.SNAPSHOT) {
                SnapshotInfo sinfo = (SnapshotInfo)srcData;
                SnapshotDetailsVO snapshotDetail = snapshotDetailsDao.findDetail(sinfo.getId(), StorPoolUtil.SP_DELAY_DELETE);
                // bypass secondary storage
                if (StorPoolConfigurationManager.BypassSecondaryStorage.value() || snapshotDetail != null) {
                    SnapshotObjectTO snapshot = (SnapshotObjectTO) srcData.getTO();
                    answer = new CopyCmdAnswer(snapshot);
                } else {
                    // copy snapshot to secondary storage (backup snapshot)
                    cmd = new StorPoolBackupSnapshotCommand(srcData.getTO(), dstData.getTO(), StorPoolHelper.getTimeout(StorPoolHelper.BackupSnapshotWait, configDao), VirtualMachineManager.ExecuteInSequence.value());

                    final String snapName =  StorPoolStorageAdaptor.getVolumeNameFromPath(((SnapshotInfo) srcData).getPath(), true);
                    SpConnectionDesc conn = StorPoolUtil.getSpConnection(srcData.getDataStore().getUuid(), srcData.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);
                    try {
                        Long clusterId = StorPoolHelper.findClusterIdByGlobalId(snapName, clusterDao);
                        EndPoint ep = clusterId != null ? RemoteHostEndPoint.getHypervisorHostEndPoint(StorPoolHelper.findHostByCluster(clusterId, hostDao)) : selector.select(srcData, dstData);
                        if (ep == null) {
                            err = "No remote endpoint to send command, check if host or ssvm is down?";
                        } else {
                            answer = ep.sendMessage(cmd);
                            // if error during snapshot backup, cleanup the StorPool snapshot
                            if (answer != null && !answer.getResult()) {
                                StorPoolUtil.spLog(String.format("Error while backing-up snapshot '%s' - cleaning up StorPool snapshot. Error: %s", snapName, answer.getDetails()));
                                SpApiResponse resp = StorPoolUtil.snapshotDelete(snapName, conn);
                                if (resp.getError() != null) {
                                    final String err2 = String.format("Failed to cleanup StorPool snapshot '%s'. Error: %s.", snapName, resp.getError());
                                    logger.error(err2);
                                    StorPoolUtil.spLog(err2);
                                }
                            }
                        }
                    } catch (CloudRuntimeException e) {
                        err = e.getMessage();
                    }
                }
            } else if (srcType == DataObjectType.VOLUME && dstType == DataObjectType.TEMPLATE) {
                // create template from volume
                VolumeObjectTO volume = (VolumeObjectTO) srcData.getTO();
                TemplateObjectTO template = (TemplateObjectTO) dstData.getTO();
                SpConnectionDesc conn = StorPoolUtil.getSpConnection(srcData.getDataStore().getUuid(), srcData.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);

                String volumeName = StorPoolStorageAdaptor.getVolumeNameFromPath(volume.getPath(), true);


                cmd = new StorPoolBackupTemplateFromSnapshotCommand(volume, template,
                        StorPoolHelper.getTimeout(StorPoolHelper.PrimaryStorageDownloadWait, configDao), VirtualMachineManager.ExecuteInSequence.value());

                try {
                    Long clusterId = StorPoolHelper.findClusterIdByGlobalId(volumeName, clusterDao);
                    EndPoint ep2 = clusterId != null ? RemoteHostEndPoint.getHypervisorHostEndPoint(StorPoolHelper.findHostByCluster(clusterId, hostDao)) : selector.select(srcData, dstData);
                    if (ep2 == null) {
                        err = "No remote endpoint to send command, check if host or ssvm is down?";
                    } else {
                        answer = ep2.sendMessage(cmd);
                        if (answer != null && answer.getResult()) {
                            SpApiResponse resSnapshot = StorPoolUtil.volumeSnapshot(volumeName, template.getUuid(), null, "template", "no", conn);
                            if (resSnapshot.getError() != null) {
                                logger.debug(String.format("Could not snapshot volume with ID=%s", volume.getId()));
                                StorPoolUtil.spLog("Volume snapshot failed with error=%s", resSnapshot.getError().getDescr());
                                err = resSnapshot.getError().getDescr();
                            }
                            else {
                                StorPoolHelper.updateVmStoreTemplate(template.getId(), template.getDataStore().getRole(), StorPoolUtil.devPath(StorPoolUtil.getSnapshotNameFromResponse(resSnapshot, false, StorPoolUtil.GLOBAL_ID)), vmTemplateDataStoreDao);
                                vmTemplateDetailsDao.persist(new VMTemplateDetailVO(template.getId(), StorPoolUtil.SP_STORAGE_POOL_ID, String.valueOf(srcData.getDataStore().getId()), false));
                            }
                        }else {
                            err = "Could not copy template to secondary " + answer.getResult();
                        }
                    }
                }catch (CloudRuntimeException e) {
                    err = e.getMessage();
                }
            } else if (srcType == DataObjectType.TEMPLATE && dstType == DataObjectType.TEMPLATE) {
                // copy template to primary storage
                TemplateInfo tinfo = (TemplateInfo)dstData;
                Long size = tinfo.getSize();
                if(size == null || size == 0)
                    size = 1L*1024*1024*1024;
                SpConnectionDesc conn = StorPoolUtil.getSpConnection(dstData.getDataStore().getUuid(), dstData.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);

                TemplateDataStoreVO templDataStoreVO = vmTemplateDataStoreDao.findByTemplate(tinfo.getId(), DataStoreRole.Image);

                String snapshotName = (templDataStoreVO != null && templDataStoreVO.getLocalDownloadPath() != null)
                        ? StorPoolStorageAdaptor.getVolumeNameFromPath(templDataStoreVO.getLocalDownloadPath(), true)
                        : null;
                String name = tinfo.getUuid();

                SpApiResponse resp = null;
                if (snapshotName != null) {
                    //no need to copy volume from secondary, because we have it already on primary. Just need to create a child snapshot from it.
                    //The child snapshot is needed when configuration "storage.cleanup.enabled" is true, not to clean the base snapshot and to lose everything
                    resp = StorPoolUtil.volumeCreate(name, snapshotName, size, null, "no", "template", null, conn);
                    if (resp.getError() != null) {
                        err = String.format("Could not create Storpool volume for CS template %s. Error: %s", name, resp.getError());
                    } else {
                        String volumeNameToSnapshot = StorPoolUtil.getNameFromResponse(resp, true);
                        TemplateObjectTO dstTO = (TemplateObjectTO) dstData.getTO();

                        answer = createVolumeSnapshot(cmd, size, conn, volumeNameToSnapshot, dstTO);
                        StorPoolUtil.volumeDelete(volumeNameToSnapshot, conn);
                    }
                } else {
                    resp = StorPoolUtil.volumeCreate(name, null, size, null, "no", "template", null, conn);
                    if (resp.getError() != null) {
                        err = String.format("Could not create Storpool volume for CS template %s. Error: %s", name, resp.getError());
                    } else {
                        String volName = StorPoolUtil.getNameFromResponse(resp, true);
                        TemplateObjectTO dstTO = (TemplateObjectTO)dstData.getTO();
                        dstTO.setPath(StorPoolUtil.devPath(StorPoolUtil.getNameFromResponse(resp, false)));
                        dstTO.setSize(size);

                        cmd = new StorPoolDownloadTemplateCommand(srcData.getTO(), dstTO, StorPoolHelper.getTimeout(StorPoolHelper.PrimaryStorageDownloadWait, configDao), VirtualMachineManager.ExecuteInSequence.value(), "volume");

                        EndPoint ep = selector.select(srcData, dstData);
                        if (ep == null) {
                            err = "No remote endpoint to send command, check if host or ssvm is down?";
                        } else {
                            answer = ep.sendMessage(cmd);
                        }

                        if (answer != null && answer.getResult()) {
                            // successfully downloaded template to primary storage
                            TemplateObjectTO templ = (TemplateObjectTO) ((CopyCmdAnswer) answer).getNewData();
                            answer = createVolumeSnapshot(cmd, size, conn, volName, templ);
                        } else {
                            err = answer != null ? answer.getDetails() : "Unknown error while downloading template. Null answer returned.";
                        }

                        StorPoolUtil.volumeDelete(volName, conn);
                    }
                }
            } else if (srcType == DataObjectType.TEMPLATE && dstType == DataObjectType.VOLUME) {
                // create volume from template on Storpool PRIMARY
                TemplateInfo tinfo = (TemplateInfo)srcData;

                VolumeInfo vinfo = (VolumeInfo) dstData;
                VMTemplateStoragePoolVO templStoragePoolVO = StorPoolHelper.findByPoolTemplate(vinfo.getPoolId(),
                        tinfo.getId());
                final String parentName = templStoragePoolVO.getLocalDownloadPath() != null
                        ? StorPoolStorageAdaptor.getVolumeNameFromPath(templStoragePoolVO.getLocalDownloadPath(), true)
                        : StorPoolStorageAdaptor.getVolumeNameFromPath(templStoragePoolVO.getInstallPath(), true);
                final String name = vinfo.getUuid();
                SpConnectionDesc conn = StorPoolUtil.getSpConnection(vinfo.getDataStore().getUuid(),
                        vinfo.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);

                Long snapshotSize = templStoragePoolVO.getTemplateSize();
                boolean withoutEncryption = vinfo.getPassphraseId() == null;
                long size = withoutEncryption ? vinfo.getSize() : vinfo.getSize() + 2097152;
                if (snapshotSize != null && size < snapshotSize) {
                    StorPoolUtil.spLog(String.format("provided size is too small for snapshot. Provided %d, snapshot %d. Using snapshot size", size, snapshotSize));
                    size = withoutEncryption ? snapshotSize : snapshotSize + 2097152;
                }
                StorPoolUtil.spLog(String.format("volume size is: %d", size));
                Long vmId = vinfo.getInstanceId();

                String template = null;
                String tier = null;
                SpApiResponse resp = new SpApiResponse();

                if (vinfo.getDiskOfferingId() != null) {
                    tier = getTierFromOfferingDetail(vinfo.getDiskOfferingId());
                    if (tier == null) {
                        template = getTemplateFromOfferingDetail(vinfo.getDiskOfferingId());
                    }
                    StorPoolUtil.spLog(
                            "Creating volume [%s] with template [%s] or tier tags [%s] described in disk/service offerings details",
                            vinfo.getUuid(), template, tier);
                }

                Map<String, String> tags = StorPoolHelper.addStorPoolTags(name, getVMInstanceUUID(vmId), "volume", getVcPolicyTag(vmId), tier);

                if (vinfo.getDeviceId() != null) {
                    tags.put("disk", vinfo.getDeviceId().toString());
                }

                if (template == null) {
                    template = conn.getTemplateName();
                }

                StorPoolVolumeDef volumeDef = new StorPoolVolumeDef(null, size, tags, parentName, null, template, null, null, null);
                resp = StorPoolUtil.volumeCreate(volumeDef, conn);

                if (resp.getError() == null) {
                    updateStoragePool(dstData.getDataStore().getId(), vinfo.getSize());
                    updateVolumePoolType(vinfo);

                    if (withoutEncryption) {
                        VolumeObjectTO to = updateVolumeObjectTO(vinfo, resp);
                        answer = new CopyCmdAnswer(to);
                    } else {
                        VolumeObjectTO volume = updateVolumeObjectTO(vinfo, resp);
                        String snapshotPath = StorPoolUtil.devPath(parentName.split("~")[1]);
                        answer = createEncryptedVolume(dstData.getDataStore(), dstData, vinfo, size, volume, snapshotPath, false);
                        if (answer.getResult()) {
                             answer = new CopyCmdAnswer(((StorPoolSetVolumeEncryptionAnswer) answer).getVolume());
                        }
                    }
                } else {
                    err = String.format("Could not create Storpool volume %s. Error: %s", name, resp.getError());
                }
            } else if (srcType == DataObjectType.VOLUME && dstType == DataObjectType.VOLUME) {
                StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriver.copyAsync src Data Store=%s", srcData.getDataStore().getDriver());
                VolumeInfo dstInfo = (VolumeInfo)dstData;
                VolumeInfo srcInfo = (VolumeInfo) srcData;

                if( !(srcData.getDataStore().getDriver() instanceof StorPoolPrimaryDataStoreDriver ) ) {
                    // copy "VOLUME" to primary storage
                    String name = dstInfo.getUuid();
                    Long size = dstInfo.getSize();
                    if(size == null || size == 0)
                        size = 1L*1024*1024*1024;
                    SpConnectionDesc conn = StorPoolUtil.getSpConnection(dstData.getDataStore().getUuid(), dstData.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);
                    Long vmId = srcInfo.getInstanceId();

                    SpApiResponse resp = StorPoolUtil.volumeCreate(name, null, size, getVMInstanceUUID(vmId), getVcPolicyTag(vmId), "volume", dstInfo.getMaxIops(), conn);
                    if (resp.getError() != null) {
                        err = String.format("Could not create Storpool volume for CS template %s. Error: %s", name, resp.getError());
                    } else {
                        //updateVolume(dstData.getId());
                        VolumeObjectTO dstTO = (VolumeObjectTO)dstData.getTO();
                        dstTO.setPath(StorPoolUtil.devPath(StorPoolUtil.getNameFromResponse(resp, false)));
                        dstTO.setSize(size);

                        cmd = new StorPoolDownloadVolumeCommand(srcData.getTO(), dstTO, StorPoolHelper.getTimeout(StorPoolHelper.PrimaryStorageDownloadWait, configDao), VirtualMachineManager.ExecuteInSequence.value());

                        EndPoint ep = selector.select(srcData, dstData);

                        if (ep == null || storagePoolHostDao.findByPoolHost(dstData.getId(), ep.getId()) == null) {
                            StorPoolUtil.spLog("select(srcData, dstData) returned NULL or the destination pool is not connected to the selected host. Trying dstData");
                            ep = selector.select(dstData); // Storpool is zone
                        }
                        if (ep == null) {
                            err = "No remote endpoint to send command, check if host or ssvm is down?";
                        } else {
                            StorPoolUtil.spLog("Sending command to %s", ep.getHostAddr());
                            answer = ep.sendMessage(cmd);

                            if (answer != null && answer.getResult()) {
                                // successfully downloaded volume to primary storage
                            } else {
                                err = answer != null ? answer.getDetails() : "Unknown error while downloading volume. Null answer returned.";
                            }
                        }

                        if (err != null) {
                            SpApiResponse resp3 = StorPoolUtil.volumeDelete(name, conn);
                            if (resp3.getError() != null) {
                               logger.warn(String.format("Could not clean-up Storpool volume %s. Error: %s", name, resp3.getError()));
                            }
                        }
                    }
                } else {
                    // download volume - first copies to secondary
                    VolumeObjectTO srcTO = (VolumeObjectTO)srcData.getTO();
                    StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriverImpl.copyAsnc SRC path=%s DST canonicalName=%s ", srcTO.getPath(), dstData.getDataStore().getClass().getCanonicalName());
                    PrimaryDataStoreTO checkStoragePool = dstData.getTO().getDataStore() instanceof PrimaryDataStoreTO ? (PrimaryDataStoreTO)dstData.getTO().getDataStore() : null;
                    final String volumeName = StorPoolStorageAdaptor.getVolumeNameFromPath(srcTO.getPath(), true);

                    if (checkStoragePool != null && checkStoragePool.getPoolType().equals(StoragePoolType.StorPool)) {
                        answer = migrateVolumeToStorPool(srcData, dstData, srcInfo, srcTO, volumeName);
                    } else {
                        SpConnectionDesc conn = StorPoolUtil.getSpConnection(srcData.getDataStore().getUuid(), srcData.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);
                        final SpApiResponse resp = StorPoolUtil.volumeSnapshot(volumeName, srcTO.getUuid(), srcInfo.getInstanceId() != null ? getVMInstanceUUID(srcInfo.getInstanceId()) : null, "temporary", null, conn);
                        String snapshotName = StorPoolUtil.getSnapshotNameFromResponse(resp, true, StorPoolUtil.GLOBAL_ID);
                        if (resp.getError() == null) {
                            srcTO.setPath(StorPoolUtil.devPath(
                                    StorPoolUtil.getSnapshotNameFromResponse(resp, false, StorPoolUtil.GLOBAL_ID)));

                            cmd = new StorPoolCopyVolumeToSecondaryCommand(srcTO, dstData.getTO(), StorPoolHelper.getTimeout(StorPoolHelper.CopyVolumeWait, configDao), VirtualMachineManager.ExecuteInSequence.value());

                            StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriverImpl.copyAsnc command=%s ", cmd);

                            try {
                                Long clusterId = StorPoolHelper.findClusterIdByGlobalId(snapshotName, clusterDao);
                                EndPoint ep = clusterId != null ? RemoteHostEndPoint.getHypervisorHostEndPoint(StorPoolHelper.findHostByCluster(clusterId, hostDao)) : selector.select(srcData, dstData);
                                StorPoolUtil.spLog("selector.select(srcData, dstData) ", ep);
                                if (ep == null) {
                                    ep = selector.select(dstData);
                                    StorPoolUtil.spLog("selector.select(srcData) ", ep);
                                }

                                if (ep == null) {
                                    err = "No remote endpoint to send command, check if host or ssvm is down?";
                                } else {
                                    answer = ep.sendMessage(cmd);
                                    StorPoolUtil.spLog("Answer: details=%s, result=%s", answer.getDetails(), answer.getResult());
                                }
                            } catch (CloudRuntimeException e) {
                                err = e.getMessage();
                            }
                        } else {
                            err = String.format("Failed to create temporary StorPool snapshot while trying to download volume %s (uuid %s). Error: %s", srcTO.getName(), srcTO.getUuid(), resp.getError());
                        }
                        final SpApiResponse resp2 = StorPoolUtil.snapshotDelete(snapshotName, conn);
                        if (resp2.getError() != null) {
                            final String err2 = String.format("Failed to delete temporary StorPool snapshot %s. Error: %s", StorPoolUtil.getNameFromResponse(resp, true), resp2.getError());
                            logger.error(err2);
                            StorPoolUtil.spLog(err2);
                        }
                    }
                }
            } else {
                err = String.format("Unsupported copy operation from %s (type %s) to %s (type %s)", srcData.getUuid(), srcType, dstData.getUuid(), dstType);
            }
        } catch (Exception e) {
            StorPoolUtil.spLog("Caught exception: %s", e.toString());
            err = e.toString();
        }

        if (answer != null && !answer.getResult()) {
            err = answer.getDetails();
        }

        if (err != null) {
            StorPoolUtil.spLog("Failed due to %s", err);

            logger.error(err);
            answer = new Answer(cmd, false, err);
        }

        CopyCommandResult res = new CopyCommandResult(null, answer);
        res.setResult(err);
        callback.complete(res);
    }

    private StorPoolVolumeDef createVolumeWithTags(SnapshotInfo sinfo, String snapshotName, VolumeInfo vinfo, String volumeName, Long size, SpConnectionDesc conn) {
        Pair<String, String> templateAndTier = getTemplateAndTier(vinfo, conn);
        Map<String, String> tags = StorPoolHelper.addStorPoolTags(volumeName, getVMInstanceUUID(vinfo.getInstanceId()), "volume", getVcPolicyTag(vinfo.getInstanceId()), templateAndTier.first());
        return new StorPoolVolumeDef(null, size, tags, snapshotName, sinfo.getBaseVolume().getMaxIops(), templateAndTier.second(), null, null, null);
    }

    private Pair<String, String> getTemplateAndTier(VolumeInfo vinfo, SpConnectionDesc conn) {
        String tier = null;
        String template = null;
        if (vinfo.getDiskOfferingId() != null) {
            tier = getTierFromOfferingDetail(vinfo.getDiskOfferingId());
            if (tier == null) {
                template = getTemplateFromOfferingDetail(vinfo.getDiskOfferingId());
            }
        }

        if (template == null) {
            template = conn.getTemplateName();
        }
        return new Pair<>(tier, template);
    }
    private Answer createVolumeSnapshot(StorageSubSystemCommand cmd, Long size, SpConnectionDesc conn,
            String volName, TemplateObjectTO dstTO) {
        Answer answer;
        SpApiResponse resp = StorPoolUtil.volumeSnapshot(volName, dstTO.getUuid(), null, "template", null, conn);
        if (resp.getError() != null) {
            answer = new Answer(cmd, false, String.format("Could not snapshot volume. Error: %s", resp.getError()));
        } else {
            dstTO.setPath(StorPoolUtil.devPath(
                    StorPoolUtil.getSnapshotNameFromResponse(resp, false, StorPoolUtil.GLOBAL_ID)));
            answer = new CopyCmdAnswer(dstTO);
        }
        return answer;
    }

    private Answer createVolumeFromSnapshot(DataObject srcData, DataObject dstData, final Long size,
            SpApiResponse emptyVolumeCreateResp) {
        Answer answer;
        String name = StorPoolUtil.getNameFromResponse(emptyVolumeCreateResp, false);
        VolumeObjectTO dstTO = (VolumeObjectTO) dstData.getTO();
        dstTO.setSize(size);
        dstTO.setPath(StorPoolUtil.devPath(name));
        StorageSubSystemCommand cmd = new StorPoolDownloadTemplateCommand(srcData.getTO(), dstTO, StorPoolHelper.getTimeout(StorPoolHelper.PrimaryStorageDownloadWait, configDao), VirtualMachineManager.ExecuteInSequence.value(), "volume");

        EndPoint ep = selector.select(srcData, dstData);
        if (ep == null) {
            answer = new Answer(cmd, false, "\"No remote endpoint to send command, check if host or ssvm is down?\"");
        } else {
            answer = ep.sendMessage(cmd);
        }
        if (answer == null || !answer.getResult()) {
            answer = new Answer(cmd, false, answer != null ? answer.getDetails() : "Unknown error while downloading template. Null answer returned.");
        }
        return answer;
    }

    private void updateVolumePoolType(VolumeInfo vinfo) {
        VolumeVO volumeVO = volumeDao.findById(vinfo.getId());
        volumeVO.setPoolType(StoragePoolType.StorPool);
        volumeDao.update(volumeVO.getId(), volumeVO);
    }

    private VolumeObjectTO updateVolumeObjectTO(VolumeInfo vinfo, SpApiResponse resp) {
        VolumeObjectTO to = (VolumeObjectTO) vinfo.getTO();
        to.setSize(vinfo.getSize());
        to.setPath(StorPoolUtil.devPath(StorPoolUtil.getNameFromResponse(resp, false)));
        return to;
    }

    /**
     * Live migrate/copy volume from one StorPool storage to another
     * @param srcData The source volume data
     * @param dstData The destination volume data
     * @param srcInfo The source volume info
     * @param srcTO The source Volume TO
     * @param volumeName The name of the volume
     * @return Answer
     */
    private Answer migrateVolumeToStorPool(DataObject srcData, DataObject dstData, VolumeInfo srcInfo,
            VolumeObjectTO srcTO, final String volumeName) {
        Answer answer;
        SpConnectionDesc conn = StorPoolUtil.getSpConnection(dstData.getDataStore().getUuid(), dstData.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);
        String baseOn = StorPoolStorageAdaptor.getVolumeNameFromPath(srcTO.getPath(), true);

        String vmUuid = null;
        String vcPolicyTag = null;

        VMInstanceVO vm = null;
        if (srcInfo.getInstanceId() != null) {
            vm = vmInstanceDao.findById(srcInfo.getInstanceId());
        }

        if (vm != null) {
            vmUuid = vm.getUuid();
            vcPolicyTag = getVcPolicyTag(vm.getId());
        }

        if (vm != null && vm.getState().equals(State.Running)) {
            answer = migrateVolume(srcData, dstData, volumeName, conn);
        } else {
            answer = copyVolume(srcInfo, srcTO, conn, baseOn, vmUuid, vcPolicyTag);
        }
        return answer;
    }

    /**
     * Copy the volume from StorPool primary storage to another StorPool primary storage
     * @param srcInfo The source volume info
     * @param srcTO The source Volume TO
     * @param conn StorPool connection
     * @param baseOn The name of an already existing volume that the new volume is to be a copy of.
     * @param vmUuid The UUID of the VM
     * @param vcPolicyTag The VC policy tag
     * @return Answer
     */
    private Answer copyVolume(VolumeInfo srcInfo, VolumeObjectTO srcTO, SpConnectionDesc conn, String baseOn, String vmUuid, String vcPolicyTag) {
        //uuid tag will be the same as srcData.uuid
        String volumeName = srcInfo.getUuid();
        Long iops = (srcInfo.getMaxIops() != null && srcInfo.getMaxIops().longValue() > 0) ? srcInfo.getMaxIops() : null;
        SpApiResponse response = StorPoolUtil.volumeCopy(volumeName, baseOn, "volume", iops, vmUuid, vcPolicyTag, conn);
        if (response.getError() != null) {
            return new CopyCmdAnswer(String.format("Could not copy volume [%s] due to %s", baseOn, response.getError()));
        }
        String newVolume = StorPoolUtil.getNameFromResponse(response, false);

        StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriverImpl.copyAsnc copy volume[%s] from pool[%s] with a new name [%s]",
                baseOn, srcInfo.getDataStore().getName(), newVolume);

        srcTO.setSize(srcInfo.getSize());
        srcTO.setPath(StorPoolUtil.devPath(newVolume));

        return new CopyCmdAnswer(srcTO);
    }

    /**
     * Live migrate the StorPool's volume to another StorPool template
     * @param srcData The source data volume
     * @param dstData The destination data volume
     * @param name The volume's name
     * @param conn StorPool's connection
     * @return Answer
     */
    private Answer migrateVolume(DataObject srcData, DataObject dstData, String name, SpConnectionDesc conn) {
        Answer answer;
        SpApiResponse resp = StorPoolUtil.volumeUpdateTemplate(name, conn);
        if (resp.getError() != null) {
            answer = new Answer(null, false, String.format("Could not migrate volume %s to %s due to %s", name, conn.getTemplateName(), resp.getError()));
        } else {
            StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriverImpl.copyAsnc migrate volume[%s] from pool[%s] to pool[%s]",
                    name, srcData.getDataStore().getName(),dstData.getDataStore().getName());
            VolumeVO updatedVolume = volumeDao.findById(srcData.getId());
            updatedVolume.setPoolId(dstData.getDataStore().getId());
            updatedVolume.setLastPoolId(srcData.getDataStore().getId());
            volumeDao.update(updatedVolume.getId(), updatedVolume);
            answer = new Answer(null, true, null);
        }
        return answer;
    }

    @Override
    public void takeSnapshot(SnapshotInfo snapshot, AsyncCompletionCallback<CreateCmdResult> callback) {
        String snapshotName = snapshot.getUuid();
        VolumeInfo vinfo = snapshot.getBaseVolume();
        String volumeName = StorPoolStorageAdaptor.getVolumeNameFromPath(vinfo.getPath(), true);
        Long vmId = vinfo.getInstanceId();
        if (volumeName != null) {
            StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriver.takeSnapshot volumename=%s vmInstance=%s",volumeName, vmId);
        } else {
            throw new UnsupportedOperationException("The path should be: " + StorPoolUtil.SP_DEV_PATH);
        }

        CreateObjectAnswer answer = null;
        String err = null;

        try {
            SpConnectionDesc conn = StorPoolUtil.getSpConnection(vinfo.getDataStore().getUuid(), vinfo.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);

            SpApiResponse resp = StorPoolUtil.volumeSnapshot(volumeName, snapshotName, vmId != null ? getVMInstanceUUID(vmId) : null, "snapshot", null, conn);

            if (resp.getError() != null) {
                err = String.format("Could not snapshot StorPool volume %s. Error %s", volumeName, resp.getError());
                answer = new CreateObjectAnswer(err);
            } else {
                String name = StorPoolUtil.getSnapshotNameFromResponse(resp, true, StorPoolUtil.GLOBAL_ID);
                SnapshotObjectTO snapTo = (SnapshotObjectTO)snapshot.getTO();
                snapTo.setPath(StorPoolUtil.devPath(name.split("~")[1]));
                answer = new CreateObjectAnswer(snapTo);
                StorPoolHelper.addSnapshotDetails(snapshot.getId(), snapshot.getUuid(), snapTo.getPath(), snapshotDetailsDao);
                //add primary storage of snapshot
                StorPoolHelper.addSnapshotDetails(snapshot.getId(), StorPoolUtil.SP_STORAGE_POOL_ID, String.valueOf(snapshot.getDataStore().getId()), snapshotDetailsDao);
                StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriverImpl.takeSnapshot: snapshot: name=%s, uuid=%s, volume: name=%s, uuid=%s", name, snapshot.getUuid(), volumeName, vinfo.getUuid());
            }
        } catch (Exception e) {
            err = String.format("Could not take volume snapshot due to %s", e.getMessage());
        }

        CreateCmdResult res = new CreateCmdResult(null, answer);
        res.setResult(err);
        callback.complete(res);
    }

    @Override
    public void revertSnapshot(final SnapshotInfo snapshot, final SnapshotInfo snapshotOnPrimaryStore, final AsyncCompletionCallback<CommandResult> callback) {
        final VolumeInfo vinfo = snapshot.getBaseVolume();
        final String snapshotName = StorPoolHelper.getSnapshotName(snapshot.getId(), snapshot.getUuid(), snapshotDataStoreDao, snapshotDetailsDao);
        final String volumeName = StorPoolStorageAdaptor.getVolumeNameFromPath(vinfo.getPath(), true);
        StorPoolUtil.spLog("StorpoolPrimaryDataStoreDriverImpl.revertSnapshot: snapshot: name=%s, uuid=%s, volume: name=%s, uuid=%s", snapshotName, snapshot.getUuid(), volumeName, vinfo.getUuid());
        String err = null;

        SpConnectionDesc conn = null;
        try {
            conn = StorPoolUtil.getSpConnection(vinfo.getDataStore().getUuid(), vinfo.getDataStore().getId(), storagePoolDetailsDao, primaryStoreDao);
        } catch (Exception e) {
            err = String.format("Could not revert volume due to %s", e.getMessage());
            completeResponse(err, callback);
            return;
        }

        VolumeDetailVO detail = volumeDetailsDao.findDetail(vinfo.getId(), StorPoolUtil.SP_PROVIDER_NAME);
        if (detail != null) {
            //Rename volume to its global id only if it was migrated from UUID to global id
            SpApiResponse updateVolumeResponse = StorPoolUtil.volumeUpdateRename(StorPoolStorageAdaptor.getVolumeNameFromPath(vinfo.getPath(), true), "", StorPoolStorageAdaptor.getVolumeNameFromPath(detail.getValue(), false), conn);

            if (updateVolumeResponse.getError() != null) {
                StorPoolUtil.spLog("Could not update StorPool's volume %s to it's globalId due to %s", StorPoolStorageAdaptor.getVolumeNameFromPath(vinfo.getPath(), true), updateVolumeResponse.getError().getDescr());
                err = String.format("Could not update StorPool's volume %s to it's globalId due to %s", StorPoolStorageAdaptor.getVolumeNameFromPath(vinfo.getPath(), true), updateVolumeResponse.getError().getDescr());
                completeResponse(err, callback);
                return;
            }
            volumeDetailsDao.remove(detail.getId());
        }

        SpApiResponse resp = StorPoolUtil.detachAllForced(volumeName, false, conn);
        if (resp.getError() != null) {
            err = String.format("Could not detach StorPool volume %s due to %s", volumeName, resp.getError());
            completeResponse(err, callback);
            return;
        }
        SpApiResponse response = StorPoolUtil.volumeRevert(volumeName, snapshotName, conn);
        if (response.getError() != null) {
            err = String.format(
                    "Could not revert StorPool volume %s to the %s snapshot: could not create the new volume: error %s",
                    volumeName, snapshotName, response.getError());
            completeResponse(err, callback);
            return;
        }

        if (vinfo.getMaxIops() != null) {
            response = StorPoolUtil.volumeUpdateIopsAndTags(volumeName, null, vinfo.getMaxIops(), conn, null);
            if (response.getError() != null) {
                StorPoolUtil.spLog("Volume was reverted successfully but max iops could not be set due to %s", response.getError().getDescr());
            }
        }

        final VolumeObjectTO to = (VolumeObjectTO)vinfo.getTO();
        completeResponse(to, callback);
    }

    private String getVcPolicyTag(Long vmId) {
        ResourceTag resourceTag = vmId != null ? resourceTagDao.findByKey(vmId, ResourceObjectType.UserVm, StorPoolUtil.SP_VC_POLICY) : null;
        return resourceTag != null ? resourceTag.getValue() : "";
    }

    public void handleQualityOfServiceForVolumeMigration(VolumeInfo arg0, QualityOfServiceState arg1) {
        logger.debug(String.format("handleQualityOfServiceForVolumeMigration with volume name=%s is not supported", arg0.getName()));
    }


    public void copyAsync(DataObject srcData, DataObject destData, Host destHost,
            AsyncCompletionCallback<CopyCommandResult> callback) {
        copyAsync(srcData, destData, callback);
    }

    public boolean canProvideStorageStats() {
        return StorPoolConfigurationManager.StorageStatsInterval.value() > 0;
    }

    public Pair<Long, Long> getStorageStats(StoragePool storagePool) {
        if (storagePool == null) {
            return null;
        }
        Map<Long, Map<String, Pair<Long, Long>>> templatesStats = StorPoolStatsCollector.templatesStats;
        if (MapUtils.isNotEmpty(templatesStats) && templatesStats.containsKey(storagePool.getDataCenterId())) {
            Map<String, Pair<Long, Long>> storageStats = templatesStats.get(storagePool.getDataCenterId());
            StoragePoolDetailVO templateName = storagePoolDetailsDao.findDetail(storagePool.getId(), StorPoolUtil.SP_TEMPLATE);
            if (storageStats.containsKey(templateName.getValue()) && templateName != null) {
                Pair<Long, Long> stats = storageStats.get(templateName.getValue());
                if (stats.first() != storagePool.getCapacityBytes()) {
                    primaryStoreDao.updateCapacityBytes(storagePool.getId(), stats.first());
                }
                return storageStats.get(templateName.getValue());
            }
        }
        return null;
    }

    public boolean canProvideVolumeStats() {
        return StorPoolConfigurationManager.VolumesStatsInterval.value() > 0;
    }

    public Pair<Long, Long> getVolumeStats(StoragePool storagePool, String volumeId) {

        if (volumeId == null) {
            return null;
        }

        Map<String, Pair<Long, Long>> volumesStats = StorPoolStatsCollector.volumesStats;
        if (MapUtils.isNotEmpty(volumesStats)) {
            Pair<Long, Long> volumeStats = volumesStats.get(StorPoolStorageAdaptor.getVolumeNameFromPath(volumeId, true));
            if (volumeStats != null) {
                return volumeStats;
            }
        } else {
            List<VolumeVO> volumes = volumeDao.findByPoolId(storagePool.getId());
            for (VolumeVO volume : volumes) {
                if (volume.getPath() != null && volume.getPath().equals(volumeId)) {
                    long size = volume.getSize();
                    StorPoolUtil.spLog("Volume [%s] doesn't have any statistics, returning its size [%s]", volumeId, size);
                    return new Pair<>(size, size);
                }
            }
        }
        return null;
    }

    public boolean canHostAccessStoragePool(Host host, StoragePool pool) {
        return false;
    }

    @Override
    public boolean isVmInfoNeeded() {
        return true;
    }

    @Override
    public void provideVmInfo(long vmId, long volumeId) {
        VolumeVO volume = volumeDao.findById(volumeId);
        if (volume.getInstanceId() == null) {
            return;
        }
        StoragePoolVO poolVO = primaryStoreDao.findById(volume.getPoolId());
        if (poolVO != null && StoragePoolType.StorPool.equals(poolVO.getPoolType())) {
            VolumeInfo vInfo = volumeDataFactory.getVolume(volumeId);
            if (vInfo == null) {
                StorPoolUtil.spLog("Could not find volume with volume ID [%s] to set tags", volumeId);
                return;
            }
            updateVolumeWithTags(poolVO, vInfo);
        }
    }

    private void updateVolumeWithTags(StoragePoolVO poolVO, VolumeInfo vInfo) {
        try {
            SpConnectionDesc conn = StorPoolUtil.getSpConnection(poolVO.getUuid(), poolVO.getId(), storagePoolDetailsDao, primaryStoreDao);
            String volName = StorPoolStorageAdaptor.getVolumeNameFromPath(vInfo.getPath(), true);
            Pair<String, String> templateAndTier = getTemplateAndTier(vInfo, conn);
            Map<String, String> tags = StorPoolHelper.addStorPoolTags(volName, getVMInstanceUUID(vInfo.getInstanceId()), "volume", getVcPolicyTag(vInfo.getInstanceId()), templateAndTier.first());
            if (vInfo.getDeviceId() != null) {
                tags.put("disk", vInfo.getDeviceId().toString());
            }
            StorPoolVolumeDef spVolume = new StorPoolVolumeDef(volName, null, tags, null, null, templateAndTier.second(), null, null, null);
            StorPoolUtil.spLog("Updating volume's tags [%s] with template [%s]", tags, templateAndTier.second());
            SpApiResponse resp = StorPoolUtil.volumeUpdate(spVolume, conn);
            if (resp.getError() != null) {
                logger.warn(String.format("Could not update VC policy tags of a volume with id [%s]", vInfo.getUuid()));
            }
        } catch (Exception e) {
            logger.warn(String.format("Could not update Virtual machine tags due to %s", e.getMessage()));
        }
    }

    @Override
    public boolean isVmTagsNeeded(String tagKey) {
        return tagKey != null && tagKey.equals(StorPoolUtil.SP_VC_POLICY);
    }

    @Override
    public void provideVmTags(long vmId, long volumeId, String tagValue) {
        VolumeVO volume = volumeDao.findById(volumeId);
        StoragePoolVO poolVO = primaryStoreDao.findById(volume.getPoolId());
        if (poolVO != null) {
            try {
                SpConnectionDesc conn = StorPoolUtil.getSpConnection(poolVO.getUuid(), poolVO.getId(), storagePoolDetailsDao, primaryStoreDao);
                String volName = StorPoolStorageAdaptor.getVolumeNameFromPath(volume.getPath(), true);
                SpApiResponse resp = StorPoolUtil.volumeUpdateVCTags(volName, conn, getVcPolicyTag(vmId));
                if (resp.getError() != null) {
                    logger.warn(String.format("Could not update VC policy tags of a volume with id [%s]", volume.getUuid()));
                }
            } catch (Exception e) {
                logger.warn(String.format("Could not update Virtual machine tags due to %s", e.getMessage()));
            }
        }
    }

    @Override
    public boolean isStorageSupportHA(StoragePoolType type) {
        return true;
    }

    @Override
    public void detachVolumeFromAllStorageNodes(Volume volume) {
        StoragePoolVO poolVO = primaryStoreDao.findById(volume.getPoolId());
        if (poolVO != null) {
            SpConnectionDesc conn = StorPoolUtil.getSpConnection(poolVO.getUuid(), poolVO.getId(), storagePoolDetailsDao, primaryStoreDao);
            String volName = StorPoolStorageAdaptor.getVolumeNameFromPath(volume.getPath(), true);
            SpApiResponse resp = StorPoolUtil.detachAllForced(volName, false, conn);
            StorPoolUtil.spLog("The volume [%s] is detach from all clusters [%s]", volName, resp);
        }
    }

    @Override
    public boolean informStorageForDiskOfferingChange() {
        return true;
    }

    @Override
    public void updateStorageWithTheNewDiskOffering(Volume volume, DiskOffering newDiskOffering) {
        if (newDiskOffering == null) {
            return;
        }

        StoragePoolVO pool = primaryStoreDao.findById(volume.getPoolId());
        if (pool == null) {
            return;
        }

        String tier = getTierFromOfferingDetail(newDiskOffering.getId());
        String template = null;
        if (tier == null) {
            template = getTemplateFromOfferingDetail(newDiskOffering.getId());
        }
        if (tier == null && template == null) {
            return;
        }
        SpConnectionDesc conn = StorPoolUtil.getSpConnection(pool.getUuid(), pool.getId(), storagePoolDetailsDao, primaryStoreDao);
        StorPoolUtil.spLog("Updating volume [%s] with tier tag [%s] or template [%s] from Disk offering", volume.getId(), tier, template);
        String volumeName = StorPoolStorageAdaptor.getVolumeNameFromPath(volume.getPath(), true);
        Map<String, String> tags = StorPoolHelper.addStorPoolTags(null, null, null, null, tier);
        StorPoolVolumeDef spVolume = new StorPoolVolumeDef(volumeName, null, tags, null, null, template, null, null, null);
        SpApiResponse response = StorPoolUtil.volumeUpdate(spVolume, conn);
        if (response.getError() != null) {
            StorPoolUtil.spLog("Could not update volume [%s] with tier tag [%s] or template [%s] from Disk offering due to [%s]", volume.getId(), tier, template, response.getError());
        }
    }

    private String getTemplateFromOfferingDetail(Long diskOfferingId) {
        String template = null;
        DiskOfferingDetailVO diskOfferingDetail = diskOfferingDetailsDao.findDetail(diskOfferingId, StorPoolUtil.SP_TEMPLATE);
        if (diskOfferingDetail == null ) {
            ServiceOfferingVO serviceOffering = serviceOfferingDao.findServiceOfferingByComputeOnlyDiskOffering(diskOfferingId, true);
            if (serviceOffering != null) {
                ServiceOfferingDetailsVO serviceOfferingDetail = serviceOfferingDetailDao.findDetail(serviceOffering.getId(), StorPoolUtil.SP_TEMPLATE);
                if (serviceOfferingDetail != null) {
                    template = serviceOfferingDetail.getValue();
                }
            }
        } else {
            template = diskOfferingDetail.getValue();
        }
        return template;
    }

    private String getTierFromOfferingDetail(Long diskOfferingId) {
        String tier = null;
        DiskOfferingDetailVO diskOfferingDetail = diskOfferingDetailsDao.findDetail(diskOfferingId, StorPoolUtil.SP_TIER);
        if (diskOfferingDetail == null ) {
            return tier;
        } else {
            tier = diskOfferingDetail.getValue();
        }
        return tier;
    }
}
