<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { FormRules, FormInst } from 'naive-ui'
import { login } from '@/api/auth'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const formRef = ref<FormInst | null>(null)
const form = ref({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false) return

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
  <div class="login-wrapper">
    <div class="login-bg" />
    <n-card class="login-card" :bordered="false">
      <div class="login-header">
        <h1 class="login-title">API-Convert</h1>
        <p class="login-subtitle">智能网关管理后台</p>
      </div>
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" @submit.prevent="handleLogin">
        <n-form-item label="用户名" path="username">
          <n-input
            v-model:value="form.username"
            placeholder="请输入用户名"
            autofocus
            :input-props="{ autocomplete: 'username' }"
          />
        </n-form-item>
        <n-form-item label="密码" path="password">
          <n-input
            v-model:value="form.password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
            :input-props="{ autocomplete: 'current-password' }"
          />
        </n-form-item>
        <n-button type="primary" attr-type="submit" block :loading="loading" size="large">
          登 录
        </n-button>
      </n-form>
    </n-card>
  </div>
</template>

<style scoped>
.login-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  opacity: 0.85;
}

.login-card {
  position: relative;
  width: 400px;
  padding: 32px 24px 24px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.12);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #333;
  letter-spacing: 1px;
}

.login-subtitle {
  margin: 8px 0 0;
  font-size: 14px;
  color: #999;
}
</style>
