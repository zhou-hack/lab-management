import PlaceholderView from '@/views/common/PlaceholderView.vue'
import router from '@/router'

/**
 * 本地组件白名单：sys_menu.component_key → 本地视图组件与归属布局（admin/front）。
 * 禁止直接执行后端下发的组件路径或代码；未命中白名单的菜单项跳过并告警。
 * component_key 统一大驼峰，与 sql 脚本中 sys_menu.component_key 一一对应；
 * 业务页面落地时在此登记真实组件（布局按页面形态选择）。
 */
const viewModules = {
  // system 模块（用户管理已落地，其余占位页逐页替换）
  SystemUser: { layout: 'admin', load: () => import('@/views/admin/system/SystemUser.vue') },
  SystemRoster: { layout: 'admin', load: PlaceholderView },
  SystemDepartment: { layout: 'admin', load: PlaceholderView },
  SystemRole: { layout: 'admin', load: PlaceholderView },
  SystemScope: { layout: 'admin', load: PlaceholderView },
  SystemRestriction: { layout: 'admin', load: PlaceholderView },
  SystemLog: { layout: 'admin', load: PlaceholderView },
  SystemPolicy: { layout: 'admin', load: PlaceholderView },
  SystemGrantAudit: { layout: 'admin', load: PlaceholderView },
  GrantManage: { layout: 'admin', load: PlaceholderView },
  // space 模块
  SpaceLabList: { layout: 'admin', load: () => import('@/views/admin/space/LabList.vue') },
  // booking 模块
  BookingAdminList: { layout: 'admin', load: () => import('@/views/admin/booking/BookingList.vue') },
  BookingApproval: { layout: 'admin', load: () => import('@/views/admin/booking/BookingApproval.vue') },
  BookingUserList: { layout: 'front', load: () => import('@/views/booking/BookingList.vue') },
  // equipment 模块
  EquipmentCategory: { layout: 'admin', load: () => import('@/views/admin/equipment/CategoryManage.vue') },
  EquipmentModel: { layout: 'admin', load: () => import('@/views/admin/equipment/ModelManage.vue') },
  EquipmentAsset: { layout: 'admin', load: () => import('@/views/admin/equipment/AssetManage.vue') },
  // item 模块（单工作台形态，内部分页签）
  ItemWorkspace: { layout: 'admin', load: () => import('@/views/item/ItemWorkspace.vue') }
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
