<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CommentItem } from '@/api/types'
import { formatDateTime } from '@/utils/format'

/**
 * 递归评论节点。
 *
 * - 自己的评论不显示「回复」：后端也会拒绝自己回复自己；
 * - 回复超过 5 条时只展示前 3 条，其余折叠；
 * - 博主评论带「博主」标记。
 */
const props = defineProps<{
  comment: CommentItem
  depth?: number
}>()

const emit = defineEmits<{ reply: [comment: CommentItem] }>()

const expanded = ref(false)
const maxDepth = 3

const COLLAPSE_THRESHOLD = 5
const COLLAPSED_VISIBLE = 3

const replies = computed(() => props.comment.replies ?? [])
const shouldCollapse = computed(() => replies.value.length > COLLAPSE_THRESHOLD)
const visibleReplies = computed(() => {
  if (!shouldCollapse.value || expanded.value) return replies.value
  return replies.value.slice(0, COLLAPSED_VISIBLE)
})
const hiddenCount = computed(() => replies.value.length - COLLAPSED_VISIBLE)
const canNest = computed(() => (props.depth ?? 0) < maxDepth)
</script>

<template>
  <div :id="`comment-${comment.id}`" class="comment" :class="{ 'comment--nested': (depth ?? 0) > 0 }">
    <div class="comment__head">
      <img v-if="comment.avatar" :src="comment.avatar" class="comment__avatar" alt="" />
      <span class="comment__name">{{ comment.nickname }}</span>
      <span v-if="comment.author" class="badge badge--author">博主</span>
      <span v-if="comment.mine" class="badge badge--mine">我</span>
      <span class="muted comment__time">{{ formatDateTime(comment.createTime) }}</span>
    </div>

    <p class="comment__content">{{ comment.content }}</p>

    <div class="comment__actions">
      <button v-if="!comment.mine" class="comment__reply" @click="emit('reply', comment)">回复</button>
      <button
        v-if="replies.length && canNest && shouldCollapse"
        class="comment__reply"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起回复' : `展开其余 ${hiddenCount} 条回复` }}
      </button>
    </div>

    <div v-if="replies.length && canNest" class="replies">
      <CommentNode
        v-for="child in visibleReplies"
        :key="child.id"
        :comment="child"
        :depth="(depth ?? 0) + 1"
        @reply="(target) => emit('reply', target)"
      />
    </div>
  </div>
</template>

<style scoped>
.comment {
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--border);
  scroll-margin-top: 90px;
}

.comment--nested {
  border-bottom: none;
  padding-bottom: 0;
  padding-left: 14px;
  border-left: 2px solid var(--border);
}

.comment__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.comment__avatar {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  object-fit: cover;
}

.comment__name {
  font-weight: 600;
  font-size: 14px;
}

.comment__time {
  font-size: 12px;
}

.badge {
  font-size: 11px;
  border-radius: 999px;
  padding: 0 6px;
  line-height: 17px;
}

.badge--mine {
  color: var(--accent);
  border: 1px solid var(--accent);
}

.badge--author {
  color: #fff;
  background: var(--accent);
  font-weight: 500;
}

.comment__content {
  margin: 0;
  font-size: 14px;
  color: var(--fg-soft);
  line-height: 1.7;
  white-space: pre-wrap;
}

.comment__actions {
  display: flex;
  gap: 12px;
}

.comment__reply {
  margin-top: 6px;
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: var(--accent);
  cursor: pointer;
}

.replies {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
