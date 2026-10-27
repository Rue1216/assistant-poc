<script setup lang="ts">
import { computed } from 'vue'
import { isNavigationFailure, NavigationFailureType, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './useAuthStore'
import { useTheme } from './useTheme'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { dark, toggle: toggleTheme } = useTheme()

const initials = computed(() => auth.user?.username.slice(0, 2).toUpperCase() ?? '')

async function onLogout() {
  // Leave the current page first so its guard can ask about unsaved edits (once: while loggingOut is
  // set the router does not bounce /login back to /); a veto stops the logout.
  auth.loggingOut = true
  let failure
  try {
    failure = await router.push('/login')
  } finally {
    auth.loggingOut = false
  }
  if (isNavigationFailure(failure, NavigationFailureType.aborted | NavigationFailureType.cancelled)) return
  try {
    await auth.logout()
  } catch {
    // the failure toast is already shown by apiClient; local state is cleared anyway
  }
  await router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <RouterLink class="app-header__brand" to="/"><span class="app-header__logo" aria-hidden="true">✦</span>平台</RouterLink>
    <div class="app-header__spacer"></div>
    <button
      class="app-header__icon-btn theme-toggle"
      type="button"
      :aria-pressed="dark"
      :aria-label="dark ? '切換淺色模式' : '切換深色模式'"
      @click="toggleTheme"
    >
      <svg class="theme-toggle__moon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" /></svg>
      <svg class="theme-toggle__sun" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></svg>
    </button>
    <span class="app-header__divider" aria-hidden="true"></span>
    <div v-if="auth.user" class="app-header__user">
      <span class="badge badge--info badge--plain">{{ auth.user.roleName }}</span>
      <span class="app-header__user-name">{{ auth.user.username }}</span>
      <span class="avatar" aria-hidden="true">{{ initials }}</span>
    </div>
    <button class="btn btn--ghost btn--sm logout" type="button" @click="onLogout">登出</button>
  </header>

  <!-- 個人 360 頁不顯示左側選單（同 prototype） -->
  <main v-if="route.meta.hideSidebar">
    <RouterView />
  </main>
  <div v-else class="app-shell">
    <nav class="app-sidebar" aria-label="主選單">
      <div class="app-sidebar__group">
        <div class="app-sidebar__heading">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20a6.5 6.5 0 0 1 13 0" /><path d="M16 4.5a3.5 3.5 0 0 1 0 7M21.5 20a6.5 6.5 0 0 0-4-6" /></svg>
          顧客經營
        </div>
        <ul class="app-sidebar__items">
          <li><RouterLink to="/customer">顧客名單</RouterLink></li>
        </ul>
      </div>
      <div v-if="auth.hasPermission('AI_CONFIG_READ')" class="app-sidebar__group">
        <div class="app-sidebar__heading">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" /></svg>
          系統管理
        </div>
        <ul class="app-sidebar__items">
          <li><RouterLink to="/setting/prompt-management">Prompt 管理</RouterLink></li>
        </ul>
      </div>
    </nav>
    <main class="app-main">
      <div class="app-page">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
.app-page {
  max-width: 1600px;
  margin: 0 auto;
  padding: var(--space-6) var(--space-6) 96px;
}
</style>
