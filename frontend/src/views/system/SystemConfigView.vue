<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NIcon, NTabPane, NTabs, NTooltip, useDialog, useMessage } from 'naive-ui'
import { InformationCircleOutline } from '@vicons/ionicons5'
import PageHeader from '@/components/PageHeader.vue'
import { getRoutingConfig, updateRoutingConfig } from '@/api/systemConfig'
import type { RoutingConfigForm } from '@/types'
import { routeModes } from '@/types'
import { useAsyncAction, errorMessage } from '@/composables/useAsyncAction'
import { useDirtyForm } from '@/composables/useDirtyForm'
import { SettingsOutline } from '@vicons/ionicons5'

const message = useMessage()
const dialog = useDialog()
const run = useAsyncAction()

const loading = ref(false)
const form = ref<RoutingConfigForm>({
  mode: 'RANDOM',
  failureThreshold: 3,
  failureCooldownMinutes: 10,
  stickyTtlMinutes: 60,
})

const { isDirty, markClean, reset: resetDirtyForm } = useDirtyForm(form)

const routeModeOptions = routeModes.map((mode) => ({
  label: modeLabel(mode),
  value: mode,
}))

const failureCooldownEnabled = computed(() => (form.value.failureThreshold ?? 0) > 0)

function modeLabel(mode: string) {
  return {
    RANDOM: '随机',
    ROUND_ROBIN: '轮询',
    WEIGHTED: '加权',
    SESSION_STICKY: '会话粘性',
  }[mode] || mode
}

const fieldError = computed(() => {
  const errors: Record<string, string> = {}
  const threshold = form.value.failureThreshold ?? 0
  const cooldown = form.value.failureCooldownMinutes ?? 0
  const sticky = form.value.stickyTtlMinutes ?? 0
  if (threshold < 0) errors.failureThreshold = '不能小于 0'
  if (cooldown < 0) errors.failureCooldownMinutes = '不能小于 0'
  if (sticky < 1) errors.stickyTtlMinutes = '至少 1 分钟'
  if (threshold > 0 && cooldown < 1) errors.failureCooldownMinutes = '已启用失败避让，冷却时间至少 1 分钟'
  return errors
})

const canSave = computed(() => isDirty.value && Object.keys(fieldError.value).length === 0)

async function load() {
  loading.value = true
  try {
    const res = await run(() => getRoutingConfig(), { errorFallback: '加载系统配置失败' })
    if (res) {
      form.value = {
        mode: res.data.data.mode || 'RANDOM',
        failureThreshold: res.data.data.failureThreshold ?? 0,
        failureCooldownMinutes: res.data.data.failureCooldownMinutes ?? 0,
        stickyTtlMinutes: res.data.data.stickyTtlMinutes ?? 60,
      }
      markClean()
    }
  } finally {
    loading.value = false
  }
}

function confirmDiscard() {
  if (!isDirty.value) {
    return Promise.resolve(true)
  }
  return new Promise<boolean>((resolve) => {
    dialog.warning({
      title: '放弃未保存的修改？',
      content: '当前页面的修改尚未保存，刷新或放弃后将无法恢复。',
      positiveText: '放弃修改',
      negativeText: '继续编辑',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
    })
  })
}

async function handleRefresh() {
  if (await confirmDiscard()) {
    await load()
  }
}

async function handleSave() {
  if (!canSave.value) {
    if (Object.keys(fieldError.value).length > 0) {
      message.error('请先修正表单中的错误')
    }
    return
  }
  const payload = {
    ...form.value,
    failureThreshold: form.value.failureThreshold ?? 0,
    failureCooldownMinutes: form.value.failureCooldownMinutes ?? 0,
    stickyTtlMinutes: form.value.stickyTtlMinutes ?? 1,
  }
  const res = await run(() => updateRoutingConfig(payload), {
    success: '系统配置已保存',
    errorFallback: '保存系统配置失败',
  })
  if (res) {
    form.value = {
      mode: res.data.data.mode || 'RANDOM',
      failureThreshold: res.data.data.failureThreshold ?? 0,
      failureCooldownMinutes: res.data.data.failureCooldownMinutes ?? 0,
      stickyTtlMinutes: res.data.data.stickyTtlMinutes ?? 60,
    }
    markClean()
  }
}

function handleReset() {
  resetDirtyForm({
    mode: 'RANDOM',
    failureThreshold: 3,
    failureCooldownMinutes: 10,
    stickyTtlMinutes: 60,
  })
}

function infoTip(text: string) {
  return () => h(NTooltip, { trigger: 'hover', placement: 'top' }, {
    trigger: () => h(NIcon, { size: 16, class: 'tip-icon' }, { default: () => h(InformationCircleOutline) }),
    default: () => h('span', text),
  })
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader
      title="系统配置"
      subtitle="管理路由策略、失败避让与会话粘性等运行期行为；修改后所有新请求立即生效，已有请求不重路由。"
      :icon="SettingsOutline"
    >
      <template #actions>
        <n-button :loading="loading" @click="handleRefresh">刷新</n-button>
        <n-button :disabled="!isDirty" @click="handleReset">重置</n-button>
        <n-button type="primary" :disabled="!canSave" :loading="loading" @click="handleSave">保存</n-button>
      </template>
    </PageHeader>

    <n-tabs type="line" animated default-value="routing" class="config-tabs">
      <n-tab-pane name="routing" tab="路由策略">
        <n-card class="config-card">
          <template #header>
            <span>路由策略</span>
            <component :is="infoTip('决定多个可用渠道时网关如何选择上游。修改后立即生效。')" />
          </template>
          <n-form :model="form" label-placement="top" label-width="120">
            <n-form-item label="路由模式">
              <template #label>
                <span>路由模式</span>
                <component :is="infoTip('RANDOM 随机；ROUND_ROBIN 轮询；WEIGHTED 按渠道权重加权；SESSION_STICKY 同会话优先粘性渠道。')" />
              </template>
              <n-select v-model:value="form.mode" :options="routeModeOptions" />
            </n-form-item>
          </n-form>
        </n-card>
      </n-tab-pane>

      <n-tab-pane name="avoidance" tab="失败避让">
        <n-card class="config-card">
          <template #header>
            <span>失败避让</span>
            <component :is="infoTip('按 (apiKey + channel + model) 累计连续失败次数，达到阈值时将该渠道在冷却时间内移出候选。阈值 0 关闭避让。')" />
          </template>
          <n-alert v-if="!failureCooldownEnabled" type="warning" :show-icon="false" style="margin-bottom: 12px">
            失败阈值 = 0，失败避让已关闭；连续失败不会影响路由选择。
          </n-alert>
          <n-form :model="form" label-placement="top" label-width="120">
            <n-form-item :show-feedback="!!fieldError.failureThreshold" :feedback="fieldError.failureThreshold" label="连续失败阈值">
              <template #label>
                <span>连续失败阈值</span>
                <component :is="infoTip('单 (apiKey, channel, model) 维度累计的连续失败次数；达到后进入临时避让状态。0 = 关闭。')" />
              </template>
              <n-input-number v-model:value="form.failureThreshold" :min="0" :precision="0" placeholder="0 表示关闭" style="width: 100%" />
            </n-form-item>
            <n-form-item :show-feedback="!!fieldError.failureCooldownMinutes" :feedback="fieldError.failureCooldownMinutes" label="冷却分钟数">
              <template #label>
                <span>冷却分钟数</span>
                <component :is="infoTip('达到阈值后该渠道避让多久（分钟），到期后自动恢复候选。')" />
              </template>
              <n-input-number v-model:value="form.failureCooldownMinutes" :min="0" :precision="0" placeholder="0 表示关闭" style="width: 100%" :disabled="!failureCooldownEnabled" />
            </n-form-item>
          </n-form>
        </n-card>
      </n-tab-pane>

      <n-tab-pane name="sticky" tab="会话粘性">
        <n-card class="config-card">
          <template #header>
            <span>会话粘性</span>
            <component :is="infoTip('SESSION_STICKY 模式下，同一会话/密钥在 TTL 内始终命中首次选中的渠道，避免模型上下文漂移。')" />
          </template>
          <n-form :model="form" label-placement="top" label-width="120">
            <n-form-item :show-feedback="!!fieldError.stickyTtlMinutes" :feedback="fieldError.stickyTtlMinutes" label="粘性保留分钟数">
              <template #label>
                <span>粘性保留分钟数</span>
                <component :is="infoTip('粘性绑定在内存中的保留时长，到期后下一次请求重新走路由策略。')" />
              </template>
              <n-input-number v-model:value="form.stickyTtlMinutes" :min="1" :precision="0" style="width: 100%" />
            </n-form-item>
          </n-form>
        </n-card>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<style scoped>
.config-tabs {
  background: var(--surface-bg);
  border-radius: 8px;
  padding: 0 16px;
  box-shadow: var(--shadow-sm);
}

:deep(.tip-icon) {
  margin-left: 6px;
  color: var(--text-muted);
  cursor: help;
  vertical-align: middle;
}

:deep(.config-card) {
  margin: 16px 0 20px;
  background: var(--surface-bg-soft);
}

:deep(.config-card .n-card-header) {
  font-weight: 600;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
