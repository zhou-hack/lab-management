<template>
  <div class="card">
    <h4 class="booking_timeline__section">
      流程记录
    </h4>
    <el-timeline
      v-if="events.length"
      class="booking_timeline"
    >
      <el-timeline-item
        v-for="(item, idx) in events"
        :key="idx"
        :type="dotType(item.eventType)"
        :timestamp="formatInstant(item.occurredAt)"
        placement="top"
      >
        <div class="booking_timeline__row">
          <span class="booking_timeline__name">{{ item.eventName }}</span>
          <span class="booking_timeline__actor">{{ item.actorType === 'SYSTEM' ? '系统' : item.actorName }}</span>
        </div>
        <div
          v-if="item.reason"
          class="booking_timeline__reason"
        >
          {{ item.reason }}
        </div>
      </el-timeline-item>
    </el-timeline>
    <p
      v-else
      class="t-hint"
    >
      暂无流程记录
    </p>
  </div>
</template>

<script setup>
import { formatInstant } from '@/utils/datetime'

const props = defineProps({
  /** document_event 时间线：BookingTimelineEventVO[] */
  events: { type: Array, default: () => [] }
})

/** 事件类型 → 时间线节点配色 */
function dotType(type) {
  switch (type) {
    case 'APPROVE':
    case 'CHECKIN':
      return 'success'
    case 'REJECT':
    case 'TIMEOUT_CANCEL':
    case 'CANCEL':
      return 'danger'
    case 'CHECKOUT':
      return 'info'
    default:
      return 'primary'
  }
}
</script>

<style scoped>
.booking_timeline__section {
  margin: 0 0 14px;
  font-size: 14px;
  font-weight: var(--fw-semibold, 600);
  color: var(--g1);
}

.booking_timeline__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.booking_timeline__name {
  font-weight: var(--fw-medium, 500);
}

.booking_timeline__actor {
  font-size: 12px;
  color: var(--g3, #6b7280);
}

.booking_timeline__reason {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--g2, #4b5563);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
