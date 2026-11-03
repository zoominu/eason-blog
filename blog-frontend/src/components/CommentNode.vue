<script setup lang="ts">
import { ref } from 'vue'
import type { CommentItem } from '@/api/types'
import { formatDateTime } from '@/utils/format'

/**
 * 递归评论节点：任意层级回复都能展示。
 * 数据结构是 CommentVO.replies 嵌套，模板无法自引用，必须抽成组件。
 */
defineProps<{
  comment: CommentItem
  depth?: number
}>()

const emit = defineEmits<{ reply: [comment: CommentItem] }>()

const collapsed = ref(false)
const maxDepth = 3

function toggle() {
  collapsed.value = !collapsed.value
}
</script>

<template>
  <div class="comment" :class="{ 'comment--nested': (depth ?? 0) > 0 }">
    <div class="comment__head">
      <img v-if="comment.avatar" :src="comment.avatar" class="comment__avatar" alt="" />
      <span class="comment__name">{{ comment.nickname }}</span>
      <span class="muted comment__time">{{ formatDateTime(comment.createTime) }}</span>
      <span v-if="comment.mine" class="mine">我</span>
    </div>

    <p class="comment__content">{{ comment.content }}</p>

    <div class="comment__actions">
      <button class="comment__reply" @click="emit('reply', comment)">回复</button>
      <button
        v-if="comment.replies.length && (depth ?? 0) < maxDepth"
        class="comment__reply"
        @click="toggle"
      >
        {{ collapsed ? `展开 ${comment.replies.length} 条回复` : '收起回复' }}
      </button>
    </div>

    <div v-if="comment.replies.length && !collapsed" class="replies">
      <CommentNode
        v-for="child in comment.replies"
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

.mine {
  font-size: 11px;
  color: var(--accent);
  border: 1px solid var(--accent);
  border-radius: 999px;
  padding: 0 6px;
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
