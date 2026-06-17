<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ChannelForm, ChannelVO } from '@/types'
import { useChannelForm } from '@/composables/useChannelForm'
import { useChannelAuth } from '@/composables/useChannelAuth'
import { useChannelActions } from '@/composables/useChannelActions'
import ChannelTable from '@/components/channels/ChannelTable.vue'
import ChannelFormDialog from '@/components/channels/ChannelFormDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { ServerOutline } from '@vicons/ionicons5'

const formApi = useChannelForm()
const actionApi = useChannelActions()
const authApi = useChannelAuth(() => formApi.editingId.value)

const showModal = formApi.showModal
const form = formApi.form
const modelOptions = formApi.modelOptions
const selectedProviderModels = formApi.selectedProviderModels
const modalMode = formApi.modalMode
const editingId = formApi.editingId
const configuredCapabilityOptions = formApi.configuredCapabilityOptions

const authUploading = authApi.authUploading
const oauthAuthorizationUrl = authApi.oauthAuthorizationUrl

const authFileInputRef = ref<HTMLInputElement | null>(null)
const fetchingModels = ref(false)

watch(showModal, (visible) => {
  if (!visible) authApi.reset()
})

async function handleSave() {
  formApi.normalizeAllAliases()
  const ok = await actionApi.save(editingId.value, form.value)
  if (ok) {
    showModal.value = false
    await actionApi.load()
  }
}

async function handleRemove(id: number) {
  const ok = await actionApi.remove(id)
  if (ok) await actionApi.load()
}

async function handleFetchModels() {
  fetchingModels.value = true
  try {
    const models = await actionApi.fetchUpstreamModels({
      type: form.value.type,
      channelId: editingId.value,
      baseUrl: form.value.baseUrl,
      modelsPath: form.value.modelsPath,
      apiKey: form.value.apiKey,
    })
    if (models) {
      formApi.mergeFetchedModels(models)
    } else if (modelOptions.value.length === 0) {
      // 即使失败也要给用户一个反馈，与原逻辑保持一致
    }
  } finally {
    fetchingModels.value = false
  }
}

async function handleAuthFileChange(event: Event) {
  await authApi.handleAuthFileChange(event, () => actionApi.load())
}

function showCreate() {
  formApi.showCreate()
  authApi.reset()
}

function showEdit(item: ChannelVO) {
  formApi.showEdit(item)
  authApi.reset()
}

function showCopy(item: ChannelVO) {
  formApi.showCopy(item)
  authApi.reset()
}

function onFormChange(next: ChannelForm) {
  form.value = next
}

actionApi.load()
</script>

<template>
  <div>
    <PageHeader
      title="渠道管理"
      subtitle="把网关请求转发到指定上游；勾选端点能力并为每种能力配置独立的请求路径，模型支持多选和手动输入。"
      :icon="ServerOutline"
    >
      <template #actions>
        <n-button type="primary" @click="showCreate">新增渠道</n-button>
      </template>
    </PageHeader>

    <n-space vertical>
      <n-alert type="info" title="渠道用于把网关请求转发到指定上游">
        选择供应商后勾选需要的端点能力，为每种能力填写独立的上游请求路径。模型支持多选和手动输入；别名非必填，填写后模型管理中会按别名单独展示，未填写时使用"模型前缀/上游模型名"。
      </n-alert>

      <ChannelTable
        :data="actionApi.channels.value"
        :loading="actionApi.loading.value"
        :quota-map="actionApi.quotaMap.value"
        :quota-loading="actionApi.quotaLoading.value"
        @edit="showEdit"
        @copy="showCopy"
        @remove="handleRemove"
        @refresh-quota="(row: ChannelVO) => actionApi.refreshQuota(row)"
      />
    </n-space>

    <n-modal v-model:show="showModal" :title="modalMode === 'copy' ? '复制渠道' : editingId ? '编辑渠道' : '新增渠道'">
      <ChannelFormDialog
        :form="form"
        :model-options="modelOptions"
        :selected-provider-models="selectedProviderModels"
        :configured-capability-options="configuredCapabilityOptions"
        :modal-mode="modalMode"
        :editing-id="editingId"
        :fetching-models="fetchingModels"
        :auth-uploading="authUploading"
        :oauth-authorization-url="oauthAuthorizationUrl"
        @update:form="onFormChange"
        @update:model-options="(v) => (modelOptions = v)"
        @update:selected-provider-models="(v) => (selectedProviderModels = v)"
        @update:oauth-authorization-url="(v) => (oauthAuthorizationUrl = v)"
        @cancel="showModal = false"
        @save="handleSave"
        @type-change="(t: string) => formApi.handleTypeChange(t)"
        @sync-selected-models="(v: string[]) => formApi.syncSelectedModels(v)"
        @update-capabilities="(v: string[]) => formApi.updateCapabilities(v)"
        @fetch-upstream-models="handleFetchModels"
        @trigger-auth-upload="() => authApi.triggerAuthUpload(authFileInputRef)"
        @auth-file-change="handleAuthFileChange"
        @start-oauth="() => authApi.startOauthLogin()"
        @open-oauth-link="() => authApi.openOauthLink()"
        @copy-oauth-link="() => authApi.copyOauthLink()"
        @submit-oauth-callback="() => authApi.submitOauthCallbackUrl(() => actionApi.load())"
      />
    </n-modal>
  </div>
</template>
