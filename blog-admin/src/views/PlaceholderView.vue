<!--
  占位页：未实现模块的统一提示，避免侧边栏链接 404。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Tools } from '@element-plus/icons-vue'

const route = useRoute()

const title = computed(() => String(route.meta.title ?? '模块'))
const note = computed(() => String(route.meta.note ?? '该模块将在后续阶段实现。'))
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h2 class="page__title">{{ title }}</h2>
      <p class="page__desc">骨架已就位，功能按阶段交付</p>
    </header>

    <el-card shadow="never" class="panel">
      <div class="placeholder">
        <el-icon :size="34" class="placeholder__icon"><Tools /></el-icon>
        <p class="placeholder__title">{{ title }}尚未实现</p>
        <p class="placeholder__note">{{ note }}</p>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.panel {
  border-radius: 12px;
}

.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 56px 24px;
  text-align: center;
}

.placeholder__icon {
  color: var(--admin-muted);
  margin-bottom: 6px;
}

.placeholder__title {
  margin: 0;
  font-weight: 600;
}

.placeholder__note {
  margin: 0;
  font-size: 13px;
  color: var(--admin-muted);
  max-width: 420px;
}
</style>
