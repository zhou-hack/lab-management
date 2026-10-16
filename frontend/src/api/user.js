import request from './request'

/**
 * 认证接口（契约见 docs/RBAC实施清单.md 第四节，路径以后端 AuthController 为准）
 */

/** 账号密码登录（管理员）→ LoginVO { token, roles, passwordChangeRequired, needSetPassword, ... } */
export function loginByPassword(data) {
  return request.post('/auth/login', data)
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

/** 设置/修改密码（首次设置无密码散列免旧密码）→ { oldPassword?, newPassword } */
export function updatePassword(data) {
  return request.put('/auth/password', data)
}

/** 敏感操作二次密码确认 → { validSeconds }（期内敏感接口免再次确认） */
export function confirmPassword(password) {
  return request.post('/auth/password/confirm', { password })
}

/** 注销 */
export function logout() {
  return request.post('/auth/logout')
}
