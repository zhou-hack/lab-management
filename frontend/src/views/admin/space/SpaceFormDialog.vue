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
        <el-input
          v-model="form.labName"
          placeholder="输入场地名称，如 A703 网络实训室"
          maxlength="128"
          clearable
        />
        <span
          v-if="isEdit"
          class="t-hint space_form__hint"
        >名称全局唯一，删除后不可复用</span>
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
        <span class="t-hint space_form__hint">预约模块按此时段做可用性校验</span>
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
import { SPACE_TYPES, SPACE_STATUS } from './space.constants'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  space: { type: Object, default: null }
})

const emit = defineEmits(['close', 'submit'])

const formRef = ref()
const submitting = ref(false)
const isEdit = computed(() => !!props.space)

const emptyForm = () => ({
  labName: '',
  labType: '',
  locationDetail: '',
  capacity: 40,
  facilities: '',
  openStart: '08:00',
  openEnd: '21:45',
  status: 'AVAILABLE'
})

const form = reactive(emptyForm())

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
    console.log('[场地表单] 打开：模式 =', props.space ? '编辑' : '新建', props.space ? `id=${props.space.id}` : '（无预填数据，独立新建流程）')
    Object.assign(form, emptyForm())
    if (props.space) {
      Object.assign(form, {
        labName: props.space.labName,
        labType: props.space.labType,
        locationDetail: props.space.locationDetail || '',
        capacity: props.space.capacity,
        facilities: props.space.facilities || '',
        openStart: props.space.openStart || '08:00',
        openEnd: props.space.openEnd || '21:45',
        status: props.space.status
      })
    }
  }
)

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    console.warn('[场地表单] 前端校验未通过，终止提交')
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
    // 名称全局唯一由后端唯一约束兜底（40900），新增流程不预加载任何已有场地数据
    console.log('[场地表单] 校验通过，提交：模式 =', isEdit.value ? '编辑' : '新建', ' payload =', payload)
    emit('submit', { id: props.space?.id || null, payload })
  } finally {
    submitting.value = false
  }
}

function onClosed() {
  console.log('[场地表单] 关闭')
  formRef.value?.resetFields()
}
</script>

<style scoped>
.space_form__select {
  width: 100%;
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
