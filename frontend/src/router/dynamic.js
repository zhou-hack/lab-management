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
  '/admin/space/labs': () => import('@/views/admin/space/LabList.vue')
}

const addedRouteNames = []

function walkMenus(menus, visit) {
  for (const menu of menus || []) {
    visit(menu)
    if (menu.children && menu.children.length) {
      walkMenus(menu.children, visit)
    }
  }
}

/** 将后端菜单树映射为动态路由并注册；front 子树挂 FrontLayout，admin 子树挂 AdminLayout */
export function registerDynamicRoutes(menus) {
  const frontChildren = []
  const adminChildren = []

  walkMenus(menus, (menu) => {
    if (menu.menuType !== 'C' || !menu.path) return
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
    } else {
      frontChildren.push(route)
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
}

/** 退出登录/401 时移除全部动态路由 */
export function removeDynamicRoutes() {
  while (addedRouteNames.length) {
    const name = addedRouteNames.pop()
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  }
}
