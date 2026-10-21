<script setup lang="ts">
import { useAuthStore } from '../useAuthStore'

const auth = useAuthStore()
</script>

<template>
  <section class="home">
    <h1>歡迎，{{ auth.user?.displayName }}</h1>
    <p class="muted">角色：{{ auth.user?.roleName }}</p>
    <div class="cards">
      <RouterLink class="tile" to="/customer">
        <strong>顧客經營</strong>
        <span>查看顧客清單與個人 360，使用 AI 小幫手</span>
      </RouterLink>
      <RouterLink v-if="auth.hasPermission('AI_CONFIG_READ')" class="tile" to="/setting/prompt-management">
        <strong>Prompt 管理</strong>
        <span>管理小幫手的 System Prompt 與問題分類</span>
      </RouterLink>
    </div>
  </section>
</template>

<style scoped>
h1 {
  font-size: var(--text-2xl);
  color: var(--color-text-strong);
}
.muted {
  color: var(--color-text-muted);
  margin-top: var(--space-1);
}
.cards {
  display: flex;
  gap: var(--space-4);
  margin-top: var(--space-6);
  flex-wrap: wrap;
}
.tile {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 280px;
  padding: var(--space-5);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  text-decoration: none;
  color: var(--color-text);
}
.tile:hover {
  border-color: var(--color-primary-border);
  box-shadow: var(--shadow-sm);
}
.tile strong {
  color: var(--color-primary-active);
}
.tile span {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
</style>
