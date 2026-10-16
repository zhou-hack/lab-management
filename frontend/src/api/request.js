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
    const code = error.response && error.response.data && error.response.data.code
    // 40101 需要二级认证：不清登录态，交由 withSafeAuth 弹密码确认后重试
    if (status === 401 && code === 40101) {
      const err = new Error((error.response.data && error.response.data.msg) || '敏感操作需要二次密码确认')
      err.safeAuthRequired = true
      return Promise.reject(err)
    }
    // 二级确认密码错误返回 401 是密码问题而非会话过期，不能踢登录
    if (status === 401 && (error.config?.url || '').includes('/auth/password/confirm')) {
      ElMessage.error((error.response.data && error.response.data.msg) || '密码错误')
      return Promise.reject(error)
    }
    if (status === 401) {
      return handleUnauthorized('登录已失效，请重新登录')
    }
    if (status === 403) {
      ElMessage.error('无权限')
      return Promise.reject(error)
    }
    const isTimeout = error.code === 'ECONNABORTED' || (error.message || '').includes('timeout')
    // 后端已返回业务错误体时优先展示其 msg，避免真实原因被"网络异常"掩盖
    const serverMsg = error.response && error.response.data && error.response.data.msg
    ElMessage.error(serverMsg || (isTimeout ? '请求超时，请重试' : '网络异常，请稍后重试'))
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
