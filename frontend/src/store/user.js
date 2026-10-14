import { defineStore } from 'pinia'
import { loginByPassword, loginBySms, logout as apiLogout, getProfile } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes } from '@/router/dynamic'

/**
 * 后端菜单 menuType（sys_menu.menu_type）→ 前端菜单类型
 * 前端约定：'M' 目录 / 'C' 页面 / 'F' 按钮
 */
const MENU_TYPE_MAP = { DIRECTORY: 'M', PAGE: 'C', ACTION: 'F' }

/** 后端 MenuVO 树 → 前端菜单结构（menuId/menuName/menuType/path/children） */
function mapMenu(node) {
  if (!node) return null
  return {
    menuId: node.id,
    menuName: node.menuName,
    menuType: MENU_TYPE_MAP[node.menuType] || 'F',
    path: node.routePath,
    children: (node.children || []).map(mapMenu)
  }
}

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
    homePath: (state) => (state.roles.includes('ADMIN') ? '/admin/home' : '/home')
  },
  actions: {
    /**
     * 登录（两种模式共用，与后端 AuthController 契约对齐）：
     * { mode: 'password', accountName, password } 或 { mode: 'phone', phone, code }
     */
    async login(params) {
      const data =
        params.mode === 'phone'
          ? await loginBySms({ phone: params.phone, code: params.code })
          : await loginByPassword({ accountName: params.accountName, password: params.password })
      this.token = data.token
      setToken(data.token)
    },
    /** 登录/刷新时拉取当前身份（ProfileVO），映射为前端状态 */
    async fetchUserMenus() {
      const data = await getProfile()
      this.userInfo = data.userId
        ? { userId: data.userId, realName: data.realName, userType: data.userType }
        : null
      this.roles = data.roles || []
      this.perms = data.permissions || []
      this.menus = (data.menus || []).map(mapMenu)
    },
    async buildRoutes() {
      registerDynamicRoutes(this.menus)
      this.routesLoaded = true
    },
    async logout() {
      try {
        await apiLogout()
      } catch {
        // 后端不可达或会话已过期时忽略注销接口失败，前端状态照常清理
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
