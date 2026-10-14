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
      class="lab_form"
    >
      <el-form-item label="所属校区">
        <el-select
          v-model="form.campusId"
          placeholder="请选择校区"
          class="lab_form__select"
          :disabled="isEdit"
          @change="onCampusChange"
        >
          <el-option
            v-for="c in campusOptions"
            :key="c.id"
            :label="c.label"
            :value="c.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="所属楼栋"
        prop="buildingId"
      >
        <el-select
          v-model="form.buildingId"
          placeholder="请先选择校区"
          class="lab_form__select"
          :disabled="isEdit"
        >
          <el-option
            v-for="b in buildingOptions"
            :key="b.id"
            :label="b.label"
            :value="b.id"
          />
        </el-select>
        <span
          v-if="isEdit"
          class="t-hint lab_form__lock"
        >一期楼栋不可变更（Q3）</span>
      </el-form-item>
      <el-form-item
        label="归口部门"
        prop="departmentId"
      >
        <el-select
          v-model="form.departmentId"
          placeholder="请选择归口部门"
          class="lab_form__select"
        >
          <el-option
            v-for="d in departmentOptions"
            :key="d.id"
            :label="d.name"
            :value="d.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="场地编码"
        prop="labCode"
      >
        <el-input
          v-model="form.labCode"
          placeholder="如 A703，字母/数字/下划线/短横线"
          :disabled="isEdit"
          maxlength="64"
        />
        <span
          v-if="isEdit"
          class="t-hint lab_form__lock"
        >编码创建后不可修改</span>
      </el-form-item>
      <el-form-item
        label="场地名称"
        prop="labName"
      >
        <el-input
          v-model="form.labName"
          placeholder="如 网络实训室"
          maxlength="128"
        />
      </el-form-item>
      <el-form-item
        label="场地类型"
        prop="labType"
      >
        <el-select
          v-model="form.labType"
          placeholder="请选择类型"
          class="lab_form__select"
        >
          <el-option
            v-for="t in LAB_TYPES"
            :key="t.value"
            :label="t.label"
            :value="t.value"
          />
        </el-select>
        <span
          v-if="form.labType === 'OTHER'"
          class="t-hint lab_form__lock"
        >
          其他/待分类场地可建档维护，但不开放预约
        </span>
      </el-form-item>
      <div class="lab_form__row">
        <el-form-item
          label="楼层"
          prop="floorNo"
        >
          <el-input
            v-model="form.floorNo"
            placeholder="如 F7 / B1"
            maxlength="16"
          />
        </el-form-item>
        <el-form-item
          label="房间号"
          prop="roomNo"
        >
          <el-input
            v-model="form.roomNo"
            placeholder="如 703"
            maxlength="64"
          />
        </el-form-item>
      </div>
      <div class="lab_form__row">
        <el-form-item
          label="容纳人数"
          prop="capacity"
        >
          <el-input-number
            v-model="form.capacity"
            :min="1"
            :max="9999"
            controls-position="right"
            class="lab_form__num"
          />
        </el-form-item>
        <el-form-item
          label="面积(㎡)"
          prop="areaSqm"
        >
          <el-input-number
            v-model="form.areaSqm"
            :min="0"
            :precision="2"
            :step="10"
            controls-position="right"
            class="lab_form__num"
          />
        </el-form-item>
      </div>
      <el-form-item
        label="位置描述"
        prop="locationNote"
      >
        <el-input
          v-model="form.locationNote"
          type="textarea"
          :rows="2"
          placeholder="选填，如 A楼七层电梯右转"
          maxlength="2000"
        />
      </el-form-item>
      <el-form-item
        label="启用状态"
        prop="enabled"
      >
        <el-switch
          v-model="enabledBool"
          active-text="启用"
          inactive-text="停用"
          inline-prompt
        />
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
import { LAB_TYPES } from './space.constants'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  lab: { type: Object, default: null },
  campusOptions: { type: Array, default: () => [] },
  buildingOptions: { type: Array, default: () => [] },
  departmentOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['close', 'submit'])

const formRef = ref()
const submitting = ref(false)
const isEdit = computed(() => !!props.lab)

const emptyForm = () => ({
  campusId: null,
  buildingId: null,
  departmentId: '1',
  labCode: '',
  labName: '',
  labType: '',
  floorNo: '',
  roomNo: '',
  capacity: 40,
  areaSqm: null,
  locationNote: '',
  enabled: 1
})

const form = reactive(emptyForm())

const enabledBool = computed({
  get: () => form.enabled === 1,
  set: (v) => {
    form.enabled = v ? 1 : 0
  }
})

const rules = {
  buildingId: [{ required: true, message: '请选择所属楼栋', trigger: 'change' }],
  departmentId: [{ required: true, message: '请选择归口部门', trigger: 'change' }],
  labCode: [
    { required: true, message: '请输入场地编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: '仅允许字母、数字、下划线、短横线', trigger: 'blur' }
  ],
  labName: [{ required: true, message: '请输入场地名称', trigger: 'blur' }],
  labType: [{ required: true, message: '请选择场地类型', trigger: 'change' }],
  floorNo: [{ required: true, message: '请输入楼层', trigger: 'blur' }],
  roomNo: [{ required: true, message: '请输入房间号', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容纳人数', trigger: 'change' }]
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    Object.assign(form, emptyForm())
    if (props.lab) {
      Object.assign(form, {
        campusId: props.lab.campusId,
        buildingId: props.lab.buildingId,
        departmentId: props.lab.departmentId,
        labCode: props.lab.labCode,
        labName: props.lab.labName,
        labType: props.lab.labType,
        floorNo: props.lab.floorNo,
        roomNo: props.lab.roomNo,
        capacity: props.lab.capacity,
        areaSqm: props.lab.areaSqm,
        locationNote: props.lab.locationNote || '',
        enabled: props.lab.enabled
      })
    }
  }
)

function onCampusChange() {
  form.buildingId = null
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = {
      buildingId: form.buildingId,
      departmentId: form.departmentId,
      labCode: form.labCode.trim(),
      labName: form.labName.trim(),
      labType: form.labType,
      floorNo: form.floorNo.trim(),
      roomNo: form.roomNo.trim(),
      capacity: form.capacity,
      areaSqm: form.areaSqm,
      locationNote: form.locationNote?.trim() || null,
      enabled: form.enabled
    }
    // 编辑时 buildingId/labCode 由后端忽略，但保持契约清晰仍随表单带出
    emit('submit', { id: props.lab?.id || null, payload })
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
.lab_form__select {
  width: 100%;
}

.lab_form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.lab_form__num {
  width: 100%;
}

.lab_form__lock {
  display: block;
  margin-top: 4px;
}

@media (max-width: 640px) {
  .lab_form__row {
    grid-template-columns: 1fr;
  }
}
</style>
