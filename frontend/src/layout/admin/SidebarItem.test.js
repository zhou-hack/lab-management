import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import SidebarItem from './SidebarItem.vue'

/** 设备管理菜单（DB 中为单页 C 型；子菜单「设备使用审批」挂其下，前端虚拟折叠收纳设备台账；「设备库存」2026-10-26 起暂时移除入口） */
const equipmentMenu = {
  menuId: 210,
  menuName: '设备管理',
  menuType: 'C',
  path: '/admin/equipment',
  componentKey: '/admin/equipment',
  children: [
    {
      menuId: 219,
      menuName: '设备使用审批',
      menuType: 'C',
      path: '/admin/equipment/bookings',
      children: []
    }
  ]
}

const mountItem = () =>
  mount(
    {
      template: '<el-menu><sidebar-item :item="item" /></el-menu>',
      components: { SidebarItem },
      data: () => ({ item: equipmentMenu })
    },
    { global: { plugins: [ElementPlus] } }
  )

describe('侧栏「设备管理」折叠子菜单', () => {
  it('渲染为折叠子菜单，主标题为「设备管理」，含「设备台账」「设备使用审批」两个子项（设备库存入口已移除）', () => {
    const wrapper = mountItem()
    const subMenu = wrapper.find('.el-sub-menu')
    expect(subMenu.exists()).toBe(true)
    expect(subMenu.find('.el-sub-menu__title').text()).toContain('设备管理')

    const items = wrapper.findAllComponents({ name: 'ElMenuItem' })
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('设备台账')
    expect(items[0].text()).not.toContain('设备库存')
    expect(items[1].text()).toContain('设备使用审批')
    wrapper.unmount()
  })

  it('点击「设备管理」标题可展开/收起子项', async () => {
    const wrapper = mountItem()
    const title = wrapper.find('.el-sub-menu__title')
    // 初始收起
    expect(wrapper.find('.el-sub-menu').classes()).not.toContain('is-opened')
    await title.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-sub-menu').classes()).toContain('is-opened')
    await title.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-sub-menu').classes()).not.toContain('is-opened')
    wrapper.unmount()
  })

  it('子项路由 index 指向设备台账/设备使用审批（asset、bookings；model 已移除）', () => {
    const wrapper = mountItem()
    const items = wrapper.findAllComponents({ name: 'ElMenuItem' })
    const indexes = items.map((i) => i.props('index'))
    expect(indexes).not.toContain('/admin/equipment/model')
    expect(indexes).toContain('/admin/equipment/asset')
    expect(indexes).toContain('/admin/equipment/bookings')
    wrapper.unmount()
  })
})
