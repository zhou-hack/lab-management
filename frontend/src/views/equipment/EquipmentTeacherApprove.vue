<template>
  <div class="page teacher_approve">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          设备申请初审
        </h2>
        <p class="t-sub">
          指定给您初审的学生申请 · 普通设备通过即生效，贵重设备通过后转管理员终审；超 24 小时未处理管理员可代批
        </p>
      </div>
    </div>

    <div class="card">
      <el-alert
        title="驳回须填写审批意见；审批通过与借出登记均会重验该时段设备占用，冲突时整单拒绝"
        type="info"
        :closable="false"
      />
    </div>

    <div class="card">
      <div
        v-if="loading && !records.length"
        class="teacher_approve__skeleton"
      >
        <span class="skeleton teacher_approve__sk_row" />
        <span class="skeleton teacher_approve__sk_row" />
        <span class="skeleton teacher_approve__sk_row" />
        <span class="skeleton teacher_approve__sk_row" />
        <span class="skeleton teacher_approve__sk_row" />
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
            prop="documentNo"
            label="单据编号"
            min-width="140"
          />
          <el-table-column
            prop="applicantName"
            label="申请人"
            width="90"
          />
          <el-table-column
            prop="equipmentName"
            label="设备名称"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="assetCode"
            label="资产编号"
            min-width="110"
          />
          <el-table-column
            label="价值分级"
            width="90"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="enumMeta(row.valueLevelSnapshot, VALUE_LEVEL).pill"
              >
                {{ enumMeta(row.valueLevelSnapshot, VALUE_LEVEL).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="使用时段"
            min-width="150"
          >
            <template #default="{ row }">
              <div class="booking_time">
                {{ formatSh(row.startAt) }}
              </div>
              <div class="booking_time booking_time--to">
                ~ {{ formatSh(row.endAt) }}
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="借用用途"
            min-width="160"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.purpose }}
            </template>
          </el-table-column>
          <el-table-column
            label="提交时间"
            width="140"
          >
            <template #default="{ row }">
              {{ formatSh(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column
            label="超时"
            width="90"
          >
            <template #default="{ row }">
              <el-tooltip
                v-if="isOvertime(row)"
                content="超 24 小时未初审，管理员可代批"
                placement="top"
              >
                <span class="pill pill--danger">超24h</span>
              </el-tooltip>
              <span
                v-else
                class="t-hint"
              >—</span>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="110"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-permission="'equipment:booking:teacher_review'"
                link
                type="primary"
                @click="approve(row)"
              >
                通过
              </el-button>
              <el-button
                v-permission="'equipment:booking:teacher_review'"
                link
                type="danger"
                @click="openReject(row)"
              >
                驳回
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
          <Tickets />
        </el-icon>
        暂无待初审申请
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="teacher_approve__pager"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </div>

    <!-- 驳回（意见必填） -->
    <el-dialog
      v-model="rejectDialog.visible"
      :title="`驳回申请${rejectForm.documentNo ? `（${rejectForm.documentNo}）` : ''}`"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="rejectFormRef"
        :model="rejectForm"
        :rules="rejectRules"
        label-width="80px"
      >
        <el-form-item
          label="驳回意见"
          prop="comment"
        >
          <el-input
            v-model="rejectForm.comment"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请填写驳回原因（必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="rejectDialog.saving"
          @click="saveReject"
        >
          确认驳回
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import { pageTeacherPending, teacherReview } from '@/api/equipment'
import { VALUE_LEVEL, enumMeta } from '@/utils/enums'
import { formatSh } from '@/utils/datetime'

/**
 * 设备申请初审（教师端）：指定审批教师为本人、状态固定 PENDING_TEACHER 的待办分页。
 * 通过 → 普通即生效、贵重转管理员终审；驳回意见必填。
 */
const REVIEW_TIMEOUT_MS = 24 * 60 * 60 * 1000

const query = reactive({
  pageNum: 1,
  pageSize: 10
})
const records = ref([])
const total = ref(0)
const loading = ref(false)

/** 加载时刻的当前时间：超时标记按页面数据快照计算 */
let loadedAt = Date.now()

function isOvertime(row) {
  const time = new Date(row.createTime).getTime()
  return Number.isFinite(time) && loadedAt - time > REVIEW_TIMEOUT_MS
}

async function load() {
  loading.value = true
  loadedAt = Date.now()
  try {
    const data = await pageTeacherPending({ pageNum: query.pageNum, pageSize: query.pageSize })
    records.value = data.records || []
    total.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function approve(row) {
  const toAdmin = row.valueLevelSnapshot === 'VALUABLE'
  try {
    await ElMessageBox.confirm(
      toAdmin
        ? '该申请为贵重设备，初审通过后将转管理员终审。确认通过？'
        : '初审通过后普通设备申请即生效。确认通过？',
      '初审通过',
      { type: 'info', confirmButtonText: '通过', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await teacherReview(row.id, { action: 'approve' })
    ElMessage.success(toAdmin ? '已通过，转管理员终审' : '已通过')
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/* ---------------- 驳回 ---------------- */

const rejectDialog = reactive({ visible: false, saving: false })
const rejectFormRef = ref()
const rejectForm = reactive({ id: '', documentNo: '', comment: '' })
const rejectRules = {
  comment: [{ required: true, message: '请填写驳回意见', trigger: 'blur' }]
}

function openReject(row) {
  Object.assign(rejectForm, {
    id: row.id,
    documentNo: row.documentNo,
    comment: ''
  })
  rejectDialog.visible = true
}

async function saveReject() {
  try {
    await rejectFormRef.value.validate()
  } catch {
    return
  }
  rejectDialog.saving = true
  try {
    await teacherReview(rejectForm.id, {
      action: 'reject',
      comment: rejectForm.comment.trim()
    })
    ElMessage.success('已驳回')
    rejectDialog.visible = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    rejectDialog.saving = false
  }
}

onMounted(load)
</script>

<style scoped>
/* 时段两行展示：结束行弱化 */
.booking_time {
  line-height: 1.5;
  white-space: nowrap;
}

.booking_time--to {
  color: var(--g3);
  font-size: 12px;
}

/* 加载骨架：模拟表格行位（规范 7.8） */
.teacher_approve__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 2px 10px;
}

.teacher_approve__sk_row {
  height: 36px;
  border-radius: var(--r-sm);
}

.teacher_approve__pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
