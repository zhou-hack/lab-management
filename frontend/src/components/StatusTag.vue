<template>
  <el-tag :type="cfg.type">
    {{ cfg.label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { statusOf } from '@/utils/statusMap'

/**
 * 状态标签（配合 utils/statusMap 使用）
 * 用法：<StatusTag :value="row.status" :map="documentStatusMap" />
 */
const props = defineProps({
  value: { type: [String, Number], default: '' },
  map: { type: Object, required: true }
})

const cfg = computed(() => statusOf(props.map, props.value))
</script>
