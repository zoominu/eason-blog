/**
 * 管理后台入口：Element Plus（中文 locale）+ Pinia + Router。
 *
 * <p>后台构建时通过 `ADMIN_BASE=/admin/` 设置 Vite base，
 * 路由使用 `createWebHistory(import.meta.env.BASE_URL)`，因此部署在子路径也能正常刷新。</p>
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
