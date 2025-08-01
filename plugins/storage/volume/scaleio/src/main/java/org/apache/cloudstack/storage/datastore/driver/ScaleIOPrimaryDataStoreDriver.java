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
package org.apache.cloudstack.storage.datastore.driver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.cloudstack.engine.orchestration.service.VolumeOrchestrationService;
import org.apache.cloudstack.engine.subsystem.api.storage.ChapInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.CopyCommandResult;
import org.apache.cloudstack.engine.subsystem.api.storage.CreateCmdResult;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreCapabilities;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPoint;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPointSelector;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreDriver;
import org.apache.cloudstack.engine.subsystem.api.storage.SnapshotInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeService;
import org.apache.cloudstack.framework.async.AsyncCompletionCallback;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.resourcedetail.DiskOfferingDetailVO;
import org.apache.cloudstack.resourcedetail.dao.DiskOfferingDetailsDao;
import org.apache.cloudstack.storage.RemoteHostEndPoint;
import org.apache.cloudstack.storage.command.CommandResult;
import org.apache.cloudstack.storage.command.CopyCommand;
import org.apache.cloudstack.storage.command.CreateObjectAnswer;
import org.apache.cloudstack.storage.command.CreateObjectCommand;
import org.apache.cloudstack.storage.datastore.api.StoragePoolStatistics;
import org.apache.cloudstack.storage.datastore.api.VolumeStatistics;
import org.apache.cloudstack.storage.datastore.client.ScaleIOGatewayClient;
import org.apache.cloudstack.storage.datastore.client.ScaleIOGatewayClientConnectionPool;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.SnapshotDataStoreVO;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailVO;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailsDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.datastore.manager.ScaleIOSDCManager;
import org.apache.cloudstack.storage.datastore.manager.ScaleIOSDCManagerImpl;
import org.apache.cloudstack.storage.datastore.util.ScaleIOUtil;
import org.apache.cloudstack.storage.to.SnapshotObjectTO;
import org.apache.cloudstack.storage.to.VolumeObjectTO;
import org.apache.cloudstack.storage.volume.VolumeObject;
import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.GetVolumeStatAnswer;
import com.cloud.agent.api.GetVolumeStatCommand;
import com.cloud.agent.api.storage.MigrateVolumeCommand;
import com.cloud.agent.api.storage.ResizeVolumeCommand;
import com.cloud.agent.api.to.DataObjectType;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.DataTO;
import com.cloud.agent.api.to.DiskTO;
import com.cloud.agent.api.to.StorageFilerTO;
import com.cloud.alert.AlertManager;
import com.cloud.configuration.Config;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.server.ManagementServerImpl;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.ResizeVolumePayload;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.Storage;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StoragePoolHostVO;
import com.cloud.storage.VMTemplateStoragePoolVO;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeDetailVO;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.StoragePoolHostDao;
import com.cloud.storage.dao.VMTemplatePoolDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.dao.VolumeDetailsDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.common.base.Preconditions;

public class ScaleIOPrimaryDataStoreDriver implements PrimaryDataStoreDriver {
    protected Logger logger = LogManager.getLogger(getClass());

    @Inject
    EndPointSelector selector;
    @Inject
    private PrimaryDataStoreDao storagePoolDao;
    @Inject
    private StoragePoolDetailsDao storagePoolDetailsDao;
    @Inject
    private StoragePoolHostDao storagePoolHostDao;
    @Inject
    private VolumeDao volumeDao;
    @Inject
    private VolumeDetailsDao volumeDetailsDao;
    @Inject
    private VMTemplatePoolDao vmTemplatePoolDao;
    @Inject
    private SnapshotDataStoreDao snapshotDataStoreDao;
    @Inject
    protected SnapshotDao snapshotDao;
    @Inject
    private AlertManager alertMgr;
    @Inject
    private ConfigurationDao configDao;
    @Inject
    private DiskOfferingDetailsDao diskOfferingDetailsDao;
    @Inject
    private HostDao hostDao;
    @Inject
    private VMInstanceDao vmInstanceDao;
    @Inject
    private VolumeService volumeService;
    @Inject
    private VolumeOrchestrationService volumeMgr;
    private ScaleIOSDCManager sdcManager;

    public ScaleIOPrimaryDataStoreDriver() {
        sdcManager = new ScaleIOSDCManagerImpl();
    }

    ScaleIOGatewayClient getScaleIOClient(final StoragePool storagePool) throws Exception {
        return ScaleIOGatewayClientConnectionPool.getInstance().getClient(storagePool, storagePoolDetailsDao);
    }

    ScaleIOGatewayClient getScaleIOClient(final DataStore dataStore) throws Exception {
        return ScaleIOGatewayClientConnectionPool.getInstance().getClient(dataStore, storagePoolDetailsDao);
    }

    private boolean setVolumeLimitsOnSDC(VolumeVO volume, Host host, DataStore dataStore, Long iopsLimit, Long bandwidthLimitInKbps) throws Exception {
        sdcManager = ComponentContext.inject(sdcManager);
        final String sdcId = sdcManager.prepareSDC(host, dataStore);
        if (StringUtils.isBlank(sdcId)) {
            alertHostSdcDisconnection(host);
            throw new CloudRuntimeException("Unable to grant access to volume: " + volume + ", no Sdc connected with host ip: " + host.getPrivateIpAddress());
        }

        final ScaleIOGatewayClient client = getScaleIOClient(dataStore);
        return client.mapVolumeToSdcWithLimits(ScaleIOUtil.getVolumePath(volume.getPath()), sdcId, iopsLimit, bandwidthLimitInKbps);
    }

    private boolean setVolumeLimitsFromDetails(VolumeVO volume, Host host, DataStore dataStore) throws Exception {
        Long bandwidthLimitInKbps = 0L; // Unlimited
        // Check Bandwidth Limit parameter in volume details
        final VolumeDetailVO bandwidthVolumeDetail = volumeDetailsDao.findDetail(volume.getId(), Volume.BANDWIDTH_LIMIT_IN_MBPS);
        if (bandwidthVolumeDetail != null && bandwidthVolumeDetail.getValue() != null) {
            bandwidthLimitInKbps = Long.parseLong(bandwidthVolumeDetail.getValue()) * 1024;
        }

        Long iopsLimit = 0L; // Unlimited
        // Check IOPS Limit parameter in volume details, else try MaxIOPS
        final VolumeDetailVO iopsVolumeDetail = volumeDetailsDao.findDetail(volume.getId(), Volume.IOPS_LIMIT);
        if (iopsVolumeDetail != null && iopsVolumeDetail.getValue() != null) {
            iopsLimit = Long.parseLong(iopsVolumeDetail.getValue());
        } else if (volume.getMaxIops() != null) {
            iopsLimit = volume.getMaxIops();
        }
        if (iopsLimit > 0 && iopsLimit < ScaleIOUtil.MINIMUM_ALLOWED_IOPS_LIMIT) {
            iopsLimit = ScaleIOUtil.MINIMUM_ALLOWED_IOPS_LIMIT;
        }

        return setVolumeLimitsOnSDC(volume, host, dataStore, iopsLimit, bandwidthLimitInKbps);
    }

    @Override
    public boolean grantAccess(DataObject dataObject, Host host, DataStore dataStore) {
        try {
            sdcManager = ComponentContext.inject(sdcManager);
            final String sdcId = sdcManager.prepareSDC(host, dataStore);
            if (StringUtils.isBlank(sdcId)) {
                alertHostSdcDisconnection(host);
                throw new CloudRuntimeException(String.format(
                        "Unable to grant access to %s: [id: %d, uuid: %s], no Sdc connected with host ip: %s",
                        dataObject.getType(), dataObject.getId(),
                        dataObject.getUuid(), host.getPrivateIpAddress()));
            }

            if (DataObjectType.VOLUME.equals(dataObject.getType())) {
                final VolumeVO volume = volumeDao.findById(dataObject.getId());
                logger.debug("Granting access for PowerFlex volume: {} at path {}", volume, volume.getPath());
                return setVolumeLimitsFromDetails(volume, host, dataStore);
            } else if (DataObjectType.TEMPLATE.equals(dataObject.getType())) {
                final VMTemplateStoragePoolVO templatePoolRef = vmTemplatePoolDao.findByPoolTemplate(dataStore.getId(), dataObject.getId(), null);
                logger.debug("Granting access for PowerFlex template volume: {}", templatePoolRef.getInstallPath());
                final ScaleIOGatewayClient client = getScaleIOClient(dataStore);
                return client.mapVolumeToSdc(ScaleIOUtil.getVolumePath(templatePoolRef.getInstallPath()), sdcId);
            } else if (DataObjectType.SNAPSHOT.equals(dataObject.getType())) {
                SnapshotInfo snapshot = (SnapshotInfo) dataObject;
                logger.debug("Granting access for PowerFlex volume snapshot: {} at path {}", snapshot, snapshot.getPath());
                final ScaleIOGatewayClient client = getScaleIOClient(dataStore);
                return client.mapVolumeToSdc(ScaleIOUtil.getVolumePath(snapshot.getPath()), sdcId);
            }

            return false;
        } catch (Exception e) {
            throw new CloudRuntimeException(e);
        }
    }

    private boolean grantAccess(DataObject dataObject, EndPoint ep, DataStore dataStore) {
        Host host = hostDao.findById(ep.getId());
        return grantAccess(dataObject, host, dataStore);
    }

    @Override
    public void revokeAccess(DataObject dataObject, Host host, DataStore dataStore) {
        if (host == null) {
            logger.info("Declining to revoke access to PowerFlex volume when a host is not provided");
            return;
        }

        try {
            final String sdcId = getConnectedSdc(dataStore, host);
            if (StringUtils.isBlank(sdcId)) {
                logger.warn("Unable to revoke access for {}: [id: {}, uuid: {}], " +
                        "no Sdc connected with host [id: {}, uuid: {}, ip: {}]",
                        dataObject.getType(), dataObject.getId(), dataObject.getUuid(),
                        host.getId(), host.getUuid(), host.getPrivateIpAddress());
                return;
            }
            final ScaleIOGatewayClient client = getScaleIOClient(dataStore);
            if (DataObjectType.VOLUME.equals(dataObject.getType())) {
                final VolumeVO volume = volumeDao.findById(dataObject.getId());
                logger.debug("Revoking access for PowerFlex volume: {} at path {}", volume, volume.getPath());
                client.unmapVolumeFromSdc(ScaleIOUtil.getVolumePath(volume.getPath()), sdcId);
            } else if (DataObjectType.TEMPLATE.equals(dataObject.getType())) {
                final VMTemplateStoragePoolVO templatePoolRef = vmTemplatePoolDao.findByPoolTemplate(dataStore.getId(), dataObject.getId(), null);
                logger.debug("Revoking access for PowerFlex template volume: {}", templatePoolRef.getInstallPath());
                client.unmapVolumeFromSdc(ScaleIOUtil.getVolumePath(templatePoolRef.getInstallPath()), sdcId);
            } else if (DataObjectType.SNAPSHOT.equals(dataObject.getType())) {
                SnapshotInfo snapshot = (SnapshotInfo) dataObject;
                logger.debug("Revoking access for PowerFlex volume snapshot: {} at path {}", snapshot, snapshot.getPath());
                client.unmapVolumeFromSdc(ScaleIOUtil.getVolumePath(snapshot.getPath()), sdcId);
            }
            if (client.listVolumesMappedToSdc(sdcId).isEmpty()) {
                sdcManager = ComponentContext.inject(sdcManager);
                sdcManager.unprepareSDC(host, dataStore);
            }
        } catch (Exception e) {
            logger.warn("Failed to revoke access due to: " + e.getMessage(), e);
        }
    }

    public void revokeVolumeAccess(String volumePath, Host host, DataStore dataStore) {
        if (host == null) {
            logger.warn("Declining to revoke access to PowerFlex volume when a host is not provided");
            return;
        }

        try {
            logger.debug("Revoking access for PowerFlex volume: " + volumePath);

            final String sdcId = getConnectedSdc(dataStore, host);
            if (StringUtils.isBlank(sdcId)) {
                logger.warn("Unable to revoke access for volume: {}, " +
                        "no Sdc connected with host [id: {}, uuid: {}, ip: {}]",
                        volumePath, host.getId(), host.getUuid(), host.getPrivateIpAddress());
                return;
            }

            final ScaleIOGatewayClient client = getScaleIOClient(dataStore);
            client.unmapVolumeFromSdc(ScaleIOUtil.getVolumePath(volumePath), sdcId);
            if (client.listVolumesMappedToSdc(sdcId).isEmpty()) {
                sdcManager = ComponentContext.inject(sdcManager);
                sdcManager.unprepareSDC(host, dataStore);
            }
        } catch (Exception e) {
            logger.warn("Failed to revoke access due to: " + e.getMessage(), e);
        }
    }

    private void revokeAccess(DataObject dataObject, EndPoint ep, DataStore dataStore) {
        Host host = hostDao.findById(ep.getId());
        revokeAccess(dataObject, host, dataStore);
    }

    public String getConnectedSdc(DataStore dataStore, Host host) {
        try {
            StoragePoolHostVO poolHostVO = storagePoolHostDao.findByPoolHost(dataStore.getId(), host.getId());
            if (poolHostVO == null) {
                return null;
            }

            final ScaleIOGatewayClient client = getScaleIOClient(dataStore);
            if (client.isSdcConnected(poolHostVO.getLocalPath())) {
                return poolHostVO.getLocalPath();
            }
        } catch (Exception e) {
            logger.warn(String.format("Couldn't check SDC connection for the host: %s and " +
                    "storage pool: %s due to %s", host, dataStore, e.getMessage()), e);
        }

        return null;
    }

    @Override
    public boolean requiresAccessForMigration(DataObject dataObject) {
        return true;
    }

    @Override
    public long getUsedBytes(StoragePool storagePool) {
        long usedSpaceBytes = 0;
        // Volumes
        List<VolumeVO> volumes = volumeDao.findByPoolIdAndState(storagePool.getId(), Volume.State.Ready);
        if (volumes != null) {
            for (VolumeVO volume : volumes) {
                usedSpaceBytes += volume.getSize();

                long vmSnapshotChainSize = volume.getVmSnapshotChainSize() == null ? 0 : volume.getVmSnapshotChainSize();
                usedSpaceBytes += vmSnapshotChainSize;
            }
        }

        //Snapshots
        List<SnapshotDataStoreVO> snapshots = snapshotDataStoreDao.listByStoreIdAndState(storagePool.getId(), ObjectInDataStoreStateMachine.State.Ready);
        if (snapshots != null) {
            for (SnapshotDataStoreVO snapshot : snapshots) {
                usedSpaceBytes += snapshot.getSize();
            }
        }

        // Templates
        List<VMTemplateStoragePoolVO> templates = vmTemplatePoolDao.listByPoolIdAndState(storagePool.getId(), ObjectInDataStoreStateMachine.State.Ready);
        if (templates != null) {
            for (VMTemplateStoragePoolVO template : templates) {
                usedSpaceBytes += template.getTemplateSize();
            }
        }

        logger.debug("Used/Allocated storage space (in bytes): " + String.valueOf(usedSpaceBytes));

        return usedSpaceBytes;
    }

    @Override
    public long getUsedIops(StoragePool storagePool) {
        return 0;
    }

    @Override
    public long getDataObjectSizeIncludingHypervisorSnapshotReserve(DataObject dataObject, StoragePool pool) {
        return ((dataObject != null && dataObject.getSize() != null) ? dataObject.getSize() : 0);
    }

    @Override
    public long getBytesRequiredForTemplate(TemplateInfo templateInfo, StoragePool storagePool) {
        if (templateInfo == null || storagePool == null) {
            return 0;
        }

        VMTemplateStoragePoolVO templatePoolRef = vmTemplatePoolDao.findByPoolTemplate(storagePool.getId(), templateInfo.getId(), null);
        if (templatePoolRef != null) {
            // Template exists on this primary storage, do not require additional space
            return 0;
        }

        return getDataObjectSizeIncludingHypervisorSnapshotReserve(templateInfo, storagePool);
    }

    @Override
    public Map<String, String> getCapabilities() {
        Map<String, String> mapCapabilities = new HashMap<>();
        mapCapabilities.put(DataStoreCapabilities.CAN_CREATE_VOLUME_FROM_VOLUME.toString(), Boolean.TRUE.toString());
        mapCapabilities.put(DataStoreCapabilities.CAN_CREATE_VOLUME_FROM_SNAPSHOT.toString(), Boolean.TRUE.toString());
        mapCapabilities.put(DataStoreCapabilities.CAN_REVERT_VOLUME_TO_SNAPSHOT.toString(), Boolean.TRUE.toString());
        mapCapabilities.put(DataStoreCapabilities.STORAGE_SYSTEM_SNAPSHOT.toString(), Boolean.TRUE.toString());
        return mapCapabilities;
    }

    @Override
    public ChapInfo getChapInfo(DataObject dataObject) {
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
    public void takeSnapshot(SnapshotInfo snapshotInfo, AsyncCompletionCallback<CreateCmdResult> callback) {
        logger.debug("Taking PowerFlex volume snapshot");

        Preconditions.checkArgument(snapshotInfo != null, "snapshotInfo cannot be null");

        VolumeInfo volumeInfo = snapshotInfo.getBaseVolume();
        Preconditions.checkArgument(volumeInfo != null, "volumeInfo cannot be null");

        VolumeVO volumeVO = volumeDao.findById(volumeInfo.getId());

        long storagePoolId = volumeVO.getPoolId();
        Preconditions.checkArgument(storagePoolId > 0, "storagePoolId should be > 0");

        StoragePoolVO storagePool = storagePoolDao.findById(storagePoolId);
        Preconditions.checkArgument(storagePool != null && storagePool.getHostAddress() != null, "storagePool and host address should not be null");

        CreateCmdResult result;

        try {
            SnapshotObjectTO snapshotObjectTo = (SnapshotObjectTO)snapshotInfo.getTO();

            final ScaleIOGatewayClient client = getScaleIOClient(storagePool);
            final String scaleIOVolumeId = ScaleIOUtil.getVolumePath(volumeVO.getPath());
            String snapshotName = String.format("%s-%s-%s-%s", ScaleIOUtil.SNAPSHOT_PREFIX, snapshotInfo.getId(),
                    storagePool.getUuid().split("-")[0].substring(4), ManagementServerImpl.customCsIdentifier.value());

            org.apache.cloudstack.storage.datastore.api.Volume scaleIOVolume = null;
            scaleIOVolume = client.takeSnapshot(scaleIOVolumeId, snapshotName);

            if (scaleIOVolume == null) {
                throw new CloudRuntimeException("Failed to take snapshot on PowerFlex cluster");
            }

            snapshotObjectTo.setPath(ScaleIOUtil.updatedPathWithVolumeName(scaleIOVolume.getId(), snapshotName));
            CreateObjectAnswer createObjectAnswer = new CreateObjectAnswer(snapshotObjectTo);
            result = new CreateCmdResult(null, createObjectAnswer);
            result.setResult(null);
        } catch (Exception e) {
            logger.warn("Unable to take PowerFlex volume snapshot for volume: {} due to {}", volumeInfo, e.getMessage());
            result = new CreateCmdResult(null, new CreateObjectAnswer(e.toString()));
            result.setResult(e.toString());
        }

        callback.complete(result);
    }

    @Override
    public void revertSnapshot(SnapshotInfo snapshot, SnapshotInfo snapshotOnPrimaryStore, AsyncCompletionCallback<CommandResult> callback) {
        logger.debug("Reverting to PowerFlex volume snapshot");

        Preconditions.checkArgument(snapshot != null, "snapshotInfo cannot be null");

        VolumeInfo volumeInfo = snapshot.getBaseVolume();
        Preconditions.checkArgument(volumeInfo != null, "volumeInfo cannot be null");

        VolumeVO volumeVO = volumeDao.findById(volumeInfo.getId());

        try {
            if (volumeVO == null || volumeVO.getRemoved() != null) {
                String errMsg = "The volume that the snapshot belongs to no longer exists.";
                CommandResult commandResult = new CommandResult();
                commandResult.setResult(errMsg);
                callback.complete(commandResult);
                return;
            }

            StoragePoolVO storagePool = storagePoolDao.findById(volumeVO.getPoolId());
            final ScaleIOGatewayClient client = getScaleIOClient(storagePool);
            String snapshotVolumeId = ScaleIOUtil.getVolumePath(snapshot.getPath());
            final String destVolumeId = ScaleIOUtil.getVolumePath(volumeVO.getPath());
            client.revertSnapshot(snapshotVolumeId, destVolumeId);

            CommandResult commandResult = new CommandResult();
            callback.complete(commandResult);
        } catch (Exception ex) {
            logger.debug(String.format("Unable to revert to PowerFlex snapshot: %s", snapshot), ex);
            throw new CloudRuntimeException(ex.getMessage());
        }
    }

    public CreateObjectAnswer createVolume(VolumeInfo volumeInfo, long storagePoolId) {
        return createVolume(volumeInfo, storagePoolId, false, null);
    }

    public CreateObjectAnswer createVolume(VolumeInfo volumeInfo, long storagePoolId, boolean migrationInvolved, Long usageSize) {
        logger.debug("Creating PowerFlex volume");

        StoragePoolVO storagePool = storagePoolDao.findById(storagePoolId);

        Preconditions.checkArgument(volumeInfo != null, "volumeInfo cannot be null");
        Preconditions.checkArgument(storagePoolId > 0, "storagePoolId should be > 0");
        Preconditions.checkArgument(storagePool != null && storagePool.getHostAddress() != null, "storagePool and host address should not be null");

        try {
            final ScaleIOGatewayClient client = getScaleIOClient(storagePool);
            final String scaleIOStoragePoolId = storagePool.getPath();
            final Long sizeInBytes = volumeInfo.getSize();
            final long sizeInGb = (long) Math.ceil(sizeInBytes / (1024.0 * 1024.0 * 1024.0));
            final String scaleIOVolumeName = String.format("%s-%s-%s-%s", ScaleIOUtil.VOLUME_PREFIX, volumeInfo.getId(),
                    storagePool.getUuid().split("-")[0].substring(4), ManagementServerImpl.customCsIdentifier.value());

            org.apache.cloudstack.storage.datastore.api.Volume scaleIOVolume = null;
            scaleIOVolume = client.createVolume(scaleIOVolumeName, scaleIOStoragePoolId, (int) sizeInGb, volumeInfo.getProvisioningType());

            if (scaleIOVolume == null) {
                throw new CloudRuntimeException("Failed to create volume on PowerFlex cluster");
            }

            VolumeVO volume = volumeDao.findById(volumeInfo.getId());
            String volumePath = ScaleIOUtil.updatedPathWithVolumeName(scaleIOVolume.getId(), scaleIOVolumeName);
            volume.set_iScsiName(volumePath);
            volume.setPath(volumePath);
            volume.setFolder(scaleIOVolume.getVtreeId());
            volume.setSize(scaleIOVolume.getSizeInKb() * 1024);
            volume.setPoolType(Storage.StoragePoolType.PowerFlex);
            volume.setFormat(volumeInfo.getFormat());
            volume.setPoolId(storagePoolId);
            VolumeObject createdObject = VolumeObject.getVolumeObject(volumeInfo.getDataStore(), volume);
            createdObject.update();

            long capacityBytes = storagePool.getCapacityBytes();
            long usedBytes = storagePool.getUsedBytes();
            usedBytes += volume.getSize();
            storagePool.setUsedBytes(usedBytes > capacityBytes ? capacityBytes : usedBytes);
            storagePoolDao.update(storagePoolId, storagePool);

            CreateObjectAnswer answer = new CreateObjectAnswer(createdObject.getTO());

            // if volume needs to be set up with encryption, do it now if it's not a root disk (which gets done during template copy)
            if (anyVolumeRequiresEncryption(volumeInfo) && (!volumeInfo.getVolumeType().equals(Volume.Type.ROOT) || migrationInvolved)) {
                logger.debug("Setting up encryption for volume [id: {}, uuid: {}, name: {}]",
                        volumeInfo.getId(), volumeInfo.getUuid(), volumeInfo.getName());
                VolumeObjectTO prepVolume = (VolumeObjectTO) createdObject.getTO();
                prepVolume.setPath(volumePath);
                prepVolume.setUuid(volumePath);
                if (usageSize != null) {
                    prepVolume.setUsableSize(usageSize);
                }
                CreateObjectCommand cmd = new CreateObjectCommand(prepVolume);
                EndPoint ep = selector.select(volumeInfo, true);
                if (ep == null) {
                    throw new CloudRuntimeException("No remote endpoint to send PowerFlex volume encryption preparation");
                } else {
                    try {
                        grantAccess(createdObject, ep, volumeInfo.getDataStore());
                        answer = (CreateObjectAnswer) ep.sendMessage(cmd);
                        if (!answer.getResult()) {
                            throw new CloudRuntimeException("Failed to set up encryption on PowerFlex volume: " + answer.getDetails());
                        }
                    } finally {
                        revokeAccess(createdObject, ep, volumeInfo.getDataStore());
                        prepVolume.clearPassphrase();
                    }
                }
            } else {
                 logger.debug("No encryption configured for data volume [id: {}, uuid: {}, name: {}]",
                         volumeInfo.getId(), volumeInfo.getUuid(), volumeInfo.getName());
            }

            return answer;
        } catch (Exception e) {
            String errMsg = "Unable to create PowerFlex Volume due to " + e.getMessage();
            logger.warn(errMsg);
            throw new CloudRuntimeException(errMsg, e);
        }
    }

    private String  createTemplateVolume(TemplateInfo templateInfo, long storagePoolId) {
        logger.debug("Creating PowerFlex template volume");

        StoragePoolVO storagePool = storagePoolDao.findById(storagePoolId);
        Preconditions.checkArgument(templateInfo != null, "templateInfo cannot be null");
        Preconditions.checkArgument(storagePoolId > 0, "storagePoolId should be > 0");
        Preconditions.checkArgument(storagePool != null && storagePool.getHostAddress() != null, "storagePool and host address should not be null");

        try {
            final ScaleIOGatewayClient client = getScaleIOClient(storagePool);
            final String scaleIOStoragePoolId = storagePool.getPath();
            final Long sizeInBytes = templateInfo.getSize();
            final long sizeInGb = (long) Math.ceil(sizeInBytes / (1024.0 * 1024.0 * 1024.0));
            final String scaleIOVolumeName = String.format("%s-%s-%s-%s", ScaleIOUtil.TEMPLATE_PREFIX, templateInfo.getId(),
                    storagePool.getUuid().split("-")[0].substring(4), ManagementServerImpl.customCsIdentifier.value());

            org.apache.cloudstack.storage.datastore.api.Volume scaleIOVolume = null;
            scaleIOVolume = client.createVolume(scaleIOVolumeName, scaleIOStoragePoolId, (int) sizeInGb, Storage.ProvisioningType.THIN);

            if (scaleIOVolume == null) {
                throw new CloudRuntimeException("Failed to create template volume on PowerFlex cluster");
            }

            VMTemplateStoragePoolVO templatePoolRef = vmTemplatePoolDao.findByPoolTemplate(storagePoolId, templateInfo.getId(), null);
            String templatePath = ScaleIOUtil.updatedPathWithVolumeName(scaleIOVolume.getId(), scaleIOVolumeName);
            templatePoolRef.setInstallPath(templatePath);
            templatePoolRef.setLocalDownloadPath(scaleIOVolume.getId());
            templatePoolRef.setTemplateSize(scaleIOVolume.getSizeInKb() * 1024);
            vmTemplatePoolDao.update(templatePoolRef.getId(), templatePoolRef);

            long capacityBytes = storagePool.getCapacityBytes();
            long usedBytes = storagePool.getUsedBytes();
            usedBytes += templatePoolRef.getTemplateSize();
            storagePool.setUsedBytes(usedBytes > capacityBytes ? capacityBytes : usedBytes);
            storagePoolDao.update(storagePoolId, storagePool);

            return templatePath;
        } catch (Exception e) {
            String errMsg = "Unable to create PowerFlex template volume due to " + e.getMessage();
            logger.warn(errMsg);
            throw new CloudRuntimeException(errMsg, e);
        }
    }

    @Override
    public void createAsync(DataStore dataStore, DataObject dataObject, AsyncCompletionCallback<CreateCmdResult> callback) {
        String scaleIOVolumePath = null;
        String errMsg = null;
        Answer answer = new Answer(null, false, "not started");
        try {
            if (dataObject.getType() == DataObjectType.VOLUME) {
                logger.debug("createAsync - creating volume");
                CreateObjectAnswer createAnswer = createVolume((VolumeInfo) dataObject, dataStore.getId());
                scaleIOVolumePath = createAnswer.getData().getPath();
                answer = createAnswer;
            } else if (dataObject.getType() == DataObjectType.TEMPLATE) {
                logger.debug("createAsync - creating template");
                scaleIOVolumePath = createTemplateVolume((TemplateInfo)dataObject, dataStore.getId());
                answer = new Answer(null, true, "created template");
            } else {
                errMsg = "Invalid DataObjectType (" + dataObject.getType() + ") passed to createAsync";
                logger.error(errMsg);
                answer = new Answer(null, false, errMsg);
            }
        } catch (Exception ex) {
            errMsg = ex.getMessage();
            logger.error(errMsg);
            if (callback == null) {
                throw ex;
            }
            answer = new Answer(null, false, errMsg);
        }

        if (callback != null) {
            CreateCmdResult result = new CreateCmdResult(scaleIOVolumePath, answer);
            result.setResult(errMsg);
            callback.complete(result);
        }
    }

    @Override
    public void deleteAsync(DataStore dataStore, DataObject dataObject, AsyncCompletionCallback<CommandResult> callback) {
        Preconditions.checkArgument(dataObject != null, "dataObject cannot be null");

        long storagePoolId = dataStore.getId();
        StoragePoolVO storagePool = storagePoolDao.findById(storagePoolId);
        Preconditions.checkArgument(storagePoolId > 0, "storagePoolId should be > 0");
        Preconditions.checkArgument(storagePool != null && storagePool.getHostAddress() != null, "storagePool and host address should not be null");

        String errMsg = null;
        String scaleIOVolumePath = null;
        try {
            boolean deleteResult = false;
            if (dataObject.getType() == DataObjectType.VOLUME) {
                logger.debug("deleteAsync - deleting volume");
                scaleIOVolumePath = ((VolumeInfo) dataObject).getPath();
            } else if (dataObject.getType() == DataObjectType.SNAPSHOT) {
                logger.debug("deleteAsync - deleting snapshot");
                scaleIOVolumePath = ((SnapshotInfo) dataObject).getPath();
            } else if (dataObject.getType() == DataObjectType.TEMPLATE) {
                logger.debug("deleteAsync - deleting template");
                scaleIOVolumePath = ((TemplateInfo) dataObject).getInstallPath();
            } else {
                errMsg = "Invalid DataObjectType (" + dataObject.getType() + ") passed to deleteAsync";
                logger.error(errMsg);
                throw new CloudRuntimeException(errMsg);
            }

            try {
                String scaleIOVolumeId = ScaleIOUtil.getVolumePath(scaleIOVolumePath);
                final ScaleIOGatewayClient client = getScaleIOClient(storagePool);
                deleteResult =  client.deleteVolume(scaleIOVolumeId);
                if (!deleteResult) {
                    errMsg = "Failed to delete PowerFlex volume with id: " + scaleIOVolumeId;
                }

                long usedBytes = storagePool.getUsedBytes();
                usedBytes -= dataObject.getSize();
                storagePool.setUsedBytes(usedBytes < 0 ? 0 : usedBytes);
                storagePoolDao.update(storagePoolId, storagePool);
            } catch (Exception e) {
                errMsg = "Unable to delete PowerFlex volume: " + scaleIOVolumePath + " due to " + e.getMessage();
                logger.warn(errMsg);
                throw new CloudRuntimeException(errMsg, e);
            }
        } catch (Exception ex) {
            errMsg = ex.getMessage();
            logger.error(errMsg);
            if (callback == null) {
                throw ex;
            }
        }

        if (callback != null) {
            CommandResult result = new CommandResult();
            result.setResult(errMsg);
            callback.complete(result);
        }
    }

    @Override
    public void copyAsync(DataObject srcData, DataObject destData, AsyncCompletionCallback<CopyCommandResult> callback) {
        copyAsync(srcData, destData, null, callback);
    }

    @Override
    public void copyAsync(DataObject srcData, DataObject destData, Host destHost, AsyncCompletionCallback<CopyCommandResult> callback) {
        Answer answer = null;
        String errMsg = null;
        CopyCommandResult result;

        try {
            DataStore srcStore = srcData.getDataStore();
            DataStore destStore = destData.getDataStore();
            if (srcStore.getRole() == DataStoreRole.Primary && (destStore.getRole() == DataStoreRole.Primary && destData.getType() == DataObjectType.VOLUME)) {
                if (srcData.getType() == DataObjectType.TEMPLATE) {
                    answer = copyTemplateToVolume(srcData, destData, destHost);
                } else if (srcData.getType() == DataObjectType.VOLUME) {
                    if (isSameScaleIOStorageInstance(srcStore, destStore)) {
                        answer = migrateVolume(srcData, destData);
                    } else {
                        String vmName = ((VolumeInfo) srcData).getAttachedVmName();
                        if (vmName == null || !vmInstanceDao.findVMByInstanceName(vmName).getState().equals(VirtualMachine.State.Running)) {
                            answer = copyOfflineVolume(srcData, destData, destHost);
                        } else {
                            answer = liveMigrateVolume(srcData, destData);
                        }
                    }
                } else {
                    errMsg = "Unsupported copy operation from src object: (" + srcData.getType() + ", " + srcData.getDataStore() + "), dest object: ("
                            + destData.getType() + ", " + destData.getDataStore() + ")";
                    logger.warn(errMsg);
                    answer = new Answer(null, false, errMsg);
                }
            } else {
                errMsg = "Unsupported copy operation";
                logger.warn(errMsg);
                answer = new Answer(null, false, errMsg);
            }
        } catch (Exception e) {
            logger.debug("Failed to copy due to " + e.getMessage(), e);
            errMsg = e.toString();
            answer = new Answer(null, false, errMsg);
        }

        result = new CopyCommandResult(null, answer);
        if (answer != null && !answer.getResult()) {
            result.setResult(answer.getDetails());
        }
        callback.complete(result);
    }

    /**
     * Responsible for copying template on ScaleIO primary to root disk
     * @param srcData dataobject representing the template
     * @param destData dataobject representing the target root disk
     * @param destHost host to use for copy
     * @return answer
     */
    private Answer copyTemplateToVolume(DataObject srcData, DataObject destData, Host destHost) {
        /* If encryption is requested, since the template object is not encrypted we need to grow the destination disk to accommodate the new headers.
         * Data stores of file type happen automatically, but block device types have to handle it. Unfortunately for ScaleIO this means we add a whole 8GB to
         * the original size, but only if we are close to an 8GB boundary.
         */
        logger.debug("Copying template {} to volume {}", srcData, destData);
        VolumeInfo destInfo = (VolumeInfo) destData;
        boolean encryptionRequired = anyVolumeRequiresEncryption(destData);
        if (encryptionRequired) {
            if (needsExpansionForEncryptionHeader(srcData.getSize(), destData.getSize())) {
                long newSize = destData.getSize() + (1<<30);
                logger.debug("Destination volume {} ({}) is configured for encryption. Resizing to fit headers, new size {} will be rounded up to nearest 8Gi", destInfo, destData.getSize(), newSize);
                ResizeVolumePayload p = new ResizeVolumePayload(newSize, destInfo.getMinIops(), destInfo.getMaxIops(),
                    destInfo.getHypervisorSnapshotReserve(), false, destInfo.getAttachedVmName(), null, true);
                destInfo.addPayload(p);
                resizeVolume(destInfo);
            } else {
                logger.debug("Template {} has size {}, ok for volume {} with size {}", srcData, srcData.getSize(), destData, destData.getSize());
            }
        } else {
            logger.debug("Destination volume is not configured for encryption, skipping encryption prep. Volume: {}", destData);
        }

        // Copy PowerFlex/ScaleIO template to volume
        logger.debug("Initiating copy from PowerFlex template volume on host {}", destHost != null ? destHost : "<not specified>");
        int primaryStorageDownloadWait = StorageManager.PRIMARY_STORAGE_DOWNLOAD_WAIT.value();
        CopyCommand cmd = new CopyCommand(srcData.getTO(), destData.getTO(), primaryStorageDownloadWait, VirtualMachineManager.ExecuteInSequence.value());

        Answer answer = null;
        EndPoint ep = destHost != null ? RemoteHostEndPoint.getHypervisorHostEndPoint(destHost) : selector.select(srcData, encryptionRequired);
        if (ep == null) {
            String errorMsg = String.format("No remote endpoint to send command, unable to find a valid endpoint. Requires encryption support: %s", encryptionRequired);
            logger.error(errorMsg);
            answer = new Answer(cmd, false, errorMsg);
        } else {
            VolumeVO volume = volumeDao.findById(destData.getId());
            Host host = destHost != null ? destHost : hostDao.findById(ep.getId());
            try {
                setVolumeLimitsOnSDC(volume, host, destData.getDataStore(), 0L, 0L);
                answer = ep.sendMessage(cmd);
            } catch (Exception e) {
                logger.error("Failed to copy template to volume due to: " + e.getMessage(), e);
                answer = new Answer(cmd, false, e.getMessage());
            }
        }

        return answer;
    }

    protected Answer copyOfflineVolume(DataObject srcData, DataObject destData, Host destHost) {
        // Copy PowerFlex/ScaleIO volume
        logger.debug("Initiating copy from PowerFlex template volume on host {}", destHost != null ? destHost : "<not specified>");
        String value = configDao.getValue(Config.CopyVolumeWait.key());
        int copyVolumeWait = NumbersUtil.parseInt(value, Integer.parseInt(Config.CopyVolumeWait.getDefaultValue()));

        CopyCommand cmd = new CopyCommand(srcData.getTO(), destData.getTO(), copyVolumeWait, VirtualMachineManager.ExecuteInSequence.value());

        Answer answer = null;
        boolean encryptionRequired = anyVolumeRequiresEncryption(srcData, destData);
        EndPoint ep = destHost != null ? RemoteHostEndPoint.getHypervisorHostEndPoint(destHost) : selector.select(srcData, encryptionRequired);
        if (ep == null) {
            String errorMsg = String.format("No remote endpoint to send command, unable to find a valid endpoint. Requires encryption support: %s", encryptionRequired);
            logger.error(errorMsg);
            answer = new Answer(cmd, false, errorMsg);
        } else {
            answer = ep.sendMessage(cmd);
        }

        return answer;
    }

    public Answer liveMigrateVolume(DataObject srcData, DataObject destData) {
        // Volume migration across different PowerFlex/ScaleIO clusters
        final long srcVolumeId = srcData.getId();
        DataStore srcStore = srcData.getDataStore();
        VolumeInfo srcVolumeInfo = (VolumeInfo) srcData;
        Map<String, String> srcDetails = getVolumeDetails(srcVolumeInfo, srcStore);

        DataStore destStore = destData.getDataStore();
        final long destPoolId = destStore.getId();
        Map<String, String> destDetails = getVolumeDetails((VolumeInfo) destData, destStore);
        VolumeObjectTO destVolTO = (VolumeObjectTO) destData.getTO();
        String destVolumePath = null;

        Host host = findEndpointForVolumeOperation(srcData);
        EndPoint ep = RemoteHostEndPoint.getHypervisorHostEndPoint(host);

        Answer answer = null;
        Long srcVolumeUsableSize = null;
        try {
            GetVolumeStatCommand statCmd = new GetVolumeStatCommand(srcVolumeInfo.getPath(), srcVolumeInfo.getStoragePoolType(), srcStore.getUuid());
            GetVolumeStatAnswer statAnswer = (GetVolumeStatAnswer) ep.sendMessage(statCmd);
            if (!statAnswer.getResult() ) {
                logger.warn(String.format("Unable to get volume %s stats", srcVolumeInfo));
            } else if (statAnswer.getVirtualSize() > 0) {
                srcVolumeUsableSize = statAnswer.getVirtualSize();
            }

            CreateObjectAnswer createAnswer = createVolume((VolumeInfo) destData, destStore.getId(), true, srcVolumeUsableSize);
            destVolumePath = createAnswer.getData().getPath();
            destVolTO.setPath(destVolumePath);

            grantAccess(destData, host, destData.getDataStore());

            int waitInterval = NumbersUtil.parseInt(configDao.getValue(Config.MigrateWait.key()), Integer.parseInt(Config.MigrateWait.getDefaultValue()));
            MigrateVolumeCommand migrateVolumeCommand = new MigrateVolumeCommand(srcData.getTO(), destVolTO,
                    srcDetails, destDetails, waitInterval);
            answer = ep.sendMessage(migrateVolumeCommand);
            boolean migrateStatus = answer.getResult();

            if (migrateStatus) {
                updateAfterSuccessfulVolumeMigration(srcData, destData, host);
                logger.debug("Successfully migrated migrate PowerFlex volume {} to storage pool {}", srcData,  destStore);
                answer = new Answer(null, true, null);
            } else {
                String errorMsg = String.format("Failed to migrate PowerFlex volume: %s to storage pool %s", srcData, destStore);
                logger.debug(errorMsg);
                answer = new Answer(null, false, errorMsg);
            }
        } catch (Exception e) {
            logger.error("Failed to migrate PowerFlex volume: {} due to: {}", srcData, e.getMessage());
            answer = new Answer(null, false, e.getMessage());
            if (e.getMessage().contains(OperationTimedoutException.class.getName())) {
                logger.error(String.format("The PowerFlex volume %s might have been migrated because the exception is %s, checking the volume on destination pool", srcData, OperationTimedoutException.class.getName()));
                Boolean volumeOnDestination = getVolumeStateOnPool(destStore, destVolumePath);
                if (volumeOnDestination) {
                    logger.error(String.format("The PowerFlex volume %s has been migrated to destination pool %s", srcData, destStore.getName()));
                    updateAfterSuccessfulVolumeMigration(srcData, destData, host);
                    answer = new Answer(null, true, null);
                } else {
                    logger.error(String.format("The PowerFlex volume %s has not been migrated completely to destination pool %s", srcData, destStore.getName()));
                }
            }
        }

        if (destVolumePath != null && !answer.getResult()) {
            revertBlockCopyVolumeOperations(srcData, destData, host, destVolumePath);
        }

        return answer;
    }

    private void updateAfterSuccessfulVolumeMigration(DataObject srcData, DataObject destData, Host host) {
        try {
            updateVolumeAfterCopyVolume(srcData, destData);
            updateSnapshotsAfterCopyVolume(srcData, destData);
            deleteSourceVolumeAfterSuccessfulBlockCopy(srcData, host);
        } catch (Exception ex) {
            logger.error(String.format("Error while update PowerFlex volume: %s after successfully migration due to: %s", srcData, ex.getMessage()));
        }
    }

    public Boolean getVolumeStateOnPool(DataStore srcStore, String srcVolumePath) {
        try {
            // check the state of volume on pool via ScaleIO gateway
            final ScaleIOGatewayClient client = getScaleIOClient(srcStore);
            final String sourceScaleIOVolumeId = ScaleIOUtil.getVolumePath(srcVolumePath);
            final org.apache.cloudstack.storage.datastore.api.Volume sourceScaleIOVolume = client.getVolume(sourceScaleIOVolumeId);
            logger.debug(String.format("The PowerFlex volume %s on pool %s is: %s", srcVolumePath, srcStore.getName(),
                    ReflectionToStringBuilderUtils.reflectOnlySelectedFields(sourceScaleIOVolume, "id", "name", "vtreeId", "sizeInGB", "volumeSizeInGb")));
            if (sourceScaleIOVolume == null || StringUtils.isEmpty(sourceScaleIOVolume.getVtreeId())) {
                return false;
            }
            Pair<Long, Long> volumeStats = getVolumeStats(storagePoolDao.findById(srcStore.getId()), srcVolumePath);
            if (volumeStats == null) {
                logger.debug(String.format("Unable to find volume stats for %s on pool %s", srcVolumePath, srcStore.getName()));
                return false;
            }
            logger.debug(String.format("Found volume stats for %s: provisionedSizeInBytes = %s, allocatedSizeInBytes = %s on pool %s", srcVolumePath, volumeStats.first(), volumeStats.second(), srcStore.getName()));
            return volumeStats.first().equals(volumeStats.second());
        } catch (Exception ex) {
            logger.error(String.format("Failed to check if PowerFlex volume %s exists on source pool %s", srcVolumePath, srcStore.getName()));
        }
        return null;
    }

    protected void updateVolumeAfterCopyVolume(DataObject srcData, DataObject destData) {
        // destination volume is already created and volume path is set in database by this time at "CreateObjectAnswer createAnswer = createVolume((VolumeInfo) destData, destStore.getId());"
        final long srcVolumeId = srcData.getId();
        final long destVolumeId = destData.getId();

        if (srcVolumeId != destVolumeId) {
            VolumeVO srcVolume = volumeDao.findById(srcVolumeId);
            srcVolume.set_iScsiName(null);
            srcVolume.setPath(null);
            srcVolume.setFolder(null);
            volumeDao.update(srcVolumeId, srcVolume);
        } else {
            // Live migrate volume
            VolumeVO volume = volumeDao.findById(srcVolumeId);
            Long oldPoolId = volume.getPoolId();
            volume.setLastPoolId(oldPoolId);
            volumeDao.update(srcVolumeId, volume);
        }
    }

    private Host findEndpointForVolumeOperation(DataObject srcData) {
        long hostId = 0;
        VMInstanceVO instance = vmInstanceDao.findVMByInstanceName(((VolumeInfo) srcData).getAttachedVmName());
        if (instance.getState().equals(VirtualMachine.State.Running)) {
            hostId = instance.getHostId();
        }
        if (hostId == 0) {
            hostId = selector.select(srcData, true).getId();
        }
        HostVO host = hostDao.findById(hostId);
        if (host == null) {
            throw new CloudRuntimeException("Found no hosts to run migrate volume command on");
        }

        return host;
    }

    public void updateSnapshotsAfterCopyVolume(DataObject srcData, DataObject destData) throws Exception {
        final long srcVolumeId = srcData.getId();
        DataStore srcStore = srcData.getDataStore();
        final ScaleIOGatewayClient client = getScaleIOClient(srcStore);

        DataStore destStore = destData.getDataStore();
        final long destPoolId = destStore.getId();
        final StoragePoolVO destStoragePool = storagePoolDao.findById(destPoolId);

        List<SnapshotVO> snapshots = snapshotDao.listByVolumeId(srcVolumeId);
        if (CollectionUtils.isNotEmpty(snapshots)) {
            for (SnapshotVO snapshot : snapshots) {
                SnapshotDataStoreVO snapshotStore = snapshotDataStoreDao.findByStoreSnapshot(DataStoreRole.Primary, srcStore.getId(), snapshot.getId());
                if (snapshotStore == null) {
                    continue;
                }

                String snapshotVolumeId = ScaleIOUtil.getVolumePath(snapshotStore.getInstallPath());
                String newSnapshotName = String.format("%s-%s-%s-%s", ScaleIOUtil.SNAPSHOT_PREFIX, snapshot.getId(),
                        destStoragePool.getUuid().split("-")[0].substring(4), ManagementServerImpl.customCsIdentifier.value());
                boolean renamed = client.renameVolume(snapshotVolumeId, newSnapshotName);

                snapshotStore.setDataStoreId(destPoolId);
                // Snapshot Id in the PowerFlex/ScaleIO pool remains the same after the migration
                // Update PowerFlex snapshot name only after it is renamed, to maintain the consistency
                if (renamed) {
                    snapshotStore.setInstallPath(ScaleIOUtil.updatedPathWithVolumeName(snapshotVolumeId, newSnapshotName));
                }
                snapshotDataStoreDao.update(snapshotStore.getId(), snapshotStore);
            }
        }
    }

    public void deleteSourceVolumeAfterSuccessfulBlockCopy(DataObject srcData, Host host) {
        DataStore srcStore = srcData.getDataStore();
        String srcVolumePath = srcData.getTO().getPath();
        revokeVolumeAccess(srcVolumePath, host, srcData.getDataStore());
        String errMsg;
        try {
            String scaleIOVolumeId = ScaleIOUtil.getVolumePath(srcVolumePath);
            final ScaleIOGatewayClient client = getScaleIOClient(srcStore);
            Boolean deleteResult =  client.deleteVolume(scaleIOVolumeId);
            if (!deleteResult) {
                errMsg = "Failed to delete source PowerFlex volume with id: " + scaleIOVolumeId;
                logger.warn(errMsg);
            }
        } catch (Exception e) {
            errMsg = "Unable to delete source PowerFlex volume: " + srcVolumePath + " due to " + e.getMessage();
            logger.warn(errMsg);;
        }
    }

    public void revertBlockCopyVolumeOperations(DataObject srcData, DataObject destData, Host host, String destVolumePath) {
        final String srcVolumePath = ((VolumeInfo) srcData).getPath();
        final String srcVolumeFolder = ((VolumeInfo) srcData).getFolder();
        DataStore destStore = destData.getDataStore();

        revokeAccess(destData, host, destData.getDataStore());
        String errMsg;
        try {
            String scaleIOVolumeId = ScaleIOUtil.getVolumePath(destVolumePath);
            final ScaleIOGatewayClient client = getScaleIOClient(destStore);
            Boolean deleteResult =  client.deleteVolume(scaleIOVolumeId);
            if (!deleteResult) {
                errMsg = "Failed to delete PowerFlex volume with id: " + scaleIOVolumeId;
                logger.warn(errMsg);
            }

        } catch (Exception e) {
            errMsg = "Unable to delete destination PowerFlex volume: " + destVolumePath + " due to " + e.getMessage();
            logger.warn(errMsg);
            throw new CloudRuntimeException(errMsg, e);
        }

        final long srcVolumeId = srcData.getId();
        if (srcVolumeId == destData.getId()) {
            VolumeVO volume = volumeDao.findById(srcVolumeId);
            volume.set_iScsiName(srcVolumePath);
            volume.setPath(srcVolumePath);
            volume.setFolder(srcVolumeFolder);
            volume.setPoolId(((VolumeInfo) srcData).getPoolId());
            volumeDao.update(srcVolumeId, volume);
        }
    }

    private Map<String, String> getVolumeDetails(VolumeInfo volumeInfo, DataStore dataStore) {
        long storagePoolId = dataStore.getId();
        StoragePoolVO storagePoolVO = storagePoolDao.findById(storagePoolId);

        if (!storagePoolVO.isManaged()) {
            return null;
        }

        Map<String, String> volumeDetails = new HashMap<>();

        VolumeVO volumeVO = volumeDao.findById(volumeInfo.getId());

        volumeDetails.put(DiskTO.STORAGE_HOST, storagePoolVO.getHostAddress());
        volumeDetails.put(DiskTO.STORAGE_PORT, String.valueOf(storagePoolVO.getPort()));
        volumeDetails.put(DiskTO.IQN, volumeVO.get_iScsiName());
        volumeDetails.put(DiskTO.PROTOCOL_TYPE, (volumeVO.getPoolType() != null) ? volumeVO.getPoolType().toString() : null);
        volumeDetails.put(StorageManager.STORAGE_POOL_DISK_WAIT.toString(), String.valueOf(StorageManager.STORAGE_POOL_DISK_WAIT.valueIn(storagePoolVO.getId())));

        volumeDetails.put(DiskTO.VOLUME_SIZE, String.valueOf(volumeVO.getSize()));
        volumeDetails.put(DiskTO.SCSI_NAA_DEVICE_ID, getVolumeProperty(volumeInfo.getId(), DiskTO.SCSI_NAA_DEVICE_ID));

        ChapInfo chapInfo = volumeService.getChapInfo(volumeInfo, dataStore);

        if (chapInfo != null) {
            volumeDetails.put(DiskTO.CHAP_INITIATOR_USERNAME, chapInfo.getInitiatorUsername());
            volumeDetails.put(DiskTO.CHAP_INITIATOR_SECRET, chapInfo.getInitiatorSecret());
            volumeDetails.put(DiskTO.CHAP_TARGET_USERNAME, chapInfo.getTargetUsername());
            volumeDetails.put(DiskTO.CHAP_TARGET_SECRET, chapInfo.getTargetSecret());
        }

        String systemId = null;
        StoragePoolDetailVO systemIdDetail = storagePoolDetailsDao.findDetail(storagePoolId, ScaleIOGatewayClient.STORAGE_POOL_SYSTEM_ID);
        if (systemIdDetail != null) {
            systemId = systemIdDetail.getValue();
        }
        volumeDetails.put(ScaleIOGatewayClient.STORAGE_POOL_SYSTEM_ID, systemId);

        return volumeDetails;
    }

    private String getVolumeProperty(long volumeId, String property) {
        VolumeDetailVO volumeDetails = volumeDetailsDao.findDetail(volumeId, property);

        if (volumeDetails != null) {
            return volumeDetails.getValue();
        }

        return null;
    }

    private Answer migrateVolume(DataObject srcData, DataObject destData) {
        // Volume migration within same PowerFlex/ScaleIO cluster (with same System ID)
        DataStore srcStore = srcData.getDataStore();
        DataStore destStore = destData.getDataStore();
        Answer answer = null;
        try {
            long srcPoolId = srcStore.getId();
            long destPoolId = destStore.getId();

            final ScaleIOGatewayClient client = getScaleIOClient(srcStore);
            final String srcVolumePath = ((VolumeInfo) srcData).getPath();
            final String srcVolumeId = ScaleIOUtil.getVolumePath(srcVolumePath);
            final StoragePoolVO destStoragePool = storagePoolDao.findById(destPoolId);
            final String destStoragePoolId = destStoragePool.getPath();
            int migrationTimeout = StorageManager.KvmStorageOfflineMigrationWait.value();
            boolean migrateStatus = client.migrateVolume(srcVolumeId, destStoragePoolId, migrationTimeout);
            if (migrateStatus) {
                String newVolumeName = String.format("%s-%s-%s-%s", ScaleIOUtil.VOLUME_PREFIX, destData.getId(),
                        destStoragePool.getUuid().split("-")[0].substring(4), ManagementServerImpl.customCsIdentifier.value());
                boolean renamed = client.renameVolume(srcVolumeId, newVolumeName);

                if (srcData.getId() != destData.getId()) {
                    VolumeVO destVolume = volumeDao.findById(destData.getId());
                    // Volume Id in the PowerFlex/ScaleIO pool remains the same after the migration
                    // Update PowerFlex volume name only after it is renamed, to maintain the consistency
                    if (renamed) {
                        String newVolumePath = ScaleIOUtil.updatedPathWithVolumeName(srcVolumeId, newVolumeName);
                        destVolume.set_iScsiName(newVolumePath);
                        destVolume.setPath(newVolumePath);
                    } else {
                        destVolume.set_iScsiName(srcVolumePath);
                        destVolume.setPath(srcVolumePath);
                    }
                    volumeDao.update(destData.getId(), destVolume);

                    VolumeVO srcVolume = volumeDao.findById(srcData.getId());
                    srcVolume.set_iScsiName(null);
                    srcVolume.setPath(null);
                    srcVolume.setFolder(null);
                    volumeDao.update(srcData.getId(), srcVolume);
                } else {
                    // Live migrate volume
                    VolumeVO volume = volumeDao.findById(srcData.getId());
                    Long oldPoolId = volume.getPoolId();
                    volume.setPoolId(destPoolId);
                    volume.setLastPoolId(oldPoolId);
                    volumeDao.update(srcData.getId(), volume);
                }

                List<SnapshotVO> snapshots = snapshotDao.listByVolumeId(srcData.getId());
                if (CollectionUtils.isNotEmpty(snapshots)) {
                    for (SnapshotVO snapshot : snapshots) {
                        SnapshotDataStoreVO snapshotStore = snapshotDataStoreDao.findByStoreSnapshot(DataStoreRole.Primary, srcPoolId, snapshot.getId());
                        if (snapshotStore == null) {
                            continue;
                        }

                        String snapshotVolumeId = ScaleIOUtil.getVolumePath(snapshotStore.getInstallPath());
                        String newSnapshotName = String.format("%s-%s-%s-%s", ScaleIOUtil.SNAPSHOT_PREFIX, snapshot.getId(),
                                destStoragePool.getUuid().split("-")[0].substring(4), ManagementServerImpl.customCsIdentifier.value());
                        renamed = client.renameVolume(snapshotVolumeId, newSnapshotName);

                        snapshotStore.setDataStoreId(destPoolId);
                        // Snapshot Id in the PowerFlex/ScaleIO pool remains the same after the migration
                        // Update PowerFlex snapshot name only after it is renamed, to maintain the consistency
                        if (renamed) {
                            snapshotStore.setInstallPath(ScaleIOUtil.updatedPathWithVolumeName(snapshotVolumeId, newSnapshotName));
                        }
                        snapshotDataStoreDao.update(snapshotStore.getId(), snapshotStore);
                    }
                }

                answer = new Answer(null, true, null);
            } else {
                String errorMsg = String.format("Failed to migrate PowerFlex volume: %s to storage pool %d", srcData, destPoolId);
                logger.debug(errorMsg);
                answer = new Answer(null, false, errorMsg);
            }
        } catch (Exception e) {
            logger.error("Failed to migrate PowerFlex volume: {} due to: {}", srcData, e.getMessage());
            answer = new Answer(null, false, e.getMessage());
        }

        return answer;
    }

    public boolean isSameScaleIOStorageInstance(DataStore srcStore, DataStore destStore) {
        long srcPoolId = srcStore.getId();
        String srcPoolSystemId = null;
        StoragePoolDetailVO srcPoolSystemIdDetail = storagePoolDetailsDao.findDetail(srcPoolId, ScaleIOGatewayClient.STORAGE_POOL_SYSTEM_ID);
        if (srcPoolSystemIdDetail != null) {
            srcPoolSystemId = srcPoolSystemIdDetail.getValue();
        }

        long destPoolId = destStore.getId();
        String destPoolSystemId = null;
        StoragePoolDetailVO destPoolSystemIdDetail = storagePoolDetailsDao.findDetail(destPoolId, ScaleIOGatewayClient.STORAGE_POOL_SYSTEM_ID);
        if (destPoolSystemIdDetail != null) {
            destPoolSystemId = destPoolSystemIdDetail.getValue();
        }

        if (StringUtils.isAnyEmpty(srcPoolSystemId, destPoolSystemId)) {
            throw new CloudRuntimeException("Failed to validate PowerFlex pools compatibility for migration as storage instance details are not available");
        }

        if (srcPoolSystemId.equals(destPoolSystemId)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean canCopy(DataObject srcData, DataObject destData) {
        DataStore srcStore = srcData.getDataStore();
        DataStore destStore = destData.getDataStore();
        if ((srcStore.getRole() == DataStoreRole.Primary && (srcData.getType() == DataObjectType.TEMPLATE || srcData.getType() == DataObjectType.VOLUME))
                && (destStore.getRole() == DataStoreRole.Primary && destData.getType() == DataObjectType.VOLUME)) {
            StoragePoolVO srcPoolVO = storagePoolDao.findById(srcStore.getId());
            StoragePoolVO destPoolVO = storagePoolDao.findById(destStore.getId());
            if (srcPoolVO != null && srcPoolVO.getPoolType() == Storage.StoragePoolType.PowerFlex
                    && destPoolVO != null && destPoolVO.getPoolType() == Storage.StoragePoolType.PowerFlex) {
                return true;
            }
        }
        return false;
    }

    private void resizeVolume(VolumeInfo volumeInfo) {
        logger.debug("Resizing PowerFlex volume");

        Preconditions.checkArgument(volumeInfo != null, "volumeInfo cannot be null");

        try {
            String scaleIOVolumeId = ScaleIOUtil.getVolumePath(volumeInfo.getPath());
            Long storagePoolId = volumeInfo.getPoolId();
            StoragePoolVO storagePool = storagePoolDao.findById(storagePoolId);
            final ScaleIOGatewayClient client = getScaleIOClient(storagePool);

            ResizeVolumePayload payload = (ResizeVolumePayload)volumeInfo.getpayload();
            long newSizeInBytes = payload.newSize != null ? payload.newSize : volumeInfo.getSize();
            // Only increase size is allowed and size should be specified in granularity of 8 GB
            if (newSizeInBytes < volumeInfo.getSize()) {
                throw new CloudRuntimeException("Only increase size is allowed for volume: " + volumeInfo.getName());
            }

            org.apache.cloudstack.storage.datastore.api.Volume scaleIOVolume = client.getVolume(scaleIOVolumeId);
            double newSizeInGB = newSizeInBytes / (1024.0 * 1024 * 1024);
            long newSizeIn8GBBoundary = (long) (Math.ceil(newSizeInGB / 8.0) * 8.0);

            if (scaleIOVolume.getSizeInKb() == newSizeIn8GBBoundary << 20) {
                logger.debug("No resize necessary at API");
            } else {
                scaleIOVolume = client.resizeVolume(scaleIOVolumeId, (int) newSizeIn8GBBoundary);
                if (scaleIOVolume == null) {
                    throw new CloudRuntimeException("Failed to resize volume: " + volumeInfo.getName());
                }
            }

            boolean attachedRunning = false;
            long hostId = 0;

            if (payload.instanceName != null) {
                VMInstanceVO instance = vmInstanceDao.findVMByInstanceName(payload.instanceName);
                if (instance != null && instance.getState().equals(VirtualMachine.State.Running)) {
                    hostId = instance.getHostId();
                    attachedRunning = true;
                }
            }

            Long newMaxIops = payload.newMaxIops != null ? payload.newMaxIops : volumeInfo.getMaxIops();
            long newBandwidthLimit = 0L;
            Long newDiskOfferingId = payload.newDiskOfferingId != null ? payload.newDiskOfferingId : volumeInfo.getDiskOfferingId();
            if (newDiskOfferingId != null) {
                DiskOfferingDetailVO bandwidthLimitDetail = diskOfferingDetailsDao.findDetail(newDiskOfferingId, Volume.BANDWIDTH_LIMIT_IN_MBPS);
                if (bandwidthLimitDetail != null) {
                    newBandwidthLimit = Long.parseLong(bandwidthLimitDetail.getValue()) * 1024;
                }
                DiskOfferingDetailVO iopsLimitDetail = diskOfferingDetailsDao.findDetail(newDiskOfferingId, Volume.IOPS_LIMIT);
                if (iopsLimitDetail != null) {
                    newMaxIops = Long.parseLong(iopsLimitDetail.getValue());
                }
            }

            if (volumeInfo.getFormat().equals(Storage.ImageFormat.QCOW2) || attachedRunning) {
                logger.debug("Volume needs to be resized at the hypervisor host");

                if (hostId == 0) {
                    hostId = selector.select(volumeInfo, true).getId();
                }

                HostVO host = hostDao.findById(hostId);
                if (host == null) {
                    throw new CloudRuntimeException("Found no hosts to run resize command on");
                }

                EndPoint ep = RemoteHostEndPoint.getHypervisorHostEndPoint(host);
                ResizeVolumeCommand resizeVolumeCommand = new ResizeVolumeCommand(
                        volumeInfo.getPath(), new StorageFilerTO(storagePool), volumeInfo.getSize(), newSizeInBytes,
                        payload.shrinkOk, payload.instanceName, volumeInfo.getChainInfo(),
                        volumeInfo.getPassphrase(), volumeInfo.getEncryptFormat());

                try {
                    VolumeVO volume = volumeDao.findById(volumeInfo.getId());
                    setVolumeLimitsOnSDC(volume, host, volumeInfo.getDataStore(), newMaxIops != null ? newMaxIops : 0L, newBandwidthLimit);
                    Answer answer = ep.sendMessage(resizeVolumeCommand);

                    if (!answer.getResult() && volumeInfo.getFormat().equals(Storage.ImageFormat.QCOW2)) {
                        throw new CloudRuntimeException("Failed to resize at host: " + answer.getDetails());
                    } else if (!answer.getResult()) {
                        // for non-qcow2, notifying the running VM is going to be best-effort since we can't roll back
                        // or avoid VM seeing a successful change at the PowerFlex volume after e.g. reboot
                        logger.warn("Resized raw volume, but failed to notify. VM will see change on reboot. Error:" + answer.getDetails());
                    } else {
                        logger.debug("Resized volume at host: " + answer.getDetails());
                    }
                } finally {
                    if (!attachedRunning) {
                        revokeAccess(volumeInfo, ep, volumeInfo.getDataStore());
                    }
                }
            }

            VolumeVO volume = volumeDao.findById(volumeInfo.getId());
            long oldVolumeSize = volume.getSize();
            volume.setSize(scaleIOVolume.getSizeInKb() * 1024);
            if (payload.newMinIops != null) {
                volume.setMinIops(payload.newMinIops);
            }
            if (payload.newMaxIops != null) {
                volume.setMaxIops(payload.newMaxIops);
            }
            volumeDao.update(volume.getId(), volume);
            if (payload.newDiskOfferingId != null) {
                volumeMgr.saveVolumeDetails(payload.newDiskOfferingId, volume.getId());
            }

            long capacityBytes = storagePool.getCapacityBytes();
            long usedBytes = storagePool.getUsedBytes();

            long newVolumeSize = volume.getSize();
            usedBytes += newVolumeSize - oldVolumeSize;
            storagePool.setUsedBytes(Math.min(usedBytes, capacityBytes));
            storagePoolDao.update(storagePoolId, storagePool);
        } catch (Exception e) {
            String errMsg = "Unable to resize PowerFlex volume: " + volumeInfo + " due to " + e.getMessage();
            logger.warn(errMsg);
            throw new CloudRuntimeException(errMsg, e);
        }
    }

    @Override
    public void resize(DataObject dataObject, AsyncCompletionCallback<CreateCmdResult> callback) {
        String scaleIOVolumePath = null;
        String errMsg = null;
        try {
            if (dataObject.getType() == DataObjectType.VOLUME) {
                scaleIOVolumePath = ((VolumeInfo) dataObject).getPath();
                resizeVolume((VolumeInfo) dataObject);
            } else {
                errMsg = "Invalid DataObjectType (" + dataObject.getType() + ") passed to resize";
            }
        } catch (Exception ex) {
            errMsg = ex.getMessage();
            logger.error(errMsg);
            if (callback == null) {
                throw ex;
            }
        }

        if (callback != null) {
            CreateCmdResult result = new CreateCmdResult(scaleIOVolumePath, new Answer(null, errMsg == null, errMsg));
            result.setResult(errMsg);
            callback.complete(result);
        }
    }

    @Override
    public long getVolumeSizeRequiredOnPool(long volumeSize, Long templateSize, boolean isEncryptionRequired) {
        double newSizeInGB = volumeSize / (1024.0 * 1024 * 1024);
        if (templateSize != null && isEncryptionRequired && needsExpansionForEncryptionHeader(templateSize, volumeSize)) {
            newSizeInGB = (volumeSize + (1<<30)) / (1024.0 * 1024 * 1024);
        }
        long newSizeIn8GBBoundary = (long) (Math.ceil(newSizeInGB / 8.0) * 8.0);
        return newSizeIn8GBBoundary * (1024 * 1024 * 1024);
    }

    @Override
    public void handleQualityOfServiceForVolumeMigration(VolumeInfo volumeInfo, QualityOfServiceState qualityOfServiceState) {
    }

    @Override
    public boolean canProvideStorageStats() {
        return true;
    }

    @Override
    public boolean poolProvidesCustomStorageStats() {
        return true;
    }

    @Override
    public Map<String, String> getCustomStorageStats(StoragePool pool) {
        Preconditions.checkArgument(pool != null, "pool cannot be null");
        Map<String, String> customStats = new HashMap<>();

        try {
            final ScaleIOGatewayClient client = getScaleIOClient(pool);
            int connectedSdcsCount = client.getConnectedSdcsCount();
            customStats.put(ScaleIOUtil.CONNECTED_SDC_COUNT_STAT, String.valueOf(connectedSdcsCount));
        } catch (Exception e) {
            logger.error("Unable to get custom storage stats for the pool: {} due to {}", pool, e.getMessage());
        }

        return customStats;
    }

    @Override
    public Pair<Long, Long> getStorageStats(StoragePool storagePool) {
        Preconditions.checkArgument(storagePool != null, "storagePool cannot be null");

        try {
            final ScaleIOGatewayClient client = getScaleIOClient(storagePool);
            StoragePoolStatistics poolStatistics = client.getStoragePoolStatistics(storagePool.getPath());
            if (poolStatistics != null && poolStatistics.getNetMaxCapacityInBytes() != null && poolStatistics.getNetUsedCapacityInBytes() != null) {
                Long capacityBytes = poolStatistics.getNetMaxCapacityInBytes();
                Long usedBytes = poolStatistics.getNetUsedCapacityInBytes();
                return new Pair<Long, Long>(capacityBytes, usedBytes);
            }
        } catch (Exception e) {
            String errMsg = "Unable to get storage stats for the pool: " + storagePool + " due to " + e.getMessage();
            logger.warn(errMsg);
            throw new CloudRuntimeException(errMsg, e);
        }

        return null;
    }

    @Override
    public boolean canProvideVolumeStats() {
        return true;
    }

    @Override
    public Pair<Long, Long> getVolumeStats(StoragePool storagePool, String volumePath) {
        Preconditions.checkArgument(storagePool != null, "storagePool cannot be null");
        Preconditions.checkArgument(StringUtils.isNotEmpty(volumePath), "volumePath cannot be null");

        try {
            final ScaleIOGatewayClient client = getScaleIOClient(storagePool);
            VolumeStatistics volumeStatistics = client.getVolumeStatistics(ScaleIOUtil.getVolumePath(volumePath));
            if (volumeStatistics != null) {
                Long provisionedSizeInBytes = volumeStatistics.getNetProvisionedAddressesInBytes();
                Long allocatedSizeInBytes = volumeStatistics.getAllocatedSizeInBytes();
                return new Pair<Long, Long>(provisionedSizeInBytes, allocatedSizeInBytes);
            }
        }  catch (Exception e) {
            String errMsg = "Unable to get stats for the volume: " + volumePath + " in the pool: " + storagePool + " due to " + e.getMessage();
            logger.warn(errMsg);
            throw new CloudRuntimeException(errMsg, e);
        }

        return null;
    }

    @Override
    public boolean canHostAccessStoragePool(Host host, StoragePool pool) {
        if (host == null || pool == null) {
            return false;
        }

        try {
            StoragePoolHostVO poolHostVO = storagePoolHostDao.findByPoolHost(pool.getId(), host.getId());
            if (poolHostVO == null) {
                return false;
            }
            final ScaleIOGatewayClient client = getScaleIOClient(pool);
            return client.isSdcConnected(poolHostVO.getLocalPath());
        } catch (Exception e) {
            logger.warn("Unable to check the host: {} access to storage pool: {} due to {}", host, pool, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean canHostPrepareStoragePoolAccess(Host host, StoragePool pool) {
        if (host == null || pool == null) {
            return false;
        }

        sdcManager = ComponentContext.inject(sdcManager);
        return sdcManager.areSDCConnectionsWithinLimit(pool.getId());
    }

    @Override
    public boolean canDisconnectHostFromStoragePool(Host host, StoragePool pool) {
        if (host == null || pool == null) {
            return false;
        }

        StoragePoolHostVO poolHostVO = storagePoolHostDao.findByPoolHost(pool.getId(), host.getId());
        if (poolHostVO == null) {
            return false;
        }

        final String sdcId = poolHostVO.getLocalPath();
        if (StringUtils.isBlank(sdcId)) {
            return false;
        }

        List<StoragePoolHostVO> poolHostVOsBySdc = storagePoolHostDao.findByLocalPath(sdcId);
        if (CollectionUtils.isNotEmpty(poolHostVOsBySdc) && poolHostVOsBySdc.size() > 1) {
            logger.debug(String.format("There are other connected pools with the same SDC of the host %s, shouldn't disconnect SDC", host));
            return false;
        }

        try {
            final ScaleIOGatewayClient client = getScaleIOClient(pool);
            return client.listVolumesMappedToSdc(sdcId).isEmpty();
        } catch (Exception e) {
            logger.warn("Unable to check whether the host: " + host.getId() + " can be disconnected from storage pool: " + pool.getId() + ", due to " + e.getMessage(), e);
            return false;
        }
    }

    private void alertHostSdcDisconnection(Host host) {
        if (host == null) {
            return;
        }

        logger.warn("SDC not connected on the host: {}", host);
        String msg = String.format("SDC not connected on the host: %s, reconnect the SDC to MDM", host);
        alertMgr.sendAlert(AlertManager.AlertType.ALERT_TYPE_HOST, host.getDataCenterId(), host.getPodId(), "SDC disconnected on host: " + host.getUuid(), msg);
    }

    @Override
    public boolean isVmInfoNeeded() {
        return false;
    }

    @Override
    public void provideVmInfo(long vmId, long volumeId) {
    }

    @Override
    public boolean isVmTagsNeeded(String tagKey) {
        return false;
    }

    @Override
    public void provideVmTags(long vmId, long volumeId, String tagValue) {
    }

    /**
     * Does the destination size fit the source size plus an encryption header?
     * @param srcSize size of source
     * @param dstSize size of destination
     * @return true if resize is required
     */
    private boolean needsExpansionForEncryptionHeader(long srcSize, long dstSize) {
        int headerSize = 32<<20; // ensure we have 32MiB for encryption header
        return srcSize + headerSize > dstSize;
    }

    /**
     * Does any object require encryption support?
     */
    protected boolean anyVolumeRequiresEncryption(DataObject ... objects) {
        for (DataObject o : objects) {
            if (o instanceof VolumeInfo && ((VolumeInfo) o).getPassphraseId() != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isStorageSupportHA(StoragePoolType type) {
        return false;
    }

    @Override
    public void detachVolumeFromAllStorageNodes(Volume volume) {
    }

    @Override
    public boolean volumesRequireGrantAccessWhenUsed() {
        return true;
    }

    @Override
    public boolean zoneWideVolumesAvailableWithoutClusterMotion() {
        return true;
    }

    @Override
    public boolean canDisplayDetails() {
        return false;
    }
}
