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
  <a-spin :spinning="loading">
    <a-form
      class="form-layout"
      layout="vertical"
      :ref="formRef"
      :model="form"
      :rules="rules"
      v-ctrl-enter="handleSubmit"
      @finish="handleSubmit">

      <a-form-item name="name" ref="name">
        <template #label>
          <tooltip-label :title="$t('label.name')" :tooltip="apiParams.name.description"/>
        </template>
        <a-input v-model:value="form.name" />
      </a-form-item>

      <a-form-item name="dns1" ref="dns1">
        <template #label>
          <tooltip-label :title="$t('label.dns1')" :tooltip="apiParams.dns1.description"/>
        </template>
        <a-input v-model:value="form.dns1" />
      </a-form-item>

      <a-form-item name="dns2" ref="dns2">
        <template #label>
          <tooltip-label :title="$t('label.dns2')" :tooltip="apiParams.dns2.description"/>
        </template>
        <a-input v-model:value="form.dns2" />
      </a-form-item>

      <a-form-item name="ip6dns1" ref="ip6dns1">
        <template #label>
          <tooltip-label :title="$t('label.ip6dns1')" :tooltip="apiParams.ip6dns1.description"/>
        </template>
        <a-input v-model:value="form.ip6dns1" />
      </a-form-item>

      <a-form-item name="ip6dns2" ref="ip6dns2">
        <template #label>
          <tooltip-label :title="$t('label.ip6dns2')" :tooltip="apiParams.ip6dns2.description"/>
        </template>
        <a-input v-model:value="form.ip6dns2" />
      </a-form-item>

      <a-form-item name="internaldns1" ref="internaldns1">
        <template #label>
          <tooltip-label :title="$t('label.internaldns1')" :tooltip="apiParams.internaldns1.description"/>
        </template>
        <a-input v-model:value="form.internaldns1" />
      </a-form-item>

      <a-form-item name="internaldns2" ref="internaldns2">
        <template #label>
          <tooltip-label :title="$t('label.internaldns2')" :tooltip="apiParams.internaldns2.description"/>
        </template>
        <a-input v-model:value="form.internaldns2" />
      </a-form-item>

      <a-form-item name="guestcidraddress" ref="guestcidraddress">
        <template #label>
          <tooltip-label :title="$t('label.guestcidraddress')" :tooltip="apiParams.guestcidraddress.description"/>
        </template>
        <a-input v-model:value="form.guestcidraddress" />
      </a-form-item>

      <a-form-item name="domain" ref="domain">
        <template #label>
          <tooltip-label :title="$t('label.domain')" :tooltip="apiParams.domain.description"/>
        </template>
        <a-input v-model:value="form.domain" />
      </a-form-item>

      <a-form-item name="localstorageenabled" ref="localstorageenabled">
        <template #label>
          <tooltip-label :title="$t('label.localstorageenabled')" :tooltip="apiParams.localstorageenabled.description"/>
        </template>
        <a-switch v-model:checked="form.localstorageenabled" />
      </a-form-item>

      <a-form-item name="storageaccessgroups" ref="storageaccessgroups">
        <template #label>
          <tooltip-label :title="$t('label.storageaccessgroups')" :tooltip="apiParamsConfigureStorageAccess.storageaccessgroups.description"/>
        </template>
        <a-select
          mode="tags"
          v-model:value="form.storageaccessgroups"
          showSearch
          optionFilterProp="label"
          :filterOption="(input, option) => {
            return option.children?.[0].children.toLowerCase().indexOf(input.toLowerCase()) >= 0
          }"
          :loading="storageAccessGroupsLoading"
          :placeholder="apiParamsConfigureStorageAccess.storageaccessgroups.description">
          <a-select-option v-for="(opt) in storageAccessGroups" :key="opt">
            {{ opt }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <div :span="24" class="action-button">
        <a-button :loading="loading" @click="onCloseAction">{{ $t('label.cancel') }}</a-button>
        <a-button :loading="loading" ref="submit" type="primary" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
      </div>
    </a-form>
  </a-spin>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  name: 'ZoneUpdate',
  components: {
    TooltipLabel
  },
  props: {
    action: {
      type: Object,
      required: true
    },
    resource: {
      type: Object,
      required: true
    }
  },
  data () {
    return {
      loading: false,
      storageAccessGroups: [],
      storageAccessGroupsLoading: false
    }
  },
  beforeCreate () {
    this.apiParams = this.$getApiParams('updateZone')
    this.apiParamsConfigureStorageAccess = this.$getApiParams('configureStorageAccess')
  },
  created () {
    this.initForm()
    this.fetchStorageAccessGroupsData()
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({
        name: this.resource.name,
        dns1: this.resource.dns1,
        dns2: this.resource.dns2,
        ip6dns1: this.resource.ip6dns1,
        ip6dns2: this.resource.ip6dns2,
        internaldns1: this.resource.internaldns1,
        internaldns2: this.resource.internaldns2,
        guestcidraddress: this.resource.guestcidraddress,
        domain: this.resource.domain,
        localstorageenabled: this.resource.localstorageenabled,
        storageaccessgroups: this.resource.storageaccessgroups
          ? this.resource.storageaccessgroups.split(',')
          : []
      })
      this.rules = reactive({})
    },
    fetchStorageAccessGroupsData () {
      const params = {}
      this.storageAccessGroupsLoading = true
      getAPI('listStorageAccessGroups', params).then(json => {
        const sags = json.liststorageaccessgroupsresponse.storageaccessgroup || []
        for (const sag of sags) {
          if (!this.storageAccessGroups.includes(sag.name)) {
            this.storageAccessGroups.push(sag.name)
          }
        }
      }).finally(() => {
        this.storageAccessGroupsLoading = false
      })
      this.rules = reactive({})
    },
    handleSubmit () {
      this.formRef.value.validate().then(() => {
        const values = toRaw(this.form)
        const params = { id: this.resource.id, ...values }
        delete params.storageaccessgroups

        this.loading = true

        postAPI('updateZone', params).then(json => {
          this.$message.success({
            content: `${this.$t('label.action.update.zone')} - ${values.name}`,
            duration: 2
          })

          if (values.storageaccessgroups != null && values.storageaccessgroups.length > 0) {
            params.storageaccessgroups = values.storageaccessgroups.join(',')
          } else {
            params.storageaccessgroups = ''
          }

          if (params.storageaccessgroups !== undefined && (this.resource.storageaccessgroups ? this.resource.storageaccessgroups.split(',').join(',') : '') !== params.storageaccessgroups) {
            postAPI('configureStorageAccess', {
              zoneid: params.id,
              storageaccessgroups: params.storageaccessgroups
            }).then(response => {
              this.$pollJob({
                jobId: response.configurestorageaccessresponse.jobid,
                successMethod: () => {
                  this.$message.success({
                    content: this.$t('label.action.configure.storage.access.group'),
                    duration: 2
                  })
                },
                errorMessage: this.$t('message.configuring.storage.access.failed')
              })
            })
          }

          this.$emit('refresh-data')
          this.onCloseAction()
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => { this.loading = false })
      }).catch(error => {
        this.formRef.value.scrollToField(error.errorFields[0].name)
      })
    },
    onCloseAction () {
      this.$emit('close-action')
    }
  }
}
</script>

<style scoped lang="less">
.form-layout {
  width: 80vw;

  @media (min-width: 600px) {
    width: 450px;
  }

  .action-button {
    text-align: right;
    margin-top: 20px;

    button {
      margin-right: 5px;
    }
  }
}
</style>
