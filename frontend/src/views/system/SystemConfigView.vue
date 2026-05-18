<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { getRoutingConfig, updateRoutingConfig } from '@/api/systemConfig'
import type { RoutingConfigForm } from '@/types'
import { routeModes } from '@/types'

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const form = ref<RoutingConfigForm>({
  mode: 'RANDOM',
  failureThreshold: 0,
  failureCooldownMinutes: 0,
  stickyTtlMinutes: 1440,
})

const routeModeOptions = routeModes.map((mode) => ({
  label: modeLabel(mode),
  value: mode,
}))

function modeLabel(mode: string) {
  return {
    RANDOM: '随机',
    ROUND_ROBIN: '轮询',
    WEIGHTED: '加权',
    SESSION_STICKY: '会话粘性',
  }[mode] || mode
}

function errorMessage(error: unknown, fallback: string) {
  const response = (error as { response?: { data?: { message?: string } } })?.response
  return response?.data?.message || fallback
}

async function load() {
  loading.value = true
  try {
    const res = await getRoutingConfig()
    form.value = { ...res.data.data }
  } catch (error) {
    message.error(errorMessage(error, '加载系统配置失败'))
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const payload = {
      ...form.value,
      failureThreshold: form.value.failureThreshold || 0,
      failureCooldownMinutes: form.value.failureCooldownMinutes || 0,
      stickyTtlMinutes: form.value.stickyTtlMinutes || 1,
    }
    const res = await updateRoutingConfig(payload)
    form.value = { ...res.data.data }
    message.success('系统配置已保存')
  } catch (error) {
    message.error(errorMessage(error, '保存系统配置失败'))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <n-space vertical :size="18">
      <n-space justify="space-between" align="center">
        <n-h2>系统配置</n-h2>
        <n-button :loading="loading" @click="load">刷新</n-button>
      </n-space>

      <n-alert type="info" title="路由策略">
        加权模式使用渠道管理里的路由权重；失败避让按“网关密钥 + 渠道 + 模型”累计，阈值或冷却分钟数为 0 时关闭避让。
      </n-alert>

      <n-form :model="form" label-placement="left" label-width="150" class="routing-form">
        <n-form-item label="路由模式">
          <n-select v-model:value="form.mode" :options="routeModeOptions" />
        </n-form-item>
        <n-form-item label="失败阈值">
          <n-input-number
            v-model:value="form.failureThreshold"
            :min="0"
            :precision="0"
            placeholder="0 表示关闭"
            style="width: 100%"
          />
        </n-form-item>
        <n-form-item label="冷却分钟数">
          <n-input-number
            v-model:value="form.failureCooldownMinutes"
            :min="0"
            :precision="0"
            placeholder="0 表示关闭"
            style="width: 100%"
          />
        </n-form-item>
        <n-form-item label="粘性保留分钟数">
          <n-input-number
            v-model:value="form.stickyTtlMinutes"
            :min="1"
            :precision="0"
            style="width: 100%"
          />
        </n-form-item>
        <n-space justify="end" class="form-actions">
          <n-button @click="load">取消</n-button>
          <n-button type="primary" :loading="saving" @click="save">保存</n-button>
        </n-space>
      </n-form>
    </n-space>
  </div>
</template>

<style scoped>
.routing-form {
  max-width: 720px;
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.form-actions {
  padding-top: 4px;
}
</style>
