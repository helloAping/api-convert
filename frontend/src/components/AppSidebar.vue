<script setup lang="ts">
import { h, type Component } from 'vue'
import { NIcon, NText, NTooltip } from 'naive-ui'
import { MenuOutline } from '@vicons/ionicons5'

defineProps<{
  menuOptions: { label: string; key: string; icon: Component }[]
  activeKey: string
  collapsed: boolean
  /** 网关版本号，会显示在 logo 下方 */
  version?: string
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
  <div class="sider-root">
    <div class="sider-brand">
      <!--
        品牌区主体（logo + 标题 + 版本号）跳转到控制台首页（/），与折叠按钮解耦：
        logo 是入口，chevron 是折叠状态切换，避免点击行为歧义。
      -->
      <router-link to="/" class="brand-link" :title="'返回控制台首页'">
        <span class="brand-logo">AC</span>
        <span v-if="!collapsed" class="brand-text">
          <span class="brand-title">API-Convert</span>
          <span v-if="version" class="brand-version">v{{ version }}</span>
        </span>
      </router-link>
      <n-tooltip :delay="300" placement="right">
        <template #trigger>
          <button
            class="brand-toggle"
            :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'"
            @click="emit('update:collapsed', !collapsed)"
          >
            <n-icon size="16"><MenuOutline /></n-icon>
          </button>
        </template>
        {{ collapsed ? '展开侧边栏' : '折叠侧边栏' }}
      </n-tooltip>
    </div>
    <n-menu
      class="sider-menu"
      :collapsed="collapsed"
      :collapsed-width="64"
      :collapsed-icon-size="20"
      :indent="20"
      :options="menuOptions.map(m => ({ label: m.label, key: m.key, icon: renderIcon(m.icon) }))"
      :value="activeKey"
      @update:value="(key: string) => emit('menu-click', key)"
    />
    <div v-if="!collapsed" class="sider-footer">
      <n-text :depth="3" style="font-size: 11px">© 2026 api-convert</n-text>
    </div>
  </div>
</template>

<style scoped>
.sider-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--sider-bg);
  transition: background-color 0.2s ease;
}

.sider-brand {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 12px 8px;
  border-bottom: 1px solid var(--border-color-soft);
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  padding: 6px 8px;
  border-radius: 8px;
  text-decoration: none;
  color: inherit;
  transition: background-color 0.15s ease;
}

.brand-link:hover {
  background: var(--surface-bg-soft);
}

.brand-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  color: var(--text-muted);
  transition: background-color 0.15s ease, color 0.15s ease;
  flex-shrink: 0;
}

.brand-toggle:hover {
  background: var(--surface-bg-soft);
  color: var(--text-primary);
}

.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--logo-color);
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  letter-spacing: 0.5px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.brand-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand-version {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 500;
  line-height: 1.2;
  margin-top: 2px;
}

.sider-menu {
  flex: 1;
  overflow-y: auto;
  padding-top: 8px;
}

.sider-footer {
  padding: 10px 16px 14px;
  border-top: 1px solid var(--border-color-soft);
}
</style>
