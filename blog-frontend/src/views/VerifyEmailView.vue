<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { BadgeCheck } from 'lucide-vue-next'
import { authApi } from '@/api'

const route = useRoute()
const token = computed(() => String(route.query.token ?? ''))
const status = ref<'loading' | 'ok' | 'fail'>('loading')
const message = ref('')

onMounted(async () => {
  if (!token.value) {
    status.value = 'fail'
    message.value = '链接缺少 token，请从邮件重新打开。'
    return
  }
  try {
    await authApi.verifyEmail(token.value)
    status.value = 'ok'
    message.value = '邮箱验证成功，感谢确认。'
  } catch (e) {
    status.value = 'fail'
    message.value = (e as Error).message
  }
})
</script>

<template>
  <div class="container narrow">
    <div class="card panel">
      <BadgeCheck v-if="status === 'ok'" :size="28" class="icon ok" />
      <h1>邮箱验证</h1>
      <p v-if="status === 'loading'" class="muted">正在验证…</p>
      <p v-else-if="status === 'ok'" class="ok">{{ message }}</p>
      <p v-else class="error">{{ message }}</p>
      <RouterLink to="/" class="btn wide">回首页</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 460px;
  padding-top: 40px;
}

.panel {
  padding: 28px 30px;
  text-align: center;
}

.panel h1 {
  margin: 8px 0;
  font-size: 22px;
}

.icon {
  margin: 0 auto;
}

.ok {
  color: var(--accent);
  font-size: 14px;
}

.error {
  color: #e5484d;
  font-size: 14px;
}

.wide {
  width: 100%;
  justify-content: center;
  margin-top: 18px;
}
</style>
