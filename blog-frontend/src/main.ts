/**
 * 前台入口：创建 Vue 应用、安装 Pinia/Router，并在挂载前初始化主题。
 *
 * <p>主题必须在 mount 前设置，否则首屏会先渲染默认色再切换，出现闪白/闪黑。</p>
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/main.css'
import { useThemeStore } from './stores/theme'

const app = createApp(App)
app.use(createPinia())

// 必须在挂载前初始化主题，避免首屏闪白
useThemeStore().init()

app.use(router)
app.mount('#app')
