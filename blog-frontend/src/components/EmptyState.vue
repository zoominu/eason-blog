<script setup lang="ts">
import { FileText } from 'lucide-vue-next'

withDefaults(defineProps<{ title?: string; description?: string }>(), {
  title: '暂无内容',
  description: '这里还没有文章，稍后再来看看吧。'
})
</script>

<template>
  <div class="empty card">
    <FileText :size="28" class="empty__icon" />
    <p class="empty__title">{{ title }}</p>
    <p class="empty__desc muted">{{ description }}</p>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 48px 24px;
  text-align: center;
}

.empty__icon {
  color: var(--muted);
  margin-bottom: 6px;
}

.empty__title {
  margin: 0;
  font-weight: 600;
}

.empty__desc {
  margin: 0;
  font-size: 13px;
}
</style>
