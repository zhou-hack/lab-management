import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/token'

/**
 * 统一 Axios 实例：
 * - baseURL 固定 /api/v1（与开发代理前缀一致）
 * - 请求拦截注入 Authorization: Bearer <token>
 * - 响应拦截解包 code/msg/data；401 清状态跳登录；403 提示无权限
 */
const service = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const body = response.data
    if (!body || typeof body !== 'object' || !('code' in body)) {
      return body
    }
    if (body.code === 0) {
      return body.data
    }
    if (body.code === 40100) {
      return handleUnauthorized(body.msg)
    }
    if (body.code === 40300) {
      ElMessage.error(body.msg || '无权限')
      return Promise.reject(new Error(body.msg || '无权限'))
    }
    ElMessage.error(body.msg || '请求失败')
    return Promise.reject(new Error(body.msg || '请求失败'))
  },
  (error) => {
    const status = error.response && error.response.status
    if (status === 401) {
      return handleUnauthorized('登录已失效，请重新登录')
    }
    if (status === 403) {
      ElMessage.error('无权限')
      return Promise.reject(error)
    }
    // 后端业务错误响应（400/409/500 等）携带 code/msg，直接展示真实原因，
    // 避免把「学生申请必须指定审批教师」「该时段设备已被占用」等提示误报为网络异常
    const body = error.response && error.response.data
    const serverMsg = body && typeof body === 'object' && body.msg ? body.msg : null
    if (serverMsg) {
      ElMessage.error(serverMsg)
      return Promise.reject(error)
    }
    const isTimeout = error.code === 'ECONNABORTED' || (error.message || '').includes('timeout')
    ElMessage.error(isTimeout ? '请求超时，请重试' : '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

/**
 * 401 统一处理：清理 token/用户/权限/动态路由并跳转登录页。
 * store 与 router 动态引入，避免与 store/user.js、router/index.js 形成循环依赖。
 * handlingUnauthorized 去重锁：并发请求同时 401 时只处理一次，避免重复弹窗与重复跳转。
 */
let handlingUnauthorized = false

async function handleUnauthorized(msg) {
  if (handlingUnauthorized) {
    return Promise.reject(new Error(msg || '未登录'))
  }
  handlingUnauthorized = true
  try {
    const [{ useUserStore }, { default: router }] = await Promise.all([
      import('@/store/user'),
      import('@/router')
    ])
    const store = useUserStore()
    store.resetOn401()
    ElMessage.error(msg || '登录已失效，请重新登录')
    const current = router.currentRoute.value
    if (current.path !== '/login') {
      router.push({ path: '/login', query: { redirect: current.fullPath } })
    }
  } finally {
    handlingUnauthorized = false
  }
  return Promise.reject(new Error(msg || '未登录'))
}

export default service
