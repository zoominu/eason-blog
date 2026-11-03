import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// 开发期通过 Vite 代理把 /api 转发到后端，避免本地跨域与 Cookie 问题；
// 生产由 Nginx 同域反代，见 deploy/nginx/eason-blog.conf。
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    // 监听所有网卡：虚拟机 / 手机 / 同局域网设备才能用「本机 IP:5173」访问。
    // 只在本机开发时可改回 '127.0.0.1'。
    host: '0.0.0.0',
    // 编辑器/脚本的原子写入会生成 .xxx.tmpdir 临时目录，watcher 碰到被占用的文件会直接崩掉
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
