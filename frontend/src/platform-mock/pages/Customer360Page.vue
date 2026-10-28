<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { apiClient, ApiError } from '../apiClient'
import AssistantLauncher from '../../features/ai-assistant/components/AssistantLauncher.vue'
import AssetCards from './customer360/AssetCards.vue'
import BehaviorCards from './customer360/BehaviorCards.vue'
import IntentCards from './customer360/IntentCards.vue'
import OpsCard from './customer360/OpsCard.vue'
import ProfileCards from './customer360/ProfileCards.vue'
import '../styles/customer-360.css'

interface CustomerDetail {
  certNo: string
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

const props = defineProps<{ certNo: string }>()

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
const consent = computed(() => {
  const value = customer.value?.marketingConsent
  if (value === undefined) return '—'
  return value ? '同意' : '不同意'
})

watch(
  () => props.certNo,
  async (id) => {
    loading.value = true
    notFound.value = false
    customer.value = null
    try {
      const res = await apiClient.get<CustomerDetail>(`/mock/customers/${encodeURIComponent(id)}`, {
        meta: { silentCodes: ['AI_CUSTOMER_NOT_FOUND'] },
      })
      // ignore a response that arrives after navigating to another customer
      if (id === props.certNo) customer.value = res.data
    } catch (e) {
      if (id !== props.certNo) return
      if (e instanceof ApiError && e.code === 'AI_CUSTOMER_NOT_FOUND') notFound.value = true
    } finally {
      if (id === props.certNo) loading.value = false
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="c360">
    <div class="c360-topbar">
      <RouterLink class="c360-back" to="/customer">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 18l-6-6 6-6" /></svg>返回顧客名單
      </RouterLink>
      <nav class="breadcrumb" aria-label="麵包屑">
        <RouterLink to="/customer">顧客經營</RouterLink><span aria-hidden="true">/</span><span>個人 360</span>
      </nav>
      <span class="c360-topbar__time">
        <template v-if="customer?.accessLevel === 'FULL'">圖表為示意資料・</template>資料更新 2026/10/20 08:00
      </span>
    </div>

    <p v-if="loading" class="c360-loading">載入中…</p>
    <p v-else-if="notFound" class="c360-notfound">查無此顧客或您沒有查看權限</p>

    <template v-else-if="customer">
      <div class="c360-grid">
        <section class="card c360-a c360-namecard" data-area="A" aria-label="顧客名片">
          <div class="c360-namecard__head">
            <div class="c360-namecard__avatar" aria-hidden="true">{{ customer.name.charAt(0) }}</div>
            <div class="c360-namecard__who">
              <div class="c360-namecard__name-row">
                <h1 class="c360-namecard__name">{{ customer.name }}</h1>
                <span class="badge badge--published badge--plain">{{ customer.segment }}</span>
              </div>
              <span class="c360-namecard__id">{{ customer.certNo }}・往來 {{ customer.relationshipYears }} 年</span>
            </div>
          </div>
          <div v-if="customer.tags?.length" class="c360-tags" aria-label="分群標籤">
            <span v-for="t in customer.tags" :key="t" class="c360-tag">{{ t }}</span>
          </div>
          <dl class="c360-kv">
            <div><dt>風險屬性</dt><dd>{{ customer.riskProfile ?? '—' }}</dd></div>
            <div><dt>偏好管道</dt><dd>{{ customer.preferredChannel ?? '—' }}</dd></div>
            <div><dt>行銷同意</dt><dd>{{ consent }}</dd></div>
            <div><dt>資產級距</dt><dd>{{ tier }}</dd></div>
          </dl>
        </section>

        <p v-if="customer.accessLevel === 'LIMITED'" class="card c360-limited">
          您的角色僅能查看基本資料，其他區塊不顯示。
        </p>
        <template v-else>
          <OpsCard />
          <AssetCards />
          <ProfileCards />
          <IntentCards />
          <BehaviorCards />
        </template>
      </div>

      <AssistantLauncher :cert-no="certNo" />
    </template>
  </div>
</template>
