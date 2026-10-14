import { defineStore } from 'pinia'
import { loginByPassword, loginByPhone, logout as apiLogout, getUserMenus } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes } from '@/router/dynamic'

/** 开发环境测试账号数据（后端接入后删除） */
const TEST_PROFILES = {
  STUDENT: {
    userInfo: { realName: '测试学生', username: '20230001' },
    roles: ['STUDENT'],
    perms: ['my:page'],
    menus: [
      {
        menuId: 100,
        menuName: '学生视图',
        menuType: 'M',
        path: '/',
        children: [{ menuId: 130, menuName: '首页', menuType: 'C', path: '/home', perms: 'my:page' }]
      }
    ]
  },
  TEACHER: {
    userInfo: { realName: '测试老师', username: 'T1001' },
    roles: ['TEACHER'],
    perms: ['my:page', 'tApprove:page'],
    menus: [
      {
        menuId: 200,
        menuName: '老师视图',
        menuType: 'M',
        path: '/',
        children: [{ menuId: 230, menuName: '首页', menuType: 'C', path: '/home', perms: 'my:page' }]
      }
    ]
  },
  ADMIN: {
    userInfo: { realName: '测试管理员', username: 'admin' },
    roles: ['ADMIN'],
    perms: [
      'dashboard:view',
      'equipment:edit',
      'space:space:list',
      'space:space:read',
      'space:space:create',
      'space:space:update',
      'space:space:delete'
    ],
    menus: [
      {
        menuId: 300,
        menuName: '管理端',
        menuType: 'M',
        path: '/admin',
        children: [
          { menuId: 381, menuName: '首页', menuType: 'C', path: '/admin/home', perms: 'dashboard:view' },
          {
            menuId: 1001,
            menuName: '空间管理',
            menuType: 'M',
            path: '/admin/space',
            children: [
              { menuId: 1002, menuName: '场地管理', menuType: 'C', path: '/admin/space/labs', perms: 'space:space:list' }
            ]
          }
        ]
      }
    ]
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
     * 登录（两种模式共用）：
     * { mode: 'password', username, password } 或 { mode: 'phone', phone, code }
     */
    async login(params) {
      const data =
        params.mode === 'phone'
          ? await loginByPhone({ phone: params.phone, code: params.code })
          : await loginByPassword({ username: params.username, password: params.password })
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
    /**
     * 开发环境测试登录：注入模拟用户/角色/权限/菜单并注册动态路由。
     * 仅在 import.meta.env.DEV 下由登录页测试按钮调用，后端接入后删除。
     */
    loginAsTest(role) {
      const profile = TEST_PROFILES[role]
      if (!profile) return
      removeDynamicRoutes()
      this.token = 'test_token'
      setToken('test_token')
      this.userInfo = profile.userInfo
      this.roles = profile.roles
      this.perms = profile.perms
      this.menus = profile.menus
      registerDynamicRoutes(this.menus)
      this.routesLoaded = true
    },
    async buildRoutes() {
      registerDynamicRoutes(this.menus)
      this.routesLoaded = true
    },
    async logout() {
      try {
        await apiLogout()
      } catch {
        // 后端未就绪或网络异常时忽略注销接口失败，前端状态照常清理
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
