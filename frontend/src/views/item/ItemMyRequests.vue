<template>
  <div class="page">
    <div class="toolbar">
      <el-button
        type="primary"
        @click="openCreate"
      >
        新建申领
      </el-button>
      <el-select
        v-model="query.status"
        clearable
        placeholder="全部状态"
        class="w180"
        @change="pager.search"
      >
        <el-option
          label="全部状态"
          value=""
        />
        <el-option
          v-for="(cfg, value) in myRequestStatusMap"
          :key="value"
          :label="cfg.label"
          :value="value"
        />
      </el-select>
      <el-button @click="pager.search">
        查询
      </el-button>
    </div>

    <el-alert
      :title="isStudent
        ? '申领流程：提交申领（需指定审批教师）→ 老师初审 → 管理员终审 → 批准后可领用'
        : '申领流程：提交申领 → 管理员终审 → 批准后可领用'"
      type="info"
      :closable="false"
      show-icon
      class="mb12"
    />

    <el-table
      v-loading="pager.loading"
      :data="pager.rows"
      border
    >
      <el-table-column
        prop="id"
        label="申领单号"
        width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="审批教师"
        width="110"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ displayName(row.ownerTeacherName, row.ownerTeacherId) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="purpose"
        label="用途"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="领取方式"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            v-if="row.pickupMode === 'PROXY_PICKUP'"
            type="warning"
            size="small"
          >
            学生代理领取
          </el-tag>
          <el-tag
            v-else
            type="info"
            size="small"
          >
            本人领取
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="120"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.status"
            :map="myRequestStatusMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="登记时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="220"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openDetail(row.id)"
          >
            详情
          </el-button>
          <el-button
            v-if="issuable(row)"
            link
            type="success"
            @click="openIssue(row)"
          >
            出库
          </el-button>
          <el-button
            v-if="returnable(row)"
            link
            type="warning"
            @click="openReturn(row)"
          >
            归还
          </el-button>
          <el-button
            v-if="cancellable(row)"
            link
            type="danger"
            @click="doCancel(row)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 新建申领 -->
    <el-dialog
      v-model="createVisible"
      title="新建物料申领"
      width="880px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item
          v-if="isStudent"
          label="审批教师"
          prop="ownerTeacherId"
        >
          <el-select
            v-model="form.ownerTeacherId"
            filterable
            placeholder="请选择审批教师（初审人）"
            class="w240"
          >
            <el-option
              v-for="t in teacherList"
              :key="t.id"
              :label="t.realName"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="!isStudent"
          label="领取方式"
          prop="pickupMode"
        >
          <el-radio-group v-model="form.pickupMode">
            <el-radio value="SELF_PICKUP">
              本人领取
            </el-radio>
            <el-radio value="PROXY_PICKUP">
              学生代理领取
            </el-radio>
          </el-radio-group>
          <div class="muted small">
            学生代理领取：终审前即可由持有效授权的助理学生在「物料代领」页按剩余量代领
          </div>
        </el-form-item>
        <el-form-item label="参与人数">
          <el-input-number
            v-model="form.participantCount"
            :min="0"
            :step="1"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="课时数">
          <el-input-number
            v-model="form.periodCount"
            :min="0"
            :step="1"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item
          label="用途"
          prop="purpose"
        >
          <el-input
            v-model="form.purpose"
            type="textarea"
            :rows="2"
            placeholder="如：有机化学实验课耗材"
          />
        </el-form-item>
        <el-form-item label="申领物料">
          <el-table
            :data="form.lines"
            border
          >
            <el-table-column
              label="物料"
              min-width="260"
            >
              <template #default="{ row }">
                <SkuSelect
                  v-model="row.skuId"
                  for-request
                  placeholder="输入编码/名称搜索物料"
                />
              </template>
            </el-table-column>
            <el-table-column
              label="申请量"
              width="170"
            >
              <template #default="{ row }">
                <el-input-number
                  v-model="row.requestedQty"
                  :min="0"
                  :step="1"
                  :precision="3"
                  controls-position="right"
                />
              </template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="70"
              align="center"
            >
              <template #default="{ $index }">
                <el-button
                  link
                  type="danger"
                  @click="form.lines.splice($index, 1)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            class="mt8"
            @click="form.lines.push({ skuId: '', requestedQty: null })"
          >
            + 添加行
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCreate"
        >
          提交申领
        </el-button>
      </template>
    </el-dialog>

    <!-- 申领详情 -->
    <el-dialog
      v-model="detailVisible"
      title="申领单详情"
      width="960px"
    >
      <el-descriptions
        v-if="detail"
        :column="3"
        border
      >
        <el-descriptions-item label="申领单号">
          {{ detail.id }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag
            :value="detail.status"
            :map="myRequestStatusMap"
          />
        </el-descriptions-item>
        <el-descriptions-item label="审批教师">
          {{ displayName(detail.ownerTeacherName, detail.ownerTeacherId) }}
        </el-descriptions-item>
        <el-descriptions-item label="参与人数">
          {{ detail.participantCount ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="课时数">
          {{ detail.periodCount ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="登记时间">
          {{ fmtShanghai(detail.createTime) }}
        </el-descriptions-item>
        <el-descriptions-item
          label="用途"
          :span="3"
        >
          {{ detail.purpose || '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <el-table
        v-if="detail"
        :data="detail.lines"
        border
        class="mt8"
      >
        <el-table-column
          label="物料"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.skuName }}（{{ row.baseUnit }}）
          </template>
        </el-table-column>
        <el-table-column
          label="申请量"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.requestedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="批准量"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.approvedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="已实发"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.fulfilledQty) }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
    <!-- 出库领取（生成领用出库单草稿，管理员审批过账后扣库存） -->
    <el-dialog
      v-model="issueVisible"
      title="出库领取"
      width="520px"
    >
      <el-form label-width="100px">
        <el-form-item
          label="出库库位"
          required
        >
          <LocationSelect
            v-model="issueForm.fromLocationId"
            placeholder="选择出库库位"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="issueForm.reason"
            type="textarea"
            :rows="2"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <div class="muted small">
        全部申领行按剩余批准量生成出库草稿单；管理员在「库存单据」审批并过账后库存扣减。
      </div>
      <template #footer>
        <el-button @click="issueVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitIssue"
        >
          生成出库单
        </el-button>
      </template>
    </el-dialog>

    <!-- 归还（生成归还单草稿，审批过账后回增库存） -->
    <el-dialog
      v-model="returnVisible"
      title="归还物料"
      width="820px"
    >
      <div class="muted small mb8">
        按已过账出库行归还，可多次部分归还；可归还量 = 出库量 − 已归还量。
        管理员在「库存单据」审批并过账后库存回增。
      </div>
      <el-table
        v-loading="returnLoading"
        :data="returnLines"
        border
      >
        <el-table-column
          label="物料"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.materialName }}（{{ row.baseUnit }}）
          </template>
        </el-table-column>
        <el-table-column
          label="出库量"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.issuedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="已归还"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.returnedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="可归还量"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.returnableQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="本次归还量"
          width="160"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.returnQty"
              :min="0"
              :max="Number(row.returnableQty)"
              :step="1"
              :precision="3"
              :disabled="Number(row.returnableQty) <= 0"
              controls-position="right"
            />
          </template>
        </el-table-column>
      </el-table>
      <el-form
        label-width="80px"
        class="mt12"
      >
        <el-form-item label="事由">
          <el-input
            v-model="returnReason"
            type="textarea"
            :rows="2"
            placeholder="选填，如：课后归还剩余连接线"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitReturn"
        >
          提交归还
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  cancelRequest,
  createRequest,
  getRequest,
  issueRequest,
  pageRequests,
  requestTeacherOptions,
  returnableLines,
  returnRequest
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { fmtShanghai, formatQty, nowUtcIso } from '@/utils/format'
import { useUserStore } from '@/store/user'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import SkuSelect from '@/components/SkuSelect.vue'
import LocationSelect from '@/components/LocationSelect.vue'

/**
 * 物料申领（老师/学生共用用户端页面）：
 * - 学生：必填审批教师，提交后待老师初审 → 老师初审通过 → 管理员终审
 * - 老师：无需审批教师（归属本人），提交后直接待管理员终审
 * - 初版不预约时段：offeringId/场地预约/课次均不填（无定额外快照）
 * 列表仅展示本人经办的申领单（operatorId = 当前用户，数据范围由后端兜底）
 */

/** 用户端状态文案（与全局 statusMap 区分：草稿即待老师初审） */
const myRequestStatusMap = {
  DRAFT: { label: '待老师初审', type: 'warning' },
  PENDING: { label: '待审批', type: 'warning' },
  TEACHER_APPROVED: { label: '待管理员终审', type: 'warning' },
  APPROVED: { label: '已批准', type: 'success' },
  PARTIALLY_FULFILLED: { label: '部分实发', type: 'warning' },
  FULFILLED: { label: '全部实发', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const store = useUserStore()
const isStudent = store.user?.userType === 'STUDENT'

const query = reactive({ status: '' })
const pager = usePagedList((params) =>
  pageRequests({
    ...params,
    status: query.status || undefined,
    operatorId: store.user?.userId
  })
)

const createVisible = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({
  ownerTeacherId: '',
  pickupMode: 'SELF_PICKUP',
  participantCount: null,
  periodCount: null,
  purpose: '',
  lines: []
})

const rules = {
  ownerTeacherId: isStudent
    ? [{ required: true, message: '请选择审批教师', trigger: 'change' }]
    : [],
  purpose: [{ required: true, message: '请填写用途', trigger: 'blur' }]
}

const teacherList = ref([])

const detailVisible = ref(false)
const detail = ref(null)

function displayName(name, id) {
  return name || id || '-'
}

function cancellable(row) {
  return row.status === 'DRAFT' || row.status === 'TEACHER_APPROVED' || row.status === 'APPROVED'
}

/** 已批准/部分实发可出库领取 */
function issuable(row) {
  return row.status === 'APPROVED' || row.status === 'PARTIALLY_FULFILLED'
}

/** 有实发记录（部分/全部实发）可归还 */
function returnable(row) {
  return row.status === 'PARTIALLY_FULFILLED' || row.status === 'FULFILLED'
}

/* ---------------- 出库领取 ---------------- */
const issueVisible = ref(false)
const issueForm = reactive({ requestId: null, fromLocationId: null, reason: '' })

function openIssue(row) {
  issueForm.requestId = row.id
  issueForm.fromLocationId = null
  issueForm.reason = ''
  issueVisible.value = true
}

async function submitIssue() {
  if (!issueForm.fromLocationId) {
    ElMessage.warning('请选择出库库位')
    return
  }
  submitting.value = true
  try {
    const docId = await issueRequest(issueForm.requestId, {
      occurredAt: nowUtcIso(),
      fromLocationId: issueForm.fromLocationId,
      reason: issueForm.reason || undefined
    })
    ElMessage.success(`已生成出库单 ${docId}，待管理员审批过账后库存扣减`)
    issueVisible.value = false
    pager.load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/* ---------------- 归还 ---------------- */
const returnVisible = ref(false)
const returnLoading = ref(false)
const returnRequestId = ref(null)
const returnLines = ref([])
const returnReason = ref('')

async function openReturn(row) {
  returnRequestId.value = row.id
  returnLines.value = []
  returnReason.value = ''
  returnVisible.value = true
  returnLoading.value = true
  try {
    const lines = await returnableLines(row.id)
    if (!lines || !lines.length) {
      ElMessage.warning('该申领单暂无已过账的出库记录，出库单过账后才能归还')
      returnVisible.value = false
      return
    }
    returnLines.value = lines.map((l) => ({
      ...l,
      returnQty: Number(l.returnableQty) > 0 ? Number(l.returnableQty) : 0
    }))
  } catch {
    returnVisible.value = false
  } finally {
    returnLoading.value = false
  }
}

async function submitReturn() {
  const taken = returnLines.value.filter((l) => l.returnQty > 0)
  if (!taken.length) {
    ElMessage.warning('请至少填写一行归还量大于 0 的物料')
    return
  }
  for (const line of taken) {
    if (line.returnQty > Number(line.returnableQty)) {
      ElMessage.warning(`「${line.materialName}」归还量超过可归还量`)
      return
    }
  }
  submitting.value = true
  try {
    const docId = await returnRequest(returnRequestId.value, {
      reason: returnReason.value || undefined,
      lines: taken.map((l) => ({ issueLineId: l.issueLineId, quantity: l.returnQty }))
    })
    ElMessage.success(`已生成归还单 ${docId}，待管理员审批过账后库存回增`)
    returnVisible.value = false
    pager.load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function openCreate() {
  form.ownerTeacherId = ''
  form.pickupMode = 'SELF_PICKUP'
  form.participantCount = null
  form.periodCount = null
  form.purpose = ''
  form.lines = [{ skuId: '', requestedQty: null }]
  createVisible.value = true
  if (isStudent && !teacherList.value.length) {
    try {
      teacherList.value = (await requestTeacherOptions()) || []
    } catch {
      // 失败提示由请求拦截器统一处理
    }
  }
}

async function submitCreate() {
  await formRef.value.validate().catch(() => Promise.reject(new Error('表单校验未通过')))
  if (!form.lines.length) {
    ElMessage.warning('请至少添加一行物料')
    return
  }
  for (const line of form.lines) {
    if (!line.skuId) {
      ElMessage.warning('存在未选择物料的行')
      return
    }
    if (line.requestedQty === null || line.requestedQty <= 0) {
      ElMessage.warning('申请量必须大于 0')
      return
    }
  }
  submitting.value = true
  try {
    const id = await createRequest({
      requestType: 'SELF',
      ownerTeacherId: isStudent ? form.ownerTeacherId : undefined,
      pickupMode: isStudent ? undefined : form.pickupMode,
      participantCount: form.participantCount ?? undefined,
      periodCount: form.periodCount ?? undefined,
      purpose: form.purpose,
      lines: form.lines.map((l) => ({ skuId: l.skuId, requestedQty: l.requestedQty }))
    })
    ElMessage.success(isStudent
      ? `申领已提交（单号 ${id}），待老师初审`
      : `申领已提交（单号 ${id}），待管理员终审`)
    createVisible.value = false
    pager.load()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function openDetail(id) {
  try {
    detail.value = await getRequest(id)
    detailVisible.value = true
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

async function doCancel(row) {
  const ok = await confirmAction('确认取消该申领单？未实发的申领可取消。', '取消确认')
  if (!ok) return
  try {
    await cancelRequest(row.id)
    ElMessage.success('已取消')
    pager.load()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

onMounted(pager.load)
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.w180 {
  width: 180px;
  flex: none;
}

.w240 {
  width: 240px;
}

.mt8 {
  margin-top: 8px;
}
</style>
