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
  <a-spin :spinning="loadingNic">
    <a-button
      type="primary"
      style="width: 100%; margin-bottom: 10px"
      @click="showAddNicModal"
      :loading="loadingNic"
      :disabled="!('addNicToVirtualMachine' in $store.getters.apis) || resource.hypervisor === 'External'">
      <template #icon><plus-outlined /></template> {{ $t('label.network.addvm') }}
    </a-button>
    <NicsTable :resource="resource" :loading="loading">
      <template #actions="record">
        <a-popconfirm
        :title="$t('label.set.default.nic')"
          @confirm="setAsDefault(record.nic)"
          :okText="$t('label.yes')"
         :cancelText="$t('label.no')"
          v-if="!record.nic.isdefault && resource.hypervisor !== 'External'"
        >
          <tooltip-button
            tooltipPlacement="bottom"
            :tooltip="$t('label.set.default.nic')"
            :disabled="!('updateDefaultNicForVirtualMachine' in $store.getters.apis)"
            icon="check-square-outlined" />
        </a-popconfirm>
        <tooltip-button
          v-if="record.nic.type !== 'L2' && resource.hypervisor !== 'External'"
          tooltipPlacement="bottom"
          :tooltip="$t('label.change.ip.address')"
          icon="swap-outlined"
          :disabled="!('updateVmNicIp' in $store.getters.apis)"
          @onClick="onChangeIPAddress(record)" />
        <tooltip-button
          v-if="record.nic.type !== 'L2' && resource.hypervisor !== 'External'"
          tooltipPlacement="bottom"
          :tooltip="$t('label.edit.secondary.ips')"
          icon="environment-outlined"
          :disabled="(!('addIpToNic' in $store.getters.apis) && !('addIpToNic' in $store.getters.apis))"
          @onClick="onAcquireSecondaryIPAddress(record)" />
        <a-popconfirm
          :title="$t('message.network.removenic')"
          @confirm="removeNIC(record.nic)"
          :okText="$t('label.yes')"
          :cancelText="$t('label.no')"
          v-if="!record.nic.isdefault && resource.hypervisor !== 'External'"
        >
          <tooltip-button
            tooltipPlacement="bottom"
            :tooltip="$t('label.action.remove.nic')"
            :disabled="!('removeNicFromVirtualMachine' in $store.getters.apis)"
            type="primary"
            :danger="true"
            icon="delete-outlined" />
        </a-popconfirm>
      </template>
    </NicsTable>

    <a-modal
      :visible="showAddNetworkModal"
      :title="$t('label.network.addvm')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="closeModals">
      {{ $t('message.network.addvm.desc') }}
      <a-form @finish="submitAddNetwork" v-ctrl-enter="submitAddNetwork">
        <div class="modal-form">
          <p class="modal-form__label">{{ $t('label.network') }}:</p>
          <a-select
            :value="addNetworkData.network"
            @change="e => addNetworkData.network = e"
            v-focus="true"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option
              v-for="network in addNetworkData.allNetworks"
              :key="network.id"
              :value="network.id"
              :label="network.name">
              <span>
                <resource-icon v-if="network.icon" :image="network.icon.base64image" size="1x" style="margin-right: 5px"/>
                <apartment-outlined v-else style="margin-right: 5px" />
                {{ network.name }}
              </span>
            </a-select-option>
          </a-select>
          <p class="modal-form__label">{{ $t('label.publicip') }}:</p>
          <a-input v-model:value="addNetworkData.ip"></a-input>
          <br>
          <a-checkbox v-model:checked="addNetworkData.makedefault">
            {{ $t('label.make.default') }}
          </a-checkbox>
          <br>
        </div>

        <div :span="24" class="action-button">
          <a-button @click="closeModals">{{ $t('label.cancel') }}</a-button>
          <a-button type="primary" ref="submit" @click="submitAddNetwork">{{ $t('label.ok') }}</a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      :visible="showUpdateIpModal"
      :title="$t('label.change.ipaddress')"
      :maskClosable="false"
      :closable="true"
      :footer="null"
      @cancel="closeModals"
    >
      {{ $t('message.network.updateip') }}

      <a-form @finish="submitUpdateIP" v-ctrl-enter="submitUpdateIP">
        <div class="modal-form">
          <p class="modal-form__label">{{ $t('label.publicip') }}:</p>
          <a-select
            v-if="editNicResource.type==='Shared'"
            v-model:value="editIpAddressValue"
            :loading="listIps.loading"
            v-focus="editNicResource.type==='Shared'"
            showSearch
            optionFilterProp="value"
            :filterOption="(input, option) => {
              return option.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }">
            <a-select-option v-for="ip in listIps.opts" :key="ip.ipaddress">
              {{ ip.ipaddress }}
            </a-select-option>
          </a-select>
          <a-input
            v-else
            v-model:value="editIpAddressValue"
            v-focus="editNicResource.type!=='Shared'"></a-input>
        </div>

        <div :span="24" class="action-button">
          <a-button @click="closeModals">{{ $t('label.cancel') }}</a-button>
          <a-button type="primary" ref="submit" @click="submitUpdateIP">{{ $t('label.ok') }}</a-button>
        </div>
      </a-form>
    </a-modal>

    <a-modal
      :visible="showSecondaryIpModal"
      :title="$t('label.acquire.new.secondary.ip')"
      :maskClosable="false"
      :footer="null"
      :closable="false"
      class="wide-modal"
      @cancel="closeModals"
    >
      <p>
        {{ $t('message.network.secondaryip') }}
      </p>
      <a-divider />
      <div v-ctrl-enter="submitSecondaryIP">
        <div class="modal-form">
          <p class="modal-form__label">{{ $t('label.publicip') }}:</p>
          <a-select
            v-if="editNicResource.type==='Shared'"
            v-model:value="newSecondaryIp"
            :loading="listIps.loading"
            v-focus="editNicResource.type==='Shared'"
            showSearch
            optionFilterProp="value"
            :filterOption="(input, option) => {
              return option.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }">
            <a-select-option v-for="ip in listIps.opts" :key="ip.ipaddress">
              {{ ip.ipaddress }}
            </a-select-option>
          </a-select>
          <a-input
            v-else
            :placeholder="$t('label.new.secondaryip.description')"
            v-model:value="newSecondaryIp"
            v-focus="editNicResource.type!=='Shared'"></a-input>
        </div>

        <div style="margin-top: 10px; display: flex; justify-content:flex-end;">
          <a-button @click="submitSecondaryIP" ref="submit" type="primary" style="margin-right: 10px;">{{ $t('label.add.secondary.ip') }}</a-button>
          <a-button @click="closeModals">{{ $t('label.close') }}</a-button>
        </div>
      </div>

      <a-divider />
      <a-list itemLayout="vertical">
        <a-list-item v-for="(ip, index) in secondaryIPs" :key="index">
          <a-popconfirm
            :title="`${$t('label.action.release.ip')}?`"
            @confirm="removeSecondaryIP(ip.id)"
            :okText="$t('label.yes')"
            :cancelText="$t('label.no')"
          >
            <tooltip-button
              tooltipPlacement="top"
              :tooltip="$t('label.action.release.ip')"
              type="primary"
              :danger="true"
              icon="delete-outlined" />
            {{ ip.ipaddress }}
          </a-popconfirm>
        </a-list-item>
      </a-list>
    </a-modal>
  </a-spin>
</template>

<script>
import { getAPI, postAPI } from '@/api'
import NicsTable from '@/views/network/NicsTable'
import TooltipButton from '@/components/widgets/TooltipButton'
import ResourceIcon from '@/components/view/ResourceIcon'

export default {
  name: 'NicsTab',
  components: {
    NicsTable,
    TooltipButton,
    ResourceIcon
  },
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
  inject: ['parentFetchData'],
  data () {
    return {
      vm: {},
      nic: {},
      showAddNetworkModal: false,
      showUpdateIpModal: false,
      showSecondaryIpModal: false,
      addNetworkData: {
        allNetworks: [],
        network: '',
        ip: '',
        makedefault: false
      },
      loadingNic: false,
      editIpAddressNic: '',
      editIpAddressValue: '',
      editNetworkId: '',
      secondaryIPs: [],
      selectedNicId: '',
      newSecondaryIp: '',
      editNicResource: {},
      listIps: {
        loading: false,
        opts: []
      }
    }
  },
  created () {
    this.vm = this.resource
  },
  methods: {
    listNetworks () {
      getAPI('listNetworks', {
        listAll: 'true',
        showicon: true,
        zoneid: this.vm.zoneid
      }).then(response => {
        this.addNetworkData.allNetworks = response.listnetworksresponse.network.filter(network => !this.vm.nic.map(nic => nic.networkid).includes(network.id))
        this.addNetworkData.network = this.addNetworkData.allNetworks[0].id
      })
    },
    fetchSecondaryIPs (nicId) {
      this.showSecondaryIpModal = true
      this.selectedNicId = nicId
      getAPI('listNics', {
        nicId: nicId,
        keyword: '',
        virtualmachineid: this.vm.id
      }).then(response => {
        this.secondaryIPs = response.listnicsresponse.nic[0].secondaryip
      })
    },
    fetchPublicIps (networkid) {
      this.listIps.loading = true
      this.listIps.opts = []
      getAPI('listPublicIpAddresses', {
        networkid: networkid,
        allocatedonly: false,
        forvirtualnetwork: false
      }).then(json => {
        const listPublicIps = json.listpublicipaddressesresponse.publicipaddress || []
        listPublicIps.forEach(item => {
          if (item.state === 'Free') {
            this.listIps.opts.push({
              ipaddress: item.ipaddress
            })
          }
        })
        this.listIps.opts.sort(function (a, b) {
          const currentIp = a.ipaddress.replaceAll('.', '')
          const nextIp = b.ipaddress.replaceAll('.', '')
          if (parseInt(currentIp) < parseInt(nextIp)) { return -1 }
          if (parseInt(currentIp) > parseInt(nextIp)) { return 1 }
          return 0
        })
      }).finally(() => {
        this.listIps.loading = false
      })
    },
    showAddNicModal () {
      this.showAddNetworkModal = true
      this.listNetworks()
    },
    closeModals () {
      this.showAddNetworkModal = false
      this.showUpdateIpModal = false
      this.showSecondaryIpModal = false
      this.addNetworkData.network = ''
      this.addNetworkData.ip = ''
      this.addNetworkData.makedefault = false
      this.editIpAddressValue = ''
      this.newSecondaryIp = ''
    },
    onChangeIPAddress (record) {
      this.editNicResource = record.nic
      this.editIpAddressNic = record.nic.id
      this.showUpdateIpModal = true
      if (record.nic.type === 'Shared') {
        this.fetchPublicIps(record.nic.networkid)
      }
    },
    onAcquireSecondaryIPAddress (record) {
      if (record.nic.type === 'Shared') {
        this.fetchPublicIps(record.nic.networkid)
      } else {
        this.listIps.opts = []
      }

      this.editNicResource = record.nic
      this.editNetworkId = record.nic.networkid
      this.fetchSecondaryIPs(record.nic.id)
    },
    submitAddNetwork () {
      if (this.loadingNic) return
      const params = {}
      params.virtualmachineid = this.vm.id
      params.networkid = this.addNetworkData.network
      if (this.addNetworkData.ip) {
        params.ipaddress = this.addNetworkData.ip
      }
      this.showAddNetworkModal = false
      this.loadingNic = true
      postAPI('addNicToVirtualMachine', params).then(response => {
        this.$pollJob({
          jobId: response.addnictovirtualmachineresponse.jobid,
          successMessage: this.$t('message.success.add.network'),
          successMethod: async () => {
            if (this.addNetworkData.makedefault) {
              try {
                this.nic = await this.getNic(params.networkid, params.virtualmachineid)
                if (this.nic) {
                  this.setAsDefault(this.nic)
                } else {
                  this.$notifyError('NIC data not found.')
                }
              } catch (error) {
                this.$notifyError('Failed to fetch NIC data.')
              }
            }
            this.loadingNic = false
            this.closeModals()
          },
          errorMessage: this.$t('message.add.network.failed'),
          errorMethod: () => {
            this.loadingNic = false
            this.closeModals()
          },
          loadingMessage: this.$t('message.add.network.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.loadingNic = false
            this.closeModals()
            this.$emit('refresh')
          }
        })
      }).catch(error => {
        this.$notifyError(error)
        this.loadingNic = false
      })
    },
    getNic (networkid, virtualmachineid) {
      const params = {}
      params.virtualmachineid = virtualmachineid
      params.networkid = networkid
      return getAPI('listNics', params).then(response => {
        return response.listnicsresponse.nic[0]
      })
    },
    setAsDefault (item) {
      this.loadingNic = true
      postAPI('updateDefaultNicForVirtualMachine', {
        virtualmachineid: this.vm.id,
        nicid: item.id
      }).then(response => {
        this.$pollJob({
          jobId: response.updatedefaultnicforvirtualmachineresponse.jobid,
          successMessage: `${this.$t('label.success.set')} ${item.networkname} ${this.$t('label.as.default')}. ${this.$t('message.set.default.nic.manual')}.`,
          successMethod: () => {
            this.loadingNic = false
          },
          errorMessage: `${this.$t('label.error.setting')} ${item.networkname} ${this.$t('label.as.default')}`,
          errorMethod: () => {
            this.loadingNic = false
          },
          loadingMessage: `${this.$t('label.setting')} ${item.networkname} ${this.$t('label.as.default')}...`,
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.loadingNic = false
            this.$emit('refresh')
          }
        })
      }).catch(error => {
        this.$notifyError(error)
        this.loadingNic = false
      })
    },
    submitUpdateIP () {
      if (this.loadingNic) return
      this.loadingNic = true
      this.showUpdateIpModal = false
      const params = {
        nicId: this.editIpAddressNic
      }
      if (this.editIpAddressValue) {
        params.ipaddress = this.editIpAddressValue
      }
      postAPI('updateVmNicIp', params).then(response => {
        this.$pollJob({
          jobId: response.updatevmnicipresponse.jobid,
          successMessage: this.$t('message.success.update.ipaddress'),
          successMethod: () => {
            this.loadingNic = false
            this.closeModals()
          },
          errorMessage: this.$t('label.error'),
          errorMethod: () => {
            this.loadingNic = false
            this.closeModals()
          },
          loadingMessage: this.$t('message.update.ipaddress.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.loadingNic = false
            this.closeModals()
            this.$emit('refresh')
          }
        })
      })
        .catch(error => {
          this.$notifyError(error)
          this.loadingNic = false
        })
    },
    removeNIC (item) {
      this.loadingNic = true

      postAPI('removeNicFromVirtualMachine', {
        nicid: item.id,
        virtualmachineid: this.vm.id
      }).then(response => {
        this.$pollJob({
          jobId: response.removenicfromvirtualmachineresponse.jobid,
          successMessage: this.$t('message.success.remove.nic'),
          successMethod: () => {
            this.loadingNic = false
          },
          errorMessage: this.$t('message.error.remove.nic'),
          errorMethod: () => {
            this.loadingNic = false
          },
          loadingMessage: this.$t('message.remove.nic.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.loadingNic = false
            this.$emit('refresh')
          }
        })
      })
        .catch(error => {
          this.$notifyError(error)
          this.loadingNic = false
        })
    },
    submitSecondaryIP () {
      if (this.loadingNic) return
      this.loadingNic = true

      const params = {}
      params.nicid = this.selectedNicId
      if (this.newSecondaryIp) {
        params.ipaddress = this.newSecondaryIp
      }

      postAPI('addIpToNic', params).then(response => {
        this.$pollJob({
          jobId: response.addiptovmnicresponse.jobid,
          successMessage: this.$t('message.success.add.secondary.ipaddress'),
          successMethod: () => {
            this.loadingNic = false
            this.fetchSecondaryIPs(this.selectedNicId)
          },
          errorMessage: this.$t('message.error.add.secondary.ipaddress'),
          errorMethod: () => {
            this.loadingNic = false
            this.fetchSecondaryIPs(this.selectedNicId)
          },
          loadingMessage: this.$t('message.add.secondary.ipaddress.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.loadingNic = false
            this.fetchSecondaryIPs(this.selectedNicId)
            this.$emit('refresh')
          }
        })
      }).catch(error => {
        this.$notifyError(error)
        this.loadingNic = false
      }).finally(() => {
        this.newSecondaryIp = null
        this.fetchPublicIps(this.editNetworkId)
      })
    },
    removeSecondaryIP (id) {
      this.loadingNic = true

      postAPI('removeIpFromNic', { id }).then(response => {
        this.$pollJob({
          jobId: response.removeipfromnicresponse.jobid,
          successMessage: this.$t('message.success.remove.secondary.ipaddress'),
          successMethod: () => {
            this.loadingNic = false
            this.fetchSecondaryIPs(this.selectedNicId)
            this.fetchPublicIps(this.editNetworkId)
          },
          errorMessage: this.$t('message.error.remove.secondary.ipaddress'),
          errorMethod: () => {
            this.loadingNic = false
            this.fetchSecondaryIPs(this.selectedNicId)
          },
          loadingMessage: this.$t('message.remove.secondary.ipaddress.processing'),
          catchMessage: this.$t('error.fetching.async.job.result'),
          catchMethod: () => {
            this.loadingNic = false
            this.fetchSecondaryIPs(this.selectedNicId)
            this.$emit('refresh')
          }
        })
      }).catch(error => {
        this.$notifyError(error)
        this.loadingNic = false
        this.fetchSecondaryIPs(this.selectedNicId)
      })
    }
  }
}
</script>

<style scoped>
.modal-form {
  display: flex;
  flex-direction: column;

  &__label {
    margin-top: 20px;
    margin-bottom: 5px;
    font-weight: bold;

    &--no-margin {
      margin-top: 0;
    }
  }
}

.action-button {
  display: flex;
  flex-wrap: wrap;

  button {
    padding: 5px;
    height: auto;
    margin-bottom: 10px;
    align-self: flex-start;

    &:not(:last-child) {
      margin-right: 10px;
    }
  }
}

.wide-modal {
  min-width: 50vw;
}

:deep(.ant-list-item) {
  padding-top: 12px;
  padding-bottom: 12px;
}
</style>
