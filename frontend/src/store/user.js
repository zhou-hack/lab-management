import { defineStore } from 'pinia'
import { loginByPassword, loginByPhone, logout as apiLogout, getUserMenus } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes } from '@/router/dynamic'

/**
 * 开发环境测试账号数据（后端接入后删除）。
 * perms 与 sql/ 固定设备.sql、equipment_module.sql 种子一致（M/C/F 菜单格式）；
 * 生产菜单走未来 user_menus 接口并映射 DIRECTORY/PAGE/ACTION。
 */
const BOOKING_PERMS_STUDENT = [
  'my:page',
  'space:lab:list',
  'equipment:asset:list',
  'equipment:booking:create',
  'equipment:booking:cancel',
  'equipment:booking:list',
  'equipment:booking:checkout',
  'equipment:booking:return'
]

const FRONT_MENUS = (extra) => ({
  menuId: 100,
  menuName: '设备服务',
  menuType: 'M',
  path: '/',
  children: [
    { menuId: 130, menuName: '首页', menuType: 'C', path: '/home', perms: 'my:page' },
    { menuId: 131, menuName: '发起借用', menuType: 'C', path: '/equipment/booking/create', perms: 'equipment:booking:create' },
    { menuId: 132, menuName: '我的借用', menuType: 'C', path: '/equipment/bookings/mine', perms: 'equipment:booking:list' },
    ...(extra || [])
  ]
})

const TEST_PROFILES = {
  STUDENT: {
    userInfo: { realName: '测试学生', username: '20230001' },
    roles: ['STUDENT'],
    perms: BOOKING_PERMS_STUDENT,
    menus: [FRONT_MENUS()]
  },
  TEACHER: {
    userInfo: { realName: '测试老师', username: 'T1001' },
    roles: ['TEACHER'],
    perms: [
      ...BOOKING_PERMS_STUDENT,
      'equipment:booking:teacher_approve'
    ],
    menus: [
      FRONT_MENUS([
        { menuId: 133, menuName: '借用审批', menuType: 'C', path: '/equipment/approvals', perms: 'equipment:booking:teacher_approve' }
      ])
    ]
  },
  ADMIN: {
    userInfo: { realName: '测试管理员', username: 'admin' },
    roles: ['ADMIN'],
    perms: [
      'dashboard:view',
      'space:campus:list',
      'space:campus:create',
      'space:campus:update',
      'space:campus:delete',
      'space:building:list',
      'space:building:create',
      'space:building:update',
      'space:building:delete',
      'space:lab:list',
      'space:lab:create',
      'space:lab:update',
      'space:lab:delete',
      'equipment:category:list',
      'equipment:category:create',
      'equipment:category:update',
      'equipment:category:delete',
      'equipment:model:list',
      'equipment:model:create',
      'equipment:model:update',
      'equipment:model:delete',
      'equipment:asset:list',
      'equipment:asset:create',
      'equipment:asset:update',
      'equipment:asset:delete',
      'equipment:asset:transfer',
      'equipment:asset:classify',
      'equipment:booking:create',
      'equipment:booking:cancel',
      'equipment:booking:list',
      'equipment:booking:teacher_approve',
      'equipment:booking:admin_approve',
      'equipment:booking:checkout',
      'equipment:booking:return'
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
            menuId: 390,
            menuName: '基础数据',
            menuType: 'M',
            path: '/admin/base',
            children: [
              { menuId: 391, menuName: '空间管理', menuType: 'C', path: '/admin/space', perms: 'space:campus:list' }
            ]
          },
          {
            menuId: 395,
            menuName: '设备管理',
            menuType: 'M',
            path: '/admin/equipment_root',
            children: [
              { menuId: 396, menuName: '分类管理', menuType: 'C', path: '/admin/equipment/category', perms: 'equipment:category:list' },
              { menuId: 397, menuName: '型号管理', menuType: 'C', path: '/admin/equipment/model', perms: 'equipment:model:list' },
              { menuId: 398, menuName: '设备台账', menuType: 'C', path: '/admin/equipment/asset', perms: 'equipment:asset:list' },
              { menuId: 399, menuName: '借用管理', menuType: 'C', path: '/admin/equipment/booking', perms: 'equipment:booking:list' }
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
