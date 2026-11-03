import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus, { ElMessage } from 'element-plus'
import ItemLocationSelect from '@/components/item/ItemLocationSelect.vue'
import DocumentCenterPanel from '@/views/admin/item/panels/DocumentCenterPanel.vue'
import request from '@/api/request'
import MaterialCatalogCart from './MaterialCatalogCart.vue'
import { normalizeClaimDraftLines, setMaterialQuantity } from './materialCart.rules'
import {
  assetStatusLabel,
  businessSceneLabel,
  documentTypeLabel,
  eventTypeLabel,
  reservationRuleLabel,
  settlementTypeLabel,
  stockEventTypeLabel
} from './claim.constants'
import {
  addClaimLine,
  buildClaimPayload,
  buildClaimSubmissionPayload,
  canIssueClaim,
  canReviewClaim,
  getClaimAllocationsError,
  getClaimIssueLocationId,
  getClaimLinesError,
  groupClaimAllocationsByLocation,
  groupContiguousPeriods,
  resolveClaimPeriodIds,
  shouldRefreshClaimPreview,
  toggleClaimPeriod
} from './claim.rules'

vi.mock('@/api/request', () => ({ default: { get: vi.fn() } }))

function claimSource() {
  const form = {
    purpose: '  课程实验  ',
    bookingDate: '2026-10-28',
    startPeriodId: 1,
    endPeriodId: 4,
    periodIds: [1, 2, 4],
    revisionNo: 7,
    reviewStatus: 'REJECTED',
    reviewerId: 99,
    reservations: [{ id: 88 }]
  }
  const lines = [{
    materialId: 10,
    materialName: '示波器',
    materialType: 'FIXED_ASSET',
    requestedQuantity: '2',
    reviewStatus: 'APPROVED',
    reservations: [{ id: 88 }]
  }]
  const allocations = [{
    materialId: 10,
    locationId: 20,
    plannedQuantity: '2',
    reservationId: 88,
    availableQuantity: 9
  }]
  const payload = {
    purpose: '课程实验',
    bookingDate: '2026-10-28',
    startPeriodId: '1',
    endPeriodId: '4',
    periodIds: ['1', '2', '4'],
    lines: [{ materialId: '10', requestedQuantity: 2 }],
    allocations: [{ materialId: '10', locationId: '20', plannedQuantity: 2 }]
  }
  return { form, lines, allocations, payload }
}

describe('申领 payload', () => {
  it('新建只提交预约字段、物料、分配和幂等键', () => {
    const { form, lines, allocations, payload } = claimSource()

    expect(buildClaimPayload(form, lines, allocations)).toEqual(payload)
    expect(buildClaimSubmissionPayload('create', payload, {
      revisionNo: 7,
      idempotencyKey: 'create-key'
    })).toEqual({ ...payload, idempotencyKey: 'create-key' })
  })

  it('修改携带 revisionNo 且不携带新建幂等键', () => {
    const { payload } = claimSource()
    const result = buildClaimSubmissionPayload('edit', payload, {
      revisionNo: 7,
      idempotencyKey: 'unused-key'
    })

    expect(result).toEqual({ ...payload, revisionNo: 7 })
    expect(result).not.toHaveProperty('idempotencyKey')
  })

  it('再次预约使用新幂等键且不带旧审核、版本和预占数据', () => {
    const { form, lines, allocations, payload } = claimSource()
    const result = buildClaimSubmissionPayload(
      'rebook',
      buildClaimPayload(form, lines, allocations),
      { revisionNo: 7, idempotencyKey: 'rebook-key' }
    )

    expect(result).toEqual({ ...payload, idempotencyKey: 'rebook-key' })
    expect(result).not.toHaveProperty('revisionNo')
    expect(result).not.toHaveProperty('reviewStatus')
    expect(result).not.toHaveProperty('reviewerId')
    expect(result).not.toHaveProperty('reservations')
    expect(result.lines[0]).not.toHaveProperty('reservations')
    expect(result.allocations[0]).not.toHaveProperty('reservationId')
  })
})

describe('按库位拆分子申领单的前端规则', () => {
  it('按库位分组成子单预览并保留各分配行与库位信息', () => {
    const allocations = [
      { materialId: 1, locationId: 20, locationCode: 'A01', locationName: '甲库', plannedQuantity: 2 },
      { materialId: 2, locationId: '20', locationCode: 'A01', locationName: '甲库', plannedQuantity: 1 },
      { materialId: 1, locationId: 21, locationCode: 'B01', locationName: '乙库', plannedQuantity: 3 }
    ]

    const groups = groupClaimAllocationsByLocation(allocations)

    expect(groups).toHaveLength(2)
    expect(groups[0]).toMatchObject({ locationId: '20', locationCode: 'A01', locationName: '甲库' })
    expect(groups[0].allocations).toEqual([allocations[0], allocations[1]])
    expect(groups[1]).toMatchObject({ locationId: '21', locationCode: 'B01', locationName: '乙库' })
    expect(groups[1].allocations).toEqual([allocations[2]])
    expect(allocations).toHaveLength(3)
  })

  it('ROOT 即使误返回可操作标记也不显示审核和实发入口', () => {
    const root = { claimKind: 'ROOT', canReview: true, canIssue: true }

    expect(canReviewClaim(root)).toBe(false)
    expect(canIssueClaim(root)).toBe(false)
  })

  it('CHILD、STANDALONE 与历史无类型申领沿用后端操作标记', () => {
    for (const claimKind of ['CHILD', 'STANDALONE', undefined]) {
      expect(canReviewClaim({ claimKind, canReview: true })).toBe(true)
      expect(canIssueClaim({ claimKind, canIssue: true })).toBe(true)
      expect(canReviewClaim({ claimKind, canReview: false })).toBe(false)
      expect(canIssueClaim({ claimKind, canIssue: false })).toBe(false)
    }
  })

  it('CHILD 实发库位固定为详情所属库位，独立申领仍使用已选库位', () => {
    const child = { claimKind: 'CHILD', locationId: 20 }

    expect(getClaimIssueLocationId(child, 99)).toBe('20')
    expect(getClaimIssueLocationId({ claimKind: 'STANDALONE' }, 99)).toBe('99')
    expect(getClaimIssueLocationId({ claimKind: 'STANDALONE' })).toBe('')
  })
})

describe('申领节次卡片选择与分段', () => {
  it('可自由跳选并按节次号排序去重', () => {
    const selected = toggleClaimPeriod([4, 2, 2], 1)

    expect(selected).toEqual([1, 2, 4])
    expect(toggleClaimPeriod(selected, 6)).toEqual([1, 2, 4, 6])
  })

  it('可取消中间节次并保留前后两个非连续段', () => {
    expect(toggleClaimPeriod([1, 2, 3], 2)).toEqual([1, 3])
    expect(toggleClaimPeriod([1, 3], 3)).toEqual([1])
  })

  it('按极大连续段分组，补选间断节次后合并段', () => {
    expect(groupContiguousPeriods([4, 1, 2, 2])).toEqual([[1, 2], [4]])
    expect(groupContiguousPeriods(toggleClaimPeriod([1, 2, 4], 3))).toEqual([[1, 2, 3, 4]])
  })

  it('编辑按详情段展开实际节次ID，不填补跳选段之间的空缺', () => {
    const periods = [
      { id: 104, periodNo: 4 },
      { id: 101, periodNo: 1 },
      { id: 103, periodNo: 3 },
      { id: 102, periodNo: 2 }
    ]
    const detail = {
      startPeriodId: 101,
      endPeriodId: 104,
      segments: [
        { segmentSeq: 2, startPeriodId: 104, endPeriodId: 104 },
        { segmentSeq: 1, startPeriodId: 101, endPeriodId: 102 }
      ]
    }

    expect(resolveClaimPeriodIds(detail, periods)).toEqual(['101', '102', '104'])
  })

  it('历史无段详情才按旧起止节次补满', () => {
    const periods = [
      { id: 101, periodNo: 1 },
      { id: 102, periodNo: 2 },
      { id: 103, periodNo: 3 },
      { id: 104, periodNo: 4 }
    ]

    expect(resolveClaimPeriodIds({ startPeriodId: 101, endPeriodId: 103, segments: [] }, periods))
      .toEqual(['101', '102', '103'])
  })
})

describe('库存目录加购规则', () => {
  function catalogMaterial(changes = {}) {
    return {
      materialId: 10,
      materialName: '试剂',
      specification: '500ml',
      materialType: 'CONSUMABLE',
      quantityMode: 'DECIMAL',
      baseUnit: '瓶',
      ownedQuantity: 20,
      availableQuantity: 10,
      ...changes
    }
  }

  it('添加时移除空初始行，重复添加同一物料会合并整数数量', () => {
    const initial = [{ materialId: '', requestedQuantity: null }]
    const added = addClaimLine(initial, catalogMaterial(), '1')

    expect(added.error).toBe('')
    expect(added.lines).toHaveLength(1)
    expect(added.lines[0]).toMatchObject({
      materialId: '10',
      materialName: '试剂',
      requestedQuantity: 1,
      availableQuantity: 10
    })
    expect(initial).toEqual([{ materialId: '', requestedQuantity: null }])

    const merged = addClaimLine(added.lines, catalogMaterial(), '1')
    expect(merged.error).toBe('')
    expect(merged.lines).toHaveLength(1)
    expect(merged.lines[0].requestedQuantity).toBe(2)
    expect(getClaimLinesError(merged.lines)).toBe('')
  })

  it('删除最后一项形成空购物车后由结算校验拦截', () => {
    const { lines } = addClaimLine([], catalogMaterial(), 1)
    const remaining = lines.filter((_, index) => index !== 0)

    expect(remaining).toEqual([])
    expect(getClaimLinesError(remaining)).toBe('请至少添加一项物料')
    expect(getClaimLinesError([])).toBe('请至少添加一项物料')
  })

  it.each([0, -1, null])('加购数量必须为正数：%s', (quantity) => {
    expect(addClaimLine([], catalogMaterial(), quantity).error).toContain('有效申请数量')
  })

  it.each([
    { label: '耗材', materialType: 'CONSUMABLE' },
    { label: '固定资产', materialType: 'FIXED_ASSET' }
  ])('$label加购数量必须为整数', ({ materialType }) => {
    const material = catalogMaterial({ materialType, availableQuantity: 5 })

    expect(addClaimLine([], material, 1.5).error)
      .toBe('物料「试剂」的申请数量必须为整数')
    expect(addClaimLine([], material, 2).error).toBe('')
  })

  it('首次或重复加购超过预计可约量时提示库存不足且购物车不变', () => {
    const material = catalogMaterial({ availableQuantity: 2 })
    const added = addClaimLine([], material, 1)
    const merged = addClaimLine(added.lines, material, 1)

    expect(merged.error).toBe('')
    expect(merged.lines[0].requestedQuantity).toBe(2)
    expect(addClaimLine(merged.lines, material, 1)).toEqual({
      lines: merged.lines,
      error: '库存不足'
    })
    expect(addClaimLine([], material, 3)).toEqual({ lines: [], error: '库存不足' })
  })
})

describe('申领数量与计划分配规则', () => {
  it.each([
    { label: '耗材', materialType: 'CONSUMABLE' },
    { label: '固定资产', materialType: 'FIXED_ASSET' }
  ])('$label申请数量与计划分配数量都必须是整数', ({ materialType }) => {
    const line = {
      materialId: 10,
      materialName: '测试物料',
      materialType,
      requestedQuantity: 1.5
    }

    expect(getClaimLinesError([line])).toBe('物料「测试物料」的申请数量必须为整数')
    expect(getClaimAllocationsError(
      [{ materialId: 10, locationId: 20, plannedQuantity: 1.5 }],
      [line]
    )).toBe('计划分配数量必须为整数')
    expect(getClaimLinesError([{ ...line, requestedQuantity: 2 }])).toBe('')
    expect(getClaimAllocationsError(
      [{ materialId: 10, locationId: 20, plannedQuantity: 2 }],
      [{ ...line, requestedQuantity: 2 }]
    )).toBe('')
  })

  it('申请数量与计划分配数量的 0 值仍按正数必填处理', () => {
    const line = {
      materialId: 10,
      materialName: '试剂',
      materialType: 'CONSUMABLE',
      requestedQuantity: 0
    }

    expect(getClaimLinesError([line])).toContain('有效申请数量')
    expect(getClaimAllocationsError(
      [{ materialId: 10, locationId: 20, plannedQuantity: 0 }],
      [{ ...line, requestedQuantity: 1 }]
    )).toBe('计划分配数量必须大于 0')
  })

  it('每项物料的计划分配合计必须等于申请数量', () => {
    const lines = [{
      materialId: 10,
      materialName: '试剂',
      materialType: 'CONSUMABLE',
      requestedQuantity: 5
    }]
    const allocations = [
      { materialId: 10, locationId: 20, plannedQuantity: 2 },
      { materialId: 10, locationId: 21, plannedQuantity: 1 }
    ]

    expect(getClaimAllocationsError(allocations, lines))
      .toBe('物料「试剂」的计划分配合计必须等于申请数量')
    allocations[1].plannedQuantity = 3
    expect(getClaimAllocationsError(allocations, lines)).toBe('')
  })
})

describe('申领预占展示与冲突恢复', () => {
  it('预约时段与上海全天低保有资产使用不同预占标签', () => {
    expect(reservationRuleLabel('BOOKING_WINDOW')).toBe('按预约时段预占')
    expect(reservationRuleLabel('SHANGHAI_DAY_LOW_TOTAL')).toBe('低保有资产按预约日全天预占')
    expect(reservationRuleLabel('OTHER_RULE')).toBe('OTHER_RULE')
    expect(reservationRuleLabel('')).toBe('—')
  })

  it.each([
    [{ response: { status: 409 } }],
    [{ response: { data: { code: 409 } } }],
    [{ response: { data: { code: 40900 } } }]
  ])('409 提交冲突进入重新预览分支：%j', (error) => {
    expect(shouldRefreshClaimPreview(error)).toBe(true)
  })

  it('普通提交错误不触发重新预览', () => {
    expect(shouldRefreshClaimPreview({ response: { status: 500 } })).toBe(false)
    expect(shouldRefreshClaimPreview(new Error('network'))).toBe(false)
  })
})

describe('申领数量直填与本地草稿', () => {
  const material = {
    materialId: 1,
    materialName: '信息面板',
    materialType: 'CONSUMABLE',
    baseUnit: '个',
    availableQuantity: 10
  }

  it('填写数量直接加入或更新物料，不重复累加', () => {
    const added = setMaterialQuantity([], material, 2)
    const updated = setMaterialQuantity(added.lines, material, 4)

    expect(added.error).toBe('')
    expect(updated.lines).toHaveLength(1)
    expect(updated.lines[0]).toMatchObject({ materialId: '1', requestedQuantity: 4 })
  })

  it('清空数量移除物料，超量输入保留原购物车', () => {
    const added = setMaterialQuantity([], material, 2)
    const rejected = setMaterialQuantity(added.lines, material, 99)

    expect(rejected).toEqual({ lines: added.lines, error: '库存不足' })
    expect(setMaterialQuantity(added.lines, material, null)).toEqual({ lines: [], error: '' })
  })

  it('本地草稿仅保留有效的唯一物料数量', () => {
    const lines = normalizeClaimDraftLines([
      { materialId: 1, materialName: '信息面板', requestedQuantity: 2 },
      { materialId: '1', materialName: '重复项', requestedQuantity: 3 },
      { materialId: 2, requestedQuantity: 0 },
      { materialName: '缺少ID', requestedQuantity: 1 }
    ])

    expect(lines).toHaveLength(1)
    expect(lines[0]).toMatchObject({ materialId: '1', requestedQuantity: 2 })
  })
})

describe('库存目录数量输入边界', () => {
  it('零预计可约物料禁用输入且不会出现最大值小于最小值', async () => {
    const wrapper = mount(MaterialCatalogCart, {
      props: {
        mode: 'claim',
        active: true,
        loadCatalog: async () => ({
          records: [{
            materialId: 1,
            materialName: '零库存物料',
            ownedQuantity: 0,
            availableQuantity: 0,
            baseUnit: '个'
          }],
          total: 1
        })
      },
      global: {
        plugins: [ElementPlus],
        stubs: { MaterialLinesEditor: true, ItemQuantityText: true }
      }
    })

    await flushPromises()
    const input = wrapper.findComponent({ name: 'ElInputNumber' })
    expect(input.props('disabled')).toBe(true)
    expect(input.props('min')).toBe(0)
    expect(input.props('max')).toBe(Number.MAX_SAFE_INTEGER)
  })

  it('超量输入提示库存不足且不更新购物车', async () => {
    const warning = vi.spyOn(ElMessage, 'warning').mockImplementation(() => {})
    const wrapper = mount(MaterialCatalogCart, {
      props: {
        mode: 'claim',
        active: true,
        loadCatalog: async () => ({
          records: [{
            materialId: 1,
            materialName: '信息面板',
            ownedQuantity: 61,
            availableQuantity: 61,
            baseUnit: '个'
          }],
          total: 1
        })
      },
      global: {
        plugins: [ElementPlus],
        stubs: { MaterialLinesEditor: true, ItemQuantityText: true }
      }
    })

    await flushPromises()
    const input = wrapper.findComponent({ name: 'ElInputNumber' })
    expect(input.props('max')).toBe(Number.MAX_SAFE_INTEGER)
    input.vm.$emit('update:modelValue', 99)
    await flushPromises()

    expect(warning).toHaveBeenCalledWith('库存不足')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.emitted('change')).toBeUndefined()
    warning.mockRestore()
  })

  it('有效数量直填即加入购物车且不再显示操作列和添加按钮', async () => {
    const wrapper = mount(MaterialCatalogCart, {
      props: {
        mode: 'claim',
        active: true,
        loadCatalog: async () => ({
          records: [{
            materialId: 1,
            materialName: '信息面板',
            materialType: 'CONSUMABLE',
            ownedQuantity: 61,
            availableQuantity: 61,
            baseUnit: '个'
          }],
          total: 1
        })
      },
      global: {
        plugins: [ElementPlus],
        stubs: { MaterialLinesEditor: true, ItemQuantityText: true }
      }
    })

    await flushPromises()
    wrapper.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:modelValue', 2)
    await flushPromises()

    expect(wrapper.emitted('update:modelValue')[0][0]).toMatchObject([{
      materialId: '1',
      requestedQuantity: 2
    }])
    expect(wrapper.findAll('th').map((th) => th.text())).not.toContain('操作')
    expect(wrapper.findAll('button').map((button) => button.text())).not.toContain('添加')
  })

  it('申请数量保存到浏览器并能在重新打开时恢复', async () => {
    const draftKey = 'test-claim-material-draft'
    globalThis.localStorage.removeItem(draftKey)
    const loadCatalog = async () => ({
      records: [{
        materialId: 1,
        materialName: '信息面板',
        materialType: 'CONSUMABLE',
        ownedQuantity: 61,
        availableQuantity: 61,
        baseUnit: '个'
      }],
      total: 1
    })
    const options = {
      props: { mode: 'claim', active: true, loadCatalog, draftKey },
      global: {
        plugins: [ElementPlus],
        stubs: { MaterialLinesEditor: true, ItemQuantityText: true }
      }
    }
    const first = mount(MaterialCatalogCart, options)
    await flushPromises()
    first.findComponent({ name: 'ElInputNumber' }).vm.$emit('update:modelValue', 2)
    await flushPromises()
    expect(JSON.parse(globalThis.localStorage.getItem(draftKey))[0].requestedQuantity).toBe(2)
    first.unmount()

    const reopened = mount(MaterialCatalogCart, options)
    await flushPromises()
    expect(reopened.emitted('update:modelValue')[0][0]).toMatchObject([{
      materialId: '1',
      requestedQuantity: 2
    }])
    globalThis.localStorage.removeItem(draftKey)
  })
})

describe('申领库位选择', () => {
  it('使用申领轻量选项接口并直接展示数组结果', async () => {
    request.get.mockResolvedValue([{
      id: 101,
      locationCode: 'ITEM_A',
      locationName: '物料甲库',
      enabled: 1
    }])
    const wrapper = mount(ItemLocationSelect, {
      props: {
        forClaim: true,
        modelValue: '101',
        currentOption: {
          id: '101',
          locationCode: 'ITEM_A',
          locationName: '物料甲库',
          enabled: 1
        }
      },
      global: { plugins: [ElementPlus] }
    })

    await flushPromises()
    expect(request.get).toHaveBeenCalledWith('/item/locations/options', {
      params: { keyword: undefined }
    })
    expect(wrapper.findComponent({ name: 'ElOption' }).props('label')).toBe('物料甲库')
    expect(wrapper.find('.el-select__placeholder').text()).toBe('物料甲库')
  })
})

describe('库存单据业务链筛选', () => {
  it('按字符串传递完整业务链ID并在重置后清空条件', async () => {
    request.get.mockImplementation(() => Promise.resolve({ records: [], total: 0 }))
    const wrapper = mount(DocumentCenterPanel, {
      global: {
        plugins: [ElementPlus],
        directives: { permission: { mounted() {} } },
        stubs: { DocumentDetailDrawer: true, ReturnRegistrationDialog: true, ItemStatusPill: true }
      }
    })
    await flushPromises()

    const chainInput = wrapper.find('input[placeholder="ALL业务链号或历史数值ID"]')
    await chainInput.setValue('ALL2026111000001')
    request.get.mockClear()
    await wrapper.findAll('button').find((button) => button.text() === '查询').trigger('click')
    await flushPromises()
    expect(request.get).toHaveBeenCalledWith('/item/documents', expect.objectContaining({
      params: expect.objectContaining({ businessChainId: 'ALL2026111000001' })
    }))

    await chainInput.setValue('2115364261393514497')
    request.get.mockClear()
    await wrapper.findAll('button').find((button) => button.text() === '查询').trigger('click')
    await flushPromises()
    expect(request.get).toHaveBeenCalledWith('/item/documents', expect.objectContaining({
      params: expect.objectContaining({ businessChainId: '2115364261393514497' })
    }))

    await wrapper.findAll('button').find((button) => button.text() === '重置').trigger('click')
    await flushPromises()
    const documentCalls = request.get.mock.calls.filter(([url]) => url === '/item/documents')
    expect(documentCalls.at(-1)[1].params.businessChainId).toBeUndefined()
    expect(chainInput.element.value).toBe('')
  })
})

describe('库存单据中文展示', () => {
  it('单据类型、业务场景和核销类型不直接显示内部编码', () => {
    expect(documentTypeLabel('INBOUND')).toBe('入库单')
    expect(documentTypeLabel('OPENING')).toBe('期初入库单')
    expect(businessSceneLabel('CLAIM_RETURN')).toBe('申领归还')
    expect(settlementTypeLabel('CONSUMED')).toBe('教学损耗')
    expect(assetStatusLabel('IDLE')).toBe('在库')
    expect(assetStatusLabel('ON_LOAN')).toBe('借出中')
    expect(documentTypeLabel('UNKNOWN_TYPE')).toBe('库存单据')
    expect(businessSceneLabel('UNKNOWN_SCENE')).toBe('其他业务')
  })

  it('库存事件按业务场景显示中文，归还与普通入库不会混淆', () => {
    expect(stockEventTypeLabel('STOCK_IN', 'CLAIM_RETURN')).toBe('归还入库')
    expect(stockEventTypeLabel('STOCK_IN', 'OPENING')).toBe('入库')
    expect(stockEventTypeLabel('STOCK_OUT', 'CLAIM_ISSUE')).toBe('实发出库')
    expect(eventTypeLabel('TRANSFER_SEND')).toBe('移动发出')
    expect(eventTypeLabel('UNKNOWN_EVENT')).toBe('状态变更')
  })
})
