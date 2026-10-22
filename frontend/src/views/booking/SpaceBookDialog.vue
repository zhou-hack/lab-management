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
      <div class="book_form__row">
        <el-form-item
          label="预约场地"
          prop="spaceId"
          class="book_form__grow"
        >
          <el-select
            v-model="form.spaceId"
            placeholder="仅展示可用状态场地"
            class="book_form__full"
            filterable
            :disabled="!!presetSpaceId"
            @change="onSpaceChange"
          >
            <el-option
              v-for="s in spaceOptions"
              :key="s.id"
              :label="`${s.labName}（${s.capacity} 人）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="日期（上海本地）"
          prop="date"
        >
          <el-date-picker
            v-model="form.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :disabled-date="disablePast"
            class="book_form__date"
            @change="loadAvailability"
          />
        </el-form-item>
      </div>

      <!-- 节次选择：默认带入时间轴选中的节次，也可手动调整 -->
      <div class="book_form__row">
        <el-form-item
          label="起始节次"
          prop="startPeriodId"
        >
          <el-select
            v-model="form.startPeriodId"
            placeholder="起始节次"
            class="book_form__period"
            @change="syncPeriodTime"
          >
            <el-option
              v-for="p in periodOptions"
              :key="p.id"
              :label="periodLabel(p)"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="结束节次"
          prop="endPeriodId"
        >
          <el-select
            v-model="form.endPeriodId"
            placeholder="结束节次"
            class="book_form__period"
            @change="syncPeriodTime"
          >
            <el-option
              v-for="p in periodOptions"
              :key="p.id"
              :label="periodLabel(p)"
              :value="p.id"
            />
          </el-select>
          <span
            v-if="startPeriod && endPeriod"
            class="book_form__period_range t-hint"
          >
            {{ form.startPeriodId <= form.endPeriodId ? '占用 ' + startPeriod.startClock + ' ~ ' + endPeriod.endClock : '' }}
          </span>
        </el-form-item>
      </div>

      <!-- 该场地已有预约/占用（可选中的场地 + 日期联动） -->
      <div
        v-if="form.spaceId && form.date"
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
import { shanghaiLocalToIso, todayShanghai } from '@/utils/datetime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 从时间轴「发起预约」带入的预选场地/日期/节次 */
  presetSpaceId: { type: String, default: '' },
  presetDate: { type: String, default: '' },
  /** 数组：[起始节次号, 结束节次号]（基于 class_period.period_no） */
  presetPeriodNos: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'submit'])

const formRef = ref()
const availabilityLoading = ref(false)
const spaceOptions = ref([])
const periodOptions = ref([])
const teacherOptions = ref([])
const availability = ref(null)

const form = reactive({
  spaceId: '',
  date: '',
  startPeriodId: null,
  endPeriodId: null,
  courseName: '',
  className: '',
  teacherId: '',
  participantCount: null,
  purpose: ''
})

const selectedSpace = computed(() => spaceOptions.value.find((s) => s.id === form.spaceId))
const capacity = computed(() => selectedSpace.value?.capacity || null)
const startPeriod = computed(() => periodOptions.value.find((p) => p.id === form.startPeriodId))
const endPeriod = computed(() => periodOptions.value.find((p) => p.id === form.endPeriodId))
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

const rules = {
  spaceId: [{ required: true, message: '请选择预约场地', trigger: 'change' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startPeriodId: [{ required: true, message: '请选择起始节次', trigger: 'change' }],
  endPeriodId: [{ required: true, message: '请选择结束节次', trigger: 'change' }],
  purpose: [
    { required: true, message: '请填写使用事由', trigger: 'blur' },
    { max: 512, message: '事由不能超过 512 字', trigger: 'blur' }
  ],
  participantCount: [{ required: true, message: '请填写参与人数', trigger: 'blur' }]
}

function periodLabel(p) {
  return `${p.periodName} ${p.startClock}-${p.endClock}`
}

function formatClock(iso) {
  return iso ? String(iso).slice(11, 16) : ''
}

/** 禁选昨天（含）之前 */
function disablePast(date) {
  const [y, m, d] = todayShanghai().split('-').map(Number)
  return date.getTime() < Date.UTC(y, m - 1, d)
}

/** 节次区间 ↔ 起止时间推导：startPeriodId <= endPeriodId，占用段 = 首节次起始 ~ 末节次结束 */
async function syncPeriodTime() {
  if (!form.startPeriodId || !form.endPeriodId) return
  if (Number(form.startPeriodId) > Number(form.endPeriodId)) {
    ElMessage.warning('起始节次不能晚于结束节次')
    return
  }
  // 结束节次下拉回显（Element Plus 渲染需要，v-model 已绑定）
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
  const [startNo, endNo] = props.presetPeriodNos || []
  form.spaceId = props.presetSpaceId || ''
  form.date = props.presetDate || ''
  form.startPeriodId = startNo ? String(periodOf(startNo)) : null
  form.endPeriodId = endNo ? String(periodOf(endNo)) : null
  form.courseName = ''
  form.className = ''
  form.teacherId = ''
  form.participantCount = null
  form.purpose = ''
  availability.value = null
  if (form.spaceId && form.date) await loadAvailability()
}

/** 节次号 → 节次 ID（class_period.id 与 period_no 一致） */
function periodOf(periodNo) {
  const p = periodOptions.value.find((item) => Number(item.periodNo) === Number(periodNo))
  return p ? p.id : periodNo
}

function onSpaceChange() {
  availability.value = null
  loadAvailability()
}

async function loadAvailability() {
  if (!form.spaceId || !form.date) {
    availability.value = null
    return
  }
  availabilityLoading.value = true
  try {
    availability.value = await getAvailability(form.spaceId, form.date)
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
  if (!startPeriod.value || !endPeriod.value) {
    ElMessage.warning('请先选择起始与结束节次')
    return
  }
  if (Number(form.startPeriodId) > Number(form.endPeriodId)) {
    ElMessage.warning('起始节次不能晚于结束节次')
    return
  }
  const startAt = shanghaiLocalToIso(form.date, startPeriod.value.startClock)
  const endAt = shanghaiLocalToIso(form.date, endPeriod.value.endClock)
  if (!startAt || !endAt) {
    ElMessage.warning('时间格式无效')
    return
  }
  const av = availability.value
  if (av && av.openStart && av.openEnd) {
    if (startPeriod.value.startClock < av.openStart || endPeriod.value.endClock > av.openEnd) {
      ElMessage.warning(`须在开放时段 ${av.openStart} - ${av.openEnd} 内`)
      return
    }
  }
  if (capacity.value && form.participantCount > capacity.value) {
    ElMessage.warning(`参与人数不能超过场地容量（${capacity.value} 人）`)
    return
  }
  emit('submit', {
    spaceId: form.spaceId,
    purpose: form.purpose,
    participantCount: form.participantCount,
    courseName: form.courseName || undefined,
    className: form.className || undefined,
    teacherId: form.teacherId || undefined,
    bookingDate: form.date,
    startPeriodId: form.startPeriodId,
    endPeriodId: form.endPeriodId,
    startAt,
    endAt
  })
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) initForm()
  }
)
</script>

<style scoped>
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

.book_form__date {
  width: 150px;
}

.book_form__period {
  width: 130px;
}

.book_form__period_range {
  display: block;
  margin-top: 4px;
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

.book_form__segments {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 6px;
}

@media (max-width: 640px) {
  .book_form__row {
    flex-direction: column;
    gap: 0;
  }
}
</style>