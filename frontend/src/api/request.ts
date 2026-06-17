import axios from 'axios'
import router from '@/router'

const request = axios.create({
  baseURL: '',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let redirecting = false
request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !redirecting) {
      // 路由级别跳转而非整页刷新，保留应用状态并避免重渲染全部组件；
      // 同一时刻只触发一次，避免多个并发请求都触发重定向。
      redirecting = true
      localStorage.removeItem('admin-token')
      router.push('/login').finally(() => {
        setTimeout(() => { redirecting = false }, 1000)
      })
    }
    return Promise.reject(error)
  }
)

export default request
