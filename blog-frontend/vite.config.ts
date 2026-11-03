import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// Codespaces 里页面通过 https://<name>-5173.app.github.dev 访问（443），
// HMR 的 websocket 也必须走 443/wss，否则热更新连不上（页面本身仍可用）。
const inCodespaces = process.env.CODESPACES === 'true'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    // 监听所有网卡：虚拟机 / 手机 / 同局域网设备才能用「本机 IP:5173」访问
    host: '0.0.0.0',
    // 允许 Codespaces 的 *.app.github.dev 域名，以及局域网里的任意主机名
    allowedHosts: true,
    hmr: inCodespaces ? { clientPort: 443, protocol: 'wss' } : undefined,
    // 编辑器/脚本的原子写入会生成 .xxx.tmpdir 临时目录，watcher 碰到被占用的文件会直接崩掉
    watch: {
      ignored: ['**/*.tmpdir/**', '**/node_modules/**', '**/.git/**']
    },
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: false
      },
      // SEO/RSS 由后端生成，开发环境也走代理，避免 5173 上 404
      '/sitemap.xml': { target: 'http://127.0.0.1:8080', changeOrigin: false },
      '/robots.txt': { target: 'http://127.0.0.1:8080', changeOrigin: false },
      '/rss.xml': { target: 'http://127.0.0.1:8080', changeOrigin: false },
      '/uploads': { target: 'http://127.0.0.1:8080', changeOrigin: false }
    }
  }
})
