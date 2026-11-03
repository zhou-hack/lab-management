import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ReturnRegistrationDialog from './components/ReturnRegistrationDialog.vue'
import ClaimIssueDialog from './components/ClaimIssueDialog.vue'
import InboundPanel from './panels/InboundPanel.vue'
import ConsumableStockPanel from './panels/ConsumableStockPanel.vue'
import MaterialArchivePanel from './panels/MaterialArchivePanel.vue'
import SettlementPanel from './panels/SettlementPanel.vue'
import {
  CURRENT_STOCK_AVAILABILITY_NOTICE,
  getConsumableIssueDefaults,
  getFixedAssetIssueDefaults,
  getTransferReceiptError,
  isReturnWithinRemaining,
  materialPayload
} from './inventory.rules'

const workflowApi = vi.hoisted(() => ({
  createClaimReturn: vi.fn(),
  getReturnContext: vi.fn(),
  getClaimIssueContext: vi.fn(),
  issueClaim: vi.fn(),
  pageDocuments: vi.fn(),
  createScrap: vi.fn(),
  createAdjust: vi.fn()
}))

vi.mock('@/api/item/workflow', () => workflowApi)

const stockApi = vi.hoisted(() => ({
  createMaterial: vi.fn(),
  createOpening: vi.fn(),
  createReplenishment: vi.fn(),
  deleteMaterial: vi.fn(),
  getBalances: vi.fn(),
  getAssets: vi.fn(),
  getMaterials: vi.fn(),
  updateBalancePosition: vi.fn(),
  updateMaterial: vi.fn()
}))

vi.mock('@/api/item/stock', () => stockApi)
vi.mock('@/store/user', () => ({
  useUserStore: () => ({ permissions: ['item:material:manage'] })
}))

describe('移动实收规则', () => {
  it('整数实收数量等于或小于剩余在途量时通过，0 值行不处理', () => {
    expect(getTransferReceiptError([{ quantity: 5, remainingQuantity: 5 }])).toBe('')
    expect(getTransferReceiptError([
      { quantity: 0, remainingQuantity: 5 },
      { quantity: '2', remainingQuantity: '5' }
    ])).toBe('')
  })

  it.each([
    { label: '耗材', materialType: 'CONSUMABLE' },
    { label: '固定资产', materialType: 'FIXED_ASSET' }
  ])('$label小数实收数量均拒绝', ({ materialType }) => {
    expect(getTransferReceiptError([{
      materialType,
      quantity: 2.5,
      remainingQuantity: 5
    }])).toBe('实收数量必须为整数')
  })

  it('任一实收行超过剩余在途量时拒绝提交', () => {
    expect(getTransferReceiptError([
      { quantity: 2, remainingQuantity: 5 },
      { quantity: 6, remainingQuantity: 5 }
    ])).toBe('实收数量不能超过剩余在途数量')
  })

  it('没有有效实收行时保留原提示', () => {
    expect(getTransferReceiptError([{ quantity: 0, remainingQuantity: 5 }])).toBe('请至少填写一条实收行')
  })
})

describe('归还四类数量规则', () => {
  it('整数回库、消耗、丢失、损坏合计不超过剩余量，0 值表示不处理', () => {
    const row = {
      returnQuantity: 2,
      consumedQuantity: 1,
      lostQuantity: 1,
      damagedQuantity: 1,
      remainingQuantity: 5
    }

    expect(isReturnWithinRemaining(row)).toBe(true)
    expect(isReturnWithinRemaining({ ...row, remainingQuantity: 4.999 })).toBe(false)
    expect(isReturnWithinRemaining({
      returnQuantity: 0,
      consumedQuantity: 0,
      lostQuantity: 0,
      damagedQuantity: 0,
      remainingQuantity: 5
    })).toBe(true)
  })

  it.each([
    { label: '耗材', materialType: 'CONSUMABLE' },
    { label: '固定资产', materialType: 'FIXED_ASSET' }
  ])('$label小数归还或核销数量均拒绝', ({ materialType }) => {
    expect(isReturnWithinRemaining({
      materialType,
      returnQuantity: 0.5,
      consumedQuantity: 0,
      lostQuantity: 0,
      damagedQuantity: 0,
      remainingQuantity: 5
    })).toBe(false)
  })
})

describe('归还默认数量', () => {
  it('打开时默认全部回库，管理员减少某行后按调整值提交', async () => {
    workflowApi.getReturnContext.mockResolvedValue({
      sourceDocumentNo: 'OUT-1',
      locationName: '甲库',
      locationCode: 'A001',
      businessChainId: 'claim-1',
      overdue: false,
      canReturn: true,
      lines: [
        {
          sourceMaterialDocumentLineId: 'line-1',
          materialId: 'material-1',
          materialName: '数据线',
          materialType: 'CONSUMABLE',
          baseUnit: '根',
          issuedQuantity: 5,
          returnedQuantity: 2,
          settledQuantity: 0,
          remainingQuantity: 3,
          sourcePositionText: 'A-1'
        },
        {
          sourceMaterialDocumentLineId: 'line-2',
          materialId: 'material-2',
          materialName: '电脑',
          materialType: 'FIXED_ASSET',
          baseUnit: '台',
          assetCode: 'PC-1',
          issuedQuantity: 1,
          returnedQuantity: 0,
          settledQuantity: 0,
          remainingQuantity: 1,
          sourcePositionText: 'B-1'
        }
      ]
    })
    workflowApi.createClaimReturn.mockResolvedValue({ id: 'return-1' })
    const wrapper = mount(ReturnRegistrationDialog, {
      attachTo: document.body,
      props: { modelValue: false, sourceDocument: { id: 'source-1' } },
      global: { plugins: [ElementPlus], stubs: { ItemQuantityText: true } }
    })

    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const inputs = wrapper.findAllComponents({ name: 'ElInputNumber' })
    expect(inputs).toHaveLength(8)
    expect(inputs.map((input) => input.props('modelValue'))).toEqual([3, 0, 0, 0, 1, 0, 0, 0])

    inputs[0].vm.$emit('update:modelValue', 1)
    await wrapper.vm.$nextTick()
    const submitButton = wrapper.findAll('button')
      .find((button) => button.text() === '提交本次归还')
    await submitButton.trigger('click')
    await flushPromises()

    const payload = workflowApi.createClaimReturn.mock.calls[0][1]
    expect(payload.lines).toHaveLength(2)
    expect(payload.lines.map((line) => line.returnQuantity)).toEqual([1, 1])
    expect(payload.lines.every((line) => line.consumedQuantity === 0
      && line.lostQuantity === 0 && line.damagedQuantity === 0)).toBe(true)

    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('核销数量变化自动重算回库量且消耗无需填写原因', async () => {
    workflowApi.getReturnContext.mockResolvedValue({
      sourceDocumentNo: 'OUT-CONSUMED',
      locationName: '甲库',
      locationCode: 'A001',
      businessChainId: 'claim-consumed',
      overdue: false,
      canReturn: true,
      lines: [{
        sourceMaterialDocumentLineId: 'line-consumed',
        materialId: 'material-consumed',
        materialName: '数据线',
        materialType: 'CONSUMABLE',
        baseUnit: '根',
        issuedQuantity: 10,
        returnedQuantity: 0,
        settledQuantity: 0,
        remainingQuantity: 10,
        sourcePositionText: 'A-1'
      }]
    })
    workflowApi.createClaimReturn.mockReset()
    workflowApi.createClaimReturn.mockResolvedValue({ id: 'return-consumed' })
    const wrapper = mount(ReturnRegistrationDialog, {
      attachTo: document.body,
      props: { modelValue: false, sourceDocument: { id: 'source-consumed' } },
      global: { plugins: [ElementPlus], stubs: { ItemQuantityText: true } }
    })

    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    const inputs = wrapper.findAllComponents({ name: 'ElInputNumber' })
    expect(inputs.map((input) => input.props('modelValue'))).toEqual([10, 0, 0, 0])

    inputs[1].vm.$emit('update:modelValue', 2)
    inputs[1].vm.$emit('change', 2, 0)
    await wrapper.vm.$nextTick()
    expect(inputs[0].props('modelValue')).toBe(8)
    inputs[2].vm.$emit('update:modelValue', 1)
    inputs[2].vm.$emit('change', 1, 0)
    await wrapper.vm.$nextTick()
    expect(inputs[0].props('modelValue')).toBe(7)
    inputs[3].vm.$emit('update:modelValue', 1)
    inputs[3].vm.$emit('change', 1, 0)
    await wrapper.vm.$nextTick()
    expect(inputs[0].props('modelValue')).toBe(6)
    inputs[3].vm.$emit('update:modelValue', 0)
    inputs[3].vm.$emit('change', 0, 1)
    await wrapper.vm.$nextTick()
    expect(inputs[0].props('modelValue')).toBe(7)
    inputs[2].vm.$emit('update:modelValue', 0)
    inputs[2].vm.$emit('change', 0, 1)
    await wrapper.vm.$nextTick()
    expect(inputs[0].props('modelValue')).toBe(8)

    const submitButton = wrapper.findAll('button')
      .find((button) => button.text() === '提交本次归还')
    await submitButton.trigger('click')
    await flushPromises()

    expect(workflowApi.createClaimReturn).toHaveBeenCalledOnce()
    const payload = workflowApi.createClaimReturn.mock.calls[0][1]
    expect(payload.lines).toHaveLength(1)
    expect(payload.lines[0].returnQuantity).toBe(8)
    expect(payload.lines[0].consumedQuantity).toBe(2)
    expect(payload.lines[0]).not.toHaveProperty('consumedReason')

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('物料档案 SKU 规则', () => {
  const baseForm = {
    materialType: 'CONSUMABLE',
    trackMode: 'QUANTITY',
    skuCode: 'A703-0037',
    materialName: '六类非屏蔽跳线',
    specification: '山泽',
    baseUnit: '个',
    quantityMode: 'EXACT',
    enabled: 1
  }

  it('耗材即使携带 SKU 也始终提交空值', () => {
    expect(materialPayload(baseForm).skuCode).toBeNull()
  })

  it('固定资产保留 SKU 并清理首尾空格', () => {
    expect(materialPayload({
      ...baseForm,
      materialType: 'FIXED_ASSET',
      trackMode: 'SERIAL',
      skuCode: '  A000-0047  '
    }).skuCode).toBe('A000-0047')
  })
})

describe('库存数量展示语义', () => {
  it('明确区分当前实际可用与目标时段可约', () => {
    expect(CURRENT_STOCK_AVAILABILITY_NOTICE).toContain('当前时刻实际可用数量')
    expect(CURRENT_STOCK_AVAILABILITY_NOTICE).toContain('当前实际可用 ≠ 目标时段可约')
    expect(CURRENT_STOCK_AVAILABILITY_NOTICE).toContain('预约仍需按目标时段校验')
    expect(CURRENT_STOCK_AVAILABILITY_NOTICE).not.toContain('available')
  })
})

describe('实发默认数量', () => {
  it('库存充足时默认填入全部未领取数量', () => {
    expect(getConsumableIssueDefaults({
      remainingQuantity: 12,
      positions: [{ positionText: null, availableQuantity: 61 }]
    })).toEqual({ positionText: '', quantity: 12 })
  })

  it('库存不足时默认填入全部可发库存', () => {
    expect(getConsumableIssueDefaults({
      remainingQuantity: 12,
      positions: [{ positionText: 'A-1', availableQuantity: 5 }]
    })).toEqual({ positionText: 'A-1', quantity: 5 })
  })

  it('多个位置优先选择可发最多的位置，零库存不预填', () => {
    expect(getConsumableIssueDefaults({
      remainingQuantity: 20,
      positions: [
        { positionText: 'A-1', availableQuantity: 4 },
        { positionText: 'B-1', availableQuantity: 9 }
      ]
    })).toEqual({ positionText: 'B-1', quantity: 9 })
    expect(getConsumableIssueDefaults({
      remainingQuantity: 20,
      positions: [{ positionText: 'A-1', availableQuantity: 0 }]
    })).toEqual({ positionText: null, quantity: 0 })
  })

  it('固定资产默认按未领取量选满并优先本申领预占资产', () => {
    const line = {
      remainingQuantity: 2,
      candidateAssets: [
        { fixedAssetId: 'asset-1', reservedByThisClaim: false },
        { fixedAssetId: 'asset-2', reservedByThisClaim: true },
        { fixedAssetId: 'asset-3', reservedByThisClaim: false }
      ]
    }

    expect(getFixedAssetIssueDefaults(line)).toEqual(['asset-2', 'asset-1'])
    expect(getFixedAssetIssueDefaults({ ...line, remainingQuantity: 5 }))
      .toEqual(['asset-2', 'asset-1', 'asset-3'])
  })

  it('加载实发弹窗后自动填入默认位置和数量', async () => {
    workflowApi.getClaimIssueContext.mockReset()
    workflowApi.getClaimIssueContext.mockResolvedValue({
      locationName: 'A703电工电子实训室',
      locationCode: 'A703',
      bookingStartAt: '2026-10-29T00:00:00.000Z',
      bookingEndAt: '2026-10-29T13:45:00.000Z',
      issueEarliestAt: '2026-10-28T16:00:00.000Z',
      canIssue: true,
      pickupMode: 'SELF',
      riskWarnings: [],
      lines: [{
        claimLineId: 'line-1',
        materialId: 'material-1',
        materialName: '信息面板',
        materialType: 'CONSUMABLE',
        baseUnit: '个',
        remainingQuantity: 12,
        positions: [{ positionText: null, ownedQuantity: 61, availableQuantity: 61 }],
        candidateAssets: [],
        reservations: []
      }]
    })
    const locationStub = {
      name: 'ItemLocationSelect',
      props: { modelValue: { type: String, default: '' } },
      emits: ['update:modelValue', 'change'],
      template: '<div />'
    }
    const wrapper = mount(ClaimIssueDialog, {
      attachTo: document.body,
      props: { modelValue: true, claim: { id: 'claim-1' } },
      global: {
        plugins: [ElementPlus],
        stubs: { ItemLocationSelect: locationStub, ItemQuantityText: true }
      }
    })

    await flushPromises()
    const locationSelect = wrapper.findComponent({ name: 'ItemLocationSelect' })
    locationSelect.vm.$emit('update:modelValue', 'location-1')
    locationSelect.vm.$emit('change')
    await flushPromises()

    expect(workflowApi.getClaimIssueContext).toHaveBeenCalledWith('claim-1', 'location-1')
    expect(wrapper.findComponent({ name: 'ElInputNumber' }).props('modelValue')).toBe(12)
    expect(wrapper.findComponent({ name: 'ElSelect' }).props('modelValue')).toBe('')

    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('固定资产打开实发弹窗即默认全选并可一键提交', async () => {
    workflowApi.getClaimIssueContext.mockReset()
    workflowApi.getClaimIssueContext.mockResolvedValue({
      locationName: 'A703电工电子实训室',
      locationCode: 'A703',
      canIssue: true,
      pickupMode: 'SELF',
      riskWarnings: [],
      lines: [{
        claimLineId: 'asset-line-1',
        materialId: 'asset-material-1',
        materialName: '笔记本电脑',
        materialType: 'FIXED_ASSET',
        baseUnit: '台',
        remainingQuantity: 2,
        positions: [],
        candidateAssets: [
          { fixedAssetId: 'asset-1', assetCode: 'PC-1', reservedByThisClaim: false },
          { fixedAssetId: 'asset-2', assetCode: 'PC-2', reservedByThisClaim: true },
          { fixedAssetId: 'asset-3', assetCode: 'PC-3', reservedByThisClaim: false }
        ],
        reservations: []
      }]
    })
    workflowApi.issueClaim.mockReset()
    workflowApi.issueClaim.mockResolvedValue({ id: 'issue-1' })
    const wrapper = mount(ClaimIssueDialog, {
      attachTo: document.body,
      props: {
        modelValue: false,
        claim: { id: 'asset-claim-1', claimKind: 'CHILD', locationId: '101' }
      },
      global: { plugins: [ElementPlus], stubs: { ItemQuantityText: true } }
    })

    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(wrapper.findComponent({ name: 'ElSelect' }).props('modelValue'))
      .toEqual(['asset-2', 'asset-1'])
    const submitButton = wrapper.findAll('button')
      .find((button) => button.text() === '确认实发')
    await submitButton.trigger('click')
    await flushPromises()

    expect(workflowApi.issueClaim).toHaveBeenCalledOnce()
    const payload = workflowApi.issueClaim.mock.calls[0][1]
    expect(payload.locationId).toBe('101')
    expect(payload.lines.map((line) => line.fixedAssetId)).toEqual(['asset-2', 'asset-1'])
    expect(payload.lines.every((line) => line.quantity === 1)).toBe(true)

    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('CHILD子单自动使用所属库位且不提供库位选择器', async () => {
    workflowApi.getClaimIssueContext.mockReset()
    workflowApi.getClaimIssueContext.mockResolvedValue({
      locationName: 'A703电工电子实训室',
      locationCode: 'A703',
      canIssue: false,
      issueBlockedReason: '测试固定库位',
      riskWarnings: [],
      lines: []
    })
    const locationStub = {
      name: 'ItemLocationSelect',
      props: { modelValue: { type: String, default: '' } },
      template: '<div />'
    }
    const wrapper = mount(ClaimIssueDialog, {
      attachTo: document.body,
      props: {
        modelValue: false,
        claim: {
          id: 'child-1',
          claimKind: 'CHILD',
          locationId: '101',
          locationName: 'A703电工电子实训室',
          locationCode: 'A703'
        }
      },
      global: {
        plugins: [ElementPlus],
        stubs: { ItemLocationSelect: locationStub, ItemQuantityText: true }
      }
    })

    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(workflowApi.getClaimIssueContext).toHaveBeenCalledWith('child-1', '101')
    expect(wrapper.findComponent({ name: 'ItemLocationSelect' }).exists()).toBe(false)
    const fixedInput = wrapper.find('.claim_issue__location input')
    expect(fixedInput.attributes('disabled')).toBeDefined()
    expect(fixedInput.element.value).toBe('A703电工电子实训室 · A703')

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('入库幂等键展示', () => {
  it('页面不显示幂等键但提交仍自动携带', async () => {
    stockApi.createOpening.mockReset()
    stockApi.createOpening.mockResolvedValue({
      document: {
        documentNo: 'IN-1',
        businessScene: 'OPENING',
        processStatus: 'PROCESSED'
      },
      lines: []
    })
    const selectStub = (name) => ({
      name,
      props: { modelValue: { type: String, default: '' } },
      emits: ['update:modelValue'],
      template: '<div />'
    })
    const wrapper = mount(InboundPanel, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        stubs: {
          ItemMaterialSelect: selectStub('ItemMaterialSelect'),
          ItemLocationSelect: selectStub('ItemLocationSelect'),
          ItemQuantityText: true,
          ItemStatusPill: true,
          AssetImportDialog: true
        }
      }
    })

    expect(wrapper.text()).not.toContain('幂等键')
    expect(wrapper.find('.inbound_panel__idempotency').exists()).toBe(false)
    wrapper.findComponent({ name: 'ItemMaterialSelect' }).vm.$emit('update:modelValue', 'material-1')
    wrapper.findComponent({ name: 'ItemLocationSelect' }).vm.$emit('update:modelValue', 'location-1')
    await flushPromises()
    const submitButton = wrapper.findAll('button')
      .find((button) => button.text() === '提交期初入库')
    await submitButton.trigger('click')
    await flushPromises()

    expect(stockApi.createOpening).toHaveBeenCalledOnce()
    const payload = stockApi.createOpening.mock.calls[0][0]
    expect(payload.idempotencyKey).toEqual(expect.any(String))
    expect(payload.idempotencyKey.length).toBeGreaterThan(0)

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('物料与耗材名称筛选联想', () => {
  it('物料档案按名称片段及当前类型远程查询并去重', async () => {
    stockApi.getMaterials.mockReset()
    stockApi.getMaterials.mockResolvedValue({
      records: [
        { materialName: '电脑' },
        { materialName: '电脑' },
        { materialName: '电脑桌椅' },
        { materialName: ' ' }
      ],
      total: 4
    })
    const wrapper = mount(MaterialArchivePanel, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()
    const autocomplete = wrapper.findComponent({ name: 'ElAutocomplete' })
    expect(autocomplete.props('debounce')).toBe(300)
    wrapper.findAllComponents({ name: 'ElSelect' })[0].vm.$emit('update:modelValue', 'CONSUMABLE')
    await wrapper.vm.$nextTick()
    stockApi.getMaterials.mockClear()

    const callback = vi.fn()
    await autocomplete.props('fetchSuggestions')(' 电 ', callback)
    expect(stockApi.getMaterials).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 100,
      materialName: '电',
      materialType: 'CONSUMABLE'
    })
    expect(callback).toHaveBeenCalledWith([{ value: '电脑' }, { value: '电脑桌椅' }])

    stockApi.getMaterials.mockClear()
    const emptyCallback = vi.fn()
    await autocomplete.props('fetchSuggestions')('  ', emptyCallback)
    expect(emptyCallback).toHaveBeenCalledWith([])
    expect(stockApi.getMaterials).not.toHaveBeenCalled()

    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('耗材库存按名称片段远程查询并去重', async () => {
    stockApi.getBalances.mockReset()
    stockApi.getBalances.mockResolvedValue({
      records: [
        { materialName: '数据线' },
        { materialName: '数据线' },
        { materialName: '数据线收纳盒' },
        { materialName: null }
      ],
      total: 4
    })
    const wrapper = mount(ConsumableStockPanel, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        stubs: { ItemLocationSelect: true, ItemQuantityText: true }
      }
    })
    await flushPromises()
    const autocomplete = wrapper.findComponent({ name: 'ElAutocomplete' })
    expect(autocomplete.props('debounce')).toBe(300)
    stockApi.getBalances.mockClear()

    const callback = vi.fn()
    await autocomplete.props('fetchSuggestions')(' 数据 ', callback)
    expect(stockApi.getBalances).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 100,
      materialName: '数据',
      locationId: undefined
    })
    expect(callback).toHaveBeenCalledWith([{ value: '数据线' }, { value: '数据线收纳盒' }])

    stockApi.getBalances.mockClear()
    const emptyCallback = vi.fn()
    await autocomplete.props('fetchSuggestions')('', emptyCallback)
    expect(emptyCallback).toHaveBeenCalledWith([])
    expect(stockApi.getBalances).not.toHaveBeenCalled()

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('耗材库内位置编辑', () => {
  it('弹窗仅提供位置输入并按库存版本提交', async () => {
    stockApi.getBalances.mockReset()
    stockApi.updateBalancePosition.mockReset()
    stockApi.getBalances.mockResolvedValue({
      records: [{
        id: 'balance-1',
        materialId: 'material-1',
        materialName: '数据线',
        specification: 'Type-C',
        baseUnit: '根',
        locationId: 'location-1',
        locationCode: 'A703',
        locationName: 'A703库',
        positionText: 'A-1',
        ownedQuantity: 5,
        loanedQuantity: 0,
        availableQuantity: 5,
        version: 3,
        updateTime: null
      }],
      total: 1,
      pageNum: 1,
      pageSize: 20
    })
    stockApi.updateBalancePosition.mockResolvedValue(null)
    const wrapper = mount(ConsumableStockPanel, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        stubs: { ItemLocationSelect: true, ItemQuantityText: true }
      }
    })

    await flushPromises()
    const editButton = wrapper.findAll('button').find((button) => button.text() === '编辑')
    expect(editButton).toBeTruthy()
    await editButton.trigger('click')
    await wrapper.vm.$nextTick()

    const dialog = wrapper.find('.el-dialog')
    expect(dialog.exists()).toBe(true)
    expect(dialog.text()).toContain('数据线')
    expect(dialog.text()).toContain('A703库')
    expect(dialog.findAll('input')).toHaveLength(1)
    const input = dialog.find('input')
    expect(input.element.value).toBe('A-1')
    await input.setValue('A-2')
    const saveButton = wrapper.findAll('button').find((button) => button.text() === '保存')
    await saveButton.trigger('click')
    await flushPromises()

    expect(stockApi.updateBalancePosition).toHaveBeenCalledWith('balance-1', {
      positionText: 'A-2',
      version: 3
    })

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('在库核销物料远程搜索', () => {
  it('耗材和固定资产均可输入名称后按当前库位联想选择', async () => {
    workflowApi.pageDocuments.mockReset()
    workflowApi.pageDocuments.mockResolvedValue({ records: [], total: 0 })
    stockApi.getBalances.mockReset()
    stockApi.getAssets.mockReset()
    stockApi.getBalances.mockResolvedValue({
      records: [{
        id: 'balance-1', materialId: 'material-1', materialName: '信息面板', baseUnit: '个',
        locationId: 'location-1', positionText: 'A-1', ownedQuantity: 10,
        loanedQuantity: 0, availableQuantity: 10, version: 1
      }],
      total: 1
    })
    stockApi.getAssets.mockResolvedValue({
      records: [{
        id: 'asset-1', materialId: 'material-2', materialName: '二层交换机',
        assetCode: 'A000-0047', locationId: 'location-1', status: 'IDLE'
      }],
      total: 1
    })
    const locationStub = {
      name: 'ItemLocationSelect',
      props: { modelValue: { type: String, default: '' } },
      emits: ['update:modelValue', 'change'],
      template: '<div />'
    }
    const wrapper = mount(SettlementPanel, {
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        directives: { permission: () => {} },
        stubs: {
          ItemLocationSelect: locationStub,
          ItemStatusPill: true,
          DocumentDetailDrawer: true,
          SettlementReviewDialog: true
        }
      }
    })

    await flushPromises()
    const createButton = wrapper.findAll('button')
      .find((button) => button.text() === '登记在库核销')
    await createButton.trigger('click')
    await wrapper.vm.$nextTick()
    const location = wrapper.findAllComponents(locationStub).at(-1)
    location.vm.$emit('update:modelValue', 'location-1')
    await wrapper.vm.$nextTick()
    location.vm.$emit('change')
    await flushPromises()

    const consumableSelect = wrapper.findAllComponents({ name: 'ElSelect' })
      .find((component) => component.props('placeholder') === '输入耗材名称搜索并选择存放位置')
    expect(consumableSelect.props('filterable')).toBe(true)
    expect(consumableSelect.props('remote')).toBe(true)
    await consumableSelect.vm.remoteMethod('信息')
    await new Promise((resolve) => setTimeout(resolve, 350))
    await flushPromises()
    expect(stockApi.getBalances).toHaveBeenCalledWith(expect.objectContaining({
      locationId: 'location-1',
      materialName: '信息'
    }))

    const fixedAssetRadio = wrapper.findAll('.el-radio-button')
      .find((element) => element.text() === '固定资产')
    await fixedAssetRadio.trigger('click')
    await wrapper.vm.$nextTick()
    const assetSelect = wrapper.findAllComponents({ name: 'ElSelect' })
      .find((component) => component.props('placeholder') === '输入资产名称搜索并逐台选择')
    expect(assetSelect.props('filterable')).toBe(true)
    expect(assetSelect.props('remote')).toBe(true)
    await assetSelect.vm.remoteMethod('二层')
    await new Promise((resolve) => setTimeout(resolve, 350))
    await flushPromises()
    expect(stockApi.getAssets).toHaveBeenCalledWith(expect.objectContaining({
      locationId: 'location-1',
      status: 'IDLE',
      materialName: '二层'
    }))
    expect(stockApi.getAssets).toHaveBeenCalledWith(expect.objectContaining({
      locationId: 'location-1',
      status: 'MAINTENANCE',
      materialName: '二层'
    }))

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})
