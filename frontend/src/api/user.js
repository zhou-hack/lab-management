import request from './request'
import { encryptPassword } from '@/utils/crypto'

/**
 * 认证接口（契约见 docs/RBAC实施清单.md 第四节，路径以后端 AuthController 为准）。
 * 口令字段一律 RSA 密文上传：先经 encryptPassword 加密，明文不进入请求报文。
 */

/** 账号密码登录（管理员）→ LoginVO { token, roles, passwordChangeRequired, needSetPassword, ... } */
export async function loginByPassword(data) {
  return request.post('/auth/login', {
    accountName: data.accountName,
    password: await encryptPassword(data.password)
  })
}

/** 发送短信验证码 → { ttlSeconds, devCode }（devCode 仅 dev 环境模拟通道回显） */
export function sendSmsCode(phone) {
  return request.post('/auth/sms/send', { phone })
}

/** 短信验证码登录（学生/老师，首登匹配名单自动建档）→ LoginVO */
export function loginByPhone(data) {
  return request.post('/auth/sms/login', data)
}

/** 当前身份：用户 / 角色 / 权限 / 菜单树 + 强制改密标志 → ProfileVO */
export function getProfile() {
  return request.get('/auth/profile')
}

/** 切换活动角色（多角色用户会话内切换身份视图）→ ProfileVO（新身份全量装配） */
export function switchRole(roleCode) {
  return request.post('/auth/switch_role', { roleCode })
}

/** 设置/修改密码（首次设置无密码散列免旧密码）→ { oldPassword?, newPassword }（均为密文） */
export async function updatePassword(data) {
  return request.put('/auth/password', {
    oldPassword: data.oldPassword ? await encryptPassword(data.oldPassword) : undefined,
    newPassword: await encryptPassword(data.newPassword)
  })
}

/** 敏感操作二次密码确认 → { validSeconds }（期内敏感接口免再次确认） */
export async function confirmPassword(password) {
  return request.post('/auth/password/confirm', { password: await encryptPassword(password) })
}

/** 注销 */
export function logout() {
  return request.post('/auth/logout')
}
