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
package org.apache.cloudstack.api.response;

import com.google.gson.annotations.SerializedName;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponseWithAnnotations;
import org.apache.cloudstack.api.EntityReference;

import com.cloud.domain.Domain;
import com.cloud.serializer.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

@EntityReference(value = Domain.class)
public class DomainResponse extends BaseResponseWithAnnotations implements ResourceLimitAndCountResponse, SetResourceIconResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the ID of the domain")
    private String id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "the name of the domain")
    private String domainName;

    @SerializedName(ApiConstants.LEVEL)
    @Param(description = "the level of the domain")
    private Integer level;

    @SerializedName("parentdomainid")
    @Param(description = "the domain ID of the parent domain")
    private String parentDomainId;

    @SerializedName("parentdomainname")
    @Param(description = "the domain name of the parent domain")
    private String parentDomainName;

    @SerializedName("haschild")
    @Param(description = "whether the domain has one or more sub-domains")
    private boolean hasChild;

    @SerializedName(ApiConstants.NETWORK_DOMAIN)
    @Param(description = "the network domain")
    private String networkDomain;

    @SerializedName(ApiConstants.PATH)
    @Param(description = "the path of the domain")
    private String path;

    @SerializedName(ApiConstants.STATE) @Param(description="the state of the domain")
    private String state;

    @SerializedName(ApiConstants.CREATED) @Param(description="the date when this domain was created")
    private Date created;

    @SerializedName(ApiConstants.VM_LIMIT) @Param(description="the total number of virtual machines that can be deployed by this domain")
    private String vmLimit;

    @SerializedName(ApiConstants.VM_TOTAL) @Param(description="the total number of virtual machines deployed by this domain")
    private Long vmTotal;

    @SerializedName(ApiConstants.VM_AVAILABLE) @Param(description="the total number of virtual machines available for this domain to acquire")
    private String vmAvailable;

    @SerializedName(ApiConstants.IP_LIMIT) @Param(description="the total number of public ip addresses this domain can acquire")
    private String ipLimit;

    @SerializedName(ApiConstants.IP_TOTAL) @Param(description="the total number of public ip addresses allocated for this domain")
    private Long ipTotal;

    @SerializedName(ApiConstants.IP_AVAILABLE) @Param(description="the total number of public ip addresses available for this domain to acquire")
    private String ipAvailable;

    @SerializedName("volumelimit") @Param(description="the total volume which can be used by this domain")
    private String volumeLimit;

    @SerializedName("volumetotal") @Param(description="the total volume being used by this domain")
    private Long volumeTotal;

    @SerializedName("volumeavailable") @Param(description="the total volume available for this domain")
    private String volumeAvailable;

    @SerializedName("snapshotlimit") @Param(description="the total number of snapshots which can be stored by this domain")
    private String snapshotLimit;

    @SerializedName("snapshottotal") @Param(description="the total number of snapshots stored by this domain")
    private Long snapshotTotal;

    @SerializedName("snapshotavailable") @Param(description="the total number of snapshots available for this domain")
    private String snapshotAvailable;

    @SerializedName(ApiConstants.BACKUP_LIMIT)
    @Param(description = "the total number of backups which can be stored by this domain", since = "4.21.0")
    private String backupLimit;

    @SerializedName(ApiConstants.BACKUP_TOTAL)
    @Param(description = "the total number of backups stored by this domain", since = "4.21.0")
    private Long backupTotal;

    @SerializedName(ApiConstants.BACKUP_AVAILABLE)
    @Param(description = "the total number of backups available to this domain", since = "4.21.0")
    private String backupAvailable;

    @SerializedName(ApiConstants.BACKUP_STORAGE_LIMIT)
    @Param(description = "the total backup storage space (in GiB) the domain can own", since = "4.21.0")
    private String backupStorageLimit;

    @SerializedName(ApiConstants.BACKUP_STORAGE_TOTAL)
    @Param(description = "the total backup storage space (in GiB) owned by the domain", since = "4.21.0")
    private Long backupStorageTotal;

    @SerializedName(ApiConstants.BACKUP_STORAGE_AVAILABLE)
    @Param(description = "the total backup storage space (in GiB) available to the domain", since = "4.21.0")
    private String backupStorageAvailable;

    @SerializedName("templatelimit") @Param(description="the total number of templates which can be created by this domain")
    private String templateLimit;

    @SerializedName("templatetotal") @Param(description="the total number of templates which have been created by this domain")
    private Long templateTotal;

    @SerializedName("templateavailable") @Param(description="the total number of templates available to be created by this domain")
    private String templateAvailable;

    @SerializedName("projectlimit") @Param(description="the total number of projects the domain can own", since="3.0.1")
    private String projectLimit;

    @SerializedName("projecttotal") @Param(description="the total number of projects being administrated by this domain", since="3.0.1")
    private Long projectTotal;

    @SerializedName("projectavailable") @Param(description="the total number of projects available for administration by this domain", since="3.0.1")
    private String projectAvailable;

    @SerializedName("networklimit") @Param(description="the total number of networks the domain can own", since="3.0.1")
    private String networkLimit;

    @SerializedName("networktotal") @Param(description="the total number of networks owned by domain", since="3.0.1")
    private Long networkTotal;

    @SerializedName("networkavailable") @Param(description="the total number of networks available to be created for this domain", since="3.0.1")
    private String networkAvailable;

    @SerializedName("vpclimit") @Param(description="the total number of vpcs the domain can own", since="4.0.0")
    private String vpcLimit;

    @SerializedName("vpctotal") @Param(description="the total number of vpcs owned by domain", since="4.0.0")
    private Long vpcTotal;

    @SerializedName("vpcavailable") @Param(description="the total number of vpcs available to be created for this domain", since="4.0.0")
    private String vpcAvailable;

    @SerializedName("cpulimit") @Param(description="the total number of cpu cores the domain can own", since="4.2.0")
    private String cpuLimit;

    @SerializedName("cputotal") @Param(description="the total number of cpu cores owned by domain", since="4.2.0")
    private Long cpuTotal;

    @SerializedName("cpuavailable") @Param(description="the total number of cpu cores available to be created for this domain", since="4.2.0")
    private String cpuAvailable;

    @SerializedName("memorylimit") @Param(description="the total memory (in MB) the domain can own", since="4.2.0")
    private String memoryLimit;

    @SerializedName("memorytotal") @Param(description="the total memory (in MB) owned by domain", since="4.2.0")
    private Long memoryTotal;

    @SerializedName("memoryavailable") @Param(description="the total memory (in MB) available to be created for this domain", since="4.2.0")
    private String memoryAvailable;

    @SerializedName("gpulimit") @Param(description="the total number of gpus the domain can own", since="4.21.0")
    private String gpuLimit;

    @SerializedName("gputotal") @Param(description="the total number of gpus owned by domain", since="4.21.0")
    private Long gpuTotal;

    @SerializedName("gpuavailable") @Param(description="the total number of gpus available to be created for this domain", since="4.21.0")
    private String gpuAvailable;

    @SerializedName("primarystoragelimit") @Param(description="the total primary storage space (in GiB) the domain can own", since="4.2.0")
    private String primaryStorageLimit;

    @SerializedName("primarystoragetotal") @Param(description="the total primary storage space (in GiB) owned by domain", since="4.2.0")
    private Long primaryStorageTotal;

    @SerializedName("primarystorageavailable") @Param(description="the total primary storage space (in GiB) available to be used for this domain", since="4.2.0")
    private String primaryStorageAvailable;

    @SerializedName("secondarystoragelimit") @Param(description="the total secondary storage space (in GiB) the domain can own", since="4.2.0")
    private String secondaryStorageLimit;

    @SerializedName("secondarystoragetotal") @Param(description="the total secondary storage space (in GiB) owned by domain", since="4.2.0")
    private float secondaryStorageTotal;

    @SerializedName("secondarystorageavailable") @Param(description="the total secondary storage space (in GiB) available to be used for this domain", since="4.2.0")
    private String secondaryStorageAvailable;

    @SerializedName(ApiConstants.BUCKET_LIMIT)
    @Param(description = "the total number of buckets which can be stored by this domain", since = "4.21.0")
    private String bucketLimit;

    @SerializedName(ApiConstants.BUCKET_TOTAL)
    @Param(description = "the total number of buckets stored by this domain", since = "4.21.0")
    private Long bucketTotal;

    @SerializedName(ApiConstants.BUCKET_AVAILABLE)
    @Param(description = "the total number of buckets available to this domain", since = "4.21.0")
    private String bucketAvailable;

    @SerializedName(ApiConstants.OBJECT_STORAGE_LIMIT)
    @Param(description = "the total object storage space (in GiB) the domain can own", since = "4.21.0")
    private String objectStorageLimit;

    @SerializedName(ApiConstants.OBJECT_STORAGE_TOTAL)
    @Param(description = "the total object storage space (in GiB) owned by the domain", since = "4.21.0")
    private Long objectStorageTotal;

    @SerializedName(ApiConstants.OBJECT_STORAGE_AVAILABLE)
    @Param(description = "the total object storage space (in GiB) available to the domain", since = "4.21.0")
    private String objectStorageAvailable;

    @SerializedName(ApiConstants.RESOURCE_ICON)
    @Param(description = "Base64 string representation of the resource icon", since = "4.16.0.0")
    ResourceIconResponse icon;

    @SerializedName(ApiConstants.DOMAIN_DETAILS)
    @Param(description = "details for the domain")
    private Map<String, String> details;

    @SerializedName(ApiConstants.TAGGED_RESOURCES)
    @Param(description = "The tagged resource limit and count for the domain", since = "4.20.0")
    List<TaggedResourceLimitAndCountResponse> taggedResources;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getParentDomainId() {
        return parentDomainId;
    }

    public void setParentDomainId(String parentDomainId) {
        this.parentDomainId = parentDomainId;
    }

    public String getParentDomainName() {
        return parentDomainName;
    }

    public void setParentDomainName(String parentDomainName) {
        this.parentDomainName = parentDomainName;
    }

    public boolean getHasChild() {
        return hasChild;
    }

    public void setHasChild(boolean hasChild) {
        this.hasChild = hasChild;
    }

    public void setNetworkDomain(String networkDomain) {
        this.networkDomain = networkDomain;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @Override
    public void setVmLimit(String vmLimit) {
        this.vmLimit = vmLimit;
    }

    @Override
    public void setVmTotal(Long vmTotal) {
        this.vmTotal = vmTotal;
    }

    @Override
    public void setVmAvailable(String vmAvailable) {
        this.vmAvailable = vmAvailable;
    }

    @Override
    public void setIpLimit(String ipLimit) {
        this.ipLimit = ipLimit;
    }

    @Override
    public void setIpTotal(Long ipTotal) {
        this.ipTotal = ipTotal;
    }

    @Override
    public void setIpAvailable(String ipAvailable) {
        this.ipAvailable = ipAvailable;
    }

    @Override
    public void setVolumeLimit(String volumeLimit) {
        this.volumeLimit = volumeLimit;
    }

    @Override
    public void setVolumeTotal(Long volumeTotal) {
        this.volumeTotal = volumeTotal;
    }

    @Override
    public void setVolumeAvailable(String volumeAvailable) {
        this.volumeAvailable = volumeAvailable;
    }

    @Override
    public void setSnapshotLimit(String snapshotLimit) {
        this.snapshotLimit = snapshotLimit;
    }

    @Override
    public void setSnapshotTotal(Long snapshotTotal) {
        this.snapshotTotal = snapshotTotal;
    }

    @Override
    public void setSnapshotAvailable(String snapshotAvailable) {
        this.snapshotAvailable = snapshotAvailable;
    }

    @Override
    public void setBackupLimit(String backupLimit) {
        this.backupLimit = backupLimit;
    }

    @Override
    public void setBackupTotal(Long backupTotal) {
        this.backupTotal = backupTotal;
    }

    @Override
    public void setBackupAvailable(String backupAvailable) {
        this.backupAvailable = backupAvailable;
    }

    @Override
    public void setBackupStorageLimit(String backupStorageLimit) {
        this.backupStorageLimit = backupStorageLimit;
    }

    @Override
    public void setBackupStorageTotal(Long backupStorageTotal) {
        this.backupStorageTotal = backupStorageTotal;
    }

    @Override
    public void setBackupStorageAvailable(String backupStorageAvailable) {
        this.backupStorageAvailable = backupStorageAvailable;
    }

    @Override
    public void setTemplateLimit(String templateLimit) {
        this.templateLimit = templateLimit;
    }

    @Override
    public void setTemplateTotal(Long templateTotal) {
        this.templateTotal = templateTotal;
    }

    @Override
    public void setTemplateAvailable(String templateAvailable) {
        this.templateAvailable = templateAvailable;
    }

    public void setProjectLimit(String projectLimit) {
        this.projectLimit = projectLimit;
    }

    public void setProjectTotal(Long projectTotal) {
        this.projectTotal = projectTotal;
    }

    public void setProjectAvailable(String projectAvailable) {
        this.projectAvailable = projectAvailable;
    }

    @Override
    public void setNetworkLimit(String networkLimit) {
        this.networkLimit = networkLimit;
    }

    @Override
    public void setNetworkTotal(Long networkTotal) {
        this.networkTotal = networkTotal;
    }

    @Override
    public void setNetworkAvailable(String networkAvailable) {
        this.networkAvailable = networkAvailable;
    }

    @Override
    public void setVpcLimit(String vpcLimit) {
        this.vpcLimit = networkLimit;
    }

    @Override
    public void setVpcTotal(Long vpcTotal) {
        this.vpcTotal = vpcTotal;
    }

    @Override
    public void setVpcAvailable(String vpcAvailable) {
        this.vpcAvailable = vpcAvailable;
    }

    @Override
    public void setCpuLimit(String cpuLimit) {
        this.cpuLimit = cpuLimit;
    }

    @Override
    public void setCpuTotal(Long cpuTotal) {
        this.cpuTotal = cpuTotal;
    }

    @Override
    public void setCpuAvailable(String cpuAvailable) {
        this.cpuAvailable = cpuAvailable;
    }

    @Override
    public void setMemoryLimit(String memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    @Override
    public void setMemoryTotal(Long memoryTotal) {
        this.memoryTotal = memoryTotal;
    }

    @Override
    public void setMemoryAvailable(String memoryAvailable) {
        this.memoryAvailable = memoryAvailable;
    }

    @Override
    public void setGpuLimit(String gpuLimit) {
        this.gpuLimit = gpuLimit;
    }

    @Override
    public void setGpuTotal(Long gpuTotal) {
        this.gpuTotal = gpuTotal;
    }

    @Override
    public void setGpuAvailable(String gpuAvailable) {
        this.gpuAvailable = gpuAvailable;
    }

    @Override
    public void setPrimaryStorageLimit(String primaryStorageLimit) {
        this.primaryStorageLimit = primaryStorageLimit;
    }

    @Override
    public void setPrimaryStorageTotal(Long primaryStorageTotal) {
        this.primaryStorageTotal = primaryStorageTotal;
    }

    @Override
    public void setPrimaryStorageAvailable(String primaryStorageAvailable) {
        this.primaryStorageAvailable = primaryStorageAvailable;
    }

    @Override
    public void setSecondaryStorageLimit(String secondaryStorageLimit) {
        this.secondaryStorageLimit = secondaryStorageLimit;
    }

    @Override
    public void setSecondaryStorageTotal(float secondaryStorageTotal) {
        this.secondaryStorageTotal = secondaryStorageTotal;
    }

    @Override
    public void setSecondaryStorageAvailable(String secondaryStorageAvailable) {
        this.secondaryStorageAvailable = secondaryStorageAvailable;
    }

    @Override
    public void setBucketLimit(String bucketLimit) {
        this.bucketLimit = bucketLimit;
    }

    @Override
    public void setBucketTotal(Long bucketTotal) {
        this.bucketTotal = bucketTotal;
    }

    @Override
    public void setBucketAvailable(String bucketAvailable) {
        this.bucketAvailable = bucketAvailable;
    }

    @Override
    public void setObjectStorageLimit(String objectStorageLimit) {
        this.objectStorageLimit = objectStorageLimit;
    }

    @Override
    public void setObjectStorageTotal(Long objectStorageTotal) {
        this.objectStorageTotal = objectStorageTotal;
    }

    @Override
    public void setObjectStorageAvailable(String objectStorageAvailable) {
        this.objectStorageAvailable = objectStorageAvailable;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public void setVmStopped(Integer vmStopped) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setVmRunning(Integer vmRunning) {
        // TODO Auto-generated method stub
    }

    @Override
    public void setResourceIconResponse(ResourceIconResponse icon) {
        this.icon = icon;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }

    @Override
    public void setTaggedResourceLimitsAndCounts(List<TaggedResourceLimitAndCountResponse> taggedResourceLimitsAndCounts) {
        this.taggedResources = taggedResourceLimitsAndCounts;
    }
}
