import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import LocationPanel from './panels/LocationPanel.vue'

const api = vi.hoisted(() => ({
  createLocation: vi.fn(),
  deleteLocation: vi.fn(),
  getLocationAdminOptions: vi.fn(),
  getLocationChangeHistory: vi.fn(),
  getLocationInventory: vi.fn(),
  getLocations: vi.fn(),
  updateLocation: vi.fn(),
  updateLocationAdmins: vi.fn()
}))

vi.mock('@/api/item/stock', () => api)
vi.mock('@/store/user', () => ({
  useUserStore: () => ({ permissions: ['item:location:list', 'item:location:manage'] })
}))

describe('库位变动记录', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.getLocations.mockResolvedValue({ records: [], total: 0 })
    api.getLocationChangeHistory.mockResolvedValue({
      records: [{
        id: 'CREATE_1',
        locationId: '1',
        locationCode: 'A001',
        locationName: '计算机网络实训室',
        changeType: 'CREATE',
        operatorId: '11',
        operatorName: '张三',
        occurredAt: '2026-10-29T01:00:00.000Z'
      }],
      total: 1,
      pageNum: 1,
      pageSize: 10
    })
  })

  it('打开变动记录后按默认分页加载并展示创建人和库位', async () => {
    const wrapper = mount(LocationPanel, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        stubs: { ItemQuantityText: true, ItemStatusPill: true }
      }
    })

    await flushPromises()
    const historyButton = wrapper.findAll('button')
      .find((button) => button.text() === '变动记录')
    expect(historyButton).toBeDefined()
    await historyButton.trigger('click')
    await flushPromises()

    expect(api.getLocationChangeHistory).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10 })
    expect(document.body.textContent).toContain('库位变动记录')
    expect(document.body.textContent).toContain('计算机网络实训室')
    expect(document.body.textContent).toContain('A001')
    expect(document.body.textContent).toContain('创建')
    expect(document.body.textContent).toContain('张三')

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('库内库存分页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('耗材、固定资产汇总和逐台明细分别每页7行且翻页互不影响', async () => {
    api.getLocations.mockResolvedValue({
      records: [{ id: 'loc-1', locationCode: 'A001', locationName: '测试库', enabled: 1, admins: [] }],
      total: 1
    })
    api.getLocationInventory.mockResolvedValue({
      balances: Array.from({ length: 9 }, (_, index) => ({
        id: `balance-${index + 1}`,
        materialName: `耗材-${index + 1}`,
        ownedQuantity: 10,
        loanedQuantity: 0,
        availableQuantity: 10,
        baseUnit: '个'
      })),
      assets: Array.from({ length: 9 }, (_, index) => ({
        id: `asset-${index + 1}`,
        assetCode: `资产-${index + 1}`,
        materialName: '电脑',
        status: 'IDLE',
        custodians: []
      }))
    })
    const wrapper = mount(LocationPanel, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        stubs: { ItemQuantityText: true, ItemStatusPill: true }
      }
    })

    await flushPromises()
    await wrapper.find('.location_panel__location').trigger('click')
    await flushPromises()
    const pagers = wrapper.findAllComponents({ name: 'ElPagination' })
      .filter((pager) => pager.classes().includes('location_panel__inventory_pager'))
    expect(pagers).toHaveLength(3)
    expect(pagers.map((pager) => pager.props('pageSize'))).toEqual([7, 7, 7])
    expect(pagers.map((pager) => pager.props('total'))).toEqual([9, 1, 9])
    expect(wrapper.text()).toContain('耗材-1')
    expect(wrapper.text()).toContain('耗材-7')
    expect(wrapper.text()).not.toContain('耗材-8')
    expect(wrapper.text()).toContain('资产-1')
    expect(wrapper.text()).toContain('资产-7')
    expect(wrapper.text()).not.toContain('资产-8')

    await pagers[0].findAll('li.number')[1].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('耗材-8')
    expect(wrapper.text()).toContain('耗材-9')
    expect(wrapper.text()).not.toContain('耗材-7')
    expect(wrapper.text()).toContain('资产-1')
    expect(wrapper.text()).toContain('资产-7')
    expect(wrapper.text()).not.toContain('资产-8')

    await pagers[2].findAll('li.number')[1].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('耗材-8')
    expect(wrapper.text()).toContain('耗材-9')
    expect(wrapper.text()).toContain('资产-8')
    expect(wrapper.text()).toContain('资产-9')
    expect(wrapper.text()).not.toContain('资产-7')

    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('按物料名同步筛选汇总和库存明细并在搜索后重置分页', async () => {
    api.getLocations.mockResolvedValue({
      records: [{ id: 'loc-1', locationCode: 'A001', locationName: '测试库', enabled: 1, admins: [] }],
      total: 1
    })
    api.getLocationInventory.mockResolvedValue({
      balances: Array.from({ length: 9 }, (_, index) => ({
        id: `balance-${index + 1}`,
        materialName: index === 0 ? '电脑耗材' : `耗材-${index + 1}`,
        ownedQuantity: 10,
        loanedQuantity: 0,
        availableQuantity: 10,
        baseUnit: '个'
      })),
      assets: Array.from({ length: 9 }, (_, index) => ({
        id: `asset-${index + 1}`,
        assetCode: `资产-${index + 1}`,
        materialName: index === 0 ? '电脑' : '实验台',
        status: 'IDLE',
        custodians: []
      }))
    })
    const wrapper = mount(LocationPanel, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        stubs: { ItemQuantityText: true, ItemStatusPill: true }
      }
    })

    await flushPromises()
    await wrapper.find('.location_panel__location').trigger('click')
    await flushPromises()
    const pagers = wrapper.findAllComponents({ name: 'ElPagination' })
      .filter((pager) => pager.classes().includes('location_panel__inventory_pager'))
    await pagers[0].findAll('li.number')[1].trigger('click')
    await pagers[2].findAll('li.number')[1].trigger('click')
    await flushPromises()

    const searchInput = wrapper.find('.location_panel__inventory_search input')
    await searchInput.setValue('电脑')
    await flushPromises()
    expect(pagers.map((pager) => pager.props('total'))).toEqual([1, 1, 1])
    expect(pagers.map((pager) => pager.props('currentPage'))).toEqual([1, 1, 1])
    expect(wrapper.text()).toContain('电脑耗材')
    expect(wrapper.text()).toContain('资产-1')
    expect(wrapper.text()).not.toContain('耗材-8')
    expect(wrapper.text()).not.toContain('资产-8')

    await searchInput.setValue('不存在')
    await flushPromises()
    expect(pagers.map((pager) => pager.props('total'))).toEqual([0, 0, 0])
    expect(wrapper.text()).toContain('没有匹配的耗材')
    expect(wrapper.text()).toContain('没有匹配的固定资产')

    await searchInput.setValue('')
    await flushPromises()
    expect(pagers.map((pager) => pager.props('total'))).toEqual([9, 2, 9])
    expect(pagers.map((pager) => pager.props('currentPage'))).toEqual([1, 1, 1])
    expect(wrapper.text()).toContain('电脑耗材')
    expect(wrapper.text()).toContain('耗材-7')
    expect(wrapper.text()).toContain('资产-1')
    expect(wrapper.text()).toContain('资产-7')

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('库内固定资产物料汇总', () => {
  it('按物料汇总保有和可用并排除丢失报废数量', async () => {
    vi.clearAllMocks()
    api.getLocations.mockResolvedValue({
      records: [{ id: 'loc-1', locationCode: 'A001', locationName: '测试库', enabled: 1, admins: [] }],
      total: 1
    })
    const assets = [
      ['asset-1', 'material-1', '电脑', '规格-X1', '台', 'IDLE'],
      ['asset-2', 'material-1', '电脑', '规格-X1', '台', 'ON_LOAN'],
      ['asset-3', 'material-1', '电脑', '规格-X1', '台', 'MAINTENANCE'],
      ['asset-4', 'material-1', '电脑', '规格-X1', '台', 'LOST'],
      ['asset-5', 'material-1', '电脑', '规格-X1', '台', 'SCRAPPED'],
      ['asset-6', 'material-2', '实验台', '规格-T2', '张', 'IDLE'],
      ['asset-7', 'material-2', '实验台', '规格-T2', '张', 'MAINTENANCE']
    ].map(([id, materialId, materialName, specification, baseUnit, status]) => ({
      id, materialId, materialName, specification, baseUnit, status, assetCode: id, custodians: []
    }))
    api.getLocationInventory.mockResolvedValue({ balances: [], assets })
    const wrapper = mount(LocationPanel, {
      attachTo: document.body,
      global: { plugins: [ElementPlus], stubs: { ItemStatusPill: true } }
    })

    await flushPromises()
    await wrapper.find('.location_panel__location').trigger('click')
    await flushPromises()
    const summarySection = wrapper.findAll('section')
      .find((section) => section.find('h3').text() === '固定资产物料汇总（保有 / 可用）')
    expect(summarySection).toBeDefined()
    const rows = summarySection.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('电脑')
    expect(rows[0].text()).toContain('规格-X1')
    expect(rows[0].text()).toContain('3台')
    expect(rows[0].text()).toContain('1台')
    expect(rows[1].text()).toContain('实验台')
    expect(rows[1].text()).toContain('2张')
    expect(rows[1].text()).toContain('1张')

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})
