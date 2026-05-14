<script setup lang="ts">
import { h, type Component } from 'vue'
import { NIcon } from 'naive-ui'
import { MenuOutline } from '@vicons/ionicons5'

defineProps<{
  menuOptions: { label: string; key: string; icon: Component }[]
  activeKey: string
  collapsed: boolean
}>()

const emit = defineEmits<{
  'update:collapsed': [value: boolean]
  'menu-click': [key: string]
}>()

function renderIcon(icon: Component) {
  return () => h(NIcon, null, { default: () => h(icon) })
}
</script>

<template>
  <div style="display: flex; flex-direction: column; height: 100%">
    <div style="padding: 16px; display: flex; align-items: center; gap: 8px">
      <n-button text @click="emit('update:collapsed', !collapsed)">
        <template #icon>
          <n-icon size="22"><MenuOutline /></n-icon>
        </template>
      </n-button>
      <n-text v-if="!collapsed" strong style="font-size: 16px">API-Convert 管理后台</n-text>
    </div>
    <n-menu
      :collapsed="collapsed"
      :collapsed-width="64"
      :options="menuOptions.map(m => ({ label: m.label, key: m.key, icon: renderIcon(m.icon) }))"
      :value="activeKey"
      @update:value="(key: string) => emit('menu-click', key)"
    />
  </div>
</template>
