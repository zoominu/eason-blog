<!--
  前台主布局：导航 + 内容 + 页脚；认证路由下切换全屏壁纸并让导航/页脚透明。
-->
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import SiteHeader from '@/components/SiteHeader.vue'
import SiteFooter from '@/components/SiteFooter.vue'

const route = useRoute()
const AUTH_ROUTES = new Set(['login', 'register', 'forgot-password', 'reset-password', 'verify-email'])
const isAuth = computed(() => AUTH_ROUTES.has(String(route.name)))
</script>

<template>
  <div class="layout" :class="{ 'layout--auth': isAuth }">
    <div v-if="isAuth" class="auth-wallpaper" aria-hidden="true" />
    <SiteHeader />
    <main class="main">
      <RouterView />
    </main>
    <SiteFooter />
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.main {
  flex: 1;
  padding: 96px 0 64px;
}

/* 认证页：壁纸铺满整个视口，导航栏/页脚透明叠在图片上 */
.auth-wallpaper {
  position: fixed;
  inset: 0;
  z-index: 0;
  background-color: #fff;
  background-image: url('https://api.paugram.com/wallpaper/?source=gt');
  background-size: cover;
  background-position: right center;
  background-repeat: no-repeat;
  pointer-events: none;
}

.layout--auth {
  position: relative;
  min-height: 100vh;
}

.layout--auth .main {
  flex: 1;
  display: flex;
  padding: 96px 0 24px;
}

.layout--auth :deep(.auth-page) {
  flex: 1;
  min-height: 0;
}

.layout--auth .main,
.layout--auth :deep(.footer) {
  position: relative;
  z-index: 1;
}

.layout--auth :deep(.nav),
.layout--auth :deep(.nav--scrolled) {
  background: transparent !important;
  border-bottom-color: transparent !important;
  backdrop-filter: none !important;
  -webkit-backdrop-filter: none !important;
}

.layout--auth :deep(.footer) {
  background: transparent !important;
  border-top: none !important;
  box-shadow: none !important;
}
</style>
