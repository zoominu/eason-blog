<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

// 默认全宽编辑：分栏模式下右侧预览区会占掉一半，写作时显得局促
const mode = ref<'split' | 'edit' | 'preview'>('edit')
const textareaRef = ref<HTMLTextAreaElement | null>(null)

marked.setOptions({ gfm: true, breaks: false })

// 预览必须净化：编辑器允许粘贴任意 HTML，直接 v-html 等于开放 XSS
const html = computed(() => DOMPurify.sanitize(marked.parse(props.modelValue || '') as string))

const stats = computed(() => {
  const text = props.modelValue || ''
  return { chars: text.length, lines: text ? text.split('\n').length : 0 }
})

/** 在光标处插入 Markdown 片段，保持选区可用。 */
async function wrap(before: string, after = '', placeholder = '文本') {
  const el = textareaRef.value
  if (!el) return
  const start = el.selectionStart
  const end = el.selectionEnd
  const value = props.modelValue || ''
  const selected = value.slice(start, end) || placeholder
  const next = value.slice(0, start) + before + selected + after + value.slice(end)
  emit('update:modelValue', next)
  await nextTick()
  el.focus()
  el.setSelectionRange(start + before.length, start + before.length + selected.length)
}

function prefixLine(prefix: string, placeholder: string) {
  const el = textareaRef.value
  if (!el) return
  const value = props.modelValue || ''
  const start = el.selectionStart
  const lineStart = value.lastIndexOf('\n', start - 1) + 1
  const next = value.slice(0, lineStart) + prefix + (value.slice(lineStart) || placeholder)
  emit('update:modelValue', next)
  nextTick(() => el.focus())
}
</script>

<template>
  <div class="editor">
    <div class="toolbar">
      <button type="button" title="二级标题" @click="prefixLine('## ', '标题')">H2</button>
      <button type="button" title="加粗" @click="wrap('**', '**')"><b>B</b></button>
      <button type="button" title="斜体" @click="wrap('*', '*')"><i>I</i></button>
      <button type="button" title="行内代码" @click="wrap('`', '`', 'code')">&lt;/&gt;</button>
      <button type="button" title="代码块" @click="wrap('\n```java\n', '\n```\n', 'code')">{ }</button>
      <button type="button" title="引用" @click="prefixLine('> ', '引用')">“”</button>
      <button type="button" title="无序列表" @click="prefixLine('- ', '列表项')">•</button>
      <button type="button" title="有序列表" @click="prefixLine('1. ', '列表项')">1.</button>
      <button type="button" title="链接" @click="wrap('[', '](https://)', '链接文字')">🔗</button>

      <span class="toolbar__spacer" />
      <div class="modes">
        <button type="button" :class="{ active: mode === 'edit' }" @click="mode = 'edit'">编辑</button>
        <button type="button" :class="{ active: mode === 'split' }" @click="mode = 'split'">分栏</button>
        <button type="button" :class="{ active: mode === 'preview' }" @click="mode = 'preview'">预览</button>
      </div>
    </div>

    <div class="body" :class="`body--${mode}`">
      <textarea
        v-if="mode !== 'preview'"
        ref="textareaRef"
        class="input"
        :value="modelValue"
        spellcheck="false"
        placeholder="支持 Markdown：## 标题、**加粗**、```代码块```、> 引用、- 列表…"
        @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
      />
      <div v-if="mode !== 'edit'" class="preview">
        <article v-if="modelValue" class="prose" v-html="html" />
        <p v-else class="preview__empty">预览区：左侧输入后这里实时渲染</p>
      </div>
    </div>

    <div class="footer">
      <span>{{ stats.chars }} 字符 · {{ stats.lines }} 行</span>
      <span class="muted">正文以 Markdown 原文保存，渲染在阅读端完成</span>
    </div>
  </div>
</template>

<style scoped>
.editor {
  /* el-form-item__content 是 flex 容器，不显式撑满会按内容宽度收缩 */
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-bg-color);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--el-border-color);
  background: var(--el-fill-color-lighter);
}

.toolbar button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.toolbar button:hover {
  background: var(--el-fill-color);
  color: var(--el-color-primary);
}

.toolbar__spacer {
  flex: 1;
}

.modes {
  display: inline-flex;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
}

.modes button {
  width: auto;
  padding: 0 10px;
  border-radius: 0;
  font-size: 12px;
}

.modes button.active {
  background: var(--el-color-primary);
  color: #fff;
}

.body {
  display: grid;
  min-height: 560px;
}

.body--split {
  grid-template-columns: 1fr 1fr;
}

.body--edit,
.body--preview {
  grid-template-columns: 1fr;
}

.input {
  width: 100%;
  min-height: 560px;
  padding: 16px;
  border: none;
  outline: none;
  resize: vertical;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
  background: transparent;
  color: var(--el-text-color-primary);
}

.preview {
  padding: 16px 20px;
  overflow: auto;
  border-left: 1px solid var(--el-border-color);
  max-height: 620px;
}

.body--preview .preview {
  border-left: none;
}

.preview__empty {
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

.footer {
  display: flex;
  justify-content: space-between;
  padding: 6px 12px;
  border-top: 1px solid var(--el-border-color);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.muted {
  color: var(--el-text-color-placeholder);
}

/* 预览区排版 */
.prose {
  font-size: 14px;
  line-height: 1.8;
  word-break: break-word;
}

.prose :deep(h2) {
  font-size: 20px;
  margin: 1.4em 0 0.5em;
}

.prose :deep(h3) {
  font-size: 16px;
  margin: 1.2em 0 0.4em;
}

.prose :deep(p) {
  margin: 0.7em 0;
}

.prose :deep(code) {
  background: var(--el-fill-color-light);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 0.9em;
}

.prose :deep(pre) {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
}

.prose :deep(pre code) {
  background: transparent;
  padding: 0;
}

.prose :deep(blockquote) {
  margin: 1em 0;
  padding: 0.3em 1em;
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-lighter);
}

.prose :deep(a) {
  color: var(--el-color-primary);
}

.prose :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.prose :deep(th),
.prose :deep(td) {
  border: 1px solid var(--el-border-color);
  padding: 6px 10px;
}
</style>
