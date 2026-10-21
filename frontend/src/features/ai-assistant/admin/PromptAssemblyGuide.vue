<script setup lang="ts">
// Two steps only: the platform safety rules are code-only and never shown here (Phase C exception).
defineProps<{ instructionCount: number }>()
defineEmits<{ 'go-instructions': [] }>()
</script>

<template>
  <section class="pm-assembly" aria-label="送給模型的系統訊息組裝順序">
    <h3 class="pm-assembly__title">送給模型的系統訊息</h3>
    <ol class="pm-assembly__steps">
      <li class="pm-assembly__step is-current">
        <span class="pm-assembly__no">1</span>
        <div>
          <strong>System Prompt</strong>
          <span>角色、安全規則、回答格式</span>
        </div>
        <span class="pm-assembly__tag">本頁編輯</span>
      </li>
      <li class="pm-assembly__step">
        <span class="pm-assembly__no">2</span>
        <div>
          <strong>問題分類</strong>
          <span>{{ instructionCount }} 份已發布的 System Instructions</span>
        </div>
        <button class="pm-assembly__link" type="button" @click="$emit('go-instructions')">前往設定</button>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.pm-assembly__title {
  margin-bottom: 8px;
  font-size: var(--text-sm);
  font-weight: 600;
}

.pm-assembly__steps {
  list-style: none;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
}

.pm-assembly__step {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  font-size: var(--text-sm);
}

/* 步驟之間的箭頭 */
.pm-assembly__step:not(:last-child)::after {
  content: '';
  position: absolute;
  right: -7px;
  top: 50%;
  z-index: 1;
  width: 10px;
  height: 10px;
  border-top: 1px solid var(--color-border-strong);
  border-right: 1px solid var(--color-border-strong);
  background: var(--color-surface);
  transform: translateY(-50%) rotate(45deg);
}

.pm-assembly__step > div {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.pm-assembly__step > div span {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.pm-assembly__no {
  display: grid;
  place-items: center;
  flex: none;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--gray-100);
  color: var(--gray-600);
  font-size: var(--text-xs);
  font-weight: 700;
}

.pm-assembly__step.is-current {
  border-color: var(--green-300);
  background: var(--color-primary-subtle);
}

.pm-assembly__step.is-current::after {
  border-color: var(--green-300);
  background: var(--color-primary-subtle);
}

.pm-assembly__step.is-current .pm-assembly__no {
  background: var(--color-primary);
  color: #fff;
}

.pm-assembly__tag,
.pm-assembly__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.pm-assembly__tag {
  background: var(--color-surface);
  color: var(--green-700);
  border: 1px solid var(--green-200);
}

.pm-assembly__link {
  border: 0;
  background: none;
  color: var(--green-700);
  cursor: pointer;
}

.pm-assembly__link:hover {
  background: var(--green-50);
}

@media (max-width: 1100px) {
  .pm-assembly__steps {
    grid-template-columns: 1fr;
  }
  .pm-assembly__step:not(:last-child)::after {
    display: none;
  }
}
</style>
