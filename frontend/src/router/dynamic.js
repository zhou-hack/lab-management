import router from '@/router'
import AdminLayout from '@/layout/admin/AdminLayout.vue'

/**
 * 本地组件白名单：后端 sys_menu.path → 本地视图组件。
 * 禁止直接执行后端下发的组件路径或代码；未命中白名单的菜单项丢弃并告警。
 * 业务页面落地时在此登记（key 与 sys_menu.path 一致）。
 */
const viewModules = {
  '/admin/home': () => import('@/views/admin/home/AdminHome.vue'),
  '/admin/space': () => import('@/views/admin/space/SpaceManage.vue'),
  '/admin/equipment/category': () => import('@/views/admin/equipment/CategoryManage.vue'),
  '/admin/equipment/model': () => import('@/views/admin/equipment/ModelManage.vue'),
  '/admin/equipment/asset': () => import('@/views/admin/equipment/AssetManage.vue')
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

/** 将后端菜单树映射为动态路由并注册；admin 子树挂 AdminLayout */
export function registerDynamicRoutes(menus) {
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
      console.warn(`[router] 菜单「${menu.menuName}」(${menu.path}) 不属于管理端子树，已跳过`)
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
