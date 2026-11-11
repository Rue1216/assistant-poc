<script setup lang="ts">
import { computed } from 'vue'
import type { AssetBlock, LiabilityBlock } from '@/beans/customer-bean'
import { formatPercent, formatPoints, formatWan } from '@/utils/formatters'
import RingChart from '@/components/charts/ring-chart.vue'

const props = defineProps<{ aum: AssetBlock; lum: LiabilityBlock }>()

const AUM_ROWS: { key: keyof AssetBlock['auMData']; label: string }[] = [
  { key: 'demandDeposit', label: '活存' },
  { key: 'timeDeposit', label: '定存' },
  { key: 'fund', label: '基金' },
  { key: 'financialProduct', label: '金融商品' },
  { key: 'insurance', label: '保險' },
  { key: 'financialAsset', label: '財金商品' },
]

const LUM_ROWS: { key: keyof LiabilityBlock['luMData']; label: string }[] = [
  { key: 'mtgPurchase', label: '房貸・購置' },
  { key: 'mtgRevolve', label: '房貸・周轉' },
  { key: 'collateral', label: '副擔保' },
  { key: 'creditLoan', label: '信貸' },
  { key: 'finAssetLoan', label: '金融資產融資' },
  { key: 'ccBalance', label: '信用卡已使用' },
]

/** Hero numbers print the amount without the unit; the unit is a separate element. */
const wanValue = (yuan: number) => formatWan(yuan).replace(' 萬', '')
/** The comparison cells are narrow, so the 個百分點 suffix is dropped there. */
const points = (diff: number) => formatPoints(diff).replace(' 個百分點', '')

const aumRows = computed(() =>
  AUM_ROWS.map(({ key, label }, i) => {
    const d = props.aum.auMData[key]
    return {
      label,
      cls: `c${i + 1}`,
      amount: formatWan(d.amountList[0]),
      ratio: Number(d.ratioList[0]),
      group: Number(d.ratioList[1]),
      top: Number(d.ratioList[2]),
    }
  }),
)

// RingChart prints the ratio as given, so hand it the same 2-digit precision the table shows.
const ringItems = computed(() => aumRows.value.map((r) => ({ label: r.label, ratio: Number(r.ratio.toFixed(2)) })))
const ringLabel = computed(() => `資產配置：${aumRows.value.map((r) => `${r.label} ${formatPercent(r.ratio)}`).join('、')}`)

const lumRows = computed(() =>
  LUM_ROWS.map(({ key, label }, i) => {
    const d = props.lum.luMData[key]
    return { label, cls: `c${i + 1}`, amount: formatWan(d.amountList[0]), ratio: Number(d.ratioList[0]) }
  }),
)
const stackLabel = computed(() => lumRows.value.map((r) => `${r.label} ${formatPercent(r.ratio)}`).join('、'))
const debtToAsset = computed(() =>
  props.aum.customerTotal > 0 ? (props.lum.customerTotal / props.aum.customerTotal).toFixed(2) : '—',
)
</script>

<template>
  <section class="card dash-card c360-c" data-area="C" aria-label="資產 AuM">
    <header class="dash-card__head"><h2 class="dash-card__title">資產 AuM</h2><div class="seg" role="radiogroup" aria-label="比較對象">
        <input class="sr-only" type="radio" name="aum-cmp" id="aum-cmp-self" checked><label for="aum-cmp-self">顧客</label>
        <input class="sr-only" type="radio" name="aum-cmp" id="aum-cmp-group"><label for="aum-cmp-group">客群</label>
        <input class="sr-only" type="radio" name="aum-cmp" id="aum-cmp-top"><label for="aum-cmp-top">Top 10%</label>
      </div><span class="dash-card__meta">萬元</span></header>
    <div class="dash-card__body">
      <div class="hero-num"><span class="hero-num__value">{{ wanValue(aum.customerTotal) }}</span><span class="hero-num__unit">萬元</span></div>
      <div class="aum">
        <div class="aum__chart">
          <ring-chart :items="ringItems" :ariaLabel="ringLabel" />
          <p class="aum__scale">圓弧滿格 = 40%</p>
        </div>
        <table class="aum-table">
          <thead><tr><th scope="col">項目</th><th scope="col">金額</th><th scope="col">佔比</th><th scope="col" class="cmp cmp--group">客群</th><th scope="col" class="cmp cmp--top">Top 10%</th></tr></thead>
          <tbody>
            <tr v-for="r in aumRows" :key="r.label">
              <th scope="row"><span :class="['legend__swatch', r.cls]"></span>{{ r.label }}</th>
              <td>{{ r.amount }}</td>
              <td>{{ formatPercent(r.ratio) }}</td>
              <td class="cmp cmp--group">{{ formatPercent(r.group) }}<small>{{ points(r.ratio - r.group) }}</small></td>
              <td class="cmp cmp--top">{{ formatPercent(r.top) }}<small>{{ points(r.ratio - r.top) }}</small></td>
            </tr>
          </tbody>
          <tfoot><tr><th scope="row">合計</th><td>{{ formatWan(aum.customerTotal) }}</td><td>{{ formatPercent(100) }}</td><td class="cmp cmp--group">{{ formatWan(aum.segmentTotal) }}</td><td class="cmp cmp--top">{{ formatWan(aum.topTenTotal) }}</td></tr></tfoot>
        </table>
      </div></div>
  </section>
  <section class="card dash-card c360-d" data-area="D" aria-label="負債 LuM＋信用卡">
    <header class="dash-card__head"><h2 class="dash-card__title">負債 LuM＋信用卡</h2><span class="dash-card__meta">萬元</span></header>
    <div class="dash-card__body">
      <div class="hero-num"><span class="hero-num__value">{{ wanValue(lum.customerTotal) }}</span><span class="hero-num__unit">萬元</span><span class="delta">負債／資產 {{ debtToAsset }}</span></div>
      <div class="stack" role="img" :aria-label="stackLabel">
        <span v-for="r in lumRows" :key="r.label" :class="['stack__seg', r.cls]" :style="{ flexBasis: `${r.ratio}%` }" :title="`${r.label} ${formatPercent(r.ratio)}｜${r.amount}`"></span>
      </div>
      <ul class="legend legend--table">
        <li v-for="r in lumRows" :key="r.label"><span :class="['legend__swatch', r.cls]"></span>{{ r.label }}<span class="legend__pct">{{ formatPercent(r.ratio) }}</span><strong>{{ r.amount }}</strong></li>
      </ul></div>
  </section>
</template>
