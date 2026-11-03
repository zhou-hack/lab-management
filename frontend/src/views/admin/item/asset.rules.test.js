import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import FixedAssetStockPanel from './panels/FixedAssetStockPanel.vue'

const api = vi.hoisted(() => ({
  getAsset: vi.fn(),
  getAssets: vi.fn(),
  getAssetSummary: vi.fn(),
  getLocationAdminOptions: vi.fn(),
  toggleAssetMaintenance: vi.fn(),
  updateAsset: vi.fn(),
  updateLocationAdmins: vi.fn()
}))

vi.mock('@/api/item/stock', () => api)
vi.mock('@/store/user', () => ({
  useUserStore: () => ({ permissions: ['item:asset:manage', 'item:location:manage'] })
}))

describe('单台资产维修切换', () => {
  it('在库资产点击维修后调用切换接口并显示解除维修', async () => {
    let currentAsset = {
      id: 'asset-1',
      assetCode: 'PC-1',
      materialName: '电脑',
      locationId: '101',
      locationName: '甲库',
      locationCode: 'A001',
      status: 'IDLE',
      custodians: [],
      price: null
    }
    api.getAssets.mockImplementation(() => Promise.resolve({ records: [currentAsset], total: 1 }))
    api.getAssetSummary.mockResolvedValue([])
    api.toggleAssetMaintenance.mockImplementation(() => {
      currentAsset = { ...currentAsset, status: 'MAINTENANCE' }
      return Promise.resolve(currentAsset)
    })
    const wrapper = mount(FixedAssetStockPanel, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        stubs: { ItemLocationSelect: true, ItemQuantityText: true, ItemStatusPill: true }
      }
    })

    await flushPromises()
    const maintenanceButton = wrapper.findAll('button')
      .find((button) => button.text() === '维修')
    expect(maintenanceButton).toBeDefined()
    await maintenanceButton.trigger('click')
    await flushPromises()

    expect(api.toggleAssetMaintenance).toHaveBeenCalledWith('asset-1')
    expect(api.getAssets).toHaveBeenCalledTimes(2)
    expect(api.getAssetSummary).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('button').some((button) => button.text() === '解除维修')).toBe(true)

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('固定资产筛选输入联想', () => {
  it('物料名称和资产编码按当前条件远程查询并去重', async () => {
    vi.clearAllMocks()
    api.getAssets.mockResolvedValue({
      records: [
        { id: 'asset-1', materialName: '电脑', assetCode: 'PC-001' },
        { id: 'asset-2', materialName: '电脑', assetCode: 'PC-002' },
        { id: 'asset-3', materialName: '电脑桌椅', assetCode: 'PC-001' }
      ],
      total: 3
    })
    api.getAssetSummary.mockResolvedValue([])
    const wrapper = mount(FixedAssetStockPanel, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        stubs: { ItemLocationSelect: true, ItemQuantityText: true, ItemStatusPill: true }
      }
    })
    await flushPromises()
    const autocompletes = wrapper.findAllComponents({ name: 'ElAutocomplete' })
    expect(autocompletes).toHaveLength(2)
    api.getAssets.mockClear()

    const materialCallback = vi.fn()
    await autocompletes[0].props('fetchSuggestions')(' 电 ', materialCallback)
    expect(api.getAssets).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 100,
      materialName: '电',
      assetCode: undefined,
      locationId: undefined,
      status: undefined
    })
    expect(materialCallback).toHaveBeenCalledWith([{ value: '电脑' }, { value: '电脑桌椅' }])

    const assetCallback = vi.fn()
    await autocompletes[1].props('fetchSuggestions')('PC', assetCallback)
    expect(api.getAssets).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 100,
      materialName: undefined,
      assetCode: 'PC',
      locationId: undefined,
      status: undefined
    })
    expect(assetCallback).toHaveBeenCalledWith([{ value: 'PC-001' }, { value: 'PC-002' }])

    api.getAssets.mockClear()
    const emptyCallback = vi.fn()
    await autocompletes[0].props('fetchSuggestions')('', emptyCallback)
    expect(emptyCallback).toHaveBeenCalledWith([])
    expect(api.getAssets).not.toHaveBeenCalled()

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})
