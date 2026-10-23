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
})
