<script setup lang="ts">
import { computed } from 'vue'
import type { RadarView } from '@/beans/customer-bean'
import RadarChart from '@/components/charts/radar-chart.vue'

const props = defineProps<{ assetProduct: RadarView; liabilitiesAndFx: RadarView; accountRelationshipDepth: RadarView }>()

const ranks = (v: RadarView) => v.customerData.map((p) => p.rank)
const segmentRanks = (v: RadarView) => v.segmentData.map((p) => p.rank)
const highlights = (v: RadarView) => v.customerData.map((p) => p.highlight === true)

/** Strongest and second-strongest axis of a view, as "label score". */
function topTwo(v: RadarView): { first: string; second: string | null } {
  const order = v.labels.map((label, i) => ({ label, rank: v.customerData[i]?.rank ?? 0 })).sort((a, b) => b.rank - a.rank)
  const text = (e: { label: string; rank: number } | undefined) => (e ? `${e.label} ${e.rank}` : null)
  return { first: text(order[0]) ?? '—', second: text(order[1]) }
}

const assetTop = computed(() => topTwo(props.assetProduct))
const liabilityTop = computed(() => topTwo(props.liabilitiesAndFx))
</script>

<template>
  <section class="card dash-card c360-h" data-area="H" aria-label="資產類產品意圖">
    <header class="dash-card__head"><h2 class="dash-card__title">資產類產品意圖</h2><span class="dash-card__meta">意圖分數 0–100</span></header>
    <div class="dash-card__body">
      <radar-chart :labels="assetProduct.labels" :customer="ranks(assetProduct)" :segment="segmentRanks(assetProduct)" :highlight="highlights(assetProduct)" :ariaLabel="`資產類產品意圖，最強為${assetTop.first} 分`" />
      <p class="intent-top"><span class="intent-top__dot" aria-hidden="true"></span>最強意圖<strong>{{ assetTop.first }}</strong><span v-if="assetTop.second">次強：{{ assetTop.second }}</span></p>
    </div>
  </section>
  <section class="card dash-card c360-i" data-area="I" aria-label="負債與外匯類產品意圖">
    <header class="dash-card__head"><h2 class="dash-card__title">負債與外匯類產品意圖</h2><span class="dash-card__meta">需求強度 0–100</span></header>
    <div class="dash-card__body">
      <radar-chart :labels="liabilitiesAndFx.labels" :customer="ranks(liabilitiesAndFx)" :segment="segmentRanks(liabilitiesAndFx)" :highlight="highlights(liabilitiesAndFx)" :ariaLabel="`負債與外匯類產品意圖，最強為${liabilityTop.first} 分`" />
      <p class="intent-top"><span class="intent-top__dot" aria-hidden="true"></span>最強需求<strong>{{ liabilityTop.first }}</strong><span v-if="liabilityTop.second">次強：{{ liabilityTop.second }}</span></p>
    </div>
  </section>
  <section class="card dash-card c360-j" data-area="J" aria-label="帳戶往來深度">
    <header class="dash-card__head"><h2 class="dash-card__title">帳戶往來深度</h2><span class="dash-card__meta">近 6 個月</span></header>
    <div class="dash-card__body">
      <radar-chart :labels="accountRelationshipDepth.labels" :customer="ranks(accountRelationshipDepth)" :segment="segmentRanks(accountRelationshipDepth)" ariaLabel="帳戶往來深度：本顧客 與 同客群平均" />
      <ul class="legend"><li><span class="legend__swatch s1"></span>本顧客</li><li><span class="legend__swatch s2"></span>同客群平均</li></ul>
    </div>
  </section>
</template>
