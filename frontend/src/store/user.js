import { defineStore } from 'pinia'
import { loginByPassword, loginByPhone, logout as apiLogout, getProfile } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes, getRegisteredPaths } from '@/router/dynamic'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    user: null, // { userId, realName, userType }
    roles: [],
    permissions: [],
    menus: [],
    routesLoaded: false,
    needSetPassword: false,
    passwordChangeRequired: false
  }),
  getters: {
    /** 任一强制改密标志命中即拦截到设置密码页 */
    mustSetPassword: (state) => state.needSetPassword || state.passwordChangeRequired,
    /** 全部账号统一直落到管理端首页（同一布局与设计风格） */
    homePath: () => '/admin/home'
  },
  actions: {
    /**
     * 登录（两种模式共用）：
     * { mode: 'password', accountName, password } 或 { mode: 'phone', phone, code }
     */
    async login(params) {
      const data =
        params.mode === 'phone'
          ? await loginByPhone({ phone: params.phone, code: params.code })
          : await loginByPassword({ accountName: params.accountName, password: params.password })
      this.token = data.token
      setToken(data.token)
      this.needSetPassword = !!data.needSetPassword
      this.passwordChangeRequired = !!data.passwordChangeRequired
    },
    async fetchProfile() {
      const data = await getProfile()
      this.user = { userId: data.userId, realName: data.realName, userType: data.userType }
      this.roles = data.roles || []
      this.permissions = data.permissions || []
      this.menus = data.menus || []
      this.needSetPassword = !!data.needSetPassword
      this.passwordChangeRequired = !!data.passwordChangeRequired
    },
    async buildRoutes() {
      registerDynamicRoutes(this.menus, this.userInfo?.userType)
      this.routesLoaded = true
    },
    /** 设置密码成功后清除强制改密标志 */
    clearPasswordFlags() {
      this.needSetPassword = false
      this.passwordChangeRequired = false
    },
    async logout() {
      try {
        await apiLogout()
      } catch {
        // 网络异常时忽略注销接口失败，前端状态照常清理
      }
      this.resetState()
    },
    resetOn401() {
      this.resetState()
    },
    resetState() {
      removeDynamicRoutes()
      removeToken()
      this.token = ''
      this.user = null
      this.roles = []
      this.permissions = []
      this.menus = []
      this.routesLoaded = false
      this.needSetPassword = false
      this.passwordChangeRequired = false
    }
  }
})
