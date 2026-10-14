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
  },
  { path: '/:pathMatch(.*)*', redirect: '/404' }
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
      return { ...to, replace: true }
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

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${APP_TITLE}` : APP_TITLE
})

export default router
