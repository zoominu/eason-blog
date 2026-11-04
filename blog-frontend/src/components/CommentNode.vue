<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import type { CommentItem } from '@/api/types'
import { formatDateTime } from '@/utils/format'

/**
 * 递归评论节点。
 *
 * - 自己的评论不显示「回复」：后端也会拒绝自己回复自己；
 * - 回复超过 5 条时只展示前 3 条，展开按钮放在回复列表末尾；
 * - 博主评论带「博主」标记；
 * - 从通知跳转时传 focusId，命中路径上的折叠评论会自动展开。
 */
const props = defineProps<{
  comment: CommentItem
  depth?: number
  focusId?: number | null
}>()

const emit = defineEmits<{ reply: [comment: CommentItem] }>()

const expanded = ref(false)
const contentExpanded = ref(false)
const contentRef = ref<HTMLElement | null>(null)
const overflowing = ref(false)
const maxDepth = 3

const COLLAPSE_THRESHOLD = 5
const COLLAPSED_VISIBLE = 3
const LONG_CONTENT_CHARS = 180
const LONG_CONTENT_LINES = 6

const replies = computed(() => props.comment.replies ?? [])

/** 长评论先按字符/行数做初判，是否真的被截断由 max-height 实测决定 */
const isLongContent = computed(() => {
  if (props.comment.hidden) return false
  const content = props.comment.content ?? ''
  return content.length > LONG_CONTENT_CHARS || content.split('\n').length > LONG_CONTENT_LINES
})

/** 只有内容真的超过 6 行才显示「显示全部」，避免 4 行文本也弹按钮 */
function measureOverflow() {
  const el = contentRef.value
  if (!el) return
  if (contentExpanded.value) {
    overflowing.value = true
    return
  }
  overflowing.value = el.scrollHeight > el.clientHeight + 2
}

onMounted(() => nextTick(measureOverflow))
watch(() => props.comment.content, () => nextTick(measureOverflow))
watch(contentExpanded, () => nextTick(measureOverflow))

/** 目标评论是否在自己或子孙里（用于自动展开折叠链） */
function containsFocus(node: CommentItem, target: number): boolean {
  if (node.id === target) return true
  return (node.replies ?? []).some((child) => containsFocus(child, target))
}

const focusInside = computed(() => props.focusId != null && containsFocus(props.comment, props.focusId))
const shouldCollapse = computed(() => replies.value.length > COLLAPSE_THRESHOLD)
const expandedNow = computed(() => expanded.value || focusInside.value)
const visibleReplies = computed(() => {
  if (!shouldCollapse.value || expandedNow.value) return replies.value
  return replies.value.slice(0, COLLAPSED_VISIBLE)
})
const hiddenCount = computed(() => replies.value.length - COLLAPSED_VISIBLE)
const canNest = computed(() => (props.depth ?? 0) < maxDepth)

watch(focusInside, (value) => {
  if (value) expanded.value = true
})
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

    <p v-if="comment.hidden" class="comment__hidden">当前评论已被隐藏</p>
    <p
      v-else
      ref="contentRef"
      class="comment__content"
      :class="{ 'comment__content--clamp': isLongContent && !contentExpanded }"
    >
      {{ comment.content }}
    </p>
    <button
      v-if="overflowing"
      class="comment__more"
      @click="contentExpanded = !contentExpanded"
    >
      {{ contentExpanded ? '收起' : '显示全部' }}
    </button>

    <!-- 最多四级：第 4 级不再提供回复入口；自己的评论、已隐藏的评论也不显示 -->
    <div v-if="!comment.mine && !comment.hidden && (depth ?? 0) < 3" class="comment__actions">
      <button class="comment__reply" @click="emit('reply', comment)">回复</button>
    </div>

    <div v-if="replies.length && canNest" class="replies">
      <CommentNode
        v-for="child in visibleReplies"
        :key="child.id"
        :comment="child"
        :depth="(depth ?? 0) + 1"
        :focus-id="focusId"
        @reply="(target) => emit('reply', target)"
      />

      <!-- 展开按钮固定在最后一条回复下面，不再夹在正文与回复之间 -->
      <button v-if="shouldCollapse" class="comment__expand" @click="expanded = !expanded">
        {{ expandedNow ? '收起回复' : `展开其余 ${hiddenCount} 条回复` }}
      </button>
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
  overflow-wrap: anywhere;
  word-break: break-word;
}

.comment__content--clamp {
  /* 用 max-height 而不是 line-clamp：scrollHeight 才能反映真实高度，便于判断是否被截断 */
  max-height: calc(1.7em * 6);
  overflow: hidden;
}

.comment__more {
  margin: 4px 0 0;
  padding: 0;
  border: none;
  background: none;
  font-size: 12px;
  color: var(--accent);
  cursor: pointer;
}

.comment__hidden {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
  font-style: italic;
  overflow-wrap: anywhere;
}

.comment__actions {
  display: flex;
  gap: 12px;
}

/* 回复按钮平时隐藏，只覆盖当前这一条评论的悬停范围 */
.comment__reply {
  margin-top: 6px;
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: var(--accent);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.comment:hover > .comment__actions > .comment__reply,
.comment:focus-within > .comment__actions > .comment__reply {
  opacity: 1;
}

@media (hover: none) {
  .comment__reply {
    opacity: 1;
  }
}

.comment__expand {
  align-self: flex-start;
  margin-top: 2px;
  border: 1px dashed var(--border);
  border-radius: 999px;
  background: none;
  padding: 4px 12px;
  font-size: 12px;
  color: var(--fg-soft);
  cursor: pointer;
}

.comment__expand:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.replies {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
