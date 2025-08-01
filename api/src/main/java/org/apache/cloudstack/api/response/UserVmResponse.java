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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.affinity.AffinityGroupResponse;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.BaseResponseWithTagInformation;
import org.apache.cloudstack.api.EntityReference;
import org.apache.commons.collections.CollectionUtils;

import com.cloud.network.router.VirtualRouter;
import com.cloud.serializer.Param;
import com.cloud.uservm.UserVm;
import com.cloud.vm.VirtualMachine;
import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
@EntityReference(value = {VirtualMachine.class, UserVm.class, VirtualRouter.class})
public class UserVmResponse extends BaseResponseWithTagInformation implements ControlledEntityResponse, SetResourceIconResponse {
    @SerializedName(ApiConstants.ID)
    @Param(description = "the ID of the virtual machine")
    private String id;

    @SerializedName(ApiConstants.NAME)
    @Param(description = "the name of the virtual machine")
    private String name;

    @SerializedName("displayname")
    @Param(description = "user generated name. The name of the virtual machine is returned if no displayname exists.")
    private String displayName;

    @SerializedName(ApiConstants.ACCOUNT)
    @Param(description = "the account associated with the virtual machine")
    private String accountName;

    @SerializedName(ApiConstants.USER_ID)
    @Param(description = "the user's ID who deployed the virtual machine")
    private String userId;

    @SerializedName(ApiConstants.USERNAME)
    @Param(description = "the user's name who deployed the virtual machine")
    private String userName;

    @SerializedName(ApiConstants.PROJECT_ID)
    @Param(description = "the project id of the vm")
    private String projectId;

    @SerializedName(ApiConstants.PROJECT)
    @Param(description = "the project name of the vm")
    private String projectName;

    @SerializedName(ApiConstants.DOMAIN_ID)
    @Param(description = "the ID of the domain in which the virtual machine exists")
    private String domainId;

    @SerializedName(ApiConstants.DOMAIN)
    @Param(description = "the name of the domain in which the virtual machine exists")
    private String domainName;

    @SerializedName(ApiConstants.DOMAIN_PATH)
    @Param(description = "path of the domain in which the virtual machine exists", since = "4.19.2.0")
    private String domainPath;

    @SerializedName(ApiConstants.CREATED)
    @Param(description = "the date when this virtual machine was created")
    private Date created;

    @SerializedName("lastupdated")
    @Param(description="the date when this virtual machine was updated last time", since="4.16.0")
    private Date lastUpdated;

    @SerializedName(ApiConstants.STATE)
    @Param(description = "the state of the virtual machine")
    private String state;

    @SerializedName(ApiConstants.HA_ENABLE)
    @Param(description = "true if high-availability is enabled, false otherwise")
    private Boolean haEnable;

    @SerializedName(ApiConstants.GROUP_ID)
    @Param(description = "the group ID of the virtual machine")
    private String groupId;

    @SerializedName(ApiConstants.GROUP)
    @Param(description = "the group name of the virtual machine")
    private String group;

    @SerializedName(ApiConstants.ZONE_ID)
    @Param(description = "the ID of the availability zone for the virtual machine")
    private String zoneId;

    @SerializedName(ApiConstants.ZONE_NAME)
    @Param(description = "the name of the availability zone for the virtual machine")
    private String zoneName;

    @SerializedName(ApiConstants.HOST_ID)
    @Param(description = "the ID of the host for the virtual machine")
    private String hostId;

    @SerializedName("hostname")
    @Param(description = "the name of the host for the virtual machine")
    private String hostName;

    @SerializedName(ApiConstants.HOST_CONTROL_STATE)
    @Param(description = "the control state of the host for the virtual machine")
    private String hostControlState;

    @SerializedName(ApiConstants.TEMPLATE_ID)
    @Param(description = "the ID of the template for the virtual machine. A -1 is returned if the virtual machine was created from an ISO file.")
    private String templateId;

    @SerializedName("templatename")
    @Param(description = "the name of the template for the virtual machine")
    private String templateName;

    @SerializedName(ApiConstants.TEMPLATE_TYPE)
    @Param(description = "the type of the template for the virtual machine", since = "4.19.0")
    private String templateType;

    @SerializedName(ApiConstants.TEMPLATE_FORMAT)
    @Param(description = "the format of the template for the virtual machine", since = "4.19.1")
    private String templateFormat;

    @SerializedName("templatedisplaytext")
    @Param(description = " an alternate display text of the template for the virtual machine")
    private String templateDisplayText;

    @SerializedName(ApiConstants.PASSWORD_ENABLED)
    @Param(description = "true if the password rest feature is enabled, false otherwise")
    private Boolean passwordEnabled;

    @SerializedName("isoid")
    @Param(description = "the ID of the ISO attached to the virtual machine")
    private String isoId;

    @SerializedName("isoname")
    @Param(description = "the name of the ISO attached to the virtual machine")
    private String isoName;

    @SerializedName("isodisplaytext")
    @Param(description = "an alternate display text of the ISO attached to the virtual machine")
    private String isoDisplayText;

    @SerializedName(ApiConstants.SERVICE_OFFERING_ID)
    @Param(description = "the ID of the service offering of the virtual machine")
    private String serviceOfferingId;

    @SerializedName("serviceofferingname")
    @Param(description = "the name of the service offering of the virtual machine")
    private String serviceOfferingName;

    @SerializedName(ApiConstants.DISK_OFFERING_ID)
    @Param(description = "the ID of the disk offering of the virtual machine. This parameter should not be used for retrieving disk offering details of DATA volumes. Use listVolumes API instead", since = "4.4")
    private String diskOfferingId;

    @SerializedName("diskofferingname")
    @Param(description = "the name of the disk offering of the virtual machine. This parameter should not be used for retrieving disk offering details of DATA volumes. Use listVolumes API instead", since = "4.4")
    private String diskOfferingName;

    @SerializedName(ApiConstants.GPU_CARD_ID)
    @Param(description = "the ID of the gpu card to which service offering is linked", since = "4.21")
    private String gpuCardId;

    @SerializedName(ApiConstants.GPU_CARD_NAME)
    @Param(description = "the name of the gpu card to which service offering is linked", since = "4.21")
    private String gpuCardName;

    @SerializedName(ApiConstants.VGPU_PROFILE_ID)
    @Param(description = "the ID of the vgpu profile to which service offering is linked", since = "4.21")
    private String vgpuProfileId;

    @SerializedName(ApiConstants.VGPU_PROFILE_NAME)
    @Param(description = "the name of the vgpu profile to which service offering is linked", since = "4.21")
    private String vgpuProfileName;

    @SerializedName(ApiConstants.VIDEORAM)
    @Param(description = "the video RAM size in MB")
    private Long videoRam;

    @SerializedName(ApiConstants.MAXHEADS)
    @Param(description = "the maximum number of display heads")
    private Long maxHeads;

    @SerializedName(ApiConstants.MAXRESOLUTIONX)
    @Param(description = "the maximum X resolution")
    private Long maxResolutionX;

    @SerializedName(ApiConstants.MAXRESOLUTIONY)
    @Param(description = "the maximum Y resolution")
    private Long maxResolutionY;

    @SerializedName(ApiConstants.GPU_COUNT)
    @Param(description = "the count of GPUs on the virtual machine", since = "4.21")
    private Integer gpuCount;

    @SerializedName(ApiConstants.BACKUP_OFFERING_ID)
    @Param(description = "the ID of the backup offering of the virtual machine", since = "4.14")
    private String backupOfferingId;

    @SerializedName(ApiConstants.BACKUP_OFFERING_NAME)
    @Param(description = "the name of the backup offering of the virtual machine", since = "4.14")
    private String backupOfferingName;

    @SerializedName("forvirtualnetwork")
    @Param(description = "the virtual network for the service offering")
    private Boolean forVirtualNetwork;

    @SerializedName(ApiConstants.CPU_NUMBER)
    @Param(description = "the number of vCPUs this virtual machine is using")
    private Integer cpuNumber;

    @SerializedName(ApiConstants.CPU_SPEED)
    @Param(description = "the speed of each vCPU")
    private Integer cpuSpeed;

    @SerializedName(ApiConstants.MEMORY)
    @Param(description = "the memory allocated for the virtual machine")
    private Integer memory;

    @SerializedName(ApiConstants.VGPU)
    @Param(description = "the vGPU type used by the virtual machine", since = "4.4")
    private String vgpu;

    @SerializedName("cpuused")
    @Param(description = "the amount of the vm's CPU currently used")
    private String cpuUsed;

    @SerializedName("networkkbsread")
    @Param(description = "the incoming network traffic on the VM in KiB")
    private Long networkKbsRead;

    @SerializedName("networkkbswrite")
    @Param(description = "the outgoing network traffic on the host in KiB")
    private Long networkKbsWrite;

    @SerializedName(ApiConstants.DISK_KBS_READ)
    @Param(description = "the VM's disk read in KiB")
    private Long diskKbsRead;

    @SerializedName(ApiConstants.DISK_KBS_WRITE)
    @Param(description = "the VM's disk write in KiB")
    private Long diskKbsWrite;

    @SerializedName("memorykbs")
    @Param(description = "the memory used by the VM in KiB")
    private Long memoryKBs;

    @SerializedName("memoryintfreekbs")
    @Param(description = "the internal memory (KiB) that's free in VM or zero if it can not be calculated")
    private Long memoryIntFreeKBs;

    @SerializedName("memorytargetkbs")
    @Param(description = "the target memory in VM (KiB)")
    private Long memoryTargetKBs;

    @SerializedName(ApiConstants.DISK_IO_READ)
    @Param(description = "the read (IO) of disk on the VM")
    private Long diskIORead;

    @SerializedName(ApiConstants.DISK_IO_WRITE)
    @Param(description = "the write (IO) of disk on the VM")
    private Long diskIOWrite;

    @SerializedName("guestosid")
    @Param(description = "Os type ID of the virtual machine")
    private String guestOsId;

    @SerializedName("rootdeviceid")
    @Param(description = "device ID of the root volume")
    private Long rootDeviceId;

    @SerializedName("rootdevicetype")
    @Param(description = "device type of the root volume")
    private String rootDeviceType;

    @SerializedName("securitygroup")
    @Param(description = "list of security groups associated with the virtual machine", responseObject = SecurityGroupResponse.class)
    private Set<SecurityGroupResponse> securityGroupList;

    @SerializedName(ApiConstants.PASSWORD)
    @Param(description = "the password (if exists) of the virtual machine", isSensitive = true)
    private String password;

    @SerializedName("nic")
    @Param(description = "the list of nics associated with vm", responseObject = NicResponse.class)
    private Set<NicResponse> nics;

    @SerializedName("hypervisor")
    @Param(description = "the hypervisor on which the template runs")
    private String hypervisor;

    @SerializedName(ApiConstants.IP_ADDRESS)
    @Param(description = "the VM's primary IP address")
    private String ipAddress;

    @SerializedName(ApiConstants.PUBLIC_IP_ID)
    @Param(description = "public IP address id associated with vm via Static nat rule")
    private String publicIpId;

    @SerializedName(ApiConstants.PUBLIC_IP)
    @Param(description = "public IP address id associated with vm via Static nat rule")
    private String publicIp;

    @SerializedName(ApiConstants.INSTANCE_NAME)
    @Param(description = "instance name of the user vm; this parameter is returned to the ROOT admin only", since = "3.0.1")
    private String instanceName;

    transient Set<Long> tagIds;

    @SerializedName(ApiConstants.DETAILS)
    @Param(description = "Vm details in key/value pairs.", since = "4.2.1")
    private Map details;

    @SerializedName("readonlydetails")
    @Param(description = "List of read-only Vm details as comma separated string.", since = "4.16.0")
    private String readOnlyDetails;

    @SerializedName(ApiConstants.SSH_KEYPAIRS)
    @Param(description = "ssh key-pairs")
    private String keyPairNames;

    @SerializedName("affinitygroup")
    @Param(description = "list of affinity groups associated with the virtual machine", responseObject = AffinityGroupResponse.class)
    private Set<AffinityGroupResponse> affinityGroupList;

    @SerializedName(ApiConstants.DISPLAY_VM)
    @Param(description = "an optional field whether to the display the vm to the end user or not.", authorized = {RoleType.Admin})
    private Boolean displayVm;

    @SerializedName(ApiConstants.IS_DYNAMICALLY_SCALABLE)
    @Param(description = "true if vm contains XS/VMWare tools inorder to support dynamic scaling of VM cpu/memory.")
    private Boolean isDynamicallyScalable;

    @SerializedName(ApiConstants.DELETE_PROTECTION)
    @Param(description = "true if vm has delete protection.", since = "4.20.0")
    private boolean deleteProtection;

    @SerializedName(ApiConstants.SERVICE_STATE)
    @Param(description = "State of the Service from LB rule")
    private String serviceState;

    @SerializedName(ApiConstants.OS_TYPE_ID)
    @Param(description = "OS type id of the vm", since = "4.4")
    private String osTypeId;

    @SerializedName(ApiConstants.OS_DISPLAY_NAME)
    @Param(description = "OS name of the vm", since = "4.13.2")
    private String osDisplayName;

    @SerializedName(ApiConstants.BOOT_MODE)
    @Param(description = "Guest vm Boot Mode")
    private String bootMode;

    @SerializedName(ApiConstants.BOOT_TYPE)
    @Param(description = "Guest vm Boot Type")
    private String bootType;

    @SerializedName(ApiConstants.POOL_TYPE)
    @Param(description = "the pool type of the virtual machine", since = "4.16")
    private String poolType;

    @SerializedName(ApiConstants.RECEIVED_BYTES)
    @Param(description = "the total number of network traffic bytes received")
    private Long bytesReceived;

    @SerializedName(ApiConstants.SENT_BYTES)
    @Param(description = "the total number of network traffic bytes sent")
    private Long bytesSent;

    @SerializedName(ApiConstants.RESOURCE_ICON)
    @Param(description = "Base64 string representation of the resource icon", since = "4.16.0")
    ResourceIconResponse resourceIconResponse;

    @SerializedName(ApiConstants.AUTOSCALE_VMGROUP_ID)
    @Param(description = "ID of AutoScale VM group", since = "4.18.0")
    String autoScaleVmGroupId;

    @SerializedName(ApiConstants.AUTOSCALE_VMGROUP_NAME)
    @Param(description = "Name of AutoScale VM group", since = "4.18.0")
    String autoScaleVmGroupName;

    @SerializedName(ApiConstants.USER_DATA)
    @Param(description = "Base64 string containing the user data", since = "4.18.0.0")
    private String userData;

    @SerializedName(ApiConstants.USER_DATA_ID) @Param(description="the id of userdata used for the VM", since = "4.18.0")
    private String userDataId;

    @SerializedName(ApiConstants.USER_DATA_NAME) @Param(description="the name of userdata used for the VM", since = "4.18.0")
    private String userDataName;

    @SerializedName(ApiConstants.USER_DATA_POLICY) @Param(description="the userdata override policy with the userdata provided while deploying VM", since = "4.18.0")
    private String userDataPolicy;

    @SerializedName(ApiConstants.USER_DATA_DETAILS) @Param(description="list of variables and values for the variables declared in userdata", since = "4.18.0")
    private String userDataDetails;

    @SerializedName(ApiConstants.VNF_NICS)
    @Param(description = "NICs of the VNF appliance", since = "4.19.0")
    private List<VnfNicResponse> vnfNics;

    @SerializedName(ApiConstants.VNF_DETAILS)
    @Param(description = "VNF details", since = "4.19.0")
    private Map<String, String> vnfDetails;

    @SerializedName(ApiConstants.VM_TYPE)
    @Param(description = "User VM type", since = "4.20.0")
    private String vmType;

    @SerializedName(ApiConstants.ARCH)
    @Param(description = "CPU arch of the VM", since = "4.20.1")
    private String arch;

    @SerializedName(ApiConstants.INSTANCE_LEASE_DURATION)
    @Param(description = "Instance lease duration in days", since = "4.21.0")
    private Integer leaseDuration;

    @SerializedName(ApiConstants.INSTANCE_LEASE_EXPIRY_DATE)
    @Param(description = "Instance lease expiry date", since = "4.21.0")
    private Date leaseExpiryDate;

    @SerializedName(ApiConstants.INSTANCE_LEASE_EXPIRY_ACTION)
    @Param(description = "Instance lease expiry action", since = "4.21.0")
    private String leaseExpiryAction;

    public UserVmResponse() {
        securityGroupList = new LinkedHashSet<>();
        nics = new TreeSet<>(Comparator.comparingInt(x -> Integer.parseInt(x.getDeviceId())));
        tags = new LinkedHashSet<>();
        tagIds = new LinkedHashSet<>();
        affinityGroupList = new LinkedHashSet<>();
    }

    public void setHypervisor(String hypervisor) {
        this.hypervisor = hypervisor;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public Boolean getDisplayVm() {
        return displayVm;
    }

    public void setDisplayVm(Boolean displayVm) {
        this.displayVm = displayVm;
    }

    @Override
    public String getObjectId() {
        return this.getId();
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getDomainId() {
        return domainId;
    }

    public String getDomainName() {
        return domainName;
    }

    public Date getCreated() {
        return created;
    }

    public String getState() {
        return state;
    }

    public Boolean getHaEnable() {
        return haEnable;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroup() {
        return group;
    }

    public String getZoneId() {
        return zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public String getHostId() {
        return hostId;
    }

    public String getHostName() {
        return hostName;
    }

    public String getHostControlState() {
        return hostControlState;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getTemplateDisplayText() {
        return templateDisplayText;
    }

    public Boolean getPasswordEnabled() {
        return passwordEnabled;
    }

    public String getIsoId() {
        return isoId;
    }

    public String getIsoName() {
        return isoName;
    }

    public String getIsoDisplayText() {
        return isoDisplayText;
    }

    public String getServiceOfferingId() {
        return serviceOfferingId;
    }

    public String getServiceOfferingName() {
        return serviceOfferingName;
    }

    public String getDiskOfferingId() {
        return diskOfferingId;
    }

    public String getDiskOfferingName() {
        return diskOfferingName;
    }

    public String getGpuCardId() {
        return gpuCardId;
    }

    public String getGpuCardName() {
        return gpuCardName;
    }

    public String getVgpuProfileId() {
        return vgpuProfileId;
    }

    public String getVgpuProfileName() {
        return vgpuProfileName;
    }

    public Long getVideoRam() {
        return videoRam;
    }

    public Long getMaxHeads() {
        return maxHeads;
    }

    public Long getMaxResolutionX() {
        return maxResolutionX;
    }

    public Long getMaxResolutionY() {
        return maxResolutionY;
    }

    public Integer getGpuCount() {
        return gpuCount;
    }

    public String getBackupOfferingId() {
        return backupOfferingId;
    }

    public String getBackupOfferingName() {
        return backupOfferingName;
    }

    public Boolean getForVirtualNetwork() {
        return forVirtualNetwork;
    }

    public Integer getCpuNumber() {
        return cpuNumber;
    }

    public Integer getCpuSpeed() {
        return cpuSpeed;
    }

    public Integer getMemory() {
        return memory;
    }

    public String getVgpu() {
        return vgpu;
    }
    public String getCpuUsed() {
        return cpuUsed;
    }

    public Long getNetworkKbsRead() {
        return networkKbsRead;
    }

    public Long getNetworkKbsWrite() {
        return networkKbsWrite;
    }

    public Long getDiskKbsRead() {
        return diskKbsRead;
    }

    public Long getDiskKbsWrite() {
        return diskKbsWrite;
    }

    public Long getMemoryKBs() {
        return memoryKBs;
    }

    public Long getMemoryIntFreeKBs() {
        return memoryIntFreeKBs;
    }

    public Long getMemoryTargetKBs() {
        return memoryTargetKBs;
    }

    public Long getDiskIORead() {
        return diskIORead;
    }

    public Long getDiskIOWrite() {
        return diskIOWrite;
    }

    public String getGuestOsId() {
        return guestOsId;
    }

    public Long getRootDeviceId() {
        return rootDeviceId;
    }

    public String getRootDeviceType() {
        return rootDeviceType;
    }

    public Set<SecurityGroupResponse> getSecurityGroupList() {
        return securityGroupList;
    }

    public String getPassword() {
        return password;
    }

    public Set<NicResponse> getNics() {
        return nics;
    }

    public String getHypervisor() {
        return hypervisor;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getPublicIpId() {
        return publicIpId;
    }

    public String getPublicIp() {
        return publicIp;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public String getKeyPairNames() {
        return keyPairNames;
    }

    public Set<AffinityGroupResponse> getAffinityGroupList() {
        return affinityGroupList;
    }

    public Boolean getIsDynamicallyScalable() {
        return isDynamicallyScalable;
    }

    public String getServiceState() {
        return serviceState;
    }

    public String getUserData() {
        return userData;
    }

    public void setIsDynamicallyScalable(Boolean isDynamicallyScalable) {
        this.isDynamicallyScalable = isDynamicallyScalable;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    @Override
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    @Override
    public void setDomainPath(String domainPath) {
        this.domainPath = domainPath;
    }
    public void setCreated(Date created) {
        this.created = created;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setHaEnable(Boolean haEnable) {
        this.haEnable = haEnable;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public void setHostControlState(String hostControlState) {
        this.hostControlState = hostControlState;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public void setTemplateDisplayText(String templateDisplayText) {
        this.templateDisplayText = templateDisplayText;
    }

    public void setPasswordEnabled(Boolean passwordEnabled) {
        this.passwordEnabled = passwordEnabled;
    }

    public void setIsoId(String isoId) {
        this.isoId = isoId;
    }

    public void setIsoName(String isoName) {
        this.isoName = isoName;
    }

    public void setIsoDisplayText(String isoDisplayText) {
        this.isoDisplayText = isoDisplayText;
    }

    public void setDiskKbsRead(Long diskKbsRead) {
        this.diskKbsRead = diskKbsRead;
    }

    public void setDiskKbsWrite(Long diskKbsWrite) {
        this.diskKbsWrite = diskKbsWrite;
    }

    public void setDiskIORead(Long diskIORead) {
        this.diskIORead = diskIORead;
    }

    public void setMemoryKBs(Long memoryKBs) {
        this.memoryKBs = memoryKBs;
    }

    public void setMemoryIntFreeKBs(Long memoryIntFreeKBs) {
        this.memoryIntFreeKBs = memoryIntFreeKBs;
    }

    public void setMemoryTargetKBs(Long memoryTargetKBs) {
        this.memoryTargetKBs = memoryTargetKBs;
    }

    public void setDiskIOWrite(Long diskIOWrite) {
        this.diskIOWrite = diskIOWrite;
    }

    public void setServiceOfferingId(String serviceOfferingId) {
        this.serviceOfferingId = serviceOfferingId;
    }

    public void setServiceOfferingName(String serviceOfferingName) {
        this.serviceOfferingName = serviceOfferingName;
    }

    public void setDiskOfferingId(String diskOfferingId) {
        this.diskOfferingId = diskOfferingId;
    }

    public void setDiskOfferingName(String diskOfferingName) {
        this.diskOfferingName = diskOfferingName;
    }

    public void setGpuCardId(String gpuCardId) {
        this.gpuCardId = gpuCardId;
    }

    public void setGpuCardName(String gpuCardName) {
        this.gpuCardName = gpuCardName;
    }

    public void setVgpuProfileId(String vgpuProfileId) {
        this.vgpuProfileId = vgpuProfileId;
    }

    public void setVgpuProfileName(String vgpuProfileName) {
        this.vgpuProfileName = vgpuProfileName;
    }

    public void setVideoRam(Long videoRam) {
        this.videoRam = videoRam;
    }

    public void setMaxHeads(Long maxHeads) {
        this.maxHeads = maxHeads;
    }

    public void setMaxResolutionX(Long maxResolutionX) {
        this.maxResolutionX = maxResolutionX;
    }

    public void setMaxResolutionY(Long maxResolutionY) {
        this.maxResolutionY = maxResolutionY;
    }

    public void setGpuCount(Integer gpuCount) {
        this.gpuCount = gpuCount;
    }

    public void setBackupOfferingId(String backupOfferingId) {
        this.backupOfferingId = backupOfferingId;
    }

    public void setBackupOfferingName(String backupOfferingName) {
        this.backupOfferingName = backupOfferingName;
    }

    public void setCpuNumber(Integer cpuNumber) {
        this.cpuNumber = cpuNumber;
    }

    public void setCpuSpeed(Integer cpuSpeed) {
        this.cpuSpeed = cpuSpeed;
    }

    public void setMemory(Integer memory) {
        this.memory = memory;
    }

    public void setVgpu(String vgpu) {
        this.vgpu = vgpu;
    }
    public void setCpuUsed(String cpuUsed) {
        this.cpuUsed = cpuUsed;
    }

    public void setNetworkKbsRead(Long networkKbsRead) {
        this.networkKbsRead = networkKbsRead;
    }

    public void setNetworkKbsWrite(Long networkKbsWrite) {
        this.networkKbsWrite = networkKbsWrite;
    }

    public void setGuestOsId(String guestOsId) {
        this.guestOsId = guestOsId;
    }

    public void setRootDeviceId(Long rootDeviceId) {
        this.rootDeviceId = rootDeviceId;
    }

    public void setRootDeviceType(String rootDeviceType) {
        this.rootDeviceType = rootDeviceType;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setForVirtualNetwork(Boolean forVirtualNetwork) {
        this.forVirtualNetwork = forVirtualNetwork;
    }

    public void setNics(Set<NicResponse> nics) {
        this.nics = nics;
        setIpAddress(nics);
    }

    public void setIpAddress(final Set<NicResponse> nics) {
        if (CollectionUtils.isNotEmpty(nics)) {
            this.ipAddress = nics.iterator().next().getIpaddress();
        }
    }

    public void addNic(NicResponse nic) {
        this.nics.add(nic);
    }

    public void setSecurityGroupList(Set<SecurityGroupResponse> securityGroups) {
        this.securityGroupList = securityGroups;
    }

    public void addSecurityGroup(SecurityGroupResponse securityGroup) {
        this.securityGroupList.add(securityGroup);
    }

    @Override
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @Override
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public void setPublicIpId(String publicIpId) {
        this.publicIpId = publicIpId;
    }

    public void setPublicIp(String publicIp) {
        this.publicIp = publicIp;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public boolean containTag(Long tagId) {
        return tagIds.contains(tagId);
    }

    public void setTags(Set<ResourceTagResponse> tags) {
        this.tags = tags;
    }

    public void setKeyPairNames(String keyPairNames) {
        this.keyPairNames = keyPairNames;
    }

    public void setAffinityGroupList(Set<AffinityGroupResponse> affinityGroups) {
        this.affinityGroupList = affinityGroups;
    }

    public void addAffinityGroup(AffinityGroupResponse affinityGroup) {
        this.affinityGroupList.add(affinityGroup);
    }

    public void setDynamicallyScalable(boolean isDynamicallyScalable) {
        this.isDynamicallyScalable = isDynamicallyScalable;
    }

    public void setServiceState(String state) {
        this.serviceState = state;
    }

    public void setDetails(Map details) {
        this.details = details;
    }

    public void setReadOnlyDetails(String readOnlyDetails) {
        this.readOnlyDetails = readOnlyDetails;
    }

    public void setOsTypeId(String osTypeId) {
        this.osTypeId = osTypeId;
    }

    public void setOsDisplayName(String osDisplayName) {
        this.osDisplayName = osDisplayName;
    }

    public Set<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(Set<Long> tagIds) {
        this.tagIds = tagIds;
    }

    public Map getDetails() {
        return details;
    }

    public String getReadOnlyDetails() {
        return readOnlyDetails;
    }

    public Boolean getDynamicallyScalable() {
        return isDynamicallyScalable;
    }

    public void setDynamicallyScalable(Boolean dynamicallyScalable) {
        isDynamicallyScalable = dynamicallyScalable;
    }

    public boolean isDeleteProtection() {
        return deleteProtection;
    }

    public void setDeleteProtection(boolean deleteProtection) {
        this.deleteProtection = deleteProtection;
    }

    public String getOsTypeId() {
        return osTypeId;
    }

    public String getOsDisplayName() {
        return osDisplayName;
    }

    public String getBootType() { return bootType; }

    public void setBootType(String bootType) { this.bootType = bootType; }

    public String getBootMode() { return bootMode; }

    public void setBootMode(String bootMode) { this.bootMode = bootMode; }

    public String getPoolType() { return poolType; }

    public void setPoolType(String poolType) { this.poolType = poolType; }

    @Override
    public void setResourceIconResponse(ResourceIconResponse resourceIconResponse) {
        this.resourceIconResponse = resourceIconResponse;
    }

    public ResourceIconResponse getResourceIconResponse() {
        return resourceIconResponse;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public void setBytesReceived(Long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }

    public void setBytesSent(Long bytesSent) {
        this.bytesSent = bytesSent;
    }

    public void setAutoScaleVmGroupId(String autoScaleVmGroupId) {
        this.autoScaleVmGroupId = autoScaleVmGroupId;
    }

    public void setAutoScaleVmGroupName(String autoScaleVmGroupName) {
        this.autoScaleVmGroupName = autoScaleVmGroupName;
    }

    public String getAutoScaleVmGroupId() {
        return autoScaleVmGroupId;
    }

    public String getAutoScaleVmGroupName() {
        return autoScaleVmGroupName;
    }

    public void setUserData(String userData) {
        this.userData = userData;
    }

    public String getUserDataId() {
        return userDataId;
    }

    public void setUserDataId(String userDataId) {
        this.userDataId = userDataId;
    }

    public String getUserDataName() {
        return userDataName;
    }

    public void setUserDataName(String userDataName) {
        this.userDataName = userDataName;
    }

    public String getUserDataPolicy() {
        return userDataPolicy;
    }

    public void setUserDataPolicy(String userDataPolicy) {
        this.userDataPolicy = userDataPolicy;
    }

    public String getUserDataDetails() {
        return userDataDetails;
    }

    public void setUserDataDetails(String userDataDetails) {
        this.userDataDetails = userDataDetails;
    }

    public Long getBytesReceived() {
        return bytesReceived;
    }

    public Long getBytesSent() {
        return bytesSent;
    }

    public String getTemplateType() {
        return templateType;
    }

    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }

    public String getTemplateFormat() {
        return templateFormat;
    }

    public void setTemplateFormat(String templateFormat) {
        this.templateFormat = templateFormat;
    }

    public List<VnfNicResponse> getVnfNics() {
        return vnfNics;
    }

    public void setVnfNics(List<VnfNicResponse> vnfNics) {
        this.vnfNics = vnfNics;
    }

    public Map<String, String> getVnfDetails() {
        return vnfDetails;
    }

    public void setVnfDetails(Map<String, String> vnfDetails) {
        this.vnfDetails = vnfDetails;
    }

    public void addVnfNic(VnfNicResponse vnfNic) {
        if (this.vnfNics == null) {
            this.vnfNics = new ArrayList<>();
        }
        this.vnfNics.add(vnfNic);
    }

    public void addVnfDetail(String key, String value) {
        if (this.vnfDetails == null) {
            this.vnfDetails = new LinkedHashMap<>();
        }
        this.vnfDetails.put(key,value);
    }

    public void setVmType(String vmType) {
        this.vmType = vmType;
    }

    public String getVmType() {
        return vmType;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public Integer getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Integer leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public String getLeaseExpiryAction() {
        return leaseExpiryAction;
    }

    public void setLeaseExpiryAction(String leaseExpiryAction) {
        this.leaseExpiryAction = leaseExpiryAction;
    }

    public Date getLeaseExpiryDate() {
        return leaseExpiryDate;
    }

    public void setLeaseExpiryDate(Date leaseExpiryDate) {
        this.leaseExpiryDate = leaseExpiryDate;
    }

}
