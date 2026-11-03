<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MessageSquare, Send } from 'lucide-vue-next'
import { get, post } from '@/api/http'
import Pagination from '@/components/Pagination.vue'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

interface GuestbookMessage {
  id: number
  nickname: string
  content: string
  createdAt: string
  reply?: string | null
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const messages = ref<GuestbookMessage[]>([])
const total = ref(0)
const pages = ref(0)
const loading = ref(true)
const content = ref('')
const submitting = ref(false)
const hint = ref('')

const currentPage = computed(() => Number(route.query.page ?? 1))

async function load() {
  loading.value = true
  try {
    const page = await get<{ records: GuestbookMessage[]; total: number; pages: number }>('/guestbook', {
      page: currentPage.value,
      size: 10
    })
    messages.value = page.records
    total.value = page.total
    pages.value = page.pages
  } catch {
    // 接口异常时保持空态
  } finally {
    loading.value = false
  }
}

function goPage(next: number) {
  router.push({ path: '/guestbook', query: { page: String(next) } })
}

async function submit() {
  const text = content.value.trim()
  if (!text) return
  submitting.value = true
  hint.value = ''
  try {
    await post<void>('/guestbook', { content: text })
    content.value = ''
    hint.value = '留言已提交，审核通过后展示'
  } catch (e) {
    hint.value = (e as Error).message
  } finally {
    submitting.value = false
  }
}

onMounted(load)
watch(() => route.query.page, load)
</script>

<template>
  <div class="container narrow">
    <header class="head">
      <h1>留言板</h1>
      <p class="muted">欢迎交流，留言审核后展示 · 共 {{ total }} 条</p>
    </header>

    <div v-if="userStore.isLoggedIn" class="card composer">
      <textarea
        v-model="content"
        class="composer__input"
        rows="3"
        maxlength="2000"
        placeholder="说点什么…（审核通过后展示）"
      />
      <div class="composer__foot">
        <span class="muted">{{ hint || `${content.length}/2000` }}</span>
        <button class="btn btn-primary" :disabled="submitting || !content.trim()" @click="submit">
          <Send :size="15" /> {{ submitting ? '提交中…' : '发表留言' }}
        </button>
      </div>
    </div>

    <div v-else class="card notice">
      <MessageSquare :size="18" />
      <span>登录后即可留言。</span>
      <RouterLink to="/login" class="btn btn-primary">去登录</RouterLink>
    </div>

    <div v-if="loading" class="card skeleton" />
    <div v-else-if="messages.length" class="list">
      <div v-for="message in messages" :key="message.id" class="card item">
        <div class="item__head">
          <span class="item__name">{{ message.nickname }}</span>
          <span class="muted item__time">{{ formatDateTime(message.createdAt) }}</span>
        </div>
        <p class="item__content">{{ message.content }}</p>
        <p v-if="message.reply" class="item__reply">博主回复：{{ message.reply }}</p>
      </div>
      <Pagination :current="currentPage" :pages="pages" @change="goPage" />
    </div>
    <p v-else class="muted empty">还没有留言，来做第一个吧。</p>
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

.composer {
  padding: 16px 18px;
  margin-bottom: 16px;
}

.composer__input {
  width: 100%;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--bg);
  color: var(--fg);
  padding: 10px 14px;
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  outline: none;
}

.composer__input:focus {
  border-color: var(--accent);
}

.composer__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
}

.notice {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  font-size: 14px;
  margin-bottom: 16px;
}

.notice .btn {
  margin-left: auto;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.item {
  padding: 18px 20px;
}

.item__head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 8px;
}

.item__name {
  font-weight: 600;
}

.item__content {
  margin: 0;
  font-size: 14px;
  color: var(--fg-soft);
  white-space: pre-wrap;
}

.item__reply {
  margin: 12px 0 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--bg-soft);
  font-size: 13px;
  color: var(--fg-soft);
}

.empty {
  font-size: 14px;
}

.skeleton {
  height: 120px;
}
</style>
