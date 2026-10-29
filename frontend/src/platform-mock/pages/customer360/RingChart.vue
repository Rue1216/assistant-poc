<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    items: { label: string; ratio: number }[]
    fullScale?: number
    ariaLabel: string
  }>(),
  { fullScale: 40 },
)

const CX = 110
const CY = 110

const arcs = computed(() =>
  props.items.map((item, i) => {
    const r = 98 - 14 * i
    const filled = Math.min(100, Math.max(0, (item.ratio / props.fullScale) * 100))
    return {
      ...item,
      d: `M${CX},${CY - r} A${r},${r} 0 1 1 ${CX - r},${CY}`,
      cls: `c${(i % 6) + 1}`,
      dash: `${Math.round(filled * 10) / 10} 100`,
      tickY: CY - r + 4,
    }
  }),
)
</script>

<template>
  <svg class="chart chart--rings" viewBox="0 0 220 220" role="img" :aria-label="ariaLabel">
    <template v-for="(arc, i) in arcs" :key="i">
      <path class="chart__track" :d="arc.d" stroke-width="10" pathLength="100" />
      <path :class="['chart__arc', arc.cls]" :d="arc.d" stroke-width="10" pathLength="100" :stroke-dasharray="arc.dash"><title>{{ arc.label }}｜{{ arc.ratio }}%</title></path>
      <text class="chart__tick chart__tick--ring" x="104" :y="arc.tickY" text-anchor="end">{{ arc.label }}</text>
    </template>
  </svg>
</template>
