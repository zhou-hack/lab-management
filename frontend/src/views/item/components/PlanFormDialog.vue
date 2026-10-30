<template>
  <el-dialog
    :model-value="modelValue"
    :title="planId ? '编辑预设方案' : '新建预设方案'"
    width="1080px"
    :close-on-click-modal="false"
    :before-close="beforeClose"
    @update:model-value="(visible) => !visible && !submitting && emit('close')"
  >
    <div
      v-loading="loading"
      class="plan_form"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent
      >
        <div class="plan_form__basic">
          <el-form-item
            label="方案名称"
            prop="planName"
            class="plan_form__name"
          >
            <el-input
              v-model="form.planName"
              maxlength="128"
              show-word-limit
              placeholder="如：电路实验基础物料包"
            />
          </el-form-item>
          <el-form-item
            label="启用状态"
            prop="enabled"
            class="plan_form__enabled"
          >
            <el-radio-group v-model="form.enabled">
              <el-radio-button :value="1">
                启用
              </el-radio-button>
              <el-radio-button :value="0">
                停用
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-form-item
          label="备注"
          prop="remark"
        >
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="512"
            show-word-limit
            placeholder="补充适用课程、实验或注意事项（选填）"
          />
        </el-form-item>
        <el-form-item
          label="方案物料"
          required
        >
          <MaterialCatalogCart
            v-model="lines"
            mode="plan"
            :load-catalog="loadPlanCatalog"
            :active="modelValue"
            quantity-field="defaultQuantity"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button
        :disabled="submitting"
        @click="emit('close')"
      >
        取消
      </el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="loading"
        @click="submit"
      >
        保存方案
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import MaterialCatalogCart from './MaterialCatalogCart.vue'
import { buildPlanPayload, getPlanLinesError, normalizePlanLines } from './plan.rules'
import { createMaterialPlan, getMaterialPlan, getPlanMaterials, updateMaterialPlan } from '@/api/item/plan'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  planId: { type: [String, Number], default: '' }
})
const emit = defineEmits(['close', 'saved'])

const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const lines = ref([])
const form = reactive({
  planName: '',
  enabled: 1,
  remark: ''
})
const rules = {
  planName: [
    { required: true, message: '请填写方案名称', trigger: 'blur' },
    { max: 128, message: '方案名称不能超过 128 字', trigger: 'blur' }
  ],
  enabled: [{ required: true, message: '请选择启用状态', trigger: 'change' }],
  remark: [{ max: 512, message: '备注不能超过 512 字', trigger: 'blur' }]
}

let initializeSerial = 0

function loadPlanCatalog(params) {
  return getPlanMaterials(params)
}

async function initialize() {
  const serial = ++initializeSerial
  form.planName = ''
  form.enabled = 1
  form.remark = ''
  lines.value = []
  loading.value = false
  formRef.value?.clearValidate()
  if (!props.planId) return
  loading.value = true
  try {
    const plan = await getMaterialPlan(String(props.planId))
    if (serial !== initializeSerial) return
    form.planName = plan.planName || ''
    form.enabled = Number(plan.enabled)
    form.remark = plan.remark || ''
    lines.value = normalizePlanLines(plan.lines)
  } finally {
    if (serial === initializeSerial) loading.value = false
  }
}

function validateLines() {
  const error = getPlanLinesError(lines.value)
  if (error) {
    ElMessage.warning(error)
    return false
  }
  return true
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (!validateLines()) return
  const payload = buildPlanPayload(form, lines.value)
  submitting.value = true
  try {
    const saved = props.planId
      ? await updateMaterialPlan(String(props.planId), payload)
      : await createMaterialPlan(payload)
    ElMessage.success(props.planId ? '方案已更新' : '方案已创建')
    emit('saved', saved)
    emit('close')
  } finally {
    submitting.value = false
  }
}

function beforeClose(done) {
  if (!submitting.value) done()
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) initialize()
    else {
      initializeSerial += 1
      loading.value = false
    }
  }
)
</script>

<style scoped>
.plan_form {
  min-height: 260px;
}

.plan_form__basic {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 230px;
  gap: 16px;
}

.plan_form__name,
.plan_form__enabled {
  min-width: 0;
}

@media (max-width: 640px) {
  .plan_form__basic {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
