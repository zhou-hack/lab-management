import PlaceholderView from '@/views/common/PlaceholderView.vue'
import router from '@/router'

/**
 * 本地组件白名单：sys_menu.component_key → 本地视图组件。
 * 禁止直接执行后端下发的组件路径或代码；未命中白名单的菜单项跳过并告警。
 * component_key 统一大驼峰，与 sql 脚本中 sys_menu.component_key 一一对应；
 * 全部账号共用同一套管理端布局（AdminLayout），业务页面落地时在此登记真实组件。
 */
const viewModules = {
  // system 模块（用户管理已落地，其余占位页逐页替换）
  SystemUser: () => import('@/views/admin/system/SystemUser.vue'),
  SystemRoster: PlaceholderView,
  SystemDepartment: PlaceholderView,
  SystemRole: PlaceholderView,
  SystemScope: PlaceholderView,
  SystemRestriction: PlaceholderView,
  SystemLog: PlaceholderView,
  SystemPolicy: PlaceholderView,
  SystemGrantAudit: PlaceholderView,
  GrantManage: PlaceholderView,
  // space 模块
  SpaceLabList: () => import('@/views/admin/space/LabList.vue'),
  // booking 模块
  BookingAdminList: () => import('@/views/admin/booking/BookingList.vue'),
  BookingApproval: () => import('@/views/admin/booking/BookingApproval.vue'),
  BookingUserList: () => import('@/views/booking/BookingList.vue'),
  // equipment 模块
  EquipmentCategory: () => import('@/views/admin/equipment/CategoryManage.vue'),
  EquipmentModel: () => import('@/views/admin/equipment/ModelManage.vue'),
  EquipmentAsset: () => import('@/views/admin/equipment/AssetManage.vue'),
  // item 模块（单工作台形态，内部分页签）
  ItemWorkspace: () => import('@/views/item/ItemWorkspace.vue')
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

/** 将后端菜单树映射为动态路由，统一挂到 AdminLayout 下（全部账号同一布局） */
export function registerDynamicRoutes(menus) {
  walkMenus(menus, (menu) => {
    if (menu.menuType !== 'PAGE' || !menu.routePath || !menu.componentKey) return
    const load = viewModules[menu.componentKey]
    if (!load) {
      console.warn(`[router] 菜单「${menu.menuName}」(${menu.componentKey}) 未命中本地组件白名单，已跳过`)
      return
    }
    const name = `dyn_${menu.id}`
    if (router.hasRoute(name)) return
    router.addRoute('AdminLayout', {
      path: menu.routePath,
      name,
      component: load,
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
