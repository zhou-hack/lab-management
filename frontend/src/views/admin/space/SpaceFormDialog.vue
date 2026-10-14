<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑场地' : '新建场地'"
    width="640px"
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
        prop="spaceName"
      >
        <el-input
          v-model="form.spaceName"
          placeholder="如 A703 网络实训室"
          maxlength="128"
        />
        <span
          v-if="isEdit"
          class="t-hint space_form__hint"
        >名称全局唯一，删除后不可复用</span>
      </el-form-item>
      <div class="space_form__row">
        <el-form-item
          label="场地类型"
          prop="spaceType"
        >
          <el-select
            v-model="form.spaceType"
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
        prop="location"
      >
        <el-input
          v-model="form.location"
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
  spaceName: '',
  spaceType: '',
  location: '',
  capacity: 40,
  facilities: '',
  openStart: '08:00',
  openEnd: '21:00',
  status: 'AVAILABLE'
})

const form = reactive(emptyForm())

const rules = {
  spaceName: [{ required: true, message: '请输入场地名称', trigger: 'blur' }],
  spaceType: [{ required: true, message: '请选择场地类型', trigger: 'change' }],
  location: [{ required: true, message: '请输入位置', trigger: 'blur' }],
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
    Object.assign(form, emptyForm())
    if (props.space) {
      Object.assign(form, {
        spaceName: props.space.spaceName,
        spaceType: props.space.spaceType,
        location: props.space.location || '',
        capacity: props.space.capacity,
        facilities: props.space.facilities || '',
        openStart: props.space.openStart || '08:00',
        openEnd: props.space.openEnd || '21:00',
        status: props.space.status
      })
    }
  }
)

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = {
      spaceName: form.spaceName.trim(),
      spaceType: form.spaceType,
      location: form.location.trim(),
      capacity: form.capacity,
      facilities: form.facilities?.trim() || null,
      openStart: form.openStart,
      openEnd: form.openEnd,
      status: form.status
    }
    emit('submit', { id: props.space?.id || null, payload })
  } finally {
    submitting.value = false
  }
}

function onClosed() {
  formRef.value?.resetFields()
  ElMessage.closeAll()
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

@media (max-width: 640px) {
  .space_form__row {
    grid-template-columns: 1fr;
  }
}
</style>
