/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 生产环境后台地址，如 /admin/；不配置时回退到 host:5174 */
  readonly VITE_ADMIN_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
