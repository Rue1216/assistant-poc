<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from './useAuthStore'

const auth = useAuthStore()
const router = useRouter()

async function onLogout() {
  try {
    await auth.logout()
  } catch {
    // the failure toast is already shown by apiClient; local state is cleared anyway
  }
  await router.push('/login')
}
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">行銷人員顧問小幫手 POC</RouterLink>
      <nav class="nav">
        <RouterLink to="/">首頁</RouterLink>
        <RouterLink to="/customer">顧客經營</RouterLink>
        <RouterLink v-if="auth.hasPermission('AI_CONFIG_READ')" to="/setting/prompt-management">
          Prompt 管理
        </RouterLink>
      </nav>
      <div class="user">
        <span v-if="auth.user">{{ auth.user.displayName }}（{{ auth.user.roleName }}）</span>
        <button class="logout" type="button" @click="onLogout">登出</button>
      </div>
    </header>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}
.topbar {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  padding: 0 var(--space-6);
  height: 56px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}
.brand {
  font-weight: 600;
  color: var(--color-primary-active);
  text-decoration: none;
}
.nav {
  display: flex;
  gap: var(--space-4);
  flex: 1;
}
.nav a {
  color: var(--color-text);
  text-decoration: none;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
}
.nav a.router-link-exact-active,
.nav a.router-link-active:not([href='/']) {
  background: var(--color-primary-subtle);
  color: var(--color-primary-active);
}
.user {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
.logout {
  border: 1px solid var(--color-border-strong);
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: var(--space-1) var(--space-3);
  cursor: pointer;
}
.logout:hover {
  background: var(--color-surface-alt);
}
.content {
  padding: var(--space-6);
  max-width: 1200px;
  margin: 0 auto;
}
</style>
