<script setup lang="ts">
import { Eye, Clock, Pin, FileText } from 'lucide-vue-next'
import type { ArticleSummary } from '@/api/types'
import { formatDate, formatCount } from '@/utils/format'

defineProps<{ article: ArticleSummary }>()
</script>

<template>
  <RouterLink :to="`/article/${article.slug}`" class="card article-card fade-in">
    <!-- 固定高度的缩略图区：没有封面也占位，保证所有卡片的分类行在同一水平线 -->
    <div class="article-card__cover" :class="{ 'article-card__cover--empty': !article.cover }">
      <img v-if="article.cover" :src="article.cover" :alt="article.title" />
      <FileText v-else :size="34" class="article-card__initial" />
    </div>

    <div class="article-card__head">
      <span v-if="article.categoryName" class="tag-chip">{{ article.categoryName }}</span>
      <span v-if="article.top" class="pin"><Pin :size="13" /> 置顶</span>
    </div>

    <h3 class="article-card__title">{{ article.title }}</h3>
    <p class="article-card__summary">{{ article.summary }}</p>

    <div class="article-card__meta muted">
      <span>{{ formatDate(article.publishTime) }}</span>
      <span class="dot" />
      <span><Clock :size="13" /> {{ article.readingMinutes }} 分钟</span>
      <span class="dot" />
      <span><Eye :size="13" /> {{ formatCount(article.viewCount) }}</span>
    </div>
  </RouterLink>
</template>

<style scoped>
.article-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 22px 24px;
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.article-card__cover {
  width: calc(100% + 48px);
  margin: -22px -24px 0;
  height: 150px;
  overflow: hidden;
  border-radius: 16px 16px 0 0;
  background: var(--bg-soft);
}

.article-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.article-card__cover--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg,
    color-mix(in srgb, var(--accent) 16%, var(--bg-soft)),
    color-mix(in srgb, var(--accent) 4%, var(--bg-soft)));
}

.article-card__initial {
  color: color-mix(in srgb, var(--accent) 45%, transparent);
  user-select: none;
}

.article-card:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--accent) 35%, var(--border));
  box-shadow: var(--shadow);
}

.article-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pin {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12px;
  color: var(--accent);
}

.article-card__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.01em;
  line-height: 1.45;
}

.article-card__summary {
  margin: 0;
  color: var(--fg-soft);
  font-size: 14px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-top: auto;
}

.article-card__meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--muted);
}
</style>
