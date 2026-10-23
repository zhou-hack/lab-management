<template>
  <div>
    <el-tabs
      v-model="activeTab"
      class="req-tabs"
      @tab-change="onTabChange"
    >
      <el-tab-pane
        label="物料预约"
        name="request"
      />
      <el-tab-pane
        label="领用定额"
        name="quota"
      />
    </el-tabs>

    <div v-show="activeTab === 'request'">
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
          v-model="query.courseName"
          clearable
          class="w220"
          placeholder="课程名称过滤"
          @change="pager.search"
        />
        <el-button
          v-permission="'item:request:create'"
          type="primary"
          @click="openCreate"
        >
          新建物料预约
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
          label="课程"
          min-width="170"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <template v-if="row.courseName">
              <div>{{ row.courseName }}</div>
              <div
                v-if="row.className"
                class="muted small"
              >
                {{ row.className }}
              </div>
            </template>
            <span
              v-else-if="row.offeringId"
              class="muted small"
            >开课实例 {{ row.offeringId }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          label="申领类型"
          width="110"
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
            {{ row.requestType === 'PROXY' ? displayName(row.operatorName, row.operatorId) : '-' }}
          </template>
        </el-table-column>
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
            <!-- 待终审（老师预约创建即「老师已审核」）：管理员终审 -->
            <template v-if="row.status === 'TEACHER_APPROVED'">
              <el-button
                v-permission="'item:request:approve'"
                link
                type="success"
                @click="openApprove(row)"
              >
                终审
              </el-button>
              <el-button
                v-permission="'item:request:approve'"
                link
                type="warning"
                @click="doReject(row)"
              >
                终审拒绝
              </el-button>
            </template>
            <el-button
              v-if="row.status === 'TEACHER_APPROVED' || row.status === 'APPROVED'"
              v-permission="'item:request:cancel'"
              link
              type="danger"
              @click="doCancel(row)"
            >
              取消
            </el-button>
            <el-button
              v-if="row.status === 'APPROVED'"
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

      <!-- 预约详情 -->
      <el-dialog
        v-model="detailVisible"
        title="物料预约详情"
        width="940px"
      >
        <el-descriptions
          v-if="detail"
          :column="3"
          border
        >
          <el-descriptions-item label="物料预约 ID">
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
            {{ detail.requestType === 'PROXY' ? displayName(detail.operatorName, detail.operatorId) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="学生学号">
            {{ detail.requestType === 'PROXY' ? (detail.studentNo || '-') : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="学生姓名">
            {{ detail.requestType === 'PROXY' ? (detail.studentName || '-') : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="源申领单 ID">
            {{ detail.sourceRequestId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="课程名称">
            {{ detail.courseName || (detail.offeringId ? `开课实例 ${detail.offeringId}` : '-') }}
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

      <!-- 新建物料预约（老师预约 / 学生代理代领） -->
      <el-dialog
        v-model="createVisible"
        title="新建物料预约"
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
              代领只能引用老师端已审核通过的老师预约单，不能新建空白预约；先选择责任老师，再选择其名下已预约的课程，
              老师作为第一责任人自动绑定源单归属老师（免老师级审核），创建即「老师已审核」，直接由管理员终审。
            </div>
            <div
              v-else
              class="muted small type-hint"
            >
              老师本人发起并作为第一责任人，创建即视为老师已审，仅由管理员终审。
            </div>
          </el-form-item>

          <!-- 学生代理代领：先选责任老师过滤，再选源申领单 -->
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
              label="已预约课程"
              required
            >
              <el-select
                v-model="form.sourceRequestId"
                v-loading="proxySourcesLoading"
                filterable
                placeholder="选择已预约的课程（老师端已审核通过）"
                class="proxy-source-select"
                @change="loadProxyLines"
              >
                <el-option
                  v-for="src in filteredProxySources"
                  :key="src.id"
                  :label="proxySourceLabel(src)"
                  :value="src.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item
              label="学生学号"
              required
            >
              <el-input
                v-model="form.studentNo"
                class="w240"
                maxlength="32"
                placeholder="代理学生学号"
              />
            </el-form-item>
            <el-form-item
              label="学生姓名"
              required
            >
              <el-input
                v-model="form.studentName"
                class="w240"
                maxlength="64"
                placeholder="代理学生姓名"
              />
            </el-form-item>
          </template>

          <!-- 老师预约：本人作为第一责任人 -->
          <template v-else>
            <el-form-item
              label="归属老师"
              required
            >
              <el-select
                v-model="form.ownerTeacherId"
                filterable
                remote
                clearable
                :remote-method="searchTeachers"
                :loading="teachersLoading"
                placeholder="输入老师姓名搜索（发起本人）"
                class="w240"
              >
                <el-option
                  v-for="t in teacherOptions"
                  :key="t.id"
                  :label="t.name"
                  :value="t.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item
              label="课程名称"
              required
            >
              <BookingSelect
                v-model="form.labBookingId"
                placeholder="选择已预约的课程（按场地/课程/班级搜索）"
              />
              <div class="muted small type-hint">
                下拉列出已预约（待审批/已批准）的课程；选中后本单将关联该场地预约。
              </div>
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
        <el-alert
          v-if="form.requestType === 'PROXY' && form.proxyLines.length"
          :type="proxyAllTaken ? 'warning' : 'info'"
          :closable="false"
          show-icon
          :title="proxyAllTaken
            ? '该源单的可代领额度已被其他代领单占满，领取量不可填写；请更换源单或联系管理员调整'
            : '剩余可代领 = 批准量 − 已实发 − 已被其他代领单占用；领取量默认取剩余量，可按需调小'"
          class="proxy-lines-hint"
        />
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

        <!-- 老师预约行：自选物料 -->
        <el-table
          v-if="form.requestType === 'TEACHER'"
          :data="form.lines"
          border
        >
          <el-table-column
            label="物料"
            min-width="280"
          >
            <template #default="{ row }">
              <SkuSelect
                v-model="row.skuId"
                placeholder="搜索物料"
              />
              <div
                v-if="row.skuId"
                class="small quota-hint"
                :class="quotaHintOf(row.skuId).warn ? 'quota-hint-warn' : 'muted'"
              >
                {{ quotaHintOf(row.skuId).text }}
              </div>
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
          v-if="form.requestType === 'TEACHER'"
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

      <!-- 管理员终审（逐行写批准量） -->
      <el-dialog
        v-model="approveVisible"
        title="管理员终审（老师已审核 → 已批准）"
        width="860px"
      >
        <div class="muted small mb8">
          终审：逐行调整批准量（不超过申请量）；老师预约创建即「老师已审核」，直接在此终审。
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
            终审批准
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
          全部行按剩余批准量发放；确认后自动生成出库单并过账入账（扣减库存、回写实发量）。
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

    <QuotaPanel
      v-if="quotaVisited"
      v-show="activeTab === 'quota'"
      ref="quotaPanelRef"
    />
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approveRequest,
  cancelRequest,
  createRequest,
  getRequest,
  issueRequest,
  listProxyLines,
  pageProxySources,
  pageQuotas,
  pageRequests,
  rejectRequest,
  teacherListOptions
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { requestStatusMap, requestTypeMap } from '@/utils/statusMap'
import { copyText, fmtShanghai, formatQty, nowUtcIso } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import LocationSelect from '@/components/LocationSelect.vue'
import SkuSelect from '@/components/SkuSelect.vue'
import BookingSelect from '@/components/BookingSelect.vue'
import QuotaPanel from './QuotaPanel.vue'

/**
 * 页签：物料预约 / 领用定额（2026-10-22 需求：定额并入物料预约页，不再单列菜单）
 * 定额面板懒挂载：首次切到定额页签才挂载，切回预约单仅切换显示
 */
const activeTab = ref('request')
const quotaVisited = ref(false)
const quotaPanelRef = ref(null)

function onTabChange(name) {
  if (name === 'quota') {
    quotaVisited.value = true
    nextTick(() => quotaPanelRef.value?.refresh())
  }
}

/** 按当前页签刷新：定额页签刷新定额列表，预约单页签刷新预约列表 */
function refresh() {
  if (activeTab.value === 'quota') {
    quotaPanelRef.value?.refresh()
  } else {
    pager.load()
  }
}

const query = reactive({ status: null, requestType: null, courseName: '' })

const pager = usePagedList((params) =>
  pageRequests({
    ...params,
    status: query.status || undefined,
    requestType: query.requestType || undefined,
    courseName: query.courseName || undefined
  })
)

const detailVisible = ref(false)
const detail = ref(null)

const createVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  requestType: 'TEACHER',
  labBookingId: '',
  participantCount: 0,
  periodCount: 0,
  purpose: '',
  ownerTeacherId: null,
  studentNo: '',
  studentName: '',
  proxyTeacherId: null,
  sourceRequestId: null,
  lines: [],
  proxyLines: []
})

/** 老师候选（老师预约选择归属老师用，远程搜索） */
const teacherOptions = ref([])
const teachersLoading = ref(false)

/** 可代领源申领单（老师端已审核通过的老师预约单）与代领行加载状态 */
const proxySources = ref([])
const proxySourcesLoading = ref(false)
const proxyLinesLoading = ref(false)

/** 责任老师下拉选项：从可代领源单中提取去重（代领按源单归属老师过滤） */
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

/** 当前过滤结果中重复的课程名（重名时下拉附加登记时间便于区分） */
const duplicatedCourseNames = computed(() => {
  const counts = new Map()
  for (const s of filteredProxySources.value) {
    if (s.courseName) counts.set(s.courseName, (counts.get(s.courseName) || 0) + 1)
  }
  return new Set([...counts.entries()].filter(([, n]) => n > 1).map(([k]) => k))
})

/** 已预约课程下拉展示名：课程名优先，重名附加登记时间；未关联预约时回退开课实例/单号用途 */
function proxySourceLabel(src) {
  if (src.courseName) {
    if (duplicatedCourseNames.value.has(src.courseName)) {
      return `${src.courseName} · ${fmtShanghai(src.createTime)}`
    }
    return src.courseName
  }
  if (src.offeringId) return `开课实例 ${src.offeringId}`
  return `#${src.id} · ${src.purpose || '无用途'}`
}

/** 所选源单可代领额度是否已被其他代领单全部占满（满额时领取量不可填，需换源单） */
const proxyAllTaken = computed(() =>
  form.proxyLines.length > 0
  && form.proxyLines.every((l) => Number(l.availableQty) <= 0)
)

async function searchTeachers(keyword) {
  teachersLoading.value = true
  try {
    teacherOptions.value = await teacherListOptions(keyword || undefined)
  } catch {
    teacherOptions.value = []
  } finally {
    teachersLoading.value = false
  }
}

/** 物料定额提示缓存：skuId → { loading, records }（跨弹窗复用，按物料查询） */
const quotaHints = reactive({})

async function loadQuotaHint(skuId) {
  const key = String(skuId)
  if (!key || quotaHints[key]) return
  quotaHints[key] = { loading: true, records: [] }
  try {
    const res = await pageQuotas({ skuId: key, pageNum: 1, pageSize: 20 })
    const records = res.records || []
    quotaHints[key] = { loading: false, records }
    // 有定额记录但全部停用：主动弹窗提醒（预约将按无定额处理，超量需老师签批）
    if (records.length > 0 && !records.some((r) => Number(r.enabled) === 1)) {
      ElMessage.warning('该物料的领用定额已全部停用，本次预约将按无定额处理')
    }
  } catch {
    quotaHints[key] = { loading: false, records: [] }
  }
}

// 选中物料即加载其领用定额（覆盖手动选择与程序赋值两种路径）
watch(
  () => form.lines.map((l) => l.skuId).filter(Boolean).join(','),
  (val) => {
    for (const id of val ? val.split(',') : []) {
      loadQuotaHint(id)
    }
  }
)

/** 物料行定额提示：返回 { text, warn }；停用定额用警告样式醒目提醒 */
function quotaHintOf(skuId) {
  const hint = quotaHints[String(skuId)]
  if (!hint || hint.loading) return { text: '领用定额查询中…', warn: false }
  const enabled = hint.records.filter((r) => Number(r.enabled) === 1)
  if (enabled.length === 0) {
    return hint.records.length > 0
      ? { text: '注意：该物料的领用定额已全部停用，本次预约将按无定额处理（超量需老师签批）', warn: true }
      : { text: '该物料暂无领用定额记录', warn: false }
  }
  const parts = enabled.map(
    (r) => `实例 ${r.offeringId}：${formatQty(r.qtyPerStudentPeriod)}/人·课时`
  )
  let text = `领用定额 ${parts.join('；')}`
  const people = Number(form.participantCount) || 0
  const periods = Number(form.periodCount) || 0
  if (enabled.length === 1 && people > 0 && periods > 0) {
    const est = people * periods * Number(enabled[0].qtyPerStudentPeriod)
    text += `，按 ${people}人 × ${periods}课时 估 ≈ ${formatQty(Number(est.toFixed(3)))}`
  }
  return { text, warn: false }
}

const approveVisible = ref(false)
const approveLoading = ref(false)
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

/** 打开新建弹窗（老师预约：本人作为第一责任人；学生代理代领：引用老师端已审核源单） */
async function openCreate() {
  form.requestType = 'TEACHER'
  form.labBookingId = ''
  form.participantCount = 0
  form.periodCount = 0
  form.purpose = ''
  form.ownerTeacherId = null
  form.studentNo = ''
  form.studentName = ''
  form.proxyTeacherId = null
  form.sourceRequestId = null
  form.lines = [{ skuId: null, requestedQty: null }]
  form.proxyLines = []
  proxySources.value = []
  createVisible.value = true
  if (teacherOptions.value.length === 0) {
    searchTeachers('')
  }
}

/** 切换申领类型：联动清空两分支的表单参数；切到代领时加载可代领源单 */
function onTypeChange() {
  form.labBookingId = ''
  form.participantCount = 0
  form.periodCount = 0
  form.studentNo = ''
  form.studentName = ''
  form.proxyTeacherId = null
  form.sourceRequestId = null
  form.lines = [{ skuId: null, requestedQty: null }]
  form.proxyLines = []
  if (form.requestType === 'PROXY') {
    loadProxySources()
  }
}

/** 切换责任老师：清空已选源单与代领行（源单列表随老师过滤刷新） */
function onProxyTeacherChange() {
  form.sourceRequestId = null
  form.proxyLines = []
}

/** 可代领源申领单（老师端已审核通过的老师预约单） */
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

/** 自选物料行校验 */
function validateLines() {
  if (!form.lines.length) {
    ElMessage.warning('请至少添加一行物料')
    return false
  }
  for (const line of form.lines) {
    if (!line.skuId) {
      ElMessage.warning('存在未选择物料的行')
      return false
    }
    if (line.requestedQty === null || line.requestedQty <= 0) {
      ElMessage.warning('申请量必须大于 0')
      return false
    }
  }
  return true
}

/** 提交新建：按申领类型分发（老师预约 / 学生代理代领） */
async function submitCreate() {
  if (form.requestType === 'PROXY') {
    await submitCreateProxy()
  } else {
    await submitCreateTeacher()
  }
}

/** 老师预约：本人作为第一责任人，创建即视为老师已审，仅管理员终审 */
async function submitCreateTeacher() {
  if (!form.ownerTeacherId) {
    ElMessage.warning('请选择归属老师（发起本人）')
    return
  }
  if (!form.labBookingId) {
    ElMessage.warning('请选择已预约的课程')
    return
  }
  if (!validateLines()) return
  submitting.value = true
  try {
    const id = await createRequest({
      requestType: 'TEACHER',
      ownerTeacherId: form.ownerTeacherId,
      labBookingId: form.labBookingId,
      participantCount: form.participantCount,
      periodCount: form.periodCount,
      purpose: form.purpose || undefined,
      lines: form.lines.map((l) => ({ skuId: l.skuId, requestedQty: l.requestedQty }))
    })
    ElMessage.success(`老师预约已提交，物料预约 ID：${id}，直接待管理员终审`)
    createVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

/** 学生代理代领：引用老师端已审核通过的源单，免老师级审核，直接交管理员终审 */
async function submitCreateProxy() {
  if (!form.proxyTeacherId) {
    ElMessage.warning('请先选择责任老师')
    return
  }
  if (!form.sourceRequestId) {
    ElMessage.warning('请选择已预约的课程（老师端已审核通过的老师预约单）')
    return
  }
  if (!form.studentNo.trim() || !form.studentName.trim()) {
    ElMessage.warning('请填写代理学生学号与姓名')
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
      studentNo: form.studentNo.trim(),
      studentName: form.studentName.trim(),
      purpose: form.purpose || undefined,
      lines: taken.map((l) => ({
        skuId: l.skuId,
        requestedQty: l.takeQty,
        sourceLineId: l.lineId
      }))
    })
    ElMessage.success(`代领单已提交，物料预约 ID：${id}，直接待管理员终审`)
    createVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

/** 打开终审对话框：默认保留既有批准量（未批准行按申请量） */
async function openApprove(row) {
  approveRequestId.value = row.id
  approveVisible.value = true
  approveLoading.value = true
  try {
    const d = await getRequest(row.id)
    approveLines.value = (d?.lines ?? []).map((l) => ({
      ...l,
      approvedQty: l.approvedQty != null ? l.approvedQty : l.requestedQty
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
    await approveRequest(approveRequestId.value, payload)
    ElMessage.success('终审批准完成')
    approveVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

/** 管理员终审拒绝（老师已审核 → 已拒绝） */
async function doReject(row) {
  const ok = await confirmAction('管理员终审拒绝该物料预约（老师已审核 → 已拒绝）？', '拒绝确认')
  if (!ok) return
  try {
    await rejectRequest(row.id)
    ElMessage.success('已拒绝')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doCancel(row) {
  const ok = await confirmAction('确认取消该物料预约？', '取消确认')
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
    ElMessage.success(`已出库过账，单据 ${docId}，库存与实发量已更新`)
    issueVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

onMounted(pager.load)

defineExpose({ refresh, openCreate })
</script>

<style scoped>
.type-hint {
  margin-top: 4px;
}

.mb8 {
  margin-bottom: 8px;
}

.mt8 {
  margin-top: 8px;
}

.proxy-source-select {
  width: 520px;
  max-width: 100%;
}

.proxy-lines-hint {
  margin-bottom: 8px;
}

.quota-hint {
  margin-top: 4px;
  line-height: 1.4;
}

.quota-hint-warn {
  color: var(--el-color-warning);
  font-weight: 500;
}
</style>
