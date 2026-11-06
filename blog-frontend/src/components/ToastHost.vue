<!--
  全局 Toast 容器：把 store 中的消息队列渲染成浮层，自动消失，不阻塞页面。
-->
<script setup lang="ts">
import { CheckCircle2, AlertCircle, Info } from 'lucide-vue-next'
import { toasts } from '@/utils/toast'
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="item in toasts" :key="item.id" class="toast" :class="`toast--${item.type}`">
        <CheckCircle2 v-if="item.type === 'success'" :size="16" />
        <AlertCircle v-else-if="item.type === 'error'" :size="16" />
        <Info v-else :size="16" />
        <span>{{ item.message }}</span>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 84px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 300;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px;
  border-radius: 999px;
  font-size: 13px;
  color: var(--fg);
  background: var(--card);
  border: 1px solid var(--border);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.14);
  backdrop-filter: blur(8px);
}

.toast--success {
  color: #1f8f57;
  border-color: color-mix(in srgb, #30a46c 40%, var(--border));
}

.toast--error {
  color: #d4383d;
  border-color: color-mix(in srgb, #e5484d 40%, var(--border));
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
