<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑场地' : '新建场地'"
    width="640px"
    class="space_form_dialog"
    :close-on-click-modal="false"
    @update:model-value="(v) => !v && emit('close')"
    @closed="onClosed"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="92px"
      class="space_form"
    >
      <el-form-item
        label="场地名称"
        prop="labName"
      >
        <el-popover
          v-model:visible="pickerVisible"
          trigger="click"
          placement="bottom-start"
          :teleported="true"
          :show-arrow="false"
          popper-class="space_name_picker"
          @show="onPickerShow"
          @hide="onPickerHide"
        >
          <template #reference>
            <el-input
              v-model="form.labName"
              placeholder="输入或选择场地名称，如 A703 网络实训室"
              maxlength="128"
              clearable
              @input="onNameInput"
            />
          </template>
          <div class="space_picker">
            <div class="space_picker__head">
              <div class="space_picker__tabs">
                <button
                  v-for="tab in PICKER_TABS"
                  :key="tab.value"
                  type="button"
                  class="space_picker__tab"
                  :class="{ is_active: pickerTab === tab.value }"
                  @click="pickerTab = tab.value"
                >
                  {{ tab.label }}
                </button>
              </div>
              <span class="t-hint">{{ filteredOptions.length }} 个可选场地</span>
            </div>
            <div
              v-if="optionsLoading"
              class="space_picker__state"
            >
              正在加载场地名称…
            </div>
            <div
              v-else-if="optionsError"
              class="space_picker__state"
            >
              {{ optionsError }}
            </div>
            <div
              v-else
              class="space_picker__list"
            >
              <button
                v-for="item in filteredOptions"
                :key="item.id"
                type="button"
                class="space_picker__item"
                :class="{ is_active: String(item.id) === selectedSpaceId }"
                @click="onSelectOption(item)"
              >
                <span class="space_picker__name">{{ item.labName }}</span>
                <span class="space_picker__meta">
                  {{ SPACE_TYPE_MAP[item.labType]?.label || item.labType }} · {{ item.capacity }} 人 · {{ SPACE_STATUS_MAP[item.status]?.label || item.status }}
                </span>
              </button>
              <p
                v-if="!filteredOptions.length"
                class="space_picker__state"
              >
                没有匹配的场地名称，可直接使用“{{ form.labName }}”新建
              </p>
            </div>
          </div>
        </el-popover>
        <span
          v-if="isEdit"
          class="t-hint space_form__hint"
        >名称全局唯一，删除后不可复用</span>
        <span
          v-if="duplicateName"
          class="space_form__dup"
        >该名称已被其他场地使用，请更换</span>
      </el-form-item>
      <div class="space_form__row">
        <el-form-item
          label="场地类型"
          prop="labType"
        >
          <el-select
            v-model="form.labType"
            placeholder="请选择类型"
            class="space_form__select"
          >
            <el-option
              v-for="t in SPACE_TYPES"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="容纳人数"
          prop="capacity"
        >
          <el-input-number
            v-model="form.capacity"
            :min="1"
            :max="9999"
            controls-position="right"
            class="space_form__num"
          />
        </el-form-item>
      </div>
      <el-form-item
        label="位置"
        prop="locationDetail"
      >
        <el-input
          v-model="form.locationDetail"
          placeholder="如 A 楼七层 A703"
          maxlength="128"
        />
      </el-form-item>
      <el-form-item
        label="开放时段"
        prop="openStart"
        class="space_form__time"
      >
        <div class="space_form__row space_form__row--tight">
          <el-time-select
            v-model="form.openStart"
            start="00:00"
            end="23:30"
            step="00:30"
            placeholder="开始"
            class="space_form__select"
          />
          <span class="space_form__dash">—</span>
          <el-time-select
            v-model="form.openEnd"
            :start="form.openStart || '00:00'"
            end="23:30"
            step="00:30"
            placeholder="结束"
            class="space_form__select"
          />
        </div>
        <span class="t-hint space_form__hint">上海本地时间，预约模块按此时段做可用性校验</span>
      </el-form-item>
      <el-form-item
        label="设备清单"
        prop="facilities"
      >
        <el-input
          v-model="form.facilities"
          type="textarea"
          :rows="3"
          placeholder="选填，如 示波器 20 台、实训工位 50 个"
          maxlength="2000"
          show-word-limit
        />
      </el-form-item>
      <el-form-item
        label="状态"
        prop="status"
      >
        <el-select
          v-model="form.status"
          class="space_form__select"
        >
          <el-option
            v-for="s in SPACE_STATUS"
            :key="s.value"
            :label="s.label"
            :value="s.value"
          />
        </el-select>
        <span
          v-if="form.status === 'MAINTAINING'"
          class="t-hint space_form__hint"
        >维护中场地暂停预约，恢复可用后可再开放</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('close')">
        取消
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="onSubmit"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { SPACE_TYPES, SPACE_TYPE_MAP, SPACE_STATUS, SPACE_STATUS_MAP } from './space.constants'
import { listSpaceOptions } from '@/api/space'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  space: { type: Object, default: null }
})

const emit = defineEmits(['close', 'submit', 'preview'])

const formRef = ref()
const submitting = ref(false)
const isEdit = computed(() => !!props.space)

// ---------------------------------------------------------------------------
// 场地名称选择下拉（tab：全部/教室/实训室）；选中后通知父级弹出左侧信息面板
// ---------------------------------------------------------------------------
const PICKER_TABS = [
  { value: 'ALL', label: '全部' },
  { value: 'CLASSROOM', label: '教室' },
  { value: 'TRAINING_ROOM', label: '实训室' }
]

const pickerVisible = ref(false)
const pickerTab = ref('ALL')
const allOptions = ref([])
const optionsLoading = ref(false)
const optionsError = ref('')
const selectedSpaceId = ref(null)

const emptyForm = () => ({
  labName: '',
  labType: '',
  locationDetail: '',
  capacity: 40,
  facilities: '',
  openStart: '08:00',
  openEnd: '21:00',
  status: 'AVAILABLE'
})

const form = reactive(emptyForm())

/** 输入名称与其他已有场地重名时提示（基于已加载选项，编辑时排除自身；后端唯一约束兜底） */
const duplicateName = computed(() => {
  const value = String(form.labName || '').trim()
  if (!value) return false
  const selfId = props.space?.id ? String(props.space.id) : ''
  return allOptions.value.some(
    (item) => item.labName === value && String(item.id) !== selfId
  )
})

/** 下拉候选：按 tab 与当前输入过滤（编辑时排除当前场地） */
const filteredOptions = computed(() => {
  const keyword = String(form.labName || '').trim().toLowerCase()
  const selfId = props.space?.id ? String(props.space.id) : ''
  return allOptions.value
    .filter((item) => String(item.id) !== selfId)
    .filter((item) => pickerTab.value === 'ALL' || item.labType === pickerTab.value)
    .filter((item) => !keyword || String(item.labName || '').toLowerCase().includes(keyword))
    .sort((a, b) => a.labName.localeCompare(b.labName, 'zh-Hans-CN'))
})

/** 耗时统计（毫秒），用于排查选择联动与详情加载响应速度 */
function elapsedMs(begin) {
  return Math.round(performance.now() - begin)
}

// 按需请求场地选项：默认取数据库前 15 条；输入关键字搜索时调大 limit 索引更多数据
let optionsSeq = 0
let searchTimer = null

async function loadOptions(keyword = '') {
  const begin = performance.now()
  const seq = ++optionsSeq
  optionsLoading.value = true
  optionsError.value = ''
  console.info('[场地名称选择] 请求场地选项', {
    keyword: keyword || '(默认)',
    limit: keyword ? 50 : 15,
    mode: isEdit.value ? 'edit' : 'create'
  })
  try {
    const list = await listSpaceOptions(keyword ? { keyword, limit: 50 } : { limit: 15 })
    if (seq !== optionsSeq) {
      console.info('[场地名称选择] 丢弃过期响应', { keyword, seq, latestSeq: optionsSeq })
      return
    }
    allOptions.value = list || []
    console.info('[场地名称选择] 场地选项加载完成', {
      keyword: keyword || '(默认)',
      count: allOptions.value.length,
      costMs: elapsedMs(begin)
    })
  } catch (e) {
    if (seq !== optionsSeq) return
    optionsError.value = '可选场地名称加载失败，请重试'
    console.warn('[场地名称选择] 场地选项加载失败', { keyword, error: e?.message, costMs: elapsedMs(begin) })
  } finally {
    if (seq === optionsSeq) optionsLoading.value = false
  }
}

/** 输入防抖 200ms 后按关键字请求数据库 */
function scheduleSearch(keyword) {
  if (searchTimer) clearTimeout(searchTimer)
  console.debug('[场地名称选择] 防抖排队，200ms 后请求数据库', { keyword: keyword || '(默认)' })
  searchTimer = setTimeout(() => loadOptions(keyword), 200)
}

function onPickerShow() {
  console.info('[场地名称选择] 下拉已展示', { tab: pickerTab.value, optionCount: filteredOptions.value.length })
}

function onPickerHide() {
  console.info('[场地名称选择] 下拉已收起')
}

/** 输入变化：防抖请求数据库搜索更多选项；命中已有完整名称时通知父级弹出左侧信息面板 */
function onNameInput(value) {
  // 输入时确保下拉展开
  pickerVisible.value = true
  const keyword = String(value || '').trim()
  if (!keyword) {
    selectedSpaceId.value = null
    scheduleSearch('')
    return
  }
  const match = allOptions.value.find((item) => item.labName === keyword)
  if (match) {
    if (selectedSpaceId.value !== String(match.id)) {
      console.info('[场地名称选择] 输入命中已有场地，弹出左侧信息面板', {
        spaceId: match.id,
        labName: match.labName
      })
      selectedSpaceId.value = String(match.id)
      emit('preview', match)
    }
  } else {
    selectedSpaceId.value = null
  }
  console.debug('[场地名称选择] 输入变更，触发搜索', { keyword, loadedCount: allOptions.value.length })
  scheduleSearch(keyword)
}

/** 选择下拉项：回填名称、收起下拉，并通知父级弹出左侧信息面板 */
function onSelectOption(item) {
  if (!item) return
  form.labName = item.labName
  pickerVisible.value = false
  selectedSpaceId.value = String(item.id)
  console.info('[场地名称选择] 选中场地，弹出左侧信息面板', {
    spaceId: item.id,
    labName: item.labName,
    labType: item.labType
  })
  emit('preview', item)
  formRef.value?.validateField('labName').catch(() => {})
}

const rules = {
  labName: [{ required: true, message: '请输入场地名称', trigger: 'blur' }],
  labType: [{ required: true, message: '请选择场地类型', trigger: 'change' }],
  locationDetail: [{ required: true, message: '请输入位置', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容纳人数', trigger: 'change' }],
  openStart: [
    { required: true, message: '请选择开放开始时间', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (form.openEnd && value && value >= form.openEnd) {
          callback(new Error('开始时间须早于结束时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  openEnd: [
    { required: true, message: '请选择开放结束时间', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (form.openStart && value && value <= form.openStart) {
          callback(new Error('结束时间须晚于开始时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    console.info('[场地表单] 打开', {
      mode: props.space ? 'edit' : 'create',
      spaceId: props.space?.id || null,
      labName: props.space?.labName || ''
    })
    pickerVisible.value = false
    pickerTab.value = 'ALL'
    selectedSpaceId.value = null
    Object.assign(form, emptyForm())
    if (props.space) {
      Object.assign(form, {
        labName: props.space.labName,
        labType: props.space.labType,
        locationDetail: props.space.locationDetail || '',
        capacity: props.space.capacity,
        facilities: props.space.facilities || '',
        openStart: props.space.openStart || '08:00',
        openEnd: props.space.openEnd || '21:00',
        status: props.space.status
      })
    }
    loadOptions()
  }
)

async function onSubmit() {
  if (!formRef.value) return
  console.info('[场地表单] 提交校验开始', {
    mode: isEdit.value ? 'edit' : 'create',
    labName: form.labName
  })
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    console.warn('[场地表单] 表单校验未通过，终止提交', { labName: form.labName })
    return
  }
  if (duplicateName.value) {
    console.warn('[场地表单] 名称与其他场地重复，终止提交', { labName: form.labName })
    ElMessage.warning('该场地名称已被其他场地使用，请更换')
    return
  }
  submitting.value = true
  try {
    const payload = {
      labName: form.labName.trim(),
      labType: form.labType,
      locationDetail: form.locationDetail.trim(),
      capacity: form.capacity,
      facilities: form.facilities?.trim() || null,
      openStart: form.openStart,
      openEnd: form.openEnd,
      status: form.status
    }
    console.info('[场地表单] 校验通过，提交数据', { id: props.space?.id || null, payload })
    emit('submit', { id: props.space?.id || null, payload })
  } catch (e) {
    console.error('[场地表单] 提交失败', { labName: form.labName, error: e?.message })
    throw e
  } finally {
    submitting.value = false
  }
}

function onClosed() {
  console.info('[场地表单] 关闭')
  pickerVisible.value = false
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
  optionsSeq += 1
  formRef.value?.resetFields()
  ElMessage.closeAll()
}
</script>

<style scoped>
.space_form__select {
  width: 100%;
}

.space_form__dup {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--danger, #d92d20);
}

/* 名称下拉（teleport 到 body，层级高于页面其他元素） */
:global(.space_name_picker) {
  width: min(560px, 92vw) !important;
  padding: 10px !important;
}

.space_picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.space_picker__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.space_picker__tabs {
  display: inline-flex;
  gap: 4px;
  padding: 3px;
  background: var(--surface-sunken);
  border-radius: var(--r-full, 999px);
}

.space_picker__tab {
  padding: 5px 14px;
  border: 0;
  border-radius: var(--r-full, 999px);
  background: transparent;
  color: var(--g3);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);
}

.space_picker__tab.is_active {
  background: var(--surface);
  color: var(--brand);
  font-weight: var(--fw-semibold);
  box-shadow: 0 0 0 .5px var(--hair);
}

.space_picker__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 280px;
  overflow-y: auto;
}

.space_picker__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.space_picker__item:hover,
.space_picker__item.is_active {
  background: var(--info-bg);
}

.space_picker__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--g1);
}

.space_picker__meta {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--g4);
}

.space_picker__state {
  padding: 16px 0;
  margin: 0;
  font-size: 12px;
  color: var(--g3);
  text-align: center;
}

.space_form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.space_form__row--tight {
  gap: 0 8px;
  align-items: center;
}

.space_form__num {
  width: 100%;
}

.space_form__dash {
  color: var(--g3, #6b7280);
}

.space_form__hint {
  display: block;
  margin-top: 4px;
  line-height: 1.5;
}

/* 弹窗最大宽度约束，避免窄屏溢出 */
:global(.space_form_dialog) {
  max-width: 94vw;
}

@media (max-width: 640px) {
  .space_form__row {
    grid-template-columns: 1fr;
  }
}
</style>
