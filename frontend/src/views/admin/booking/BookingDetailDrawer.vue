<template>
  <el-drawer
    :model-value="modelValue"
    title="预约详情"
    size="520px"
    @update:model-value="(v) => !v && emit('close')"
  >
    <div
      v-if="booking"
      v-loading="loading"
      class="booking_detail"
    >
      <div class="card booking_detail__head">
        <div class="booking_detail__title_row">
          <h3 class="t-title booking_detail__space">
            {{ booking.labName || booking.spaceId }}
          </h3>
          <span
            class="pill"
            :class="BOOKING_STATUS_MAP[booking.status]?.pill"
          >
            {{ BOOKING_STATUS_MAP[booking.status]?.label || booking.status }}
          </span>
        </div>
        <p class="t-sub">
          {{ formatRange(booking.startAt, booking.endAt) }}（上海本地时间）
        </p>
      </div>

      <div class="card">
        <h4 class="booking_detail__section">
          申请信息
        </h4>
        <el-descriptions
          :column="2"
          border
        >
          <el-descriptions-item label="申请人">
            {{ booking.applicantName || booking.applicantId }}
          </el-descriptions-item>
          <el-descriptions-item label="参与人数">
            {{ booking.participantCount }} 人
          </el-descriptions-item>
          <el-descriptions-item
            label="使用事由"
            :span="2"
          >
            {{ booking.purpose }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatInstant(booking.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatInstant(booking.updateTime) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 留痕信息：仅非 PENDING 时展示 -->
      <div
        v-if="booking.status === 'REJECTED' || booking.status === 'CANCELLED'"
        class="card"
      >
        <h4 class="booking_detail__section">
          {{ booking.status === 'REJECTED' ? '驳回信息' : '取消信息' }}
        </h4>
        <el-descriptions
          :column="1"
          border
        >
          <el-descriptions-item
            v-if="booking.status === 'REJECTED'"
            label="驳回原因"
          >
            {{ booking.rejectReason || '—' }}
          </el-descriptions-item>
          <template v-if="booking.status === 'CANCELLED'">
            <el-descriptions-item label="取消方式">
              {{ booking.cancelTypeName || booking.cancelType || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="取消原因">
              {{ booking.cancelReason || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="取消时间">
              {{ formatInstant(booking.cancelledAt) }}
            </el-descriptions-item>
          </template>
        </el-descriptions>
      </div>

      <p class="t-hint booking_detail__tz">
        时间点为 UTC 存储，此处按上海时区展示；批准结果即时占用时段，取消后立即释放
      </p>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { BOOKING_STATUS_MAP } from '@/views/booking/booking.constants'
import { getBookingDetail } from '@/api/booking'
import { formatInstant, formatRange } from '@/utils/datetime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 预约 ID（打开抽屉时加载详情） */
  bookingId: { type: String, default: '' }
})

const emit = defineEmits(['close'])

const loading = ref(false)
const booking = ref(null)

watch(
  () => props.modelValue,
  async (v) => {
    if (v && props.bookingId) {
      loading.value = true
      try {
        booking.value = await getBookingDetail(props.bookingId)
      } finally {
        loading.value = false
      }
    } else {
      booking.value = null
    }
  }
)
</script>

<style scoped>
.booking_detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.booking_detail__head {
  padding: 16px 20px;
}

.booking_detail__title_row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.booking_detail__space {
  margin: 0;
  font-size: 17px;
}

.booking_detail__section {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: var(--fw-semibold, 600);
  color: var(--g1);
}

.booking_detail__tz {
  margin: 0;
}
</style>
