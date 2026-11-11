<script setup lang="ts">
import { formatPercent } from '@/utils/formatters'

defineProps<{
  rows: { label: string; customer: number; segment: number }[]
  ariaLabel: string
}>()

const width = (v: number) => `${Math.min(100, Math.max(0, v))}%`
</script>

<template>
  <ul class="gbars" :aria-label="ariaLabel">
    <li v-for="row in rows" :key="row.label">
      <span class="gbars__label">{{ row.label }}</span>
      <span class="gbars__bars">
        <span class="gbars__row" :title="`本顧客｜${row.label} ${formatPercent(row.customer)}`">
          <span class="gbars__bar s1" :style="{ width: width(row.customer) }"></span>
          <span class="gbars__value">{{ formatPercent(row.customer) }}</span>
        </span>
        <span class="gbars__row" :title="`客群｜${row.label} ${formatPercent(row.segment)}`">
          <span class="gbars__bar s2" :style="{ width: width(row.segment) }"></span>
          <span class="gbars__value">{{ formatPercent(row.segment) }}</span>
        </span>
      </span>
    </li>
  </ul>
</template>
