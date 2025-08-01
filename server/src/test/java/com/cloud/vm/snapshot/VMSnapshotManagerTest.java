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
package com.cloud.vm.snapshot;

import com.cloud.agent.AgentManager;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ManagementServerException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.exception.VirtualMachineMigrationException;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.hypervisor.HypervisorGuruManager;
import com.cloud.hypervisor.dao.HypervisorCapabilitiesDao;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.service.dao.ServiceOfferingDetailsDao;
import com.cloud.storage.GuestOSVO;
import com.cloud.storage.Snapshot;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.GuestOSDao;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.uservm.UserVm;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.fsm.NoTransitionException;
import com.cloud.vm.VMInstanceDetailVO;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.VirtualMachine.State;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDetailsDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.cloud.vm.snapshot.dao.VMSnapshotDao;
import com.cloud.vm.snapshot.dao.VMSnapshotDetailsDao;
import org.apache.cloudstack.acl.ControlledEntity;
import org.apache.cloudstack.acl.SecurityChecker.AccessType;
import org.apache.cloudstack.api.ResourceDetail;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VMSnapshotManagerTest {
    @Spy
    VMSnapshotManagerImpl _vmSnapshotMgr = new VMSnapshotManagerImpl();
    @Mock
    Account admin;
    @Mock
    VMSnapshotDao _vmSnapshotDao;
    @Mock
    VolumeDao _volumeDao;
    @Mock
    AccountDao _accountDao;
    @Mock
    VMInstanceDao _vmInstanceDao;
    @Mock
    UserVmDao _userVMDao;
    @Mock
    HostDao _hostDao;
    @Mock
    UserDao _userDao;
    @Mock
    AgentManager _agentMgr;
    @Mock
    HypervisorGuruManager _hvGuruMgr;
    @Mock
    AccountManager _accountMgr;
    @Mock
    GuestOSDao _guestOSDao;
    @Mock
    PrimaryDataStoreDao _storagePoolDao;
    @Mock
    SnapshotDao _snapshotDao;
    @Mock
    VirtualMachineManager _itMgr;
    @Mock
    ConfigurationDao _configDao;
    @Mock
    HypervisorCapabilitiesDao _hypervisorCapabilitiesDao;
    @Mock
    ServiceOfferingDetailsDao _serviceOfferingDetailsDao;
    @Mock
    ServiceOfferingDao _serviceOfferingDao;
    @Mock
    VMInstanceDetailsDao _vmInstanceDetailsDao;
    @Mock
    VMSnapshotDetailsDao _vmSnapshotDetailsDao;
    @Mock
    UserVmManager _userVmManager;

    private static final long TEST_VM_ID = 3L;
    private static final long SERVICE_OFFERING_ID = 1L;
    private static final long SERVICE_OFFERING_DIFFERENT_ID = 2L;
    private static VMSnapshot.Type vmSnapshotType;
    private static List<VMInstanceDetailVO> userVmDetails;
    private static List<VMSnapshotDetailsVO> vmSnapshotDetails;

    private static final long VM_SNAPSHOT_ID = 1L;
    private static final String VM_SNAPSHOT_NAME = "Vm-Snapshot-Name";
    private static final String VM_SNAPSHOT_DESCRIPTION = "Vm-Snapshot-Desc";
    private static final String VM_SNAPSHOT_DISPLAY_NAME = "Vm-Snapshot-Display-Name";
    @Mock
    UserVmVO vmMock;
    @Mock
    VolumeVO volumeMock;
    @Mock
    VMSnapshotVO vmSnapshotVO;
    @Mock
    ServiceOfferingVO serviceOffering;
    @Mock
    VMInstanceDetailVO userVmDetailCpuNumber;
    @Mock
    VMInstanceDetailVO userVmDetailMemory;
    @Mock
    VMSnapshotDetailsVO vmSnapshotDetailCpuNumber;
    @Mock
    VMSnapshotDetailsVO vmSnapshotDetailMemory;
    @Mock
    UserVm userVm;

    @Captor
    ArgumentCaptor<List<VMSnapshotDetailsVO>> listVmSnapshotDetailsCaptor;
    @Captor
    ArgumentCaptor<Map<String,String>> mapDetailsCaptor;
    @Captor
    ArgumentCaptor<List<VMInstanceDetailVO>> listUserVmDetailsCaptor;

    private AutoCloseable closeable;

    @Before
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        doReturn(admin).when(_vmSnapshotMgr).getCaller();
        _vmSnapshotMgr._accountDao = _accountDao;
        _vmSnapshotMgr._userVMDao = _userVMDao;
        _vmSnapshotMgr._vmSnapshotDao = _vmSnapshotDao;
        _vmSnapshotMgr._volumeDao = _volumeDao;
        _vmSnapshotMgr._storagePoolDao = _storagePoolDao;
        _vmSnapshotMgr._accountMgr = _accountMgr;
        _vmSnapshotMgr._snapshotDao = _snapshotDao;
        _vmSnapshotMgr._guestOSDao = _guestOSDao;
        _vmSnapshotMgr._hypervisorCapabilitiesDao = _hypervisorCapabilitiesDao;
        _vmSnapshotMgr._serviceOfferingDetailsDao = _serviceOfferingDetailsDao;

        doNothing().when(_accountMgr).checkAccess(any(Account.class), any(AccessType.class), any(Boolean.class), any(ControlledEntity.class));

        _vmSnapshotMgr._serviceOfferingDao = _serviceOfferingDao;
        _vmSnapshotMgr._vmInstanceDetailsDao = _vmInstanceDetailsDao;
        _vmSnapshotMgr._vmSnapshotDetailsDao = _vmSnapshotDetailsDao;
        _vmSnapshotMgr._userVmManager = _userVmManager;

        when(_userVMDao.findById(anyLong())).thenReturn(vmMock);
        when(_vmSnapshotDao.findByName(anyLong(), anyString())).thenReturn(null);
        when(_vmSnapshotDao.findByVm(anyLong())).thenReturn(new ArrayList<VMSnapshotVO>());
        when(_hypervisorCapabilitiesDao.isVmSnapshotEnabled(Hypervisor.HypervisorType.XenServer, "default")).thenReturn(true);
        when(_serviceOfferingDetailsDao.findDetail(anyLong(), anyString())).thenReturn(null);

        List<VolumeVO> mockVolumeList = new ArrayList<VolumeVO>();
        mockVolumeList.add(volumeMock);
        when(volumeMock.getInstanceId()).thenReturn(TEST_VM_ID);
        when(_volumeDao.findByInstance(anyLong())).thenReturn(mockVolumeList);
        when(_volumeDao.findReadyRootVolumesByInstance(anyLong())).thenReturn(mockVolumeList);
        when(_storagePoolDao.findById(anyLong())).thenReturn(mock(StoragePoolVO.class));

        when(vmMock.getId()).thenReturn(TEST_VM_ID);
        when(vmMock.getServiceOfferingId()).thenReturn(SERVICE_OFFERING_ID);
        when(vmMock.getAccountId()).thenReturn(1L);
        when(vmMock.getDomainId()).thenReturn(1L);
        when(vmMock.getInstanceName()).thenReturn("i-3-VM-TEST");
        when(vmMock.getState()).thenReturn(State.Running);
        when(vmMock.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.XenServer);
        when(_guestOSDao.findById(anyLong())).thenReturn(mock(GuestOSVO.class));

        when(vmSnapshotVO.getId()).thenReturn(VM_SNAPSHOT_ID);
        when(serviceOffering.isDynamic()).thenReturn(false);
        when(_serviceOfferingDao.findById(SERVICE_OFFERING_ID)).thenReturn(serviceOffering);

        for (ResourceDetail detail : Arrays.asList(userVmDetailCpuNumber, vmSnapshotDetailCpuNumber)) {
            when(detail.getName()).thenReturn("cpuNumber");
            when(detail.getValue()).thenReturn("2");
            when(detail.isDisplay()).thenReturn(true);
        }

        for (ResourceDetail detail : Arrays.asList(userVmDetailMemory, vmSnapshotDetailMemory)) {
            when(detail.getName()).thenReturn("memory");
            when(detail.getValue()).thenReturn("2048");
            when(detail.isDisplay()).thenReturn(true);
        }

        userVmDetails = Arrays.asList(userVmDetailCpuNumber, userVmDetailMemory);
        vmSnapshotDetails = Arrays.asList(vmSnapshotDetailCpuNumber, vmSnapshotDetailMemory);
        when(_vmInstanceDetailsDao.listDetails(TEST_VM_ID)).thenReturn(userVmDetails);
        when(_vmSnapshotDetailsDao.listDetails(VM_SNAPSHOT_ID)).thenReturn(vmSnapshotDetails);

        when(userVm.getId()).thenReturn(TEST_VM_ID);
        when(userVm.getServiceOfferingId()).thenReturn(SERVICE_OFFERING_ID);

        when(vmSnapshotVO.getServiceOfferingId()).thenReturn(SERVICE_OFFERING_ID);
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    // vmId null case
    @Test(expected = InvalidParameterValueException.class)
    public void testAllocVMSnapshotF1() throws ResourceAllocationException {
        when(_userVMDao.findById(TEST_VM_ID)).thenReturn(null);
        _vmSnapshotMgr.allocVMSnapshot(TEST_VM_ID, "", "", true);
    }

    // hypervisorCapabilities not expected case
    @Test(expected = InvalidParameterValueException.class)
    public void testAllocVMSnapshotF6() throws ResourceAllocationException {
        when(vmMock.getHypervisorType()).thenReturn(Hypervisor.HypervisorType.Ovm);
        when(_hypervisorCapabilitiesDao.isVmSnapshotEnabled(Hypervisor.HypervisorType.Ovm, "default")).thenReturn(false);
        _vmSnapshotMgr.allocVMSnapshot(TEST_VM_ID, "", "", true);
    }

    // vm state not in [running, stopped] case
    @Test(expected = InvalidParameterValueException.class)
    public void testAllocVMSnapshotF2() throws ResourceAllocationException {
        when(vmMock.getState()).thenReturn(State.Starting);
        _vmSnapshotMgr.allocVMSnapshot(TEST_VM_ID, "", "", true);
    }

    // VM in stopped state & snapshotmemory case
    @Test(expected = InvalidParameterValueException.class)
    public void testCreateVMSnapshotF3() throws AgentUnavailableException, OperationTimedoutException, ResourceAllocationException {
        when(vmMock.getState()).thenReturn(State.Stopped);
        _vmSnapshotMgr.allocVMSnapshot(TEST_VM_ID, "", "", true);
    }

    // max snapshot limit case
    @SuppressWarnings("unchecked")
    @Test(expected = CloudRuntimeException.class)
    public void testAllocVMSnapshotF4() throws ResourceAllocationException {
        List<VMSnapshotVO> mockList = mock(List.class);
        when(mockList.size()).thenReturn(10);
        when(_vmSnapshotDao.findByVm(TEST_VM_ID)).thenReturn(mockList);
        _vmSnapshotMgr.allocVMSnapshot(TEST_VM_ID, "", "", true);
    }

    // active volume snapshots case
    @SuppressWarnings("unchecked")
    @Test(expected = CloudRuntimeException.class)
    public void testAllocVMSnapshotF5() throws ResourceAllocationException {
        List<SnapshotVO> mockList = mock(List.class);
        when(mockList.size()).thenReturn(1);
        when(_snapshotDao.listByInstanceId(TEST_VM_ID, Snapshot.State.Creating, Snapshot.State.CreatedOnPrimary, Snapshot.State.BackingUp)).thenReturn(mockList);
        _vmSnapshotMgr.allocVMSnapshot(TEST_VM_ID, "", "", true);
    }

    // successful creation case
    @Test
    public void testCreateVMSnapshot() throws AgentUnavailableException, OperationTimedoutException, ResourceAllocationException, NoTransitionException {
        when(vmMock.getState()).thenReturn(State.Running);
        _vmSnapshotMgr.allocVMSnapshot(TEST_VM_ID, "", "", true);
    }

    @Test
    public void testCreateAndPersistVMSnapshot() {
        when(_vmSnapshotDao.persist(any(VMSnapshotVO.class))).thenReturn(vmSnapshotVO);
        _vmSnapshotMgr.createAndPersistVMSnapshot(vmMock, VM_SNAPSHOT_DESCRIPTION,
                VM_SNAPSHOT_NAME, VM_SNAPSHOT_DISPLAY_NAME, vmSnapshotType);

        verify(_vmSnapshotMgr).addSupportForCustomServiceOffering(TEST_VM_ID, SERVICE_OFFERING_ID, VM_SNAPSHOT_ID);
    }

    @Test(expected=CloudRuntimeException.class)
    public void testCreateAndPersistVMSnapshotNullVMSnapshot() {
        when(_vmSnapshotDao.persist(any(VMSnapshotVO.class))).thenReturn(null);
        _vmSnapshotMgr.createAndPersistVMSnapshot(vmMock, VM_SNAPSHOT_DESCRIPTION,
                VM_SNAPSHOT_NAME, VM_SNAPSHOT_DISPLAY_NAME, vmSnapshotType);
    }

    @Test
    public void testAddSupportForCustomServiceOfferingNotDynamicServiceOffering() {
        _vmSnapshotMgr.addSupportForCustomServiceOffering(TEST_VM_ID, SERVICE_OFFERING_ID, VM_SNAPSHOT_ID);
        verify(_vmInstanceDetailsDao, never()).listDetails(TEST_VM_ID);
    }

    @Test
    public void testAddSupportForCustomServiceOfferingDynamicServiceOffering() {
        when(serviceOffering.isDynamic()).thenReturn(true);
        _vmSnapshotMgr.addSupportForCustomServiceOffering(TEST_VM_ID, SERVICE_OFFERING_ID, VM_SNAPSHOT_ID);

        verify(_vmInstanceDetailsDao).listDetails(TEST_VM_ID);
        verify(_vmSnapshotDetailsDao).saveDetails(listVmSnapshotDetailsCaptor.capture());
    }

    @Test
    public void testUpdateUserVmServiceOfferingSameServiceOffering() {
        _vmSnapshotMgr.updateUserVmServiceOffering(userVm, vmSnapshotVO);
        verify(_vmSnapshotMgr, never()).changeUserVmServiceOffering(userVm, vmSnapshotVO);
    }

    @Test
    public void testUpdateUserVmServiceOfferingDifferentServiceOffering() throws ConcurrentOperationException, ResourceUnavailableException, ManagementServerException, VirtualMachineMigrationException {
        when(userVm.getServiceOfferingId()).thenReturn(SERVICE_OFFERING_DIFFERENT_ID);
        when(_userVmManager.upgradeVirtualMachine(ArgumentMatchers.eq(TEST_VM_ID), ArgumentMatchers.eq(SERVICE_OFFERING_ID), mapDetailsCaptor.capture())).thenReturn(true);
        _vmSnapshotMgr.updateUserVmServiceOffering(userVm, vmSnapshotVO);

        verify(_vmSnapshotMgr).changeUserVmServiceOffering(userVm, vmSnapshotVO);
        verify(_vmSnapshotMgr).getVmMapDetails(userVm);
        verify(_vmSnapshotMgr).upgradeUserVmServiceOffering(ArgumentMatchers.eq(userVm), ArgumentMatchers.eq(SERVICE_OFFERING_ID), mapDetailsCaptor.capture());
    }

    @Test
    public void testGetVmMapDetails() {
        Map<String, String> result = _vmSnapshotMgr.getVmMapDetails(userVm);
        assert(result.containsKey(userVmDetailCpuNumber.getName()));
        assert(result.containsKey(userVmDetailMemory.getName()));
        assertEquals(userVmDetails.size(), result.size());
        assertEquals(userVmDetailCpuNumber.getValue(), result.get(userVmDetailCpuNumber.getName()));
        assertEquals(userVmDetailMemory.getValue(), result.get(userVmDetailMemory.getName()));
    }

    @Test
    public void testChangeUserVmServiceOffering() throws ConcurrentOperationException, ResourceUnavailableException, ManagementServerException, VirtualMachineMigrationException {
        when(_userVmManager.upgradeVirtualMachine(ArgumentMatchers.eq(TEST_VM_ID), ArgumentMatchers.eq(SERVICE_OFFERING_ID), mapDetailsCaptor.capture())).thenReturn(true);
        _vmSnapshotMgr.changeUserVmServiceOffering(userVm, vmSnapshotVO);
        verify(_vmSnapshotMgr).getVmMapDetails(userVm);
        verify(_vmSnapshotMgr).upgradeUserVmServiceOffering(ArgumentMatchers.eq(userVm), ArgumentMatchers.eq(SERVICE_OFFERING_ID), mapDetailsCaptor.capture());
    }

    @Test(expected=CloudRuntimeException.class)
    public void testChangeUserVmServiceOfferingFailOnUpgradeVMServiceOffering() throws ConcurrentOperationException, ResourceUnavailableException, ManagementServerException, VirtualMachineMigrationException {
        when(_userVmManager.upgradeVirtualMachine(ArgumentMatchers.eq(TEST_VM_ID), ArgumentMatchers.eq(SERVICE_OFFERING_ID), mapDetailsCaptor.capture())).thenReturn(false);
        _vmSnapshotMgr.changeUserVmServiceOffering(userVm, vmSnapshotVO);
        verify(_vmSnapshotMgr).getVmMapDetails(userVm);
        verify(_vmSnapshotMgr).upgradeUserVmServiceOffering(ArgumentMatchers.eq(userVm), ArgumentMatchers.eq(SERVICE_OFFERING_ID), mapDetailsCaptor.capture());
    }

    @Test
    public void testUpgradeUserVmServiceOffering() throws ConcurrentOperationException, ResourceUnavailableException, ManagementServerException, VirtualMachineMigrationException {
        Map<String, String> details = new HashMap<String, String>() {{
                put(userVmDetailCpuNumber.getName(), userVmDetailCpuNumber.getValue());
                put(userVmDetailMemory.getName(), userVmDetailMemory.getValue());
        }};
        when(_userVmManager.upgradeVirtualMachine(TEST_VM_ID, SERVICE_OFFERING_ID, details)).thenReturn(true);
        _vmSnapshotMgr.upgradeUserVmServiceOffering(userVm, SERVICE_OFFERING_ID, details);

        verify(_userVmManager).upgradeVirtualMachine(TEST_VM_ID, SERVICE_OFFERING_ID, details);
    }

    @Test
    public void testRevertUserVmDetailsFromVmSnapshotNotDynamicServiceOffering() {
        _vmSnapshotMgr.revertUserVmDetailsFromVmSnapshot(vmMock, vmSnapshotVO);
        verify(_vmSnapshotDetailsDao, never()).listDetails(anyLong());
    }

    @Test
    public void testRevertUserVmDetailsFromVmSnapshotDynamicServiceOffering() {
        when(serviceOffering.isDynamic()).thenReturn(true);
        _vmSnapshotMgr.revertUserVmDetailsFromVmSnapshot(vmMock, vmSnapshotVO);
        verify(_vmSnapshotDetailsDao).listDetails(VM_SNAPSHOT_ID);
        verify(_vmInstanceDetailsDao).saveDetails(listUserVmDetailsCaptor.capture());
    }

}
