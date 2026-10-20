import { defineStore } from 'pinia'
import { loginByPassword, loginByPhone, logout as apiLogout, getProfile, switchRole as apiSwitchRole } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes } from '@/router/dynamic'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    user: null, // { userId, realName, userType }
    roles: [],
    permissions: [],
    menus: [],
    activeRole: '', // 活动角色（当前生效的身份视图）
    switchableRoles: [], // 可切换的其他持有角色（不含动态 ASSISTANT）
    routesLoaded: false,
    needSetPassword: false,
    passwordChangeRequired: false
  }),
  getters: {
    isAdmin: (state) => state.activeRole === 'ADMIN',
    /** 任一强制改密标志命中即拦截到设置密码页 */
    mustSetPassword: (state) => state.needSetPassword || state.passwordChangeRequired,
    /** 落地点：菜单树中第一个页面；无菜单时按身份回退到静态首页 */
    homePath: (state) => {
      const queue = [...state.menus]
      while (queue.length) {
        const node = queue.shift()
        if (node.menuType === 'PAGE' && node.routePath) {
          return node.routePath
        }
        if (node.children && node.children.length) {
          queue.push(...node.children)
        }
      }
      return state.activeRole === 'ADMIN' ? '/admin/home' : '/home'
    }
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
      this.applyProfile(data)
    },
    /** 身份响应落库（profile 与 switchRole 共用） */
    applyProfile(data) {
      this.user = { userId: data.userId, realName: data.realName, userType: data.userType }
      this.roles = data.roles || []
      this.permissions = data.permissions || []
      this.menus = data.menus || []
      this.activeRole = data.activeRole || ''
      this.switchableRoles = data.switchableRoles || []
      this.needSetPassword = !!data.needSetPassword
      this.passwordChangeRequired = !!data.passwordChangeRequired
    },
    /**
     * 切换活动角色：后端按新身份重新装配权限/菜单，前端重建动态路由。
     * 返回新身份落地点，由调用方跳转。
     */
    async switchRole(roleCode) {
      const data = await apiSwitchRole(roleCode)
      removeDynamicRoutes()
      this.applyProfile(data)
      registerDynamicRoutes(this.menus)
      this.routesLoaded = true
      return this.homePath
    },
    async buildRoutes() {
      registerDynamicRoutes(this.menus)
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
      this.activeRole = ''
      this.switchableRoles = []
      this.routesLoaded = false
      this.needSetPassword = false
      this.passwordChangeRequired = false
    }
  }
})
