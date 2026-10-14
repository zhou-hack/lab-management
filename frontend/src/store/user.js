import { defineStore } from 'pinia'
import { loginByPassword, logout as apiLogout, getUserMenus } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes } from '@/router/dynamic'

/**
 * 用户会话 store：登录后由 /auth/user_menus 拉取真实用户/角色/权限/菜单。
 * 产品形态 2026-10-14：纯管理端，仅 ADMIN 可用，登录直达 /admin/home。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userInfo: null,
    roles: [],
    perms: [],
    menus: [],
    routesLoaded: false
  }),
  getters: {
    isAdmin: (state) => state.roles.includes('ADMIN'),
    homePath: () => '/admin/home'
  },
  actions: {
    /**
     * 账号密码登录：{ username, password }
     */
    async login(params) {
      const data = await loginByPassword(params)
      this.token = data.token
      setToken(data.token)
    },
    async fetchUserMenus() {
      const data = await getUserMenus()
      this.userInfo = data.userInfo || null
      this.roles = data.roles || []
      this.perms = data.perms || []
      this.menus = data.menus || []
    },
    async buildRoutes() {
      registerDynamicRoutes(this.menus)
      this.routesLoaded = true
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
      this.userInfo = null
      this.roles = []
      this.perms = []
      this.menus = []
      this.routesLoaded = false
    }
  }
})
