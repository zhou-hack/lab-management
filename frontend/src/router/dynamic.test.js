import { describe, expect, it } from 'vitest'
import { filterMenus } from './dynamic'

describe('filterMenus', () => {
  it('剔除失效设备菜单、ACTION 和空目录，保留有效场地页面', () => {
    const validPage = {
      id: 1,
      menuType: 'PAGE',
      menuName: '场地预约',
      routePath: '/booking/labs',
      componentKey: 'BookingUserList',
      children: []
    }
    const removedPage = {
      id: 2,
      menuType: 'PAGE',
      menuName: '设备管理',
      routePath: '/admin/equipment',
      componentKey: 'EquipmentManage',
      children: []
    }
    const menus = [
      { id: 10, menuType: 'DIRECTORY', menuName: '场地', children: [validPage] },
      { id: 11, menuType: 'DIRECTORY', menuName: '设备', children: [removedPage] },
      { id: 12, menuType: 'ACTION', menuName: '设备新增', componentKey: 'EquipmentManage', children: [] }
    ]

    const result = filterMenus(menus)

    expect(result).toHaveLength(1)
    expect(result[0].menuName).toBe('场地')
    expect(result[0].children).toHaveLength(1)
    expect(result[0].children[0].routePath).toBe('/booking/labs')
  })

  it('保留学生只读代领页面', () => {
    const pickupPage = {
      id: 20,
      menuType: 'PAGE',
      menuName: '我的代领任务',
      routePath: '/item/pickups',
      componentKey: 'ItemPickupCenter',
      children: []
    }

    expect(filterMenus([pickupPage])).toEqual([pickupPage])
  })

  it('空输入返回空数组', () => {
    expect(filterMenus(null)).toEqual([])
    expect(filterMenus(undefined)).toEqual([])
  })
})
