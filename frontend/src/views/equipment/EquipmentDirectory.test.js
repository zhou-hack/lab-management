import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/equipment', () => ({
  categoryTree: vi.fn(),
  directoryAssets: vi.fn()
}))

// 申请弹窗以桩替代，避免真实渲染触发额外请求
vi.mock('@/components/BookingApplyDialog.vue', () => ({
  default: {
    name: 'BookingApplyDialog',
    props: { preset: { type: Object, default: null } },
    template: '<div class="apply_stub" />'
  }
}))

import { categoryTree, directoryAssets } from '@/api/equipment'
import EquipmentDirectory from './EquipmentDirectory.vue'

const categories = [
  { id: '101', categoryName: '网络设备', categoryCode: 'NET', children: [] },
  { id: '102', categoryName: '计算设备', categoryCode: 'COMP', children: [] }
]

// 服务端按型号聚合的目录记录：在架空闲台数 + 借出中资产编号（不暴露在架资产编号）
const models = [
  {
    modelId: '201',
    modelCode: 'AR6120',
    equipmentName: '路由器',
    categoryId: '101',
    categoryName: '网络设备',
    idleCount: 3,
    checkedOutCodes: ['ZC-101', 'ZC-102'],
    firstIdleAssetId: '1',
    firstIdleAssetCode: 'ZC-001'
  },
  {
    modelId: '202',
    modelCode: '2288H',
    equipmentName: '服务器',
    categoryId: '102',
    categoryName: '计算设备',
    idleCount: 0,
    checkedOutCodes: ['ZC-201', 'ZC-202'],
    firstIdleAssetId: null,
    firstIdleAssetCode: null
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

describe('设备目录页（按型号合并分组）', () => {
  beforeEach(() => {
    categoryTree.mockResolvedValue(categories)
    directoryAssets.mockResolvedValue(models)
  })

  it('挂载加载分类树与目录聚合，按分类分组并展示在架台数/借出编号', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(categoryTree).toHaveBeenCalled()
    expect(directoryAssets).toHaveBeenCalled()
    const groups = wrapper.findAll('.equip_group')
    expect(groups).toHaveLength(2)
    expect(groups[0].text()).toContain('网络设备')
    expect(groups[0].text()).toContain('在架 5 台')
    expect(groups[1].text()).toContain('计算设备')
    // 借出中的设备显示资产编号
    expect(wrapper.text()).toContain('ZC-101')
    expect(wrapper.text()).toContain('ZC-102')
    expect(wrapper.text()).toContain('ZC-201')
    // 在架设备不暴露资产编号（仅首个在架资产用于申请预选）
    expect(wrapper.text()).not.toContain('ZC-001')
    wrapper.unmount()
  })

  it('展示借出台数与申请使用次数，申请使用次数=0 时按钮置灰禁用', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const groups = wrapper.findAll('.equip_group')
    // 网络设备：空闲 3 + 借出 2 → 在架 5、借出 2、申请使用 3，按钮可用
    expect(groups[0].text()).toContain('借出台数')
    expect(groups[0].text()).toContain('在架 5 台')
    expect(groups[0].text()).toContain('3 台')
    expect(groups[0].find('.el-button').attributes('disabled')).toBeUndefined()
    // 计算设备：空闲 0 + 借出 2 → 在架 2、借出 2、申请使用 0，按钮置灰禁用
    expect(groups[1].text()).toContain('在架 2 台')
    expect(groups[1].text()).toContain('0 台')
    expect(groups[1].find('.el-button').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('申请使用次数=在架台数-借出台数且不小于 0（归还一台借出-1、可用+1）', () => {
    const wrapper = mountPage()
    // 不依赖接口数据，直接验证口径与下限
    // 归还一台：空闲+1、借出-1 → 在架台数不变、申请使用 +1（3→4）
    expect(wrapper.vm.applyableCount({ idleCount: 3, checkedOutCodes: ['a', 'b'] })).toBe(3)
    expect(wrapper.vm.applyableCount({ idleCount: 4, checkedOutCodes: ['a'] })).toBe(4)
    // 全部借出时下限为 0
    expect(wrapper.vm.applyableCount({ idleCount: 0, checkedOutCodes: ['a', 'b'] })).toBe(0)
    expect(wrapper.vm.applyableCount({ idleCount: 2, checkedOutCodes: ['a', 'b', 'c'] })).toBe(2)
    expect(wrapper.vm.applyableCount({ idleCount: 1, checkedOutCodes: null })).toBe(1)
    // 在架台数 = 空闲 + 借出
    expect(wrapper.vm.onShelfCount({ idleCount: 3, checkedOutCodes: ['a', 'b'] })).toBe(5)
    expect(wrapper.vm.checkedOutCount({ idleCount: 3, checkedOutCodes: ['a', 'b'] })).toBe(2)
    wrapper.unmount()
  })

  it('查询参数携带分类时传给后端（分类筛选生效）', async () => {
    const wrapper = mountPage()
    await flushPromises()
    // 直接经组件实例设置筛选值并触发查询（tree-select 交互在 jsdom 中不稳）
    wrapper.vm.query.categoryId = '101'
    wrapper.vm.search()
    await flushPromises()
    expect(directoryAssets).toHaveBeenLastCalledWith(
      expect.objectContaining({ categoryId: '101' })
    )
    wrapper.unmount()
  })

  it('输入时提供设备内已有关键字建议，点击建议后按关键字查询', async () => {
    const wrapper = mountPage()
    await flushPromises()
    // 直接调用联想函数（el-autocomplete 弹层交互在 jsdom 中不稳）
    const cb = vi.fn()
    wrapper.vm.querySuggestions('路由', cb)
    expect(cb).toHaveBeenCalled()
    const suggestions = cb.mock.calls[0][0]
    expect(suggestions.length).toBeGreaterThan(0)
    expect(suggestions[0].value).toBe('路由器')
    // 模拟点击建议项：回填关键字并触发查询
    wrapper.vm.onSuggestionSelect({ value: '路由器' })
    await flushPromises()
    expect(directoryAssets).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: '路由器' })
    )
    wrapper.unmount()
  })

  it('无在架设备时展示空态', async () => {
    directoryAssets.mockResolvedValue([])
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('.empty').text()).toContain('暂无在架设备')
    wrapper.unmount()
  })
})
