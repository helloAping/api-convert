<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useRouter, useRoute, type RouteRecordRaw } from 'vue-router'
import {
  SpeedometerOutline,
  ServerOutline,
  CubeOutline,
  KeyOutline,
  SettingsOutline,
  DocumentTextOutline,
  HomeOutline,
  LogOutOutline,
  MoonOutline,
  SunnyOutline,
  PersonCircleOutline,
  InformationCircleOutline,
} from '@vicons/ionicons5'
import { NIcon, NTag, NText, NDropdown, type DropdownOption } from 'naive-ui'
import AppSidebar from '@/components/AppSidebar.vue'
import { logout } from '@/api/auth'
import { getGatewayInfo } from '@/api/gatewayInfo'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const route = useRoute()
const collapsed = ref(localStorage.getItem('api-convert-sider-collapsed') === '1')
const username = ref(localStorage.getItem('admin-username') || 'admin')
const gatewayVersion = ref<string>('')
const { effectiveDark, toggle: toggleTheme } = useTheme()

const menuOptions = [
  { label: '控制台', key: '/', icon: SpeedometerOutline },
  { label: '渠道管理', key: '/channels', icon: ServerOutline },
  { label: '模型管理', key: '/models', icon: CubeOutline },
  { label: '网关密钥', key: '/api-keys', icon: KeyOutline },
  { label: '系统配置', key: '/system-config', icon: SettingsOutline },
  { label: '请求日志', key: '/request-logs', icon: DocumentTextOutline },
]

const routeMeta = computed(() => {
  const matched = route.matched.filter(r => r.meta?.title)
  const last = matched[matched.length - 1]
  return {
    title: (last?.meta?.title as string) || '',
    breadcrumbs: matched.map(r => ({ label: (r.meta?.title as string) || '', path: r.path })),
  }
})

function handleMenuClick(key: string) {
  router.push(key)
}

function toggleSider() {
  collapsed.value = !collapsed.value
  try {
    localStorage.setItem('api-convert-sider-collapsed', collapsed.value ? '1' : '0')
  } catch { /* ignore */ }
}

async function handleLogout() {
  try {
    await logout()
  } catch { /* ignore */ }
  localStorage.removeItem('admin-token')
  localStorage.removeItem('admin-username')
  router.push('/login')
}

const userMenuOptions: DropdownOption[] = [
  { label: '当前用户：' + username.value, key: 'user-info', disabled: true },
  { type: 'divider', key: 'divider-1' },
  { label: '主题：' + (effectiveDark.value ? '深色' : '浅色'), key: 'theme-toggle', icon: () => h(NIcon, null, { default: () => h(effectiveDark.value ? SunnyOutline : MoonOutline) }) },
  { label: '退出登录', key: 'logout', icon: () => h(NIcon, null, { default: () => h(LogOutOutline) }) },
]

function onUserMenuSelect(key: string | number) {
  if (key === 'logout') {
    handleLogout()
  } else if (key === 'theme-toggle') {
    toggleTheme()
  }
}

async function loadGatewayInfo() {
  try {
    const res = await getGatewayInfo()
    gatewayVersion.value = (res.data.data as { version?: string })?.version || ''
  } catch { /* not critical */ }
}

onMounted(loadGatewayInfo)
</script>

<template>
  <n-layout has-sider class="admin-shell">
    <n-layout-sider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="232"
      :collapsed="collapsed"
      class="admin-sider"
      :native-scrollbar="false"
    >
      <AppSidebar
        :menu-options="menuOptions"
        :active-key="route.path"
        :collapsed="collapsed"
        :version="gatewayVersion"
        @update:collapsed="toggleSider"
        @menu-click="handleMenuClick"
      />
    </n-layout-sider>
    <n-layout class="admin-main">
      <n-layout-header bordered class="admin-header">
        <div class="admin-header__left">
          <n-breadcrumb separator="›">
            <n-breadcrumb-item v-for="(crumb, idx) in routeMeta.breadcrumbs" :key="idx"
                               :clickable="idx < routeMeta.breadcrumbs.length - 1">
              <!-- 第一项（首页）渲染为 NTag 标签 + Home 图标；其他项保持普通文本链接 -->
              <n-tag v-if="idx === 0" :bordered="false" size="small" type="primary" round>
                <router-link v-if="idx < routeMeta.breadcrumbs.length - 1 && crumb.path" :to="crumb.path" class="crumb-tag-link">
                  <n-icon size="13" class="crumb-tag-icon"><HomeOutline /></n-icon>
                  <span>{{ crumb.label }}</span>
                </router-link>
                <span v-else class="crumb-tag-link">
                  <n-icon size="13" class="crumb-tag-icon"><HomeOutline /></n-icon>
                  <span>{{ crumb.label }}</span>
                </span>
              </n-tag>
              <router-link v-else-if="idx < routeMeta.breadcrumbs.length - 1 && crumb.path" :to="crumb.path">{{ crumb.label }}</router-link>
              <span v-else>{{ crumb.label }}</span>
            </n-breadcrumb-item>
          </n-breadcrumb>
        </div>
        <div class="admin-header__right">
          <n-tooltip :delay="300">
            <template #trigger>
              <n-button quaternary circle @click="toggleTheme" aria-label="切换主题">
                <template #icon>
                  <n-icon size="18">
                    <component :is="effectiveDark ? SunnyOutline : MoonOutline" />
                  </n-icon>
                </template>
              </n-button>
            </template>
            {{ effectiveDark ? '切换到浅色' : '切换到深色' }}
          </n-tooltip>
          <n-dropdown :options="userMenuOptions" trigger="click" @select="onUserMenuSelect">
            <n-button quaternary>
              <template #icon>
                <n-icon size="18"><PersonCircleOutline /></n-icon>
              </template>
              <span class="admin-header__username">{{ username }}</span>
            </n-button>
          </n-dropdown>
        </div>
      </n-layout-header>
      <n-layout-content class="admin-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
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
  background: var(--sider-bg);
  transition: background-color 0.2s ease;
}

.admin-main {
  height: 100vh;
  min-width: 0;
  background: var(--page-bg);
}

.admin-header {
  height: 56px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  background: var(--header-bg);
}

.admin-header__left,
.admin-header__right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.crumb-tag-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: inherit;
  text-decoration: none;
  line-height: 1;
}

.crumb-tag-icon {
  display: inline-flex;
  align-items: center;
}

.admin-header__username {
  font-size: 13px;
  color: var(--text-primary);
  margin-left: 4px;
}

.admin-content {
  height: calc(100vh - 56px);
  padding: 24px;
  overflow: auto;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>
