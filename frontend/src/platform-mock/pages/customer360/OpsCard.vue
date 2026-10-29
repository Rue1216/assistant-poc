<script setup lang="ts">
import { computed } from 'vue'
import type { KeyAlert, ManagementCard } from '../../customer360Types'

const props = defineProps<{ management: ManagementCard; alerts: KeyAlert[] }>()

// The insight arrives as one text with items separated by "；".
const insights = computed(() => props.management.insight.split('；').filter((s) => s.trim() !== ''))
</script>

<template>
  <aside class="card c360-b c360-ops" data-area="B" aria-label="關鍵提醒與顧客經營">
    <section class="c360-section">
      <h2 class="c360-section__title">關鍵提醒<span class="c360-count">{{ alerts.length }}</span></h2>
      <ul class="alert-list">
        <li v-for="(a, i) in alerts" :key="i" :class="['alert-list__item', { 'alert-list__item--warn': a.type !== 'OTHER' }]">
          <span class="alert-list__icon" aria-hidden="true">{{ a.type === 'OTHER' ? 'i' : '!' }}</span>
          <div><strong>{{ a.title }}</strong><span>{{ a.detail }}</span></div>
        </li>
      </ul>
    </section>

    <section class="c360-section">
      <h2 class="c360-section__title">專屬活動</h2>
      <ul class="campaign-list">
        <li v-for="c in management.campaignList" :key="c.receiptNo">
          <div><strong>{{ c.campaignName }}</strong><span>{{ c.campaignDuration }}</span></div>
          <span :class="['badge', c.campaignEnabled === 'Y' ? 'badge--info' : 'badge--draft']">{{ c.campaignEnabled === 'Y' ? '可推薦' : '未開放' }}</span>
        </li>
      </ul>
    </section>

    <section class="c360-section">
      <h2 class="c360-section__title">經營洞見<span class="c360-section__hint">系統產生</span></h2>
      <ul class="insight-list">
        <li v-for="(text, i) in insights" :key="i"><strong>{{ text }}</strong></li>
        <li v-if="management.recommendedProducts.length"><strong>建議商品</strong><span>{{ management.recommendedProducts.join('、') }}</span></li>
      </ul>
    </section>
  </aside>
</template>
