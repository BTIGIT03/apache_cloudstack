# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
""" Tests for Kubernetes cluster """

#Import Local Modules
from marvin.cloudstackTestCase import cloudstackTestCase
import unittest
from marvin.cloudstackAPI import (listInfrastructure,
                                  listTemplates,
                                  listKubernetesSupportedVersions,
                                  addKubernetesSupportedVersion,
                                  deleteKubernetesSupportedVersion,
                                  listKubernetesClusters,
                                  createKubernetesCluster,
                                  stopKubernetesCluster,
                                  startKubernetesCluster,
                                  deleteKubernetesCluster,
                                  upgradeKubernetesCluster,
                                  scaleKubernetesCluster,
                                  getKubernetesClusterConfig,
                                  destroyVirtualMachine,
                                  deleteNetwork,
                                  addVirtualMachinesToKubernetesCluster,
                                  removeVirtualMachinesFromKubernetesCluster,
                                  addNodesToKubernetesCluster,
                                  removeNodesFromKubernetesCluster)
from marvin.cloudstackException import CloudstackAPIException
from marvin.codes import PASS, FAILED
from marvin.lib.base import (Template,
                             NetworkOffering,
                             Network,
                             ServiceOffering,
                             Account,
                             StoragePool,
                             Configurations,
                             VpcOffering,
                             VPC,
                             NetworkACLList,
                             NetworkACL,
                             VirtualMachine,
                             PublicIPAddress,
                             FireWallRule,
                             NATRule)
from marvin.lib.utils import (cleanup_resources,
                              validateList,
                              random_gen)
from marvin.lib.common import (get_zone,
                               get_domain,
                               get_template,
                               get_test_template)
from marvin.sshClient import SshClient
from nose.plugins.attrib import attr
from marvin.lib.decoratorGenerators import skipTestIf

from kubernetes import client, config
import time, io, yaml, random

_multiprocess_shared_ = True

k8s_cluster = None
k8s_cluster_node_offerings = None
VPC_DATA = {
    "cidr": "10.1.0.0/22",
    "tier1_gateway": "10.1.1.1",
    "tier_netmask": "255.255.255.0"
}
RAND_SUFFIX = random_gen()
NODES_TEMPLATE = {
    "kvm": {
        "name": "cks-u2204-kvm-" + RAND_SUFFIX,
        "displaytext": "cks-u2204-kvm-" + RAND_SUFFIX,
        "format": "qcow2",
        "hypervisor": "kvm",
        "ostype": "Ubuntu 22.04 LTS",
        "url": "https://download.cloudstack.org/testing/custom_templates/ubuntu/22.04/cks-ubuntu-2204-kvm.qcow2.bz2",
        "requireshvm": "True",
        "ispublic": "True",
        "isextractable": "True",
        "forcks": "True"
    },
    "xenserver": {
        "name": "cks-u2204-hyperv-" + RAND_SUFFIX,
        "displaytext": "cks-u2204-hyperv-" + RAND_SUFFIX,
        "format": "vhd",
        "hypervisor": "xenserver",
        "ostype": "Ubuntu 22.04 LTS",
        "url": "https://download.cloudstack.org/testing/custom_templates/ubuntu/22.04/cks-ubuntu-2204-hyperv.vhd.zip",
        "requireshvm": "True",
        "ispublic": "True",
        "isextractable": "True",
        "forcks": "True"
    },
    "hyperv": {
        "name": "cks-u2204-hyperv-" + RAND_SUFFIX,
        "displaytext": "cks-u2204-hyperv-" + RAND_SUFFIX,
        "format": "vhd",
        "hypervisor": "hyperv",
        "ostype": "Ubuntu 22.04 LTS",
        "url": "https://download.cloudstack.org/testing/custom_templates/ubuntu/22.04/cks-ubuntu-2204-hyperv.vhd.zip",
        "requireshvm": "True",
        "ispublic": "True",
        "isextractable": "True",
        "forcks": "True"
    },
    "vmware": {
        "name": "cks-u2204-vmware-" + RAND_SUFFIX,
        "displaytext": "cks-u2204-vmware-" + RAND_SUFFIX,
        "format": "ova",
        "hypervisor": "vmware",
        "ostype": "Ubuntu 22.04 LTS",
        "url": "https://download.cloudstack.org/testing/custom_templates/ubuntu/22.04/cks-ubuntu-2204-vmware.ova",
        "requireshvm": "True",
        "ispublic": "True",
        "isextractable": "True",
        "forcks": "True"
    }
}

class TestKubernetesCluster(cloudstackTestCase):

    @classmethod
    def setUpClass(cls):
        testClient = super(TestKubernetesCluster, cls).getClsTestClient()
        cls.apiclient = testClient.getApiClient()
        cls.services = testClient.getParsedTestDataConfig()
        cls.zone = get_zone(cls.apiclient, testClient.getZoneForTests())
        cls.hypervisor = testClient.getHypervisorInfo()
        cls.mgtSvrDetails = cls.config.__dict__["mgtSvr"][0].__dict__

        cls.hypervisorNotSupported = False
        cls.hypervisorIsNotVmware = cls.hypervisor.lower() != "vmware"
        if cls.hypervisor.lower() not in ["kvm", "vmware", "xenserver"]:
            cls.hypervisorNotSupported = True
        cls.setup_failed = False
        cls._cleanup = []
        cls.kubernetes_version_ids = []
        cls.vpcAllowAllAclDetailsMap = {}
        cls.initial_configuration_cks_enabled = None

        cls.k8s_version_from = cls.services["cks_kubernetes_version_upgrade_from"]
        cls.k8s_version_to = cls.services["cks_kubernetes_version_upgrade_to"]

        if cls.hypervisorNotSupported == False:
            cls.endpoint_url = Configurations.list(cls.apiclient, name="endpoint.url")[0].value
            if "localhost" in cls.endpoint_url:
                endpoint_url = "http://%s:%d/client/api " %(cls.mgtSvrDetails["mgtSvrIp"], cls.mgtSvrDetails["port"])
                cls.debug("Setting endpoint.url to %s" %(endpoint_url))
                Configurations.update(cls.apiclient, "endpoint.url", endpoint_url)
            cls.initial_configuration_cks_enabled = Configurations.list(cls.apiclient, name="cloud.kubernetes.service.enabled")[0].value
            if cls.initial_configuration_cks_enabled not in ["true", True]:
                cls.debug("Enabling CloudStack Kubernetes Service plugin and restarting management server")
                Configurations.update(cls.apiclient,
                                      "cloud.kubernetes.service.enabled",
                                      "true")
                cls.restartServer()
            cls.updateVmwareSettings(False)
            cls.cks_service_offering = None

            if cls.setup_failed == False:
                try:
                    cls.kubernetes_version_v1 = cls.addKubernetesSupportedVersion(cls.services["cks_kubernetes_versions"][cls.k8s_version_from])
                    cls.kubernetes_version_ids.append(cls.kubernetes_version_v1.id)
                except Exception as e:
                    cls.setup_failed = True
                    cls.debug("Failed to get Kubernetes version ISO in ready state, version=%s, url=%s, %s" %
                        (cls.services["cks_kubernetes_versions"][cls.k8s_version_from]["semanticversion"], cls.services["cks_kubernetes_versions"][cls.k8s_version_from]["url"], e))
            if cls.setup_failed == False:
                try:
                    cls.kubernetes_version_v2 = cls.addKubernetesSupportedVersion(cls.services["cks_kubernetes_versions"][cls.k8s_version_to])
                    cls.kubernetes_version_ids.append(cls.kubernetes_version_v2.id)
                except Exception as e:
                    cls.setup_failed = True
                    cls.debug("Failed to get Kubernetes version ISO in ready state, version=%s, url=%s, %s" %
                        (cls.services["cks_kubernetes_versions"][cls.k8s_version_to]["semanticversion"], cls.services["cks_kubernetes_versions"][cls.k8s_version_to]["url"], e))

            if cls.setup_failed == False:
                cls.nodes_template = None
                cls.mgmtSshKey = None
                if cls.hypervisor.lower() == "vmware":
                    cls.nodes_template = get_test_template(cls.apiclient,
                                                           cls.zone.id,
                                                           cls.hypervisor,
                                                           NODES_TEMPLATE)
                    cls.nodes_template.update(cls.apiclient, forcks=True)
                    cls.mgmtSshKey = cls.getMgmtSshKey()
                cks_offering_data = cls.services["cks_service_offering"]
                cks_offering_data["name"] = 'CKS-Instance-' + random_gen()
                cls.cks_service_offering = ServiceOffering.create(
                                                                  cls.apiclient,
                                                                  cks_offering_data
                                                                 )
                cks_offering_data["name"] = 'CKS-Worker-Offering-' + random_gen()
                cls.cks_worker_nodes_offering = ServiceOffering.create(
                    cls.apiclient,
                    cks_offering_data
                )
                cks_offering_data["name"] = 'CKS-Control-Offering-' + random_gen()
                cls.cks_control_nodes_offering = ServiceOffering.create(
                    cls.apiclient,
                    cks_offering_data
                )
                cks_offering_data["name"] = 'CKS-Etcd-Offering-' + random_gen()
                cls.cks_etcd_nodes_offering = ServiceOffering.create(
                    cls.apiclient,
                    cks_offering_data
                )
                cls._cleanup.append(cls.cks_service_offering)
                cls._cleanup.append(cls.cks_worker_nodes_offering)
                cls._cleanup.append(cls.cks_control_nodes_offering)
                cls._cleanup.append(cls.cks_etcd_nodes_offering)
                cls.domain = get_domain(cls.apiclient)
                cls.account = Account.create(
                    cls.apiclient,
                    cls.services["account"],
                    domainid=cls.domain.id
                )
                cls._cleanup.append(cls.account)

        cls.default_network = None
        if str(cls.zone.securitygroupsenabled) == "True":
            networks = Network.list(
                cls.apiclient,
                listall=True
            )
            cls.default_network = networks[0]

        return

    @classmethod
    def tearDownClass(cls):
        if k8s_cluster != None and k8s_cluster.id != None:
            clsObj = TestKubernetesCluster()
            clsObj.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)

        version_delete_failed = False
        # Delete added Kubernetes supported version
        for version_id in cls.kubernetes_version_ids:
            try:
                cls.deleteKubernetesSupportedVersion(version_id)
            except Exception as e:
                version_delete_failed = True
                cls.debug("Error: Exception during cleanup for added Kubernetes supported versions: %s" % e)
        try:
            # Restore CKS enabled
            if cls.initial_configuration_cks_enabled not in ["true", True]:
                cls.debug("Restoring Kubernetes Service enabled value")
                Configurations.update(cls.apiclient,
                                      "cloud.kubernetes.service.enabled",
                                      "false")
                cls.restartServer()

            cls.updateVmwareSettings(True)

            cleanup_resources(cls.apiclient, cls._cleanup)
        except Exception as e:
            raise Exception("Warning: Exception during cleanup : %s" % e)
        if version_delete_failed == True:
            raise Exception("Warning: Exception during cleanup, unable to delete Kubernetes supported versions")
        return

    @classmethod
    def updateVmwareSettings(cls, tearDown):
        value = "false"
        if not tearDown:
            value = "true"
        if cls.hypervisor.lower() == 'vmware':
            Configurations.update(cls.apiclient,
                                  "vmware.create.full.clone",
                                  value)
            allStoragePools = StoragePool.list(
                cls.apiclient
            )
            for pool in allStoragePools:
                Configurations.update(cls.apiclient,
                                      storageid=pool.id,
                                      name="vmware.create.full.clone",
                                      value=value)

    @classmethod
    def getMgmtSshKey(cls):
        """Get the management server SSH public key"""
        sshClient = SshClient(
            cls.mgtSvrDetails["mgtSvrIp"],
            22,
            cls.mgtSvrDetails["user"],
            cls.mgtSvrDetails["passwd"]
        )
        command = "cat /var/cloudstack/management/.ssh/id_rsa.pub"
        response = sshClient.execute(command)
        return str(response[0])

    @classmethod
    def restartServer(cls):
        """Restart management server"""

        cls.debug("Restarting management server")
        sshClient = SshClient(
                    cls.mgtSvrDetails["mgtSvrIp"],
            22,
            cls.mgtSvrDetails["user"],
            cls.mgtSvrDetails["passwd"]
        )
        command = "service cloudstack-management stop"
        sshClient.execute(command)

        command = "service cloudstack-management start"
        sshClient.execute(command)

        #Waits for management to come up in 5 mins, when it's up it will continue
        timeout = time.time() + 300
        while time.time() < timeout:
            if cls.isManagementUp() is True: return
            time.sleep(5)
        cls.setup_failed = True
        cls.debug("Management server did not come up, failing")
        return

    @classmethod
    def isManagementUp(cls):
        try:
            cls.apiclient.listInfrastructure(listInfrastructure.listInfrastructureCmd())
            return True
        except Exception:
            return False

    @classmethod
    def waitForKubernetesSupportedVersionIsoReadyState(cls, version_id, retries=30, interval=60):
        """Check if Kubernetes supported version ISO is in Ready state"""

        while retries > 0:
            time.sleep(interval)
            list_versions_response = cls.listKubernetesSupportedVersion(version_id)
            if not hasattr(list_versions_response, 'isostate') or not list_versions_response or not list_versions_response.isostate:
                retries = retries - 1
                continue
            if 'Ready' == list_versions_response.isostate:
                return
            elif 'Failed' == list_versions_response.isostate:
                raise Exception( "Failed to download template: status - %s" % template.status)
            retries = retries - 1
        raise Exception("Kubernetes supported version Ready state timed out")

    @classmethod
    def listKubernetesSupportedVersion(cls, version_id):
        listKubernetesSupportedVersionsCmd = listKubernetesSupportedVersions.listKubernetesSupportedVersionsCmd()
        listKubernetesSupportedVersionsCmd.id = version_id
        versionResponse = cls.apiclient.listKubernetesSupportedVersions(listKubernetesSupportedVersionsCmd)
        return versionResponse[0]

    @classmethod
    def addKubernetesSupportedVersion(cls, version_service):
        addKubernetesSupportedVersionCmd = addKubernetesSupportedVersion.addKubernetesSupportedVersionCmd()
        addKubernetesSupportedVersionCmd.semanticversion = version_service["semanticversion"]
        addKubernetesSupportedVersionCmd.name = 'v' + version_service["semanticversion"] + '-' + random_gen()
        addKubernetesSupportedVersionCmd.url = version_service["url"]
        addKubernetesSupportedVersionCmd.mincpunumber = version_service["mincpunumber"]
        addKubernetesSupportedVersionCmd.minmemory = version_service["minmemory"]
        kubernetes_version = cls.apiclient.addKubernetesSupportedVersion(addKubernetesSupportedVersionCmd)
        cls.debug("Waiting for Kubernetes version with ID %s to be ready" % kubernetes_version.id)
        cls.waitForKubernetesSupportedVersionIsoReadyState(kubernetes_version.id)
        kubernetes_version = cls.listKubernetesSupportedVersion(kubernetes_version.id)
        return kubernetes_version

    @classmethod
    def deleteKubernetesSupportedVersion(cls, version_id):
        deleteKubernetesSupportedVersionCmd = deleteKubernetesSupportedVersion.deleteKubernetesSupportedVersionCmd()
        deleteKubernetesSupportedVersionCmd.id = version_id
        cls.apiclient.deleteKubernetesSupportedVersion(deleteKubernetesSupportedVersionCmd)

    @classmethod
    def listKubernetesCluster(cls, cluster_id = None, cluster_name = None):
        listKubernetesClustersCmd = listKubernetesClusters.listKubernetesClustersCmd()
        listKubernetesClustersCmd.listall = True
        if cluster_id != None:
            listKubernetesClustersCmd.id = cluster_id
        if cluster_name != None:
            listKubernetesClustersCmd.name = cluster_name
        clusterResponse = cls.apiclient.listKubernetesClusters(listKubernetesClustersCmd)
        if (cluster_id != None or cluster_name != None) and clusterResponse != None:
            return clusterResponse[0]
        return clusterResponse

    @classmethod
    def deleteKubernetesCluster(cls, cluster_id):
        deleteKubernetesClusterCmd = deleteKubernetesCluster.deleteKubernetesClusterCmd()
        deleteKubernetesClusterCmd.id = cluster_id
        response = cls.apiclient.deleteKubernetesCluster(deleteKubernetesClusterCmd)
        return response

    @classmethod
    def stopKubernetesCluster(cls, cluster_id):
        stopKubernetesClusterCmd = stopKubernetesCluster.stopKubernetesClusterCmd()
        stopKubernetesClusterCmd.id = cluster_id
        response = cls.apiclient.stopKubernetesCluster(stopKubernetesClusterCmd)
        return response



    def deleteKubernetesClusterAndVerify(self, cluster_id, verify = True, forced = False):
        """Delete Kubernetes cluster and check if it is really deleted"""

        delete_response = {}
        forceDeleted = False
        try:
            delete_response = self.deleteKubernetesCluster(cluster_id)
        except Exception as e:
            if forced:
                cluster = self.listKubernetesCluster(cluster_id)
                if cluster != None:
                    if cluster.state in ['Starting', 'Running', 'Upgrading', 'Scaling']:
                        self.stopKubernetesCluster(cluster_id)
                        self.deleteKubernetesCluster(cluster_id)
                    else:
                        forceDeleted = True
                        for cluster_vm in cluster.virtualmachines:
                            cmd = destroyVirtualMachine.destroyVirtualMachineCmd()
                            cmd.id = cluster_vm.id
                            cmd.expunge = True
                            self.apiclient.destroyVirtualMachine(cmd)
                        cmd = deleteNetwork.deleteNetworkCmd()
                        cmd.id = cluster.networkid
                        cmd.forced = True
                        self.apiclient.deleteNetwork(cmd)
                        self.dbclient.execute("update kubernetes_cluster set state='Destroyed', removed=now() where uuid = '%s';" % cluster.id)
            else:
                raise Exception("Error: Exception during delete cluster : %s" % e)

        if verify == True and forceDeleted == False:
            self.assertEqual(
                delete_response.success,
                True,
                "Check KubernetesCluster delete response {}, {}".format(delete_response.success, True)
            )

            db_cluster_removed = self.dbclient.execute("select removed from kubernetes_cluster where uuid = '%s';" % cluster_id)[0][0]

            self.assertNotEqual(
                db_cluster_removed,
                None,
                "KubernetesCluster not removed in DB, {}".format(db_cluster_removed)
            )

    def setUp(self):
        self.services = self.testClient.getParsedTestDataConfig()
        self.apiclient = self.testClient.getApiClient()
        self.dbclient = self.testClient.getDbConnection()
        self.cleanup = []
        return

    def tearDown(self):
        super(TestKubernetesCluster, self).tearDown()

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    @skipTestIf("hypervisorNotSupported")
    def test_01_invalid_upgrade_kubernetes_cluster(self):
        """Test to check for failure while tying to upgrade a Kubernetes cluster to a lower version

        # Validate the following:
        # 1. upgradeKubernetesCluster should fail
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        global k8s_cluster
        k8s_cluster = self.getValidKubernetesCluster(version=self.kubernetes_version_v2)

        self.debug("Downgrading Kubernetes cluster with ID: %s to a lower version. This should fail!" % k8s_cluster.id)

        try:
            k8s_cluster = self.upgradeKubernetesCluster(k8s_cluster.id, self.kubernetes_version_v1.id)
            self.debug("Invalid CKS Kubernetes HA cluster deployed with ID: %s. Deleting it and failing test." % self.kubernetes_version_v1.id)
            self.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)
            self.fail("Kubernetes cluster downgrade to a lower Kubernetes supported version. Must be an error.")
        except Exception as e:
            self.debug("Upgrading Kubernetes cluster with invalid Kubernetes supported version check successful, API failure: %s" % e)
            self.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)

        self.verifyKubernetesClusterUpgrade(k8s_cluster, self.kubernetes_version_v2.id)
        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    @skipTestIf("hypervisorNotSupported")
    def test_02_upgrade_kubernetes_cluster(self):
        """Test to deploy a new Kubernetes cluster and upgrade it to newer version

        # Validate the following:
        # 1. upgradeKubernetesCluster should return valid info for the cluster
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        global k8s_cluster
        k8s_cluster = self.getValidKubernetesCluster(version=self.kubernetes_version_v1)

        time.sleep(self.services["sleep"])
        self.debug("Upgrading Kubernetes cluster with ID: %s" % k8s_cluster.id)
        try:
            k8s_cluster = self.upgradeKubernetesCluster(k8s_cluster.id, self.kubernetes_version_v2.id)
        except Exception as e:
            self.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)
            self.fail("Failed to upgrade Kubernetes cluster due to: %s" % e)

        self.verifyKubernetesClusterUpgrade(k8s_cluster, self.kubernetes_version_v2.id)
        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    @skipTestIf("hypervisorNotSupported")
    def test_03_deploy_and_scale_kubernetes_cluster(self):
        """Test to deploy a new Kubernetes cluster and check for failure while tying to scale it

        # Validate the following:
        # 1. scaleKubernetesCluster should return valid info for the cluster when it is scaled up
        # 2. scaleKubernetesCluster should return valid info for the cluster when it is scaled down
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        global k8s_cluster
        k8s_cluster = self.getValidKubernetesCluster()

        self.debug("Upscaling Kubernetes cluster with ID: %s" % k8s_cluster.id)
        try:
            k8s_cluster = self.scaleKubernetesCluster(k8s_cluster.id, 2)
        except Exception as e:
            self.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)
            self.fail("Failed to upscale Kubernetes cluster due to: %s" % e)

        self.verifyKubernetesClusterScale(k8s_cluster, 2)
        self.debug("Kubernetes cluster with ID: %s successfully upscaled, now downscaling it" % k8s_cluster.id)

        try:
            k8s_cluster = self.scaleKubernetesCluster(k8s_cluster.id, 1)
        except Exception as e:
            self.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)
            self.fail("Failed to downscale Kubernetes cluster due to: %s" % e)

        self.verifyKubernetesClusterScale(k8s_cluster)
        self.debug("Kubernetes cluster with ID: %s successfully downscaled" % k8s_cluster.id)
        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    @skipTestIf("hypervisorNotSupported")
    def test_04_autoscale_kubernetes_cluster(self):
        """Test to enable autoscaling a Kubernetes cluster
        # Validate the following:
        # 1. scaleKubernetesCluster should return valid info for the cluster when it is autoscaled
        # 2. cluster-autoscaler pod should be running
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        global k8s_cluster
        k8s_cluster = self.getValidKubernetesCluster(version=self.kubernetes_version_v2)

        self.debug("Autoscaling Kubernetes cluster with ID: %s" % k8s_cluster.id)
        try:
            k8s_cluster = self.autoscaleKubernetesCluster(k8s_cluster.id, 1, 2)
            self.verifyKubernetesClusterAutoscale(k8s_cluster, 1, 2)

            up = self.waitForAutoscalerPodInRunningState(k8s_cluster.id)
            self.assertTrue(up, "Autoscaler pod failed to run")
            self.debug("Kubernetes cluster with ID: %s has autoscaler running" % k8s_cluster.id)
        except Exception as e:
            self.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)
            self.fail("Failed to autoscale Kubernetes cluster due to: %s" % e)
        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    @skipTestIf("hypervisorNotSupported")
    def test_05_basic_lifecycle_kubernetes_cluster(self):
        """Test to deploy a new Kubernetes cluster

        # Validate the following:
        # 1. createKubernetesCluster should return valid info for new cluster
        # 2. The Cloud Database contains the valid information
        # 3. stopKubernetesCluster should stop the cluster
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        global k8s_cluster
        k8s_cluster = self.getValidKubernetesCluster()

        self.debug("Kubernetes cluster with ID: %s successfully deployed, now stopping it" % k8s_cluster.id)

        self.stopAndVerifyKubernetesCluster(k8s_cluster.id)

        self.debug("Kubernetes cluster with ID: %s successfully stopped, now starting it again" % k8s_cluster.id)

        try:
            k8s_cluster = self.startKubernetesCluster(k8s_cluster.id)
        except Exception as e:
            self.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)
            self.fail("Failed to start Kubernetes cluster due to: %s" % e)

        self.verifyKubernetesClusterState(k8s_cluster, 'Running')
        return


    @attr(tags=["advanced", "smoke"], required_hardware="true")
    @skipTestIf("hypervisorNotSupported")
    def test_06_delete_kubernetes_cluster(self):
        """Test to delete an existing Kubernetes cluster

        # Validate the following:
        # 1. deleteKubernetesCluster should delete an existing Kubernetes cluster
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        global k8s_cluster
        k8s_cluster = self.getValidKubernetesCluster()

        self.debug("Deleting Kubernetes cluster with ID: %s" % k8s_cluster.id)

        self.deleteKubernetesClusterAndVerify(k8s_cluster.id)

        self.debug("Kubernetes cluster with ID: %s successfully deleted" % k8s_cluster.id)

        k8s_cluster = None

        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    @skipTestIf("hypervisorNotSupported")
    def test_08_upgrade_kubernetes_ha_cluster(self):
        """Test to upgrade a HA Kubernetes cluster to newer version

        # Validate the following:
        # 1. upgradeKubernetesCluster should return valid info for the cluster
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        if self.default_network:
            self.skipTest("HA cluster on shared network requires external ip address, skipping it")
        global k8s_cluster
        k8s_cluster = self.getValidKubernetesCluster(1, 3, version=self.kubernetes_version_v1)
        time.sleep(self.services["sleep"])

        self.debug("Upgrading HA Kubernetes cluster with ID: %s" % k8s_cluster.id)
        try:
            k8s_cluster = self.upgradeKubernetesCluster(k8s_cluster.id, self.kubernetes_version_v2.id)
        except Exception as e:
            self.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)
            self.fail("Failed to upgrade Kubernetes HA cluster due to: %s" % e)

        self.verifyKubernetesClusterUpgrade(k8s_cluster, self.kubernetes_version_v2.id)
        self.debug("Kubernetes cluster with ID: %s successfully upgraded" % k8s_cluster.id)
        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    @skipTestIf("hypervisorNotSupported")
    def test_10_vpc_tier_kubernetes_cluster(self):
        """Test to deploy a Kubernetes cluster on VPC

        # Validate the following:
        # 1. Deploy a Kubernetes cluster on a VPC tier
        # 2. Destroy it
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        global k8s_cluster
        if k8s_cluster != None and k8s_cluster.id != None:
            self.deleteKubernetesClusterAndVerify(k8s_cluster.id, False, True)
        self.createVpcOffering()
        self.createVpcTierOffering()
        self.deployVpc()
        self.deployNetworkTier()
        self.default_network = self.vpc_tier
        k8s_cluster = self.getValidKubernetesCluster(1, 1)

        self.debug("Deleting Kubernetes cluster with ID: %s" % k8s_cluster.id)
        self.deleteKubernetesClusterAndVerify(k8s_cluster.id)
        self.debug("Kubernetes cluster with ID: %s successfully deleted" % k8s_cluster.id)
        k8s_cluster = None
        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    def test_11_test_unmanaged_cluster_lifecycle(self):
        """Test all operations on unmanaged Kubernetes cluster

        # Validate the following:
        # 1. createKubernetesCluster should return valid info for new cluster
        # 2. The Cloud Database contains the valid information
        # 3. stopKubernetesCluster doesn't work
        # 4. startKubernetesCluster doesn't work
        # 5. upgradeKubernetesCluster doesn't work
        # 6. Adding & removing vm from cluster works
        # 7. deleteKubernetesCluster should delete an existing HA Kubernetes cluster
        """
        cluster = self.createKubernetesCluster("test-unmanaged-cluster", None,
                                               cluster_type="ExternalManaged")
        self.verifyKubernetesClusterState(cluster, 'Running')
        self.debug("Stopping unmanaged Kubernetes cluster with ID: %s" % cluster.id)
        try:
            self.stopKubernetesCluster(cluster.id)
            self.fail("Should not be able to stop unmanaged cluster")
        except Exception as e:
            self.debug("Expected exception: %s" % e)

        self.debug("Starting unmanaged Kubernetes cluster with ID: %s" % cluster.id)
        try:
            self.startKubernetesCluster(cluster.id)
            self.fail("Should not be able to start unmanaged cluster")
        except Exception as e:
            self.debug("Expected exception: %s" % e)

        self.debug("Upgrading unmanaged Kubernetes cluster with ID: %s" % cluster.id)
        try:
            self.upgradeKubernetesCluster(cluster.id, self.kubernetes_version_1_24_0.id)
            self.fail("Should not be able to upgrade unmanaged cluster")
        except Exception as e:
            self.debug("Expected exception: %s" % e)

        template = get_template(self.apiclient,
                                    self.zone.id,
                                    self.services["ostype"])

        self.services["virtual_machine"]["template"] = template.id
        virtualMachine = VirtualMachine.create(self.apiclient, self.services["virtual_machine"], zoneid=self.zone.id,
                                                            accountid=self.account.name, domainid=self.account.domainid,
                                                            serviceofferingid=self.cks_service_offering.id)
        self.debug("Adding VM %s to unmanaged Kubernetes cluster with ID: %s" % (virtualMachine.id, cluster.id))
        self.addVirtualMachinesToKubernetesCluster(cluster.id, [virtualMachine.id])
        cluster = self.listKubernetesCluster(cluster.id)
        self.assertEqual(virtualMachine.id, cluster.virtualmachines[0].id, "VM should be part of the kubernetes cluster")
        self.assertEqual(1, len(cluster.virtualmachines), "Only one VM should be part of the kubernetes cluster")

        self.debug("Removing VM %s from unmanaged Kubernetes cluster with ID: %s" % (virtualMachine.id, cluster.id))
        self.removeVirtualMachinesFromKubernetesCluster(cluster.id, [virtualMachine.id])
        cluster = self.listKubernetesCluster(cluster.id)
        self.assertEqual(0, len(cluster.virtualmachines), "No VM should be part of the kubernetes cluster")

        self.debug("Deleting unmanaged Kubernetes cluster with ID: %s" % cluster.id)
        self.deleteKubernetesClusterAndVerify(cluster.id)
        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    def test_12_test_deploy_cluster_different_offerings_per_node_type(self):
        """Test creating a CKS cluster with different offerings per node type

        # Validate the following on Kubernetes cluster creation:
        # - Use a service offering for control nodes
        # - Use a service offering for worker nodes
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        cluster = self.getValidKubernetesCluster(worker_offering=self.cks_worker_nodes_offering,
                                                 control_offering=self.cks_control_nodes_offering)
        self.assertEqual(
            cluster.workerofferingid,
            self.cks_worker_nodes_offering.id,
            "Check Worker Nodes Offering {}, {}".format(cluster.workerofferingid, self.cks_worker_nodes_offering.id)
        )
        self.assertEqual(
            cluster.controlofferingid,
            self.cks_control_nodes_offering.id,
            "Check Control Nodes Offering {}, {}".format(cluster.workerofferingid, self.cks_worker_nodes_offering.id)
        )
        self.assertEqual(
            cluster.etcdnodes,
            0,
            "No Etcd Nodes expected but got {}".format(cluster.etcdnodes)
        )
        self.debug("Deleting Kubernetes cluster with ID: %s" % cluster.id)
        self.deleteKubernetesClusterAndVerify(cluster.id)
        return

    @attr(tags=["advanced", "smoke"], required_hardware="true")
    @skipTestIf("hypervisorIsNotVmware")
    def test_13_test_add_external_nodes_to_cluster(self):
        """Test adding and removing external nodes to CKS clusters

        # Validate the following:
        # - Deploy Kubernetes Cluster
        # - Deploy VM on the same network as the Kubernetes cluster with the worker nodes offering and CKS ready template
        # - Add external node to the Kubernetes Cluster
        # - Remove external node from the Kubernetes Cluster
        """
        if self.setup_failed == True:
            self.fail("Setup incomplete")
        cluster = self.getValidKubernetesCluster(worker_offering=self.cks_worker_nodes_offering,
                                                 control_offering=self.cks_control_nodes_offering)
        self.assertEqual(
            cluster.size,
            1,
            "Expected 1 worker node but got {}".format(cluster.size)
        )
        self.services["virtual_machine"]["template"] = self.nodes_template.id
        external_node = VirtualMachine.create(self.apiclient,
                                              self.services["virtual_machine"],
                                              zoneid=self.zone.id,
                                              accountid=self.account.name,
                                              domainid=self.account.domainid,
                                              rootdiskcontroller="osdefault",
                                              rootdisksize=8,
                                              serviceofferingid=self.cks_worker_nodes_offering.id,
                                              networkids=cluster.networkid)

        # Acquire public IP and create Port Forwarding Rule and Firewall rule for SSH access
        free_ip_addresses = PublicIPAddress.list(
            self.apiclient,
            domainid=self.account.domainid,
            account=self.account.name,
            forvirtualnetwork=True,
            state='Free'
        )
        random.shuffle(free_ip_addresses)
        external_node_ip = free_ip_addresses[0]
        external_node_ipaddress = PublicIPAddress.create(
            self.apiclient,
            zoneid=self.zone.id,
            networkid=cluster.networkid,
            ipaddress=external_node_ip.ipaddress
        )
        self.debug("Creating Firewall rule for VM ID: %s" % external_node.id)
        fw_rule = FireWallRule.create(
            self.apiclient,
            ipaddressid=external_node_ip.id,
            protocol='TCP',
            cidrlist=['0.0.0.0/0'],
            startport=22,
            endport=22
        )
        pf_rule = {
            "privateport": 22,
            "publicport": 22,
            "protocol": "TCP"
        }
        nat_rule = NATRule.create(
            self.apiclient,
            external_node,
            pf_rule,
            ipaddressid=external_node_ip.id
        )

        # Add the management server SSH key to the authorized hosts on the external node
        node_ssh_client = SshClient(
            external_node_ip.ipaddress,
            22,
            'cloud',
            'cloud',
            retries=30,
            delay=10
        )
        node_ssh_client.execute("echo '" + self.mgmtSshKey + "' > ~/.ssh/authorized_keys")
        # Remove acquired public IP address and rules
        nat_rule.delete(self.apiclient)
        fw_rule.delete(self.apiclient)
        external_node_ipaddress.delete(self.apiclient)

        self.addExternalNodesToKubernetesCluster(cluster.id, [external_node.id])
        cluster = self.listKubernetesCluster(cluster.id)
        self.assertEqual(
            cluster.size,
            2,
            "Expected 2 worker nodes but got {}".format(cluster.size)
        )
        self.removeExternalNodesFromKubernetesCluster(cluster.id, [external_node.id])
        cluster = self.listKubernetesCluster(cluster.id)
        self.assertEqual(
            cluster.size,
            1,
            "Expected 1 worker node but got {}".format(cluster.size)
        )
        VirtualMachine.delete(external_node, self.apiclient, expunge=True)
        self.debug("Deleting Kubernetes cluster with ID: %s" % cluster.id)
        self.deleteKubernetesClusterAndVerify(cluster.id)
        return

    def addExternalNodesToKubernetesCluster(self, cluster_id, vm_list):
        cmd = addNodesToKubernetesCluster.addNodesToKubernetesClusterCmd()
        cmd.id = cluster_id
        cmd.nodeids = vm_list
        return self.apiclient.addNodesToKubernetesCluster(cmd)

    def removeExternalNodesFromKubernetesCluster(self, cluster_id, vm_list):
        cmd = removeNodesFromKubernetesCluster.removeNodesFromKubernetesClusterCmd()
        cmd.id = cluster_id
        cmd.nodeids = vm_list
        return self.apiclient.removeNodesFromKubernetesCluster(cmd)

    def addVirtualMachinesToKubernetesCluster(self, cluster_id, vm_list):
        cmd = addVirtualMachinesToKubernetesCluster.addVirtualMachinesToKubernetesClusterCmd()
        cmd.id = cluster_id
        cmd.virtualmachineids = vm_list

        return self.apiclient.addVirtualMachinesToKubernetesCluster(cmd)

    def removeVirtualMachinesFromKubernetesCluster(self, cluster_id, vm_list):
        cmd = removeVirtualMachinesFromKubernetesCluster.removeVirtualMachinesFromKubernetesClusterCmd()
        cmd.id = cluster_id
        cmd.virtualmachineids = vm_list

        return self.apiclient.removeVirtualMachinesFromKubernetesCluster(cmd)

    def createKubernetesCluster(self, name, version_id, size=1, control_nodes=1, etcd_nodes=0, cluster_type='CloudManaged',
                                workers_offering=None, control_offering=None, etcd_offering=None):
        createKubernetesClusterCmd = createKubernetesCluster.createKubernetesClusterCmd()
        createKubernetesClusterCmd.name = name
        createKubernetesClusterCmd.description = name + "-description"
        createKubernetesClusterCmd.kubernetesversionid = version_id
        createKubernetesClusterCmd.size = size
        createKubernetesClusterCmd.controlnodes = control_nodes
        createKubernetesClusterCmd.serviceofferingid = self.cks_service_offering.id
        createKubernetesClusterCmd.zoneid = self.zone.id
        createKubernetesClusterCmd.noderootdisksize = 10
        createKubernetesClusterCmd.account = self.account.name
        createKubernetesClusterCmd.domainid = self.domain.id
        createKubernetesClusterCmd.clustertype = cluster_type
        if workers_offering:
            createKubernetesClusterCmd.nodeofferings.append({
                "node": "WORKER",
                "offering": workers_offering.id
            })
        if control_offering:
            createKubernetesClusterCmd.nodeofferings.append({
                "node": "CONTROL",
                "offering": control_offering.id
            })
        if etcd_nodes > 0 and etcd_offering:
            createKubernetesClusterCmd.etcdnodes = etcd_nodes
            createKubernetesClusterCmd.nodeofferings.append({
                "node": "ETCD",
                "offering": etcd_offering.id
            })
        if self.default_network:
            createKubernetesClusterCmd.networkid = self.default_network.id
        clusterResponse = self.apiclient.createKubernetesCluster(createKubernetesClusterCmd)
        return clusterResponse

    def startKubernetesCluster(self, cluster_id):
        startKubernetesClusterCmd = startKubernetesCluster.startKubernetesClusterCmd()
        startKubernetesClusterCmd.id = cluster_id
        response = self.apiclient.startKubernetesCluster(startKubernetesClusterCmd)
        return response

    def upgradeKubernetesCluster(self, cluster_id, version_id):
        upgradeKubernetesClusterCmd = upgradeKubernetesCluster.upgradeKubernetesClusterCmd()
        upgradeKubernetesClusterCmd.id = cluster_id
        upgradeKubernetesClusterCmd.kubernetesversionid = version_id
        response = self.apiclient.upgradeKubernetesCluster(upgradeKubernetesClusterCmd)
        return response

    def scaleKubernetesCluster(self, cluster_id, size):
        scaleKubernetesClusterCmd = scaleKubernetesCluster.scaleKubernetesClusterCmd()
        scaleKubernetesClusterCmd.id = cluster_id
        scaleKubernetesClusterCmd.size = size
        response = self.apiclient.scaleKubernetesCluster(scaleKubernetesClusterCmd)
        return response

    def autoscaleKubernetesCluster(self, cluster_id, minsize, maxsize):
        scaleKubernetesClusterCmd = scaleKubernetesCluster.scaleKubernetesClusterCmd()
        scaleKubernetesClusterCmd.id = cluster_id
        scaleKubernetesClusterCmd.autoscalingenabled = True
        scaleKubernetesClusterCmd.minsize = minsize
        scaleKubernetesClusterCmd.maxsize = maxsize
        response = self.apiclient.scaleKubernetesCluster(scaleKubernetesClusterCmd)
        return response

    def fetchKubernetesClusterConfig(self, cluster_id):
        getKubernetesClusterConfigCmd = getKubernetesClusterConfig.getKubernetesClusterConfigCmd()
        getKubernetesClusterConfigCmd.id = cluster_id
        response = self.apiclient.getKubernetesClusterConfig(getKubernetesClusterConfigCmd)
        return response

    def waitForAutoscalerPodInRunningState(self, cluster_id, retries=5, interval=60):
        k8s_config = self.fetchKubernetesClusterConfig(cluster_id)
        cfg = io.StringIO(k8s_config.configdata)
        cfg = yaml.safe_load(cfg)
        # Adding this so we don't get certificate exceptions
        cfg['clusters'][0]['cluster']['insecure-skip-tls-verify']=True
        config.load_kube_config_from_dict(cfg)
        v1 = client.CoreV1Api()

        while retries > 0:
            time.sleep(interval)
            pods = v1.list_pod_for_all_namespaces(watch=False, label_selector="app=cluster-autoscaler").items
            if len(pods) == 0 :
                self.debug("Autoscaler pod still not up")
                continue
            pod = pods[0]
            if pod.status.phase == 'Running' :
                self.debug("Autoscaler pod %s up and running!" % pod.metadata.name)
                return True
            self.debug("Autoscaler pod %s up but not running on retry %d. State is : %s" %(pod.metadata.name, retries, pod.status.phase))
            retries = retries - 1
        return False

    def getValidKubernetesCluster(self, size=1, control_nodes=1, version={}, etcd_nodes=0,
                                  worker_offering=None, control_offering=None, etcd_offering=None):
        cluster = k8s_cluster

        # Does a cluster already exist ?
        if cluster == None or cluster.id == None:
            if not version:
                version = self.kubernetes_version_v2
            self.debug("No existing cluster available, k8s_cluster: %s" % cluster)
            return self.createNewKubernetesCluster(version, size, control_nodes, etcd_nodes=etcd_nodes,
                                                   worker_offering=worker_offering, control_offering=control_offering,
                                                   etcd_offering=etcd_offering)

        # Is the existing cluster what is needed ?
        valid = cluster.size == size and cluster.controlnodes == control_nodes
        if version:
            # Check the version only if specified
            valid = valid and cluster.kubernetesversionid == version.id
        else:
            version = self.kubernetes_version_v2

        if valid:
            cluster_id = cluster.id
            cluster = self.listKubernetesCluster(cluster_id)
            if cluster == None:
                # Looks like the cluster disappeared !
                self.debug("Existing cluster, k8s_cluster ID: %s not returned by list API" % cluster_id)
                return self.createNewKubernetesCluster(version, size, control_nodes, etcd_nodes=etcd_nodes,
                                                       worker_offering=worker_offering, control_offering=control_offering,
                                                       etcd_offering=etcd_offering)

        if valid:
            try:
                self.verifyKubernetesCluster(cluster, cluster.name, None, size, control_nodes)
                self.debug("Existing Kubernetes cluster available with name %s" % cluster.name)
                return cluster
            except AssertionError as error:
                self.debug("Existing cluster failed verification due to %s, need to deploy a new one" % error)
                self.deleteKubernetesClusterAndVerify(cluster.id, False, True)

        # Can't have too many loose clusters running around
        if cluster.id != None:
            self.deleteKubernetesClusterAndVerify(cluster.id, False, True)

        self.debug("No valid cluster, need to deploy a new one")
        return self.createNewKubernetesCluster(version, size, control_nodes, etcd_nodes=etcd_nodes,
                                               worker_offering=worker_offering, control_offering=control_offering,
                                               etcd_offering=etcd_offering)

    def createNewKubernetesCluster(self, version, size, control_nodes, etcd_nodes=0,
                                   worker_offering=None, control_offering=None, etcd_offering=None):
        name = 'testcluster-' + random_gen()
        self.debug("Creating for Kubernetes cluster with name %s" % name)
        try:
            cluster = self.createKubernetesCluster(name, version.id, size, control_nodes, etcd_nodes=etcd_nodes,
                                                   workers_offering=worker_offering, control_offering=control_offering,
                                                   etcd_offering=etcd_offering)
            self.verifyKubernetesCluster(cluster, name, version.id, size, control_nodes)
        except Exception as ex:
            cluster = self.listKubernetesCluster(cluster_name = name)
            if cluster != None:
                self.deleteKubernetesClusterAndVerify(cluster.id, False, True)
            self.fail("Kubernetes cluster deployment failed: %s" % ex)
        except AssertionError as err:
            cluster = self.listKubernetesCluster(cluster_name = name)
            if cluster != None:
                self.deleteKubernetesClusterAndVerify(cluster.id, False, True)
            self.fail("Kubernetes cluster deployment failed during cluster verification: %s" % err)
        return cluster

    def verifyKubernetesCluster(self, cluster_response, name, version_id=None, size=1, control_nodes=1):
        """Check if Kubernetes cluster is valid"""

        self.verifyKubernetesClusterState(cluster_response, 'Running')

        if name != None:
            self.assertEqual(
                cluster_response.name,
                name,
                "Check KubernetesCluster name {}, {}".format(cluster_response.name, name)
            )

        if version_id != None:
            self.verifyKubernetesClusterVersion(cluster_response, version_id)

        self.assertEqual(
            cluster_response.zoneid,
            self.zone.id,
            "Check KubernetesCluster zone {}, {}".format(cluster_response.zoneid, self.zone.id)
        )

        self.verifyKubernetesClusterSize(cluster_response, size, control_nodes)

        db_cluster_name = self.dbclient.execute("select name from kubernetes_cluster where uuid = '%s';" % cluster_response.id)[0][0]

        self.assertEqual(
            str(db_cluster_name),
            name,
            "Check KubernetesCluster name in DB {}, {}".format(db_cluster_name, name)
        )

    def verifyKubernetesClusterState(self, cluster_response, state):
        """Check if Kubernetes cluster state is Running"""

        self.assertEqual(
            cluster_response.state,
            'Running',
            "Check KubernetesCluster state {}, {}".format(cluster_response.state, state)
        )

    def verifyKubernetesClusterVersion(self, cluster_response, version_id):
        """Check if Kubernetes cluster node sizes are valid"""

        self.assertEqual(
            cluster_response.kubernetesversionid,
            version_id,
            "Check KubernetesCluster version {}, {}".format(cluster_response.kubernetesversionid, version_id)
        )

    def verifyKubernetesClusterSize(self, cluster_response, size=1, control_nodes=1):
        """Check if Kubernetes cluster node sizes are valid"""

        self.assertEqual(
            cluster_response.size,
            size,
            "Check KubernetesCluster size {}, {}".format(cluster_response.size, size)
        )

        self.assertEqual(
            cluster_response.controlnodes,
            control_nodes,
            "Check KubernetesCluster control nodes {}, {}".format(cluster_response.controlnodes, control_nodes)
        )

    def verifyKubernetesClusterUpgrade(self, cluster_response, version_id):
        """Check if Kubernetes cluster state and version are valid after upgrade"""

        self.verifyKubernetesClusterState(cluster_response, 'Running')
        self.verifyKubernetesClusterVersion(cluster_response, version_id)

    def verifyKubernetesClusterScale(self, cluster_response, size=1, control_nodes=1):
        """Check if Kubernetes cluster state and node sizes are valid after upgrade"""

        self.verifyKubernetesClusterState(cluster_response, 'Running')
        self.verifyKubernetesClusterSize(cluster_response, size, control_nodes)

    def verifyKubernetesClusterAutoscale(self, cluster_response, minsize, maxsize):
        """Check if Kubernetes cluster state and node sizes are valid after upgrade"""

        self.verifyKubernetesClusterState(cluster_response, 'Running')
        self.assertEqual(
            cluster_response.minsize,
            minsize,
            "Check KubernetesCluster minsize {}, {}".format(cluster_response.minsize, minsize)
        )
        self.assertEqual(
            cluster_response.maxsize,
            maxsize,
            "Check KubernetesCluster maxsize {}, {}".format(cluster_response.maxsize, maxsize)
        )

    def stopAndVerifyKubernetesCluster(self, cluster_id):
        """Stop Kubernetes cluster and check if it is really stopped"""

        stop_response = self.stopKubernetesCluster(cluster_id)

        self.assertEqual(
            stop_response.success,
            True,
            "Check KubernetesCluster stop response {}, {}".format(stop_response.success, True)
        )

        db_cluster_state = self.dbclient.execute("select state from kubernetes_cluster where uuid = '%s';" % cluster_id)[0][0]

        self.assertEqual(
            db_cluster_state,
            'Stopped',
            "KubernetesCluster not stopped in DB, {}".format(db_cluster_state)
        )

    def createVpcOffering(self):
        off_service = self.services["vpc_offering"]
        self.vpc_offering = VpcOffering.create(
            self.apiclient,
            off_service
        )
        self.cleanup.append(self.vpc_offering)
        self.vpc_offering.update(self.apiclient, state='Enabled')

    def createVpcTierOffering(self):
        off_service = self.services["nw_offering_isolated_vpc"]
        self.vpc_tier_offering = NetworkOffering.create(
            self.apiclient,
            off_service,
            conservemode=False
        )
        self.cleanup.append(self.vpc_tier_offering)
        self.vpc_tier_offering.update(self.apiclient, state='Enabled')

    def deployAllowEgressDenyIngressVpcInternal(self, cidr):
        service = self.services["vpc"]
        service["cidr"] = cidr
        vpc = VPC.create(
            self.apiclient,
            service,
            vpcofferingid=self.vpc_offering.id,
            zoneid=self.zone.id,
            account=self.account.name,
            domainid=self.account.domainid
        )
        self.cleanup.append(vpc)
        acl = NetworkACLList.create(
            self.apiclient,
            services={},
            name="allowegressdenyingress",
            description="allowegressdenyingress",
            vpcid=vpc.id
        )
        rule ={
            "protocol": "all",
            "traffictype": "egress",
        }
        NetworkACL.create(self.apiclient,
            services=rule,
            aclid=acl.id
        )
        rule["traffictype"] = "ingress"
        rule["action"] = "deny"
        NetworkACL.create(self.apiclient,
            services=rule,
            aclid=acl.id
        )
        self.vpcAllowAllAclDetailsMap[vpc.id] = acl.id
        return vpc

    def deployVpc(self):
        self.vpc = self.deployAllowEgressDenyIngressVpcInternal(VPC_DATA["cidr"])

    def deployNetworkTierInternal(self, network_offering_id, vpc_id, tier_gateway, tier_netmask, acl_id=None, tier_name=None):
        if not acl_id and vpc_id in self.vpcAllowAllAclDetailsMap:
            acl_id = self.vpcAllowAllAclDetailsMap[vpc_id]
        service = self.services["ntwk"]
        if tier_name:
            service["name"] = tier_name
            service["displaytext"] = "vpc-%s" % tier_name
        network = Network.create(
            self.apiclient,
            service,
            self.account.name,
            self.account.domainid,
            networkofferingid=network_offering_id,
            vpcid=vpc_id,
            zoneid=self.zone.id,
            gateway=tier_gateway,
            netmask=tier_netmask,
            aclid=acl_id
        )
        self.cleanup.append(network)
        return network

    def deployNetworkTier(self):
        self.vpc_tier = self.deployNetworkTierInternal(
            self.vpc_tier_offering.id,
            self.vpc.id,
            VPC_DATA["tier1_gateway"],
            VPC_DATA["tier_netmask"]
        )
