<script setup lang="ts">
import { computed, h } from 'vue'
import { NConfigProvider, NDialogProvider, NMessageProvider, NNotificationProvider, darkTheme, dateZhCN, zhCN } from 'naive-ui'
import { RouterView } from 'vue-router'
import { useTheme } from '@/composables/useTheme'

const { effectiveDark } = useTheme()
const themeOverrides = computed(() => {
  return effectiveDark.value ? {
    common: {
      primaryColor: '#60a5fa',
      primaryColorHover: '#93c5fd',
      primaryColorPressed: '#3b82f6',
    },
  } : {
    common: {
      primaryColor: '#2563eb',
      primaryColorHover: '#1d4ed8',
      primaryColorPressed: '#1e40af',
    },
  }
})
</script>

<template>
  <n-config-provider
    :theme="effectiveDark ? darkTheme : null"
    :theme-overrides="themeOverrides"
    :locale="zhCN"
    :date-locale="dateZhCN"
    preflight-style-disabled
  >
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <router-view />
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<style>
html,
body,
#app {
  width: 100%;
  height: 100%;
  margin: 0;
}

body {
  overflow: hidden;
  background: var(--page-bg);
  color: var(--text-primary);
  transition: background-color 0.2s ease, color 0.2s ease;
}
</style>
