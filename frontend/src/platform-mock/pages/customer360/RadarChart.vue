<script lang="ts">
/** Offset from the chart centre; index 0 points straight up, then clockwise. */
export function radarPoint(index: number, count: number, value: number, radius: number): { x: number; y: number } {
  const angle = (2 * Math.PI * index) / count
  const r = (radius * value) / 100
  return { x: r * Math.sin(angle), y: -r * Math.cos(angle) }
}
</script>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  labels: string[]
  customer: number[]
  segment?: number[]
  highlight?: boolean[]
  ariaLabel: string
}>()

const RADIUS = 78
const LABEL_RADIUS = 90
const CX = 170

const count = computed(() => props.labels.length)
const cy = computed(() => (count.value <= 4 ? 117 : 127))
const height = computed(() => (count.value <= 4 ? 230 : 250))

const r1 = (n: number) => Math.round(n * 10) / 10
const pts = (list: { x: number; y: number }[]) => list.map((p) => `${r1(CX + p.x)},${r1(cy.value + p.y)}`).join(' ')

const rings = computed(() =>
  [25, 50, 75, 100].map((level) => pts(props.labels.map((_, i) => radarPoint(i, count.value, level, RADIUS)))),
)

const axes = computed(() =>
  props.labels.map((label, i) => {
    const end = radarPoint(i, count.value, 100, RADIUS)
    const lp = radarPoint(i, count.value, 100, LABEL_RADIUS)
    const anchor = lp.x > 1 ? 'start' : lp.x < -1 ? 'end' : 'middle'
    const dy = lp.y < -80 ? -2 : lp.y > 80 ? 10 : 4
    return {
      label,
      x2: r1(CX + end.x),
      y2: r1(cy.value + end.y),
      tx: r1(CX + lp.x),
      ty: r1(cy.value + lp.y + dy),
      anchor,
      hl: props.highlight?.[i] === true,
    }
  }),
)

function series(values: number[], name: string) {
  const points = values.map((v, i) => {
    const p = radarPoint(i, count.value, v, RADIUS)
    return {
      x: r1(CX + p.x),
      y: r1(cy.value + p.y),
      title: `${name}｜${props.labels[i]}：${v}`,
      hl: props.highlight?.[i] === true,
    }
  })
  return { area: points.map((p) => `${p.x},${p.y}`).join(' '), points }
}

const segmentSeries = computed(() => (props.segment ? series(props.segment, '同客群平均') : null))
const customerSeries = computed(() => series(props.customer, '本顧客'))
</script>

<template>
  <svg class="chart chart--radar" :viewBox="`0 0 340 ${height}`" role="img" :aria-label="ariaLabel">
    <polygon v-for="(ring, i) in rings" :key="`ring${i}`" class="chart__grid" :points="ring" />
    <template v-for="(axis, i) in axes" :key="`axis${i}`">
      <line class="chart__grid" :x1="CX" :y1="cy" :x2="axis.x2" :y2="axis.y2" />
      <text :class="['chart__tick', { 'chart__tick--hl': axis.hl }]" :x="axis.tx" :y="axis.ty" :text-anchor="axis.anchor">{{ axis.label }}</text>
    </template>
    <g v-if="segmentSeries" class="chart__series s2">
      <polygon class="chart__area" :points="segmentSeries.area" />
      <circle v-for="(p, i) in segmentSeries.points" :key="i" class="chart__dot" :cx="p.x" :cy="p.y" r="4"><title>{{ p.title }}</title></circle>
    </g>
    <g class="chart__series s1">
      <polygon class="chart__area" :points="customerSeries.area" />
      <circle
        v-for="(p, i) in customerSeries.points"
        :key="i"
        :class="['chart__dot', { 'chart__dot--hl': p.hl, 'is-highlight': p.hl }]"
        :cx="p.x"
        :cy="p.y"
        :r="p.hl ? 6 : 4"
      ><title>{{ p.title }}</title></circle>
    </g>
  </svg>
</template>
