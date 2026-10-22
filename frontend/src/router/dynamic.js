import router from '@/router'

/**
 * 本地组件白名单：sys_menu.component_key → 本地视图组件与归属布局（admin/front）。
 * 禁止直接执行后端下发的组件路径或代码；未命中白名单的菜单项跳过并告警。
 *
 * 业务页面落地时在此登记真实组件（布局按页面形态选择）：
 *   SystemUser: { layout: 'admin', load: () => import('@/views/admin/system/UserManage.vue') }
 *
 * 新增页面时同步把菜单 route_path 登记到 router/index.js 的 KNOWN_PAGE_PATHS
 * （用于已登录但无菜单权限的直接访问落 403）。
 */
const viewModules = {
  SystemUserRoster: { layout: 'admin', load: () => import('@/views/admin/system/SystemUserRoster.vue') },
  SystemDepartment: { layout: 'admin', load: () => import('@/views/admin/system/SystemDepartment.vue') },
  SystemRole: { layout: 'admin', load: () => import('@/views/admin/system/SystemRole.vue') },
  SystemScope: { layout: 'admin', load: () => import('@/views/admin/system/SystemScope.vue') },
  SystemLog: { layout: 'admin', load: () => import('@/views/admin/system/SystemLog.vue') },
  SystemPolicy: { layout: 'admin', load: () => import('@/views/admin/system/SystemPolicy.vue') },
  SystemGrantAudit: { layout: 'admin', load: () => import('@/views/admin/system/SystemGrantAudit.vue') },
  GrantManage: { layout: 'front', load: () => import('@/views/front/grant/GrantManage.vue') }
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

/** 将后端菜单树映射为动态路由，按白名单登记的布局挂到 AdminLayout / FrontLayout 下 */
export function registerDynamicRoutes(menus) {
  walkMenus(menus, (menu) => {
    if (menu.menuType !== 'PAGE' || !menu.routePath || !menu.componentKey) return
    const entry = viewModules[menu.componentKey]
    if (!entry) {
      console.warn(`[router] 菜单「${menu.menuName}」(${menu.componentKey}) 未命中本地组件白名单，已跳过`)
      return
    }
    const name = `dyn_${menu.id}`
    if (router.hasRoute(name)) return
    router.addRoute(entry.layout === 'admin' ? 'AdminLayout' : 'FrontLayout', {
      path: menu.routePath,
      name,
      component: entry.load,
      meta: { title: menu.menuName, menuId: menu.id }
    })
    addedRouteNames.push(name)
  })
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
