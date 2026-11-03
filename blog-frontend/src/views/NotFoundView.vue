<script setup lang="ts">
import { Compass } from 'lucide-vue-next'
</script>

<template>
  <div class="container notfound">
    <Compass :size="40" class="icon" />
    <h1>404</h1>
    <p class="muted">你访问的页面不存在，可能已被移动或删除。</p>
    <RouterLink to="/" class="btn btn-primary">返回首页</RouterLink>
  </div>
</template>

<style scoped>
.notfound {
  max-width: 480px;
  text-align: center;
  padding: 80px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.icon {
  color: var(--muted);
}

.notfound h1 {
  margin: 0;
  font-size: 48px;
  letter-spacing: -0.03em;
}

.notfound p {
  margin: 0 0 12px;
  font-size: 14px;
}
</style>
