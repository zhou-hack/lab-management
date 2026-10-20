import { defineStore } from 'pinia'
import { loginByPassword, loginByPhone, logout as apiLogout, getUserMenus } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/token'
import { registerDynamicRoutes, removeDynamicRoutes } from '@/router/dynamic'

/** 开发环境测试账号数据（后端接入后删除） */

/** 模拟管理员物料权限（与运行库管理员角色绑定一致，仅浏览器联调用；后端接入后删除） */
const ADMIN_ITEM_PERMS = [
  'item:category:list', 'item:category:create', 'item:category:update',
  'item:category:enable', 'item:category:delete', 'item:category:import', 'item:category:export',
  'item:material:list', 'item:material:create', 'item:material:update',
  'item:material:enable', 'item:material:delete', 'item:material:import', 'item:material:export',
  'item:location:list', 'item:location:create', 'item:location:update',
  'item:location:enable', 'item:location:delete',
  'item:balance:import', 'item:balance:export',
  // 申领两级审核 + 代理代领（管理员全部单据终审，含老师审核动作）
  'item:request:list', 'item:request:create', 'item:request:teacher_approve',
  'item:request:approve', 'item:request:cancel', 'item:request:issue'
]

const TEST_PROFILES = {
  STUDENT: {
    userInfo: { realName: '测试学生', username: '20230001' },
    roles: ['STUDENT'],
    // 与运行库学生角色绑定一致：申领查询(122)、申领创建(123)、实发出库(126)；
    // 学生可发起本人申领与代理代领，无审批权限
    perms: ['my:page', 'item:request:list', 'item:request:create', 'item:request:issue'],
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
    // 与运行库教师角色绑定一致：申领查询(122)、老师审核(167)；
    // 审核学生个人申领单据；代领单据绑定其为责任老师，无需再次审批
    perms: ['my:page', 'tApprove:page', 'item:request:list', 'item:request:teacher_approve'],
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
    perms: ['dashboard:view', 'equipment:edit', ...ADMIN_ITEM_PERMS],
    menus: [
      {
        menuId: 300,
        menuName: '管理端',
        menuType: 'M',
        path: '/admin',
        children: [
          { menuId: 381, menuName: '首页', menuType: 'C', path: '/admin/home', perms: 'dashboard:view' }
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
    // 管理员/老师进物料工作台；学生无物料权限（产品定稿），进管理端首页
    homePath: (state) =>
      state.roles.includes('ADMIN') || state.roles.includes('TEACHER') ? '/admin/item/workspace' : '/admin/home'
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
      // 骨架环境模拟会话恢复：test_token 无后端可拉取，按保存的角色重建（刷新免重登）
      if (this.token === 'test_token') {
        const role = sessionStorage.getItem('mock_role')
        if (role && TEST_PROFILES[role]) {
          this.loginAsTest(role)
          return
        }
        throw new Error('mock session expired')
      }
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
      // 模拟角色持久化：刷新后由 fetchUserMenus 重建会话
      sessionStorage.setItem('mock_role', role)
      this.userInfo = profile.userInfo
      this.roles = profile.roles
      this.perms = profile.perms
      // 侧边栏已由静态布局承载（仅物料模块），不再注册动态路由
      this.menus = []
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
      sessionStorage.removeItem('mock_role')
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.perms = []
      this.menus = []
      this.routesLoaded = false
    }
  }
})
