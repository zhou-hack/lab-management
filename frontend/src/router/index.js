import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/token'
import { useUserStore } from '@/store/user'
import AdminLayout from '@/layout/admin/AdminLayout.vue'

/**
 * 布局与首页为静态路由，始终注册；全部账号共用同一套管理端布局与设计风格。
 * 业务页面由 router/dynamic.js 按菜单 componentKey 白名单挂到 AdminLayout 下。
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
    name: 'AdminLayout',
    component: AdminLayout,
    redirect: '/admin/home',
    children: [
      {
        path: 'admin/home',
        name: 'HomeView',
        component: () => import('@/views/home/HomeView.vue'),
        meta: { title: '首页' }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '没有访问权限' }
  },
  // 兜底 404 必须直接渲染组件、不能 redirect：重定向在路由匹配阶段即完成，
  // beforeEach 拿到的 to 已变成 /404，守卫注册完动态路由后重放会丢失原始目标路径
  {
    path: '/:pathMatch(.*)*',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
]

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
    // 用完整路径重放：动态路由刚注册，需触发新一轮匹配才能命中；
    // 不用 {...to} 展开是避免 to 命中兜底 404 记录时携带其记录信息
    return to.fullPath
  }

  // 强制改密拦截：标志来自登录响应 / profile，刷新后仍生效
  if (store.mustSetPassword && to.path !== '/set_password') {
    return { path: '/set_password' }
  }

  if (to.path === '/login') {
    return store.homePath
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
})

export default router
