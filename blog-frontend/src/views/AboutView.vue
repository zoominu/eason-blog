<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { siteApi } from '@/api'
import type { SiteConfig } from '@/api/types'
import { formatDate } from '@/utils/format'

const config = ref<SiteConfig>({})

onMounted(async () => {
  try {
    config.value = await siteApi.config()
  } catch {
    // 允许多次重试：这里只做静默降级
  }
})
</script>

<template>
  <div class="container narrow">
    <header class="head">
      <h1>关于</h1>
      <p class="muted">{{ config['site.subtitle'] }}</p>
    </header>

    <div class="card block">
      <h2>{{ config['site.author'] || 'Eason' }}</h2>
      <p>
        这里是我的个人技术博客，用来沉淀 Java、Spring Boot、MySQL、Vue
        的学习笔记与实践记录。写作原则是：先讲清为什么，再给出能运行的代码。
      </p>
      <p class="muted">建站时间：{{ formatDate(config['site.startDate']) }}</p>
    </div>

    <div class="card block">
      <h2>技术栈</h2>
      <ul class="stack">
        <li><span>前端</span> Vue 3 · TypeScript · Vite · Pinia · Tailwind CSS</li>
        <li><span>后端</span> Java 21 · Spring Boot 3 · Spring Security · MyBatis-Plus</li>
        <li><span>数据</span> MySQL 8 · Flyway</li>
        <li><span>部署</span> Nginx · Linux VPS</li>
      </ul>
    </div>

    <div class="card block">
      <h2>联系</h2>
      <p>
        GitHub：
        <a :href="config['site.github'] || 'https://github.com/'" target="_blank" rel="noopener">
          {{ config['site.github'] || 'https://github.com/' }}
        </a>
      </p>
    </div>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 760px;
}

.head {
  margin-bottom: 24px;
}

.head h1 {
  margin: 0 0 4px;
  font-size: 28px;
  letter-spacing: -0.02em;
}

.block {
  padding: 26px 28px;
  margin-bottom: 16px;
}

.block h2 {
  margin: 0 0 12px;
  font-size: 17px;
}

.block p {
  margin: 0 0 8px;
  color: var(--fg-soft);
  font-size: 14px;
}

.block a {
  color: var(--accent);
}

.stack {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 14px;
  color: var(--fg-soft);
}

.stack span {
  display: inline-block;
  width: 52px;
  color: var(--muted);
  font-size: 12px;
}
</style>
