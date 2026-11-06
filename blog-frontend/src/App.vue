<script setup lang="ts">
/**
 * 应用根组件：只做三件事——渲染路由、挂全局 Toast、启动时恢复登录态。
 *
 * <p>登录态由 HttpOnly Cookie 承载，前端无法直接读取，所以每次刷新都要
 * 调一次 `/auth/me`；失败视为未登录，不阻塞页面渲染。</p>
 */
import { onMounted } from 'vue'
import ToastHost from '@/components/ToastHost.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// 全局只请求一次 /auth/me，恢复 Cookie 登录态
onMounted(() => {
  userStore.fetchMe()
})
</script>

<template>
  <RouterView v-slot="{ Component }">
    <Transition name="page" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>
  <ToastHost />
</template>

<style>
.page-enter-active,
.page-leave-active {
  transition: opacity 0.18s ease;
}

.page-enter-from,
.page-leave-to {
  opacity: 0;
}
</style>
