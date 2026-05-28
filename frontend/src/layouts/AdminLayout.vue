<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  SpeedometerOutline,
  ServerOutline,
  CubeOutline,
  KeyOutline,
  SettingsOutline,
  DocumentTextOutline,
  LogOutOutline,
} from '@vicons/ionicons5'
import AppSidebar from '@/components/AppSidebar.vue'
import { logout } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const username = ref(localStorage.getItem('admin-username') || 'admin')

const menuOptions = [
  { label: '控制台', key: '/', icon: SpeedometerOutline },
  { label: '渠道管理', key: '/channels', icon: ServerOutline },
  { label: '模型管理', key: '/models', icon: CubeOutline },
  { label: '网关密钥', key: '/api-keys', icon: KeyOutline },
  { label: '系统配置', key: '/system-config', icon: SettingsOutline },
  { label: '请求日志', key: '/request-logs', icon: DocumentTextOutline },
]

function handleMenuClick(key: string) {
  router.push(key)
}

async function handleLogout() {
  try {
    await logout()
  } catch { /* ignore */ }
  localStorage.removeItem('admin-token')
  localStorage.removeItem('admin-username')
  router.push('/login')
}
</script>

<template>
  <n-layout has-sider class="admin-shell">
    <n-layout-sider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="220"
      :collapsed="collapsed"
      class="admin-sider"
    >
      <AppSidebar
        :menu-options="menuOptions"
        :active-key="route.path"
        :collapsed="collapsed"
        @update:collapsed="collapsed = $event"
        @menu-click="handleMenuClick"
      />
    </n-layout-sider>
    <n-layout class="admin-main">
      <n-layout-header bordered class="admin-header">
        <n-space align="center">
          <n-text>{{ username }}</n-text>
          <n-button text @click="handleLogout">
            <template #icon>
              <n-icon><LogOutOutline /></n-icon>
            </template>
          </n-button>
        </n-space>
      </n-layout-header>
      <n-layout-content class="admin-content">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<style scoped>
.admin-shell {
  height: 100vh;
  width: 100%;
}

.admin-sider {
  height: 100vh;
}

.admin-main {
  height: 100vh;
  min-width: 0;
}

.admin-header {
  height: 56px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
}

.admin-content {
  height: calc(100vh - 56px);
  padding: 24px;
  overflow: auto;
}
</style>
