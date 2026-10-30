export function addMaterialLine(lines, material, quantityValue, {
  mode = 'claim',
  quantityField
} = {}) {
  const field = quantityField || (mode === 'plan' ? 'defaultQuantity' : 'requestedQuantity')
  const quantityLabel = mode === 'plan' ? '默认数量' : '申请数量'
  const materialName = material.materialName || material.materialId
  const quantity = Number(quantityValue)
  if (!Number.isFinite(quantity) || quantity <= 0) {
    return { lines, error: `请填写物料「${materialName}」的有效${quantityLabel}` }
  }
  if (!Number.isInteger(quantity)) {
    return { lines, error: `物料「${materialName}」的${quantityLabel}必须为整数` }
  }

  const cart = lines.filter((line) => line.materialId)
  const index = cart.findIndex((line) => String(line.materialId) === String(material.materialId))
  const currentQuantity = index >= 0 ? Number(cart[index][field]) : 0
  if (!Number.isInteger(currentQuantity)) {
    return { lines, error: `物料「${materialName}」的${quantityLabel}必须为整数` }
  }
  const totalQuantity = currentQuantity + quantity
  const availableQuantity = Number(material.availableQuantity)
  if (mode === 'claim' && totalQuantity > availableQuantity) {
    return { lines, error: '库存不足' }
  }

  const addedLine = {
    materialId: String(material.materialId),
    materialName: material.materialName || '',
    materialType: material.materialType || '',
    quantityMode: material.quantityMode || '',
    baseUnit: material.baseUnit || '',
    specification: material.specification || '',
    ownedQuantity: material.ownedQuantity,
    availableQuantity: material.availableQuantity,
    [field]: totalQuantity
  }
  if (index < 0) return { lines: [...cart, addedLine], error: '' }
  return {
    lines: cart.map((line, lineIndex) => lineIndex === index
      ? { ...line, ...addedLine }
      : line),
    error: ''
  }
}

export function addPlanLine(lines, material, defaultQuantity) {
  return addMaterialLine(lines, material, defaultQuantity, {
    mode: 'plan',
    quantityField: 'defaultQuantity'
  })
}
