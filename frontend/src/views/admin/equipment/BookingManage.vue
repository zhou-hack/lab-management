<template>
  <div class="booking_manage">
    <el-tabs
      v-model="activeTab"
      @tab-change="onTabChange"
    >
      <el-tab-pane
        label="待管理员审批"
        name="pending"
      />
      <el-tab-pane
        label="全部单据"
        name="all"
      />
    </el-tabs>

    <div class="card">
      <div
        v-if="activeTab === 'all'"
        class="booking_manage__toolbar"
      >
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
        <el-button
          class="booking_manage__toolbar_action"
          :loading="loading"
          @click="load"
        >
          刷新
        </el-button>
      </div>
      <div class="table-scroll-x">
        <el-table
          v-loading="loading"
          :data="records"
        >
          <el-table-column
            prop="documentNo"
            label="单据号"
            min-width="150"
          />
          <el-table-column
            label="设备"
            min-width="170"
          >
            <template #default="{ row }">
              <div class="t-body">
                {{ row.equipmentName }}
              </div>
              <div class="t-hint">
                {{ row.assetCode }}
              </div>
            </template>
          </el-table-column>
          <el-table-column
            prop="applicantName"
            label="申请人"
            min-width="90"
          />
          <el-table-column
            label="指定老师"
            min-width="90"
          >
            <template #default="{ row }">
              {{ row.approvalTeacherName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="借用时间"
            min-width="160"
          >
            <template #default="{ row }">
              <div class="t-body">
                {{ formatSh(row.startAt) }}
              </div>
              <div class="t-hint">
                至 {{ formatSh(row.endAt) }}
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
            label="申请时间"
            min-width="150"
          >
            <template #default="{ row }">
              {{ formatSh(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="220"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING_ADMIN'"
                v-permission="'equipment:booking:admin_approve'"
                link
                type="primary"
                @click="openApprove(row)"
              >
                审批
              </el-button>
              <el-button
                v-if="row.status === 'APPROVED'"
                v-permission="'equipment:booking:checkout'"
                link
                type="primary"
                @click="openCheckout(row)"
              >
                借出确认
              </el-button>
              <el-button
                v-if="row.status === 'CHECKED_OUT'"
                v-permission="'equipment:booking:return'"
                link
                type="primary"
                @click="openReturn(row)"
              >
                归还确认
              </el-button>
              <el-button
                link
                type="primary"
                @click="openDetail(row)"
              >
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
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

    <booking-approve-dialog
      v-model="approveVisible"
      action="admin"
      :booking="activeRow"
      @success="load"
    />

    <!-- 借出确认 -->
    <el-dialog
      v-model="checkoutVisible"
      title="借出确认"
      width="520px"
      destroy-on-close
    >
      <el-alert
        title="须在借用时间窗口内确认借出；借出后设备转为「使用中」"
        type="info"
        :closable="false"
        class="booking_manage__alert"
      />
      <el-form label-width="80px">
        <el-form-item label="借出备注">
          <el-input
            v-model="checkoutNote"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="checkoutVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="actionSaving"
          @click="confirmCheckout"
        >
          确认借出
        </el-button>
      </template>
    </el-dialog>

    <!-- 归还确认 -->
    <el-dialog
      v-model="returnVisible"
      title="归还确认"
      width="520px"
      destroy-on-close
    >
      <el-alert
        title="登记为损坏时设备将转入「维护中」，且备注必填"
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
        <el-form-item label="归还状态">
          <el-radio-group v-model="returnForm.condition">
            <el-radio value="NORMAL">
              正常归还
            </el-radio>
            <el-radio value="DAMAGED">
              损坏
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="归还备注">
          <el-input
            v-model="returnForm.note"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="returnForm.condition === 'DAMAGED' ? '损坏时必填，请描述损坏情况' : '选填'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="actionSaving"
          @click="confirmReturn"
        >
          确认归还
        </el-button>
      </template>
    </el-dialog>

    <booking-detail-dialog
      v-model="detailVisible"
      :booking="activeRow"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkoutBooking, pageBookings, returnBooking } from '@/api/equipment'
import { formatSh } from '@/utils/datetime'
import { BOOKING_STATUS, BOOKING_STATUS_OPTIONS, enumMeta } from '@/utils/enums'
import BookingApproveDialog from '@/components/BookingApproveDialog.vue'
import BookingDetailDialog from '@/components/BookingDetailDialog.vue'

const activeTab = ref('pending')

const query = reactive({ scope: 'PENDING', status: '', pageNum: 1, pageSize: 20 })
const records = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize, scope: query.scope }
    if (query.status) {
      params.status = query.status
    }
    const data = await pageBookings(params)
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

function onTabChange(name) {
  query.scope = name === 'all' ? 'ALL' : 'PENDING'
  query.status = ''
  search()
}

/* ---------------- 审批 ---------------- */

const approveVisible = ref(false)
const activeRow = ref(null)

function openApprove(row) {
  activeRow.value = row
  approveVisible.value = true
}

/* ---------------- 借出确认 ---------------- */

const checkoutVisible = ref(false)
const checkoutNote = ref('')
const actionSaving = ref(false)

function openCheckout(row) {
  activeRow.value = row
  checkoutNote.value = ''
  checkoutVisible.value = true
}

async function confirmCheckout() {
  if (!activeRow.value) return
  actionSaving.value = true
  try {
    await checkoutBooking(activeRow.value.id, { note: checkoutNote.value.trim() || null })
    ElMessage.success('已确认借出')
    checkoutVisible.value = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    actionSaving.value = false
  }
}

/* ---------------- 归还确认 ---------------- */

const returnVisible = ref(false)
const returnFormRef = ref()
const returnForm = reactive({ condition: 'NORMAL', note: '' })
const returnRules = {
  condition: [{ required: true, message: '请选择归还状态', trigger: 'change' }],
  note: [
    {
      validator: (rule, value, callback) => {
        if (returnForm.condition === 'DAMAGED' && !(value || '').trim()) {
          callback(new Error('登记为损坏时必须填写备注'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function openReturn(row) {
  activeRow.value = row
  returnForm.condition = 'NORMAL'
  returnForm.note = ''
  returnVisible.value = true
}

async function confirmReturn() {
  if (!activeRow.value) return
  try {
    await returnFormRef.value.validate()
  } catch {
    return
  }
  actionSaving.value = true
  try {
    await returnBooking(activeRow.value.id, {
      condition: returnForm.condition,
      note: returnForm.note.trim() || null
    })
    ElMessage.success('已确认归还')
    returnVisible.value = false
    load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    actionSaving.value = false
  }
}

/* ---------------- 详情 ---------------- */

const detailVisible = ref(false)

function openDetail(row) {
  activeRow.value = row
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.booking_manage__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.booking_manage__filter {
  width: 160px;
}

/* 提高选择器特异性以覆盖 .el-button + .el-button 的相邻 margin */
.booking_manage__toolbar .booking_manage__toolbar_action {
  margin-left: auto;
}

.booking_manage__pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.booking_manage__alert {
  margin-bottom: 16px;
}
</style>
