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
package com.cloud.agent.manager.allocator.impl;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import com.cloud.gpu.dao.VgpuProfileDao;

import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.utils.reflectiontostringbuilderutils.ReflectionToStringBuilderUtils;
import org.springframework.stereotype.Component;

import com.cloud.agent.manager.allocator.HostAllocator;
import com.cloud.capacity.Capacity;
import com.cloud.capacity.CapacityManager;
import com.cloud.capacity.CapacityVO;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.configuration.Config;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.dao.ClusterDao;
import com.cloud.deploy.DeploymentPlan;
import com.cloud.deploy.DeploymentClusterPlanner;
import com.cloud.deploy.DeploymentPlanner.ExcludeList;
import com.cloud.deploy.FirstFitPlanner;
import com.cloud.gpu.GPU;
import com.cloud.host.DetailVO;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.offering.ServiceOffering;
import com.cloud.resource.ResourceManager;
import com.cloud.service.ServiceOfferingDetailsVO;
import com.cloud.service.dao.ServiceOfferingDetailsDao;
import com.cloud.storage.GuestOSCategoryVO;
import com.cloud.storage.GuestOSVO;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.GuestOSCategoryDao;
import com.cloud.storage.dao.GuestOSDao;
import com.cloud.user.Account;
import com.cloud.utils.Pair;
import com.cloud.utils.component.AdapterBase;
import com.cloud.vm.VMInstanceDetailVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.VMInstanceDetailsDao;
import com.cloud.vm.dao.VMInstanceDao;

import org.jetbrains.annotations.NotNull;

/**
 * An allocator that tries to find a fit on a computing host.  This allocator does not care whether or not the host supports routing.
 */
@Component
public class FirstFitAllocator extends AdapterBase implements HostAllocator {
    @Inject
    protected HostDao _hostDao = null;
    @Inject
    HostDetailsDao _hostDetailsDao = null;
    @Inject
    ConfigurationDao _configDao = null;
    @Inject
    GuestOSDao _guestOSDao = null;
    @Inject
    GuestOSCategoryDao _guestOSCategoryDao = null;
    @Inject
    VMInstanceDao _vmInstanceDao = null;
    @Inject
    protected ResourceManager _resourceMgr;
    @Inject
    ClusterDao _clusterDao;
    @Inject
    ClusterDetailsDao _clusterDetailsDao;
    @Inject
    ServiceOfferingDetailsDao _serviceOfferingDetailsDao;
    @Inject
    CapacityManager _capacityMgr;
    @Inject
    CapacityDao _capacityDao;
    @Inject
    VMInstanceDetailsDao _vmInstanceDetailsDao;
    @Inject
    private VgpuProfileDao vgpuProfileDao;

    boolean _checkHvm = true;
    static DecimalFormat decimalFormat = new DecimalFormat("#.##");

    @Override
    public List<Host> allocateTo(VirtualMachineProfile vmProfile, DeploymentPlan plan, Type type, ExcludeList avoid, int returnUpTo) {
        return allocateTo(vmProfile, plan, type, avoid, returnUpTo, true);
    }

    @Override
    public List<Host> allocateTo(VirtualMachineProfile vmProfile, DeploymentPlan plan, Type type, ExcludeList avoid, int returnUpTo, boolean considerReservedCapacity) {

        long dcId = plan.getDataCenterId();
        Long podId = plan.getPodId();
        Long clusterId = plan.getClusterId();
        ServiceOffering offering = vmProfile.getServiceOffering();
        VMTemplateVO template = (VMTemplateVO)vmProfile.getTemplate();
        Account account = vmProfile.getOwner();

        boolean isVMDeployedWithUefi = false;
        VMInstanceDetailVO vmInstanceDetailVO = _vmInstanceDetailsDao.findDetail(vmProfile.getId(), "UEFI");
        if(vmInstanceDetailVO != null){
            if ("secure".equalsIgnoreCase(vmInstanceDetailVO.getValue()) || "legacy".equalsIgnoreCase(vmInstanceDetailVO.getValue())) {
                isVMDeployedWithUefi = true;
            }
        }
        logger.info(" Guest VM is requested with Custom[UEFI] Boot Type "+ isVMDeployedWithUefi);


        if (type == Host.Type.Storage) {
            // FirstFitAllocator should be used for user VMs only since it won't care whether the host is capable of routing or not
            return new ArrayList<>();
        }
        String paramAsStringToLog = String.format("zone [%s], pod [%s], cluster [%s]", dcId, podId, clusterId);
        logger.debug("Looking for hosts in {}", paramAsStringToLog);

        String hostTagOnOffering = offering.getHostTag();
        String hostTagOnTemplate = template.getTemplateTag();
        String hostTagUefi = "UEFI";

        boolean hasSvcOfferingTag = hostTagOnOffering != null ? true : false;
        boolean hasTemplateTag = hostTagOnTemplate != null ? true : false;

        List<HostVO> clusterHosts = new ArrayList<>();
        List<HostVO> hostsMatchingUefiTag = new ArrayList<>();
        if(isVMDeployedWithUefi){
            hostsMatchingUefiTag = _hostDao.listByHostCapability(type, clusterId, podId, dcId, Host.HOST_UEFI_ENABLE);
            if (logger.isDebugEnabled()) {
                logger.debug("Hosts with tag '" + hostTagUefi + "' are:" + hostsMatchingUefiTag);
            }
        }


        String haVmTag = (String)vmProfile.getParameter(VirtualMachineProfile.Param.HaTag);
        if (haVmTag != null) {
            clusterHosts = _hostDao.listByHostTag(type, clusterId, podId, dcId, haVmTag);
        } else {
            if (hostTagOnOffering == null && hostTagOnTemplate == null) {
                clusterHosts = _resourceMgr.listAllUpAndEnabledNonHAHosts(type, clusterId, podId, dcId);
            } else {
                List<HostVO> hostsMatchingOfferingTag = new ArrayList<>();
                List<HostVO> hostsMatchingTemplateTag = new ArrayList<>();
                if (hasSvcOfferingTag) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Looking for hosts having tag specified on SvcOffering:" + hostTagOnOffering);
                    }
                    hostsMatchingOfferingTag = _hostDao.listByHostTag(type, clusterId, podId, dcId, hostTagOnOffering);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Hosts with tag '" + hostTagOnOffering + "' are:" + hostsMatchingOfferingTag);
                    }
                }
                if (hasTemplateTag) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Looking for hosts having tag specified on Template:" + hostTagOnTemplate);
                    }
                    hostsMatchingTemplateTag = _hostDao.listByHostTag(type, clusterId, podId, dcId, hostTagOnTemplate);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Hosts with tag '" + hostTagOnTemplate + "' are:" + hostsMatchingTemplateTag);
                    }
                }

                if (hasSvcOfferingTag && hasTemplateTag) {
                    hostsMatchingOfferingTag.retainAll(hostsMatchingTemplateTag);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Found " + hostsMatchingOfferingTag.size() + " Hosts satisfying both tags, host ids are:" + hostsMatchingOfferingTag);
                    }

                    clusterHosts = hostsMatchingOfferingTag;
                } else {
                    if (hasSvcOfferingTag) {
                        clusterHosts = hostsMatchingOfferingTag;
                    } else {
                        clusterHosts = hostsMatchingTemplateTag;
                    }
                }
            }
        }

        if (isVMDeployedWithUefi) {
            clusterHosts.retainAll(hostsMatchingUefiTag);
        }

        clusterHosts.addAll(_hostDao.findHostsWithTagRuleThatMatchComputeOferringTags(hostTagOnOffering));


        if (clusterHosts.isEmpty()) {
            logger.warn("No suitable host found for VM [{}] with tags {} in {}.", vmProfile, hostTagOnOffering, paramAsStringToLog);
            return null;
        }
        // add all hosts that we are not considering to the avoid list
        List<HostVO> allhostsInCluster = _hostDao.listAllUpAndEnabledNonHAHosts(type, clusterId, podId, dcId, null);
        allhostsInCluster.removeAll(clusterHosts);

        logger.debug(() -> String.format("Adding hosts [%s] to the avoid set because these hosts do not support HA.",
                ReflectionToStringBuilderUtils.reflectOnlySelectedFields(allhostsInCluster, "uuid", "name")));

        for (HostVO host : allhostsInCluster) {
            avoid.addHost(host.getId());
        }

        return allocateTo(vmProfile, plan, offering, template, avoid, clusterHosts, returnUpTo, considerReservedCapacity, account);
    }

    @Override
    public List<Host> allocateTo(VirtualMachineProfile vmProfile, DeploymentPlan plan, Type type, ExcludeList avoid, List<? extends Host> hosts, int returnUpTo,
        boolean considerReservedCapacity) {
        long dcId = plan.getDataCenterId();
        Long podId = plan.getPodId();
        Long clusterId = plan.getClusterId();
        ServiceOffering offering = vmProfile.getServiceOffering();
        VMTemplateVO template = (VMTemplateVO)vmProfile.getTemplate();
        Account account = vmProfile.getOwner();
        List<Host> suitableHosts = new ArrayList<>();
        List<Host> hostsCopy = new ArrayList<>(hosts);

        if (type == Host.Type.Storage) {
            // FirstFitAllocator should be used for user VMs only since it won't care whether the host is capable of
            // routing or not.
            return suitableHosts;
        }

        String hostTagOnOffering = offering.getHostTag();
        String hostTagOnTemplate = template.getTemplateTag();
        boolean hasSvcOfferingTag = hostTagOnOffering != null ? true : false;
        boolean hasTemplateTag = hostTagOnTemplate != null ? true : false;

        String haVmTag = (String)vmProfile.getParameter(VirtualMachineProfile.Param.HaTag);
        if (haVmTag != null) {
            hostsCopy.retainAll(_hostDao.listByHostTag(type, clusterId, podId, dcId, haVmTag));
        } else {
            if (hostTagOnOffering == null && hostTagOnTemplate == null) {
                hostsCopy.retainAll(_resourceMgr.listAllUpAndEnabledNonHAHosts(type, clusterId, podId, dcId));
            } else {
                if (hasSvcOfferingTag) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Looking for hosts having tag specified on SvcOffering:" + hostTagOnOffering);
                    }
                    hostsCopy.retainAll(_hostDao.listByHostTag(type, clusterId, podId, dcId, hostTagOnOffering));

                    if (logger.isDebugEnabled()) {
                        logger.debug("Hosts with tag '" + hostTagOnOffering + "' are:" + hostsCopy);
                    }
                }

                if (hasTemplateTag) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Looking for hosts having tag specified on Template:" + hostTagOnTemplate);
                    }

                    hostsCopy.retainAll(_hostDao.listByHostTag(type, clusterId, podId, dcId, hostTagOnTemplate));

                    if (logger.isDebugEnabled()) {
                        logger.debug("Hosts with tag '" + hostTagOnTemplate + "' are:" + hostsCopy);
                    }
                }
            }
        }

        hostsCopy.addAll(_hostDao.findHostsWithTagRuleThatMatchComputeOferringTags(hostTagOnOffering));

        if (!hostsCopy.isEmpty()) {
            suitableHosts = allocateTo(vmProfile, plan, offering, template, avoid, hostsCopy, returnUpTo, considerReservedCapacity, account);
        }

        return suitableHosts;
    }

    protected List<Host> allocateTo(VirtualMachineProfile vmProfile, DeploymentPlan plan, ServiceOffering offering, VMTemplateVO template, ExcludeList avoid, List<? extends Host> hosts, int returnUpTo,
        boolean considerReservedCapacity, Account account) {
        String vmAllocationAlgorithm = DeploymentClusterPlanner.VmAllocationAlgorithm.value();
        if (vmAllocationAlgorithm.equals("random") || vmAllocationAlgorithm.equals("userconcentratedpod_random")) {
            // Shuffle this so that we don't check the hosts in the same order.
            Collections.shuffle(hosts);
        } else if (vmAllocationAlgorithm.equals("userdispersing")) {
            hosts = reorderHostsByNumberOfVms(plan, hosts, account);
        } else if(vmAllocationAlgorithm.equals("firstfitleastconsumed")){
            hosts = reorderHostsByCapacity(plan, hosts);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("FirstFitAllocator has " + hosts.size() + " hosts to check for allocation: " + hosts);
        }

        // We will try to reorder the host lists such that we give priority to hosts that have
        // the minimums to support a VM's requirements
        hosts = prioritizeHosts(template, offering, hosts);

        if (logger.isDebugEnabled()) {
            logger.debug("Found " + hosts.size() + " hosts for allocation after prioritization: " + hosts);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Looking for speed=" + (offering.getCpu() * offering.getSpeed()) + "Mhz, Ram=" + offering.getRamSize() + " MB");
        }

        long serviceOfferingId = offering.getId();
        List<Host> suitableHosts = new ArrayList<>();
        ServiceOfferingDetailsVO offeringDetails = null;

        for (Host host : hosts) {
            if (suitableHosts.size() == returnUpTo) {
                break;
            }
            if (avoid.shouldAvoid(host)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Host: {} is in avoid set, skipping this and trying other available hosts", host);
                }
                continue;
            }

            //find number of guest VMs occupying capacity on this host.
            if (_capacityMgr.checkIfHostReachMaxGuestLimit(host)) {
                logger.debug("Adding host [{}] to the avoid set because this host already has the max number of running (user and/or system) VMs.", host);
                avoid.addHost(host.getId());
                continue;
            }

            // Check if GPU device is required by offering and host has the availability
            if (_resourceMgr.isGPUDeviceAvailable(offering, host, vmProfile.getId())) {
                logger.debug("Host [{}] has required GPU devices available.", host);
            } else {
                // If GPU is not available, skip this host
                logger.debug("Adding host [{}] to avoid set, because this host does not have required GPU devices available.", host);
                avoid.addHost(host.getId());
                continue;
            }

            Pair<Boolean, Boolean> cpuCapabilityAndCapacity = _capacityMgr.checkIfHostHasCpuCapabilityAndCapacity(host, offering, considerReservedCapacity);
            if (cpuCapabilityAndCapacity.first() && cpuCapabilityAndCapacity.second()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Found a suitable host, adding to list: {}", host);
                }
                suitableHosts.add(host);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Not using host {}; host has cpu capability? {}, host has capacity?{}",
                            host, cpuCapabilityAndCapacity.first(), cpuCapabilityAndCapacity.second());
                }
                avoid.addHost(host.getId());
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Host Allocator returning " + suitableHosts.size() + " suitable hosts");
        }

        return suitableHosts;
    }

    // Reorder hosts in the decreasing order of free capacity.
    private List<? extends Host> reorderHostsByCapacity(DeploymentPlan plan, List<? extends Host> hosts) {
        Long zoneId = plan.getDataCenterId();
        Long clusterId = plan.getClusterId();
        Pair<List<Long>, Map<Long, Double>> result = getOrderedHostsByCapacity(zoneId, clusterId);
        List<Long> hostIdsByFreeCapacity = result.first();
        Map<Long, String> sortedHostByCapacity = result.second().entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> decimalFormat.format(entry.getValue() * 100) + "%",
                        (e1, e2) -> e1, LinkedHashMap::new));
        if (logger.isDebugEnabled()) {
            logger.debug("List of hosts: [{}] in descending order of free capacity (percentage) in the cluster: {}",
                    hostIdsByFreeCapacity, sortedHostByCapacity);
        }

        //now filter the given list of Hosts by this ordered list
        Map<Long, Host> hostMap = new HashMap<>();
        for (Host host : hosts) {
            hostMap.put(host.getId(), host);
        }
        List<Long> matchingHostIds = new ArrayList<>(hostMap.keySet());

        hostIdsByFreeCapacity.retainAll(matchingHostIds);

        List<Host> reorderedHosts = new ArrayList<>();
        for(Long id: hostIdsByFreeCapacity){
            reorderedHosts.add(hostMap.get(id));
        }

        return reorderedHosts;
    }

    private Pair<List<Long>, Map<Long, Double>> getOrderedHostsByCapacity(Long zoneId, Long clusterId) {
        double cpuToMemoryWeight = ConfigurationManager.HostCapacityTypeCpuMemoryWeight.value();
        // Get capacity by which we should reorder
        short capacityType = FirstFitPlanner.getHostCapacityTypeToOrderCluster(
                _configDao.getValue(Config.HostCapacityTypeToOrderClusters.key()), cpuToMemoryWeight);
        logger.debug("CapacityType: {} is used for Host ordering", FirstFitPlanner.getCapacityTypeName(capacityType));
        if (capacityType >= 0) { // for CPU or RAM
            return _capacityDao.orderHostsByFreeCapacity(zoneId, clusterId, capacityType);
        }
        List<CapacityVO> capacities = _capacityDao.listHostCapacityByCapacityTypes(zoneId, clusterId,
                List.of(Capacity.CAPACITY_TYPE_CPU, Capacity.CAPACITY_TYPE_MEMORY));
        Map<Long, Double> hostByComputedCapacity = getHostByCombinedCapacities(capacities, cpuToMemoryWeight);
        return new Pair<>(new ArrayList<>(hostByComputedCapacity.keySet()), hostByComputedCapacity);
    }

    @NotNull
    public static Map<Long, Double> getHostByCombinedCapacities(List<CapacityVO> capacities, double cpuToMemoryWeight) {
        Map<Long, Double> hostByComputedCapacity = new HashMap<>();
        for (CapacityVO capacityVO : capacities) {
            long hostId = capacityVO.getHostOrPoolId();
            double applicableWeight = capacityVO.getCapacityType() == Capacity.CAPACITY_TYPE_CPU ? cpuToMemoryWeight : 1 - cpuToMemoryWeight;
            double capacityMetric = applicableWeight * (capacityVO.getTotalCapacity() - (capacityVO.getUsedCapacity() + capacityVO.getReservedCapacity()))/capacityVO.getTotalCapacity();
            hostByComputedCapacity.merge(hostId, capacityMetric, Double::sum);
        }

        return hostByComputedCapacity.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    private List<? extends Host> reorderHostsByNumberOfVms(DeploymentPlan plan, List<? extends Host> hosts, Account account) {
        if (account == null) {
            return hosts;
        }
        long dcId = plan.getDataCenterId();
        Long podId = plan.getPodId();
        Long clusterId = plan.getClusterId();

        List<Long> hostIdsByVmCount = _vmInstanceDao.listHostIdsByVmCount(dcId, podId, clusterId, account.getAccountId());
        if (logger.isDebugEnabled()) {
            logger.debug("List of hosts in ascending order of number of VMs: " + hostIdsByVmCount);
        }

        //now filter the given list of Hosts by this ordered list
        Map<Long, Host> hostMap = new HashMap<>();
        for (Host host : hosts) {
            hostMap.put(host.getId(), host);
        }
        List<Long> matchingHostIds = new ArrayList<>(hostMap.keySet());

        hostIdsByVmCount.retainAll(matchingHostIds);

        List<Host> reorderedHosts = new ArrayList<>();
        for (Long id : hostIdsByVmCount) {
            reorderedHosts.add(hostMap.get(id));
        }

        return reorderedHosts;
    }

    @Override
    public boolean isVirtualMachineUpgradable(VirtualMachine vm, ServiceOffering offering) {
        // currently we do no special checks to rule out a VM being upgradable to an offering, so
        // return true
        return true;
    }

    protected List<? extends Host> prioritizeHosts(VMTemplateVO template, ServiceOffering offering, List<? extends Host> hosts) {
        if (template == null) {
            return hosts;
        }

        // Determine the guest OS category of the template
        String templateGuestOSCategory = getTemplateGuestOSCategory(template);

        List<Host> prioritizedHosts = new ArrayList<>();
        List<Host> noHvmHosts = new ArrayList<>();

        // If a template requires HVM and a host doesn't support HVM, remove it from consideration
        List<Host> hostsToCheck = new ArrayList<>();
        if (template.isRequiresHvm()) {
            for (Host host : hosts) {
                if (hostSupportsHVM(host)) {
                    hostsToCheck.add(host);
                } else {
                    noHvmHosts.add(host);
                }
            }
        } else {
            hostsToCheck.addAll(hosts);
        }

        if (logger.isDebugEnabled()) {
            if (noHvmHosts.size() > 0) {
                logger.debug("Not considering hosts: " + noHvmHosts + "  to deploy template: " + template + " as they are not HVM enabled");
            }
        }
        // If a host is tagged with the same guest OS category as the template, move it to a high priority list
        // If a host is tagged with a different guest OS category than the template, move it to a low priority list
        List<Host> highPriorityHosts = new ArrayList<>();
        List<Host> lowPriorityHosts = new ArrayList<>();
        for (Host host : hostsToCheck) {
            String hostGuestOSCategory = getHostGuestOSCategory(host);
            if (hostGuestOSCategory == null) {
                continue;
            } else if (templateGuestOSCategory != null && templateGuestOSCategory.equals(hostGuestOSCategory)) {
                highPriorityHosts.add(host);
            } else {
                lowPriorityHosts.add(host);
            }
        }

        hostsToCheck.removeAll(highPriorityHosts);
        hostsToCheck.removeAll(lowPriorityHosts);

        // Prioritize the remaining hosts by HVM capability
        for (Host host : hostsToCheck) {
            if (!template.isRequiresHvm() && !hostSupportsHVM(host)) {
                // Host and template both do not support hvm, put it as first consideration
                prioritizedHosts.add(0, host);
            } else {
                // Template doesn't require hvm, but the machine supports it, make it last for consideration
                prioritizedHosts.add(host);
            }
        }

        // Merge the lists
        prioritizedHosts.addAll(0, highPriorityHosts);
        prioritizedHosts.addAll(lowPriorityHosts);

        // if service offering is not GPU enabled then move all the GPU enabled hosts to the end of priority list.
        if (_serviceOfferingDetailsDao.findDetail(offering.getId(), GPU.Keys.vgpuType.toString()) == null && offering.getVgpuProfileId() == null) {

            List<Host> gpuEnabledHosts = new ArrayList<>();
            // Check for GPU enabled hosts.
            for (Host host : prioritizedHosts) {
                if (_resourceMgr.isHostGpuEnabled(host.getId())) {
                    gpuEnabledHosts.add(host);
                }
            }
            // Move GPU enabled hosts to the end of list
            if(!gpuEnabledHosts.isEmpty()) {
                prioritizedHosts.removeAll(gpuEnabledHosts);
                prioritizedHosts.addAll(gpuEnabledHosts);
            }
        }
        return prioritizedHosts;
    }

    protected boolean hostSupportsHVM(Host host) {
        if (!_checkHvm) {
            return true;
        }
        // Determine host capabilities
        String caps = host.getCapabilities();

        if (caps != null) {
            String[] tokens = caps.split(",");
            for (String token : tokens) {
                if (token.contains("hvm")) {
                    return true;
                }
            }
        }

        return false;
    }

    protected String getHostGuestOSCategory(Host host) {
        DetailVO hostDetail = _hostDetailsDao.findDetail(host.getId(), "guest.os.category.id");
        if (hostDetail != null) {
            String guestOSCategoryIdString = hostDetail.getValue();
            long guestOSCategoryId;

            try {
                guestOSCategoryId = Long.parseLong(guestOSCategoryIdString);
            } catch (Exception e) {
                return null;
            }

            GuestOSCategoryVO guestOSCategory = _guestOSCategoryDao.findById(guestOSCategoryId);

            if (guestOSCategory != null) {
                return guestOSCategory.getName();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    protected String getTemplateGuestOSCategory(VMTemplateVO template) {
        long guestOSId = template.getGuestOSId();
        GuestOSVO guestOS = _guestOSDao.findById(guestOSId);

        if (guestOS == null) {
            return null;
        }

        long guestOSCategoryId = guestOS.getCategoryId();
        GuestOSCategoryVO guestOSCategory = _guestOSCategoryDao.findById(guestOSCategoryId);
        return guestOSCategory.getName();
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        if (_configDao != null) {
            Map<String, String> configs = _configDao.getConfiguration(params);
            String value = configs.get("xenserver.check.hvm");
            _checkHvm = value == null ? true : Boolean.parseBoolean(value);
        }
        return true;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

}
