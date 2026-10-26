import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/admin': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/internal': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    target: 'es2022',
    // antd 全量 vendor 属长缓存资源（gzip ~360KB），放宽告警阈值避免误导性提示
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined
          // antd + rc-* + icons + dayjs 相互依赖，必须同 chunk 避免循环初始化
          if (id.includes('antd') || id.includes('@ant-design') || id.includes('rc-') || id.includes('dayjs')) {
            return 'vendor-antd'
          }
          if (id.includes('react') || id.includes('scheduler')) return 'vendor-react'
          if (id.includes('axios')) return 'vendor-axios'
          return undefined
        },
      },
    },
  },
})
