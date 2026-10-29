<script setup lang="ts">
export type VerdictTone = 'better' | 'worse' | 'same' | 'neutral'

defineProps<{
  tone: VerdictTone
  title: string
  subtitle: string
  progress?: { completed: number; total: number } | null
}>()

const MARK: Record<VerdictTone, string> = { better: '▲', worse: '▼', same: '≈', neutral: '' }
</script>

<template>
  <div class="ev-verdict" :class="`ev-verdict--${tone}`" role="status">
    <div class="ev-verdict__body">
      <p class="ev-verdict__head">
        <span v-if="MARK[tone]" class="ev-verdict__mark" aria-hidden="true">{{ MARK[tone] }}</span>
        <span class="ev-verdict__title">{{ title }}</span>
      </p>
      <p v-if="subtitle" class="ev-verdict__subtitle">{{ subtitle }}</p>
      <div
        v-if="progress"
        class="ev-verdict__bar"
        role="progressbar"
        aria-label="評測進度"
        aria-valuemin="0"
        :aria-valuemax="progress.total"
        :aria-valuenow="progress.completed"
      >
        <div
          class="ev-verdict__fill"
          :style="{ width: progress.total ? `${(progress.completed / progress.total) * 100}%` : '0%' }"
        ></div>
      </div>
    </div>
    <div class="ev-verdict__actions"><slot /></div>
  </div>
</template>

<style scoped>
.ev-verdict {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  background: var(--gray-50);
}
.ev-verdict--better {
  background: var(--green-50);
  border-color: var(--green-200);
}
.ev-verdict--worse {
  background: var(--red-50);
  border-color: var(--red-200);
}
.ev-verdict--same {
  background: var(--amber-50);
  border-color: var(--amber-200);
}
.ev-verdict__body {
  flex: 1 1 18rem;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ev-verdict__head,
.ev-verdict__subtitle {
  margin: 0;
}
.ev-verdict__head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text-strong);
}
.ev-verdict--better .ev-verdict__mark {
  color: var(--green-700);
}
.ev-verdict--worse .ev-verdict__mark {
  color: var(--red-600);
}
.ev-verdict--same .ev-verdict__mark {
  color: var(--amber-700);
}
.ev-verdict__subtitle {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
}
.ev-verdict__bar {
  height: 8px;
  border-radius: 4px;
  background: var(--gray-100);
  overflow: hidden;
}
.ev-verdict__fill {
  height: 100%;
  background: var(--color-primary);
  transition: width 0.3s;
}
.ev-verdict__actions {
  display: flex;
  gap: var(--space-3);
}
.ev-verdict__actions:empty {
  display: none;
}
</style>
