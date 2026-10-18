import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/token'
import { useUserStore } from '@/store/user'

const staticRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在' }
  }
  // 注意：通配符 404 兜底不能放在静态路由里，否则动态路由（后端菜单下发）
  // 注册前，所有业务路径都会先被通配符吞成 /404，导致登录/刷新后白屏。
  // 通配符在 registerDynamicRoutes() 完成后再注册，见 router/dynamic.js。
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

  // 已登录首次进入（或刷新）：拉取用户与菜单，重建动态路由后重放行
  if (!store.routesLoaded) {
    try {
      await store.fetchUserMenus()
      await store.buildRoutes()
      // 根路径在动态路由就绪后按身份分流；此时通配符已注册，
      // 不能重放 '/'（会被通配符吞成 404），必须直接指向落地首页
      if (to.path === '/') {
        return { path: store.homePath, replace: true }
      }
      // 其余路径（如刷新业务页）用字符串重新匹配刚注册的动态路由
      return { path: to.fullPath, replace: true }
    } catch {
      // 拉取失败（含 401 已清理场景）：重置状态回登录页
      store.resetState()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // 根路径按角色分流（前台视图挂根路径，无前缀）
  if (to.path === '/') {
    return store.homePath
  }

  if (to.path === '/login') {
    return store.homePath
  }

  if (to.meta && to.meta.perms && !store.perms.includes(to.meta.perms)) {
    return { path: '/404' }
  }

  // 业务规则：场地预约不对学生开放（路由注册层已过滤，此处兜底防手输 URL/历史记录进入）
  if (store.userInfo?.userType === 'STUDENT' && to.path.startsWith('/booking/')) {
    return { path: store.homePath, replace: true }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
})

export default router
