
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
package com.cloud.resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


import com.cloud.offering.ServiceOffering;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.PrimaryDataStoreInfo;
import com.cloud.gpu.VgpuProfileVO;
import com.cloud.vm.VirtualMachine;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.VgpuTypesInfo;
import com.cloud.agent.api.to.GPUDeviceTO;
import com.cloud.cpu.CPU;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.PodCluster;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.gpu.HostGpuGroupsVO;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.host.HostStats;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.resource.ResourceState.Event;
import com.cloud.utils.fsm.NoTransitionException;

/**
 * ResourceManager manages how physical resources are organized within the
 * CloudStack. It also manages the life cycle of the physical resources.
 */
public interface ResourceManager extends ResourceService, Configurable {

    ConfigKey<Boolean> KvmSshToAgentEnabled = new ConfigKey<>("Advanced", Boolean.class,
            "kvm.ssh.to.agent","true",
            "Number of retries when preparing a host into Maintenance Mode is faulty before failing",
            false);

    ConfigKey<String> HOST_MAINTENANCE_LOCAL_STRATEGY = new ConfigKey<>(String.class,
    "host.maintenance.local.storage.strategy", "Advanced","Error",
            "Defines the strategy towards VMs with volumes on local storage when putting a host in maintenance. "
                    + "The default strategy is 'Error', preventing maintenance in such a case. "
                    + "Choose 'Migration' strategy to migrate away VMs running on local storage. "
                    + "To force-stop VMs, choose 'ForceStop' strategy",
            true, ConfigKey.Scope.Global, null, null, null, null, null, ConfigKey.Kind.Select, "Error,Migration,ForceStop");

    ConfigKey<String> SystemVmPreferredArchitecture = new ConfigKey<>(
            String.class,
            "system.vm.preferred.architecture",
            "Advanced",
            CPU.CPUArch.getDefault().getType(),
            "Preferred architecture for the system VMs including virtual routers",
            true,
            ConfigKey.Scope.Zone, null, null, null, null, null,
            ConfigKey.Kind.Select,
            "," + CPU.CPUArch.getTypesAsCSV());

    /**
     * Register a listener for different types of resource life cycle events.
     * There can only be one type of listener per type of host.
     *
     * @param Event type see ResourceListener.java, allow combination of multiple events.
     * @param listener the listener to notify.
     */
    public void registerResourceEvent(Integer event, ResourceListener listener);

    public void unregisterResourceEvent(ResourceListener listener);

    /**
     *
     * @param name of adapter
     * @param adapter
     * @param hates, a list of names which will be eliminated by this adapter. Especially for the case where
     * can be only one adapter responds to an event, e.g. startupCommand
     */
    public void registerResourceStateAdapter(String name, ResourceStateAdapter adapter);

    public void unregisterResourceStateAdapter(String name);

    public Host createHostAndAgent(Long hostId, ServerResource resource, Map<String, String> details, boolean old, List<String> hostTags, boolean forRebalance);

    public Host createHostAndAgent(Long hostId, ServerResource resource, Map<String, String> details, boolean old, List<String> hostTags, boolean forRebalance, boolean isTransferredConnection);

    public Host addHost(long zoneId, ServerResource resource, Type hostType, Map<String, String> hostDetails);

    public HostVO createHostVOForConnectedAgent(StartupCommand[] cmds);

    public void checkCIDR(HostPodVO pod, DataCenterVO dc, String serverPrivateIP, String serverPrivateNetmask);

    public HostVO fillRoutingHostVO(HostVO host, StartupRoutingCommand ssCmd, HypervisorType hyType, Map<String, String> details, List<String> hostTags);

    public void deleteRoutingHost(HostVO host, boolean isForced, boolean forceDestroyStorage) throws UnableDeleteHostException;

    public boolean executeUserRequest(long hostId, ResourceState.Event event) throws AgentUnavailableException;

    boolean resourceStateTransitTo(Host host, Event event, long msId) throws NoTransitionException;

    boolean umanageHost(long hostId);

    boolean migrateAwayFailed(long hostId, long vmId);

    public boolean maintain(final long hostId) throws AgentUnavailableException;

    public boolean checkAndMaintain(final long hostId);

    @Override
    public boolean deleteHost(long hostId, boolean isForced, boolean isForceDeleteStorage);

    public List<HostVO> findDirectlyConnectedHosts();

    public List<HostVO> listAllUpAndEnabledHosts(Host.Type type, Long clusterId, Long podId, long dcId);

    public List<HostVO> listAllHosts(final Host.Type type, final Long clusterId, final Long podId, final long dcId);

    public List<HostVO> listAllUpHosts(Host.Type type, Long clusterId, Long podId, long dcId);

    public List<HostVO> listAllHostsInCluster(long clusterId);

    public List<HostVO> listHostsInClusterByStatus(long clusterId, Status status);

    public List<HostVO> listAllUpAndEnabledHostsInOneZoneByType(Host.Type type, long dcId);

    public List<HostVO> listAllUpAndEnabledHostsInOneZoneByHypervisor(HypervisorType type, long dcId);

    public List<HostVO> listAllUpHostsInOneZoneByHypervisor(HypervisorType type, long dcId);

    public List<HostVO> listAllUpAndEnabledHostsInOneZone(long dcId);

    public List<HostVO> listAllHostsInOneZoneByType(Host.Type type, long dcId);

    public List<HostVO> listAllHostsInAllZonesByType(Type type);

    public List<HostVO> listAllHostsInOneZoneNotInClusterByHypervisor(final HypervisorType type, long dcId, long clusterId);

    public List<HostVO> listAllHostsInOneZoneNotInClusterByHypervisors(List<HypervisorType> types, long dcId, long clusterId);

    public List<HypervisorType> listAvailHypervisorInZone(Long zoneId);

    public HostVO findHostByGuid(String guid);

    public HostVO findHostByName(String name);

    HostStats getHostStatistics(Host host);

    Long getGuestOSCategoryId(long hostId);

    String getHostTags(long hostId);

    List<PodCluster> listByDataCenter(long dcId);

    List<HostVO> listAllNotInMaintenanceHostsInOneZone(Type type, Long dcId);

    HypervisorType getDefaultHypervisor(long zoneId);

    HypervisorType getAvailableHypervisor(long zoneId);

    Discoverer getMatchingDiscover(HypervisorType hypervisorType);

    List<HostVO> findHostByGuid(long dcId, String guid);

    /**
     * @param type
     * @param clusterId
     * @param podId
     * @param dcId
     * @return
     */
    List<HostVO> listAllUpAndEnabledNonHAHosts(Type type, Long clusterId, Long podId, long dcId);

    /**
     * Check if host is GPU enabled
     * @param hostId the host to be checked
     * @return true if host contains GPU card else false
     */
    boolean isHostGpuEnabled(long hostId);

    boolean isGPUDeviceAvailable(ServiceOffering offering, Host host, Long vmId);

    /**
     * Get available GPU device
     *
     * @param vm          the vm for which GPU device is requested
     * @param vgpuProfile the VGPU profile
     * @param gpuCount
     * @return GPUDeviceTO[]
     */
    GPUDeviceTO getGPUDevice(VirtualMachine vm, long hostId, VgpuProfileVO vgpuProfile, int gpuCount);

    /**
     * Get available GPU device
     *
     * @param hostId    the host to be checked
     * @param groupName gpuCard name
     * @param vgpuType  the VGPU type
     * @return GPUDeviceTO[]
     */
    GPUDeviceTO getGPUDevice(long hostId, String groupName, String vgpuType);

    /**
     * Return listof available GPU devices
     *
     * @param hostId    the host to be checked
     * @param groupName gpuCard name
     * @param vgpuType  the VGPU type
     * @return List of HostGpuGroupsVO.
     */
    List<HostGpuGroupsVO> listAvailableGPUDevice(long hostId, String groupName, String vgpuType);

    /**
     * Update GPU device details (post VM deployment)
     * @param hostId, the dest host Id
     * @param groupDetails, capacity of GPU group.
     */
    void updateGPUDetails(long hostId, HashMap<String, HashMap<String, VgpuTypesInfo>> groupDetails);

    /**
     * Update GPU device details (post VM deployment)
     *
     * @param vm          the VirtualMachine object
     * @param gpuDeviceTO GPU device details
     */
    void updateGPUDetailsForVmStop(VirtualMachine vm, GPUDeviceTO gpuDeviceTO);

    void updateGPUDetailsForVmStart(long hostId, long vmId, GPUDeviceTO gpuDevice);

    /**
     * Get GPU details for a host
     * @param host, the Host object
     * @return Details of groupNames and enabled VGPU type with remaining capacity.
     */
    HashMap<String, HashMap<String, VgpuTypesInfo>> getGPUStatistics(HostVO host);

    HostVO findOneRandomRunningHostByHypervisor(HypervisorType type, Long dcId);

    boolean cancelMaintenance(final long hostId);

    void updateStoragePoolConnectionsOnHosts(Long poolId, List<String> storageAccessGroups);

    List<HostVO> getEligibleUpHostsInClusterForStorageConnection(PrimaryDataStoreInfo primaryStore);

    List<HostVO> getEligibleUpAndEnabledHostsInClusterForStorageConnection(PrimaryDataStoreInfo primaryStore);

    List<HostVO> getEligibleUpAndEnabledHostsInZoneForStorageConnection(DataStore dataStore, long zoneId, HypervisorType hypervisorType);
}
