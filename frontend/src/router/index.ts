import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { title: '首页' },
      children: [
        { path: '', name: 'Dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '控制台' } },
        { path: 'channels', name: 'Channels', component: () => import('@/views/channels/ChannelList.vue'), meta: { title: '渠道管理' } },
        { path: 'models', name: 'Models', component: () => import('@/views/models/ModelList.vue'), meta: { title: '模型管理' } },
        { path: 'api-keys', name: 'ApiKeys', component: () => import('@/views/apiKeys/ApiKeyList.vue'), meta: { title: '网关密钥' } },
        { path: 'system-config', name: 'SystemConfig', component: () => import('@/views/system/SystemConfigView.vue'), meta: { title: '系统配置' } },
        { path: 'request-logs', name: 'RequestLogs', component: () => import('@/views/requestLogs/RequestLogList.vue'), meta: { title: '请求日志' } },
      ],
    },
  ],
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('admin-token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/')
  } else {
    next()
  }
})

export default router
