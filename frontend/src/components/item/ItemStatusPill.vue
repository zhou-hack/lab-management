<template>
  <span
    class="pill item_status_pill"
    :class="meta.pill"
  >
    {{ label || meta.label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: [String, Number], default: '' },
  label: { type: String, default: '' }
})

const STATUS_MAP = {
  PENDING: { label: '待审核', pill: 'pill--warn' },
  APPROVED: { label: '已批准', pill: 'pill--ok' },
  PARTIALLY_ISSUED: { label: '部分实发', pill: 'pill--brand' },
  EXPIRED: { label: '已过期', pill: 'pill--muted' },
  CANCELLED: { label: '已取消', pill: 'pill--muted' },
  CLOSED: { label: '已关闭', pill: 'pill--muted' },
  REJECTED: { label: '已驳回', pill: 'pill--danger' },
  ACTIVE: { label: '生效中', pill: 'pill--ok' },
  IDLE: { label: '在库', pill: 'pill--ok' },
  ON_LOAN: { label: '借出中', pill: 'pill--brand' },
  MAINTENANCE: { label: '维修中', pill: 'pill--warn' },
  IN_TRANSIT: { label: '在途', pill: 'pill--brand' },
  LOST: { label: '已遗失', pill: 'pill--danger' },
  SCRAPPED: { label: '已报废', pill: 'pill--muted' },
  PENDING_RECEIPT: { label: '待接收', pill: 'pill--warn' },
  PARTIALLY_RECEIVED: { label: '部分接收', pill: 'pill--brand' },
  PENDING_RETURN: { label: '待退回', pill: 'pill--warn' },
  PENDING_REVIEW: { label: '待复核', pill: 'pill--warn' },
  UNPROCESSED: { label: '未处理', pill: 'pill--muted' },
  PARTIAL: { label: '部分处理', pill: 'pill--brand' },
  PROCESSED: { label: '已处理', pill: 'pill--ok' },
  ON_TIME: { label: '按时归还', pill: 'pill--ok' },
  OVERDUE: { label: '已逾期', pill: 'pill--danger' },
  POSTED: { label: '已过账', pill: 'pill--ok' }
}

const meta = computed(() => STATUS_MAP[props.status] || {
  label: props.status || '未知',
  pill: 'pill--muted'
})
</script>
