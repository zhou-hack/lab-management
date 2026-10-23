import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/token'
import { useUserStore } from '@/store/user'
import AdminLayout from '@/layout/admin/AdminLayout.vue'

const staticRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    // 根路径占位记录：必须有匹配记录才能进入守卫，守卫按角色分流到
    // '/admin/item/workspace' 或 '/admin/home'（该组件实际不会被渲染）
    path: '/',
    component: () => import('@/views/front/home/FrontHome.vue')
  },
  {
    // 管理端布局（全部角色共用，与发布版一致）：本分支仅物料模块，
    // 管理员/老师侧边栏见物料管理，学生仅见首页
    path: '/admin',
    component: AdminLayout,
    redirect: '/admin/item/workspace',
    children: [
      {
        // 无参旧地址（/admin 重定向、homePath、/item 兼容跳转）统一落到默认面板
        path: 'item/workspace',
        redirect: '/admin/item/workspace/balance'
      },
      {
        // 物料管理内容区：侧边栏分组直达面板（原「物料工作台」入口已由分组导航替换）；
        // 同记录参数切换，组件实例复用，面板懒挂载 + v-show 保留筛选与分页状态
        path: 'item/workspace/:panel',
        name: 'AdminItemWorkspace',
        component: () => import('@/views/item/ItemWorkspace.vue'),
        meta: { title: '物料管理' }
      },
      {
        // 教师端：老师登录默认落地页，仅物料预约（无管理员功能）
        path: 'item/teacher',
        name: 'AdminItemTeacher',
        component: () => import('@/views/item/TeacherWorkspace.vue'),
        meta: { title: '物料预约（教师端）' }
      },
      {
        path: 'home',
        name: 'AdminHome',
        component: () => import('@/views/front/home/FrontHome.vue'),
        meta: { title: '首页' }
      }
    ]
  },
  {
    // 旧地址兼容：物料骨架页与前台首页统一改由管理端布局承载
    path: '/item',
    redirect: '/admin/item/workspace'
  },
  {
    path: '/home',
    redirect: '/admin/home'
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

  // 根路径按角色分流（管理员/老师进物料工作台，学生进前台首页）
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
