import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/equipment', () => ({
  categoryTree: vi.fn(),
  pageAssets: vi.fn()
}))

// 申请弹窗以桩替代，避免真实渲染触发额外请求
vi.mock('@/components/BookingApplyDialog.vue', () => ({
  default: {
    name: 'BookingApplyDialog',
    props: { preset: { type: Object, default: null } },
    template: '<div class="apply_stub" />'
  }
}))

import { categoryTree, pageAssets } from '@/api/equipment'
import EquipmentDirectory from './EquipmentDirectory.vue'

const categories = [
  { id: '101', categoryName: '网络设备', categoryCode: 'NET', children: [] },
  { id: '102', categoryName: '计算设备', categoryCode: 'COMP', children: [] }
]

const rows = [
  {
    id: '1',
    assetCode: 'ZC-001',
    equipmentName: '路由器',
    modelCode: 'AR6120',
    categoryId: '101',
    categoryName: '网络设备',
    departmentName: '设备处',
    locationDetail: '3-301',
    custodianName: '王老师'
  },
  {
    id: '2',
    assetCode: 'ZC-002',
    equipmentName: '服务器',
    modelCode: '2288H',
    categoryId: '102',
    categoryName: '计算设备',
    departmentName: '设备处',
    locationDetail: '3-302',
    custodianName: '王老师'
  }
]

const mountPage = () =>
  mount(EquipmentDirectory, {
    global: {
      plugins: [ElementPlus],
      // v-permission 指令在真实入口注册，测试中以空指令桩替代
      directives: { permission: {} }
    }
  })

describe('设备目录页（按分类分组）', () => {
  beforeEach(() => {
    categoryTree.mockResolvedValue(categories)
    pageAssets.mockResolvedValue({ records: rows, total: rows.length })
  })

  it('挂载加载分类树与在架资产，并按分类分组展示', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(categoryTree).toHaveBeenCalled()
    expect(pageAssets).toHaveBeenCalled()
    const groups = wrapper.findAll('.equip_group')
    expect(groups).toHaveLength(2)
    expect(groups[0].text()).toContain('网络设备')
    expect(groups[0].text()).toContain('1 台设备')
    expect(groups[1].text()).toContain('计算设备')
    expect(wrapper.text()).toContain('ZC-001')
    expect(wrapper.text()).toContain('ZC-002')
    wrapper.unmount()
  })

  it('查询参数携带分类时传给后端（分类筛选生效）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    // 直接经组件实例设置筛选值并触发查询（tree-select 交互在 jsdom 中不稳）
    wrapper.vm.query.categoryId = '101'
    wrapper.vm.search()
    await flushPromises()
    expect(pageAssets).toHaveBeenLastCalledWith(
      expect.objectContaining({ categoryId: '101' })
    )
    wrapper.unmount()
  })

  it('无在架资产时展示空态', async () => {
    pageAssets.mockResolvedValue({ records: [], total: 0 })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('.empty').text()).toContain('暂无在架设备')
    wrapper.unmount()
  })
})
