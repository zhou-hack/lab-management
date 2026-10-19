<template>
  <div>
    <div class="toolbar">
      <el-select
        v-model="query.status"
        clearable
        placeholder="全部状态"
        @change="pager.search"
      >
        <el-option
          label="全部状态"
          value=""
        />
        <el-option
          v-for="(cfg, value) in requestStatusMap"
          :key="value"
          :label="cfg.label"
          :value="value"
        />
      </el-select>
      <el-select
        v-model="query.requestType"
        clearable
        placeholder="全部申领类型"
        @change="pager.search"
      >
        <el-option
          label="全部申领类型"
          value=""
        />
        <el-option
          v-for="(cfg, value) in requestTypeMap"
          :key="value"
          :label="cfg.label"
          :value="value"
        />
      </el-select>
      <el-input
        v-model="query.offeringId"
        clearable
        class="w220"
        placeholder="开课实例 ID 过滤"
        @change="pager.search"
      />
      <el-button
        v-permission="'item:request:create'"
        type="primary"
        @click="openCreate"
      >
        新建申领单
      </el-button>
      <el-button @click="pager.search">
        查询
      </el-button>
    </div>

    <el-table
      v-loading="pager.loading"
      :data="pager.rows"
      border
    >
      <el-table-column
        prop="id"
        label="申领单 ID"
        width="190"
        show-overflow-tooltip
      />
      <el-table-column
        label="申领类型"
        width="120"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.requestType"
            :map="requestTypeMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="责任老师"
        width="120"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ displayName(row.ownerTeacherName, row.ownerTeacherId) }}
        </template>
      </el-table-column>
      <el-table-column
        label="代领人"
        width="120"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ displayName(row.operatorName, row.operatorId) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="offeringId"
        label="开课实例 ID"
        width="170"
        show-overflow-tooltip
      />
      <el-table-column
        prop="purpose"
        label="用途"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        label="状态"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.status"
            :map="requestStatusMap"
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
        width="300"
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
          <!-- 草稿：学生个人申领须老师审核（第一级） -->
          <template v-if="row.status === 'DRAFT'">
            <el-button
              v-permission="'item:request:teacher_approve'"
              link
              type="success"
              @click="openApprove(row, 'teacher')"
            >
              老师审核
            </el-button>
            <el-button
              v-permission="'item:request:teacher_approve'"
              link
              type="warning"
              @click="doReject(row, 'teacher')"
            >
              老师拒绝
            </el-button>
          </template>
          <!-- 老师已审核：管理员终审（第二级；代理代领单创建即此状态，直接终审） -->
          <template v-if="row.status === 'TEACHER_APPROVED'">
            <el-button
              v-permission="'item:request:approve'"
              link
              type="success"
              @click="openApprove(row, 'final')"
            >
              终审
            </el-button>
            <el-button
              v-permission="'item:request:approve'"
              link
              type="warning"
              @click="doReject(row, 'final')"
            >
              终审拒绝
            </el-button>
          </template>
          <el-button
            v-if="row.status === 'DRAFT' || row.status === 'TEACHER_APPROVED' || row.status === 'APPROVED'"
            v-permission="'item:request:cancel'"
            link
            type="danger"
            @click="doCancel(row)"
          >
            取消
          </el-button>
          <el-button
            v-if="row.status === 'APPROVED' || row.status === 'PARTIALLY_FULFILLED'"
            v-permission="'item:request:issue'"
            link
            type="success"
            @click="openIssue(row)"
          >
            实发
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 申领详情 -->
    <el-dialog
      v-model="detailVisible"
      title="申领单详情"
      width="1040px"
    >
      <el-descriptions
        v-if="detail"
        :column="3"
        border
      >
        <el-descriptions-item label="申领单 ID">
          {{ detail.id }}
        </el-descriptions-item>
        <el-descriptions-item label="申领类型">
          <StatusTag
            :value="detail.requestType"
            :map="requestTypeMap"
          />
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag
            :value="detail.status"
            :map="requestStatusMap"
          />
        </el-descriptions-item>
        <el-descriptions-item label="责任老师">
          {{ displayName(detail.ownerTeacherName, detail.ownerTeacherId) }}
        </el-descriptions-item>
        <el-descriptions-item label="代领人">
          {{ displayName(detail.operatorName, detail.operatorId) }}
        </el-descriptions-item>
        <el-descriptions-item label="源申领单 ID">
          {{ detail.sourceRequestId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="开课实例 ID">
          {{ detail.offeringId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="参与人数">
          {{ detail.participantCount }}
        </el-descriptions-item>
        <el-descriptions-item label="课时数">
          {{ detail.periodCount }}
        </el-descriptions-item>
        <el-descriptions-item label="用途">
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
          label="行 ID（可复制）"
          width="200"
        >
          <template #default="{ row }">
            <span class="small">{{ row.id }}</span>
            <el-button
              link
              type="primary"
              class="ml4"
              @click="copyText(row.id)"
            >
              复制
            </el-button>
          </template>
        </el-table-column>
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
          label="源申领行 ID"
          width="190"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.sourceLineId || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          label="申请量"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.requestedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="定额快照"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.quotaQtySnapshot) }}
          </template>
        </el-table-column>
        <el-table-column
          label="超定额"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.requiresExtraApproval"
              type="danger"
            >
              需额外审批
            </el-tag>
            <el-tag
              v-else
              type="success"
            >
              否
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="双签"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.requiresDualSign"
              type="warning"
            >
              双签
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="批准量"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.approvedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="已实发"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.fulfilledQty) }}
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 新建申领（本人申领 / 学生代理代领） -->
    <el-dialog
      v-model="createVisible"
      title="新建申领单"
      width="960px"
    >
      <el-form label-width="110px">
        <el-form-item
          label="申领类型"
          required
        >
          <el-select
            v-model="form.requestType"
            class="w240"
            @change="onTypeChange"
          >
            <el-option
              v-for="(cfg, value) in requestTypeMap"
              :key="value"
              :label="cfg.label"
              :value="value"
            />
          </el-select>
          <div
            v-if="form.requestType === 'PROXY'"
            class="muted small type-hint"
          >
            代领只能引用老师已审核通过的申领单，不能新建空白申领；先选择责任老师过滤其名下源单，
            责任老师自动绑定源单归属老师，单据创建即「老师已审核」，直接由管理员终审。
          </div>
          <div
            v-else
            class="muted small type-hint"
          >
            本人申领登记草稿，先经老师审核，再由管理员终审。
          </div>
        </el-form-item>

        <!-- 学生代理代领：先选责任老师，再选源申领单 -->
        <template v-if="form.requestType === 'PROXY'">
          <el-form-item
            label="责任老师"
            required
          >
            <el-select
              v-model="form.proxyTeacherId"
              filterable
              clearable
              placeholder="选择老师，过滤其名下源申领单"
              class="w240"
              @change="onProxyTeacherChange"
            >
              <el-option
                v-for="t in proxyTeacherOptions"
                :key="t.id"
                :label="t.label"
                :value="t.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            label="源申领单"
            required
          >
            <el-select
              v-model="form.sourceRequestId"
              v-loading="proxySourcesLoading"
              filterable
              placeholder="选择老师已审核通过的申领单"
              class="proxy-source-select"
              @change="loadProxyLines"
            >
              <el-option
                v-for="src in filteredProxySources"
                :key="src.id"
                :label="`#${src.id} · ${displayName(src.ownerTeacherName, src.ownerTeacherId)} · ${src.purpose || '无用途'}`"
                :value="src.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            label="代领人 ID"
            required
          >
            <el-input
              v-model="form.operatorId"
              class="w240"
              placeholder="代领学生本人用户 ID（纯数字）"
            />
          </el-form-item>
        </template>

        <!-- 本人申领：定额快照来源 -->
        <template v-else>
          <el-form-item
            label="开课实例 ID"
            required
          >
            <el-input
              v-model="form.offeringId"
              class="w240"
              placeholder="纯数字 ID（需已配置定额才会计快照）"
            />
          </el-form-item>
          <el-form-item
            label="参与人数"
            required
          >
            <el-input-number
              v-model="form.participantCount"
              :min="0"
              :step="1"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item
            label="课时数"
            required
          >
            <el-input-number
              v-model="form.periodCount"
              :min="0"
              :step="1"
              controls-position="right"
            />
          </el-form-item>
        </template>

        <el-form-item label="用途">
          <el-input
            v-model="form.purpose"
            type="textarea"
            :rows="2"
            placeholder="如：有机化学实验课耗材"
          />
        </el-form-item>
      </el-form>

      <!-- 代领行：源单行剩余可代领量 -->
      <el-table
        v-if="form.requestType === 'PROXY'"
        v-loading="proxyLinesLoading"
        :data="form.proxyLines"
        border
      >
        <el-table-column
          label="物料"
          min-width="200"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.materialName }}（{{ row.baseUnit }}）
          </template>
        </el-table-column>
        <el-table-column
          label="批准量"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.approvedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="已实发"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.fulfilledQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="已占用"
          width="90"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.occupiedQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="剩余可代领"
          width="110"
          align="right"
        >
          <template #default="{ row }">
            {{ formatQty(row.availableQty) }}
          </template>
        </el-table-column>
        <el-table-column
          label="本次领取量"
          width="170"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.takeQty"
              :min="0"
              :max="Number(row.availableQty)"
              :step="1"
              :precision="3"
              :disabled="Number(row.availableQty) <= 0"
              controls-position="right"
            />
          </template>
        </el-table-column>
      </el-table>

      <!-- 本人申领行：自选物料 -->
      <el-table
        v-if="form.requestType === 'SELF'"
        :data="form.lines"
        border
      >
        <el-table-column
          label="物料"
          min-width="240"
        >
          <template #default="{ row }">
            <SkuSelect
              v-model="row.skuId"
              placeholder="搜索物料"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="申请量"
          width="160"
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
          width="80"
        >
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              @click="removeLine($index)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button
        v-if="form.requestType === 'SELF'"
        class="mt8"
        @click="addLine"
      >
        + 添加行
      </el-button>

      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCreate"
        >
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 审批（老师审核 / 管理员终审共用，逐行写批准量） -->
    <el-dialog
      v-model="approveVisible"
      :title="approveMode === 'teacher' ? '老师审核（草稿 → 老师已审核）' : '管理员终审（老师已审核 → 已批准）'"
      width="860px"
    >
      <div
        v-if="approveMode === 'teacher'"
        class="muted small mb8"
      >
        第一级审核：逐行填写批准量（不超过申请量，默认按申请量批准），通过后交管理员终审。
      </div>
      <div
        v-else
        class="muted small mb8"
      >
        第二级审核（终审）：可再调整批准量；学生代理代领单跳过老师审核，直接在此终审。
      </div>
      <el-table
        v-loading="approveLoading"
        :data="approveLines"
        border
      >
        <el-table-column
          label="物料"
          min-width="200"
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
          label="超定额"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.requiresExtraApproval"
              type="danger"
              size="small"
            >
              需签批
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="双签"
          width="70"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.requiresDualSign"
              type="warning"
              size="small"
            >
              双签
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="批准量"
          width="170"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.approvedQty"
              :min="0"
              :max="Number(row.requestedQty)"
              :step="1"
              :precision="3"
              controls-position="right"
            />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="approveVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitApprove"
        >
          {{ approveMode === 'teacher' ? '通过并交终审' : '终审批准' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 实发登记 -->
    <el-dialog
      v-model="issueVisible"
      title="实发（生成领用出库单）"
      width="560px"
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
        全部行按剩余批准量发放；生成出库草稿单后，请到「库存单据」页完成审批与过账。
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approveRequest,
  cancelRequest,
  createRequest,
  getRequest,
  issueRequest,
  listProxyLines,
  pageProxySources,
  pageRequests,
  rejectRequest,
  teacherApproveRequest,
  teacherRejectRequest
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { requestStatusMap, requestTypeMap } from '@/utils/statusMap'
import { copyText, fmtShanghai, formatQty, nowUtcIso } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import LocationSelect from '@/components/LocationSelect.vue'
import SkuSelect from '@/components/SkuSelect.vue'

const query = reactive({ status: null, requestType: null, offeringId: '' })

const pager = usePagedList((params) =>
  pageRequests({
    ...params,
    status: query.status || undefined,
    requestType: query.requestType || undefined,
    offeringId: query.offeringId || undefined
  })
)

const detailVisible = ref(false)
const detail = ref(null)

const createVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  requestType: 'SELF',
  offeringId: '',
  participantCount: 0,
  periodCount: 0,
  purpose: '',
  operatorId: '',
  proxyTeacherId: null,
  sourceRequestId: null,
  lines: [],
  proxyLines: []
})

const proxySources = ref([])
const proxySourcesLoading = ref(false)
const proxyLinesLoading = ref(false)

/** 责任老师下拉选项：从可代领源单中提取去重（登录模块未接入，暂无独立老师列表接口） */
const proxyTeacherOptions = computed(() => {
  const map = new Map()
  for (const src of proxySources.value) {
    const id = String(src.ownerTeacherId ?? '')
    if (!id) continue
    if (!map.has(id)) {
      map.set(id, { id, label: displayName(src.ownerTeacherName, src.ownerTeacherId) })
    }
  }
  return [...map.values()]
})

/** 按所选老师过滤源申领单 */
const filteredProxySources = computed(() => {
  if (!form.proxyTeacherId) return proxySources.value
  return proxySources.value.filter(
    (s) => String(s.ownerTeacherId) === String(form.proxyTeacherId)
  )
})

const approveVisible = ref(false)
const approveLoading = ref(false)
const approveMode = ref('teacher')
const approveRequestId = ref(null)
const approveLines = ref([])

const issueVisible = ref(false)
const issueForm = reactive({ requestId: null, fromLocationId: null, reason: '' })

/** 姓名缺失（用户不存在/未接入登录）时回退显示 ID */
function displayName(name, id) {
  if (name) return name
  return id || '-'
}

async function openDetail(id) {
  try {
    detail.value = await getRequest(id)
    detailVisible.value = true
  } catch {
    // 失败已由全局拦截器提示
  }
}

function openCreate() {
  form.requestType = 'SELF'
  form.offeringId = ''
  form.participantCount = 0
  form.periodCount = 0
  form.purpose = ''
  form.operatorId = ''
  form.proxyTeacherId = null
  form.sourceRequestId = null
  form.lines = [{ skuId: null, requestedQty: null }]
  form.proxyLines = []
  proxySources.value = []
  createVisible.value = true
  loadProxySources()
}

/** 切换申领类型：联动清空可选物料与流程参数 */
function onTypeChange() {
  form.offeringId = ''
  form.participantCount = 0
  form.periodCount = 0
  form.operatorId = ''
  form.proxyTeacherId = null
  form.sourceRequestId = null
  form.lines = [{ skuId: null, requestedQty: null }]
  form.proxyLines = []
}

/** 切换责任老师：清空已选源单与代领行（源单列表随老师过滤刷新） */
function onProxyTeacherChange() {
  form.sourceRequestId = null
  form.proxyLines = []
}

/** 可代领源申领单（老师已审核通过的本人申领单） */
async function loadProxySources() {
  proxySourcesLoading.value = true
  try {
    const data = await pageProxySources({ pageNum: 1, pageSize: 100 })
    proxySources.value = data?.records ?? []
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    proxySourcesLoading.value = false
  }
}

/** 源申领单行含剩余可代领量，默认领取量 = 剩余 */
async function loadProxyLines() {
  if (!form.sourceRequestId) {
    form.proxyLines = []
    return
  }
  proxyLinesLoading.value = true
  try {
    const lines = await listProxyLines(form.sourceRequestId)
    form.proxyLines = (lines || []).map((l) => ({
      ...l,
      takeQty: Number(l.availableQty) > 0 ? Number(l.availableQty) : 0
    }))
  } catch {
    form.proxyLines = []
  } finally {
    proxyLinesLoading.value = false
  }
}

function addLine() {
  form.lines.push({ skuId: null, requestedQty: null })
}

function removeLine(index) {
  form.lines.splice(index, 1)
}

async function submitCreate() {
  if (form.requestType === 'PROXY') {
    await submitCreateProxy()
  } else {
    await submitCreateSelf()
  }
}

/** 本人申领：登记草稿，待老师审核 */
async function submitCreateSelf() {
  if (!form.offeringId || !/^\d{1,19}$/.test(form.offeringId)) {
    ElMessage.warning('开课实例 ID 必须为纯数字')
    return
  }
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
      offeringId: form.offeringId,
      participantCount: form.participantCount,
      periodCount: form.periodCount,
      purpose: form.purpose || undefined,
      lines: form.lines.map((l) => ({ skuId: l.skuId, requestedQty: l.requestedQty }))
    })
    ElMessage.success(`登记成功，申领单 ID：${id}，待老师审核`)
    createVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

/** 学生代理代领：引用老师已审核源单，创建即「老师已审核」，直接交管理员终审 */
async function submitCreateProxy() {
  if (!form.proxyTeacherId) {
    ElMessage.warning('请先选择责任老师')
    return
  }
  if (!form.sourceRequestId) {
    ElMessage.warning('请选择老师已审核通过的源申领单（不能新建空白申领）')
    return
  }
  if (!form.operatorId || !/^\d{1,19}$/.test(form.operatorId)) {
    ElMessage.warning('代领人 ID 必须为纯数字')
    return
  }
  const taken = form.proxyLines.filter((l) => l.takeQty > 0)
  if (!taken.length) {
    ElMessage.warning('请至少填写一行领取量大于 0 的物料')
    return
  }
  for (const line of taken) {
    if (line.takeQty > Number(line.availableQty)) {
      ElMessage.warning(`「${line.materialName}」领取量超过剩余可代领量`)
      return
    }
  }
  submitting.value = true
  try {
    const id = await createRequest({
      requestType: 'PROXY',
      sourceRequestId: form.sourceRequestId,
      operatorId: form.operatorId,
      purpose: form.purpose || undefined,
      lines: taken.map((l) => ({
        skuId: l.skuId,
        requestedQty: l.takeQty,
        sourceLineId: l.lineId
      }))
    })
    ElMessage.success(`代领单已提交，申领单 ID：${id}，直接待管理员终审`)
    createVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

/** 打开审批对话框：teacher = 老师审核（草稿）；final = 管理员终审（老师已审核） */
async function openApprove(row, mode) {
  approveMode.value = mode
  approveRequestId.value = row.id
  approveVisible.value = true
  approveLoading.value = true
  try {
    const d = await getRequest(row.id)
    // 老师审核默认按申请量批准；终审默认保留既有批准量（未批准行按申请量）
    approveLines.value = (d?.lines ?? []).map((l) => ({
      ...l,
      approvedQty: mode === 'final' && l.approvedQty != null ? l.approvedQty : l.requestedQty
    }))
  } catch {
    approveLines.value = []
    approveVisible.value = false
  } finally {
    approveLoading.value = false
  }
}

async function submitApprove() {
  for (const line of approveLines.value) {
    if (line.approvedQty === null || line.approvedQty <= 0) {
      ElMessage.warning('批准量必须大于 0')
      return
    }
    if (line.approvedQty > Number(line.requestedQty)) {
      ElMessage.warning(`「${line.skuName}」批准量不能超过申请量`)
      return
    }
  }
  submitting.value = true
  try {
    const payload = {
      lines: approveLines.value.map((l) => ({ lineId: l.id, approvedQty: l.approvedQty }))
    }
    if (approveMode.value === 'teacher') {
      await teacherApproveRequest(approveRequestId.value, payload)
      ElMessage.success('老师审核通过，已交管理员终审')
    } else {
      await approveRequest(approveRequestId.value, payload)
      ElMessage.success('终审批准完成')
    }
    approveVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doReject(row, mode) {
  const tip =
    mode === 'teacher'
      ? '老师审核拒绝该申领单（草稿 → 已拒绝）？'
      : '管理员终审拒绝该申领单（老师已审核 → 已拒绝）？'
  const ok = await confirmAction(tip, '拒绝确认')
  if (!ok) return
  try {
    if (mode === 'teacher') {
      await teacherRejectRequest(row.id)
    } else {
      await rejectRequest(row.id)
    }
    ElMessage.success('已拒绝')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doCancel(row) {
  const ok = await confirmAction('确认取消该申领单？', '取消确认')
  if (!ok) return
  try {
    await cancelRequest(row.id)
    ElMessage.success('已取消')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

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
    ElMessage.success(`已生成出库草稿单 ${docId}，请到「库存单据」页审批并过账`)
    issueVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(pager.load)

defineExpose({ refresh: pager.load })
</script>

<style scoped>
.type-hint {
  margin-top: 4px;
}

.proxy-source-select {
  width: 560px;
}

.mb8 {
  margin-bottom: 8px;
}
</style>
