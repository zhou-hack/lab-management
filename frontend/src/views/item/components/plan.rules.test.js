import { describe, expect, it } from 'vitest'
import { addPlanLine } from './materialCart.rules'
import { buildPlanPayload, getPlanLinesError, normalizePlanLines } from './plan.rules'

function planMaterial(changes = {}) {
  return {
    materialId: 10,
    materialName: '试剂',
    specification: '500ml',
    materialType: 'CONSUMABLE',
    quantityMode: 'DECIMAL',
    baseUnit: '瓶',
    ownedQuantity: 2,
    ...changes
  }
}

describe('方案目录加购规则', () => {
  it('重复添加按 materialId 合并整数默认数量并移除空初始行', () => {
    const initial = [{ materialId: '', defaultQuantity: null }]
    const added = addPlanLine(initial, planMaterial(), '1')
    const merged = addPlanLine(added.lines, planMaterial(), '1')

    expect(added.error).toBe('')
    expect(merged.error).toBe('')
    expect(merged.lines).toHaveLength(1)
    expect(merged.lines[0]).toMatchObject({
      materialId: '10',
      materialName: '试剂',
      defaultQuantity: 2
    })
    expect(getPlanLinesError(merged.lines)).toBe('')
    expect(initial).toEqual([{ materialId: '', defaultQuantity: null }])
  })

  it('默认数量允许超过保有量，零保有也可添加', () => {
    const material = planMaterial({ ownedQuantity: 0 })
    const added = addPlanLine([], material, 20)
    const merged = addPlanLine(added.lines, material, 5)

    expect(added.error).toBe('')
    expect(added.lines[0].defaultQuantity).toBe(20)
    expect(merged.error).toBe('')
    expect(merged.lines[0].defaultQuantity).toBe(25)
  })

  it.each([
    { label: '耗材', materialType: 'CONSUMABLE' },
    { label: '固定资产', materialType: 'FIXED_ASSET' }
  ])('$label默认数量必须为整数且不限制为保有量', ({ materialType }) => {
    const material = planMaterial({ materialType, ownedQuantity: 1 })

    expect(addPlanLine([], material, 1.5).error)
      .toBe('物料「试剂」的默认数量必须为整数')
    expect(getPlanLinesError([{ ...material, defaultQuantity: 1.5 }]))
      .toBe('物料「试剂」的默认数量必须为整数')
    expect(addPlanLine([], material, 2).error).toBe('')
  })

  it.each([0, -1, null])('默认数量必须为正数：%s', (quantity) => {
    expect(addPlanLine([], planMaterial(), quantity).error).toContain('有效默认数量')
    expect(getPlanLinesError([{ ...planMaterial(), defaultQuantity: quantity }])).toContain('有效默认数量')
  })
})

describe('方案编辑与保存规则', () => {
  it('空购物车会被保存校验拦截', () => {
    expect(getPlanLinesError([])).toBe('请至少添加一项方案物料')
  })

  it('编辑回显 defaultQuantity，空方案不自动补空白行', () => {
    expect(normalizePlanLines([{
      materialId: 10,
      materialName: '试剂',
      materialType: 'CONSUMABLE',
      baseUnit: '瓶',
      defaultQuantity: '1.25'
    }])).toEqual([{
      materialId: '10',
      materialName: '试剂',
      materialType: 'CONSUMABLE',
      quantityMode: '',
      baseUnit: '瓶',
      specification: '',
      defaultQuantity: 1.25
    }])
    expect(normalizePlanLines([])).toEqual([])
  })

  it('保存 DTO 仅包含原方案字段和物料默认数量', () => {
    const payload = buildPlanPayload({
      planName: '  电路实验  ',
      enabled: '0',
      remark: '   '
    }, [{
      materialId: 10,
      materialName: '不提交的名称',
      defaultQuantity: '2',
      ownedQuantity: 1
    }])

    expect(payload).toEqual({
      planName: '电路实验',
      enabled: 0,
      remark: undefined,
      lines: [{ materialId: '10', defaultQuantity: 2 }]
    })
  })
})
