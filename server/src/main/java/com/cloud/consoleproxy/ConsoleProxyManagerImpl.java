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
package com.cloud.consoleproxy;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.agent.lb.IndirectAgentLB;
import org.apache.cloudstack.ca.CAManager;
import org.apache.cloudstack.consoleproxy.ConsoleAccessManager;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.framework.ca.Certificate;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.framework.security.keys.KeysManager;
import org.apache.cloudstack.framework.security.keystore.KeystoreDao;
import org.apache.cloudstack.framework.security.keystore.KeystoreManager;
import org.apache.cloudstack.framework.security.keystore.KeystoreVO;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.ConsoleProxyLoadReportCommand;
import com.cloud.agent.api.RebootCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupProxyCommand;
import com.cloud.agent.api.check.CheckSshAnswer;
import com.cloud.agent.api.check.CheckSshCommand;
import com.cloud.agent.api.proxy.ConsoleProxyLoadAnswer;
import com.cloud.agent.manager.Commands;
import com.cloud.cluster.ClusterManager;
import com.cloud.configuration.Config;
import com.cloud.configuration.ConfigurationManagerImpl;
import com.cloud.configuration.ZoneConfig;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenter.NetworkType;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.deploy.DataCenterDeployment;
import com.cloud.deploy.DeployDestination;
import com.cloud.deploy.DeploymentPlanner;
import com.cloud.event.ActionEvent;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.info.ConsoleProxyConnectionInfo;
import com.cloud.info.ConsoleProxyInfo;
import com.cloud.info.ConsoleProxyLoadInfo;
import com.cloud.info.ConsoleProxyStatus;
import com.cloud.info.RunningHostCountInfo;
import com.cloud.info.RunningHostInfoAgregator;
import com.cloud.info.RunningHostInfoAgregator.ZoneHostInfo;
import com.cloud.network.Network;
import com.cloud.network.NetworkModel;
import com.cloud.network.Networks.TrafficType;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.IPAddressVO;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.rules.RulesManager;
import com.cloud.offering.NetworkOffering;
import com.cloud.offering.ServiceOffering;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceStateAdapter;
import com.cloud.resource.ServerResource;
import com.cloud.resource.UnableDeleteHostException;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.Storage;
import com.cloud.storage.StoragePoolStatus;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.utils.DateUtil;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.PasswordGenerator;
import com.cloud.utils.StringUtils;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.QueryBuilder;
import com.cloud.utils.db.SearchCriteria.Op;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallbackNoReturn;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.events.SubscriptionMgr;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.ConsoleProxyVO;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.SystemVmLoadScanHandler;
import com.cloud.vm.SystemVmLoadScanner;
import com.cloud.vm.SystemVmLoadScanner.AfterScanAction;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VirtualMachineGuru;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.VirtualMachineName;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.ConsoleProxyDao;
import com.cloud.vm.dao.VMInstanceDetailsDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Class to manage console proxys. <br><br>
 * Possible console proxy state transition cases:<br>
 * - Stopped -> Starting -> Running <br>
 * - HA -> Stopped -> Starting -> Running <br>
 * - Migrating -> Running    (if previous state is Running before it enters into Migrating state) <br>
 * - Migrating -> Stopped    (if previous state is not Running before it enters into Migrating state) <br>
 * - Running -> HA           (if agent lost connection) <br>
 * - Stopped -> Destroyed <br>
 *
 * <b>Starting</b>, <b>HA</b>, <b>Migrating</b> and <b>Running</b> states are all counted as <b>Open</b> for available capacity calculation  because sooner or later, it will be driven into <b>Running</b> state
 **/
public class ConsoleProxyManagerImpl extends ManagerBase implements ConsoleProxyManager, VirtualMachineGuru, SystemVmLoadScanHandler<Long>, ResourceStateAdapter, Configurable {


    private static final int DEFAULT_CAPACITY_SCAN_INTERVAL_IN_MILLISECONDS = 30000;
    private static final int ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC_IN_SECONDS = 180;
    private static final int STARTUP_DELAY_IN_MILLISECONDS = 60000;

    private int consoleProxyPort = ConsoleProxyManager.DEFAULT_PROXY_VNC_PORT;

    private int managementPort = 8250;

    private List<ConsoleProxyAllocator> consoleProxyAllocators;

    @Inject
    private ConsoleProxyDao consoleProxyDao;
    @Inject
    private DataCenterDao dataCenterDao;
    @Inject
    private VMTemplateDao vmTemplateDao;
    @Inject
    private HostPodDao hostPodDao;
    @Inject
    private HostDao hostDao;
    @Inject
    private ConfigurationDao configurationDao;
    @Inject
    private VMInstanceDao vmInstanceDao;
    @Inject
    private TemplateDataStoreDao templateDataStoreDao;
    @Inject
    private AgentManager agentManager;
    @Inject
    private NetworkOrchestrationService networkOrchestrationService;
    @Inject
    private NetworkModel networkModel;
    @Inject
    private AccountManager accountManager;
    @Inject
    private ServiceOfferingDao serviceOfferingDao;
    @Inject
    private NetworkOfferingDao networkOfferingDao;
    @Inject
    private PrimaryDataStoreDao primaryDataStoreDao;
    @Inject
    private VMInstanceDetailsDao vmInstanceDetailsDao;
    @Inject
    private ResourceManager resourceManager;
    @Inject
    private NetworkDao networkDao;
    @Inject
    private RulesManager rulesManager;
    @Inject
    private IPAddressDao ipAddressDao;
    @Inject
    private KeysManager keysManager;
    @Inject
    private VirtualMachineManager virtualMachineManager;
    @Inject
    private IndirectAgentLB indirectAgentLB;
    @Inject
    private CAManager caManager;
    @Inject
    private NetworkOrchestrationService networkMgr;

    private ConsoleProxyListener consoleProxyListener;

    private ServiceOfferingVO serviceOfferingVO;

    private long capacityScanInterval = DEFAULT_CAPACITY_SCAN_INTERVAL_IN_MILLISECONDS;
    private int capacityPerProxy = ConsoleProxyManager.DEFAULT_PROXY_CAPACITY;
    private int standbyCapacity = ConsoleProxyManager.DEFAULT_STANDBY_CAPACITY;

    private boolean useStorageVm;
    private boolean disableRpFilter = false;
    private String instance;

    private int proxySessionTimeoutValue = DEFAULT_PROXY_SESSION_TIMEOUT;
    private boolean sslEnabled = false;
    private String consoleProxyUrlDomain;

    private SystemVmLoadScanner<Long> loadScanner;
    private Map<Long, ZoneHostInfo> zoneHostInfoMap;
    private Map<Long, ConsoleProxyLoadInfo> zoneProxyCountMap;
    private Map<Long, ConsoleProxyLoadInfo> zoneVmCountMap;

    private String staticPublicIp;
    private int staticPort;

    private final GlobalLock allocProxyLock = GlobalLock.getInternLock(getAllocProxyLockName());

    protected Gson jsonParser = new GsonBuilder().setVersion(1.3).create();

    protected Set<State> availableVmStateOnAssignProxy = new HashSet<>(Arrays.asList(State.Starting, State.Running, State.Stopping, State.Migrating));

    @Inject
    private KeystoreDao _ksDao;
    @Inject
    private KeystoreManager _ksMgr;
    @Inject
    private ConsoleAccessManager consoleAccessManager;

    public class VmBasedAgentHook extends AgentHookBase {

        public VmBasedAgentHook(VMInstanceDao instanceDao, HostDao hostDao, ConfigurationDao cfgDao, KeystoreManager ksMgr,
                                AgentManager agentMgr, KeysManager keysMgr, ConsoleAccessManager consoleAccessManager) {
            super(instanceDao, hostDao, cfgDao, ksMgr, agentMgr, keysMgr, consoleAccessManager);
        }

        @Override
        public void onLoadReport(ConsoleProxyLoadReportCommand cmd) {
            updateConsoleProxyStatus(cmd.getLoadInfo(), cmd.getProxyVmId());
        }

        @Override
        public void onAgentDisconnect(long agentId, Status state) {

            if (state == Status.Alert || state == Status.Disconnected) {
                HostVO host = _hostDao.findById(agentId);
                if (host.getType() == Type.ConsoleProxy) {
                    String name = host.getName();
                    if (logger.isInfoEnabled()) {
                        logger.info("Console proxy agent disconnected, proxy: " + name);
                    }
                    if (name != null && name.startsWith("v-")) {
                        String[] tokens = name.split("-");
                        long proxyVmId;
                        String tokenSecondElement = tokens[1];
                        try {
                            proxyVmId = Long.parseLong(tokenSecondElement);
                        } catch (NumberFormatException e) {
                            logger.error(String.format("[%s] is not a valid number, unable to parse [%s].", tokenSecondElement, e.getMessage()), e);
                            return;
                        }

                        final ConsoleProxyVO proxy = consoleProxyDao.findById(proxyVmId);
                        if (proxy == null && logger.isInfoEnabled()) {
                            logger.info("Console proxy agent disconnected but corresponding console proxy VM no longer exists in DB, proxy: " + name);
                        }
                    } else {
                        assert (false) : "Invalid console proxy name: " + name;
                    }
                }
            }

        }

        @Override
        protected HostVO findConsoleProxyHost(StartupProxyCommand startupCmd) {
            long proxyVmId = startupCmd.getProxyVmId();
            ConsoleProxyVO consoleProxy = consoleProxyDao.findById(proxyVmId);
            if (consoleProxy == null) {
                logger.info("Proxy " + proxyVmId + " is no longer in DB, skip sending startup command");
                return null;
            }

            assert (consoleProxy != null);
            return findConsoleProxyHostByName(consoleProxy.getHostName());
        }

    }

    @Override
    public ConsoleProxyInfo assignProxy(final long dataCenterId, final VMInstanceVO userVm) {
        ConsoleProxyVO proxy = doAssignProxy(dataCenterId, userVm);
        if (proxy == null) {
            return null;
        }

        if (proxy.getPublicIpAddress() == null) {
            logger.warn(String.format("Assigned console proxy [%s] does not have a valid public IP address.", proxy.toString()));
            return null;
        }

        KeystoreVO ksVo = _ksDao.findByName(ConsoleProxyManager.CERTIFICATE_NAME);
        if (proxy.isSslEnabled() && ksVo == null) {
            logger.warn(String.format("SSL is enabled for console proxy [%s] but no server certificate found in database.", proxy.toString()));
        }

        ConsoleProxyInfo info;
        if (staticPublicIp == null) {
            info = new ConsoleProxyInfo(proxy.isSslEnabled(), proxy.getPublicIpAddress(), consoleProxyPort, proxy.getPort(), consoleProxyUrlDomain);
        } else {
            info = new ConsoleProxyInfo(proxy.isSslEnabled(), staticPublicIp, consoleProxyPort, staticPort, consoleProxyUrlDomain);
        }
        info.setProxyName(proxy.getHostName());
        return  info;
    }

    public ConsoleProxyVO doAssignProxy(long dataCenterId, VMInstanceVO vm) {
        ConsoleProxyVO proxy = null;

        if (!availableVmStateOnAssignProxy.contains(vm.getState())) {
            if (logger.isInfoEnabled()) {
                logger.info(String.format("Detected that %s is not currently in \"Starting\", \"Running\", \"Stopping\" or \"Migrating\" state, it will fail the proxy assignment.", vm.toString()));
            }
            return null;
        }

        if (allocProxyLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC_IN_SECONDS)) {
            try {
                if (vm.getProxyId() != null) {
                    proxy = consoleProxyDao.findById(vm.getProxyId());

                    if (proxy != null) {
                        if (!isInAssignableState(proxy)) {
                            if (logger.isInfoEnabled()) {
                                logger.info("A previous assigned proxy is not assignable now, reassign console proxy for user vm : {}", vm);
                            }
                            proxy = null;
                        } else {
                            if (consoleProxyDao.getProxyActiveLoad(proxy.getId()) < capacityPerProxy || hasPreviousSession(proxy, vm)) {
                                if (logger.isDebugEnabled()) {
                                    logger.debug("Assign previous allocated console proxy for user vm: {}", vm);
                                }

                                if (proxy.getActiveSession() >= capacityPerProxy) {
                                    logger.warn("Assign overloaded proxy to user VM as previous session exists, user vm: {}", vm);
                                }
                            } else {
                                proxy = null;
                            }
                        }
                    }
                }

                if (proxy == null) {
                    proxy = assignProxyFromRunningPool(dataCenterId);
                }
            } finally {
                allocProxyLock.unlock();
            }
        } else {
            logger.error("Unable to acquire synchronization lock to get/allocate proxy " +
                    "resource for vm: {}. Previous console proxy allocation is taking too long", vm);
        }

        if (proxy == null) {
            logger.warn("Unable to find or allocate console proxy resource");
            return null;
        }

        if (vm.getProxyId() == null || vm.getProxyId() != proxy.getId()) {
            vmInstanceDao.updateProxyId(vm.getId(), proxy.getId(), DateUtil.currentGMTTime());
        }

        proxy.setSslEnabled(sslEnabled);
        if (sslEnabled) {
            proxy.setPort(443);
        } else {
            proxy.setPort(80);
        }

        return proxy;
    }

    private static boolean isInAssignableState(ConsoleProxyVO proxy) {
        return proxy.getState() == State.Running;
    }

    private boolean hasPreviousSession(ConsoleProxyVO proxy, VMInstanceVO vm) {

        ConsoleProxyStatus status = null;
        try {
            byte[] detailsInBytes = proxy.getSessionDetails();
            String details = detailsInBytes != null ? new String(detailsInBytes, Charset.forName("US-ASCII")) : null;
            status = parseJsonToConsoleProxyStatus(details);
        } catch (JsonParseException e) {
            logger.warn(String.format("Unable to parse proxy [%s] session details [%s] due to [%s].", proxy.toString(), Arrays.toString(proxy.getSessionDetails()), e.getMessage()), e);
        }

        if (status != null && status.getConnections() != null) {
            ConsoleProxyConnectionInfo[] connections = status.getConnections();
            for (ConsoleProxyConnectionInfo connection : connections) {
                long taggedVmId = 0;
                if (connection.tag != null) {
                    try {
                        taggedVmId = Long.parseLong(connection.tag);
                    } catch (NumberFormatException e) {
                        logger.warn(String.format("Unable to parse console proxy connection info passed through tag [%s] due to [%s].", connection.tag, e.getMessage()), e);
                    }
                }

                if (taggedVmId == vm.getId()) {
                    return true;
                }
            }

            return DateUtil.currentGMTTime().getTime() - vm.getProxyAssignTime().getTime() < proxySessionTimeoutValue;
        } else {
            logger.warn(String.format("Unable to retrieve load info from proxy [%s] on an overloaded proxy.", proxy.toString()));
            return false;
        }
    }

    @Override
    public ConsoleProxyVO startProxy(long proxyVmId, boolean ignoreRestartSetting) {
        try {
            ConsoleProxyVO proxy = consoleProxyDao.findById(proxyVmId);
            if (proxy.getState() == State.Running) {
                return proxy;
            }

            String restart = configurationDao.getValue(Config.ConsoleProxyRestart.key());
            if (!ignoreRestartSetting && restart != null && restart.equalsIgnoreCase("false")) {
                return null;
            }

            if (proxy.getState() == State.Stopped) {
                virtualMachineManager.advanceStart(proxy.getUuid(), null, null);
                proxy = consoleProxyDao.findById(proxy.getId());
                return proxy;
            }

            logger.warn(String.format("Console proxy [%s] must be in \"Stopped\" state to start proxy. Current state [%s].", proxy.toString(), proxy.getState()));
        } catch ( ConcurrentOperationException | InsufficientCapacityException | OperationTimedoutException | ResourceUnavailableException ex) {
            logger.warn(String.format("Unable to start proxy [%s] due to [%s].", proxyVmId, ex.getMessage()), ex);
        }

        return null;
    }

    @Override
    @ActionEvent(eventType = EventTypes.EVENT_PROXY_START, eventDescription = "restarting console proxy VM for HA", async = true)
    public void startProxyForHA(VirtualMachine vm, Map<VirtualMachineProfile.Param, Object> params,
            DeploymentPlanner planner) throws InsufficientCapacityException, ResourceUnavailableException,
            ConcurrentOperationException, OperationTimedoutException {
        virtualMachineManager.advanceStart(vm.getUuid(), params, planner);
    }

    public ConsoleProxyVO assignProxyFromRunningPool(long dataCenterId) {

        DataCenterVO zone = dataCenterDao.findById(dataCenterId);
        if (logger.isDebugEnabled()) {
            logger.debug("Assign console proxy from running pool for request from data center: {}", zone);
        }

        ConsoleProxyAllocator allocator = getCurrentAllocator();
        assert (allocator != null);
        List<ConsoleProxyVO> runningList = consoleProxyDao.getProxyListInStates(dataCenterId, State.Running);
        if (runningList != null && runningList.size() > 0) {
            Iterator<ConsoleProxyVO> it = runningList.iterator();
            while (it.hasNext()) {
                ConsoleProxyVO proxy = it.next();
                if (proxy.getActiveSession() >= capacityPerProxy) {
                    it.remove();
                }
            }
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Running [%s] proxy instances [%s].", runningList.size(), runningList.stream().map(proxy -> proxy.toString()).collect(Collectors.joining(", "))));
            }

            List<Pair<Long, Integer>> l = consoleProxyDao.getProxyLoadMatrix();
            Map<Long, Integer> loadInfo = new HashMap<>();
            if (l != null) {
                for (Pair<Long, Integer> p : l) {
                    Long proxyId = p.first();
                    Integer countRunningVms = p.second();

                    loadInfo.put(proxyId, countRunningVms);

                    if (logger.isDebugEnabled()) {
                        logger.debug(String.format("Running proxy instance allocation {\"proxyId\": %s, \"countRunningVms\": %s}.", proxyId, countRunningVms));
                    }

                }
            }

            Long allocated = allocator.allocProxy(runningList, loadInfo, dataCenterId);

            if (allocated == null) {
                logger.debug("Console proxy not found, unable to assign console proxy from running pool for request from zone [{}].", zone);
                return null;
            }

            return consoleProxyDao.findById(allocated);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Empty running proxy pool for now in data center: {}", zone);
            }

        }

        return null;
    }

    public ConsoleProxyVO assignProxyFromStoppedPool(long dataCenterId) {

        List<ConsoleProxyVO> l = consoleProxyDao.getProxyListInStates(dataCenterId, State.Starting, State.Stopped, State.Migrating, State.Stopping);
        if (CollectionUtils.isNotEmpty(l)) {
            return l.get(0);
        }

        return null;
    }

    public ConsoleProxyVO startNew(long dataCenterId) throws ConcurrentOperationException {

        if (logger.isDebugEnabled()) {
            logger.debug("Assign console proxy from a newly started instance for request from data center : " + dataCenterId);
        }

        if (!allowToLaunchNew(dataCenterId)) {
            String configKey = Config.ConsoleProxyLaunchMax.key();
            logger.warn(String.format("The number of launched console proxys on zone [%s] has reached the limit [%s]. Limit set in [%s].", dataCenterId, configurationDao.getValue(configKey), configKey));
            return null;
        }

        HypervisorType availableHypervisor = resourceManager.getAvailableHypervisor(dataCenterId);
        List<VMTemplateVO> templates = vmTemplateDao.findSystemVMReadyTemplates(dataCenterId, availableHypervisor,
                ResourceManager.SystemVmPreferredArchitecture.valueIn(dataCenterId));
        if (CollectionUtils.isEmpty(templates)) {
            throw new CloudRuntimeException("Not able to find the System templates or not downloaded in zone " + dataCenterId);
        }

        Map<String, Object> context = createProxyInstance(dataCenterId, templates);

        long proxyVmId = (Long)context.get("proxyVmId");
        if (proxyVmId == 0) {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Unable to create proxy instance in zone [%s].", dataCenterId));
            }
            return null;
        }

        ConsoleProxyVO proxy = consoleProxyDao.findById(proxyVmId);
        if (proxy != null) {
            SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
                new ConsoleProxyAlertEventArgs(ConsoleProxyAlertEventArgs.PROXY_CREATED, dataCenterId, proxy.getId(), proxy, null));
            return proxy;
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to allocate console proxy storage, remove the console proxy record from DB, proxy id: " + proxyVmId);
            }
        }
        return null;
    }

    /**
     * Get the default network for the console proxy VM, based on the zone it is in. Delegates to
     * either {@link #getDefaultNetworkForZone(DataCenter)} or {@link #getDefaultNetworkForAdvancedSGZone(DataCenter)},
     * depending on the zone network type and whether or not security groups are enabled in the zone.
     * @param dc - The zone (DataCenter) of the console proxy VM.
     * @return The default network for use with the console proxy VM.
     */
    protected NetworkVO getDefaultNetworkForCreation(DataCenter dc) {
        if (dc.getNetworkType() == NetworkType.Advanced) {
            return getDefaultNetworkForAdvancedZone(dc);
        } else {
            return getDefaultNetworkForBasicZone(dc);
        }
    }

    /**
     * Get default network for a console proxy VM starting up in an advanced zone. If the zone
     * is security group-enabled, the first network found that supports SG services is returned.
     * If the zone is not SG-enabled, the Public network is returned.
     * @param dc - The zone.
     * @return The selected default network.
     * @throws CloudRuntimeException - If the zone is not a valid choice or a network couldn't be found.
     */
    protected NetworkVO getDefaultNetworkForAdvancedZone(DataCenter dc) {
        if (dc.getNetworkType() != NetworkType.Advanced) {
            throw new CloudRuntimeException("Zone " + dc + " is not advanced.");
        }

        if (dc.isSecurityGroupEnabled()) {
            List<NetworkVO> networks = networkDao.listByZoneSecurityGroup(dc.getId());
            if (CollectionUtils.isEmpty(networks)) {
                throw new CloudRuntimeException("Can not found security enabled network in SG Zone " + dc);
            }

            return networks.get(0);
        }
        else {
            TrafficType defaultTrafficType = TrafficType.Public;
            List<NetworkVO> defaultNetworks = networkDao.listByZoneAndTrafficType(dc.getId(), defaultTrafficType);

            if (defaultNetworks.size() != 1) {
                throw new CloudRuntimeException("Found " + defaultNetworks.size() + " networks of type " + defaultTrafficType + " when expect to find 1");
            }

            return defaultNetworks.get(0);
        }
    }

    /**
     * Get default network for console proxy VM for starting up in a basic zone. Basic zones select
     * the Guest network whether or not the zone is SG-enabled.
     * @param dc - The zone.
     * @return The default network according to the zone's network selection rules.
     * @throws CloudRuntimeException - If the zone is not a valid choice or a network couldn't be found.
     */
    protected NetworkVO getDefaultNetworkForBasicZone(DataCenter dc) {
        if (dc.getNetworkType() != NetworkType.Basic) {
            throw new CloudRuntimeException("Zone " + dc + "is not basic.");
        }

        TrafficType defaultTrafficType = TrafficType.Guest;
        List<NetworkVO> defaultNetworks = networkDao.listByZoneAndTrafficType(dc.getId(), defaultTrafficType);

        if (defaultNetworks.size() != 1) {
            throw new CloudRuntimeException("Found " + defaultNetworks.size() + " networks of type " + defaultTrafficType + " when expect to find 1");
        }

        return defaultNetworks.get(0);
    }

    protected ConsoleProxyVO createOrUpdateConsoleProxy(ConsoleProxyVO proxy, long dataCenterId, long id, String name,
            ServiceOffering serviceOffering, VMTemplateVO template, Account systemAccount) {
        if (proxy == null) {
            proxy = new ConsoleProxyVO(id, serviceOffering.getId(), name, template.getId(),
                    template.getHypervisorType(), template.getGuestOSId(), dataCenterId, systemAccount.getDomainId(),
                    systemAccount.getId(), accountManager.getSystemUser().getId(), 0,
                    serviceOffering.isOfferHA());
            proxy.setDynamicallyScalable(template.isDynamicallyScalable());
            proxy.setLimitCpuUse(serviceOffering.getLimitCpuUse());
            return consoleProxyDao.persist(proxy);
        }
        proxy.setTemplateId(template.getId());
        proxy.setHypervisorType(template.getHypervisorType());
        proxy.setGuestOSId(template.getGuestOSId());
        proxy.setDynamicallyScalable(template.isDynamicallyScalable());
        consoleProxyDao.update(proxy.getId(), proxy);
        return proxy;
    }

    protected Map<String, Object> createProxyInstance(long dataCenterId, List<VMTemplateVO> templates) throws ConcurrentOperationException {

        long id = consoleProxyDao.getNextInSequence(Long.class, "id");
        String name = VirtualMachineName.getConsoleProxyName(id, instance);
        DataCenterVO dc = dataCenterDao.findById(dataCenterId);
        Account systemAcct = accountManager.getSystemAccount();

        DataCenterDeployment plan = new DataCenterDeployment(dataCenterId);

        NetworkVO defaultNetwork = getDefaultNetworkForCreation(dc);

        List<? extends NetworkOffering> offerings =
            networkModel.getSystemAccountNetworkOfferings(NetworkOffering.SystemControlNetwork, NetworkOffering.SystemManagementNetwork);
        LinkedHashMap<Network, List<? extends NicProfile>> networks = new LinkedHashMap<>(offerings.size() + 1);
        NicProfile defaultNic = new NicProfile();
        defaultNic.setDefaultNic(true);
        defaultNic.setDeviceId(2);

        networks.put(networkOrchestrationService.setupNetwork(systemAcct, networkOfferingDao.findById(defaultNetwork.getNetworkOfferingId()), plan, null, null, false).get(0),
                new ArrayList<>(Arrays.asList(defaultNic)));

        for (NetworkOffering offering : offerings) {
            networks.put(networkOrchestrationService.setupNetwork(systemAcct, offering, plan, null, null, false).get(0), new ArrayList<>());
        }

        ServiceOfferingVO serviceOffering = serviceOfferingVO;
        if (serviceOffering == null) {
            serviceOffering = serviceOfferingDao.findDefaultSystemOffering(ServiceOffering.consoleProxyDefaultOffUniqueName, ConfigurationManagerImpl.SystemVMUseLocalStorage.valueIn(dataCenterId));
        }
        ConsoleProxyVO proxy = null;
        for (final Iterator<VMTemplateVO> templateIterator = templates.iterator(); templateIterator.hasNext();) {
            VMTemplateVO template = templateIterator.next();
            proxy = createOrUpdateConsoleProxy(proxy, dataCenterId, id, name, serviceOffering, template, systemAcct);
            try {
                virtualMachineManager.allocate(name, template, serviceOffering, networks, plan,
                        template.getHypervisorType(), null, null);
                proxy = consoleProxyDao.findById(proxy.getId());
                virtualMachineManager.checkDeploymentPlan(proxy, template, serviceOffering, systemAcct, plan);
                break;
            } catch (InsufficientCapacityException e) {
                if (templateIterator.hasNext()) {
                    logger.debug("Unable to allocate proxy {} with {} in {} due to [{}]. Retrying with another template", proxy, template, dc, e.getMessage(), e);
                    continue;
                }
                throw new CloudRuntimeException("Failed to allocate proxy [%s] in zone [%s] with available templates", e);
            }
        }

        Map<String, Object> context = new HashMap<>();
        context.put("dc", dc);
        HostPodVO pod = hostPodDao.findById(proxy.getPodIdToDeployIn());
        context.put("pod", pod);
        context.put("proxyVmId", proxy.getId());

        return context;
    }

    private ConsoleProxyAllocator getCurrentAllocator() {
        for (ConsoleProxyAllocator allocator : consoleProxyAllocators) {
            return allocator;
        }

        return null;
    }

    public void onLoadAnswer(ConsoleProxyLoadAnswer answer) {
        updateConsoleProxyStatus(answer.getDetails(), answer.getProxyVmId());
    }

    public void handleAgentDisconnect(long agentId, Status state) {
        if (state == Status.Alert || state == Status.Disconnected) {
            HostVO host = hostDao.findById(agentId);
            if (host.getType() == Type.ConsoleProxy) {
                String name = host.getName();
                if (logger.isInfoEnabled()) {
                    logger.info("Console proxy agent disconnected, proxy: " + name);
                }
                if (name != null && name.startsWith("v-")) {
                    String[] tokens = name.split("-");
                    long proxyVmId;
                    try {
                        proxyVmId = Long.parseLong(tokens[1]);
                    } catch (NumberFormatException e) {
                        logger.error("Unexpected exception " + e.getMessage(), e);
                        return;
                    }

                    final ConsoleProxyVO proxy = consoleProxyDao.findById(proxyVmId);
                    if (proxy == null && logger.isInfoEnabled()) {
                        logger.info("Console proxy agent disconnected but corresponding console proxy VM no longer exists in DB, proxy: " + name);
                    }
                } else {
                    assert (false) : "Invalid console proxy name: " + name;
                }
            }
        }
    }

    private boolean reserveStandbyCapacity() {
        ConsoleProxyManagementState state = getManagementState();
        return !(state == null || state != ConsoleProxyManagementState.Auto);
    }

    private boolean isConsoleProxyVmRequired(long dcId) {
        DataCenterVO dc = dataCenterDao.findById(dcId);
        dataCenterDao.loadDetails(dc);
        String cpvmReq = dc.getDetail(ZoneConfig.EnableConsoleProxyVm.key());
        if (cpvmReq != null) {
            return Boolean.parseBoolean(cpvmReq);
        }
        return true;
    }

    private boolean allowToLaunchNew(long dcId) {
        if (!isConsoleProxyVmRequired(dcId)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Console proxy vm not required in zone " + dcId + " not launching");
            }
            return false;
        }
        List<ConsoleProxyVO> l =
            consoleProxyDao.getProxyListInStates(dcId, State.Starting, State.Running, State.Stopping,
                State.Stopped, State.Migrating, State.Shutdown, State.Unknown);

        String value = configurationDao.getValue(Config.ConsoleProxyLaunchMax.key());
        int launchLimit = NumbersUtil.parseInt(value, 10);
        return l.size() < launchLimit;
    }

    private boolean checkCapacity(ConsoleProxyLoadInfo proxyCountInfo, ConsoleProxyLoadInfo vmCountInfo) {
        return proxyCountInfo.getCount() * capacityPerProxy - vmCountInfo.getCount() > standbyCapacity;
    }

    private void allocCapacity(long dataCenterId) {
        DataCenterVO zone = dataCenterDao.findById(dataCenterId);
        if (logger.isDebugEnabled()) {
            logger.debug("Allocating console proxy standby capacity for zone [{}].", zone);
        }

        ConsoleProxyVO proxy = null;
        String errorString = null;
        try {
            boolean consoleProxyVmFromStoppedPool = false;
            proxy = assignProxyFromStoppedPool(dataCenterId);
            if (proxy == null) {
                if (logger.isInfoEnabled()) {
                    logger.info("No stopped console proxy is available, need to allocate a new console proxy");
                }

                if (allocProxyLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC_IN_SECONDS)) {
                    try {
                        proxy = startNew(dataCenterId);
                    } catch (ConcurrentOperationException e) {
                        logger.warn("Unable to start new console proxy on zone [{}] due to [{}].", zone, e.getMessage(), e);
                    } finally {
                        allocProxyLock.unlock();
                    }
                } else {
                    if (logger.isInfoEnabled()) {
                        logger.info("Unable to acquire synchronization lock for console proxy vm allocation, wait for next scan");
                    }
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Found a stopped console proxy, starting it. VM: {}", proxy);
                }
                consoleProxyVmFromStoppedPool = true;
            }

            if (proxy != null) {
                long proxyVmId = proxy.getId();
                proxy = startProxy(proxyVmId, false);

                if (proxy != null) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Console proxy {} is started", proxy);
                    }
                    SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
                        new ConsoleProxyAlertEventArgs(ConsoleProxyAlertEventArgs.PROXY_UP, dataCenterId, proxy.getId(), proxy, null));
                } else {
                    if (logger.isInfoEnabled()) {
                        logger.info("Unable to start console proxy vm for standby capacity, vm: {}, will recycle it and start a new one", proxy);
                    }

                    if (consoleProxyVmFromStoppedPool) {
                        destroyProxy(proxyVmId);
                    }
                }
            }
        } catch (Exception e) {
           errorString = e.getMessage();
           logger.warn("Unable to allocate console proxy standby capacity for zone [{}] due to [{}].", zone, e.getMessage(), e);
           throw e;
        } finally {
            if (proxy == null || proxy.getState() != State.Running)
                SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
                    new ConsoleProxyAlertEventArgs(ConsoleProxyAlertEventArgs.PROXY_CREATE_FAILURE, dataCenterId, 0l, null, errorString));
        }
    }

    public boolean isZoneReady(Map<Long, ZoneHostInfo> zoneHostInfoMap, DataCenter dataCenter) {
        Integer totalUpAndEnabledHosts = hostDao.countUpAndEnabledHostsInZone(dataCenter.getId());
        if (totalUpAndEnabledHosts != null && totalUpAndEnabledHosts < 1) {
            logger.debug("{} has no host available which is enabled and in Up state", dataCenter);
            return false;
        }
        ZoneHostInfo zoneHostInfo = zoneHostInfoMap.get(dataCenter.getId());
        if (zoneHostInfo != null && isZoneHostReady(zoneHostInfo)) {
            List<VMTemplateVO> templates = vmTemplateDao.findSystemVMReadyTemplates(dataCenter.getId(),
                    HypervisorType.Any, null);
            if (CollectionUtils.isEmpty(templates)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("System vm template is not ready at data center {}, wait until it is ready to launch console proxy vm", dataCenter);
                }
                return false;
            }
            boolean useLocalStorage = BooleanUtils.toBoolean(ConfigurationManagerImpl.SystemVMUseLocalStorage.valueIn(dataCenter.getId()));
            boolean hasDatacenterStoragePoolHostInfo = consoleProxyDao.hasDatacenterStoragePoolHostInfo(dataCenter.getId(), !useLocalStorage);
            if (hasDatacenterStoragePoolHostInfo) {
                return true;
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Primary storage is not ready, wait until it is ready to launch console proxy");
                }
            }
        }
        return false;
    }

    private boolean isZoneHostReady(ZoneHostInfo zoneHostInfo) {
        int expectedFlags;
        if (useStorageVm) {
            expectedFlags = ZoneHostInfo.ROUTING_HOST_MASK;
        } else {
            expectedFlags = ZoneHostInfo.ALL_HOST_MASK;
        }

        return (zoneHostInfo.getFlags() & expectedFlags) == expectedFlags;
    }

    private synchronized Map<Long, ZoneHostInfo> getZoneHostInfo() {
        Date cutTime = DateUtil.currentGMTTime();
        List<RunningHostCountInfo> l = hostDao.getRunningHostCounts(new Date(cutTime.getTime() - ClusterManager.HeartbeatThreshold.value()));

        RunningHostInfoAgregator aggregator = new RunningHostInfoAgregator();
        if (l.size() > 0) {
            for (RunningHostCountInfo countInfo : l) {
                aggregator.aggregate(countInfo);
            }
        }

        return aggregator.getZoneHostInfoMap();
    }

    @Override
    public boolean start() {
        if (logger.isInfoEnabled()) {
            logger.info("Start console proxy manager");
        }

        return true;
    }

    @Override
    public boolean stop() {
        if (logger.isInfoEnabled()) {
            logger.info("Stop console proxy manager");
        }

        loadScanner.stop();
        allocProxyLock.releaseRef();
        resourceManager.unregisterResourceStateAdapter(this.getClass().getSimpleName());
        return true;
    }

    @Override
    public boolean stopProxy(long proxyVmId) {
        ConsoleProxyVO proxy = consoleProxyDao.findById(proxyVmId);
        if (proxy == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Stopping console proxy failed: console proxy " + proxyVmId + " no longer exists");
            }
            return false;
        }

        try {
            virtualMachineManager.stop(proxy.getUuid());
            return true;
        } catch (CloudRuntimeException | ResourceUnavailableException e) {
            logger.warn(String.format("Unable to stop console proxy [%s] due to [%s].", proxy.toString(), e.getMessage()), e);
            return false;
        }
    }

    @Override
    @DB
    public void setManagementState(final ConsoleProxyManagementState state) {
        try {
            final ConsoleProxyManagementState lastState = getManagementState();
            if (lastState == null) {
                return;
            }

            if (lastState != state) {
                Transaction.execute(new TransactionCallbackNoReturn() {
                    @Override
                    public void doInTransactionWithoutResult(TransactionStatus status) {
                        configurationDao.update(Config.ConsoleProxyManagementLastState.key(), Config.ConsoleProxyManagementLastState.getCategory(), lastState.toString());
                        configurationDao.update(Config.ConsoleProxyManagementState.key(), Config.ConsoleProxyManagementState.getCategory(), state.toString());
                    }
                });
            }
        } catch (Exception e) {
            logger.error(String.format("Unable to set console proxy management state to [%s] due to [%s].", state, e.getMessage()), e);
        }
    }

    @Override
    public ConsoleProxyManagementState getManagementState() {
        String configKey = Config.ConsoleProxyManagementState.key();
        String value = configurationDao.getValue(configKey);

        if (value != null) {
            ConsoleProxyManagementState state = ConsoleProxyManagementState.valueOf(value);

            if (state != null) {
                return state;
            }
        }

        logger.error(String.format("Value [%s] set in global configuration [%s] is not a valid console proxy management state.", value, configKey));
        return null;
    }

    @Override
    @DB
    public void resumeLastManagementState() {
        try {
            ConsoleProxyManagementState state = getManagementState();
            ConsoleProxyManagementState lastState = getLastManagementState();
            if (lastState == null) {
                return;
            }

            if (lastState != state) {
                configurationDao.update(Config.ConsoleProxyManagementState.key(), Config.ConsoleProxyManagementState.getCategory(), lastState.toString());
            }
        } catch (Exception e) {
            logger.error(String.format("Unable to resume last management state due to [%s].", e.getMessage()), e);
        }
    }

    private ConsoleProxyManagementState getLastManagementState() {
        String configKey = Config.ConsoleProxyManagementLastState.key();
        String value = configurationDao.getValue(configKey);

        if (value != null) {
            ConsoleProxyManagementState state = ConsoleProxyManagementState.valueOf(value);

            if (state != null) {
                return state;
            }
        }

        logger.error(String.format("Value [%s] set in global configuration [%s] is not a valid console proxy management state.", value, configKey));
        return null;
    }

    @Override
    public boolean rebootProxy(long proxyVmId) {
        final ConsoleProxyVO proxy = consoleProxyDao.findById(proxyVmId);

        if (proxy == null || proxy.getState() == State.Destroyed) {
            return false;
        }

        if (proxy.getState() == State.Running && proxy.getHostId() != null) {
            final RebootCommand cmd = new RebootCommand(proxy.getInstanceName(), virtualMachineManager.getExecuteInSequence(proxy.getHypervisorType()));
            final Answer answer = agentManager.easySend(proxy.getHostId(), cmd);

            if (answer != null && answer.getResult()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Successfully reboot console proxy " + proxy.getHostName());
                }

                SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
                    new ConsoleProxyAlertEventArgs(ConsoleProxyAlertEventArgs.PROXY_REBOOTED, proxy.getDataCenterId(), proxy.getId(), proxy, null));

                return true;
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("failed to reboot console proxy : " + proxy.getHostName());
                }

                return false;
            }
        } else {
            return startProxy(proxyVmId, false) != null;
        }
    }

    @Override
    public boolean destroyProxy(long vmId) {
        ConsoleProxyVO proxy = consoleProxyDao.findById(vmId);
        try {
            virtualMachineManager.expunge(proxy.getUuid());
            proxy.setPublicIpAddress(null);
            proxy.setPublicMacAddress(null);
            proxy.setPublicNetmask(null);
            proxy.setPrivateMacAddress(null);
            proxy.setPrivateIpAddress(null);
            consoleProxyDao.update(proxy.getId(), proxy);
            consoleProxyDao.remove(vmId);
            HostVO host = hostDao.findByTypeNameAndZoneId(proxy.getDataCenterId(), proxy.getHostName(), Type.ConsoleProxy);
            if (host != null) {
                logger.debug("Removing host [{}] entry for proxy [{}].", host, proxy);
                return hostDao.remove(host.getId());
            }

            return true;
        } catch (ResourceUnavailableException e) {
            logger.warn(String.format("Unable to destroy console proxy [%s] due to [%s].", proxy, e.getMessage()), e);
            return false;
        }
    }

    @Override
    public int getVncPort() {
        return sslEnabled && _ksDao.findByName(ConsoleProxyManager.CERTIFICATE_NAME) != null ? 8443 : 8080;
    }

    private String getAllocProxyLockName() {
        return "consoleproxy.alloc";
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        if (logger.isInfoEnabled()) {
            logger.info("Start configuring console proxy manager : " + name);
        }

        Map<String, String> configs = configurationDao.getConfiguration("management-server", params);

        String value = configs.get(ConsoleProxySslEnabled.key());
        if (value != null && value.equalsIgnoreCase("true")) {
            sslEnabled = true;
        }

        consoleProxyUrlDomain = configs.get(Config.ConsoleProxyUrlDomain.key());
        if( sslEnabled && (consoleProxyUrlDomain == null || consoleProxyUrlDomain.isEmpty())) {
            logger.warn("Empty console proxy domain, explicitly disabling SSL");
            sslEnabled = false;
        }

        value = configs.get(Config.ConsoleProxyCapacityScanInterval.key());
        capacityScanInterval = NumbersUtil.parseLong(value, DEFAULT_CAPACITY_SCAN_INTERVAL_IN_MILLISECONDS);

        capacityPerProxy = NumbersUtil.parseInt(configs.get("consoleproxy.session.max"), DEFAULT_PROXY_CAPACITY);
        standbyCapacity = NumbersUtil.parseInt(configs.get("consoleproxy.capacity.standby"), DEFAULT_STANDBY_CAPACITY);
        proxySessionTimeoutValue = NumbersUtil.parseInt(configs.get("consoleproxy.session.timeout"), DEFAULT_PROXY_SESSION_TIMEOUT);

        value = configs.get("consoleproxy.port");
        if (value != null) {
            consoleProxyPort = NumbersUtil.parseInt(value, ConsoleProxyManager.DEFAULT_PROXY_VNC_PORT);
        }

        value = configs.get(Config.ConsoleProxyDisableRpFilter.key());
        if (value != null && value.equalsIgnoreCase("true")) {
            disableRpFilter = true;
        }

        value = configs.get("secondary.storage.vm");
        if (value != null && value.equalsIgnoreCase("true")) {
            useStorageVm = true;
        }

        if (logger.isInfoEnabled()) {
            logger.info("Console proxy max session soft limit : " + capacityPerProxy);
            logger.info("Console proxy standby capacity : " + standbyCapacity);
        }

        instance = configs.get("instance.name");
        if (instance == null) {
            instance = "DEFAULT";
        }

        Map<String, String> agentMgrConfigs = configurationDao.getConfiguration("AgentManager", params);

        value = agentMgrConfigs.get("port");
        managementPort = NumbersUtil.parseInt(value, 8250);

        consoleProxyListener = new ConsoleProxyListener(new VmBasedAgentHook(vmInstanceDao, hostDao, configurationDao,
                _ksMgr, agentManager, keysManager, consoleAccessManager));
        agentManager.registerForHostEvents(consoleProxyListener, true, true, false);

        virtualMachineManager.registerGuru(VirtualMachine.Type.ConsoleProxy, this);

        String configKey = Config.ConsoleProxyServiceOffering.key();
        String cpvmSrvcOffIdStr = configs.get(configKey);
        if (cpvmSrvcOffIdStr != null) {
            serviceOfferingVO = serviceOfferingDao.findByUuid(cpvmSrvcOffIdStr);
            if (serviceOfferingVO == null) {
                try {
                     logger.debug(String.format("Unable to find a service offering by the UUID for console proxy VM with the value [%s] set in the configuration [%s]. Trying to find by the ID.", cpvmSrvcOffIdStr, configKey));
                    serviceOfferingVO = serviceOfferingDao.findById(Long.parseLong(cpvmSrvcOffIdStr));
                } catch (NumberFormatException ex) {
                    logger.warn(String.format("Unable to find a service offering by the ID for console proxy VM with the value [%s] set in the configuration [%s]. The value is not a valid integer number. Error: [%s].", cpvmSrvcOffIdStr, configKey, ex.getMessage()), ex);
                }
            }
            if (serviceOfferingVO == null) {
                logger.warn(String.format("Unable to find a service offering by the UUID or ID for console proxy VM with the value [%s] set in the configuration [%s]", cpvmSrvcOffIdStr, configKey));
            }
        }

        if (serviceOfferingVO == null || !serviceOfferingVO.isSystemUse()) {
            int ramSize = NumbersUtil.parseInt(configurationDao.getValue("console.ram.size"), DEFAULT_PROXY_VM_RAMSIZE);
            int cpuFreq = NumbersUtil.parseInt(configurationDao.getValue("console.cpu.mhz"), DEFAULT_PROXY_VM_CPUMHZ);
            List<ServiceOfferingVO> offerings = serviceOfferingDao.createSystemServiceOfferings("System Offering For Console Proxy",
                    ServiceOffering.consoleProxyDefaultOffUniqueName, 1, ramSize, cpuFreq, 0, 0, false, null,
                    Storage.ProvisioningType.THIN, true, null, true, VirtualMachine.Type.ConsoleProxy, true);

            if (offerings == null || offerings.size() < 2) {
                String msg = "Data integrity problem : System Offering For Console Proxy has been removed?";
                logger.error(msg);
                throw new ConfigurationException(msg);
            }
        }

        loadScanner = new SystemVmLoadScanner<>(this);
        loadScanner.initScan(STARTUP_DELAY_IN_MILLISECONDS, capacityScanInterval);
        resourceManager.registerResourceStateAdapter(this.getClass().getSimpleName(), this);

        staticPublicIp = configurationDao.getValue("consoleproxy.static.publicIp");
        if (staticPublicIp != null) {
            staticPort = NumbersUtil.parseInt(configurationDao.getValue("consoleproxy.static.port"), 8443);
        }

        if (logger.isInfoEnabled()) {
            logger.info("Console Proxy Manager is configured.");
        }
        return true;
    }

    protected ConsoleProxyManagerImpl() {
    }

    @Override
    public boolean finalizeVirtualMachineProfile(VirtualMachineProfile profile, DeployDestination dest, ReservationContext context) {
        final Map<String, String> sshAccessDetails = networkMgr.getSystemVMAccessDetails(profile.getVirtualMachine());
        final Map<String, String> ipAddressDetails = new HashMap<>(sshAccessDetails);
        ipAddressDetails.remove("router.name");
        final Certificate certificate = caManager.issueCertificate(null, Arrays.asList(profile.getHostName(), profile.getInstanceName()),
                new ArrayList<>(ipAddressDetails.values()), CAManager.CertValidityPeriod.value(), null);
        ConsoleProxyVO vm = consoleProxyDao.findById(profile.getId());
        Map<String, String> details = vmInstanceDetailsDao.listDetailsKeyPairs(vm.getId());
        vm.setDetails(details);

        StringBuilder buf = profile.getBootArgsBuilder();
        buf.append(" template=domP type=consoleproxy");
        buf.append(" host=").append(StringUtils.toCSVList(indirectAgentLB.getManagementServerList(dest.getHost().getId(), dest.getDataCenter().getId(), null)));
        buf.append(" port=").append(managementPort);
        buf.append(" name=").append(profile.getVirtualMachine().getHostName());
        if (sslEnabled) {
            buf.append(" premium=true");
        }
        buf.append(" zone=").append(dest.getDataCenter().getId());
        buf.append(" pod=").append(dest.getPod().getId());
        buf.append(" guid=Proxy.").append(profile.getId());
        buf.append(" proxy_vm=").append(profile.getId());
        if (disableRpFilter) {
            buf.append(" disable_rp_filter=true");
        }

        String msPublicKey = configurationDao.getValue("ssh.publickey");
        buf.append(" authorized_key=").append(VirtualMachineGuru.getEncodedMsPublicKey(msPublicKey));

        boolean externalDhcp = false;
        String externalDhcpStr = configurationDao.getValue("direct.attach.network.externalIpAllocator.enabled");
        if (externalDhcpStr != null && externalDhcpStr.equalsIgnoreCase("true")) {
            externalDhcp = true;
        }

        if (Boolean.valueOf(configurationDao.getValue("system.vm.random.password"))) {
            buf.append(" vmpassword=").append(configurationDao.getValue("system.vm.password"));
        }

        if (StringUtils.isNotEmpty(NTPServerConfig.value())) {
            buf.append(" ntpserverlist=").append(NTPServerConfig.value().replaceAll("\\s+",""));
        }

        for (NicProfile nic : profile.getNics()) {
            int deviceId = nic.getDeviceId();
            if (nic.getIPv4Address() == null) {
                buf.append(" eth").append(deviceId).append("ip=").append("0.0.0.0");
                buf.append(" eth").append(deviceId).append("mask=").append("0.0.0.0");
            } else {
                buf.append(" eth").append(deviceId).append("ip=").append(nic.getIPv4Address());
                buf.append(" eth").append(deviceId).append("mask=").append(nic.getIPv4Netmask());
            }

            if (nic.isDefaultNic()) {
                buf.append(" gateway=").append(nic.getIPv4Gateway());
            }

            if (nic.getTrafficType() == TrafficType.Management) {
                String mgmt_cidr = configurationDao.getValue(Config.ManagementNetwork.key());
                if (NetUtils.isValidCidrList(mgmt_cidr)) {
                    logger.debug("Management server cidr list is " + mgmt_cidr);
                    buf.append(" mgmtcidr=").append(mgmt_cidr);
                } else {
                    logger.error("Invalid management cidr list: " + mgmt_cidr);
                }
                buf.append(" localgw=").append(dest.getPod().getGateway());
            }
        }

        if (externalDhcp) {
            buf.append(" bootproto=dhcp");
        }
        DataCenterVO dc = dataCenterDao.findById(profile.getVirtualMachine().getDataCenterId());
        buf.append(" internaldns1=").append(dc.getInternalDns1());
        if (dc.getInternalDns2() != null) {
            buf.append(" internaldns2=").append(dc.getInternalDns2());
        }
        buf.append(" dns1=").append(dc.getDns1());
        if (dc.getDns2() != null) {
            buf.append(" dns2=").append(dc.getDns2());
        }
        if (VirtualMachine.Type.ConsoleProxy == profile.getVirtualMachine().getType()) {
            buf.append(" vncport=").append(getVncPort());
        }
        buf.append(" keystore_password=").append(VirtualMachineGuru.getEncodedString(PasswordGenerator.generateRandomPassword(16)));
        String bootArgs = buf.toString();
        if (logger.isDebugEnabled()) {
            logger.debug("Boot Args for " + profile + ": " + bootArgs);
        }

        return true;
    }

    @Override
    public boolean finalizeDeployment(Commands cmds, VirtualMachineProfile profile, DeployDestination dest, ReservationContext context) {

        finalizeCommandsOnStart(cmds, profile);

        ConsoleProxyVO proxy = consoleProxyDao.findById(profile.getId());
        DataCenter dc = dest.getDataCenter();
        List<NicProfile> nics = profile.getNics();
        for (NicProfile nic : nics) {
            if ((nic.getTrafficType() == TrafficType.Public && dc.getNetworkType() == NetworkType.Advanced) ||
                (nic.getTrafficType() == TrafficType.Guest && (dc.getNetworkType() == NetworkType.Basic || dc.isSecurityGroupEnabled()))) {
                proxy.setPublicIpAddress(nic.getIPv4Address());
                proxy.setPublicNetmask(nic.getIPv4Netmask());
                proxy.setPublicMacAddress(nic.getMacAddress());
            } else if (nic.getTrafficType() == TrafficType.Management) {
                proxy.setPrivateIpAddress(nic.getIPv4Address());
                proxy.setPrivateMacAddress(nic.getMacAddress());
            }
        }
        consoleProxyDao.update(proxy.getId(), proxy);
        return true;
    }

    @Override
    public boolean finalizeCommandsOnStart(Commands cmds, VirtualMachineProfile profile) {

        NicProfile managementNic = null;
        NicProfile controlNic = null;
        for (NicProfile nic : profile.getNics()) {
            if (nic.getTrafficType() == TrafficType.Management) {
                managementNic = nic;
            } else if (nic.getTrafficType() == TrafficType.Control && nic.getIPv4Address() != null) {
                controlNic = nic;
            }
        }

        if (controlNic == null) {
            if (managementNic == null) {
                logger.error("Management network doesn't exist for the console proxy vm " + profile.getVirtualMachine());
                return false;
            }
            controlNic = managementNic;
        }

        if(profile.getHypervisorType() == HypervisorType.Hyperv) {
            controlNic = managementNic;
        }
        CheckSshCommand check = new CheckSshCommand(profile.getInstanceName(), controlNic.getIPv4Address(), 3922);
        cmds.addCommand("checkSsh", check);

        return true;
    }

    @Override
    public boolean finalizeStart(VirtualMachineProfile profile, long hostId, Commands cmds, ReservationContext context) {
        CheckSshAnswer answer = (CheckSshAnswer)cmds.getAnswer("checkSsh");
        if (answer == null || !answer.getResult()) {
            logger.warn(String.format("Unable to use SSH on the VM [%s] due to [%s].", profile.toString(), answer == null ? "null answer" : answer.getDetails()));
            return false;
        }

        try {
            rulesManager.getSystemIpAndEnableStaticNatForVm(profile.getVirtualMachine(), false);
            IPAddressVO ipaddr = ipAddressDao.findByAssociatedVmId(profile.getVirtualMachine().getId());
            if (ipaddr != null && ipaddr.getSystem()) {
                ConsoleProxyVO consoleVm = consoleProxyDao.findById(profile.getId());
                consoleVm.setPublicIpAddress(ipaddr.getAddress().addr());
                consoleProxyDao.update(consoleVm.getId(), consoleVm);
            }
        } catch (InsufficientAddressCapacityException ex) {
            logger.warn(String.format("Unable to retrieve system IP and enable static NAT for the VM [%s] due to [%s].", profile.toString(), ex.getMessage()), ex);
            return false;
        }

        return true;
    }

    @Override
    public void finalizeExpunge(VirtualMachine vm) {
        ConsoleProxyVO proxy = consoleProxyDao.findById(vm.getId());
        proxy.setPublicIpAddress(null);
        proxy.setPublicMacAddress(null);
        proxy.setPublicNetmask(null);
        proxy.setPrivateMacAddress(null);
        proxy.setPrivateIpAddress(null);
        consoleProxyDao.update(proxy.getId(), proxy);
    }

    @Override
    public void finalizeStop(VirtualMachineProfile profile, Answer answer) {
        IPAddressVO ip = ipAddressDao.findByAssociatedVmId(profile.getId());
        if (ip != null && ip.getSystem()) {
            CallContext ctx = CallContext.current();
            try {
                rulesManager.disableStaticNat(ip.getId(), ctx.getCallingAccount(), ctx.getCallingUserId(), true);
            } catch (ResourceUnavailableException ex) {
                logger.error(String.format("Unable to disable static NAT and release system IP [%s] as a part of VM [%s] stop due to [%s].", ip.toString(), profile.toString(), ex.getMessage()), ex);
            }
        }
    }

    @Override
    public String getScanHandlerName() {
        return "consoleproxy";
    }

    @Override
    public void onScanStart() {
        zoneHostInfoMap = getZoneHostInfo();

        zoneProxyCountMap = new HashMap<>();
        List<ConsoleProxyLoadInfo> listProxyCounts = consoleProxyDao.getDatacenterProxyLoadMatrix();
        for (ConsoleProxyLoadInfo info : listProxyCounts) {
            zoneProxyCountMap.put(info.getId(), info);
        }

        zoneVmCountMap = new HashMap<>();
        List<ConsoleProxyLoadInfo> listVmCounts = consoleProxyDao.getDatacenterSessionLoadMatrix();
        for (ConsoleProxyLoadInfo info : listVmCounts) {
            zoneVmCountMap.put(info.getId(), info);
        }
    }

    private void scanManagementState() {
        ConsoleProxyManagementState state = getManagementState();
        if (state != null) {
            switch (state) {
                case Auto:
                case Manual:
                case Suspending:
                    break;

                case ResetSuspending:
                    handleResetSuspending();
                    break;

                default:
                    assert (false);
            }
        }
    }

    private void handleResetSuspending() {
        List<ConsoleProxyVO> runningProxies = consoleProxyDao.getProxyListInStates(State.Running);
        for (ConsoleProxyVO proxy : runningProxies) {
            logger.info("Stop console proxy {} because of we are currently in ResetSuspending management mode", proxy);
            stopProxy(proxy.getId());
        }

        List<ConsoleProxyVO> proxiesInTransition = consoleProxyDao.getProxyListInStates(State.Running, State.Starting, State.Stopping);
        if (CollectionUtils.isEmpty(proxiesInTransition)) {
            logger.info("All previous console proxy VMs in transition mode ceased the mode, we will now resume to last management state");
            resumeLastManagementState();
        }
    }

    @Override
    public boolean canScan() {
        scanManagementState();

        if (!reserveStandbyCapacity()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Reserving standby capacity is disabled, skip capacity scan");
            }
            return false;
        }

        List<StoragePoolVO> upPools = primaryDataStoreDao.listByStatus(StoragePoolStatus.Up);
        if (CollectionUtils.isEmpty(upPools)) {
            logger.debug("Skip capacity scan as there is no Primary Storage in 'Up' state");
            return false;
        }

        return true;
    }

    @Override
    public Long[] getScannablePools() {
        List<Long> zoneIds = dataCenterDao.listEnabledNonEdgeZoneIds();
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Enabled non-edge zones available for scan: %s", StringUtils.join(zoneIds, ",")));
        }
        return zoneIds.toArray(Long[]::new);
    }

    @Override
    public boolean isPoolReadyForScan(Long dataCenterId) {
        DataCenterVO zone = dataCenterDao.findById(dataCenterId);
        if (!isZoneReady(zoneHostInfoMap, zone)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Zone {} is not ready to launch console proxy yet", zone);
            }
            return false;
        }

        List<ConsoleProxyVO> l = consoleProxyDao.getProxyListInStates(State.Starting, State.Stopping);
        if (l.size() > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("Zone {} has {} console proxy VM(s) in transition state", zone, l.size());
            }

            return false;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Zone {} is ready to launch console proxy", zone);
        }
        return true;
    }

    @Override
    public Pair<AfterScanAction, Object> scanPool(Long dataCenterId) {
        ConsoleProxyLoadInfo proxyInfo = zoneProxyCountMap.get(dataCenterId);
        if (proxyInfo == null) {
            return new Pair<>(AfterScanAction.nop, null);
        }

        ConsoleProxyLoadInfo vmInfo = zoneVmCountMap.get(dataCenterId);
        if (vmInfo == null) {
            vmInfo = new ConsoleProxyLoadInfo();
        }

        if (!checkCapacity(proxyInfo, vmInfo)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Expand console proxy standby capacity for zone " + proxyInfo.getName());
            }

            return new Pair<>(AfterScanAction.expand, null);
        }

        return new Pair<>(AfterScanAction.nop, null);
    }

    @Override
    public void expandPool(Long dataCenterId, Object actionArgs) {
        allocCapacity(dataCenterId);
    }

    @Override
    public void shrinkPool(Long pool, Object actionArgs) {
    }

    @Override
    public void onScanEnd() {
    }

    @Override
    public HostVO createHostVOForConnectedAgent(HostVO host, StartupCommand[] cmd) {
        if (!(cmd[0] instanceof StartupProxyCommand)) {
            return null;
        }

        host.setType(Type.ConsoleProxy);
        return host;
    }

    @Override
    public HostVO createHostVOForDirectConnectAgent(HostVO host, StartupCommand[] startup, ServerResource resource, Map<String, String> details, List<String> hostTags) {
        return null;
    }

    @Override
    public DeleteHostAnswer deleteHost(HostVO host, boolean isForced, boolean isForceDeleteStorage) throws UnableDeleteHostException {
        return null;
    }

    protected HostVO findConsoleProxyHostByName(String name) {
        QueryBuilder<HostVO> sc = QueryBuilder.create(HostVO.class);
        sc.and(sc.entity().getType(), Op.EQ, Type.ConsoleProxy);
        sc.and(sc.entity().getName(), Op.EQ, name);
        return sc.find();
    }

    @Override
    public void prepareStop(VirtualMachineProfile profile) {
    }

    @Override
    public void finalizeUnmanage(VirtualMachine vm) {
    }

    public List<ConsoleProxyAllocator> getConsoleProxyAllocators() {
        return consoleProxyAllocators;
    }

    @Inject
    public void setConsoleProxyAllocators(List<ConsoleProxyAllocator> consoleProxyAllocators) {
        this.consoleProxyAllocators = consoleProxyAllocators;
    }

    @Override
    public String getConfigComponentName() {
        return ConsoleProxyManager.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] { ConsoleProxySslEnabled, NoVncConsoleDefault, NoVncConsoleSourceIpCheckEnabled };
    }

    protected ConsoleProxyStatus parseJsonToConsoleProxyStatus(String json) throws JsonParseException {
        return jsonParser.fromJson(json, ConsoleProxyStatus.class);
    }

    protected void updateConsoleProxyStatus(String statusInfo, Long proxyVmId) {
        if (statusInfo == null) return;

        ConsoleProxyStatus status = null;
        try {
            status = parseJsonToConsoleProxyStatus(statusInfo);
        } catch (JsonParseException e) {
            logger.warn(String.format("Unable to parse load info [%s] from proxy {\"vmId\": %s} due to [%s].", statusInfo, proxyVmId, e.getMessage()), e);
        }

        int count = 0;
        byte[] details = null;

        if (status != null) {
            if (status.getConnections() != null) {
                count = status.getConnections().length;
            }
            if (status.getRemovedSessions() != null) {
                consoleAccessManager.removeSessions(status.getRemovedSessions());
            }
            details = statusInfo.getBytes(Charset.forName("US-ASCII"));
        } else {
            logger.debug(String.format("Unable to retrieve load info from proxy {\"vmId\": %s}. Invalid load info [%s].", proxyVmId, statusInfo));
        }

        consoleProxyDao.update(proxyVmId, count, DateUtil.currentGMTTime(), details);
    }
}
