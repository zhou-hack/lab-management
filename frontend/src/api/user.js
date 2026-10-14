import request from './request'

/** 账号密码登录 → { token } */
export function loginByPassword(data) {
  return request.post('/auth/login', data)
}

/** 当前用户 + 角色 + 权限 + 菜单树 */
export function getUserMenus() {
  return request.get('/auth/user_menus')
}

/** 注销 */
export function logout() {
  return request.post('/auth/logout')
}
