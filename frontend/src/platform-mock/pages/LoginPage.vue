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
    <form class="card login" @submit.prevent="onSubmit">
      <div class="login__brand">
        <span class="app-header__logo" aria-hidden="true">✦</span>
        <div>
          <h1>行銷人員顧問小幫手 POC</h1>
          <p class="login__hint">模擬平台登入（示範帳號密碼皆為 poc1234）</p>
        </div>
      </div>

      <label class="field">
        <span class="field__label">示範帳號</span>
        <select v-model="username" class="input">
          <option value="" disabled>請選擇示範帳號</option>
          <option v-for="a in DEMO_ACCOUNTS" :key="a" :value="a">{{ a }}</option>
        </select>
      </label>
      <label class="field">
        <span class="field__label">帳號</span>
        <input v-model="username" class="input" type="text" autocomplete="username" required />
      </label>
      <label class="field">
        <span class="field__label">密碼</span>
        <input v-model="password" class="input" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="loginError" class="login__error" role="alert">{{ loginError }}</p>
      <button class="btn btn--primary login__submit" type="submit" :disabled="submitting">登入</button>
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
.login {
  width: 380px;
  max-width: calc(100% - 32px);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: var(--space-8);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-md);
}
.login__brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}
.login__brand .app-header__logo {
  flex: none;
  width: 40px;
  height: 40px;
  font-size: 18px;
}
h1 {
  font-size: var(--text-xl);
  color: var(--color-text-strong);
}
.login__hint {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
.login__error {
  font-size: var(--text-sm);
  color: var(--red-600);
}
.login__submit {
  height: 40px;
  margin-top: var(--space-2);
}
</style>
