<template>
  <el-drawer
    :model-value="modelValue"
    title="预约详情"
    :size="drawerSize"
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
            v-if="booking.status === 'APPROVED' && booking.runtimePhase"
            class="pill"
            :class="RUNTIME_PHASE_MAP[booking.runtimePhase]?.pill"
          >
            {{ RUNTIME_PHASE_MAP[booking.runtimePhase]?.label || booking.runtimePhase }}
          </span>
          <span
            v-else
            class="pill"
            :class="BOOKING_STATUS_MAP[booking.status]?.pill"
          >
            {{ booking.statusName || BOOKING_STATUS_MAP[booking.status]?.label || booking.status }}
          </span>
        </div>
        <p class="t-sub">
          {{ formatRange(booking.startAt, booking.endAt) }}
        </p>
      </div>

      <!-- 驳回原因专属展示区：被驳回时高亮完整原因，不只依赖通知 -->
      <el-alert
        v-if="booking.status === 'REJECTED'"
        type="error"
        :closable="false"
        show-icon
        class="booking_detail__reject"
      >
        <template #title>
          <span class="booking_detail__reject_label">驳回原因</span>
        </template>
        <div class="booking_detail__reject_text">
          {{ booking.rejectReason || '未填写驳回原因' }}
        </div>
      </el-alert>

      <div class="card">
        <h4 class="booking_detail__section">
          申请信息
        </h4>
        <el-descriptions
          :column="2"
          border
        >
          <el-descriptions-item
            label="场地名称"
            :span="2"
          >
            {{ booking.labName || booking.spaceId }}
          </el-descriptions-item>
          <el-descriptions-item
            label="使用时段"
            :span="2"
          >
            {{ formatRange(booking.startAt, booking.endAt) }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="booking.startPeriodName || booking.bookingDate"
            label="节次"
            :span="2"
          >
            {{ booking.bookingDate || '' }}
            {{ booking.startPeriodName ? `${booking.startPeriodName} ~ ${booking.endPeriodName || booking.startPeriodName}` : '' }}
          </el-descriptions-item>
          <el-descriptions-item label="课堂信息">
            {{ [booking.courseName, booking.className].filter(Boolean).join(' · ') || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="上课老师">
            {{ booking.teacherName || '—' }}
          </el-descriptions-item>
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
            {{ booking.purpose || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatInstant(booking.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatInstant(booking.updateTime) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 处理信息：非驳回状态显示驳回原因「—」；取消追加取消留痕 -->
      <div
        v-if="booking.status !== 'REJECTED'"
        class="card"
      >
        <h4 class="booking_detail__section">
          处理信息
        </h4>
        <el-descriptions
          :column="1"
          border
        >
          <el-descriptions-item label="驳回原因">
            <span class="t-hint">—</span>
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

      <BookingTimeline :events="booking.timeline || []" />

      <p class="t-hint booking_detail__tz">
        时间点为 UTC 存储，此处按上海时区展示；批准结果即时占用时段，取消后立即释放
      </p>
    </div>
  </el-drawer>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { BOOKING_STATUS_MAP, RUNTIME_PHASE_MAP } from './booking.constants'
import BookingTimeline from './BookingTimeline.vue'
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

// 响应式宽度：≥768px 固定 520px（同管理端），窄屏全宽避免超出视口
const narrowMql = window.matchMedia('(max-width: 767px)')
const drawerSize = ref(narrowMql.matches ? '100%' : '520px')
function onNarrowChange(e) {
  drawerSize.value = e.matches ? '100%' : '520px'
}
onMounted(() => narrowMql.addEventListener('change', onNarrowChange))
onBeforeUnmount(() => narrowMql.removeEventListener('change', onNarrowChange))

watch(
  () => props.modelValue,
  async (v) => {
    if (v && props.bookingId) {
      loading.value = true
      try {
        booking.value = await getBookingDetail(props.bookingId)
      } catch {
        // 403（非本人/无审批权限）、404 等错误提示由 Axios 拦截器统一处理
        booking.value = null
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
  flex-wrap: wrap;
  gap: 10px;
}

.booking_detail__space {
  margin: 0;
  font-size: 17px;
  /* 窄屏下超长场地名换行，不挤压状态胶囊 */
  min-width: 0;
  overflow-wrap: anywhere;
}

.booking_detail__section {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: var(--fw-semibold, 600);
  color: var(--g1);
}

/* 驳回原因专属区：标签加粗，正文完整展示可换行 */
.booking_detail__reject {
  align-items: flex-start;
}

.booking_detail__reject_label {
  font-weight: var(--fw-semibold, 600);
}

.booking_detail__reject_text {
  margin-top: 4px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.booking_detail__tz {
  margin: 0;
}

@media (max-width: 767px) {
  .booking_detail__head {
    padding: 14px 16px;
  }
}
</style>
