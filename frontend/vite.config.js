import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  // 后端地址可用 VITE_API_TARGET 覆盖（.env.local 或 shell 环境变量），
  // 如本机 8080 被占用时指向 http://localhost:8081
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      // 监听全部网卡（0.0.0.0），允许局域网设备访问开发服务
      host: '0.0.0.0',
      // 固定 5174（5173 留给本机其他项目）
      port: 5174,
      proxy: {
        '/api/v1': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    }
  }
})
