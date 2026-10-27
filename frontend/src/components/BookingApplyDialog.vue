<template>
  <el-dialog
    v-model="visible"
    :title="preset ? `申请使用（${preset.assetCode}）` : '申请使用设备'"
    width="560px"
    destroy-on-close
  >
    <el-alert
      class="booking_apply__alert"
      title="学生申请须指定审批教师，经教师初审后由管理员终审；教师申请直接进入管理员终审。学生/教师可同时段申请，实际占用以管理员终审与借出登记时的校验为准"
      type="info"
      :closable="false"
    />
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item
        v-if="preset"
        label="设备"
      >
        {{ preset.equipmentName }}
      </el-form-item>
      <el-form-item
        v-else
        label="设备"
        prop="assetId"
      >
        <el-select
          v-model="form.assetId"
          class="booking_apply__asset"
          filterable
          remote
          :remote-method="searchEquipment"
          :loading="assetLoading"
          placeholder="输入资产编号 / 设备名称搜索"
        >
          <el-option
            v-for="asset in assetOptions"
            :key="asset.id"
            :label="`${asset.equipmentName}（${asset.assetCode}）`"
            :value="asset.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="使用时段"
        prop="range"
      >
        <el-date-picker
          v-model="form.range"
          class="booking_apply__range"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
      </el-form-item>
      <el-form-item
        v-if="isStudent"
        label="审批教师"
        prop="approvalTeacherId"
      >
        <el-select
          v-model="form.approvalTeacherId"
          filterable
          placeholder="请选择审批教师"
        >
          <el-option
            v-for="teacher in teacherList"
            :key="teacher.id"
            :label="teacher.realName"
            :value="teacher.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="借用用途"
        prop="purpose"
      >
        <el-input
          v-model="form.purpose"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="请填写用途说明"
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
        @click="submit"
      >
        提交申请
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { applyBooking, pageAssets, teacherOptions } from '@/api/equipment'
import { toShOffsetIso } from '@/utils/datetime'
import { useUserStore } from '@/store/user'

/**
 * 设备使用申请弹窗（设备目录 / 我的设备申请共用）。
 * - preset 传入时申请指定设备（目录行入口）；否则展示远程搜索下拉，
 *   按关键字查在架目录（服务端收敛为 IDLE 启用视图）
 * - 学生单须指定审批教师（按登录人 userType 区分）
 * - 提交成功后 emit('success')，由调用方刷新列表
 */
const props = defineProps({
  /** 预选资产 { id, assetCode, equipmentName }；不传则弹窗内搜索选择设备 */
  preset: { type: Object, default: null }
})
const emit = defineEmits(['success'])

const store = useUserStore()
const isStudent = store.userInfo?.userType === 'STUDENT'

const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  assetId: '',
  range: null,
  approvalTeacherId: '',
  purpose: ''
})

const rules = {
  assetId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  range: [{ required: true, message: '请选择使用时段', trigger: 'change' }],
  approvalTeacherId: isStudent
    ? [{ required: true, message: '请选择审批教师', trigger: 'change' }]
    : [],
  purpose: [{ required: true, message: '请填写借用用途', trigger: 'blur' }]
}

/* ---------------- 设备远程搜索（无预选资产时） ---------------- */

const assetOptions = ref([])
const assetLoading = ref(false)

async function searchEquipment(keyword = '') {
  assetLoading.value = true
  try {
    // 默认拉取 15 条在架设备；输入关键字搜索时扩大拉取量（服务端收敛为 IDLE 启用视图）
    const params = { pageNum: 1, pageSize: 15 }
    const text = (keyword || '').trim()
    if (text) {
      params.keyword = text
      params.pageSize = 50
    }
    const data = await pageAssets(params)
    assetOptions.value = data.records || []
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    assetLoading.value = false
  }
}

/* ---------------- 审批教师下拉（学生单） ---------------- */

const teacherList = ref([])
let teacherOptionsLoaded = false

async function loadTeacherOptions() {
  if (!isStudent || teacherOptionsLoaded) {
    return
  }
  teacherOptionsLoaded = true
  try {
    teacherList.value = (await teacherOptions()) || []
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/* ---------------- 打开与提交 ---------------- */

/** 打开弹窗并重置表单 */
async function open() {
  form.assetId = props.preset ? props.preset.id : ''
  form.range = null
  form.approvalTeacherId = ''
  form.purpose = ''
  visible.value = true
  if (!props.preset) {
    await searchEquipment()
  }
  loadTeacherOptions()
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  const [start, end] = form.range || []
  if (!start || !end) {
    return
  }
  saving.value = true
  try {
    await applyBooking({
      assetId: form.assetId,
      purpose: form.purpose.trim(),
      startAt: toShOffsetIso(start),
      endAt: toShOffsetIso(end),
      approvalTeacherId: isStudent ? form.approvalTeacherId : null
    })
    ElMessage.success('申请已提交，可在「我的设备申请」中查看进度')
    visible.value = false
    emit('success')
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.booking_apply__alert {
  margin-bottom: 16px;
}

/* 时段选择与设备下拉占满表单行 */
.booking_apply__range,
.booking_apply__asset {
  width: 100%;
}
</style>
