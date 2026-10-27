<script setup lang="ts">
withDefaults(defineProps<{ direction: 'up' | 'down'; size?: number; filled?: boolean }>(), {
  size: 14,
  filled: false,
})

const PATH = {
  up: 'M7 11v9H4v-9h3zM7 11l4-7a2 2 0 0 1 2 2v4h6a2 2 0 0 1 2 2.3l-1 6A2 2 0 0 1 18 20H7',
  down: 'M17 13V4h3v9h-3zM17 13l-4 7a2 2 0 0 1-2-2v-4H5a2 2 0 0 1-2-2.3l1-6A2 2 0 0 1 6 4h11',
}
</script>

<template>
  <svg viewBox="0 0 24 24" :width="size" :height="size" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="PATH[direction]" :fill="filled ? 'currentColor' : undefined" />
  </svg>
</template>
