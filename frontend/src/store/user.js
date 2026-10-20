import { defineStore } from 'pinia'
import {
  loginByPassword,
  loginBySms,
  logout as apiLogout,
  getProfile,
  switchIdentity as apiSwitchIdentity
} from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes, getRegisteredPaths } from '@/router/dynamic'

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
    identities: [],
    currentIdentity: '',
    routesLoaded: false
  }),
  getters: {
    isAdmin: (state) => state.roles.includes('ADMIN'),
    /**
     * 当前身份中文名（顶栏身份切换器标签）。
     */
    identityLabel: (state) => {
      const current = state.currentIdentity
      const option = (state.identities || []).find((item) => item.code === current)
      return option?.label || state.userInfo?.userType || ''
    },
    /**
     * 登录落地页：优先约定首页（/admin/home、/home），
     * 后端菜单未下发首页时回退到该身份第一个已注册页面，避免跳入 404 白屏。
     */
    homePath: (state) => {
      const { admin, front } = getRegisteredPaths()
      if (state.roles.includes('ADMIN')) {
        if (admin.includes('/admin/home')) return '/admin/home'
        return admin[0] || '/login'
      }
      if (front.includes('/home')) return '/home'
      return front[0] || '/login'
    }
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
      this.identities = data.identities || []
      this.currentIdentity = data.currentIdentity || data.userType || ''
    },
    /**
     * 身份一键切换：后端按新身份重算角色/权限/菜单后返回完整 ProfileVO，
     * 前端据此换掉动态路由并回到该身份的落地页。
     */
    async switchIdentity(roleCode) {
      const data = await apiSwitchIdentity({ roleCode })
      this.userInfo = data.userId
        ? { userId: data.userId, realName: data.realName, userType: data.userType }
        : null
      this.roles = data.roles || []
      this.perms = data.permissions || []
      this.identities = data.identities || []
      this.currentIdentity = data.currentIdentity || roleCode
      // 菜单变了：先拆除旧动态路由（含通配符），再按新身份重建
      removeDynamicRoutes()
      this.menus = (data.menus || []).map(mapMenu)
      await this.buildRoutes()
    },
    async buildRoutes() {
      registerDynamicRoutes(this.menus, this.userInfo?.userType)
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
