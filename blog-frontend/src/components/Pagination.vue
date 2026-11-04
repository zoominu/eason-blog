<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

const jumpTo = ref('')

watch(
  () => props.current,
  () => (jumpTo.value = '')
)

function go(page: number) {
  if (page < 1 || page > props.pages || page === props.current) return
  emit('change', page)
}

/** 页码多时提供「跳至第 N 页」，避免只能一页页点 */
function submitJump() {
  const page = Number(jumpTo.value)
  if (!Number.isFinite(page)) return
  const target = Math.min(Math.max(Math.trunc(page), 1), props.pages)
  go(target)
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

    <span v-if="pages > 5" class="pager__jump">
      跳至
      <input
        v-model="jumpTo"
        class="pager__input"
        type="number"
        min="1"
        :max="pages"
        inputmode="numeric"
        @keyup.enter="submitJump"
      />
      页
      <button class="pager__btn pager__btn--go" @click="submitJump">确定</button>
    </span>
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

.pager__jump {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 10px;
  font-size: 13px;
  color: var(--fg-soft);
}

.pager__input {
  width: 56px;
  height: 34px;
  padding: 0 8px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--fg);
  font-size: 13px;
  outline: none;
}

.pager__input:focus {
  border-color: var(--accent);
}

.pager__btn--go {
  padding: 0 12px;
}
</style>
