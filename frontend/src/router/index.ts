import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'Dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'channels', name: 'Channels', component: () => import('@/views/channels/ChannelList.vue') },
        { path: 'models', name: 'Models', component: () => import('@/views/models/ModelList.vue') },
        { path: 'api-keys', name: 'ApiKeys', component: () => import('@/views/apiKeys/ApiKeyList.vue') },
        { path: 'request-logs', name: 'RequestLogs', component: () => import('@/views/requestLogs/RequestLogList.vue') },
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
