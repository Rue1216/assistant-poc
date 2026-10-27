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
    <nav class="breadcrumb" aria-label="麵包屑">
      <span>顧客經營</span><span aria-hidden="true">/</span><span>顧客名單</span>
    </nav>
    <h1>顧客名單</h1>

    <form class="search" @submit.prevent="open(inputId)">
      <label class="sr-only" for="customer-id">輸入顧客編號</label>
      <input id="customer-id" v-model="inputId" class="input" type="text" placeholder="輸入顧客編號，例如 C001" />
      <button type="submit" class="btn btn--primary">查詢</button>
    </form>

    <p v-if="loading" class="muted">載入中…</p>
    <ul v-else class="card list">
      <li v-for="c in customers" :key="c.customerId">
        <button type="button" class="row" @click="open(c.customerId)">
          <span class="avatar" aria-hidden="true">{{ c.name.charAt(0) }}</span>
          <span class="name">{{ c.name }}</span>
          <span class="id">{{ c.customerId }}</span>
          <span class="seg"><span class="badge badge--published badge--plain">{{ c.segment }}</span></span>
          <span v-if="c.accessLevel === 'LIMITED'" class="badge badge--scheduled badge--plain">基本輪廓</span>
          <svg class="chevron" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 18l6-6-6-6" /></svg>
        </button>
      </li>
    </ul>
  </section>
</template>

<style scoped>
h1 {
  font-size: var(--text-2xl);
  color: var(--color-text-strong);
  margin-bottom: var(--space-5);
}
.search {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  max-width: 480px;
  margin-bottom: var(--space-5);
}
.muted {
  color: var(--color-text-muted);
}
.list {
  max-width: 880px;
  list-style: none;
  padding: var(--space-1) 0;
}
.list li + li {
  border-top: 1px solid var(--color-border);
}
.row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}
.row:hover {
  background: var(--color-surface-alt);
}
.name {
  font-weight: 600;
  color: var(--color-text-strong);
  min-width: 72px;
}
.id {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  width: 56px;
}
.seg {
  flex: 1;
}
.chevron {
  color: var(--color-text-subtle);
}
.row:hover .chevron {
  color: var(--color-primary);
}
</style>
