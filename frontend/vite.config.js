import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api/v1': {
        // 后端独立端口 8081：8080 常被本机其他项目（cmzs-backend）抢占
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
