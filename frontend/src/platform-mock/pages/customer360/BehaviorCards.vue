<script setup lang="ts">
import { computed } from 'vue'
import type { RadarView } from '../../customer360Types'
import RadarChart from './RadarChart.vue'

const props = defineProps<{ consumption: RadarView; channel: RadarView; digital: RadarView }>()

const ranks = (v: RadarView) => v.customerData.map((p) => p.rank)
const segmentRanks = (v: RadarView) => v.segmentData.map((p) => p.rank)

const topChannel = computed(() => {
  const order = props.channel.labels.map((label, i) => ({ label, rank: props.channel.customerData[i]?.rank ?? 0 })).sort((a, b) => b.rank - a.rank)
  return order[0]?.label ?? '—'
})
const digitalInfo = computed(() =>
  props.digital.labels
    .map((label, i) => {
      const info = props.digital.customerData[i]?.info
      return info ? `${label} ${info}` : null
    })
    .filter((s): s is string => s !== null)
    .join('・'),
)
</script>

<template>
  <section class="card dash-card c360-k" data-area="K" aria-label="消費行為與場景">
    <header class="dash-card__head"><h2 class="dash-card__title">消費行為與場景</h2><span class="dash-card__meta">近 3 個月・佔比指數</span></header>
    <div class="dash-card__body">
      <RadarChart :labels="consumption.labels" :customer="ranks(consumption)" :segment="segmentRanks(consumption)" ariaLabel="消費行為與場景（近 3 個月）" />
      <div v-if="consumption.tagList?.length" class="persona"><span class="persona__label">消費族群</span><span v-for="t in consumption.tagList" :key="t" class="c360-tag">{{ t }}</span></div>
    </div>
  </section>
  <section class="card dash-card c360-l" data-area="L" aria-label="通路行為">
    <header class="dash-card__head"><h2 class="dash-card__title">通路行為</h2><span class="dash-card__meta">近 6 個月</span></header>
    <div class="dash-card__body">
      <RadarChart :labels="channel.labels" :customer="ranks(channel)" :segment="segmentRanks(channel)" ariaLabel="通路行為：本顧客 與 客群平均" />
      <ul class="legend"><li><span class="legend__swatch s1"></span>本顧客</li><li><span class="legend__swatch s2"></span>客群平均</li></ul>
      <p class="dash-verdict">最常使用 <strong>{{ topChannel }}</strong></p>
    </div>
  </section>
  <section class="card dash-card c360-m" data-area="M" aria-label="數位行為">
    <header class="dash-card__head"><h2 class="dash-card__title">數位行為</h2><span class="dash-card__meta">近 6 個月・同客群比較</span></header>
    <div class="dash-card__body">
      <RadarChart :labels="digital.labels" :customer="ranks(digital)" :segment="segmentRanks(digital)" ariaLabel="數位行為：本顧客 與 同客群平均" />
      <ul class="legend"><li><span class="legend__swatch s1"></span>本顧客</li><li><span class="legend__swatch s2"></span>同客群平均</li></ul>
      <p v-if="digitalInfo" class="dash-verdict">{{ digitalInfo }}</p>
    </div>
  </section>
</template>
