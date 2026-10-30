export function normalizePlanLines(sourceLines) {
  return (sourceLines || []).map((line) => ({
    materialId: String(line.materialId),
    materialName: line.materialName || '',
    materialType: line.materialType || '',
    quantityMode: line.quantityMode || '',
    baseUnit: line.baseUnit || '',
    specification: line.specification || '',
    defaultQuantity: Number(line.defaultQuantity)
  }))
}

export function getPlanLinesError(lines) {
  if (!lines.length) return '请至少添加一项方案物料'
  const ids = new Set()
  for (const line of lines) {
    if (!line.materialId) return '请选择每一行的物料'
    if (ids.has(String(line.materialId))) {
      return `物料「${line.materialName || line.materialId}」重复，请合并为一行`
    }
    ids.add(String(line.materialId))
    const quantity = Number(line.defaultQuantity)
    if (!Number.isFinite(quantity) || quantity <= 0) {
      return `请填写物料「${line.materialName || line.materialId}」的有效默认数量`
    }
    if (!Number.isInteger(quantity)) {
      return `物料「${line.materialName || line.materialId}」的默认数量必须为整数`
    }
  }
  return ''
}

export function buildPlanPayload(form, lines) {
  return {
    planName: form.planName.trim(),
    enabled: Number(form.enabled),
    remark: form.remark.trim() || undefined,
    lines: lines.map((line) => ({
      materialId: String(line.materialId),
      defaultQuantity: Number(line.defaultQuantity)
    }))
  }
}
