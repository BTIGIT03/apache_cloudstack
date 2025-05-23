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

package com.cloud.kubernetes.version;

import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cloud.cpu.CPU;
import org.apache.cloudstack.api.command.admin.kubernetes.version.AddKubernetesSupportedVersionCmd;
import org.apache.cloudstack.api.command.admin.kubernetes.version.DeleteKubernetesSupportedVersionCmd;
import org.apache.cloudstack.api.command.admin.kubernetes.version.UpdateKubernetesSupportedVersionCmd;
import org.apache.cloudstack.api.command.user.iso.DeleteIsoCmd;
import org.apache.cloudstack.api.command.user.iso.RegisterIsoCmd;
import org.apache.cloudstack.api.command.user.kubernetes.version.ListKubernetesSupportedVersionsCmd;
import org.apache.cloudstack.api.response.KubernetesSupportedVersionResponse;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.commons.collections.CollectionUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.api.query.dao.TemplateJoinDao;
import com.cloud.api.query.vo.TemplateJoinVO;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.kubernetes.cluster.KubernetesClusterService;
import com.cloud.kubernetes.cluster.KubernetesClusterVO;
import com.cloud.kubernetes.cluster.dao.KubernetesClusterDao;
import com.cloud.kubernetes.version.dao.KubernetesSupportedVersionDao;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.template.TemplateApiService;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesVersionServiceTest {

    @InjectMocks
    private KubernetesVersionService kubernetesVersionService = new KubernetesVersionManagerImpl();

    @Mock
    private KubernetesSupportedVersionDao kubernetesSupportedVersionDao;
    @Mock
    private KubernetesClusterDao kubernetesClusterDao;
    @Mock
    private AccountManager accountManager;
    @Mock
    private VMTemplateDao templateDao;
    @Mock
    private TemplateJoinDao templateJoinDao;
    @Mock
    private DataCenterDao dataCenterDao;
    @Mock
    private TemplateApiService templateService;

    AutoCloseable closeable;


    private void overrideDefaultConfigValue(final ConfigKey configKey, final String name, final Object o) throws IllegalAccessException, NoSuchFieldException {
        Field f = ConfigKey.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(configKey, o);
    }

    @Before
    public void setUp() throws Exception {
        closeable = MockitoAnnotations.openMocks(this);

        overrideDefaultConfigValue(KubernetesClusterService.KubernetesServiceEnabled, "_defaultValue", "true");

        final SearchBuilder<KubernetesSupportedVersionVO> versionSearchBuilder = Mockito.mock(SearchBuilder.class);
        final SearchCriteria<KubernetesSupportedVersionVO> versionSearchCriteria = Mockito.mock(SearchCriteria.class);
        when(kubernetesSupportedVersionDao.createSearchBuilder()).thenReturn(versionSearchBuilder);
        final KubernetesSupportedVersionVO kubernetesSupportedVersionVO = Mockito.mock(KubernetesSupportedVersionVO.class);
        when(versionSearchBuilder.entity()).thenReturn(kubernetesSupportedVersionVO);
        when(versionSearchBuilder.entity()).thenReturn(kubernetesSupportedVersionVO);
        when(versionSearchBuilder.create()).thenReturn(versionSearchCriteria);
        when(kubernetesSupportedVersionDao.createSearchCriteria()).thenReturn(versionSearchCriteria);

        DataCenterVO zone = Mockito.mock(DataCenterVO.class);
        when(dataCenterDao.findById(Mockito.anyLong())).thenReturn(zone);

        TemplateJoinVO templateJoinVO = Mockito.mock(TemplateJoinVO.class);
        when(templateJoinVO.getState()).thenReturn(ObjectInDataStoreStateMachine.State.Ready);
        when(templateJoinVO.getArch()).thenReturn(CPU.CPUArch.getDefault());
        when(templateJoinDao.findById(Mockito.anyLong())).thenReturn(templateJoinVO);

        KubernetesSupportedVersionVO versionVO = Mockito.mock(KubernetesSupportedVersionVO.class);
        when(versionVO.getSemanticVersion()).thenReturn(KubernetesVersionService.MIN_KUBERNETES_VERSION);
        when(kubernetesSupportedVersionDao.persist(Mockito.any(KubernetesSupportedVersionVO.class))).thenReturn(versionVO);
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void listKubernetesSupportedVersionsTest() {
        ListKubernetesSupportedVersionsCmd cmd = Mockito.mock(ListKubernetesSupportedVersionsCmd.class);
        List<KubernetesSupportedVersionVO> versionVOs = new ArrayList<>();
        KubernetesSupportedVersionVO versionVO = Mockito.mock(KubernetesSupportedVersionVO.class);
        when(versionVO.getSemanticVersion()).thenReturn(KubernetesVersionService.MIN_KUBERNETES_VERSION);
        versionVOs.add(versionVO);
        when(kubernetesSupportedVersionDao.findById(Mockito.anyLong())).thenReturn(versionVO);
        when(kubernetesSupportedVersionDao.searchAndCount(Mockito.any(SearchCriteria.class),
                Mockito.any(Filter.class))).thenReturn(new Pair<>(versionVOs, versionVOs.size()));
        ListResponse<KubernetesSupportedVersionResponse> versionsResponse =
                kubernetesVersionService.listKubernetesSupportedVersions(cmd);
        Assert.assertEquals(versionVOs.size(), versionsResponse.getCount().intValue());
        Assert.assertTrue(CollectionUtils.isNotEmpty(versionsResponse.getResponses()));
        Assert.assertEquals(versionVOs.size(), versionsResponse.getResponses().size());
    }

    @Test(expected = InvalidParameterValueException.class)
    public void addKubernetesSupportedVersionLowerUnsupportedTest() {
        AddKubernetesSupportedVersionCmd cmd = Mockito.mock(AddKubernetesSupportedVersionCmd.class);
        when(cmd.getMinimumCpu()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_CPU);
        when(cmd.getMinimumRamSize()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_RAM_SIZE);
        AccountVO account = new AccountVO("admin", 1L, "", Account.Type.ADMIN, "uuid");
        UserVO user = new UserVO(1, "adminuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);
        when(cmd.getSemanticVersion()).thenReturn("1.1.1");
        kubernetesVersionService.addKubernetesSupportedVersion(cmd);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void addKubernetesSupportedVersionInvalidCpuTest() {
        AddKubernetesSupportedVersionCmd cmd = Mockito.mock(AddKubernetesSupportedVersionCmd.class);
        when(cmd.getMinimumCpu()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_CPU-1);
        when(cmd.getMinimumRamSize()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_RAM_SIZE);
        AccountVO account = new AccountVO("admin", 1L, "", Account.Type.ADMIN, "uuid");
        UserVO user = new UserVO(1, "adminuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        when(cmd.getSemanticVersion()).thenReturn(KubernetesVersionService.MIN_KUBERNETES_VERSION);
        CallContext.register(user, account);
        kubernetesVersionService.addKubernetesSupportedVersion(cmd);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void addKubernetesSupportedVersionInvalidRamSizeTest() {
        AddKubernetesSupportedVersionCmd cmd = Mockito.mock(AddKubernetesSupportedVersionCmd.class);
        when(cmd.getMinimumCpu()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_CPU);
        when(cmd.getMinimumRamSize()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_RAM_SIZE-10);
        AccountVO account = new AccountVO("admin", 1L, "", Account.Type.ADMIN, "uuid");
        UserVO user = new UserVO(1, "adminuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        when(cmd.getSemanticVersion()).thenReturn(KubernetesVersionService.MIN_KUBERNETES_VERSION);
        CallContext.register(user, account);
        kubernetesVersionService.addKubernetesSupportedVersion(cmd);
    }

    @Test(expected = InvalidParameterValueException.class)
    public void addKubernetesSupportedVersionEmptyUrlTest() {
        AddKubernetesSupportedVersionCmd cmd = Mockito.mock(AddKubernetesSupportedVersionCmd.class);
        when(cmd.getMinimumCpu()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_CPU);
        when(cmd.getMinimumRamSize()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_RAM_SIZE);
        AccountVO account = new AccountVO("admin", 1L, "", Account.Type.ADMIN, "uuid");
        UserVO user = new UserVO(1, "adminuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        when(cmd.getSemanticVersion()).thenReturn(KubernetesVersionService.MIN_KUBERNETES_VERSION);
        CallContext.register(user, account);
        when(cmd.getUrl()).thenReturn("");
        kubernetesVersionService.addKubernetesSupportedVersion(cmd);
    }

    @Test
    public void addKubernetesSupportedVersionIsoUrlTest() throws ResourceAllocationException, NoSuchFieldException {
        AddKubernetesSupportedVersionCmd cmd = Mockito.mock(AddKubernetesSupportedVersionCmd.class);
        AccountVO account = new AccountVO("admin", 1L, "", Account.Type.ADMIN, "uuid");
        UserVO user = new UserVO(1, "adminuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);
        when(cmd.getSemanticVersion()).thenReturn(KubernetesVersionService.MIN_KUBERNETES_VERSION);
        when(cmd.getUrl()).thenReturn("https://download.cloudstack.com");
        when(cmd.getChecksum()).thenReturn(null);
        when(cmd.getMinimumCpu()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_CPU);
        when(cmd.getMinimumRamSize()).thenReturn(KubernetesClusterService.MIN_KUBERNETES_CLUSTER_NODE_RAM_SIZE);
        when(cmd.getArch()).thenReturn(CPU.CPUArch.amd64);
        Account systemAccount =  new AccountVO("system", 1L, "", Account.Type.ADMIN, "uuid");
        when(accountManager.getSystemAccount()).thenReturn(systemAccount);
        CallContext callContext = Mockito.mock(CallContext.class);
        try (MockedStatic<ComponentContext> mockedComponentContext = Mockito.mockStatic(ComponentContext.class);
            MockedStatic<CallContext> mockedCallContext = Mockito.mockStatic(CallContext.class)) {
            mockedComponentContext.when(() -> ComponentContext.inject(Mockito.any(RegisterIsoCmd.class))).thenReturn(
                    new RegisterIsoCmd());
            mockedCallContext.when(CallContext::current).thenReturn(callContext);

            when(templateService.registerIso(Mockito.any(RegisterIsoCmd.class))).thenReturn(
                    Mockito.mock(VirtualMachineTemplate.class));
            VMTemplateVO templateVO = Mockito.mock(VMTemplateVO.class);
            when(templateVO.getId()).thenReturn(1L);
            when(templateDao.findById(Mockito.anyLong())).thenReturn(templateVO);
            KubernetesSupportedVersionResponse response = kubernetesVersionService.addKubernetesSupportedVersion(cmd);
            Assert.assertNotNull(response);
            Mockito.verify(kubernetesSupportedVersionDao, Mockito.times(1)).persist(Mockito.any(KubernetesSupportedVersionVO.class));
        }
    }

    @Test(expected = CloudRuntimeException.class)
    public void deleteKubernetesSupportedVersionExistingClustersTest() {
        DeleteKubernetesSupportedVersionCmd cmd = Mockito.mock(DeleteKubernetesSupportedVersionCmd.class);
        AccountVO account = new AccountVO("admin", 1L, "", Account.Type.ADMIN, "uuid");
        UserVO user = new UserVO(1, "adminuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);
        when(kubernetesSupportedVersionDao.findById(Mockito.anyLong())).thenReturn(Mockito.mock(KubernetesSupportedVersionVO.class));
        List<KubernetesClusterVO> clusters = new ArrayList<>();
        clusters.add(Mockito.mock(KubernetesClusterVO.class));
        when(kubernetesClusterDao.listAllByKubernetesVersion(Mockito.anyLong())).thenReturn(clusters);
        kubernetesVersionService.deleteKubernetesSupportedVersion(cmd);
    }

    @Test
    public void deleteKubernetesSupportedVersionTest() {
        DeleteKubernetesSupportedVersionCmd cmd = Mockito.mock(DeleteKubernetesSupportedVersionCmd.class);
        AccountVO account = new AccountVO("admin", 1L, "", Account.Type.ADMIN, "uuid");
        UserVO user = new UserVO(1, "adminuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);
        when(kubernetesSupportedVersionDao.findById(Mockito.anyLong())).thenReturn(Mockito.mock(KubernetesSupportedVersionVO.class));
        List<KubernetesClusterVO> clusters = new ArrayList<>();
        when(kubernetesClusterDao.listAllByKubernetesVersion(Mockito.anyLong())).thenReturn(clusters);
        try (MockedStatic<ComponentContext> mockedComponentContext = Mockito.mockStatic(ComponentContext.class)) {
            mockedComponentContext.when(() -> ComponentContext.inject(Mockito.any(DeleteIsoCmd.class))).thenReturn(new DeleteIsoCmd());
            kubernetesVersionService.deleteKubernetesSupportedVersion(cmd);
            Mockito.verify(kubernetesSupportedVersionDao).remove(Mockito.anyLong());
        }
    }

    @Test
    public void updateKubernetesSupportedVersionTest() {
        UpdateKubernetesSupportedVersionCmd cmd = Mockito.mock(UpdateKubernetesSupportedVersionCmd.class);
        when(cmd.getState()).thenReturn(KubernetesSupportedVersion.State.Disabled.toString());
        AccountVO account = new AccountVO("admin", 1L, "", Account.Type.ADMIN, "uuid");
        UserVO user = new UserVO(1, "adminuser", "password", "firstname", "lastName", "email", "timezone", UUID.randomUUID().toString(), User.Source.UNKNOWN);
        CallContext.register(user, account);
        when(kubernetesSupportedVersionDao.findById(Mockito.anyLong())).thenReturn(Mockito.mock(KubernetesSupportedVersionVO.class));
        KubernetesSupportedVersionVO version = Mockito.mock(KubernetesSupportedVersionVO.class);
        when(version.getState()).thenReturn(KubernetesSupportedVersion.State.Disabled);
        when(version.getSemanticVersion()).thenReturn(KubernetesVersionService.MIN_KUBERNETES_VERSION);
        when(kubernetesSupportedVersionDao.findById(Mockito.anyLong())).thenReturn(version);
        KubernetesSupportedVersionResponse response = kubernetesVersionService.updateKubernetesSupportedVersion(cmd);
        Assert.assertNotNull(response);
        Assert.assertEquals(KubernetesSupportedVersion.State.Disabled.toString(), response.getState());
        Assert.assertEquals(KubernetesVersionService.MIN_KUBERNETES_VERSION, response.getSemanticVersion());
    }
}
