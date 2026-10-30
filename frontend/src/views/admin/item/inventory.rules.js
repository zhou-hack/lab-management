export const CURRENT_STOCK_AVAILABILITY_NOTICE = '库内实际可用量是当前时刻实际可用数量；当前实际可用 ≠ 目标时段可约，预约仍需按目标时段校验。'

export function getTransferReceiptError(lines) {
  const selected = lines.filter((line) => Number(line.quantity) > 0)
  if (!selected.length) return '请至少填写一条实收行'
  if (selected.some((line) => !Number.isInteger(Number(line.quantity)))) {
    return '实收数量必须为整数'
  }
  if (selected.some((line) => Number(line.quantity) > Number(line.remainingQuantity))) {
    return '实收数量不能超过剩余在途数量'
  }
  return ''
}

export function isReturnWithinRemaining(row) {
  const quantities = ['returnQuantity', 'consumedQuantity', 'lostQuantity', 'damagedQuantity']
    .map((key) => Number(row[key] || 0))
  if (quantities.some((quantity) => !Number.isInteger(quantity))) return false
  const total = quantities.reduce((sum, quantity) => sum + quantity, 0)
  return total <= Number(row.remainingQuantity)
}

export function materialPayload(form) {
  return {
    materialType: form.materialType,
    trackMode: form.trackMode,
    skuCode: form.materialType === 'CONSUMABLE' ? null : form.skuCode?.trim() || null,
    materialName: form.materialName.trim(),
    specification: form.specification?.trim() || null,
    baseUnit: form.baseUnit.trim(),
    quantityMode: form.quantityMode,
    enabled: form.enabled
  }
}

export function getConsumableIssueDefaults(line) {
  const positions = (line.positions || [])
    .filter((position) => Number(position.availableQuantity) > 0)
  if (!positions.length) return { positionText: null, quantity: 0 }
  const position = positions.reduce((selected, candidate) => (
    Number(candidate.availableQuantity) > Number(selected.availableQuantity) ? candidate : selected
  ))
  return {
    positionText: position.positionText || '',
    quantity: Math.min(Number(line.remainingQuantity || 0), Number(position.availableQuantity))
  }
}
