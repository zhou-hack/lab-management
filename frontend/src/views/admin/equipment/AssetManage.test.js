import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/equipment', () => ({
  createAsset: vi.fn(),
  custodianOptions: vi.fn(),
  deleteAsset: vi.fn(),
  exportAssets: vi.fn(),
  importAssets: vi.fn(),
  pageAssets: vi.fn(),
  pageModels: vi.fn(),
  transferAsset: vi.fn(),
  updateAsset: vi.fn()
}))

vi.mock('@/api/department', () => ({
  departmentTreeOptions: vi.fn()
}))

import { pageAssets, pageModels, custodianOptions } from '@/api/equipment'
import { departmentTreeOptions } from '@/api/department'
import AssetManage from './AssetManage.vue'

const assets = [
  {
    id: '1',
    assetCode: 'R01',
    serialNo: 'SN-HUAWEI-01',
    equipmentName: '路由器',
    modelId: '201',
    modelCode: 'AR6120'
  },
  {
    id: '2',
    assetCode: 'SW01',
    serialNo: null,
    equipmentName: '交换机',
    modelId: '202',
    modelCode: 'S5735'
  }
]

const mountSection = () =>
  mount(AssetManage, {
    props: { active: true },
    global: {
      plugins: [ElementPlus],
      // v-permission 指令在真实入口注册，测试中以空指令桩替代
      directives: { permission: {} }
    }
  })

describe('设备台账区搜索关键字联想', () => {
  beforeEach(() => {
    pageAssets.mockResolvedValue({ records: assets, total: assets.length })
    pageModels.mockResolvedValue({ records: [], total: 0 })
    custodianOptions.mockResolvedValue([])
    departmentTreeOptions.mockResolvedValue([])
  })

  it('输入时提供台账内已有关键字建议，点击建议后按关键字查询', async () => {
    const wrapper = mountSection()
    await flushPromises()
    // 直接调用联想函数（el-autocomplete 弹层交互在 jsdom 中不稳）
    const cb = vi.fn()
    wrapper.vm.querySuggestions('R0', cb)
    expect(cb).toHaveBeenCalled()
    const suggestions = cb.mock.calls[0][0]
    expect(suggestions.length).toBeGreaterThan(0)
    expect(suggestions[0].value).toBe('R01')
    // 模拟点击建议项：回填关键字并触发查询
    wrapper.vm.onSuggestionSelect({ value: 'R01' })
    await flushPromises()
    expect(pageAssets).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: 'R01', pageNum: 1 })
    )
    wrapper.unmount()
  })

  it('型号分组默认折叠，且每页仅渲染 10 组', async () => {
    // 注入 12 个型号（每型号 1 条台账）：第 1 页渲染 10 组、第 2 页 2 组
    const many = Array.from({ length: 12 }, (_, i) => ({
      id: String(i),
      assetCode: `A${i}`,
      serialNo: null,
      equipmentName: `设备${i}`,
      modelId: String(300 + i),
      modelCode: `M${i}`,
      categoryId: null,
      categoryName: null,
      departmentId: null,
      departmentName: null,
      locationDetail: null,
      custodianId: null,
      custodianName: null,
      purchaseDate: null,
      purchasePrice: null,
      warrantyUntil: null,
      status: 'IDLE',
      enabled: 1
    }))
    pageAssets.mockResolvedValue({ records: many, total: many.length })
    const wrapper = mountSection()
    await flushPromises()
    expect(wrapper.vm.groups.length).toBe(12)
    // 默认折叠：所有分组带 collapsed 样式
    for (const group of wrapper.findAll('.equip_group')) {
      expect(group.classes()).toContain('equip_group--collapsed')
    }
    // 第 1 页仅 10 组
    expect(wrapper.vm.pagedGroups.length).toBe(10)
    expect(wrapper.findAll('.equip_group').length).toBe(10)
    // 翻到第 2 页剩 2 组
    wrapper.vm.groupPageNum = 2
    await flushPromises()
    expect(wrapper.vm.pagedGroups.length).toBe(2)
    const renderedCount = wrapper.findAll('.equip_group').length
    expect(renderedCount, `page2 rendered ${renderedCount}`).toBe(2)
    wrapper.unmount()
  })
})
