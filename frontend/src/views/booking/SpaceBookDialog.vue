<template>
  <el-dialog
    :model-value="modelValue"
    title="发起场地预约"
    width="560px"
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
      <el-form-item
        label="预约场地"
        prop="spaceId"
      >
        <el-select
          v-model="form.spaceId"
          placeholder="仅展示可用状态场地"
          class="book_form__full"
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

      <div class="book_form__row">
        <el-form-item
          label="日期（上海本地）"
          prop="date"
          class="book_form__grow"
        >
          <el-date-picker
            v-model="form.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            :disabled-date="disablePast"
            class="book_form__full"
            @change="loadAvailability"
          />
        </el-form-item>
        <el-form-item
          label="开始时间"
          prop="startTime"
        >
          <el-time-select
            v-model="form.startTime"
            start="06:00"
            end="23:30"
            step="00:30"
            placeholder="开始"
            @change="loadAvailability"
          />
        </el-form-item>
        <el-form-item
          label="结束时间"
          prop="endTime"
        >
          <el-time-select
            v-model="form.endTime"
            start="06:00"
            end="23:30"
            step="00:30"
            placeholder="结束"
            @change="loadAvailability"
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
          placeholder="如：Python 程序设计实训课"
        />
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
          placeholder="不超过场地容量"
          class="book_form__count"
        />
        <span
          v-if="capacity"
          class="book_form__capacity t-hint"
        >
          场地容量 {{ capacity }} 人
        </span>
      </el-form-item>

      <!-- 可用时段辅助区 -->
      <div
        v-if="form.spaceId && form.date"
        v-loading="availabilityLoading"
        class="book_form__avail"
      >
        <p
          v-if="openWindow"
          class="t-sub"
        >
          开放时段 {{ openWindow }}（上海本地时间，须整段在窗口内）
        </p>
        <div
          v-if="freeSegments.length"
          class="book_form__segments"
        >
          <span class="t-hint">空闲：</span>
          <el-tag
            v-for="(seg, i) in freeSegments"
            :key="i"
            size="small"
            type="success"
            effect="plain"
          >
            {{ seg }}
          </el-tag>
        </div>
        <div
          v-else
          class="book_form__segments"
        >
          <span class="t-hint">该日开放窗口内已无空闲时段</span>
        </div>
        <div
          v-if="occupiedSegments.length"
          class="book_form__segments"
        >
          <span class="t-hint">已占用：</span>
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
      </div>
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
import { getAvailability } from '@/api/booking'
import { formatInstant, shanghaiLocalToIso, todayShanghai } from '@/utils/datetime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 从场地列表「预约」进入时的预选场地 ID */
  presetSpaceId: { type: String, default: '' }
})

const emit = defineEmits(['close', 'submit'])

const formRef = ref()
const availabilityLoading = ref(false)
const spaceOptions = ref([])
const availability = ref(null)

const form = reactive({
  spaceId: '',
  date: '',
  startTime: '',
  endTime: '',
  purpose: '',
  participantCount: null
})

const selectedSpace = computed(() => spaceOptions.value.find((s) => s.id === form.spaceId))
const capacity = computed(() => selectedSpace.value?.capacity || null)
const openWindow = computed(() =>
  availability.value ? `${availability.value.openStart} - ${availability.value.openEnd}` : ''
)
const occupiedSegments = computed(() =>
  (availability.value?.occupied || []).map((seg) => {
    const s = formatInstant(seg.startAt, { withDate: false })
    const e = formatInstant(seg.endAt, { withDate: false })
    return `${s} ~ ${e}`
  })
)

/** 开放窗口减去占用段 = 空闲段（全部为上海本地 HH:mm） */
const freeSegments = computed(() => {
  const av = availability.value
  if (!av || !av.openStart || !av.openEnd) return []
  const toMin = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5))
  const toHHmm = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
  let cursor = toMin(av.openStart)
  const end = toMin(av.openEnd)
  const occupied = (av.occupied || [])
    .map((seg) => ({
      start: toMin(formatInstant(seg.startAt, { withDate: false })),
      end: toMin(formatInstant(seg.endAt, { withDate: false }))
    }))
    .filter((seg) => seg.end > seg.start)
    .sort((a, b) => a.start - b.start)
  const free = []
  for (const seg of occupied) {
    if (seg.start > cursor) free.push(`${toHHmm(cursor)} ~ ${toHHmm(Math.min(seg.start, end))}`)
    cursor = Math.max(cursor, seg.end)
    if (cursor >= end) break
  }
  if (cursor < end) free.push(`${toHHmm(cursor)} ~ ${toHHmm(end)}`)
  // 过滤零长度或越界产生的无效段
  return free.filter((s) => s.split(' ~ ')[0] < s.split(' ~ ')[1])
})

const rules = {
  spaceId: [{ required: true, message: '请选择预约场地', trigger: 'change' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  purpose: [
    { required: true, message: '请填写使用事由', trigger: 'blur' },
    { max: 512, message: '事由不能超过 512 字', trigger: 'blur' }
  ],
  participantCount: [{ required: true, message: '请填写参与人数', trigger: 'blur' }]
}

/** 禁选今天（上海）之前的日期 */
function disablePast(date) {
  const [y, m, d] = todayShanghai().split('-').map(Number)
  return date.getTime() < Date.UTC(y, m - 1, d)
}

async function initForm() {
  try {
    const list = await listSpaceOptions({ availableOnly: true })
    spaceOptions.value = list || []
  } catch {
    spaceOptions.value = []
  }
  form.spaceId = props.presetSpaceId || ''
  form.date = ''
  form.startTime = ''
  form.endTime = ''
  form.purpose = ''
  form.participantCount = null
  availability.value = null
  if (form.spaceId) await loadAvailability()
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
  const startAt = shanghaiLocalToIso(form.date, form.startTime)
  const endAt = shanghaiLocalToIso(form.date, form.endTime)
  if (!startAt || !endAt) {
    ElMessage.warning('时间格式无效')
    return
  }
  if (form.startTime >= form.endTime) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  const av = availability.value
  if (av && av.openStart && av.openEnd) {
    if (form.startTime < av.openStart || form.endTime > av.openEnd) {
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

.book_form__count {
  width: 140px;
}

.book_form__capacity {
  margin-left: 10px;
}

.book_form__avail {
  padding: 12px 14px;
  margin-top: -6px;
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

@media (max-width: 600px) {
  .book_form__row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
