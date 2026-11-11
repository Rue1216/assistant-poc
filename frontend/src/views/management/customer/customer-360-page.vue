<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { apiClient, ApiError } from '@/utils/api-client'
import type { CustomerDto, RealTimeCustomerDto } from '@/beans/customer-bean'
import AssistantLauncher from '@/components/ai-assistant/assistant-launcher.vue'
import AssetCards from '@/views/management/customer/components/asset-cards.vue'
import BehaviorCards from '@/views/management/customer/components/behavior-cards.vue'
import IntentCards from '@/views/management/customer/components/intent-cards.vue'
import OpsCard from '@/views/management/customer/components/ops-card.vue'
import ProfileCards from '@/views/management/customer/components/profile-cards.vue'
import '@/assets/styles/customer-360.css'

interface CustomerDetail {
  certNo: string
  accessLevel: 'FULL' | 'LIMITED'
  name: string
  segment: string
}

const props = defineProps<{ certNo: string }>()
const router = useRouter()

const customer = ref<CustomerDetail | null>(null)
const data = ref<CustomerDto | null>(null)
const realTime = ref<RealTimeCustomerDto | null>(null)
const notFound = ref(false)
const stopUse = ref(false)
const dataFailed = ref(false)
const loading = ref(true)

const path = (id: string, suffix = '') => `/mock/customers/${encodeURIComponent(id)}${suffix}`

const flags = computed(() => {
  const rt = realTime.value
  if (!rt) return []
  const list: { key: string; label: string; note: string }[] = rt.customerFlaggingList.map((f) => ({
    key: f.type,
    label: f.label,
    note: f.expiryDate ? `至 ${f.expiryDate}` : '',
  }))
  if (rt.attentionCustomer) list.push({ key: 'ATTENTION', label: '應注意名單', note: '' })
  if (rt.specialCareCustomer) list.push({ key: 'SPECIAL_CARE', label: '特殊客群', note: '' })
  return list
})

function confirmStopUse() {
  router.push('/customer')
}

watch(
  () => props.certNo,
  async (id) => {
    const current = () => id === props.certNo // ignore a response that arrives after navigating to another customer
    loading.value = true
    notFound.value = false
    stopUse.value = false
    dataFailed.value = false
    customer.value = null
    data.value = null
    realTime.value = null
    try {
      const cip = await apiClient.get<{ stopUseCustomerDataFlag: 'Y' | 'N' }>(path(id, '/cip'), {
        meta: { silentCodes: ['AI_CUSTOMER_NOT_FOUND'] },
      })
      if (!current()) return
      if (cip.data.stopUseCustomerDataFlag === 'Y') {
        stopUse.value = true
        return
      }
      const res = await apiClient.get<CustomerDetail>(path(id), { meta: { silentCodes: ['AI_CUSTOMER_NOT_FOUND'] } })
      if (!current()) return
      customer.value = res.data
      if (res.data.accessLevel !== 'FULL') return
      try {
        const [d, rt] = await Promise.all([
          apiClient.get<CustomerDto>(path(id, '/360')),
          apiClient.get<RealTimeCustomerDto>(path(id, '/real-time')),
        ])
        if (!current()) return
        data.value = d.data
        realTime.value = rt.data
      } catch {
        if (current()) dataFailed.value = true
      }
    } catch (e) {
      if (!current()) return
      if (e instanceof ApiError && e.code === 'AI_CUSTOMER_NOT_FOUND') notFound.value = true
    } finally {
      if (current()) loading.value = false
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
      <span v-if="data" class="c360-topbar__time">資料日期 {{ data.dataDate }}</span>
    </div>

    <p v-if="loading" class="c360-loading">載入中…</p>
    <p v-else-if="notFound" class="c360-notfound">查無此顧客或您沒有查看權限</p>
    <div v-else-if="stopUse" class="card c360-notfound" role="alertdialog" aria-modal="true" aria-labelledby="c360-stop-use-msg">
      <p id="c360-stop-use-msg">此為停止蒐集利用個資顧客，故無法查詢</p>
      <button class="btn btn--primary" type="button" @click="confirmStopUse">確認</button>
    </div>

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
              <span class="c360-namecard__id">{{ customer.certNo }}</span>
            </div>
          </div>
          <template v-if="data">
            <div v-if="realTime?.profilePhoto.tags.length" class="c360-tags" aria-label="分群標籤">
              <span v-for="t in realTime.profilePhoto.tags" :key="t" class="c360-tag">{{ t }}</span>
            </div>
            <div v-if="flags.length" class="c360-tags" aria-label="顧客註記">
              <span v-for="f in flags" :key="f.key" class="c360-tag c360-tag--muted">{{ f.label }}<template v-if="f.note">（{{ f.note }}）</template></span>
            </div>
            <dl class="c360-kv">
              <div><dt>年齡</dt><dd>{{ data.customerProfile.age }}</dd></div>
              <div><dt>主要往來分行</dt><dd>{{ data.customerProfile.primaryBranch }}</dd></div>
              <div><dt>理專</dt><dd>{{ data.customerProfile.financialCounsultant }}</dd></div>
            </dl>
          </template>
        </section>

        <p v-if="customer.accessLevel === 'LIMITED'" class="card c360-limited">
          您的角色僅能查看基本資料，其他區塊不顯示。
        </p>
        <p v-else-if="dataFailed" class="card c360-notfound">無法載入 360 資料</p>
        <template v-else-if="data && realTime">
          <ops-card :management="data.customerManagement" :alerts="realTime.keyAlerts.items" />
          <asset-cards :aum="data.aum" :lum="data.lum" />
          <profile-cards :member-rank="data.memberRank" :wealth-potential="data.wealthPotential" :product-mix="data.productMix" />
          <intent-cards :asset-product="data.assetProduct" :liabilities-and-fx="data.liabilitiesAndFx" :account-relationship-depth="data.accountRelationshipDepth" />
          <behavior-cards :consumption="data.consumptionBehavior" :channel="data.channelBehavior" :digital="data.digitalBehavior" />
        </template>
      </div>

      <assistant-launcher :cert-no="certNo" />
    </template>
  </div>
</template>
