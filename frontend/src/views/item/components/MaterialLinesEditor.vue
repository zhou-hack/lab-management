<template>
  <div class="material_lines">
    <div
      v-for="(line, index) in modelValue"
      :key="index"
      class="material_lines__row"
    >
      <div class="material_lines__material">
        <ItemMaterialSelect
          :model-value="line.materialId"
          :disabled="disabled"
          :clearable="false"
          @update:model-value="updateLine(index, 'materialId', $event)"
          @change="(id, option) => selectMaterial(index, option)"
        />
        <span
          v-if="line.specification || materialTypeLabel(line)"
          class="t-hint material_lines__meta"
        >
          {{ [materialTypeLabel(line), line.specification].filter(Boolean).join(' · ') }}
        </span>
      </div>
      <div class="material_lines__quantity">
        <el-input-number
          :model-value="line[quantityField]"
          :min="1"
          :precision="0"
          :step="1"
          :controls="false"
          :disabled="disabled"
          :placeholder="quantityLabel"
          class="material_lines__number"
          @update:model-value="updateLine(index, quantityField, $event)"
        />
        <span class="t-hint material_lines__unit">{{ line.baseUnit || '数量' }}</span>
      </div>
      <el-button
        link
        type="danger"
        class="material_lines__remove"
        :disabled="disabled || (!allowEmpty && modelValue.length <= 1)"
        aria-label="删除物料行"
        @click="removeLine(index)"
      >
        删除
      </el-button>
    </div>

    <div class="material_lines__footer">
      <el-button
        :disabled="disabled"
        @click="addLine"
      >
        添加物料
      </el-button>
      <span class="t-hint">同一物料仅可添加一行；所有物料数量必须为整数</span>
    </div>
  </div>
</template>

<script setup>
import ItemMaterialSelect from '@/components/item/ItemMaterialSelect.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  quantityField: { type: String, default: 'requestedQuantity' },
  quantityLabel: { type: String, default: '申请数量' },
  disabled: { type: Boolean, default: false },
  allowEmpty: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])

function isFixedAsset(line) {
  return line.materialType === 'FIXED_ASSET'
}

function materialTypeLabel(line) {
  if (line.materialType === 'FIXED_ASSET') return '固定资产'
  if (line.materialType === 'CONSUMABLE') return '耗材'
  return ''
}

function replaceLine(index, changes) {
  const lines = props.modelValue.map((line, lineIndex) => lineIndex === index
    ? { ...line, ...changes }
    : line)
  emit('update:modelValue', lines)
  emit('change', lines)
}

function updateLine(index, field, value) {
  replaceLine(index, { [field]: value })
}

function selectMaterial(index, option) {
  if (!option) {
    replaceLine(index, {
      materialName: '',
      materialType: '',
      quantityMode: '',
      baseUnit: '',
      specification: ''
    })
    return
  }
  const previous = props.modelValue[index]
  const wasFixed = isFixedAsset(previous)
  const isFixed = option.materialType === 'FIXED_ASSET'
  let quantity = previous[props.quantityField]
  if (isFixed && (!wasFixed || !Number.isInteger(Number(quantity)))) {
    quantity = Math.max(1, Math.round(Number(quantity) || 1))
  }
  replaceLine(index, {
    materialName: option.materialName || '',
    materialType: option.materialType || '',
    quantityMode: option.quantityMode || '',
    baseUnit: option.baseUnit || '',
    specification: option.specification || '',
    [props.quantityField]: quantity
  })
}

function addLine() {
  const lines = [...props.modelValue, {
    materialId: '',
    materialName: '',
    materialType: '',
    quantityMode: '',
    baseUnit: '',
    specification: '',
    [props.quantityField]: null
  }]
  emit('update:modelValue', lines)
  emit('change', lines)
}

function removeLine(index) {
  const lines = props.modelValue.filter((_, lineIndex) => lineIndex !== index)
  emit('update:modelValue', lines)
  emit('change', lines)
}
</script>

<style scoped>
.material_lines {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.material_lines__row {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 180px 48px;
  gap: 12px;
  align-items: start;
  padding: 12px;
  background: var(--surface-sunken);
  border: .5px solid var(--hair);
  border-radius: var(--r-md);
}

.material_lines__material {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.material_lines__meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material_lines__quantity {
  display: flex;
  align-items: center;
  gap: 8px;
}

.material_lines__number {
  width: 130px;
}

.material_lines__unit {
  min-width: 32px;
}

.material_lines__remove {
  margin-top: 5px;
}

.material_lines__footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

@media (max-width: 720px) {
  .material_lines__row {
    grid-template-columns: 1fr;
  }

  .material_lines__quantity {
    width: 100%;
  }

  .material_lines__number {
    flex: 1;
    width: auto;
  }

  .material_lines__remove {
    justify-self: end;
    margin-top: 0;
  }
}
</style>
