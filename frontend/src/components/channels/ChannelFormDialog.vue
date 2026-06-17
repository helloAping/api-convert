<script setup lang="ts">
import { ref } from 'vue'
import { capabilityDefaultPaths, channelTypes, endpointLabels, endpointTypeOptions } from '@/types'
import type { ChannelForm, ChannelModelForm, UpstreamModelVO } from '@/types'
import { channelTypeLabel, isAuthType } from '@/composables/useChannelForm'

const props = defineProps<{
  form: ChannelForm
  modelOptions: { label: string; value: string }[]
  selectedProviderModels: string[]
  configuredCapabilityOptions: { label: string; value: string }[]
  modalMode: 'create' | 'edit' | 'copy'
  editingId: number | null
  fetchingModels: boolean
  authUploading: boolean
  oauthAuthorizationUrl: string
}>()

const emit = defineEmits<{
  (e: 'update:form', value: ChannelForm): void
  (e: 'update:modelOptions', value: { label: string; value: string }[]): void
  (e: 'update:selectedProviderModels', value: string[]): void
  (e: 'update:oauthAuthorizationUrl', value: string): void
  (e: 'cancel'): void
  (e: 'save'): void
  (e: 'type-change', type: string): void
  (e: 'sync-selected-models', values: string[]): void
  (e: 'update-capabilities', types: string[]): void
  (e: 'fetch-upstream-models'): void
  (e: 'trigger-auth-upload'): void
  (e: 'auth-file-change', event: Event): void
  (e: 'start-oauth'): void
  (e: 'open-oauth-link'): void
  (e: 'copy-oauth-link'): void
  (e: 'submit-oauth-callback'): void
}>()

const authFileInputRef = ref<HTMLInputElement | null>(null)
const oauthCallbackUrl = ref('')

function buildPublicModelName(providerModel: string, prefix: string): string {
  const normalizedPrefix = prefix.trim().replace(/^\/+/, '').replace(/\/+$/, '')
  return normalizedPrefix ? `${normalizedPrefix}/${providerModel}` : providerModel
}

function parseEndpointTypes(value: string | null | undefined): string[] {
  if (!value) return []
  return value.split(',').map(s => s.trim()).filter(Boolean)
}

function onCapabilityPathChange(index: number, val: string) {
  const next = { ...props.form, capabilities: (props.form.capabilities || []).map((c, i) => i === index ? { ...c, path: val } : c) }
  emit('update:form', next)
}

function onModelAliasUpdate(index: number, val: string) {
  const next = { ...props.form, models: props.form.models.map((m, i) => i === index ? { ...m, modelAlias: val } : m) }
  emit('update:form', next)
}

function onAllowedEndpointsUpdate(index: number, val: string[]) {
  const next = { ...props.form, models: props.form.models.map((m, i) => i === index ? { ...m, allowedEndpointTypes: val.join(',') } : m) }
  emit('update:form', next)
}

function onAllowedCapabilitiesUpdate(index: number, val: string[]) {
  const next = { ...props.form, models: props.form.models.map((m, i) => i === index ? { ...m, allowedCapabilities: val.join(',') } : m) }
  emit('update:form', next)
}
</script>

<template>
  <n-card style="width: 760px">
    <n-form :model="props.form" label-placement="left" label-width="120">
      <n-form-item label="渠道编码">
        <n-input :value="props.form.code" :disabled="!!props.editingId" placeholder="例如：deepseek"
                  @update:value="(v: string) => emit('update:form', { ...props.form, code: v })" />
      </n-form-item>
      <n-form-item label="渠道名称">
        <n-input :value="props.form.name" placeholder="例如：DeepSeek"
                  @update:value="(v: string) => emit('update:form', { ...props.form, name: v })" />
      </n-form-item>
      <n-form-item label="供应商">
        <n-select
          :value="props.form.type"
          :options="channelTypes.map(t => ({ label: channelTypeLabel(t), value: t }))"
          @update:value="(v: string) => { emit('update:form', { ...props.form, type: v }); emit('type-change', v) }"
        />
      </n-form-item>
      <n-form-item v-if="!isAuthType(props.form.type)" label="Base URL">
        <n-input :value="props.form.baseUrl" placeholder="例如：https://api.deepseek.com"
                  @update:value="(v: string) => emit('update:form', { ...props.form, baseUrl: v })" />
      </n-form-item>

      <n-form-item v-if="!isAuthType(props.form.type)" label="端点能力">
        <n-space vertical style="width: 100%">
          <n-select
            :value="(props.form.capabilities || []).map(c => c.type)"
            :options="endpointTypeOptions"
            multiple
            clearable
            :max-tag-count="1"
            placeholder="请选择需要支持的端点能力"
            @update:value="(vals: string[]) => emit('update-capabilities', vals)"
          />
          <div v-if="(props.form.capabilities || []).length > 0" class="capability-table">
            <div class="capability-row capability-head">
              <div>端点类型</div>
              <div>请求路径</div>
            </div>
            <div
              v-for="(cap, index) in (props.form.capabilities || [])"
              :key="cap.type"
              class="capability-row"
            >
              <n-text>{{ endpointLabels[cap.type] || cap.type }}</n-text>
              <n-input
                :value="cap.path"
                :placeholder="capabilityDefaultPaths[cap.type]?.[props.form.type] || ''"
                size="small"
                @update:value="(val: string) => onCapabilityPathChange(index, val)"
              />
            </div>
          </div>
        </n-space>
      </n-form-item>

      <n-form-item v-if="!isAuthType(props.form.type)" label="模型列表路径">
        <n-input :value="props.form.modelsPath" placeholder="例如：/v1/models"
                  @update:value="(v: string) => emit('update:form', { ...props.form, modelsPath: v })" />
      </n-form-item>
      <n-form-item v-if="!isAuthType(props.form.type)" label="API Key">
        <n-input
          :value="props.form.apiKey"
          type="password"
          show-password-on="click"
          :placeholder="props.editingId ? '留空表示不修改密钥' : props.modalMode === 'copy' ? '复制的渠道需重新输入密钥' : '请输入上游 API Key'"
          @update:value="(v: string) => emit('update:form', { ...props.form, apiKey: v })"
        />
      </n-form-item>
      <n-form-item v-else label="授权文件">
        <n-space vertical style="width: 100%">
          <n-space>
            <n-button :loading="props.authUploading" @click="emit('trigger-auth-upload')">上传 auth.json</n-button>
            <n-button @click="emit('start-oauth')">生成 OAuth 授权链接</n-button>
            <input ref="authFileInputRef" type="file" accept=".json,application/json" style="display: none"
                   @change="emit('auth-file-change', $event)" />
          </n-space>
          <n-input-group v-if="props.oauthAuthorizationUrl">
            <n-input :value="props.oauthAuthorizationUrl" readonly />
            <n-button @click="emit('open-oauth-link')">打开</n-button>
            <n-button @click="emit('copy-oauth-link')">复制</n-button>
          </n-input-group>
          <n-input-group v-if="props.oauthAuthorizationUrl">
            <n-input
              v-model:value="oauthCallbackUrl"
              placeholder="粘贴完整回调 URL，例如 http://localhost:1455/auth/callback?code=...&state=..."
            />
            <n-button type="primary" @click="emit('submit-oauth-callback')">提交回调</n-button>
          </n-input-group>
          <n-text depth="3">
            先保存渠道，再上传 auth.json 或生成 OAuth 授权链接；打开授权链接后，把浏览器跳转到 localhost 的完整 URL 粘贴回来。
          </n-text>
        </n-space>
      </n-form-item>
      <n-form-item label="模型前缀">
        <n-input
          :value="props.form.modelPrefix"
          placeholder="非必填，例如：baidu"
          @update:value="(v: string) => emit('update:form', { ...props.form, modelPrefix: v })"
        />
      </n-form-item>
      <n-form-item label="上游模型">
        <n-space vertical style="width: 100%">
          <n-space>
            <n-select
              :value="props.selectedProviderModels"
              :options="props.modelOptions"
              multiple
              filterable
              tag
              clearable
              :max-tag-count="1"
              placeholder="请选择或输入多个上游模型名"
              style="width: 480px"
              @update:value="(vals: string[]) => { emit('update:selectedProviderModels', vals); emit('sync-selected-models', vals) }"
            />
            <n-button :loading="props.fetchingModels" @click="emit('fetch-upstream-models')">获取模型</n-button>
          </n-space>
          <n-text depth="3">获取失败或列表为空时，可以直接输入自定义上游模型名。</n-text>
        </n-space>
      </n-form-item>
      <n-form-item v-if="props.form.models.length > 0" label="模型别名">
        <div class="model-alias-table">
          <div class="model-alias-row model-alias-head">
            <div>模型名称</div>
            <div>模型别名</div>
            <div>允许端点</div>
            <div>能力限制</div>
          </div>
          <div
            v-for="(model, index) in props.form.models"
            :key="model.providerModel"
            class="model-alias-row"
          >
            <n-text>{{ model.providerModel }}</n-text>
            <n-input
              :value="model.modelAlias"
              :placeholder="`默认：${buildPublicModelName(model.providerModel, props.form.modelPrefix)}`"
              @update:value="(val: string) => onModelAliasUpdate(index, val)"
            />
            <n-select
              :value="parseEndpointTypes(model.allowedEndpointTypes)"
              :options="endpointTypeOptions"
              multiple
              clearable
              :max-tag-count="1"
              placeholder="不限"
              @update:value="(val: string[]) => onAllowedEndpointsUpdate(index, val)"
            />
            <n-select
              :value="parseEndpointTypes(model.allowedCapabilities)"
              :options="props.configuredCapabilityOptions"
              multiple
              clearable
              :max-tag-count="1"
              placeholder="不限"
              @update:value="(val: string[]) => onAllowedCapabilitiesUpdate(index, val)"
            />
          </div>
        </div>
      </n-form-item>
      <n-form-item label="路由权重">
        <n-input-number :value="props.form.priority" :min="1" :precision="0"
                        @update:value="(v: number | null) => emit('update:form', { ...props.form, priority: v ?? 100 })" />
      </n-form-item>
      <n-form-item label="启用渠道">
        <n-switch :value="props.form.enabled"
                  @update:value="(v: boolean) => emit('update:form', { ...props.form, enabled: v })" />
      </n-form-item>
    </n-form>
    <n-space justify="end">
      <n-button @click="emit('cancel')">取消</n-button>
      <n-button type="primary" @click="emit('save')">保存</n-button>
    </n-space>
  </n-card>
</template>

<style scoped>
.model-alias-table {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.model-alias-row {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 0.8fr) minmax(0, 1.2fr) minmax(0, 1.2fr);
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border-top: 1px solid #edf0f5;
}

.model-alias-row:first-child {
  border-top: 0;
}

.model-alias-head {
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  background: #f8fafc;
}

.capability-table {
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.capability-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-top: 1px solid #edf0f5;
}

.capability-row:first-child {
  border-top: 0;
}

.capability-head {
  color: #4b5563;
  font-size: 13px;
  font-weight: 600;
  background: #f8fafc;
}
</style>
