<script setup lang="ts">
import { computed } from 'vue'
import { Monitor, Moon, Sun } from 'lucide-vue-next'
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()

const icon = computed(() => {
  if (theme.mode === 'system') return Monitor
  return theme.mode === 'dark' ? Moon : Sun
})

const label = computed(() => {
  if (theme.mode === 'system') return '跟随系统'
  return theme.mode === 'dark' ? '深色' : '浅色'
})
</script>

<template>
  <button
    class="theme-toggle"
    :title="`主题：${label}（点击切换）`"
    :aria-label="`主题：${label}，点击切换`"
    @click="theme.toggle()"
  >
    <component :is="icon" :size="18" />
  </button>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--fg-soft);
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease, transform 0.15s ease;
}

.theme-toggle:hover {
  color: var(--fg);
  background: var(--bg-soft);
  transform: rotate(8deg);
}
</style>
