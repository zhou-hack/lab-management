import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    // Vitest 5.0.4 在 Vite 8（Rolldown）下默认 forks 池存在兼容问题
    // （worker 中 collectorContext.runner 为 undefined），改用 threads 池规避
    pool: 'threads'
  }
})
