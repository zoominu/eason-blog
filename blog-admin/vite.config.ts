import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 管理端独立端口 5174；生产建议部署到 /admin/ 子路径（见 DEPLOYMENT.md）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5174,
    // 同前台：允许虚拟机通过本机 IP 访问后台
    host: '0.0.0.0',
    watch: {
      ignored: ['**/*.tmpdir/**', '**/node_modules/**', '**/.git/**']
    },
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false
      }
    }
  }
})
