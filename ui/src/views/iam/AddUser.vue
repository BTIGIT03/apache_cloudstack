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
  <div class="form-layout" v-ctrl-enter="handleSubmit">
    <a-spin :spinning="loading">
      <a-form
        :ref="formRef"
        :model="form"
        :rules="rules"
        :loading="loading"
        layout="vertical"
        @finish="handleSubmit">
        <a-form-item name="username" ref="username">
          <template #label>
            <tooltip-label :title="$t('label.username')" :tooltip="apiParams.username.description"/>
          </template>
          <a-input
            v-model:value="form.username"
            :placeholder="apiParams.username.description"
            v-focus="true"/>
        </a-form-item>
        <a-row :gutter="12">
          <a-col :md="24" :lg="12">
            <a-form-item name="password" ref="password">
              <template #label>
                <tooltip-label :title="$t('label.password')" :tooltip="apiParams.password.description"/>
              </template>
              <a-input-password
                v-model:value="form.password"
                :placeholder="apiParams.password.description"/>
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item name="confirmpassword" ref="confirmpassword">
              <template #label>
                <tooltip-label :title="$t('label.confirmpassword')" :tooltip="apiParams.password.description"/>
              </template>
              <a-input-password
                v-model:value="form.confirmpassword"
                :placeholder="apiParams.password.description"/>
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item name="email" ref="email">
          <template #label>
            <tooltip-label :title="$t('label.email')" :tooltip="apiParams.email.description"/>
          </template>
          <a-input
            v-model:value="form.email"
            :placeholder="apiParams.email.description" />
        </a-form-item>
        <a-row :gutter="12">
          <a-col :md="24" :lg="12">
            <a-form-item name="firstname" ref="firstname">
              <template #label>
                <tooltip-label :title="$t('label.firstname')" :tooltip="apiParams.firstname.description"/>
              </template>
              <a-input
                v-model:value="form.firstname"
                :placeholder="apiParams.firstname.description" />
            </a-form-item>
          </a-col>
          <a-col :md="24" :lg="12">
            <a-form-item name="lastname" ref="lastname">
              <template #label>
                <tooltip-label :title="$t('label.lastname')" :tooltip="apiParams.lastname.description"/>
              </template>
              <a-input
                v-model:value="form.lastname"
                :placeholder="apiParams.lastname.description" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item name="domainid" ref="domainid" v-if="isAdminOrDomainAdmin() && !domainid">
          <template #label>
            <tooltip-label :title="$t('label.domainid')" :tooltip="apiParams.domainid.description"/>
          </template>
          <a-select
            :loading="domainLoading"
            v-model:value="form.domainid"
            :placeholder="apiParams.domainid.description"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return  option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option v-for="domain in domainsList" :key="domain.id" :label="domain.path || domain.name || domain.description">
              <span>
                <resource-icon v-if="domain && domain.icon" :image="domain.icon.base64image" size="1x" style="margin-right: 5px"/>
                <block-outlined v-else style="margin-right: 5px" />
                {{ domain.path || domain.name || domain.description }}
              </span>
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="account" ref="account" v-if="!account">
          <template #label>
            <tooltip-label :title="$t('label.account')" :tooltip="apiParams.account.description"/>
          </template>
          <a-select
            v-model:value="form.account"
            :loading="loadingAccount"
            :placeholder="apiParams.account.description"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return  option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option v-for="(item, idx) in accountList" :key="idx" :label="item.name">
              <span>
                <resource-icon v-if="item && item.icon" :image="item.icon.base64image" size="1x" style="margin-right: 5px"/>
                <team-outlined v-else style="margin-right: 5px" />
                {{ item.name }}
              </span>
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="timezone" ref="timezone">
          <template #label>
            <tooltip-label :title="$t('label.timezone')" :tooltip="apiParams.timezone.description"/>
          </template>
          <a-select
            v-model:value="form.timezone"
            :loading="timeZoneLoading"
            showSearch
            optionFilterProp="label"
            :filterOption="(input, option) => {
              return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }" >
            <a-select-option v-for="opt in timeZoneMap" :key="opt.id" :label="opt.name || opt.description">
              {{ opt.name || opt.description }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <div v-if="samlAllowed">
          <a-form-item name="samlenable" ref="samlenable" :label="$t('label.samlenable')">
            <a-switch v-model:checked="form.samlenable" />
          </a-form-item>
          <a-form-item name="samlentity" ref="samlentity" v-if="form.samlenable">
            <template #label>
              <tooltip-label :title="$t('label.samlentity')" :tooltip="apiParams.entityid.description"/>
            </template>
            <a-select
              v-model:value="form.samlentity"
              :loading="idpLoading"
              showSearch
              optionFilterProp="label"
              :filterOption="(input, option) => {
                return option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
              }" >
              <a-select-option v-for="idp in idps" :key="idp.id" :label="idp.orgName">
                {{ idp.orgName }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </div>
        <div :span="24" class="action-button">
          <a-button @click="closeAction">{{ $t('label.cancel') }}</a-button>
          <a-button :loading="loading" ref="submit" type="primary" @click="handleSubmit">{{ $t('label.ok') }}</a-button>
        </div>
      </a-form>
    </a-spin>
  </div>
</template>

<script>
import { ref, reactive, toRaw } from 'vue'
import { getAPI, postAPI } from '@/api'
import { timeZone } from '@/utils/timezone'
import debounce from 'lodash/debounce'
import ResourceIcon from '@/components/view/ResourceIcon'
import TooltipLabel from '@/components/widgets/TooltipLabel'

export default {
  name: 'AddUser',
  components: {
    TooltipLabel,
    ResourceIcon
  },
  data () {
    this.fetchTimeZone = debounce(this.fetchTimeZone, 800)
    return {
      loading: false,
      timeZoneLoading: false,
      timeZoneMap: [],
      domainLoading: false,
      domainsList: [],
      selectedDomain: '',
      samlEnable: false,
      idpLoading: false,
      idps: [],
      loadingAccount: false,
      accountList: [],
      account: null,
      domainid: null
    }
  },
  created () {
    this.apiParams = this.$getApiParams('createUser', 'authorizeSamlSso')
    this.initForm()
    this.fetchData()
  },
  computed: {
    samlAllowed () {
      return 'authorizeSamlSso' in this.$store.getters.apis
    }
  },
  methods: {
    initForm () {
      this.formRef = ref()
      this.form = reactive({})
      this.rules = reactive({
        username: [{ required: true, message: this.$t('message.error.required.input') }],
        password: [{ required: true, message: this.$t('message.error.required.input') }],
        confirmpassword: [
          { required: true, message: this.$t('message.error.required.input') },
          { validator: this.validateConfirmPassword }
        ],
        email: [{ required: true, message: this.$t('message.error.required.input') }],
        firstname: [{ required: true, message: this.$t('message.error.required.input') }],
        lastname: [{ required: true, message: this.$t('message.error.required.input') }],
        account: [{ required: true, message: this.$t('message.error.required.input') }],
        timezone: [{ required: true, message: this.$t('message.error.required.input') }]
      })
    },
    fetchData () {
      this.account = this.$route.query && this.$route.query.account ? this.$route.query.account : null
      this.domainid = this.$route.query && this.$route.query.domainid ? this.$route.query.domainid : null
      if (!this.domianid) {
        this.fetchDomains()
      }
      this.fetchTimeZone()
      if (this.samlAllowed) {
        this.fetchIdps()
      }
    },
    fetchDomains () {
      this.domainLoading = true
      var params = {
        listAll: true,
        showicon: true,
        details: 'min'
      }
      getAPI('listDomains', params).then(response => {
        this.domainsList = response.listdomainsresponse.domain || []
      }).catch(error => {
        this.$notification.error({
          message: `${this.$t('label.error')} ${error.response.status}`,
          description: error.response.data.errorresponse.errortext
        })
      }).finally(() => {
        const domainid = this.domainsList[0]?.id || ''
        this.form.domainid = domainid
        this.fetchAccount(domainid)
        this.domainLoading = false
      })
    },
    fetchAccount (domainid) {
      this.accountList = []
      this.form.account = null
      this.loadingAccount = true
      var params = { listAll: true, showicon: true }
      if (domainid) {
        params.domainid = domainid
      }
      getAPI('listAccounts', params).then(response => {
        this.accountList = response.listaccountsresponse.account || []
      }).catch(error => {
        this.$notification.error({
          message: `${this.$t('label.error')} ${error.response.status}`,
          description: error.response.data.errorresponse.errortext
        })
      }).finally(() => {
        this.loadingAccount = false
      })
    },
    fetchTimeZone (value) {
      this.timeZoneMap = []
      this.timeZoneLoading = true

      timeZone(value).then(json => {
        this.timeZoneMap = json
        this.timeZoneLoading = false
      })
    },
    fetchIdps () {
      this.idpLoading = true
      getAPI('listIdps').then(response => {
        this.idps = response.listidpsresponse.idp || []
        this.form.samlentity = this.idps[0].id || ''
      }).finally(() => {
        this.idpLoading = false
      })
    },
    isAdminOrDomainAdmin () {
      return ['Admin', 'DomainAdmin'].includes(this.$store.getters.userInfo.roletype)
    },
    isValidValueForKey (obj, key) {
      return key in obj && obj[key] != null
    },
    async handleSubmit (e) {
      e.preventDefault()
      if (this.loading) return

      await this.formRef.value.validate()
        .catch(error => this.formRef.value.scrollToField(error.errorFields[0].name))

      this.loading = true
      const values = toRaw(this.form)
      try {
        const userCreationResponse = await this.createUser(values)
        this.$notification.success({
          message: this.$t('label.create.user'),
          description: `${this.$t('message.success.create.user')} ${values.username}`
        })

        const user = userCreationResponse?.createuserresponse?.user
        if (values.samlenable && user) {
          await postAPI('authorizeSamlSso', {
            enable: values.samlenable,
            entityid: values.samlentity,
            userid: user.id
          })
          this.$notification.success({
            message: this.$t('label.samlenable'),
            description: this.$t('message.success.enable.saml.auth')
          })
        }

        this.closeAction()
        this.$emit('refresh-data')
      } catch (error) {
        if (error?.config?.params?.command === 'authorizeSamlSso') {
          this.closeAction()
          this.$emit('refresh-data')
        }

        this.$notification.error({
          message: this.$t('message.request.failed'),
          description: error?.response?.headers['x-description'] || error.message,
          duration: 0
        })
      } finally {
        this.loading = false
      }
    },
    async createUser (rawParams) {
      const params = {
        username: rawParams.username,
        password: rawParams.password,
        email: rawParams.email,
        firstname: rawParams.firstname,
        lastname: rawParams.lastname,
        accounttype: 0
      }

      if (this.account) {
        params.account = this.account
      } else if (this.accountList[rawParams.account]) {
        params.account = this.accountList[rawParams.account].name
      }

      if (this.domainid) {
        params.domainid = this.domainid
      } else if (rawParams.domainid) {
        params.domainid = rawParams.domainid
      }

      if (this.isValidValueForKey(rawParams, 'timezone') && rawParams.timezone.length > 0) {
        params.timezone = rawParams.timezone
      }

      return postAPI('createUser', params)
    },
    async validateConfirmPassword (rule, value) {
      if (!value || value.length === 0) {
        return Promise.resolve()
      } else if (rule.field === 'confirmpassword') {
        const messageConfirm = this.$t('error.password.not.match')
        const passwordVal = this.form.password
        if (passwordVal && passwordVal !== value) {
          return Promise.reject(messageConfirm)
        } else {
          return Promise.resolve()
        }
      } else {
        return Promise.resolve()
      }
    },
    closeAction () {
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
}
</style>
