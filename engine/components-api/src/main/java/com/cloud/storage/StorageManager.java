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
package com.cloud.storage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.HypervisorHostListener;
import org.apache.cloudstack.engine.subsystem.api.storage.Scope;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.ModifyStoragePoolAnswer;
import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.agent.api.to.DataTO;
import com.cloud.agent.api.to.DiskTO;
import com.cloud.agent.manager.Commands;
import com.cloud.capacity.CapacityVO;
import com.cloud.exception.ConnectionException;
import com.cloud.exception.StorageConflictException;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.host.Host;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.offering.DiskOffering;
import com.cloud.offering.ServiceOffering;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.utils.Pair;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.DiskProfile;
import com.cloud.vm.VMInstanceVO;

public interface StorageManager extends StorageService {
    ConfigKey<Boolean> StorageCleanupEnabled = new ConfigKey<>(Boolean.class,
            "storage.cleanup.enabled",
            "Advanced",
            "true",
            "Enables/disables the storage cleanup thread.",
            false,
            ConfigKey.Scope.Global,
            null);
    ConfigKey<Integer> StorageCleanupInterval = new ConfigKey<>(Integer.class,
            "storage.cleanup.interval",
            "Advanced",
            "86400",
            "The interval (in seconds) to wait before running the storage cleanup thread.",
            false,
            ConfigKey.Scope.Global,
            null,
            StorageCleanupEnabled.key());
    ConfigKey<Integer> StorageCleanupDelay = new ConfigKey<>(Integer.class,
            "storage.cleanup.delay",
            "Advanced",
            "86400",
            "Determines how long (in seconds) to wait before actually expunging destroyed volumes. The default value = the default value of storage.cleanup.interval.",
            false,
            ConfigKey.Scope.Global,
            null,
            StorageCleanupEnabled.key());
    ConfigKey<Boolean> TemplateCleanupEnabled = new ConfigKey<>(Boolean.class,
            "storage.template.cleanup.enabled",
            "Storage",
            "true",
            "Enable/disable template cleanup activity, only take effect when overall storage cleanup is enabled",
            false,
            ConfigKey.Scope.Global,
            null,
            StorageCleanupEnabled.key());
    ConfigKey<Integer> KvmStorageOfflineMigrationWait = new ConfigKey<>(Integer.class,
            "kvm.storage.offline.migration.wait",
            "Storage",
            "10800",
            "Timeout in seconds for offline (non-live) storage migration to complete on KVM",
            true,
            ConfigKey.Scope.Global,
            null);
    ConfigKey<Integer> KvmStorageOnlineMigrationWait = new ConfigKey<>(Integer.class,
            "kvm.storage.online.migration.wait",
            "Storage",
            "86400",
            "Timeout in seconds for online (live) storage migration to complete on KVM (migrateVirtualMachineWithVolume)",
            true,
            ConfigKey.Scope.Global,
            null);
    ConfigKey<Boolean> KvmAutoConvergence = new ConfigKey<>(Boolean.class,
            "kvm.auto.convergence",
            "Storage",
            "false",
            "Setting this to 'true' allows KVM to use auto convergence to complete VM migration (libvirt version 1.2.3+ and QEMU version 1.6+)",
            true,
            ConfigKey.Scope.Global,
            null);
    ConfigKey<Integer> MaxNumberOfManagedClusteredFileSystems = new ConfigKey<>(Integer.class,
            "max.number.managed.clustered.file.systems",
            "Storage",
            "200",
            "XenServer and VMware only: Maximum number of managed SRs or datastores per compute cluster",
            true,
            ConfigKey.Scope.Cluster,
            null);

    ConfigKey<Integer> STORAGE_POOL_DISK_WAIT = new ConfigKey<>(Integer.class,
            "storage.pool.disk.wait",
            "Storage",
            "60",
            "Timeout (in secs) for the storage pool disk (of managed pool) to become available in the host. Currently supported for PowerFlex only.",
            true,
            ConfigKey.Scope.StoragePool,
            null);

    ConfigKey<Integer> STORAGE_POOL_CLIENT_TIMEOUT = new ConfigKey<>(Integer.class,
            "storage.pool.client.timeout",
            "Storage",
            "60",
            "Timeout (in secs) for the API client connection timeout of storage pool (for managed pools). Currently supported for PowerFlex only.",
            false,
            ConfigKey.Scope.StoragePool,
            null);

    ConfigKey<Integer> STORAGE_POOL_CLIENT_MAX_CONNECTIONS = new ConfigKey<>(Integer.class,
            "storage.pool.client.max.connections",
            "Storage",
            "100",
            "Maximum connections for the API client of storage pool (for managed pools). Currently supported for PowerFlex only.",
            false,
            ConfigKey.Scope.StoragePool,
            null);

    ConfigKey<Integer> STORAGE_POOL_CONNECTED_CLIENTS_LIMIT = new ConfigKey<>(Integer.class,
            "storage.pool.connected.clients.limit",
            "Storage",
            "-1",
            "Maximum connected storage pool clients supported for the storage (for managed pools), <= 0 for unlimited (default: -1). Currently supported for PowerFlex only.",
            true,
            ConfigKey.Scope.StoragePool,
            null);

    ConfigKey<String> STORAGE_POOL_IO_POLICY = new ConfigKey<>(String.class,
            "kvm.storage.pool.io.policy",
            "Storage",
            null,
            "IO driver policy - 'threads', 'native' or 'io_uring'. If the IO policy is set for a specific storage and enabled in the VM settings this option will override be overridden from the VM's setting",
            false,
            ConfigKey.Scope.StoragePool,
            null);

    ConfigKey<Integer> PRIMARY_STORAGE_DOWNLOAD_WAIT = new ConfigKey<Integer>("Storage", Integer.class, "primary.storage.download.wait", "10800",
            "In second, timeout for download template to primary storage", false);

    ConfigKey<Integer>  SecStorageMaxMigrateSessions = new ConfigKey<Integer>("Advanced", Integer.class, "secstorage.max.migrate.sessions", "2",
            "The max number of concurrent copy command execution sessions that an SSVM can handle", false, ConfigKey.Scope.Global);

    ConfigKey<Boolean>  SecStorageVMAutoScaleDown = new ConfigKey<Boolean>("Advanced", Boolean.class, "secstorage.vm.auto.scale.down", "false",
            "Setting this to 'true' will auto scale down SSVMs", true, ConfigKey.Scope.Global);

    ConfigKey<Integer> MaxDataMigrationWaitTime = new ConfigKey<Integer>("Advanced", Integer.class, "max.data.migration.wait.time", "15",
            "Maximum wait time (in minutes) for a data migration task before spawning a new SSVM", false, ConfigKey.Scope.Global);
    ConfigKey<Boolean> DiskProvisioningStrictness = new ConfigKey<Boolean>("Storage", Boolean.class, "disk.provisioning.type.strictness", "false",
            "If set to true, the disk is created only when there is a suitable storage pool that supports the disk provisioning type specified by the service/disk offering. " +
                    "If set to false, the disk is created with a disk provisioning type supported by the pool. Default value is false, and this is currently supported for VMware only.",
            true, ConfigKey.Scope.Zone);
    ConfigKey<String> PreferredStoragePool = new ConfigKey<String>(String.class, "preferred.storage.pool", "Advanced", "",
            "The UUID of preferred storage pool for allocation.", true, ConfigKey.Scope.Account, null);

    ConfigKey<Boolean> MountDisabledStoragePool = new ConfigKey<>(Boolean.class,
            "mount.disabled.storage.pool",
            "Storage",
            "false",
            "Mount all zone-wide or cluster-wide disabled storage pools after node reboot",
            true,
            ConfigKey.Scope.Cluster,
            null);
    ConfigKey<Boolean> VmwareCreateCloneFull = new ConfigKey<>(Boolean.class,
            "vmware.create.full.clone",
            "Storage",
            "false",
            "If set to true, creates VMs as full clones on ESX hypervisor",
            true,
            ConfigKey.Scope.StoragePool,
            null);
    ConfigKey<Boolean> VmwareAllowParallelExecution = new ConfigKey<>(Boolean.class,
            "vmware.allow.parallel.command.execution",
            "Advanced",
            "false",
            "allow commands to be executed in parallel in spite of 'vmware.create.full.clone' being set to true.",
            true,
            ConfigKey.Scope.Global,
            null);
    static final ConfigKey<Boolean> DataStoreDownloadFollowRedirects = new ConfigKey<>(ConfigKey.CATEGORY_ADVANCED,
            Boolean.class, "store.download.follow.redirects", "false",
            "Whether HTTP redirect is followed during store downloads for objects such as template, volume etc.",
            true, ConfigKey.Scope.Global);

    ConfigKey<Long> HEURISTICS_SCRIPT_TIMEOUT = new ConfigKey<>("Advanced", Long.class, "heuristics.script.timeout", "3000",
            "The maximum runtime, in milliseconds, to execute the heuristic rule; if it is reached, a timeout will happen.", true);

    ConfigKey<Boolean> AllowVolumeReSizeBeyondAllocation = new ConfigKey<Boolean>("Advanced", Boolean.class, "volume.resize.allowed.beyond.allocation", "false",
            "Determines whether volume size can exceed the pool capacity allocation disable threshold (pool.storage.allocated.capacity.disablethreshold) " +
                    "when resize a volume upto resize capacity disable threshold (pool.storage.allocated.resize.capacity.disablethreshold)",
            true, List.of(ConfigKey.Scope.StoragePool, ConfigKey.Scope.Zone));

    ConfigKey<Integer> StoragePoolHostConnectWorkers = new ConfigKey<>("Storage", Integer.class,
            "storage.pool.host.connect.workers", "1",
            "Number of worker threads to be used to connect hosts to a primary storage", true);

    ConfigKey<Float> ObjectStorageCapacityThreshold = new ConfigKey<>("Alert", Float.class,
            "objectStorage.capacity.notificationthreshold",
            "0.75",
            "Percentage (as a value between 0 and 1) of object storage utilization above which alerts will be sent about low storage available.",
            true,
            ConfigKey.Scope.Global,
            null);

    /**
     * should we execute in sequence not involving any storages?
     * @return tru if commands should execute in sequence
     */
    static boolean shouldExecuteInSequenceOnVmware() {
        return shouldExecuteInSequenceOnVmware(null, null);
    }

    static boolean shouldExecuteInSequenceOnVmware(Long srcStoreId, Long dstStoreId) {
        final Boolean fullClone = getFullCloneConfiguration(srcStoreId) || getFullCloneConfiguration(dstStoreId);
        final Boolean allowParallel = getAllowParallelExecutionConfiguration();
        return fullClone && !allowParallel;
    }

    static Boolean getAllowParallelExecutionConfiguration() {
        return VmwareAllowParallelExecution.value();
    }

    static Boolean getFullCloneConfiguration(Long storeId) {
        return VmwareCreateCloneFull.valueIn(storeId);
    }

    /**
     * Returns a comma separated list of tags for the specified storage pool
     * @param poolId
     * @return comma separated list of tags
     */
    String getStoragePoolTags(long poolId);

    /**
     * Returns a list of Strings with tags for the specified storage pool
     * @param poolId
     * @return comma separated list of tags
     */
    List<String> getStoragePoolTagList(long poolId);

    Answer sendToPool(long poolId, Command cmd) throws StorageUnavailableException;

    Answer sendToPool(StoragePool pool, Command cmd) throws StorageUnavailableException;

    Answer[] sendToPool(long poolId, Commands cmd) throws StorageUnavailableException;

    Answer[] sendToPool(StoragePool pool, Commands cmds) throws StorageUnavailableException;

    Pair<Long, Answer[]> sendToPool(StoragePool pool, long[] hostIdsToTryFirst, List<Long> hostIdsToAvoid, Commands cmds) throws StorageUnavailableException;

    Pair<Long, Answer> sendToPool(StoragePool pool, long[] hostIdsToTryFirst, List<Long> hostIdsToAvoid, Command cmd) throws StorageUnavailableException;

    public Answer getVolumeStats(StoragePool pool, Command cmd);

    boolean canPoolProvideStorageStats(StoragePool pool);

    boolean poolProvidesCustomStorageStats(StoragePool pool);

    Map<String, String> getCustomStorageStats(StoragePool pool);

    /**
     * Checks if a host has running VMs that are using its local storage pool.
     * @return true if local storage is active on the host
     */
    boolean isLocalStorageActiveOnHost(Long hostId);

    /**
     * Cleans up storage pools by removing unused templates.
     * @param recurring - true if this cleanup is part of a recurring garbage collection thread
     */
    void cleanupStorage(boolean recurring);

    String getPrimaryStorageNameLabel(VolumeVO volume);

    void createCapacityEntry(StoragePoolVO storagePool, short capacityType, long allocated);

    Answer sendToPool(StoragePool pool, long[] hostIdsToTryFirst, Command cmd) throws StorageUnavailableException;

    CapacityVO getSecondaryStorageUsedStats(Long hostId, Long zoneId);

    CapacityVO getStoragePoolUsedStats(Long poolId, Long clusterId, Long podId, Long zoneId);

    CapacityVO getStoragePoolUsedStats(Long zoneId, Long podId, Long clusterId, List<Long> poolIds);

    List<StoragePoolVO> ListByDataCenterHypervisor(long datacenterId, HypervisorType type);

    List<VMInstanceVO> listByStoragePool(long storagePoolId);

    StoragePoolVO findLocalStorageOnHost(long hostId);

    Host findUpAndEnabledHostWithAccessToStoragePools(List<Long> poolIds);

    List<StoragePoolHostVO> findStoragePoolsConnectedToHost(long hostId);

    boolean canHostAccessStoragePool(Host host, StoragePool pool);

    boolean canHostPrepareStoragePoolAccess(Host host, StoragePool pool);

    boolean canDisconnectHostFromStoragePool(Host host, StoragePool pool);

    Host getHost(long hostId);

    Host updateSecondaryStorage(long secStorageId, String newUrl);

    void removeStoragePoolFromCluster(long hostId, String iScsiName, StoragePool storagePool);

    List<Long> getUpHostsInPool(long poolId);

    void cleanupSecondaryStorage(boolean recurring);

    HypervisorType getHypervisorTypeFromFormat(ImageFormat format);

    boolean storagePoolHasEnoughIops(List<Pair<Volume, DiskProfile>> volumeDiskProfilePairs, StoragePool pool);

    boolean storagePoolHasEnoughIops(Long requestedIops, StoragePool pool);
    boolean storagePoolHasEnoughSpace(Long size, StoragePool pool);

    boolean storagePoolHasEnoughSpace(List<Pair<Volume, DiskProfile>> volumeDiskProfilePairs, StoragePool pool);

    /**
     * This comment is relevant to managed storage only.
     *
     *  Long clusterId = only used for managed storage
     *
     *  Some managed storage can be more efficient handling VM templates (via cloning) if it knows the capabilities of the compute cluster it is dealing with.
     *  If the compute cluster supports UUID resigning and the storage system can clone a volume from a volume, then this determines how much more space a
     *  new root volume (that makes use of a template) will take up on the storage system.
     *
     *  For example, if a storage system can clone a volume from a volume and the compute cluster supports UUID resigning (relevant for hypervisors like
     *  XenServer and ESXi that put virtual disks in clustered file systems), then the storage system will need to determine if it already has a copy of
     *  the template or if it will need to create one first before cloning the template to a new volume to be used for the new root disk (assuming the root
     *  disk is being deployed from a template). If the template doesn't already exists on the storage system, then you need to take into consideration space
     *  required for that template (stored in one volume) and space required for a new volume created from that template volume (for your new root volume).
     *
     *  If UUID resigning is not available in the compute cluster or the storage system doesn't support cloning a volume from a volume, then for each new
     *  root disk that uses a template, CloudStack will have the template be copied down to a newly created volume on the storage system (i.e. no need
     *  to take into consideration the possible need to first create a volume on the storage system for a template that will be used for the root disk
     *  via cloning).
     *
     *  Cloning volumes on the back-end instead of copying down a new template for each new volume helps to alleviate load on the hypervisors.
     */
    boolean storagePoolHasEnoughSpace(List<Pair<Volume, DiskProfile>> volume, StoragePool pool, Long clusterId);

    boolean storagePoolHasEnoughSpaceForResize(StoragePool pool, long currentSize, long newSize);

    boolean storagePoolCompatibleWithVolumePool(StoragePool pool, Volume volume);

    boolean isStoragePoolCompliantWithStoragePolicy(List<Pair<Volume, DiskProfile>> volumes, StoragePool pool) throws StorageUnavailableException;

    boolean isStoragePoolCompliantWithStoragePolicy(long diskOfferingId, StoragePool pool) throws StorageUnavailableException;

    boolean registerHostListener(String providerUuid, HypervisorHostListener listener);

    Pair<Map<String, String>, Boolean> getStoragePoolNFSMountOpts(StoragePool pool, Map<String, String> details);

    String getStoragePoolMountFailureReason(String error);

    void connectHostsToPool(DataStore primaryStore, List<Long> hostIds, Scope scope,
            boolean handleStorageConflictException, boolean errorOnNoUpHost) throws CloudRuntimeException;

    boolean connectHostToSharedPool(Host host, long poolId) throws StorageUnavailableException, StorageConflictException;

    void disconnectHostFromSharedPool(Host host, StoragePool pool) throws StorageUnavailableException, StorageConflictException;

    void enableHost(long hostId) throws StorageUnavailableException, StorageConflictException;

    void createCapacityEntry(long poolId);

    DataStore createLocalStorage(Host host, StoragePoolInfo poolInfo) throws ConnectionException;

    BigDecimal getStorageOverProvisioningFactor(Long dcId);

    Long getDiskBytesReadRate(ServiceOffering offering, DiskOffering diskOffering);

    Long getDiskBytesWriteRate(ServiceOffering offering, DiskOffering diskOffering);

    Long getDiskIopsReadRate(ServiceOffering offering, DiskOffering diskOffering);

    Long getDiskIopsWriteRate(ServiceOffering offering, DiskOffering diskOffering);

    ImageStore updateImageStoreStatus(Long id, String name, Boolean readonly, Long capacityBytes);

    void cleanupDownloadUrls();

    void setDiskProfileThrottling(DiskProfile dskCh, ServiceOffering offering, DiskOffering diskOffering);

    DiskTO getDiskWithThrottling(DataTO volTO, Volume.Type volumeType, long deviceId, String path, long offeringId, long diskOfferingId);

    boolean isStoragePoolDatastoreClusterParent(StoragePool pool);

    void syncDatastoreClusterStoragePool(long datastoreClusterPoolId, List<ModifyStoragePoolAnswer> childDatastoreAnswerList, long hostId);

    void validateChildDatastoresToBeAddedInUpState(StoragePoolVO datastoreClusterPool, List<ModifyStoragePoolAnswer> childDatastoreAnswerList);

    boolean checkIfHostAndStoragePoolHasCommonStorageAccessGroups(Host host, StoragePool pool);

    Pair<Boolean, String> checkIfReadyVolumeFitsInStoragePoolWithStorageAccessGroups(StoragePool destPool, Volume volume);

    String[] getStorageAccessGroups(Long zoneId, Long podId, Long clusterId, Long hostId);

    CapacityVO getObjectStorageUsedStats(Long zoneId);
}
