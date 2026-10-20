<template>
  <div class="page equipment_directory">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          设备目录
        </h2>
        <p class="t-sub">
          在架可申请设备一览 · 学生单由指定教师初审，贵重设备初审后转管理员终审
        </p>
      </div>
    </div>

    <div class="card">
      <div class="equipment_directory__toolbar">
        <el-input
          v-model="query.keyword"
          class="equipment_directory__filter"
          placeholder="资产编号 / 序列号 / 名称"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-button @click="search">
          查询
        </el-button>
      </div>
    </div>

    <div class="card">
      <div
        v-if="loading && !records.length"
        class="equipment_directory__skeleton"
      >
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row" />
      </div>
      <div
        v-else-if="records.length"
        class="table-scroll-x"
      >
        <el-table
          v-loading="loading"
          :data="records"
        >
          <el-table-column
            prop="assetCode"
            label="资产编号"
            min-width="110"
          />
          <el-table-column
            prop="equipmentName"
            label="设备名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="modelCode"
            label="型号码"
            min-width="110"
          />
          <el-table-column
            prop="departmentName"
            label="归口部门"
            min-width="110"
          />
          <el-table-column
            label="存放位置"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.locationDetail || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="保管人"
            width="90"
          >
            <template #default="{ row }">
              {{ row.custodianName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="价值分级"
            width="90"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="valueLevelMeta(row).pill"
              >
                {{ valueLevelMeta(row).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="110"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-permission="'equipment:booking:apply'"
                link
                type="primary"
                @click="openApply(row)"
              >
                申请使用
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div
        v-else
        class="empty"
      >
        <el-icon
          class="empty__icon"
          :size="40"
        >
          <Monitor />
        </el-icon>
        暂无在架设备
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="equipment_directory__pager"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </div>

    <!-- 申请使用 -->
    <el-dialog
      v-model="applyDialog.visible"
      :title="`申请使用${applyForm.assetCode ? `（${applyForm.assetCode}）` : ''}`"
      width="560px"
      destroy-on-close
    >
      <el-alert
        class="equipment_directory__alert"
        title="普通设备审批通过即生效；贵重设备初审通过后转管理员终审；提交与审批均校验时段占用"
        type="info"
        :closable="false"
      />
      <el-form
        ref="applyFormRef"
        :model="applyForm"
        :rules="applyRules"
        label-width="100px"
      >
        <el-form-item label="设备">
          {{ applyForm.equipmentName }}
        </el-form-item>
        <el-form-item
          label="使用时段"
          prop="range"
        >
          <el-date-picker
            v-model="applyForm.range"
            class="equipment_directory__range"
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
            v-model="applyForm.approvalTeacherId"
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
            v-model="applyForm.purpose"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请填写用途说明"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="applyDialog.saving"
          @click="saveApply"
        >
          提交申请
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Monitor } from '@element-plus/icons-vue'
import { applyBooking, pageAssets, teacherOptions } from '@/api/equipment'
import { VALUE_LEVEL, enumMeta } from '@/utils/enums'
import { toShOffsetIso } from '@/utils/datetime'
import { useUserStore } from '@/store/user'

/**
 * 设备目录（学生/教师用户端）：只读在架目录（服务端收敛为 IDLE 且启用视图），
 * 「申请使用」打开申请弹窗；学生单须指定审批教师（按登录人 userType 区分）。
 */
const store = useUserStore()
const isStudent = store.user?.userType === 'STUDENT'

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: ''
})
const records = ref([])
const total = ref(0)
const loading = ref(false)

function valueLevelMeta(row) {
  return row.valueLevel
    ? enumMeta(row.valueLevel, VALUE_LEVEL)
    : { label: '待分类', pill: 'pill--danger' }
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.keyword.trim()) {
      params.keyword = query.keyword.trim()
    }
    const data = await pageAssets(params)
    records.value = data.records || []
    total.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

/* ---------------- 申请使用 ---------------- */

const applyDialog = reactive({ visible: false, saving: false })
const applyFormRef = ref()
const applyForm = reactive({
  assetId: '',
  assetCode: '',
  equipmentName: '',
  range: null,
  approvalTeacherId: '',
  purpose: ''
})

const applyRules = {
  range: [{ required: true, message: '请选择使用时段', trigger: 'change' }],
  approvalTeacherId: isStudent
    ? [{ required: true, message: '请选择审批教师', trigger: 'change' }]
    : [],
  purpose: [{ required: true, message: '请填写借用用途', trigger: 'blur' }]
}

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

function openApply(row) {
  Object.assign(applyForm, {
    assetId: row.id,
    assetCode: row.assetCode,
    equipmentName: row.equipmentName,
    range: null,
    approvalTeacherId: '',
    purpose: ''
  })
  applyDialog.visible = true
  loadTeacherOptions()
}

async function saveApply() {
  try {
    await applyFormRef.value.validate()
  } catch {
    return
  }
  const [start, end] = applyForm.range || []
  if (!start || !end) {
    return
  }
  applyDialog.saving = true
  try {
    await applyBooking({
      assetId: applyForm.assetId,
      purpose: applyForm.purpose.trim(),
      startAt: toShOffsetIso(start),
      endAt: toShOffsetIso(end),
      approvalTeacherId: isStudent ? applyForm.approvalTeacherId : null
    })
    ElMessage.success('申请已提交，可在「我的设备申请」中查看进度')
    applyDialog.visible = false
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    applyDialog.saving = false
  }
}

onMounted(load)
</script>

<style scoped>
.equipment_directory__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.equipment_directory__filter {
  width: 240px;
}

.equipment_directory__alert {
  margin-bottom: 16px;
}

/* 时段选择占满表单行 */
.equipment_directory__range {
  width: 100%;
}

/* 加载骨架：模拟表格行位（规范 7.8） */
.equipment_directory__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 2px 10px;
}

.equipment_directory__sk_row {
  height: 36px;
  border-radius: var(--r-sm);
}

.equipment_directory__pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
