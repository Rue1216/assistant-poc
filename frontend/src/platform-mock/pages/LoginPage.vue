<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../apiClient'
import { useAuthStore } from '../useAuthStore'

const DEMO_ACCOUNTS = [
  'hq.viewer',
  'region.north',
  'branch.mgr',
  'fc.supervisor',
  'fc.officer',
  'ao.supervisor',
  'ao.officer',
  'backoffice.admin',
  'it.admin',
]

const auth = useAuthStore()
const router = useRouter()
const username = ref('')
const password = ref('poc1234')
const submitting = ref(false)
const loginError = ref('')

async function onSubmit() {
  if (submitting.value) return
  submitting.value = true
  loginError.value = ''
  try {
    await auth.login(username.value.trim(), password.value)
    await router.push('/')
  } catch (e) {
    // 401 is silent in apiClient and answered here; every other error was already toasted
    if (e instanceof ApiError && e.status === 401) loginError.value = '帳號或密碼錯誤'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <form class="card" @submit.prevent="onSubmit">
      <h1>行銷人員顧問小幫手 POC</h1>
      <p class="hint">模擬平台登入（示範帳號密碼皆為 poc1234）</p>

      <label>
        <span>示範帳號</span>
        <select v-model="username">
          <option value="" disabled>請選擇示範帳號</option>
          <option v-for="a in DEMO_ACCOUNTS" :key="a" :value="a">{{ a }}</option>
        </select>
      </label>
      <label>
        <span>帳號</span>
        <input v-model="username" type="text" autocomplete="username" required />
      </label>
      <label>
        <span>密碼</span>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="loginError" class="error" role="alert">{{ loginError }}</p>
      <button class="primary" type="submit" :disabled="submitting">登入</button>
    </form>
  </div>
</template>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gradient-soft);
}
.card {
  width: 360px;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-8);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
}
h1 {
  font-size: var(--text-xl);
  color: var(--color-text-strong);
}
.hint {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
.error {
  font-size: var(--text-sm);
  color: var(--red-600);
}
label {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
input,
select {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}
input:focus,
select:focus {
  outline: 2px solid var(--color-focus-ring);
}
.primary {
  padding: var(--space-3);
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-text-on-primary);
  cursor: pointer;
}
.primary:hover:not(:disabled) {
  background: var(--color-primary-hover);
}
.primary:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
