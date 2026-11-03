<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = defineProps<{
  current: number
  pages: number
}>()

const emit = defineEmits<{ change: [page: number] }>()

const visiblePages = computed(() => {
  const total = props.pages
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const set = new Set<number>([1, total, props.current, props.current - 1, props.current + 1])
  return [...set].filter((p) => p >= 1 && p <= total).sort((a, b) => a - b)
})

function go(page: number) {
  if (page < 1 || page > props.pages || page === props.current) return
  emit('change', page)
}
</script>

<template>
  <nav v-if="pages > 1" class="pager">
    <button class="pager__btn" :disabled="current <= 1" @click="go(current - 1)">
      <ChevronLeft :size="16" />
    </button>
    <template v-for="(page, index) in visiblePages" :key="page">
      <span v-if="index > 0 && page - visiblePages[index - 1] > 1" class="pager__gap">…</span>
      <button
        class="pager__btn"
        :class="{ 'pager__btn--active': page === current }"
        @click="go(page)"
      >
        {{ page }}
      </button>
    </template>
    <button class="pager__btn" :disabled="current >= pages" @click="go(current + 1)">
      <ChevronRight :size="16" />
    </button>
  </nav>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 32px;
}

.pager__btn {
  min-width: 34px;
  height: 34px;
  padding: 0 10px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--fg-soft);
  font-size: 13px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease, color 0.15s ease;
}

.pager__btn:hover:not(:disabled) {
  background: var(--bg-soft);
  color: var(--fg);
}

.pager__btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pager__btn--active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.pager__gap {
  color: var(--muted);
  font-size: 13px;
}
</style>
