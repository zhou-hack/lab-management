import { defineStore } from 'pinia'
import {
  loginByPassword,
  logout as apiLogout,
  getProfile,
  switchRole as apiSwitchRole
} from '@/api/user'
import { getToken, setToken, setRefreshToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes } from '@/router/dynamic'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    user: null, // { userId, realName, userType, accountName }
    roles: [],
    permissions: [],
    menus: [],
    activeRole: '', // 活动角色（当前生效的身份视图，多角色用户可切换）
    switchableRoles: [], // 可切换的其他持有角色（不含动态 ASSISTANT）
    routesLoaded: false
  }),
  getters: {
    /** 全部账号统一直落到管理端首页（同一布局与设计风格） */
    homePath: () => '/admin/home'
  },
  actions: {
    /** 账密登录：{ accountName, password } → 双 token 入库 */
    async login(params) {
      const data = await loginByPassword({ accountName: params.accountName, password: params.password })
      this.token = data.accessToken
      setToken(data.accessToken)
      setRefreshToken(data.refreshToken)
      this.applyLogin(data)
    },
    /** 登录/切换身份响应落库（LoginVO） */
    applyLogin(data) {
      this.user = {
        userId: data.userId,
        realName: data.realName,
        userType: data.userType,
        accountName: data.accountName
      }
      this.roles = data.roles || []
      this.activeRole = data.activeRole || ''
    },
    async fetchProfile() {
      const data = await getProfile()
      this.applyProfile(data)
    },
    /** 身份响应落库（ProfileVO） */
    applyProfile(data) {
      this.user = {
        userId: data.userId,
        realName: data.realName,
        userType: data.userType,
        accountName: data.accountName
      }
      this.roles = data.roles || []
      this.permissions = data.permissions || []
      this.menus = data.menus || []
      this.activeRole = data.activeRole || ''
      this.switchableRoles = data.switchableRoles || []
    },
    /**
     * 切换活动角色：后端按新身份重签 token 对并重新装配权限/菜单，
     * 前端保存新 token 后拉取身份并重建动态路由。返回新身份落地点。
     */
    async switchRole(roleCode) {
      const data = await apiSwitchRole(roleCode)
      this.token = data.accessToken
      setToken(data.accessToken)
      setRefreshToken(data.refreshToken)
      removeDynamicRoutes()
      await this.fetchProfile()
      registerDynamicRoutes(this.menus)
      this.routesLoaded = true
      return this.homePath
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
      this.user = null
      this.roles = []
      this.permissions = []
      this.menus = []
      this.activeRole = ''
      this.switchableRoles = []
      this.routesLoaded = false
    }
  }
})
