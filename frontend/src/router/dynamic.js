import router from '@/router'
import FrontLayout from '@/layout/front/FrontLayout.vue'
import AdminLayout from '@/layout/admin/AdminLayout.vue'

/**
 * 本地组件白名单：后端 sys_menu.path → 本地视图组件。
 * 禁止直接执行后端下发的组件路径或代码；未命中白名单的菜单项丢弃并告警。
 * 业务页面落地时在此登记（key 与 sys_menu.path 一致）。
 */
const viewModules = {
  '/home': () => import('@/views/front/home/FrontHome.vue'),
  '/admin/home': () => import('@/views/admin/home/AdminHome.vue'),
  '/admin/space/labs': () => import('@/views/admin/space/LabList.vue'),
  '/admin/space/center': () => import('@/views/admin/space/SpaceCenter.vue'),
  '/admin/booking/labs': () => import('@/views/admin/booking/BookingList.vue'),
  '/admin/booking/approval': () => import('@/views/admin/booking/BookingApproval.vue'),
  '/booking/labs': () => import('@/views/booking/BookingList.vue'),
  '/grant': () => import('@/views/grant/GrantManage.vue')
}

const addedRouteNames = []

/**
 * 最近一次注册成功的页面路径（已命中本地组件白名单）。
 * 供登录落地页回退使用：后端菜单缺首页（如 /admin/home）时避免跳入 404。
 */
let registeredPaths = { admin: [], front: [] }

export function getRegisteredPaths() {
  return registeredPaths
}

function walkMenus(menus, visit) {
  for (const menu of menus || []) {
    visit(menu)
    if (menu.children && menu.children.length) {
      walkMenus(menu.children, visit)
    }
  }
}

/**
 * 业务规则（2026-10-17）：场地预约仅教师可发起；学生为纯只读——
 * 后端仅下发 view 权限的「预约情况」菜单（sys_menu 3004，同路径 /booking/labs），
 * 页面内发起/撤销按钮由 v-permission 与身份判断隐藏，写接口由后端权限拦截。
 * 故同一路由组件对学生开放注册，不再按身份硬封锁。
 */
const STUDENT_BLOCKED_FRONT_PATHS = []

/** 将后端菜单树映射为动态路由并注册；front 子树挂 FrontLayout，admin 子树挂 AdminLayout */
export function registerDynamicRoutes(menus, userType) {
  const frontChildren = []
  const adminChildren = []
  registeredPaths = { admin: [], front: [] }
  const isStudent = userType === 'STUDENT'

  walkMenus(menus, (menu) => {
    if (menu.menuType !== 'C' || !menu.path) return
    if (isStudent && STUDENT_BLOCKED_FRONT_PATHS.includes(menu.path)) {
      console.warn(`[router] 学生身份不开放页面「${menu.menuName}」(${menu.path})，已跳过`)
      return
    }
    const load = viewModules[menu.path]
    if (!load) {
      console.warn(`[router] 菜单「${menu.menuName}」(${menu.path}) 未命中本地组件白名单，已跳过`)
      return
    }
    const route = {
      path: menu.path,
      name: `dyn_${menu.menuId}`,
      component: load,
      meta: { title: menu.menuName, icon: menu.icon || null, perms: menu.perms || null, menuId: menu.menuId }
    }
    if (menu.path.startsWith('/admin')) {
      adminChildren.push(route)
      registeredPaths.admin.push(menu.path)
    } else {
      frontChildren.push(route)
      registeredPaths.front.push(menu.path)
    }
  })

  if (adminChildren.length) {
    router.addRoute({
      path: '/admin',
      name: 'AdminLayout',
      component: AdminLayout,
      redirect: adminChildren[0].path,
      children: adminChildren
    })
    addedRouteNames.push('AdminLayout')
  }
  if (frontChildren.length) {
    // 前台视图挂根路径，URL 不带前缀（如 /home）
    router.addRoute({
      path: '/',
      name: 'FrontLayout',
      component: FrontLayout,
      redirect: frontChildren[0].path,
      children: frontChildren
    })
    addedRouteNames.push('FrontLayout')
  }

  // 动态路由就绪后再挂通配符，避免业务路径在菜单注册前被提前吞成 404
  if (!router.hasRoute('NotFoundCatchAll')) {
    router.addRoute({ path: '/:pathMatch(.*)*', name: 'NotFoundCatchAll', redirect: '/404' })
    addedRouteNames.push('NotFoundCatchAll')
  }
}

/** 退出登录/401 时移除全部动态路由 */
export function removeDynamicRoutes() {
  while (addedRouteNames.length) {
    const name = addedRouteNames.pop()
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  }
  registeredPaths = { admin: [], front: [] }
}
