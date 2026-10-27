<script setup lang="ts">
import { useAuthStore } from '../useAuthStore'

const auth = useAuthStore()
</script>

<template>
  <section class="home">
    <h1>歡迎，{{ auth.user?.displayName }}</h1>
    <p class="muted">角色：{{ auth.user?.roleName }}</p>
    <div class="cards">
      <RouterLink class="card tile" to="/customer">
        <span class="tile__icon" aria-hidden="true">✦</span>
        <strong>顧客經營</strong>
        <span class="tile__desc">查看顧客清單與個人 360，使用 AI 小幫手</span>
      </RouterLink>
      <RouterLink v-if="auth.hasPermission('AI_CONFIG_READ')" class="card tile" to="/setting/prompt-management">
        <span class="tile__icon" aria-hidden="true">⚙</span>
        <strong>Prompt 管理</strong>
        <span class="tile__desc">管理小幫手的 System Prompt 與問題分類</span>
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
  gap: var(--space-1);
  width: 300px;
  padding: var(--space-5);
  text-decoration: none;
  color: var(--color-text);
  transition: border-color var(--duration-fast), transform var(--duration-fast);
}
.tile:hover {
  border-color: var(--green-300);
  transform: translateY(-2px);
}
.tile__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  margin-bottom: var(--space-2);
  border-radius: 10px;
  background: var(--gradient-ai);
  color: #fff;
  font-size: 18px;
}
.tile strong {
  font-size: var(--text-lg);
  color: var(--color-text-strong);
}
.tile__desc {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
</style>
