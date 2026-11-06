<!--
  后台根组件：只负责渲染路由出口与全局样式/消息容器。
-->
<script setup lang="ts">
import { onMounted } from 'vue'
import { useThemeStore } from '@/stores/theme'

const theme = useThemeStore()
onMounted(() => theme.init())
</script>

<template>
  <RouterView />
</template>
