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
