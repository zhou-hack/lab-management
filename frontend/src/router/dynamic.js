import router from '@/router'
import AdminLayout from '@/layout/admin/AdminLayout.vue'

/**
 * 本地组件白名单：后端 sys_menu.route_path → 本地视图组件。
 * 禁止直接执行后端下发的组件路径或代码；未命中白名单的菜单项丢弃并告警。
 * 业务页面落地时在此登记（key 与 sys_menu.route_path 一致）。
 */
const viewModules = {
  '/admin/home': () => import('@/views/admin/home/AdminHome.vue'),
  // 设备管理单页（产品定稿不设子菜单）：型号 + 设备台账双折叠面板，
  // 分类目录在型号面板的「管理分类」抽屉内
  '/admin/equipment': () => import('@/views/admin/equipment/EquipmentManage.vue'),
  // 三权级设备使用申请（2026-10-19）：用户端三页 + 管理端审批页
  '/equipment/directory': () => import('@/views/equipment/EquipmentDirectory.vue'),
  '/equipment/bookings/my': () => import('@/views/equipment/MyEquipmentBookings.vue'),
  '/equipment/bookings/teacher': () => import('@/views/equipment/EquipmentTeacherApprove.vue'),
  '/admin/equipment/bookings': () => import('@/views/admin/equipment/EquipmentBookingManage.vue')
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

/**
 * 将后端菜单树映射为动态路由并注册。
 * 管理端子树（/admin 前缀）与用户端子树（其余 PAGE，如 /equipment/*）
 * 共用同一 AdminLayout：子路由使用绝对 path，组件仍嵌套在布局 router-view 内，
 * 侧栏按各自菜单树渲染（管理员见管理端菜单，学生/教师见用户端菜单）。
 */
export function registerDynamicRoutes(menus) {
  const children = []

  walkMenus(menus, (menu) => {
    if (menu.menuType !== 'C' || !menu.path) return
    const load = viewModules[menu.path]
    if (!load) {
      console.warn(`[router] 菜单「${menu.menuName}」(${menu.path}) 未命中本地组件白名单，已跳过`)
      return
    }
    children.push({
      path: menu.path,
      name: `dyn_${menu.menuId}`,
      component: load,
      meta: { title: menu.menuName, icon: menu.icon || null, perms: menu.perms || null, menuId: menu.menuId }
    })
  })

  // 固定首页：不依赖后端菜单下发（规范 7.4「固定首页」），菜单树未含时补注册
  if (!children.some((r) => r.path === '/admin/home')) {
    children.unshift({
      path: '/admin/home',
      name: 'dyn_home_fixed',
      component: viewModules['/admin/home'],
      meta: { title: '首页', icon: null, perms: null, menuId: 'fixed_home' }
    })
  }

  if (children.length) {
    router.addRoute({
      path: '/admin',
      name: 'AdminLayout',
      component: AdminLayout,
      redirect: children[0].path,
      children
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
