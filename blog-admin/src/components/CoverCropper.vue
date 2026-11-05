<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

/**
 * 封面裁剪：固定 16:9 框，图片可拖动、缩放，导出 1200x675 JPEG。
 * 依赖浏览器原生 pointer/canvas，不引入额外库。
 */
const props = defineProps<{ modelValue: boolean; file: File | null }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [file: File]
}>()

const FRAME_W = 640
const FRAME_H = 360

const imageUrl = ref('')
const imgEl = ref<HTMLImageElement | null>(null)
const natural = ref({ w: 0, h: 0 })
const zoom = ref(1)
const offset = ref({ x: 0, y: 0 })
const dragging = ref(false)
let dragStart = { x: 0, y: 0, ox: 0, oy: 0 }

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

/** 图片按比例铺满 16:9 框的基础缩放 */
const baseScale = computed(() =>
  natural.value.w && natural.value.h
    ? Math.max(FRAME_W / natural.value.w, FRAME_H / natural.value.h)
    : 1
)
const totalScale = computed(() => baseScale.value * zoom.value)
const displayW = computed(() => natural.value.w * totalScale.value)
const displayH = computed(() => natural.value.h * totalScale.value)

function clampOffset() {
  const maxX = Math.max(0, (displayW.value - FRAME_W) / 2)
  const maxY = Math.max(0, (displayH.value - FRAME_H) / 2)
  offset.value = {
    x: Math.min(maxX, Math.max(-maxX, offset.value.x)),
    y: Math.min(maxY, Math.max(-maxY, offset.value.y))
  }
}

watch(
  () => props.file,
  (file) => {
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = file ? URL.createObjectURL(file) : ''
    zoom.value = 1
    offset.value = { x: 0, y: 0 }
    natural.value = { w: 0, h: 0 }
  }
)

async function onImageLoad() {
  if (!imgEl.value) return
  natural.value = { w: imgEl.value.naturalWidth, h: imgEl.value.naturalHeight }
  zoom.value = 1
  offset.value = { x: 0, y: 0 }
  await nextTick()
  clampOffset()
}

watch(zoom, () => {
  clampOffset()
})

function onPointerDown(event: PointerEvent) {
  dragging.value = true
  dragStart = { x: event.clientX, y: event.clientY, ox: offset.value.x, oy: offset.value.y }
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value) return
  offset.value = {
    x: dragStart.ox + (event.clientX - dragStart.x),
    y: dragStart.oy + (event.clientY - dragStart.y)
  }
  clampOffset()
}

function onPointerUp() {
  dragging.value = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
}

onBeforeUnmount(() => {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
})

function confirm() {
  if (!imgEl.value || !natural.value.w) return
  const canvas = document.createElement('canvas')
  canvas.width = 1200
  canvas.height = 675
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const k = 1200 / FRAME_W
  const w = displayW.value * k
  const h = displayH.value * k
  const x = ((FRAME_W - displayW.value) / 2 + offset.value.x) * k
  const y = ((FRAME_H - displayH.value) / 2 + offset.value.y) * k
  ctx.fillStyle = '#fff'
  ctx.fillRect(0, 0, 1200, 675)
  ctx.drawImage(imgEl.value, x, y, w, h)
  canvas.toBlob(
    (blob) => {
      if (!blob) return
      emit('confirm', new File([blob], 'cover.jpg', { type: 'image/jpeg' }))
      visible.value = false
    },
    'image/jpeg',
    0.9
  )
}
</script>

<template>
  <el-dialog v-model="visible" title="裁剪封面（16:9）" width="720px" destroy-on-close>
    <div class="cropper">
      <div class="cropper__frame" @pointerdown="onPointerDown">
        <img
          v-if="imageUrl"
          ref="imgEl"
          :src="imageUrl"
          class="cropper__image"
          :style="{
            width: displayW + 'px',
            height: displayH + 'px',
            left: (FRAME_W - displayW) / 2 + offset.x + 'px',
            top: (FRAME_H - displayH) / 2 + offset.y + 'px',
            cursor: dragging ? 'grabbing' : 'grab'
          }"
          alt="封面裁剪"
          @load="onImageLoad"
        />
      </div>
      <div class="cropper__bar">
        <span class="muted">拖动图片选择展示区域</span>
        <el-slider v-model="zoom" :min="1" :max="3" :step="0.05" style="width: 220px" />
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="confirm">使用这张封面</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.cropper__frame {
  position: relative;
  width: 640px;
  height: 360px;
  margin: 0 auto;
  overflow: hidden;
  background: #111;
  border-radius: 10px;
  touch-action: none;
  user-select: none;
}

.cropper__image {
  position: absolute;
  max-width: none;
  pointer-events: none;
}

.cropper__bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 14px;
}
</style>
