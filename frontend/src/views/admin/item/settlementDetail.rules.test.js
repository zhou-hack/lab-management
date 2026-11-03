import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import SettlementDetailPanel from './panels/SettlementDetailPanel.vue'

const workflowApi = vi.hoisted(() => ({
  pageSettlements: vi.fn(),
  getDocument: vi.fn()
}))

vi.mock('@/api/item/workflow', () => workflowApi)

const selectStub = (name) => ({
  name,
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  template: '<button class="select-stub" @click="$emit(\'update:modelValue\', modelValue)">{{ modelValue }}</button>'
})

const drawerStub = {
  name: 'DocumentDetailDrawer',
  props: ['modelValue', 'documentId'],
  template: '<div class="document-detail-stub">{{ documentId }}</div>'
}

function row(overrides = {}) {
  return {
    id: 'settlement-1',
    materialDocumentId: 'document-1',
    documentNo: 'IM-1',
    businessScene: 'IN_STOCK_SETTLE',
    sourceMaterialDocumentLineId: 'source-line-1',
    materialId: 'material-1',
    materialName: '元器件',
    specification: '10个/包',
    baseUnit: '个',
    fixedAssetId: null,
    assetCode: null,
    settlementType: 'ADJUST',
    quantity: 2,
    targetAssetStatus: null,
    reason: '盘点差异',
    locationId: '101',
    locationCode: 'A001',
    locationName: '甲库',
    operatorId: '11',
    operatorName: '管理员甲',
    reviewStatus: 'PENDING_REVIEW',
    reviewedBy: null,
    reviewedByName: null,
    reviewedAt: null,
    occurredAt: '2026-11-02T01:00:00.000Z',
    postedAt: null,
    ...overrides
  }
}

function mountPanel() {
  return mount(SettlementDetailPanel, {
    global: {
      plugins: [ElementPlus],
      stubs: {
        ItemMaterialSelect: selectStub('ItemMaterialSelect'),
        ItemLocationSelect: selectStub('ItemLocationSelect'),
        ItemQuantityText: {
          props: ['value', 'unit'],
          template: '<span>{{ value }}{{ unit }}</span>'
        },
        ItemStatusPill: {
          props: ['status'],
          template: '<span class="status-pill">{{ status }}</span>'
        },
        DocumentDetailDrawer: drawerStub
      }
    }
  })
}

describe('核销明细独立页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('首次按发生时间倒序加载跨单据记录，点击单号才打开详情', async () => {
    workflowApi.pageSettlements.mockResolvedValue({
      records: [
        row(),
        row({
          id: 'settlement-2',
          materialDocumentId: 'document-2',
          documentNo: 'RK-2',
          businessScene: 'CLAIM_RETURN',
          settlementType: 'CONSUMED',
          reviewStatus: null,
          postedAt: '2026-11-02T02:00:00.000Z'
        })
      ],
      total: 2,
      pageNum: 1,
      pageSize: 20
    })
    const wrapper = mountPanel()
    await flushPromises()

    expect(workflowApi.pageSettlements).toHaveBeenCalledTimes(1)
    expect(workflowApi.pageSettlements).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 20,
      sortBy: 'occurredAt',
      sortOrder: 'desc'
    }))
    expect(wrapper.text()).toContain('IM-1')
    expect(wrapper.text()).toContain('RK-2')
    expect(wrapper.text()).toContain('元器件')
    expect(wrapper.text()).toContain('未过账')
    expect(wrapper.text()).toContain('已过账')
    expect(workflowApi.getDocument).not.toHaveBeenCalled()

    const documentButton = wrapper.findAll('button').find((button) => button.text() === 'IM-1')
    await documentButton.trigger('click')
    const drawer = wrapper.findComponent(drawerStub)
    expect(drawer.props('modelValue')).toBe(true)
    expect(drawer.props('documentId')).toBe('document-1')
    wrapper.unmount()
  })

  it('组合筛选携带全部条件，重置后恢复默认分页', async () => {
    workflowApi.pageSettlements.mockResolvedValue({ records: [], total: 0, pageNum: 1, pageSize: 20 })
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.findComponent({ name: 'ElInput' }).setValue('IM-1')
    wrapper.findComponent({ name: 'ItemMaterialSelect' }).vm.$emit('update:modelValue', 'material-1')
    const selects = wrapper.findAllComponents({ name: 'ElSelect' })
    await selects[0].vm.$emit('update:modelValue', 'SCRAP')
    wrapper.findComponent({ name: 'ItemLocationSelect' }).vm.$emit('update:modelValue', '101')
    await selects[1].vm.$emit('update:modelValue', 'PENDING_REVIEW')
    await wrapper.findComponent({ name: 'ElDatePicker' }).vm.$emit('update:modelValue', ['2026-11-01', '2026-11-02'])
    const queryButton = wrapper.findAll('button').find((button) => button.text() === '查询')
    await queryButton.trigger('click')
    await flushPromises()

    expect(workflowApi.pageSettlements).toHaveBeenLastCalledWith(expect.objectContaining({
      documentNo: 'IM-1',
      materialId: 'material-1',
      settlementType: 'SCRAP',
      locationId: '101',
      reviewStatus: 'PENDING_REVIEW',
      startDate: '2026-11-01',
      endDate: '2026-11-02',
      pageNum: 1,
      pageSize: 20
    }))

    const resetButton = wrapper.findAll('button').find((button) => button.text() === '重置')
    await resetButton.trigger('click')
    await flushPromises()
    expect(workflowApi.pageSettlements).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 20,
      sortBy: 'occurredAt',
      sortOrder: 'desc'
    }))
    const lastParams = workflowApi.pageSettlements.mock.calls.at(-1)[0]
    expect(lastParams.documentNo).toBeUndefined()
    expect(lastParams.materialId).toBeUndefined()
    expect(lastParams.locationId).toBeUndefined()
    expect(lastParams.startDate).toBeUndefined()
    wrapper.unmount()
  })

  it('待复核和已驳回均显示未过账', async () => {
    workflowApi.pageSettlements.mockResolvedValue({
      records: [
        row(),
        row({ id: 'settlement-2', reviewStatus: 'REJECTED', reviewedByName: '复核员乙' })
      ],
      total: 2,
      pageNum: 1,
      pageSize: 20
    })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('PENDING_REVIEW')
    expect(wrapper.text()).toContain('REJECTED')
    expect(wrapper.text()).toContain('复核员乙')
    expect(wrapper.findAll('.settlement_detail__review_status .t-hint')
      .filter((node) => node.text() === '未过账')).toHaveLength(2)
    wrapper.unmount()
  })
})
