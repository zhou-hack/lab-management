import { defineStore } from 'pinia'
import { loginByPassword, logout as apiLogout, getUserMenus } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes } from '@/router/dynamic'

/**
 * 用户会话 store：登录后由 /auth/user_menus 拉取真实用户/角色/权限/菜单。
 * 三权级形态（2026-10-19）：ADMIN 进 /home，学生/教师进第一个用户端页面。
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
    /**
     * 登录落地页：菜单含 /admin 子树（管理员）进固定首页 /home；
     * 学生/教师（用户端菜单挂 NULL 根）进第一个可见 PAGE。
     */
    homePath: (state) => {
      const menus = state.menus || []
      const hasAdminTree = menus.some((m) => (m.path || '').startsWith('/admin'))
      if (hasAdminTree || !menus.length) {
        return '/home'
      }
      const firstPage = menus.find((m) => m.menuType === 'C' && m.path)
      return firstPage ? firstPage.path : '/home'
    }
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
