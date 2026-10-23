import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/equipment', () => ({
  categoryTree: vi.fn(),
  createModel: vi.fn(),
  deleteModel: vi.fn(),
  pageModels: vi.fn(),
  updateModel: vi.fn()
}))

import { pageModels, categoryTree } from '@/api/equipment'
import ModelManage from './ModelManage.vue'

const models = [
  {
    id: '201',
    modelCode: 'HUAWEI_AR6120',
    equipmentName: '企业级路由器 AR6120',
    brand: '华为',
    categoryId: '111',
    categoryName: '企业级路由器'
  },
  {
    id: '202',
    modelCode: 'HUAWEI_S5735L24',
    equipmentName: '二层交换机 S5735',
    brand: '华为',
    categoryId: '112',
    categoryName: '二层交换机'
  }
]

const mountSection = () =>
  mount(ModelManage, {
    props: { active: true },
    global: {
      plugins: [ElementPlus],
      // v-permission 指令在真实入口注册，测试中以空指令桩替代
      directives: { permission: {} }
    }
  })

describe('设备库存区搜索关键字联想', () => {
  beforeEach(() => {
    pageModels.mockResolvedValue({ records: models, total: models.length })
    categoryTree.mockResolvedValue([])
  })

  it('输入时提供型号库内已有关键字建议，点击建议后按关键字查询', async () => {
    const wrapper = mountSection()
    await flushPromises()
    // 直接调用联想函数（el-autocomplete 弹层交互在 jsdom 中不稳）
    const cb = vi.fn()
    wrapper.vm.querySuggestions('AR61', cb)
    expect(cb).toHaveBeenCalled()
    const suggestions = cb.mock.calls[0][0]
    expect(suggestions.length).toBeGreaterThan(0)
    expect(suggestions[0].value).toBe('HUAWEI_AR6120')
    // 模拟点击建议项：回填关键字并触发查询
    wrapper.vm.onSuggestionSelect({ value: 'HUAWEI_AR6120' })
    await flushPromises()
    expect(pageModels).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: 'HUAWEI_AR6120', pageNum: 1 })
    )
    wrapper.unmount()
  })
})
