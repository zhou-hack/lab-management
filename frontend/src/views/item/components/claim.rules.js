import { addMaterialLine } from './materialCart.rules'

export function buildClaimPayload(form, lines, allocations) {
  return {
    purpose: form.purpose.trim(),
    bookingDate: form.bookingDate,
    startPeriodId: String(form.startPeriodId),
    endPeriodId: String(form.endPeriodId),
    lines: lines.map((line) => ({
      materialId: String(line.materialId),
      requestedQuantity: Number(line.requestedQuantity)
    })),
    allocations: (allocations || []).map((allocation) => ({
      materialId: String(allocation.materialId),
      locationId: String(allocation.locationId),
      plannedQuantity: Number(allocation.plannedQuantity)
    }))
  }
}

export function buildClaimSubmissionPayload(mode, payload, { revisionNo, idempotencyKey } = {}) {
  return mode === 'edit'
    ? { ...payload, revisionNo }
    : { ...payload, idempotencyKey }
}

export function addClaimLine(lines, material, requestedQuantity) {
  return addMaterialLine(lines, material, requestedQuantity, {
    mode: 'claim',
    quantityField: 'requestedQuantity'
  })
}

export function getClaimLinesError(lines) {
  if (!lines.length) return '请至少添加一项物料'
  const materialIds = new Set()
  for (const line of lines) {
    if (!line.materialId) return '请选择每一行的物料'
    if (materialIds.has(String(line.materialId))) {
      return `物料「${line.materialName || line.materialId}」重复，请合并为一行`
    }
    materialIds.add(String(line.materialId))
    const quantity = Number(line.requestedQuantity)
    if (!Number.isFinite(quantity) || quantity <= 0) {
      return `请填写物料「${line.materialName || line.materialId}」的有效申请数量`
    }
    if (!Number.isInteger(quantity)) {
      return `物料「${line.materialName || line.materialId}」的申请数量必须为整数`
    }
  }
  return ''
}

export function getClaimAllocationsError(allocations, lines) {
  if (!allocations.length) return '请添加计划分配'
  const uniqueKeys = new Set()
  const totals = new Map()
  for (const allocation of allocations) {
    if (!allocation.materialId || !allocation.locationId) {
      return '请完整选择每项分配的物料和库位'
    }
    const key = `${allocation.materialId}/${allocation.locationId}`
    if (uniqueKeys.has(key)) return '同一物料在同一库位只能分配一次'
    uniqueKeys.add(key)
    const quantity = Number(allocation.plannedQuantity)
    if (!Number.isFinite(quantity) || quantity <= 0) return '计划分配数量必须大于 0'
    if (!Number.isInteger(quantity)) return '计划分配数量必须为整数'
    totals.set(String(allocation.materialId), (totals.get(String(allocation.materialId)) || 0) + quantity)
  }
  for (const line of lines) {
    const total = totals.get(String(line.materialId)) || 0
    if (total !== Number(line.requestedQuantity)) {
      return `物料「${line.materialName}」的计划分配合计必须等于申请数量`
    }
  }
  return ''
}

export function shouldRefreshClaimPreview(error) {
  const status = error?.response?.status
  const code = error?.response?.data?.code
  return status === 409 || code === 409 || code === 40900
}
