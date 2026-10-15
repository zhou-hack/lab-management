<template>
  <div>
    <div class="toolbar">
      <el-select
        v-model="query.status"
        clearable
        placeholder="全部状态"
        class="w140"
        @change="pager.search"
      >
        <el-option
          v-for="(cfg, value) in requestStatusMap"
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
        prop="offeringId"
        label="开课实例 ID"
        width="170"
        show-overflow-tooltip
      />
      <el-table-column
        prop="purpose"
        label="用途"
        min-width="180"
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
        width="280"
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
          <template v-if="row.status === 'DRAFT'">
            <el-button
              v-permission="'item:request:approve'"
              link
              type="success"
              @click="doApprove(row)"
            >
              审批
            </el-button>
            <el-button
              v-permission="'item:request:approve'"
              link
              type="warning"
              @click="doReject(row)"
            >
              拒绝
            </el-button>
            <el-button
              v-permission="'item:request:cancel'"
              link
              type="danger"
              @click="doCancel(row)"
            >
              取消
            </el-button>
          </template>
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
      width="980px"
    >
      <el-descriptions
        v-if="detail"
        :column="3"
        border
      >
        <el-descriptions-item label="申领单 ID">
          {{ detail.id }}
        </el-descriptions-item>
        <el-descriptions-item label="开课实例 ID">
          {{ detail.offeringId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag
            :value="detail.status"
            :map="requestStatusMap"
          />
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

    <!-- 新建申领 -->
    <el-dialog
      v-model="createVisible"
      title="新建申领单"
      width="900px"
    >
      <el-form label-width="110px">
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
        <el-form-item label="用途">
          <el-input
            v-model="form.purpose"
            type="textarea"
            :rows="2"
            placeholder="如：有机化学实验课耗材"
          />
        </el-form-item>
      </el-form>
      <el-table
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approveRequest,
  cancelRequest,
  createRequest,
  getRequest,
  issueRequest,
  pageRequests,
  rejectRequest
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { requestStatusMap } from '@/utils/statusMap'
import { copyText, fmtShanghai, formatQty, nowUtcIso } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import LocationSelect from '@/components/LocationSelect.vue'
import SkuSelect from '@/components/SkuSelect.vue'

const query = reactive({ status: null, offeringId: '' })

const pager = usePagedList((params) =>
  pageRequests({
    ...params,
    status: query.status || undefined,
    offeringId: query.offeringId || undefined
  })
)

const detailVisible = ref(false)
const detail = ref(null)

const createVisible = ref(false)
const submitting = ref(false)
const form = reactive({ offeringId: '', participantCount: 0, periodCount: 0, purpose: '', lines: [] })

const issueVisible = ref(false)
const issueForm = reactive({ requestId: null, fromLocationId: null, reason: '' })

async function openDetail(id) {
  try {
    detail.value = await getRequest(id)
    detailVisible.value = true
  } catch {
    // 失败已由全局拦截器提示
  }
}

function openCreate() {
  form.offeringId = ''
  form.participantCount = 0
  form.periodCount = 0
  form.purpose = ''
  form.lines = [{ skuId: null, requestedQty: null }]
  createVisible.value = true
}

function addLine() {
  form.lines.push({ skuId: null, requestedQty: null })
}

function removeLine(index) {
  form.lines.splice(index, 1)
}

async function submitCreate() {
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
      offeringId: form.offeringId,
      participantCount: form.participantCount,
      periodCount: form.periodCount,
      purpose: form.purpose || undefined,
      lines: form.lines.map((l) => ({ skuId: l.skuId, requestedQty: l.requestedQty }))
    })
    ElMessage.success(`登记成功，申领单 ID：${id}`)
    createVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doApprove(row) {
  const ok = await confirmAction('按申请量批准全部行？超定额行仅作标记，不阻断流程。', '审批确认', { type: 'info' })
  if (!ok) return
  try {
    await approveRequest(row.id, {})
    ElMessage.success('已批准')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doReject(row) {
  const ok = await confirmAction('确认拒绝该申领单？', '拒绝确认')
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
