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
package com.cloud.api.query.vo;

import com.cloud.offering.ServiceOffering.State;
import com.cloud.storage.Storage;
import com.cloud.utils.db.GenericDao;
import org.apache.cloudstack.api.Identity;
import org.apache.cloudstack.api.InternalIdentity;
import org.apache.cloudstack.vm.lease.VMLeaseManager;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "service_offering_view")
public class ServiceOfferingJoinVO extends BaseViewVO implements InternalIdentity, Identity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private long id;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "name")
    private String name;

    @Column(name = "state")
    @Enumerated(value = EnumType.STRING)
    private State state;

    @Column(name = "display_text")
    private String displayText;

    @Column(name = "provisioning_type")
    Storage.ProvisioningType provisioningType;

    @Column(name = "tags", length = 4096)
    String tags;

    @Column(name = "use_local_storage")
    private boolean useLocalStorage;

    @Column(name = "system_use")
    private boolean systemUse;

    @Column(name = "cpu")
    private Integer cpu;

    @Column(name = "speed")
    private Integer speed;

    @Column(name = "ram_size")
    private Integer ramSize;

    @Column(name = "nw_rate")
    private Integer rateMbps;

    @Column(name = "mc_rate")
    private Integer multicastRateMbps;

    @Column(name = "ha_enabled")
    private boolean offerHA;

    @Column(name = "limit_cpu_use")
    private boolean limitCpuUse;

    @Column(name = "is_volatile")
    private boolean volatileVm;

    @Column(name = "host_tag")
    private String hostTag;

    @Column(name = "default_use")
    private boolean defaultUse;

    @Column(name = "vm_type")
    private String vmType;

    @Column(name = "customized_iops")
    private Boolean customizedIops;

    @Column(name = "min_iops")
    private Long minIops;

    @Column(name = "max_iops")
    private Long maxIops;

    @Column(name = "hv_ss_reserve")
    private Integer hypervisorSnapshotReserve;

    @Column(name = "sort_key")
    int sortKey;

    @Column(name = "bytes_read_rate")
    Long bytesReadRate;

    @Column(name = "bytes_read_rate_max")
    Long bytesReadRateMax;

    @Column(name = "bytes_read_rate_max_length")
    Long bytesReadRateMaxLength;

    @Column(name = "bytes_write_rate")
    Long bytesWriteRate;

    @Column(name = "bytes_write_rate_max")
    Long bytesWriteRateMax;

    @Column(name = "bytes_write_rate_max_length")
    Long bytesWriteRateMaxLength;

    @Column(name = "iops_read_rate")
    Long iopsReadRate;

    @Column(name = "iops_read_rate_max")
    Long iopsReadRateMax;

    @Column(name = "iops_read_rate_max_length")
    Long iopsReadRateMaxLength;

    @Column(name = "iops_write_rate")
    Long iopsWriteRate;

    @Column(name = "iops_write_rate_max")
    Long iopsWriteRateMax;

    @Column(name = "iops_write_rate_max_length")
    Long iopsWriteRateMaxLength;

    @Column(name = GenericDao.CREATED_COLUMN)
    private Date created;

    @Column(name = GenericDao.REMOVED_COLUMN)
    private Date removed;

    @Column(name = "domain_id")
    private String domainId;

    @Column(name = "domain_uuid")
    private String domainUuid;

    @Column(name = "domain_name")
    private String domainName = null;

    @Column(name = "domain_path")
    private String domainPath = null;

    @Column(name = "zone_id")
    private String zoneId = null;

    @Column(name = "zone_uuid")
    private String zoneUuid = null;

    @Column(name = "zone_name")
    private String zoneName = null;

    @Column(name = "deployment_planner")
    private String deploymentPlanner;

    @Column(name = "cache_mode")
    String cacheMode;

    @Column(name = "min_cpu")
    Integer minCpu;

    @Column(name = "max_cpu")
    Integer maxCpu;

    @Column(name = "min_memory")
    Integer minMemory;

    @Column(name = "max_memory")
    Integer maxMemory;

    @Column(name = "vsphere_storage_policy")
    String vsphereStoragePolicy;

    @Column(name = "root_disk_size")
    private Long rootDiskSize;

    @Column(name = "dynamic_scaling_enabled")
    private boolean dynamicScalingEnabled;

    @Column(name = "disk_offering_strictness")
    private boolean diskOfferingStrictness;

    @Column(name = "disk_offering_id")
    private long diskOfferingId;

    @Column(name = "disk_offering_uuid")
    private String diskOfferingUuid;

    @Column(name = "disk_offering_name")
    private String diskOfferingName;

    @Column(name = "disk_offering_display_text")
    private String diskOfferingDisplayText;

    @Column(name = "encrypt_root")
    private boolean encryptRoot;

    @Column(name = "lease_duration")
    private Integer leaseDuration;

    @Column(name = "lease_expiry_action")
    @Enumerated(value = EnumType.STRING)
    private VMLeaseManager.ExpiryAction leaseExpiryAction;

    @Column(name = "gpu_card_id")
    private Long gpuCardId;

    @Column(name = "gpu_card_uuid")
    private String gpuCardUuid;

    @Column(name = "gpu_card_name")
    private String gpuCardName;

    @Column(name = "vgpu_profile_id")
    private Long vgpuProfileId;

    @Column(name = "vgpu_profile_uuid")
    private String vgpuProfileUuid;

    @Column(name = "vgpu_profile_name")
    private String vgpuProfileName;

    @Column(name = "vgpu_profile_video_ram")
    private Long videoRam;

    @Column(name = "vgpu_profile_max_heads")
    private Long maxHeads;

    @Column(name = "vgpu_profile_max_resolution_x")
    private Long maxResolutionX;

    @Column(name = "vgpu_profile_max_resolution_y")
    private Long maxResolutionY;

    @Column(name = "gpu_count")
    private Integer gpuCount;

    @Column(name = "gpu_display")
    private Boolean gpuDisplay;

    public ServiceOfferingJoinVO() {
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public State getState() {
        return state;
    }

    public String getDisplayText() {
        return displayText;
    }

    public Storage.ProvisioningType getProvisioningType(){
        return provisioningType;
    }

    public String getTags() {
        return tags;
    }

    public boolean isUseLocalStorage() {
        return useLocalStorage;
    }

    public boolean isSystemUse() {
        return systemUse;
    }

    public Date getCreated() {
        return created;
    }

    public Date getRemoved() {
        return removed;
    }

    public String getDomainId() {
        return domainId;
    }

    public String getDomainUuid() {
        return domainUuid;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getDomainPath() {
        return domainPath;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneUuid() {
        return zoneUuid;
    }

    public String getZoneName() {
        return zoneName;
    }

    public Boolean isCustomizedIops() {
        return customizedIops;
    }

    public Long getMinIops() {
        return minIops;
    }

    public Long getMaxIops() {
        return maxIops;
    }

    public Integer getHypervisorSnapshotReserve() {
        return hypervisorSnapshotReserve;
    }

    public int getSortKey() {
        return sortKey;
    }

    public Integer getCpu() {
        return cpu;
    }

    public Integer getSpeed() {
        return speed;
    }

    public Integer getRamSize() {
        return ramSize;
    }

    public Integer getRateMbps() {
        return rateMbps;
    }

    public Integer getMulticastRateMbps() {
        return multicastRateMbps;
    }

    public boolean isOfferHA() {
        return offerHA;
    }

    public boolean isLimitCpuUse() {
        return limitCpuUse;
    }

    public String getHostTag() {
        return hostTag;
    }

    public boolean isDefaultUse() {
        return defaultUse;
    }

    public String getSystemVmType() {
        return vmType;
    }

    public String getDeploymentPlanner() {
        return deploymentPlanner;
    }

    public boolean getVolatileVm() {
        return volatileVm;
    }

    public Long getBytesReadRate() {
        return bytesReadRate;
    }

    public Long getBytesReadRateMax() { return bytesReadRateMax; }

    public Long getBytesReadRateMaxLength() { return bytesReadRateMaxLength; }

    public Long getBytesWriteRate() {
        return bytesWriteRate;
    }

    public Long getBytesWriteRateMax() { return bytesWriteRateMax; }

    public Long getBytesWriteRateMaxLength() { return bytesWriteRateMaxLength; }

    public Long getIopsReadRate() {
        return iopsReadRate;
    }

    public Long getIopsReadRateMax() { return iopsReadRateMax; }

    public Long getIopsReadRateMaxLength() { return iopsReadRateMaxLength; }

    public Long getIopsWriteRate() {
        return iopsWriteRate;
    }

    public Long getIopsWriteRateMax() { return iopsWriteRateMax; }

    public Long getIopsWriteRateMaxLength() { return iopsWriteRateMaxLength; }

    public boolean isDynamic() {
        return cpu == null || speed == null || ramSize == null;
    }

    public String getCacheMode() {
        return cacheMode;
    }

    public Integer getMinCpu() {
        return minCpu;
    }

    public Integer getMaxCpu() {
        return maxCpu;
    }

    public Integer getMinMemory() {
        return minMemory;
    }

    public Integer getMaxMemory() {
        return maxMemory;
    }

    public String getVsphereStoragePolicy() {
        return vsphereStoragePolicy;
    }

    public Long getRootDiskSize() {
        return rootDiskSize ;
    }

    public boolean isDynamicScalingEnabled() {
        return dynamicScalingEnabled;
    }

    public void setDynamicScalingEnabled(boolean dynamicScalingEnabled) {
        this.dynamicScalingEnabled = dynamicScalingEnabled;
    }

    public boolean getDiskOfferingStrictness() {
        return diskOfferingStrictness;
    }

    public long getDiskOfferingId() {
        return diskOfferingId;
    }

    public String getDiskOfferingUuid() {
        return diskOfferingUuid;
    }

    public String getDiskOfferingName() {
        return diskOfferingName;
    }

    public String getDiskOfferingDisplayText() {
        return diskOfferingDisplayText;
    }

    public boolean getEncryptRoot() { return encryptRoot; }

    public Integer getLeaseDuration() {
        return leaseDuration;
    }

    public VMLeaseManager.ExpiryAction getLeaseExpiryAction() {
        return leaseExpiryAction;
    }

    public Long getGpuCardId() {
        return gpuCardId;
    }

    public String getGpuCardUuid() {
        return gpuCardUuid;
    }

    public String getGpuCardName() {
        return gpuCardName;
    }

    public Long getVgpuProfileId() {
        return vgpuProfileId;
    }

    public String getVgpuProfileUuid() {
        return vgpuProfileUuid;
    }

    public String getVgpuProfileName() {
        return vgpuProfileName;
    }

    public Long getMaxResolutionY() {
        return maxResolutionY;
    }

    public Long getMaxResolutionX() {
        return maxResolutionX;
    }

    public Long getMaxHeads() {
        return maxHeads;
    }

    public Long getVideoRam() {
        return videoRam;
    }

    public Integer getGpuCount() {
        return gpuCount;
    }

    public Boolean getGpuDisplay() {
        return gpuDisplay;
    }
}
