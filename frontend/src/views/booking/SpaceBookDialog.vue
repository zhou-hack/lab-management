<template>
  <el-dialog
    :model-value="modelValue"
    title="发起场地预约"
    width="600px"
    :close-on-click-modal="false"
    @update:model-value="(v) => !v && emit('close')"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent
    >
      <!-- 场地/日期/节次由时间轴带入锁定，不重复选择 -->
      <div class="book_form__preset">
        <p class="book_form__preset_line">
          <span class="t-hint">预约场地：</span><b>{{ spaceName || '—' }}</b>
          <span
            v-if="capacity"
            class="t-hint"
          >（容量 {{ capacity }} 人）</span>
        </p>
        <p class="book_form__preset_line">
          <span class="t-hint">预约日期：</span><b>{{ presetDate }}</b>（上海本地）
        </p>
        <div class="book_form__preset_line">
          <span class="t-hint">预约节次：</span>
          <span class="book_form__segments">
            <el-tag
              v-for="(seg, i) in segmentLabels"
              :key="i"
              size="small"
              effect="plain"
            >
              {{ seg }}
            </el-tag>
          </span>
        </div>
        <p
          v-if="segmentLabels.length > 1"
          class="t-hint book_form__split_hint"
        >
          所选节次不连续，将按连续段拆分为 {{ segmentLabels.length }} 个预约单分别提交与审批
        </p>
      </div>

      <!-- 该场地当日已有占用（带入场地 + 日期联动展示） -->
      <div
        v-loading="availabilityLoading"
        class="book_form__avail"
      >
        <p
          v-if="openWindow"
          class="t-sub"
        >
          开放时段 {{ openWindow }}（上海本地时间）
        </p>
        <div
          v-if="occupiedSegments.length"
          class="book_form__segments"
        >
          <span class="t-hint">该日已占用：</span>
          <el-tag
            v-for="(seg, i) in occupiedSegments"
            :key="i"
            size="small"
            type="info"
            effect="plain"
          >
            {{ seg }}
          </el-tag>
        </div>
        <p
          v-else-if="!availabilityLoading"
          class="t-hint"
        >
          该日暂无已占用时段
        </p>
      </div>

      <div class="book_form__row">
        <el-form-item
          label="课程"
          prop="courseName"
          class="book_form__grow"
        >
          <el-input
            v-model="form.courseName"
            maxlength="128"
            placeholder="如：Python 程序设计实训课"
          />
        </el-form-item>
        <el-form-item
          label="班级"
          prop="className"
          class="book_form__grow"
        >
          <el-input
            v-model="form.className"
            maxlength="128"
            placeholder="如：软件 2301、2302 合班"
          />
        </el-form-item>
      </div>

      <div class="book_form__row">
        <el-form-item
          label="老师"
          prop="teacherId"
          class="book_form__grow"
        >
          <el-select
            v-model="form.teacherId"
            placeholder="上课老师"
            filterable
            class="book_form__full"
          >
            <el-option
              v-for="t in teacherOptions"
              :key="t.id"
              :label="t.realName"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="参与人数"
          prop="participantCount"
        >
          <el-input-number
            v-model="form.participantCount"
            :min="1"
            :max="capacity || 9999"
            :controls="false"
            placeholder="人数"
            class="book_form__count"
          />
        </el-form-item>
      </div>

      <el-form-item
        label="使用事由"
        prop="purpose"
      >
        <el-input
          v-model="form.purpose"
          type="textarea"
          :rows="2"
          maxlength="512"
          show-word-limit
          placeholder="如：周一 3-4 节实训课"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('close')">
        取消
      </el-button>
      <el-button
        type="primary"
        @click="submit"
      >
        提交预约
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listSpaceOptions } from '@/api/space'
import { getAvailability, listPeriods, listTeachers } from '@/api/booking'
import { shanghaiLocalToIso } from '@/utils/datetime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 从时间轴带入的预选场地/日期（锁定展示，不重复选择） */
  presetSpaceId: { type: String, default: '' },
  presetDate: { type: String, default: '' },
  /** 连续段数组：[[起始节次号, 结束节次号], ...]（不连续选择已由时间轴拆段） */
  presetSegments: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'submit'])

const formRef = ref()
const availabilityLoading = ref(false)
const spaceOptions = ref([])
const periodOptions = ref([])
const teacherOptions = ref([])
const availability = ref(null)

const form = reactive({
  courseName: '',
  className: '',
  teacherId: '',
  participantCount: null,
  purpose: ''
})

const selectedSpace = computed(() => spaceOptions.value.find((s) => s.id === props.presetSpaceId))
const spaceName = computed(() => selectedSpace.value?.labName || '')
const capacity = computed(() => selectedSpace.value?.capacity || null)
const openWindow = computed(() =>
  availability.value ? `${availability.value.openStart} - ${availability.value.openEnd}` : ''
)
const occupiedSegments = computed(() =>
  (availability.value?.occupied || []).map((seg) => {
    const s = formatClock(seg.startAt)
    const e = formatClock(seg.endAt)
    return `${s} ~ ${e}`
  })
)

/** 节次号 → 节次记录（class_period.period_no 匹配） */
function periodOf(periodNo) {
  return periodOptions.value.find((item) => Number(item.periodNo) === Number(periodNo))
}

/** 分段只读展示：第1节 08:00-08:45 / 第3~5节 10:00-11:40 */
const segmentLabels = computed(() =>
  (props.presetSegments || []).map(([startNo, endNo]) => {
    const sp = periodOf(startNo)
    const ep = periodOf(endNo)
    if (!sp || !ep) return `第${startNo}节 ~ 第${endNo}节`
    const name = startNo === endNo ? sp.periodName : `${sp.periodName} ~ ${ep.periodName}`
    return `${name}（${sp.startClock} - ${ep.endClock}）`
  })
)

const rules = {
  purpose: [
    { required: true, message: '请填写使用事由', trigger: 'blur' },
    { max: 512, message: '事由不能超过 512 字', trigger: 'blur' }
  ],
  participantCount: [{ required: true, message: '请填写参与人数', trigger: 'blur' }]
}

function formatClock(iso) {
  return iso ? String(iso).slice(11, 16) : ''
}

async function initForm() {
  try {
    const [spaces, periods, teachers] = await Promise.all([
      listSpaceOptions({ availableOnly: true }),
      listPeriods(),
      listTeachers()
    ])
    spaceOptions.value = spaces || []
    periodOptions.value = periods || []
    teacherOptions.value = teachers || []
  } catch {
    spaceOptions.value = []
    periodOptions.value = []
    teacherOptions.value = []
  }
  form.courseName = ''
  form.className = ''
  form.teacherId = ''
  form.participantCount = null
  form.purpose = ''
  availability.value = null
  await loadAvailability()
}

async function loadAvailability() {
  if (!props.presetSpaceId || !props.presetDate) {
    availability.value = null
    return
  }
  availabilityLoading.value = true
  try {
    availability.value = await getAvailability(props.presetSpaceId, props.presetDate)
  } catch {
    availability.value = null
  } finally {
    availabilityLoading.value = false
  }
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!props.presetSegments.length) {
    ElMessage.warning('请先在时间轴点按选择节次')
    return
  }
  if (capacity.value && form.participantCount > capacity.value) {
    ElMessage.warning(`参与人数不能超过场地容量（${capacity.value} 人）`)
    return
  }
  // 逐段构造预约负载：startAt/endAt 按首节次起始 ~ 末节次结束（上海本地转 ISO）
  const payloads = []
  for (const [startNo, endNo] of props.presetSegments) {
    const sp = periodOf(startNo)
    const ep = periodOf(endNo)
    if (!sp || !ep) {
      ElMessage.warning('节次数据无效，请重新选择')
      return
    }
    const startAt = shanghaiLocalToIso(props.presetDate, sp.startClock)
    const endAt = shanghaiLocalToIso(props.presetDate, ep.endClock)
    if (!startAt || !endAt) {
      ElMessage.warning('时间格式无效')
      return
    }
    const av = availability.value
    if (av && av.openStart && av.openEnd) {
      if (sp.startClock < av.openStart || ep.endClock > av.openEnd) {
        ElMessage.warning(`须在开放时段 ${av.openStart} - ${av.openEnd} 内`)
        return
      }
    }
    payloads.push({
      spaceId: props.presetSpaceId,
      purpose: form.purpose,
      participantCount: form.participantCount,
      courseName: form.courseName || undefined,
      className: form.className || undefined,
      teacherId: form.teacherId || undefined,
      bookingDate: props.presetDate,
      startPeriodId: String(sp.id),
      endPeriodId: String(ep.id),
      startAt,
      endAt
    })
  }
  emit('submit', payloads)
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) initForm()
  }
)
</script>

<style scoped>
.book_form__preset {
  padding: 12px 14px;
  margin-bottom: 12px;
  background: var(--surface-sunken, #f5f7fa);
  border-radius: 8px;
}

.book_form__preset_line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin: 0 0 6px;
  font-size: 13px;
}

.book_form__preset_line:last-child {
  margin-bottom: 0;
}

.book_form__segments {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.book_form__split_hint {
  margin: 4px 0 0;
  color: var(--warn, #f79009);
}

.book_form__row {
  display: flex;
  gap: 12px;
}

.book_form__grow {
  flex: 1;
}

.book_form__full {
  width: 100%;
}

.book_form__count {
  width: 130px;
}

.book_form__avail {
  padding: 12px 14px;
  margin-top: -4px;
  margin-bottom: 12px;
  background: var(--surface-sunken, #f5f7fa);
  border-radius: 8px;
}

.book_form__avail > .t-sub {
  margin: 0 0 6px;
}

.book_form__avail .book_form__segments {
  display: flex;
  margin-top: 6px;
}

@media (max-width: 640px) {
  .book_form__row {
    flex-direction: column;
    gap: 0;
  }
}
</style>