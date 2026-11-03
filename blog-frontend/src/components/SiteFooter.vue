<script setup lang="ts">
import { computed } from 'vue'
import { siteApi } from '@/api'
import { ref, onMounted } from 'vue'
import type { SiteConfig } from '@/api/types'

const config = ref<SiteConfig>({})

onMounted(async () => {
  try {
    config.value = await siteApi.config()
  } catch {
    // 页脚信息失败不影响页面，静默降级
  }
})

const year = computed(() => new Date().getFullYear())
</script>

<template>
  <footer class="footer">
    <div class="container footer__inner">
      <div class="footer__brand">
        <span class="footer__title">{{ config['site.title'] || 'Eason Blog' }}</span>
        <span class="muted">{{ config['site.subtitle'] || '记录 · 思考 · 分享' }}</span>
      </div>
      <div class="footer__meta muted">
        <span>© {{ year }} {{ config['site.author'] || 'Eason' }}</span>
        <span v-if="config['site.icp']"> · {{ config['site.icp'] }}</span>
        <span> · Built with Spring Boot &amp; Vue 3</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  border-top: 1px solid var(--border);
  padding: 32px 0 40px;
  background: var(--bg-soft);
}

.footer__inner {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
}

.footer__brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.footer__title {
  font-weight: 600;
}

.footer__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
