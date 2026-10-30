<template>
  <div
    v-loading="loading"
    class="pwc"
  >
    <p
      v-if="!spaceId"
      class="t-hint pwc__empty"
    >
      周日历展示单个场地的一周占用，请先在上方筛选或日矩阵中选择场地
    </p>
    <div
      v-else
      class="pwc__scroll"
    >
      <table class="pwc__table">
        <thead>
          <tr>
            <th class="pwc__corner">
              节次 \ 星期
            </th>
            <th
              v-for="d in weekDays"
              :key="d.date"
              class="pwc__day"
              :class="{ 'pwc__day--today': d.date === today }"
              @click="emit('switch-day', d.date)"
            >
              <div>{{ d.label }}</div>
              <div class="pwc__day_date">
                {{ d.date.slice(5) }}
              </div>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="p in periods"
            :key="p.periodNo"
          >
            <th class="pwc__period">
              <b>{{ p.periodName }}</b>
              <span>{{ p.startClock }}-{{ p.endClock }}</span>
            </th>
            <td
              v-for="d in weekDays"
              :key="d.date"
              class="pwc__cell"
              :class="`pwc__cell--${cellOf(d.date, p).state}`"
              :title="cellOf(d.date, p).tip"
            >
              {{ cellOf(d.date, p).text }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { getAvailability } from '@/api/booking'
import { shanghaiClockOf, todayShanghai, addDaysShanghai, nowShanghaiClock } from '@/utils/datetime'

/**
 * 场地周日历（节次×星期一~日）：纵览单场地一周占用。
 * 数据源 GET /booking/labs/availability（booking:lab:create，教师端可用；
 * 学生无该权限，父组件不下发本视图）。占用段与节次按上海时刻半开区间重叠判定。
 */
const props = defineProps({
  periods: { type: Array, default: () => [] },
  spaceId: { type: [String, Number], default: '' },
  anchorDate: { type: String, default: '' },
  /** 分钟级跳变时钟（父组件定时器驱动）：触发已过去状态随时刻流转 */
  nowTick: { type: Number, default: 0 }
})

const emit = defineEmits(['switch-day'])

const today = todayShanghai()
const toMin = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5))
const WEEK_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

/** 锚定日期所在周的周一（上海日历日） */
function mondayOf(dateStr) {
  const [y, m, d] = String(dateStr || today).split('-').map(Number)
  const dow = new Date(Date.UTC(y, m - 1, d)).getUTCDay() // 0=周日
  return addDaysShanghai(dateStr || today, dow === 0 ? -6 : 1 - dow)
}

const weekDays = computed(() => {
  const monday = mondayOf(props.anchorDate)
  return WEEK_LABELS.map((label, i) => ({ label, date: addDaysShanghai(monday, i) }))
})

const loading = ref(false)
/** 一周占用：date → slots[]（上海分钟区间 + 文本） */
const weekSlots = ref({})
let weekSeq = 0

async function loadWeek() {
  if (!props.spaceId) {
    weekSlots.value = {}
    return
  }
  const seq = ++weekSeq
  loading.value = true
  try {
    const days = weekDays.value.map((d) => d.date)
    const results = await Promise.all(days.map((date) =>
      getAvailability(props.spaceId, date).catch(() => null)))
    if (seq !== weekSeq) return
    const map = {}
    results.forEach((res, i) => {
      map[days[i]] = (res?.occupied || []).map((seg) => ({
        start: toMin(shanghaiClockOf(seg.startAt) || '00:00'),
        end: toMin(shanghaiClockOf(seg.endAt) || '00:00'),
        text: seg.courseName || seg.teacherName || seg.applicantName || '已占用'
      }))
    })
    weekSlots.value = map
  } finally {
    if (seq === weekSeq) loading.value = false
  }
}

watch(() => [props.spaceId, weekDays.value[0]?.date], loadWeek, { immediate: true })

function cellOf(date, p) {
  void props.nowTick // 建立分钟级重渲依赖（已过去状态随时刻流转）
  const sMin = toMin(p.startClock)
  const eMin = toMin(p.endClock)
  const nowMin = toMin(nowShanghaiClock())
  if (date < today || (date === today && eMin <= nowMin)) {
    return { state: 'past', text: '', tip: '已过去' }
  }
  const hit = (weekSlots.value[date] || []).find((seg) => seg.end > sMin && seg.start < eMin)
  if (hit) {
    return { state: 'occupied', text: hit.text, tip: `已占用：${hit.text}` }
  }
  return { state: 'free', text: '', tip: '空闲' }
}
</script>

<style scoped>
.pwc__empty {
  padding: 24px 0;
  text-align: center;
}

.pwc__scroll {
  overflow-x: auto;
}

.pwc__table {
  border-collapse: separate;
  border-spacing: 3px;
  width: 100%;
  min-width: 720px;
  table-layout: fixed;
}

.pwc__corner,
.pwc__day {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  text-align: center;
}

.pwc__day {
  cursor: pointer;
}

.pwc__day:hover {
  border-color: #8db8f7;
}

.pwc__day--today {
  background: #ecf3ff;
  border-color: #2f6bff;
}

.pwc__day_date {
  font-weight: 400;
  color: #9ca3af;
  font-size: 11px;
}

.pwc__period {
  background: #fafbfc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 4px 8px;
  text-align: left;
  min-width: 96px;
}

.pwc__period b {
  display: block;
  font-size: 12.5px;
  color: #374151;
}

.pwc__period span {
  font-size: 11px;
  color: #9ca3af;
  font-weight: 400;
}

.pwc__cell {
  height: 38px;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
  font-size: 11px;
  text-align: center;
  color: #6b7280;
  padding: 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pwc__cell--free {
  background: #f0faf2;
  border-color: #b7e4c7;
}

.pwc__cell--occupied {
  background: #fff1f0;
  border-color: #ffa39e;
  color: #cf1322;
}

.pwc__cell--past {
  background: #f5f5f5;
  color: #c0c4cc;
}
</style>
