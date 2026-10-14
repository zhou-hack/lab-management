<template>
  <el-dialog
    v-model="visible"
    title="借用单详情"
    width="640px"
    destroy-on-close
  >
    <el-descriptions
      v-if="booking"
      :column="2"
      border
    >
      <el-descriptions-item label="单据号">
        {{ booking.documentNo }}
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <span
          class="pill"
          :class="enumMeta(booking.status, BOOKING_STATUS).pill"
        >
          {{ enumMeta(booking.status, BOOKING_STATUS).label }}
        </span>
      </el-descriptions-item>
      <el-descriptions-item label="设备名称">
        {{ booking.equipmentName }}
      </el-descriptions-item>
      <el-descriptions-item label="资产编号">
        {{ booking.assetCode }}
      </el-descriptions-item>
      <el-descriptions-item label="保管实验室">
        {{ booking.labName || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="价值分级">
        {{ enumMeta(booking.valueLevelSnapshot, VALUE_LEVEL).label }}
      </el-descriptions-item>
      <el-descriptions-item label="申请人">
        {{ booking.applicantName }}
      </el-descriptions-item>
      <el-descriptions-item label="指定审批老师">
        {{ booking.approvalTeacherName || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="开始时间">
        {{ formatSh(booking.startAt) }}
      </el-descriptions-item>
      <el-descriptions-item label="结束时间">
        {{ formatSh(booking.endAt) }}
      </el-descriptions-item>
      <el-descriptions-item
        label="借用用途"
        :span="2"
      >
        {{ booking.purpose }}
      </el-descriptions-item>
      <el-descriptions-item label="申请时间">
        {{ formatSh(booking.createTime) }}
      </el-descriptions-item>
      <el-descriptions-item
        v-if="booking.status === 'CANCELLED'"
        label="撤销时间"
      >
        {{ formatSh(booking.cancelledAt) }}
      </el-descriptions-item>
      <el-descriptions-item
        v-if="booking.cancelReason"
        label="撤销原因"
        :span="2"
      >
        {{ booking.cancelReason }}
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="visible = false">
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { formatSh } from '@/utils/datetime'
import { BOOKING_STATUS, VALUE_LEVEL, enumMeta } from '@/utils/enums'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  booking: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
</script>
