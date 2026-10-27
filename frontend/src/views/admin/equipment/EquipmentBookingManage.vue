<template>
  <div class="page booking_manage">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          设备使用审批
        </h2>
        <p class="t-sub">
          借出发放与归还登记 · 超期未还人工跟进
        </p>
      </div>
    </div>

    <div class="card">
      <div class="booking_manage__toolbar">
        <el-select
          v-model="query.status"
          class="booking_manage__filter"
          placeholder="全部状态"
          clearable
          @change="search"
        >
          <el-option
            v-for="option in BOOKING_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button @click="search">
          查询
        </el-button>
      </div>
    </div>

    <div class="card">
      <div
        v-if="loading && !records.length"
        class="booking_manage__skeleton"
      >
        <span class="skeleton booking_manage__sk_row" />
        <span class="skeleton booking_manage__sk_row" />
        <span class="skeleton booking_manage__sk_row" />
        <span class="skeleton booking_manage__sk_row" />
        <span class="skeleton booking_manage__sk_row" />
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
            label="审批教师"
            width="90"
          >
            <template #default="{ row }">
              {{ row.approvalTeacherName || '—' }}
            </template>
          </el-table-column>
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
            label="状态"
            width="110"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="enumMeta(row.status, BOOKING_STATUS).pill"
              >
                {{ enumMeta(row.status, BOOKING_STATUS).label }}
              </span>
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
            label="标记"
            width="100"
          >
            <template #default="{ row }">
              <el-tooltip
                v-if="row.status === 'OVERDUE'"
                content="已超预约归还时段未归还，申请人申请权限已冻结 7 天"
                placement="top"
              >
                <span class="pill pill--danger">逾期未还</span>
              </el-tooltip>
              <span
                v-else
                class="t-hint"
              >—</span>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="150"
            fixed="right"
          >
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING_ADMIN'">
                <el-button
                  v-permission="'equipment:booking:admin_review'"
                  link
                  type="primary"
                  @click="approve(row)"
                >
                  通过
                </el-button>
                <el-button
                  v-permission="'equipment:booking:admin_review'"
                  link
                  type="danger"
                  @click="openReject(row)"
                >
                  驳回
                </el-button>
              </template>
              <el-button
                v-else-if="row.status === 'APPROVED'"
                v-permission="'equipment:booking:checkout'"
                link
                type="primary"
                @click="checkout(row)"
              >
                借出登记
              </el-button>
              <el-button
                v-else-if="row.status === 'CHECKED_OUT' || row.status === 'OVERDUE'"
                v-permission="'equipment:booking:return'"
                link
                type="primary"
                @click="openReturn(row)"
              >
                归还登记
              </el-button>
              <span
                v-else
                class="t-hint"
              >—</span>
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
        暂无申请记录
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="booking_manage__pager"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </div>

    <!-- 终审/代批驳回（意见必填） -->
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

    <!-- 归还登记（完好恢复空闲；损坏备注必填转维护中） -->
    <el-dialog
      v-model="returnDialog.visible"
      :title="`归还登记${returnForm.documentNo ? `（${returnForm.documentNo}）` : ''}`"
      width="520px"
      destroy-on-close
    >
      <el-alert
        title="完好：设备恢复空闲；损坏：须填写备注，设备转入维护中并线下跟进维修"
        type="info"
        :closable="false"
        class="booking_manage__alert"
      />
      <el-form
        ref="returnFormRef"
        :model="returnForm"
        :rules="returnRules"
        label-width="80px"
      >
        <el-form-item
          label="归还状态"
          prop="condition"
        >
          <el-radio-group v-model="returnForm.condition">
            <el-radio value="NORMAL">
              完好
            </el-radio>
            <el-radio value="DAMAGED">
              损坏
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="归还备注"
          prop="note"
        >
          <el-input
            v-model="returnForm.note"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="returnForm.condition === 'DAMAGED' ? '损坏描述（必填）' : '备注（选填）'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="returnDialog.saving"
          @click="saveReturn"
        >
          确认归还
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets } from '@element-plus/icons-vue'
import {
  adminReview,
  checkoutBooking,
  pageAllBookings,
  returnBooking
} from '@/api/equipment'
import { BOOKING_STATUS, BOOKING_STATUS_OPTIONS, enumMeta } from '@/utils/enums'
import { formatSh } from '@/utils/datetime'

/**
 * 设备使用审批（管理端）：全部申请分页 + 状态筛选。
 * 操作按状态收敛——「待教师初审」单由教师初审，操作栏不显示通过/驳回；
 * 历史遗留 PENDING_ADMIN 单仍可终审/驳回；APPROVED 借出登记；
 * CHECKED_OUT 归还登记（损坏转维护中）。
 */
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: ''
})
const records = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.status) {
      params.status = query.status
    }
    const data = await pageAllBookings(params)
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

/* ---------------- 管理员终审 ---------------- */

async function approve(row) {
  try {
    await ElMessageBox.confirm(
      '终审通过后申请即生效，可登记借出发放。确认通过？',
      '终审通过',
      { type: 'info', confirmButtonText: '通过', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await adminReview(row.id, { action: 'approve' })
    ElMessage.success('已通过')
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

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
    await adminReview(rejectForm.id, {
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

/* ---------------- 借出发放 ---------------- */

async function checkout(row) {
  try {
    await ElMessageBox.confirm(
      `确认对「${row.equipmentName}（${row.assetCode}）」登记借出发放？设备将转为借出中。`,
      '借出登记',
      { type: 'warning', confirmButtonText: '确认借出', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await checkoutBooking(row.id)
    ElMessage.success('借出登记成功，设备已转为借出中')
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/* ---------------- 归还登记 ---------------- */

const returnDialog = reactive({ visible: false, saving: false })
const returnFormRef = ref()
const returnForm = reactive({ id: '', documentNo: '', condition: 'NORMAL', note: '' })
const returnRules = {
  condition: [{ required: true, message: '请选择归还状态', trigger: 'change' }],
  note: [
    {
      validator: (rule, value, callback) => {
        if (returnForm.condition === 'DAMAGED' && !(value || '').trim()) {
          callback(new Error('归还损坏必须填写备注说明'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function openReturn(row) {
  Object.assign(returnForm, {
    id: row.id,
    documentNo: row.documentNo,
    condition: 'NORMAL',
    note: ''
  })
  returnDialog.visible = true
}

async function saveReturn() {
  try {
    await returnFormRef.value.validate()
  } catch {
    return
  }
  returnDialog.saving = true
  try {
    await returnBooking(returnForm.id, {
      condition: returnForm.condition,
      note: returnForm.note.trim() || null
    })
    ElMessage.success(returnForm.condition === 'DAMAGED' ? '归还登记成功，设备已转入维护中' : '归还登记成功，设备已恢复空闲')
    returnDialog.visible = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    returnDialog.saving = false
  }
}

onMounted(load)
</script>

<style scoped>
.booking_manage__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.booking_manage__filter {
  width: 160px;
}

.booking_manage__alert {
  margin-bottom: 16px;
}

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
.booking_manage__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 2px 10px;
}

.booking_manage__sk_row {
  height: 36px;
  border-radius: var(--r-sm);
}

.booking_manage__pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
