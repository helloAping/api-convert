<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { login } from '@/api/auth'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const form = ref({ username: 'admin', password: 'admin123' })

async function handleLogin() {
  loading.value = true
  try {
    const res = await login(form.value.username, form.value.password)
    localStorage.setItem('admin-token', res.data.data.token)
    localStorage.setItem('admin-username', res.data.data.username)
    message.success('登录成功')
    router.push('/')
  } catch {
    message.error('用户名或密码错误')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <n-layout style="height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--n-color-body)">
    <n-card title="API-Convert 管理后台" style="width: 400px">
      <n-form :model="form" @submit.prevent="handleLogin">
        <n-form-item label="用户名">
          <n-input v-model:value="form.username" placeholder="admin" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="form.password" type="password" placeholder="请输入密码" />
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="handleLogin">
          登录
        </n-button>
      </n-form>
    </n-card>
  </n-layout>
</template>
