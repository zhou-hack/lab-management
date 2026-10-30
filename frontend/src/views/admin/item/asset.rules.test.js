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
