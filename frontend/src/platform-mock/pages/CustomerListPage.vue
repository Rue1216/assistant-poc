<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiClient } from '../apiClient'

interface CustomerSummary {
  customerId: string
  name: string
  segment: string
  accessLevel: 'FULL' | 'LIMITED'
}

const router = useRouter()
const customers = ref<CustomerSummary[]>([])
const loading = ref(true)
const inputId = ref('')

onMounted(async () => {
  try {
    const res = await apiClient.get<CustomerSummary[]>('/mock/customers')
    customers.value = res.data
  } catch {
    // apiClient already toasted the error
  } finally {
    loading.value = false
  }
})

function open(id: string) {
  const trimmed = id.trim()
  if (trimmed) void router.push(`/customer/${encodeURIComponent(trimmed)}`)
}
</script>

<template>
  <section>
    <h1>顧客經營</h1>

    <form class="search" @submit.prevent="open(inputId)">
      <label for="customer-id">輸入顧客編號</label>
      <input id="customer-id" v-model="inputId" type="text" placeholder="例如 C001" />
      <button type="submit" class="primary">查詢</button>
    </form>

    <p v-if="loading" class="muted">載入中…</p>
    <ul v-else class="list">
      <li v-for="c in customers" :key="c.customerId">
        <button type="button" class="row" @click="open(c.customerId)">
          <span class="id">{{ c.customerId }}</span>
          <span class="name">{{ c.name }}</span>
          <span class="seg">{{ c.segment }}</span>
          <span v-if="c.accessLevel === 'LIMITED'" class="badge">基本輪廓</span>
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
h1 {
  font-size: var(--text-2xl);
  color: var(--color-text-strong);
  margin-bottom: var(--space-4);
}
.search {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
}
input {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}
input:focus {
  outline: 2px solid var(--color-focus-ring);
}
.primary {
  padding: var(--space-2) var(--space-4);
  border: 0;
  border-radius: var(--radius-md);
  background: var(--color-primary);
  color: var(--color-text-on-primary);
  cursor: pointer;
}
.primary:hover {
  background: var(--color-primary-hover);
}
.muted {
  color: var(--color-text-muted);
}
.list {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  text-align: left;
}
.row:hover {
  border-color: var(--color-primary-border);
  background: var(--color-surface-alt);
}
.id {
  font-family: var(--font-mono);
  color: var(--color-text-muted);
  width: 56px;
}
.name {
  font-weight: 600;
  color: var(--color-text-strong);
  min-width: 96px;
}
.seg {
  color: var(--color-text-muted);
  flex: 1;
}
.badge {
  font-size: var(--text-xs);
  padding: 2px var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--amber-50);
  color: var(--amber-700);
}
</style>
