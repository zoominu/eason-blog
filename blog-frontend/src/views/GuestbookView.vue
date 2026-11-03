<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { MessageSquare } from 'lucide-vue-next'
import { get } from '@/api/http'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'

interface GuestbookMessage {
  id: number
  nickname: string
  content: string
  createdAt: string
  reply?: string | null
}

const messages = ref<GuestbookMessage[]>([])
const loading = ref(true)
const userStore = useUserStore()

onMounted(async () => {
  try {
    const page = await get<{ records: GuestbookMessage[] }>('/guestbook', { page: 1, size: 20 })
    messages.value = page.records
  } catch {
    // 接口未返回时保持空态
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="container narrow">
    <header class="head">
      <h1>留言板</h1>
      <p class="muted">欢迎交流，留言审核后展示</p>
    </header>

    <div v-if="!userStore.isLoggedIn" class="card notice">
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
