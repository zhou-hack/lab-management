import request from './request'
import { hashPassword } from '@/utils/crypto'

/**
 * 认证接口（JWT 重写后契约，路径以后端 AuthController 为准）。
 * 口令不上明文：先按账号取 BCrypt 盐，前端复算哈希后上传比对。
 */

/** 按账号取 BCrypt 盐前缀 → { salt }（登录/二次确认前调用） */
export function getSalt(accountName) {
  return request.get('/auth/salt', { params: { accountName } })
}

/** 账号密码登录 → LoginVO { accessToken, refreshToken, expiresIn, userId, realName, userType, roles } */
export async function loginByPassword(data) {
  const { salt } = await getSalt(data.accountName)
  return request.post('/auth/login', {
    accountName: data.accountName,
    password: hashPassword(data.password, salt)
  })
}

/** 刷新令牌 → LoginVO（轮换 access + refresh） */
export function refreshToken(refreshToken) {
  return request.post('/auth/refresh', { refreshToken })
}

/** 当前身份：用户 / 角色 / 权限 / 菜单树 → ProfileVO */
export function getProfile() {
  return request.get('/auth/profile')
}

/** 注销（JWT 无状态，服务端 no-op；前端清理本地 token 即可） */
export function logout() {
  return request.post('/auth/logout')
}
