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

<template>
  <a-spin :spinning="networkLoading">
    <a-tabs
      :activeKey="currentTab"
      :tabPosition="device === 'mobile' ? 'top' : 'left'"
      :animated="false"
      @change="handleChangeTab">
      <a-tab-pane :tab="$t('label.details')" key="details">
        <DetailsTab :resource="resource" :loading="loading" />
      </a-tab-pane>
      <a-tab-pane v-if="resource.clustertype === 'CloudManaged'" :tab="$t('label.access')" key="access">
        <a-card :title="$t('label.kubeconfig.cluster')" :loading="versionLoading">
          <div v-if="clusterConfig !== ''">
            <a-textarea :value="clusterConfig" :rows="5" readonly />
            <div :span="24" class="action-button">
              <a-button @click="downloadKubernetesClusterConfig" type="primary">{{ $t('label.download.kubernetes.cluster.config') }}</a-button>
            </div>
          </div>
          <div v-else>
            <p>{{ $t('message.kubeconfig.cluster.not.available') }}</p>
          </div>
        </a-card>
        <a-card :title="$t('label.using.cli')" :loading="versionLoading">
          <a-timeline>
            <a-timeline-item>
              <p v-html="$t('label.download.kubeconfig.cluster')">
              </p>
            </a-timeline-item>
            <a-timeline-item>
              <p v-html="$t('label.download.kubectl')"></p>
              <p>
                {{ $t('label.linux') }}: <a :href="kubectlLinuxLink">{{ kubectlLinuxLink }}</a><br>
                {{ $t('label.macos') }}: <a :href="kubectlMacLink">{{ kubectlMacLink }}</a><br>
                {{ $t('label.windows') }}: <a :href="kubectlWindowsLink">{{ kubectlWindowsLink }}</a>
              </p>
            </a-timeline-item>
            <a-timeline-item>
              <p v-html="$t('label.use.kubectl.access.cluster')"></p>
              <p>
                <code><b>kubectl --kubeconfig /custom/path/kube.conf {COMMAND}</b></code><br><br>
                <em>{{ $t('label.list.pods') }}</em><br>
                <code>kubectl --kubeconfig /custom/path/kube.conf get pods --all-namespaces</code><br>
                <em>{{ $t('label.list.nodes') }}</em><br>
                <code>kubectl --kubeconfig /custom/path/kube.conf get nodes --all-namespaces</code><br>
                <em>{{ $t('label.list.services') }}</em><br>
                <code>kubectl --kubeconfig /custom/path/kube.conf get services --all-namespaces</code>
              </p>
            </a-timeline-item>
          </a-timeline>
        </a-card>
        <a-card :title="$t('label.kubernetes.dashboard')">
          <a-timeline>
            <a-timeline-item>
              <p>
                {{ $t('label.run.proxy.locally') }}<br><br>
                <code><b>kubectl --kubeconfig /custom/path/kube.conf proxy</b></code>
              </p>
            </a-timeline-item>
            <a-timeline-item>
              <p>
                {{ $t('label.open.url') }}<br><br>
                <a href="http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/"><code>http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/</code></a>
              </p>
            </a-timeline-item>
            <a-timeline-item>
              <p v-html="$t('label.kubernetes.dashboard.create.token')"></p>
              <p v-html="$t('label.kubernetes.dashboard.create.token.desc')"></p>
              <a-textarea :value="'kubectl --kubeconfig /custom/path/kube.conf apply -f - <<EOF\napiVersion: v1\nkind: ServiceAccount\nmetadata:\n  name: kubernetes-dashboard-admin-user\n  namespace: kubernetes-dashboard\n---\napiVersion: rbac.authorization.k8s.io/v1\nkind: ClusterRoleBinding\nmetadata:\n  name: kubernetes-dashboard-admin-user\nroleRef:\n  apiGroup: rbac.authorization.k8s.io\n  kind: ClusterRole\n  name: cluster-admin\nsubjects:\n- kind: ServiceAccount\n  name: kubernetes-dashboard-admin-user\n  namespace: kubernetes-dashboard\n---\napiVersion: v1\nkind: Secret\ntype: kubernetes.io/service-account-token\nmetadata:\n  name: kubernetes-dashboard-token\n  namespace: kubernetes-dashboard\n  annotations:\n    kubernetes.io/service-account.name: kubernetes-dashboard-admin-user\nEOF'" :rows="10" readonly />
            </a-timeline-item>
            <a-timeline-item>
              <p>
                {{ $t('label.token.for.dashboard.login') }}<br><br>
                <code><b>kubectl --kubeconfig /custom/path/kube.conf describe secret $(kubectl --kubeconfig /custom/path/kube.conf get secrets -n kubernetes-dashboard | grep kubernetes-dashboard-token | awk '{print $1}') -n kubernetes-dashboard</b></code>
              </p>
            </a-timeline-item>
          </a-timeline>
          <p>{{ $t('label.more.access.dashboard.ui') }}, <a href="https://kubernetes.io/docs/tasks/access-application-cluster/web-ui-dashboard/#accessing-the-dashboard-ui">https://kubernetes.io/docs/tasks/access-application-cluster/web-ui-dashboard/#accessing-the-dashboard-ui</a></p>
        </a-card>
        <a-card :title="$t('label.access.kubernetes.nodes')">
          <p v-html="$t('label.kubernetes.access.details')"></p>
        </a-card>
      </a-tab-pane>
      <a-tab-pane :tab="$t('label.instances')" key="instances">
        <a-table
          class="table"
          size="small"
          :columns="vmColumns"
          :dataSource="virtualmachines"
          :rowKey="item => item.id"
          :pagination="false"
        >
          <template #bodyCell="{ column, text, record, index }">
            <template v-if="column.key === 'name'" :name="text">
              <router-link :to="{ path: '/vm/' + record.id }">{{ record.name }}</router-link>
            </template>
            <template v-if="column.key === 'state'">
              <status :text="text ? text : ''" displayText />
            </template>
            <template v-if="column.key === 'port'" :name="text" :record="record">
              <div v-if="network.type === 'Shared' || network.ip4routing">
                {{ cksSshPortSharedNetwork }}
              </div>
              <div v-else-if="record.isexternalnode || (!record.isexternalnode && !record.isetcdnode)">
                {{ cksSshStartingPort + index }}
              </div>
              <div v-else>
                {{ parseInt(etcdSshPort) + parseInt(getEtcdIndex(record.name)) - 1 }}
              </div>
            </template>
            <template v-if="column.key === 'kubernetesnodeversion'">
              <span> {{ text ? text : '' }} </span>
            </template>
            <template v-if="column.key === 'actions'">
              <a-tooltip placement="bottom" >
                <template #title>
                  {{ $t('label.action.delete.node') }}
                </template>
                <a-popconfirm
                  :title="$t('message.action.delete.node')"
                  @confirm="deleteNode(record)"
                  :okText="$t('label.yes')"
                  :cancelText="$t('label.no')"
                  :disabled="!['Created', 'Running'].includes(resource.state) || resource.autoscalingenabled"
                >
                  <a-button
                    type="danger"
                    shape="circle"
                    :disabled="!['Created', 'Running'].includes(resource.state) || resource.autoscalingenabled">
                    <template #icon><delete-outlined /></template>
                  </a-button>
                </a-popconfirm>
              </a-tooltip>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane :tab="$t('label.firewall')" key="firewall" v-if="publicIpAddress">
        <FirewallRules :resource="publicIpAddress" :loading="networkLoading" />
      </a-tab-pane>
      <a-tab-pane :tab="$t('label.portforwarding')" key="portforwarding" v-if="publicIpAddress">
        <PortForwarding :resource="publicIpAddress" :loading="networkLoading" />
      </a-tab-pane>
      <a-tab-pane :tab="$t('label.loadbalancing')" key="loadbalancing" v-if="publicIpAddress">
        <LoadBalancing :resource="publicIpAddress" :loading="networkLoading" />
      </a-tab-pane>
      <a-tab-pane :tab="$t('label.events')" key="events" v-if="'listEvents' in $store.getters.apis">
        <events-tab :resource="resource" resourceType="KubernetesCluster" :loading="loading" />
      </a-tab-pane>
      <a-tab-pane :tab="$t('label.annotations')" key="comments" v-if="'listAnnotations' in $store.getters.apis">
        <AnnotationsTab
          :resource="resource"
          :items="annotations">
        </AnnotationsTab>
      </a-tab-pane>
    </a-tabs>
  </a-spin>
</template>

<script>
import { getAPI, postAPI } from '@/api'
import { isAdmin } from '@/role'
import { mixinDevice } from '@/utils/mixin.js'
import DetailsTab from '@/components/view/DetailsTab'
import FirewallRules from '@/views/network/FirewallRules'
import PortForwarding from '@/views/network/PortForwarding'
import LoadBalancing from '@/views/network/LoadBalancing'
import Status from '@/components/widgets/Status'
import AnnotationsTab from '@/components/view/AnnotationsTab'
import EventsTab from '@/components/view/EventsTab'

export default {
  name: 'KubernetesServiceTab',
  components: {
    DetailsTab,
    FirewallRules,
    PortForwarding,
    LoadBalancing,
    Status,
    AnnotationsTab,
    EventsTab
  },
  mixins: [mixinDevice],
  inject: ['parentFetchData'],
  props: {
    resource: {
      type: Object,
      required: true
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  data () {
    return {
      clusterConfigLoading: false,
      clusterConfig: '',
      versionLoading: false,
      kubernetesVersion: {},
      kubectlLinuxLink: 'https://storage.googleapis.com/kubernetes-release/release/v1.16.0/bin/linux/amd64/kubectl',
      kubectlMacLink: 'https://storage.googleapis.com/kubernetes-release/release/v1.16.0/bin/darwin/amd64/kubectl',
      kubectlWindowsLink: 'https://storage.googleapis.com/kubernetes-release/release/v1.16.0/bin/windows/amd64/kubectl.exe',
      instanceLoading: false,
      virtualmachines: [],
      vmColumns: [],
      networkLoading: false,
      network: null,
      publicIpAddress: null,
      currentTab: 'details',
      cksSshStartingPort: 2222,
      etcdSshPort: 50000,
      cksSshPortSharedNetwork: 22,
      annotations: []
    }
  },
  created () {
    this.vmColumns = [
      {
        key: 'name',
        title: this.$t('label.name'),
        dataIndex: 'name'
      },
      {
        key: 'state',
        title: this.$t('label.state'),
        dataIndex: 'state'
      },
      {
        title: this.$t('label.instancename'),
        dataIndex: 'instancename'
      },
      {
        title: this.$t('label.ipaddress'),
        dataIndex: 'ipaddress'
      },
      {
        key: 'port',
        title: this.$t('label.ssh.port'),
        dataIndex: 'port'
      },
      {
        key: 'kubernetesnodeversion',
        title: this.$t('label.node.version'),
        dataIndex: 'kubernetesnodeversion'
      },
      {
        title: this.$t('label.zonename'),
        dataIndex: 'zonename'
      }
    ]
    if (!isAdmin()) {
      this.vmColumns = this.vmColumns.filter(x => x.dataIndex !== 'instancename')
    }
    if (this.resource.clustertype === 'ExternalManaged') {
      this.vmColumns = this.vmColumns.filter(x => x.dataIndex !== 'port')
    }
    this.handleFetchData()
    const self = this
    window.addEventListener('popstate', function () {
      self.setCurrentTab()
    })
  },
  watch: {
    resource: {
      deep: true,
      handler (newData, oldData) {
        if (newData && newData !== oldData) {
          this.handleFetchData()
          if (this.resource.ipaddress) {
            this.vmColumns = this.vmColumns.filter(x => x.dataIndex !== 'ipaddress')
          } else {
            this.vmColumns = this.vmColumns.filter(x => x.dataIndex !== 'port')
          }
        }
      }
    },
    '$route.fullPath': function () {
      this.setCurrentTab()
    }
  },
  mounted () {
    if (this.$store.getters.apis.scaleKubernetesCluster?.params?.filter(x => x.name === 'nodeids').length > 0 && this.resource.clustertype === 'CloudManaged') {
      this.vmColumns.push({
        key: 'actions',
        title: this.$t('label.actions'),
        dataIndex: 'actions'
      })
    }
    this.fetchEtcdSshPort()
    this.handleFetchData()
    this.setCurrentTab()
  },
  methods: {
    setCurrentTab () {
      this.currentTab = this.$route.query.tab ? this.$route.query.tab : 'details'
    },
    handleChangeTab (e) {
      this.currentTab = e
      const query = Object.assign({}, this.$route.query)
      query.tab = e
      history.pushState(
        {},
        null,
        '#' + this.$route.path + '?' + Object.keys(query).map(key => {
          return (
            encodeURIComponent(key) + '=' + encodeURIComponent(query[key])
          )
        }).join('&')
      )
    },
    isValidValueForKey (obj, key) {
      return key in obj && obj[key] != null
    },
    arrayHasItems (array) {
      return array !== null && array !== undefined && Array.isArray(array) && array.length > 0
    },
    isObjectEmpty (obj) {
      return !(obj !== null && obj !== undefined && Object.keys(obj).length > 0 && obj.constructor === Object)
    },
    handleFetchData () {
      this.fetchKubernetesClusterConfig()
      this.fetchKubernetesVersion()
      this.fetchInstances()
      this.fetchPublicIpAddress()
      this.fetchComments()
    },
    fetchComments () {
      this.clusterConfigLoading = true
      getAPI('listAnnotations', { entityid: this.resource.id, entitytype: 'KUBERNETES_CLUSTER', annotationfilter: 'all' }).then(json => {
        if (json.listannotationsresponse?.annotation) {
          this.annotations = json.listannotationsresponse.annotation
        }
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.clusterConfigLoading = false
      })
    },
    fetchKubernetesClusterConfig () {
      this.clusterConfigLoading = true
      this.clusterConfig = ''
      if (!this.isObjectEmpty(this.resource)) {
        var params = {}
        params.id = this.resource.id
        getAPI('getKubernetesClusterConfig', params).then(json => {
          const config = json.getkubernetesclusterconfigresponse.clusterconfig
          if (!this.isObjectEmpty(config) &&
            this.isValidValueForKey(config, 'configdata') &&
            config.configdata !== '') {
            this.clusterConfig = config.configdata
          } else {
            this.$notification.error({
              message: this.$t('message.request.failed'),
              description: this.$t('message.error.retrieve.kubeconfig')
            })
          }
        }).finally(() => {
          this.clusterConfigLoading = false
          if (!this.isObjectEmpty(this.kubernetesVersion) && this.isValidValueForKey(this.kubernetesVersion, 'semanticversion')) {
            this.kubectlLinuxLink = 'https://dl.k8s.io/release/v' + this.kubernetesVersion.semanticversion + '/bin/linux/amd64/kubectl'
            this.kubectlMacLink = 'https://dl.k8s.io/release/v' + this.kubernetesVersion.semanticversion + '/bin/darwin/amd64/kubectl'
            this.kubectlWindowsLink = 'https://dl.k8s.io/release/v' + this.kubernetesVersion.semanticversion + '/bin/windows/amd64/kubectl.exe'
          }
        })
      }
    },
    fetchKubernetesVersion () {
      this.versionLoading = true
      if (!this.isObjectEmpty(this.resource) && this.isValidValueForKey(this.resource, 'kubernetesversionid') &&
        this.resource.kubernetesversionid !== '') {
        var params = {}
        params.id = this.resource.kubernetesversionid
        getAPI('listKubernetesSupportedVersions', params).then(json => {
          const versionObjs = json.listkubernetessupportedversionsresponse.kubernetessupportedversion
          if (this.arrayHasItems(versionObjs)) {
            this.kubernetesVersion = versionObjs[0]
          }
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => {
          this.versionLoading = false
          if (!this.isObjectEmpty(this.kubernetesVersion) && this.isValidValueForKey(this.kubernetesVersion, 'semanticversion')) {
            this.kubectlLinuxLink = 'https://dl.k8s.io/release/v' + this.kubernetesVersion.semanticversion + '/bin/linux/amd64/kubectl'
            this.kubectlMacLink = 'https://dl.k8s.io/release/v' + this.kubernetesVersion.semanticversion + '/bin/darwin/amd64/kubectl'
            this.kubectlWindowsLink = 'https://dl.k8s.io/release/v' + this.kubernetesVersion.semanticversion + '/bin/windows/amd64/kubectl.exe'
          }
        })
      }
    },
    fetchInstances () {
      this.instanceLoading = true
      var defaultNodes = this.resource.virtualmachines.filter(x => !x.isexternalnode && !x.isetcdnode)
      var externalNodes = this.resource.virtualmachines.filter(x => x.isexternalnode)
      var etcdNodes = this.resource.virtualmachines.filter(x => x.isetcdnode)
      this.virtualmachines = defaultNodes.concat(externalNodes).concat(etcdNodes)
      this.virtualmachines.map(x => { x.ipaddress = x.nic[0].ipaddress })
      this.instanceLoading = false
    },
    fetchNetwork () {
      this.networkLoading = true
      return new Promise((resolve, reject) => {
        getAPI('listNetworks', {
          listAll: true,
          id: this.resource.networkid
        }).then(json => {
          const networks = json.listnetworksresponse.network
          if (this.arrayHasItems(networks)) {
            this.network = networks[0]
          }
          resolve(this.network)
        })
        this.networkLoading = false
      })
    },
    async fetchPublicIpAddress () {
      await this.fetchNetwork()
      if (this.network && (this.network.type === 'Shared' || this.network.ip4routing)) {
        this.publicIpAddress = null
        return
      }
      this.networkLoading = true
      var params = {
        listAll: true,
        forvirtualnetwork: true
      }
      if (!this.isObjectEmpty(this.resource)) {
        if (this.isValidValueForKey(this.resource, 'projectid') &&
          this.resource.projectid !== '') {
          params.projectid = this.resource.projectid
        }
        if (this.isValidValueForKey(this.resource, 'networkid')) {
          params.associatednetworkid = this.resource.networkid
        }
      }
      getAPI('listPublicIpAddresses', params).then(json => {
        let ips = json.listpublicipaddressesresponse.publicipaddress
        if (this.arrayHasItems(ips)) {
          ips = ips.filter(x => x.issourcenat)
          this.publicIpAddress = ips.length > 0 ? ips[0] : null
        }
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.networkLoading = false
      })
    },
    fetchEtcdSshPort () {
      const params = {}
      params.name = 'cloud.kubernetes.etcd.node.start.port'
      var apiName = 'listConfigurations'
      getAPI(apiName, params).then(json => {
        const configResponse = json.listconfigurationsresponse.configuration
        this.etcdSshPort = configResponse[0]?.value
      })
    },
    downloadKubernetesClusterConfig () {
      var blob = new Blob([this.clusterConfig], { type: 'text/plain' })
      var filename = 'kube.conf'
      if (window.navigator.msSaveOrOpenBlob) {
        window.navigator.msSaveBlob(blob, filename)
      } else {
        var elem = window.document.createElement('a')
        elem.href = window.URL.createObjectURL(blob)
        elem.download = filename
        document.body.appendChild(elem)
        elem.click()
        document.body.removeChild(elem)
      }
    },
    deleteNode (node) {
      const params = {
        id: this.resource.id,
        nodeids: node.id
      }
      postAPI('scaleKubernetesCluster', params).then(json => {
        const jobId = json.scalekubernetesclusterresponse.jobid
        console.log(jobId)
        this.$store.dispatch('AddAsyncJob', {
          title: this.$t('label.action.delete.node'),
          jobid: jobId,
          description: node.name,
          status: 'progress'
        })
        this.$pollJob({
          jobId,
          loadingMessage: `${this.$t('message.deleting.node')} ${node.name}`,
          catchMessage: this.$t('error.fetching.async.job.result'),
          successMessage: `${this.$t('message.success.delete.node')} ${node.name}`,
          successMethod: () => {
            this.parentFetchData()
          }
        })
      }).catch(error => {
        this.$notifyError(error)
      }).finally(() => {
        this.parentFetchData()
      })
    },
    getEtcdIndex (name) {
      const lastIndex = name.lastIndexOf('-')
      if (lastIndex > 0) {
        return name.charAt(lastIndex - 1)
      } else {
        return null
      }
    }
  }
}
</script>

<style lang="scss" scoped>
  .list {

    &__item,
    &__row {
      display: flex;
      flex-wrap: wrap;
      width: 100%;
    }

    &__item {
      margin-bottom: -20px;
    }

    &__col {
      flex: 1;
      margin-right: 20px;
      margin-bottom: 20px;
    }

    &__label {
      font-weight: bold;
    }

  }

  .pagination {
    margin-top: 20px;
  }

  .table {
    margin-top: 20px;
    overflow-y: auto;
  }
</style>
