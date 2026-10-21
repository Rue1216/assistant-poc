<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { apiClient, ApiError } from '../apiClient'
import AssistantLauncher from '../../features/ai-assistant/components/AssistantLauncher.vue'

interface CustomerDetail {
  customerId: string
  accessLevel: 'FULL' | 'LIMITED'
  name: string
  segment: string
  relationshipYears: number
  riskProfile?: string
  marketingConsent?: boolean
  preferredChannel?: string
  tags?: string[]
  assetAmount?: number
}

const props = defineProps<{ customerId: string }>()

const customer = ref<CustomerDetail | null>(null)
const notFound = ref(false)
const loading = ref(true)

/** Coarse tier so the card never shows an exact amount. */
function assetTier(amount: number | undefined): string {
  if (amount === undefined || amount === null) return '—'
  if (amount < 10_000_000) return '1,000 萬以下'
  if (amount < 30_000_000) return '1,000 萬–3,000 萬'
  return '3,000 萬以上'
}

const tier = computed(() => assetTier(customer.value?.assetAmount))

watch(
  () => props.customerId,
  async (id) => {
    loading.value = true
    notFound.value = false
    customer.value = null
    try {
      const res = await apiClient.get<CustomerDetail>(`/mock/customers/${encodeURIComponent(id)}`, {
        meta: { silentCodes: ['AI_CUSTOMER_NOT_FOUND'] },
      })
      // ignore a response that arrives after navigating to another customer
      if (id === props.customerId) customer.value = res.data
    } catch (e) {
      if (id !== props.customerId) return
      if (e instanceof ApiError && e.code === 'AI_CUSTOMER_NOT_FOUND') notFound.value = true
    } finally {
      if (id === props.customerId) loading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <section class="page">
    <nav class="crumbs" aria-label="breadcrumb">
      <RouterLink to="/customer">顧客經營</RouterLink>
      <span> / </span>
      <span>個人 360</span>
    </nav>

    <p v-if="loading" class="muted">載入中…</p>
    <p v-else-if="notFound" class="notfound">查無此顧客或您沒有查看權限</p>

    <template v-else-if="customer">
      <div class="card profile">
        <div class="avatar">{{ customer.name.charAt(0) }}</div>
        <div class="info">
          <h1>{{ customer.name }}</h1>
          <dl class="facts">
            <div>
              <dt>客群</dt>
              <dd>{{ customer.segment }}</dd>
            </div>
            <div>
              <dt>往來年資</dt>
              <dd>{{ customer.relationshipYears }} 年</dd>
            </div>
            <div>
              <dt>資產級距</dt>
              <dd>{{ tier }}</dd>
            </div>
          </dl>
          <ul v-if="customer.tags?.length" class="tags">
            <li v-for="t in customer.tags" :key="t">{{ t }}</li>
          </ul>
        </div>
      </div>

      <div class="card basic">
        <h2>基本資料</h2>
        <dl class="grid">
          <div>
            <dt>風險屬性</dt>
            <dd>{{ customer.riskProfile ?? '—' }}</dd>
          </div>
          <div>
            <dt>行銷同意</dt>
            <dd>{{ customer.marketingConsent === undefined ? '—' : customer.marketingConsent ? '同意' : '不同意' }}</dd>
          </div>
          <div>
            <dt>偏好管道</dt>
            <dd>{{ customer.preferredChannel ?? '—' }}</dd>
          </div>
        </dl>
      </div>

      <AssistantLauncher :customer-id="customerId" />
    </template>
  </section>
</template>

<style scoped>
.crumbs {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  margin-bottom: var(--space-4);
}
.crumbs a {
  color: var(--color-primary-active);
  text-decoration: none;
}
.muted {
  color: var(--color-text-muted);
}
.notfound {
  padding: var(--space-6);
  background: var(--red-50);
  border: 1px solid var(--red-200);
  border-radius: var(--radius-md);
  color: var(--red-700);
}
.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  margin-bottom: var(--space-4);
}
.profile {
  display: flex;
  gap: var(--space-5);
  align-items: center;
}
.avatar {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-pill);
  background: var(--gradient-ai);
  color: var(--color-text-on-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xl);
}
h1 {
  font-size: var(--text-xl);
  color: var(--color-text-strong);
}
h2 {
  font-size: var(--text-lg);
  color: var(--color-text-strong);
  margin-bottom: var(--space-3);
}
dl {
  margin: 0;
}
dt {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}
dd {
  margin: 0;
  color: var(--color-text-strong);
}
.facts {
  display: flex;
  gap: var(--space-6);
  margin-top: var(--space-2);
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-4);
}
.tags {
  list-style: none;
  padding: 0;
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  margin-top: var(--space-3);
}
.tags li {
  font-size: var(--text-xs);
  padding: 2px var(--space-2);
  border-radius: var(--radius-pill);
  background: var(--color-primary-subtle);
  color: var(--color-primary-active);
  border: 1px solid var(--color-primary-border);
}
</style>
