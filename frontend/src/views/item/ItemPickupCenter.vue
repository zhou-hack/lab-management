<template>
  <div class="pickup_center">
    <div class="pickup_center__head">
      <div>
        <h2 class="t-title pickup_center__title">
          我的代领任务
        </h2>
        <p class="t-sub">
          查看老师推送给我的代领安排、预约时间、物料和计划领取库位；实际出库由管理员办理。
        </p>
      </div>
      <el-button
        :loading="listLoading"
        @click="loadPickups"
      >
        刷新
      </el-button>
    </div>

    <section class="card pickup_center__card">
      <el-form
        :inline="true"
        class="pickup_center__filters"
        @submit.prevent
      >
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部状态"
            clearable
            class="pickup_center__status"
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
            class="pickup_center__dates"
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
          min-width="170"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              class="pickup_center__document"
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
          label="老师"
          min-width="120"
        >
          <template #default="{ row }">
            {{ row.applicantName || row.applicantId || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="用途"
          min-width="180"
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
          min-width="165"
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
          label="推送时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.delegatedAt) || '—' }}
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
          <span class="t-hint">{{ hasFilters ? '当前筛选条件下暂无代领任务' : '暂无老师推送的代领任务' }}</span>
        </template>
      </el-table>

      <div class="pickup_center__pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="changePageSize"
          @current-change="loadPickups"
        />
      </div>
    </section>

    <el-drawer
      v-model="detailVisible"
      title="代领任务详情"
      size="min(900px, 96vw)"
      destroy-on-close
    >
      <div
        v-loading="detailLoading"
        class="pickup_detail"
      >
        <template v-if="detail">
          <section class="card pickup_detail__section">
            <div class="pickup_detail__title">
              <div>
                <h3 class="t-title">
                  {{ detail.documentNo || detail.id }}
                </h3>
                <p class="t-sub">
                  {{ detail.applicantName || detail.applicantId }} · {{ detail.purpose || '未填写用途' }}
                </p>
              </div>
              <ItemStatusPill :status="detail.status" />
            </div>
            <el-descriptions
              :column="2"
              border
            >
              <el-descriptions-item label="申领单 ID">
                {{ detail.id }}
              </el-descriptions-item>
              <el-descriptions-item label="老师">
                {{ detail.applicantName || detail.applicantId || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="预约日期">
                {{ detail.bookingDate || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="预约时段">
                {{ formatRange(detail.bookingStartAt, detail.bookingEndAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="归还期限">
                {{ formatInstant(detail.returnDeadline) || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="推送时间">
                {{ formatInstant(detail.delegatedAt) || '—' }}
              </el-descriptions-item>
              <el-descriptions-item
                label="用途"
                :span="2"
              >
                {{ detail.purpose || '—' }}
              </el-descriptions-item>
            </el-descriptions>
          </section>

          <section class="card pickup_detail__section">
            <h4 class="pickup_detail__section_title">
              代领物料
            </h4>
            <el-table
              :data="detail.lines || []"
              class="table-scroll-x"
              empty-text="暂无物料明细"
              row-key="id"
            >
              <el-table-column
                label="物料"
                min-width="200"
              >
                <template #default="{ row }">
                  <div class="t-strong">
                    {{ row.materialName || row.materialId }}
                  </div>
                  <div class="t-hint">
                    {{ [row.specification, materialTypeText(row.materialType)].filter(Boolean).join(' · ') }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="申请数量"
                min-width="120"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.requestedQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="已实发"
                min-width="120"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.issuedQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
              <el-table-column
                label="未领取"
                min-width="120"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText
                    :value="row.unclaimedQuantity"
                    :unit="row.baseUnit"
                  />
                </template>
              </el-table-column>
            </el-table>
          </section>

          <section class="card pickup_detail__section">
            <h4 class="pickup_detail__section_title">
              计划领取库位
            </h4>
            <el-table
              :data="detail.allocations || []"
              class="table-scroll-x"
              empty-text="暂无计划领取库位"
              row-key="id"
            >
              <el-table-column
                label="物料"
                min-width="190"
              >
                <template #default="{ row }">
                  {{ materialName(row.materialId) }}
                </template>
              </el-table-column>
              <el-table-column
                label="库位"
                min-width="190"
              >
                <template #default="{ row }">
                  <div>{{ row.locationName || row.locationId || '—' }}</div>
                  <div class="t-hint">
                    {{ row.locationCode || '' }}
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="计划数量"
                min-width="120"
                align="right"
              >
                <template #default="{ row }">
                  <ItemQuantityText :value="row.plannedQuantity" />
                </template>
              </el-table-column>
            </el-table>
            <p class="t-hint pickup_detail__notice">
              计划库位仅供参考，实际领取库位以管理员实发结果为准。
            </p>
          </section>
        </template>
        <el-empty
          v-else-if="!detailLoading"
          description="代领任务不存在或已无权查看"
          :image-size="80"
        />
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">
          关闭
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import ItemStatusPill from '@/components/item/ItemStatusPill.vue'
import { getPickupDetail, pagePickups } from '@/api/item/pickup'
import { formatInstant, formatRange } from '@/utils/datetime'
import { CLAIM_STATUS_OPTIONS } from './components/claim.constants'

const records = ref([])
const total = ref(0)
const listLoading = ref(false)
const bookingDateRange = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, status: '' })
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const detailId = ref('')
let detailSerial = 0

const hasFilters = computed(() => !!query.status || !!bookingDateRange.value?.length)

async function loadPickups() {
  listLoading.value = true
  try {
    const result = await pagePickups({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status || undefined,
      bookingDateFrom: bookingDateRange.value?.[0] || undefined,
      bookingDateTo: bookingDateRange.value?.[1] || undefined,
      sortBy: 'createTime',
      sortOrder: 'desc'
    })
    records.value = (result?.records || []).map((record) => ({ ...record, id: String(record.id) }))
    total.value = Number(result?.total) || 0
  } catch {
    records.value = []
    total.value = 0
  } finally {
    listLoading.value = false
  }
}

function search() {
  query.pageNum = 1
  loadPickups()
}

function resetFilters() {
  query.status = ''
  bookingDateRange.value = []
  search()
}

function changePageSize() {
  query.pageNum = 1
  loadPickups()
}

function openDetail(id) {
  detailId.value = String(id)
  detailVisible.value = true
}

function materialTypeText(type) {
  return type === 'FIXED_ASSET' ? '固定资产' : type === 'CONSUMABLE' ? '耗材' : ''
}

function materialName(materialId) {
  return detail.value?.lines?.find((line) => String(line.materialId) === String(materialId))?.materialName
    || materialId || '—'
}

async function loadDetail() {
  const serial = ++detailSerial
  detail.value = null
  if (!detailId.value) return
  detailLoading.value = true
  try {
    const result = await getPickupDetail(detailId.value)
    if (serial === detailSerial) detail.value = result
  } catch {
    if (serial === detailSerial) detail.value = null
  } finally {
    if (serial === detailSerial) detailLoading.value = false
  }
}

watch(detailVisible, (visible) => {
  if (visible) loadDetail()
  else {
    detailSerial += 1
    detail.value = null
  }
})

onMounted(loadPickups)
</script>

<style scoped>
.pickup_center,
.pickup_detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pickup_center__head,
.pickup_detail__title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.pickup_center__title {
  margin: 0 0 5px;
  font-size: 21px;
}

.pickup_center__card,
.pickup_detail__section {
  padding: 18px 20px;
}

.pickup_center__filters {
  display: flex;
  flex-wrap: wrap;
}

.pickup_center__status {
  width: 150px;
}

.pickup_center__dates {
  width: 270px;
}

.pickup_center__document {
  padding: 0;
  font-family: var(--font-num);
}

.pickup_center__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.pickup_detail {
  min-height: 240px;
}

.pickup_detail__title {
  margin-bottom: 16px;
}

.pickup_detail__title h3,
.pickup_detail__title p {
  margin: 0;
}

.pickup_detail__title h3 {
  margin-bottom: 5px;
  font-size: 18px;
}

.pickup_detail__section_title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.pickup_detail__notice {
  margin: 10px 0 0;
}

@media (max-width: 768px) {
  .pickup_center__head {
    flex-direction: column;
  }

  .pickup_center__card,
  .pickup_detail__section {
    padding: 14px;
  }

  .pickup_center__status,
  .pickup_center__dates {
    width: 100%;
  }
}
</style>
