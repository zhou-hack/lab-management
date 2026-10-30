import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  // 后端地址可用 VITE_API_TARGET 覆盖（.env.local 或 shell 环境变量），
  // new_system 默认使用 8082，与原系统 8081 隔离
  // 注：loadEnv 只读 .env 文件，shell 注入的变量须走 process.env
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_TARGET || process.env.VITE_API_TARGET || 'http://localhost:8082'

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
      // new_system 固定 5175，与原系统 5174 及其他项目端口隔离
      port: 5175,
      proxy: {
        '/api/v1': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    }
  }
})
