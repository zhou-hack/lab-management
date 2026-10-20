import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/token'
import { useUserStore } from '@/store/user'
import FrontLayout from '@/layout/front/FrontLayout.vue'
import AdminLayout from '@/layout/admin/AdminLayout.vue'

/**
 * 布局与首页为静态路由，始终注册；
 * 业务页面由 router/dynamic.js 按菜单 componentKey 白名单挂到对应布局下。
 */
const staticRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/set_password',
    name: 'SetPassword',
    component: () => import('@/views/login/SetPasswordView.vue'),
    meta: { title: '设置密码' }
  },
  {
    path: '/',
    name: 'FrontLayout',
    component: FrontLayout,
    // 不设记录级 redirect：记录级重定向先于守卫执行，会绕过守卫中 '/' 按身份分流的逻辑
    children: [
      {
        path: 'home',
        name: 'FrontHome',
        component: () => import('@/views/front/home/FrontHome.vue'),
        meta: { title: '首页' }
      }
    ]
  },
  {
    path: '/admin',
    name: 'AdminLayout',
    component: AdminLayout,
    redirect: '/admin/home',
    children: [
      {
        path: 'home',
        name: 'AdminHome',
        component: () => import('@/views/admin/home/AdminHome.vue'),
        meta: { title: '首页' }
      }
    ]
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在' }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '没有访问权限' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

// 已知页面路径全集（sys_menu.route_path，与 router/dynamic.js 白名单同步维护）：
// 已登录用户直接访问其中未获授权的页面时落 403；其余未注册路径仍按 404/首页兜底
const KNOWN_PAGE_PATHS = new Set([
  '/system/user', '/system/roster', '/system/department', '/system/role', '/system/scope',
  '/system/restriction', '/system/log', '/system/policy', '/system/grant',
  '/grant'
])

const APP_TITLE = '实验室设备管理系统'

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes
})

router.beforeEach(async (to) => {
  const token = getToken()

  if (!token) {
    return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
  }

  const store = useUserStore()

  // 已登录首次进入（或刷新）：拉取身份与菜单，重建动态路由后重放行
  if (!store.routesLoaded) {
    try {
      await store.fetchProfile()
      await store.buildRoutes()
    } catch {
      // 拉取失败（含 401 已清理场景）：重置状态回登录页
      store.resetState()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    // 强制改密期间无需重放目标路由，直接进入设置密码页
    if (store.mustSetPassword && to.path !== '/set_password') {
      return { path: '/set_password' }
    }
    // 目标对当前身份不可达（如沿用旧身份的 redirect 但无权限）时回首页，避免误落 404；
    // 直接输入的地址若属于已知页面但当前身份无菜单权限，落 403
    const resolved = router.resolve(to.fullPath)
    if (resolved.matched.some((r) => r.path === '/:pathMatch(.*)*')) {
      if (!to.redirectedFrom && KNOWN_PAGE_PATHS.has(to.path)) {
        return { path: '/403' }
      }
      return store.homePath
    }
    return { ...to, replace: true }
  }

  // 强制改密拦截：标志来自登录响应 / profile，刷新后仍生效
  if (store.mustSetPassword && to.path !== '/set_password') {
    return { path: '/set_password' }
  }

  // 根路径按菜单/身份分流
  if (to.path === '/') {
    return store.homePath
  }

  if (to.path === '/login') {
    return store.homePath
  }

  // 管理端静态首页仅管理员可见，无权限访问管理端落 403；动态业务页由菜单注册与否天然隔离
  if ((to.path === '/admin' || to.path.startsWith('/admin/')) && !store.isAdmin) {
    return { path: '/403' }
  }

  // 已知页面但未为当前身份注册（无菜单权限）：落 403；其余未注册路径交由兜底规则进 404
  if (to.matched.some((r) => r.path === '/:pathMatch(.*)*') && KNOWN_PAGE_PATHS.has(to.path)) {
    return { path: '/403' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
})

export default router
