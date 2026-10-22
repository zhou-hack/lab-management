<template>
  <el-dialog
    :model-value="modelValue"
    title="批量周期预约"
    width="600px"
    :close-on-click-modal="false"
    @update:model-value="(v) => !v && emit('close')"
  >
    <p class="t-hint book_series__tip">
      固定星期 + 节次 + 使用周期，一次提交；管理员批准后，周期内每周同日同节次自动占用。
    </p>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent
    >
      <div class="book_series__row">
        <el-form-item
          label="预约场地"
          prop="spaceId"
          class="book_series__grow"
        >
          <el-select
            v-model="form.spaceId"
            placeholder="仅展示可用状态场地"
            class="book_series__full"
            filterable
            :disabled="!!presetSpaceId"
          >
            <el-option
              v-for="s in spaceOptions"
              :key="s.id"
              :label="`${s.labName}（${s.capacity} 人）`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="星期">
          <el-select
            v-model="form.weekday"
            class="book_series__weekday"
          >
            <template
              v-for="(label, num) in WEEKDAY_NAMES"
              :key="num"
            >
              <el-option
                v-if="num > 0"
                :label="label"
                :value="num"
              />
            </template>
          </el-select>
        </el-form-item>
      </div>

      <div class="book_series__row">
        <el-form-item
          label="起始节次"
          prop="startPeriodId"
        >
          <el-select
            v-model="form.startPeriodId"
            placeholder="起始节次"
            class="book_series__period"
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
            class="book_series__period"
          >
            <el-option
              v-for="p in periodOptions"
              :key="p.id"
              :label="periodLabel(p)"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
      </div>

      <div class="book_series__row">
        <el-form-item
          label="周期开始日期"
          prop="cycleStartDate"
          class="book_series__grow"
        >
          <el-date-picker
            v-model="form.cycleStartDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="周期开始"
            :disabled-date="disablePast"
            class="book_series__full"
          />
        </el-form-item>
        <el-form-item
          label="周期结束日期"
          prop="cycleEndDate"
          class="book_series__grow"
        >
          <el-date-picker
            v-model="form.cycleEndDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="周期结束"
            :disabled-date="disablePast"
            class="book_series__full"
          />
        </el-form-item>
      </div>
      <p
        v-if="weekCount > 0"
        class="t-hint book_series__weeks"
      >
        周期内「{{ WEEKDAY_NAMES[form.weekday || 0] }}」共匹配 {{ weekCount }} 周
      </p>

      <div class="book_series__row">
        <el-form-item
          label="课程"
          class="book_series__grow"
        >
          <el-input
            v-model="form.courseName"
            maxlength="128"
            placeholder="如：Python 程序设计实训课"
          />
        </el-form-item>
        <el-form-item
          label="班级"
          class="book_series__grow"
        >
          <el-input
            v-model="form.className"
            maxlength="128"
            placeholder="如：软件 2301"
          />
        </el-form-item>
      </div>

      <div class="book_series__row">
        <el-form-item
          label="老师"
          class="book_series__grow"
        >
          <el-select
            v-model="form.teacherId"
            placeholder="上课老师"
            filterable
            class="book_series__full"
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
            class="book_series__count"
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
          placeholder="如：每周二 3-4 节实训课（实训周）"
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
        提交周期预约
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listSpaceOptions } from '@/api/space'
import { listPeriods, listTeachers, createSeries } from '@/api/booking'
import { addDaysShanghai, todayShanghai } from '@/utils/datetime'

// 下标即 ISO 星期值（1=周一 … 7=周日）
const WEEKDAY_NAMES = ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日']

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 从时间轴带入的预选场地/日期（推导星期与周期起点） */
  presetSpaceId: { type: String, default: '' },
  presetDate: { type: String, default: '' },
  presetPeriodNos: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'submit'])

const formRef = ref()
const spaceOptions = ref([])
const periodOptions = ref([])
const teacherOptions = ref([])

const form = reactive({
  spaceId: '',
  weekday: null,
  startPeriodId: null,
  endPeriodId: null,
  cycleStartDate: '',
  cycleEndDate: '',
  courseName: '',
  className: '',
  teacherId: '',
  participantCount: null,
  purpose: '',
  idempotencyKey: genId()
})

const selectedSpace = computed(() => spaceOptions.value.find((s) => s.id === form.spaceId))
const capacity = computed(() => selectedSpace.value?.capacity || null)

/** 周期内匹配星期的周数（表单辅助提示） */
const weekCount = computed(() => {
  if (!form.weekday || !form.cycleStartDate || !form.cycleEndDate) return 0
  if (form.cycleStartDate > form.cycleEndDate) return 0
  let count = 0
  let cursor = form.cycleStartDate
  while (cursor <= form.cycleEndDate) {
    const [y, mo, di] = cursor.split('-').map(Number)
    const day = new Date(Date.UTC(y, mo - 1, di)).getUTCDay()
    const iso = day === 0 ? 7 : day
    if (iso === form.weekday) count++
    cursor = addDaysShanghai(cursor, 1)
  }
  return count
})

const rules = {
  spaceId: [{ required: true, message: '请选择预约场地', trigger: 'change' }],
  weekday: [{ required: true, message: '请选择星期', trigger: 'change' }],
  startPeriodId: [{ required: true, message: '请选择起始节次', trigger: 'change' }],
  endPeriodId: [{ required: true, message: '请选择结束节次', trigger: 'change' }],
  cycleStartDate: [{ required: true, message: '请选择周期开始日期', trigger: 'change' }],
  cycleEndDate: [{ required: true, message: '请选择周期结束日期', trigger: 'change' }],
  purpose: [
    { required: true, message: '请填写使用事由', trigger: 'blur' },
    { max: 512, message: '事由不能超过 512 字', trigger: 'blur' }
  ],
  participantCount: [{ required: true, message: '请填写参与人数', trigger: 'blur' }]
}

function periodLabel(p) {
  return `${p.periodName} ${p.startClock}-${p.endClock}`
}

function disablePast(date) {
  const [y, m, d] = todayShanghai().split('-').map(Number)
  return date.getTime() < Date.UTC(y, m - 1, d)
}

function genId() {
  return `series_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

async function initForm() {
  try {
    const [spaces, periods, teachers] = await Promise.all([
      listSpaceOptions({ availableOnly: true, limit: 100 }),
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
  form.spaceId = props.presetSpaceId || ''
  // 由预选日期推导星期（ISO：周一=1 … 周日=7）
  if (props.presetDate) {
    const [y, m, d] = props.presetDate.split('-').map(Number)
    const day = new Date(Date.UTC(y, m - 1, d)).getUTCDay()
    form.weekday = day === 0 ? 7 : day
    form.cycleStartDate = props.presetDate
  } else {
    form.weekday = null
    form.cycleStartDate = ''
  }
  form.cycleEndDate = props.presetDate ? addDaysShanghai(props.presetDate, 6) : ''
  const [startNo, endNo] = props.presetPeriodNos || []
  form.startPeriodId = startNo ? periodIdOf(startNo) : null
  form.endPeriodId = endNo ? periodIdOf(endNo) : null
  form.courseName = ''
  form.className = ''
  form.teacherId = ''
  form.participantCount = null
  form.purpose = ''
  form.idempotencyKey = genId()
}

function periodIdOf(periodNo) {
  const p = periodOptions.value.find((item) => Number(item.periodNo) === Number(periodNo))
  return p ? p.id : periodNo
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (Number(form.startPeriodId) > Number(form.endPeriodId)) {
    ElMessage.warning('起始节次不能晚于结束节次')
    return
  }
  if (form.cycleStartDate > form.cycleEndDate) {
    ElMessage.warning('周期开始日期不能晚于结束日期')
    return
  }
  if (capacity.value && form.participantCount > capacity.value) {
    ElMessage.warning(`参与人数不能超过场地容量（${capacity.value} 人）`)
    return
  }
  if (weekCount.value === 0) {
    ElMessage.warning('周期内没有匹配该星期的日期，请调整周期')
    return
  }
  await createSeries({
    spaceId: form.spaceId,
    weekday: form.weekday,
    startPeriodId: form.startPeriodId,
    endPeriodId: form.endPeriodId,
    cycleStartDate: form.cycleStartDate,
    cycleEndDate: form.cycleEndDate,
    courseName: form.courseName || undefined,
    className: form.className || undefined,
    teacherId: form.teacherId || undefined,
    purpose: form.purpose,
    participantCount: form.participantCount,
    idempotencyKey: form.idempotencyKey
  })
  ElMessage.success('周期预约已提交，等待管理员核对')
  emit('submit')
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) initForm()
  }
)
</script>

<style scoped>
.book_series__tip {
  margin: 0 0 12px;
}

.book_series__row {
  display: flex;
  gap: 12px;
}

.book_series__grow {
  flex: 1;
}

.book_series__full {
  width: 100%;
}

.book_series__weekday {
  width: 110px;
}

.book_series__period {
  width: 150px;
}

.book_series__count {
  width: 130px;
}

.book_series__weeks {
  margin: -4px 0 10px;
}

@media (max-width: 640px) {
  .book_series__row {
    flex-direction: column;
    gap: 0;
  }
}
</style>