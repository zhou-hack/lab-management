<template>
  <div class="page">
    <el-alert
      title="代领规则：老师申领时选择「学生代理领取」的申领单会出现在下方可代领源单中；按源单剩余可代领量领取，须持有该老师的有效助理授权，代领单创建后直接交管理员终审"
      type="info"
      :closable="false"
      show-icon
      class="mb12"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card_header">
          <span>可代领源单（老师标记「学生代理领取」且已审核通过）</span>
          <el-button
            size="small"
            @click="loadSources"
          >
            刷新
          </el-button>
        </div>
      </template>
      <el-table
        v-loading="sourcesLoading"
        :data="sources"
        border
      >
        <el-table-column
          prop="id"
          label="源申领单号"
          width="180"
          show-overflow-tooltip
        />
        <el-table-column
          label="责任老师"
          width="110"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.ownerTeacherName || row.ownerTeacherId || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="purpose"
          label="用途"
          min-width="180"
          show-overflow-tooltip
        />
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
          width="100"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openPickup(row)"
            >
              代领
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card
      shadow="never"
      class="mt16"
    >
      <template #header>
        <span>我的代领单</span>
      </template>
      <el-table
        v-loading="pager.loading"
        :data="pager.rows"
        border
      >
        <el-table-column
          prop="id"
          label="代领单号"
          width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="sourceRequestId"
          label="源申领单号"
          width="180"
          show-overflow-tooltip
        />
        <el-table-column
          label="责任老师"
          width="110"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.ownerTeacherName || row.ownerTeacherId || '-' }}
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
              :map="proxyStatusMap"
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
          width="100"
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
          </template>
        </el-table-column>
      </el-table>
      <PagerBar :list="pager" />
    </el-card>

    <!-- 代领弹窗：源单行剩余可代领量 -->
    <el-dialog
      v-model="pickupVisible"
      title="代领物料"
      width="900px"
    >
      <div class="muted small mb8">
        剩余可代领 = 批准量 − 已实发 − 已被其他代领单占用；领取量默认取剩余量，可按需调小。
        代领单创建后直接交管理员终审。
      </div>
      <el-table
        v-loading="linesLoading"
        :data="pickupLines"
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
          width="160"
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
      <el-form
        label-width="80px"
        class="mt12"
      >
        <el-form-item label="用途">
          <el-input
            v-model="pickupPurpose"
            type="textarea"
            :rows="2"
            placeholder="选填，如：代领有机化学实验课耗材"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pickupVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitPickup"
        >
          提交代领
        </el-button>
      </template>
    </el-dialog>

    <!-- 代领单详情 -->
    <el-dialog
      v-model="detailVisible"
      title="代领单详情"
      width="900px"
    >
      <el-descriptions
        v-if="detail"
        :column="3"
        border
      >
        <el-descriptions-item label="代领单号">
          {{ detail.id }}
        </el-descriptions-item>
        <el-descriptions-item label="源申领单号">
          {{ detail.sourceRequestId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag
            :value="detail.status"
            :map="proxyStatusMap"
          />
        </el-descriptions-item>
        <el-descriptions-item label="责任老师">
          {{ detail.ownerTeacherName || detail.ownerTeacherId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="登记时间">
          {{ fmtShanghai(detail.createTime) }}
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
          label="物料"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.skuName }}（{{ row.baseUnit }}）
          </template>
        </el-table-column>
        <el-table-column
          label="领取量"
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
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createRequest,
  getRequest,
  listProxyLines,
  pageProxySources,
  pageRequests
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { fmtShanghai, formatQty } from '@/utils/format'
import { useUserStore } from '@/store/user'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'

/**
 * 物料代领（学生代理自助页，2026-10-24 重写）：
 * - 可代领源单 = 老师已审核通过（TEACHER_APPROVED）的本人申领单，按剩余可代领量领取
 * - 代领人由服务端强制为当前登录学生，须持有源单归属老师的有效 assistant_grant，
 *   未授权提交将被 403 拒绝；代领单创建即 TEACHER_APPROVED，直接交管理员终审
 * - 我的代领单 = 本人经办的 PROXY 单（operatorId = 当前用户，后端数据范围兜底）
 */
const proxyStatusMap = {
  TEACHER_APPROVED: { label: '待管理员终审', type: 'warning' },
  APPROVED: { label: '已批准', type: 'success' },
  PARTIALLY_FULFILLED: { label: '部分实发', type: 'warning' },
  FULFILLED: { label: '全部实发', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const store = useUserStore()

/* ---------------- 可代领源单 ---------------- */
const sources = ref([])
const sourcesLoading = ref(false)

async function loadSources() {
  sourcesLoading.value = true
  try {
    const data = await pageProxySources({ pageNum: 1, pageSize: 100 })
    sources.value = data?.records ?? []
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    sourcesLoading.value = false
  }
}

/* ---------------- 我的代领单 ---------------- */
const pager = usePagedList((params) =>
  pageRequests({
    ...params,
    requestType: 'PROXY',
    operatorId: store.user?.userId
  })
)

/* ---------------- 代领弹窗 ---------------- */
const pickupVisible = ref(false)
const linesLoading = ref(false)
const submitting = ref(false)
const pickupSourceId = ref(null)
const pickupLines = ref([])
const pickupPurpose = ref('')

async function openPickup(row) {
  pickupSourceId.value = row.id
  pickupPurpose.value = ''
  pickupLines.value = []
  pickupVisible.value = true
  linesLoading.value = true
  try {
    const lines = await listProxyLines(row.id)
    pickupLines.value = (lines || []).map((l) => ({
      ...l,
      takeQty: Number(l.availableQty) > 0 ? Number(l.availableQty) : 0
    }))
  } catch {
    pickupVisible.value = false
  } finally {
    linesLoading.value = false
  }
}

async function submitPickup() {
  const taken = pickupLines.value.filter((l) => l.takeQty > 0)
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
      sourceRequestId: pickupSourceId.value,
      purpose: pickupPurpose.value || undefined,
      lines: taken.map((l) => ({
        skuId: l.skuId,
        requestedQty: l.takeQty,
        sourceLineId: l.lineId
      }))
    })
    ElMessage.success(`代领单已提交（单号 ${id}），待管理员终审`)
    pickupVisible.value = false
    loadSources()
    pager.load()
  } catch {
    // 失败提示由请求拦截器统一处理（未授权 403 等）
  } finally {
    submitting.value = false
  }
}

/* ---------------- 详情 ---------------- */
const detailVisible = ref(false)
const detail = ref(null)

async function openDetail(id) {
  try {
    detail.value = await getRequest(id)
    detailVisible.value = true
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

onMounted(() => {
  loadSources()
  pager.load()
})
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.mb8 {
  margin-bottom: 8px;
}

.mt8 {
  margin-top: 8px;
}

.mt12 {
  margin-top: 12px;
}

.mt16 {
  margin-top: 16px;
}

.card_header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
