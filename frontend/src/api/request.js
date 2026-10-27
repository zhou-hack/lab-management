import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken } from '@/utils/token'

/**
 * 统一 Axios 实例：
 * - baseURL 固定 /api/v1（与开发代理前缀一致）
 * - 请求拦截注入 Authorization: Bearer <token>
 * - 响应拦截解包 code/msg/data；401 清状态跳登录；403 提示无权限
 * - B3：40301（需二级认证）统一弹密码确认框，确认通过后自动重试原请求
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
    if (body.code === 40301) {
      return handleNeedSafeAuth(response.config)
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
    const isTimeout = error.code === 'ECONNABORTED' || (error.message || '').includes('timeout')
    ElMessage.error(isTimeout ? '请求超时，请重试' : '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

/**
 * B3 二级认证：敏感操作被 40301 拦截时，弹密码确认框；确认成功（openSafe 300s）
 * 后自动重发原请求。safeAuthPromise 去重锁：并发敏感请求只弹一次框。
 * config.__safeRetried 防循环重试（确认后仍 40301 说明确认未生效，直接报错）。
 */
let safeAuthPromise = null

function handleNeedSafeAuth(config) {
  if (config.__safeRetried) {
    ElMessage.error('二级认证未生效，请重新确认密码后再试')
    return Promise.reject(new Error('二级认证未生效'))
  }
  if (!safeAuthPromise) {
    safeAuthPromise = promptSafeAuth().finally(() => {
      safeAuthPromise = null
    })
  }
  return safeAuthPromise.then(() => {
    config.__safeRetried = true
    return service(config)
  })
}

async function promptSafeAuth() {
  const { value } = await ElMessageBox.prompt(
    '当前为敏感操作（删除/启停/审批/强制取消/签到清点/授权等），请输入登录密码完成二级认证，确认后 5 分钟内同类操作免重复确认',
    '二级认证',
    {
      inputType: 'password',
      inputPlaceholder: '登录密码',
      inputValidator: (v) => (v && v.trim() ? true : '请输入登录密码'),
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    }
  )
  const { confirmPassword } = await import('@/api/user')
  // 密码错误由后端返回 40000，拦截器统一提示；此处不再重复提示
  await confirmPassword(value.trim())
  ElMessage.success('二级认证通过，5 分钟内敏感操作免确认')
}

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
