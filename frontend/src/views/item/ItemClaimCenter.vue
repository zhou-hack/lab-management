<template>
  <div class="claim_center">
    <div class="claim_center__head">
      <div>
        <h2 class="t-title claim_center__title">
          我的物料申领
        </h2>
        <p class="t-sub">
          管理本人申领与助理授权、查看三单业务链，并跟踪待归还与已归还的逾期记录。
        </p>
      </div>
      <div class="claim_center__actions">
        <el-button @click="goToPlans">
          我的预设方案
        </el-button>
        <el-button
          type="primary"
          @click="openCreate()"
        >
          新建申领
        </el-button>
      </div>
    </div>

    <el-tabs
      v-model="activeTab"
      class="claim_center__tabs"
    >
      <el-tab-pane
        label="申领记录"
        name="claims"
      >
        <section class="card claim_center__card">
          <el-form
            :inline="true"
            class="claim_center__filters"
            @submit.prevent
          >
            <el-form-item label="状态">
              <el-select
                v-model="query.status"
                placeholder="全部状态"
                clearable
                class="claim_center__status"
              >
                <el-option
                  v-for="option in CLAIM_STATUS_OPTIONS"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="预约日期">
              <el-date-picker
                v-model="bookingDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                clearable
                class="claim_center__dates"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                @click="search"
              >
                查询
              </el-button>
              <el-button @click="resetFilters">
                重置
              </el-button>
            </el-form-item>
          </el-form>

          <el-table
            v-loading="listLoading"
            :data="records"
            class="table-scroll-x"
            row-key="id"
          >
            <el-table-column
              label="申领单"
              min-width="160"
            >
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  class="claim_center__document"
                  @click="openDetail(row.id)"
                >
                  {{ row.documentNo || row.id }}
                </el-button>
                <div class="t-hint">
                  ID：{{ row.id }}
                </div>
              </template>
            </el-table-column>
            <el-table-column
              prop="purpose"
              label="用途"
              min-width="200"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.purpose || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              label="预约时间"
              min-width="225"
            >
              <template #default="{ row }">
                <div>{{ row.bookingDate || '—' }}</div>
                <div class="t-hint">
                  {{ formatRange(row.bookingStartAt, row.bookingEndAt) }}
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="归还期限"
              min-width="170"
            >
              <template #default="{ row }">
                {{ formatInstant(row.returnDeadline) || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              label="状态"
              width="110"
            >
              <template #default="{ row }">
                <ItemStatusPill :status="row.status" />
              </template>
            </el-table-column>
            <el-table-column
              label="版本"
              width="80"
              align="center"
            >
              <template #default="{ row }">
                <span class="claim_center__revision">v{{ row.revisionNo }}</span>
              </template>
            </el-table-column>
            <el-table-column
              label="创建时间"
              min-width="165"
            >
              <template #default="{ row }">
                {{ formatInstant(row.createTime) || '—' }}
              </template>
            </el-table-column>
            <el-table-column
              label="操作"
              width="90"
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
            <template #empty>
              <div class="claim_center__empty">
                <span class="t-hint">{{ hasFilters ? '当前筛选条件下暂无申领记录' : '暂无申领记录，可从新建申领或预设方案开始' }}</span>
                <el-button
                  v-if="!hasFilters"
                  link
                  type="primary"
                  @click="openCreate()"
                >
                  新建申领
                </el-button>
              </div>
            </template>
          </el-table>

          <div class="claim_center__pager">
            <el-pagination
              v-model:current-page="query.pageNum"
              v-model:page-size="query.pageSize"
              :total="total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="changePageSize"
              @current-change="loadClaims"
            />
          </div>
        </section>
      </el-tab-pane>
      <el-tab-pane
        label="助理授权"
        name="assistants"
        lazy
      >
        <AssistantGrantPanel v-if="activeTab === 'assistants'" />
      </el-tab-pane>
      <el-tab-pane
        label="逾期归还"
        name="overdues"
        lazy
      >
        <OverdueList
          v-if="activeTab === 'overdues'"
          @open-claim="openDetail"
        />
      </el-tab-pane>
    </el-tabs>

    <ClaimFormDialog
      v-model="formVisible"
      :mode="formMode"
      :claim-id="formClaimId"
      :plan-id="formPlanId"
      @close="formVisible = false"
      @saved="onClaimSaved"
    />
    <ClaimDetailDrawer
      v-model="detailVisible"
      :claim-id="detailId"
      @close="detailVisible = false"
      @edit="openEdit"
      @rebook="openRebook"
      @changed="loadClaims"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import ClaimFormDialog from './components/ClaimFormDialog.vue'
import ClaimDetailDrawer from './components/ClaimDetailDrawer.vue'
import OverdueList from './components/OverdueList.vue'
import AssistantGrantPanel from './components/AssistantGrantPanel.vue'
import { CLAIM_STATUS_OPTIONS } from './components/claim.constants'
import { pageMyClaims } from '@/api/item/claim'
import { formatInstant, formatRange } from '@/utils/datetime'

const route = useRoute()
const router = useRouter()
const records = ref([])
const total = ref(0)
const listLoading = ref(false)
const bookingDateRange = ref([])
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: ''
})

const formVisible = ref(false)
const formMode = ref('create')
const formClaimId = ref('')
const formPlanId = ref('')
const detailVisible = ref(false)
const detailId = ref('')

const activeTab = computed({
  get: () => ['overdues', 'assistants'].includes(route.query.tab) ? route.query.tab : 'claims',
  set: (tab) => {
    const nextQuery = { ...route.query, tab }
    if (tab !== 'claims') {
      delete nextQuery.create
      delete nextQuery.planId
    }
    router.replace({ query: nextQuery })
  }
})
const hasFilters = computed(() => !!query.status || !!bookingDateRange.value?.length)

async function loadClaims() {
  listLoading.value = true
  try {
    const data = await pageMyClaims({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      bookingDateFrom: bookingDateRange.value?.[0] || undefined,
      bookingDateTo: bookingDateRange.value?.[1] || undefined,
      sortBy: 'createTime',
      sortOrder: 'desc'
    })
    records.value = (data?.records || []).map((record) => ({ ...record, id: String(record.id) }))
    total.value = Number(data?.total) || 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    listLoading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadClaims()
}

function resetFilters() {
  query.status = ''
  bookingDateRange.value = []
  search()
}

function changePageSize() {
  query.pageNum = 1
  loadClaims()
}

function openCreate(planId = '') {
  formMode.value = 'create'
  formClaimId.value = ''
  formPlanId.value = planId ? String(planId) : ''
  formVisible.value = true
}

function openEdit(detail) {
  detailVisible.value = false
  formMode.value = 'edit'
  formClaimId.value = String(detail.id)
  formPlanId.value = ''
  formVisible.value = true
}

function openRebook(detail) {
  detailVisible.value = false
  formMode.value = 'rebook'
  formClaimId.value = String(detail.id)
  formPlanId.value = ''
  formVisible.value = true
}

function openDetail(id) {
  detailId.value = String(id)
  detailVisible.value = true
}

function onClaimSaved() {
  query.pageNum = 1
  loadClaims()
}

function goToPlans() {
  router.push('/item/plans')
}

/** 方案复制参数只在本次打开表单时消费，避免浏览器前进后退或下次进入重复预填。 */
function consumeRouteIntent() {
  if (route.query.create !== '1' && !route.query.planId) return
  const planId = route.query.planId ? String(route.query.planId) : ''
  const nextQuery = { ...route.query, tab: 'claims' }
  delete nextQuery.create
  delete nextQuery.planId
  router.replace({ query: nextQuery })
  openCreate(planId)
}

watch(
  () => [route.query.create, route.query.planId],
  consumeRouteIntent
)

onMounted(() => {
  if (!['claims', 'overdues', 'assistants'].includes(route.query.tab)) {
    router.replace({ query: { ...route.query, tab: 'claims' } })
  }
  loadClaims()
  consumeRouteIntent()
})
</script>

<style scoped>
.claim_center {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.claim_center__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.claim_center__title {
  margin: 0 0 5px;
  font-size: 21px;
}

.claim_center__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.claim_center__tabs {
  margin-top: -4px;
}

.claim_center__card {
  padding: 18px 20px;
}

.claim_center__filters {
  display: flex;
  flex-wrap: wrap;
}

.claim_center__status {
  width: 150px;
}

.claim_center__dates {
  width: 270px;
}

.claim_center__document {
  padding: 0;
  font-family: var(--font-num);
}

.claim_center__revision {
  font-family: var(--font-num);
  color: var(--g2);
}

.claim_center__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.claim_center__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
}

@media (max-width: 768px) {
  .claim_center__head {
    flex-direction: column;
  }

  .claim_center__actions {
    width: 100%;
  }

  .claim_center__actions .el-button {
    flex: 1;
    margin-left: 0;
  }

  .claim_center__card {
    padding: 14px;
  }

  .claim_center__status,
  .claim_center__dates {
    width: 100%;
  }
}
</style>
