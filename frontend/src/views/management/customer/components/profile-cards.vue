<script setup lang="ts">
import { computed } from 'vue'
import type { MemberRankData, ProductMix, RadarView } from '@/beans/customer-bean'
import { formatPercent, formatWan } from '@/utils/formatters'
import { TIERS, tierOf, type TierKey } from '@/views/management/customer/member-rank'
import PairBars from '@/views/management/customer/components/pair-bars.vue'
import RadarChart from '@/components/charts/radar-chart.vue'

const props = defineProps<{ memberRank: MemberRankData; wealthPotential: RadarView; productMix: ProductMix }>()

const AXIS: TierKey[] = ['NONE', 'NEW_RICH', 'ELITE', 'SUMMIT', 'PRIVATE']

const tier = computed(() => tierOf(props.memberRank.memberRankCode))
const isRelationship = computed(() => tier.value.key === 'RELATIONSHIP')
const currentIndex = computed(() => AXIS.indexOf(tier.value.key))
const steps = computed(() =>
  AXIS.map((key, i) => {
    const t = TIERS[key]
    return {
      key,
      name: key === 'NONE' ? '無' : t.name,
      threshold: t.threshold === null ? '—' : formatWan(t.threshold),
      cls: i < currentIndex.value ? 'is-done' : i === currentIndex.value ? 'is-current' : '',
    }
  }),
)
const progress = computed(() => Math.min(100, Math.max(0, Number(props.memberRank.progressPercent ?? 0))))

const next = computed(() => (tier.value.next ? TIERS[tier.value.next] : null))
const aum = computed(() => (props.memberRank.customerAum === null ? null : Number(props.memberRank.customerAum)))
const gap = computed(() => {
  const n = next.value
  if (!n || n.threshold === null || aum.value === null) return null
  return { next: n, short: Math.max(0, n.threshold - aum.value) }
})

const aumYield = computed(() => formatPercent(props.productMix.aumYield))
const mixRows = computed(() => [
  { label: '存貸比', customer: props.productMix.customerData.ldr, segment: props.productMix.segmentData.ldr },
  { label: '活存比', customer: props.productMix.customerData.casaAndWealthAumRatio[0], segment: props.productMix.segmentData.casaAndWealthAumRatio[0] },
  { label: '理財 AuM 比', customer: props.productMix.customerData.casaAndWealthAumRatio[1], segment: props.productMix.segmentData.casaAndWealthAumRatio[1] },
])
</script>

<template>
  <section class="card dash-card c360-e" data-area="E" aria-label="會員等級">
    <header class="dash-card__head"><h2 class="dash-card__title">會員等級</h2><span class="dash-card__meta">依 AuM 判定</span></header>
    <div class="dash-card__body">
      <p v-if="isRelationship" class="tier__next">目前為 <strong>{{ tier.name }}</strong>，不適用 AuM 升降等進度</p>
      <template v-else>
        <ol class="tier" :aria-label="`會員等級：目前為${tier.name}`">
          <li v-for="s in steps" :key="s.key" :class="['tier__step', s.cls]" :aria-current="s.cls === 'is-current' ? 'step' : undefined">
            <span class="tier__dot" aria-hidden="true"></span><strong>{{ s.name }}</strong><span>{{ s.threshold }}</span>
          </li>
          <span class="tier__fill" :style="{ '--tier-progress': `${progress}%` }" aria-hidden="true"></span>
        </ol>
        <p v-if="gap" class="tier__next">
          <template v-if="tier.key === 'NONE'">目前為 <strong>{{ tier.name }}</strong>，</template>距 <strong>{{ gap.next.name }}</strong> 尚差 AuM <strong>{{ formatWan(gap.short) }}</strong>（門檻 {{ formatWan(gap.next.threshold!) }}<template v-if="aum !== null">，目前 {{ formatWan(aum) }}</template>）
        </p>
        <p v-else-if="memberRank.upgradeOrDownGradeAlert" class="tier__next">{{ memberRank.upgradeOrDownGradeAlert }}</p>
        <p v-else-if="tier.key === 'NONE'" class="tier__next">目前為 <strong>{{ tier.name }}</strong></p>
        <div v-if="tier.benefits.length || (next && next.benefits.length)" class="perks">
          <div v-if="tier.benefits.length"><h3>目前權益・{{ tier.name }}</h3><ul><li v-for="b in tier.benefits" :key="b">{{ b }}</li></ul></div>
          <div v-if="next && next.benefits.length"><h3>升等後・{{ next.name }}</h3><ul><li v-for="b in next.benefits" :key="b">{{ b }}</li></ul></div>
        </div>
      </template>
    </div>
  </section>
  <section class="card dash-card c360-f" data-area="F" aria-label="財富潛力">
    <header class="dash-card__head"><h2 class="dash-card__title">財富潛力</h2><span class="dash-card__meta">PR 值・同客群比較</span></header>
    <div class="dash-card__body">
      <radar-chart
        :labels="wealthPotential.labels"
        :customer="wealthPotential.customerData.map((p) => p.rank)"
        :segment="wealthPotential.segmentData.map((p) => p.rank)"
        ariaLabel="財富潛力：本顧客 與 同客群平均"
      />
      <ul class="legend"><li><span class="legend__swatch s1"></span>本顧客</li><li><span class="legend__swatch s2"></span>同客群平均</li></ul>
    </div>
  </section>
  <section class="card dash-card c360-g" data-area="G" aria-label="產品結構">
    <header class="dash-card__head"><h2 class="dash-card__title">產品結構</h2><span class="dash-card__meta">本顧客 vs 客群</span></header>
    <div class="dash-card__body">
      <div class="kpi">
        <span class="kpi__label">理財顧客存量收益率</span>
        <span class="kpi__value">{{ aumYield }}</span>
      </div>
      <pair-bars :rows="mixRows" ariaLabel="存貸比、活存比、理財 AuM 比：本顧客與客群" />
      <ul class="legend"><li><span class="legend__swatch s1"></span>本顧客</li><li><span class="legend__swatch s2"></span>客群</li></ul>
    </div>
  </section>
</template>
