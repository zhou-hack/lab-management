import request from './request'

/** 账号密码登录 → { token } */
export function loginByPassword(data) {
  return request.post('/auth/login', data)
}

/** 手机验证码登录 → { token } */
export function loginByPhone(data) {
  return request.post('/auth/login_phone', data)
}

/** 发送短信验证码 */
export function sendSmsCode(phone) {
  return request.post('/auth/sms_code', { phone })
}

/** 当前用户 + 角色 + 权限 + 菜单树 */
export function getUserMenus() {
  return request.get('/auth/user_menus')
}

/** 注销 */
export function logout() {
  return request.post('/auth/logout')
}
