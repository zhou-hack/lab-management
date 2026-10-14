<template>
  <el-dialog
    v-model="visible"
    :title="action === 'teacher' ? '老师审批' : '管理员审批'"
    width="560px"
    destroy-on-close
  >
    <div
      v-if="booking"
      class="booking_approve__summary"
    >
      <p class="t-body">
        <span class="t-strong">{{ booking.equipmentName }}</span>
        （{{ booking.assetCode }}）
      </p>
      <p class="t-sub">
        申请人：{{ booking.applicantName }} ｜ 借用时间：{{ formatSh(booking.startAt) }} 至 {{ formatSh(booking.endAt) }}
      </p>
      <p class="t-sub">
        借用用途：{{ booking.purpose }}
      </p>
    </div>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
    >
      <el-form-item
        label="审批结论"
        prop="approved"
      >
        <el-radio-group v-model="form.approved">
          <el-radio :value="true">
            通过
          </el-radio>
          <el-radio :value="false">
            驳回
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item
        label="审批意见"
        prop="comment"
      >
        <el-input
          v-model="form.comment"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="驳回时必填"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">
        取消
      </el-button>
      <el-button
        type="primary"
        :loading="saving"
        @click="save"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApproveBooking, teacherApproveBooking } from '@/api/equipment'
import { formatSh } from '@/utils/datetime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  booking: { type: Object, default: null },
  /** teacher = 老师审批（第一级）；admin = 管理员审批（末级） */
  action: { type: String, default: 'teacher' }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const formRef = ref()
const saving = ref(false)
const form = reactive({ approved: true, comment: '' })

const rules = {
  approved: [{ required: true, message: '请选择审批结论', trigger: 'change' }],
  comment: [
    {
      validator: (rule, value, callback) => {
        if (!form.approved && !(value || '').trim()) {
          callback(new Error('驳回时必须填写审批意见'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

watch(
  () => props.modelValue,
  (value) => {
    if (value) {
      form.approved = true
      form.comment = ''
    }
  }
)

async function save() {
  if (!props.booking) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const payload = { approved: form.approved, comment: form.comment.trim() || null }
    if (props.action === 'teacher') {
      await teacherApproveBooking(props.booking.id, payload)
    } else {
      await adminApproveBooking(props.booking.id, payload)
    }
    ElMessage.success(form.approved ? '已通过' : '已驳回')
    visible.value = false
    emit('success')
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.booking_approve__summary {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: var(--r-md);
  background: var(--brand-grad-soft);
}

.booking_approve__summary p {
  margin: 2px 0;
}
</style>
