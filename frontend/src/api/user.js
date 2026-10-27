import request from './request'

/**
 * 账号密码登录 → LoginVO { token, userId, realName, userType, roles, passwordChangeRequired, needSetPassword }
 * @param {{ accountName: string, password: string }} data
 */
export function loginByPassword(data) {
  return request.post('/auth/login', data)
}

/**
 * 手机验证码登录（首登匹配预录名单自动建档）→ LoginVO
 * @param {{ phone: string, code: string }} data
 */
export function loginBySms(data) {
  return request.post('/auth/sms/login', data)
}

/** 发送短信验证码 */
export function sendSmsCode(phone) {
  return request.post('/auth/sms/send', { phone })
}

/** 当前身份：用户信息 + 角色 + 权限码 + 菜单树（ProfileVO） */
export function getProfile() {
  return request.get('/auth/profile')
}

/**
 * 身份一键切换（老师⇄管理员 / 学生⇄助理）。
 * @param {{ roleCode: string }} data 目标身份码，须在当前账号可用身份列表中
 * @returns {Promise<ProfileVO>} 切换后的完整身份信息
 */
export function switchIdentity(data) {
  return request.post('/auth/switch', data)
}

/** 注销 */
export function logout() {
  return request.post('/auth/logout')
}

/**
 * 敏感操作二级认证（B3）：密码确认后 300 秒内敏感操作免重复确认。
 * 密码错误返回 40000（不会触发 401 踢出登录）。
 */
export function confirmPassword(password) {
  return request.post('/auth/password/confirm', { password })
}
