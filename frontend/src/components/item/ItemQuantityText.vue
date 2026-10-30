<template>
  <span class="item_quantity_text">
    <span class="item_quantity_text__value">{{ formattedValue }}</span>
    <span
      v-if="unit && formattedValue !== placeholder"
      class="item_quantity_text__unit"
    >{{ unit }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: [String, Number], default: null },
  unit: { type: String, default: '' },
  placeholder: { type: String, default: '—' }
})

const formattedValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return props.placeholder
  const value = Number(props.value)
  if (!Number.isFinite(value)) return props.placeholder
  const text = value.toFixed(3).replace(/\.?0+$/, '')
  return text === '-0' ? '0' : text
})
</script>

<style scoped>
.item_quantity_text {
  display: inline-flex;
  align-items: baseline;
  gap: 3px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.item_quantity_text__value {
  font-family: var(--font-num);
  color: var(--g1);
}

.item_quantity_text__unit {
  font-size: 12px;
  color: var(--g3);
}
</style>
