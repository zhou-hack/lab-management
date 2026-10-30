<template>
  <div class="plan_list">
    <div class="plan_list__head">
      <div>
        <h2 class="t-title plan_list__title">
          我的物料预设方案
        </h2>
        <p class="t-sub">
          维护常用物料及默认数量，申领时可一键载入后重新选择日期、节次和计划分配。
        </p>
      </div>
      <el-button
        type="primary"
        @click="openCreate"
      >
        新建方案
      </el-button>
    </div>

    <section class="card plan_list__card">
      <div class="plan_list__toolbar">
        <el-form
          :inline="true"
          @submit.prevent
        >
          <el-form-item label="启用状态">
            <el-select
              v-model="enabledFilter"
              class="plan_list__filter"
              @change="load"
            >
              <el-option
                label="全部方案"
                value=""
              />
              <el-option
                label="仅启用"
                :value="1"
              />
              <el-option
                label="仅停用"
                :value="0"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <el-button
          :loading="loading"
          @click="load"
        >
          刷新
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="plans"
        class="table-scroll-x"
        row-key="id"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="plan_list__lines">
              <h4 class="plan_list__lines_title">
                方案物料（{{ row.lines?.length || 0 }} 项）
              </h4>
              <el-table
                :data="row.lines || []"
                class="table-scroll-x"
                empty-text="方案暂无物料"
              >
                <el-table-column
                  label="物料"
                  min-width="220"
                >
                  <template #default="{ row: line }">
                    <div class="t-strong">
                      {{ line.materialName || line.materialId }}
                    </div>
                    <div class="t-hint">
                      {{ [line.specification, materialTypeText(line.materialType)].filter(Boolean).join(' · ') }}
                    </div>
                  </template>
                </el-table-column>
                <el-table-column
                  label="默认数量"
                  min-width="130"
                  align="right"
                >
                  <template #default="{ row: line }">
                    <ItemQuantityText
                      :value="line.defaultQuantity"
                      :unit="line.baseUnit"
                    />
                  </template>
                </el-table-column>
                <el-table-column
                  label="物料 ID"
                  min-width="170"
                >
                  <template #default="{ row: line }">
                    <span class="t-hint">{{ line.materialId }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="方案名称"
          min-width="200"
        >
          <template #default="{ row }">
            <div class="plan_list__name">
              {{ row.planName }}
            </div>
            <div class="t-hint">
              方案 ID：{{ row.id }}
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="物料"
          min-width="220"
        >
          <template #default="{ row }">
            <div
              v-if="row.lines?.length"
              class="plan_list__material_summary"
            >
              {{ row.lines.map((line) => line.materialName || line.materialId).join('、') }}
            </div>
            <span
              v-else
              class="t-hint"
            >暂无物料</span>
            <div class="t-hint">
              共 {{ row.lines?.length || 0 }} 项，点击左侧展开查看默认数量
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="remark"
          label="备注"
          min-width="170"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.remark || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="110"
        >
          <template #default="{ row }">
            <span
              class="pill"
              :class="Number(row.enabled) === 1 ? 'pill--ok' : 'pill--muted'"
            >
              {{ Number(row.enabled) === 1 ? '启用中' : '已停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="启停"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-switch
              :model-value="Number(row.enabled) === 1"
              :loading="togglingId === String(row.id)"
              inline-prompt
              active-text="启"
              inactive-text="停"
              @change="(value) => togglePlan(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="更新时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.updateTime || row.createTime) || '—' }}
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
              @click="copyToClaim(row)"
            >
              复制到申领
            </el-button>
            <el-button
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="plan_list__empty">
            <span class="t-hint">{{ enabledFilter === '' ? '暂无预设方案，可点击“新建方案”添加常用物料' : '当前筛选条件下暂无方案' }}</span>
            <el-button
              v-if="enabledFilter === ''"
              link
              type="primary"
              @click="openCreate"
            >
              立即新建
            </el-button>
          </div>
        </template>
      </el-table>
    </section>

    <PlanFormDialog
      v-model="formVisible"
      :plan-id="editingPlanId"
      @close="formVisible = false"
      @saved="onSaved"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ItemQuantityText from '@/components/item/ItemQuantityText.vue'
import PlanFormDialog from './components/PlanFormDialog.vue'
import { listMaterialPlans, updateMaterialPlan } from '@/api/item/plan'
import { formatInstant } from '@/utils/datetime'

const router = useRouter()
const plans = ref([])
const enabledFilter = ref('')
const loading = ref(false)
const formVisible = ref(false)
const editingPlanId = ref('')
const togglingId = ref('')

function materialTypeText(type) {
  return type === 'FIXED_ASSET' ? '固定资产' : type === 'CONSUMABLE' ? '耗材' : ''
}

async function load() {
  loading.value = true
  try {
    const result = await listMaterialPlans(enabledFilter.value)
    plans.value = (result || []).map((plan) => ({ ...plan, id: String(plan.id) }))
  } catch {
    plans.value = []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingPlanId.value = ''
  formVisible.value = true
}

function openEdit(row) {
  editingPlanId.value = String(row.id)
  formVisible.value = true
}

function onSaved() {
  load()
}

async function togglePlan(row, enabled) {
  togglingId.value = String(row.id)
  try {
    await updateMaterialPlan(String(row.id), {
      planName: row.planName,
      enabled: enabled ? 1 : 0,
      remark: row.remark || undefined,
      lines: (row.lines || []).map((line) => ({
        materialId: String(line.materialId),
        defaultQuantity: Number(line.defaultQuantity)
      }))
    })
    ElMessage.success(enabled ? '方案已启用' : '方案已停用')
    await load()
  } finally {
    togglingId.value = ''
  }
}

async function copyToClaim(row) {
  await router.push({
    path: '/item/claims',
    query: { tab: 'claims', create: '1', planId: String(row.id) }
  })
}

onMounted(load)
</script>

<style scoped>
.plan_list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.plan_list__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.plan_list__title {
  margin: 0 0 5px;
  font-size: 21px;
}

.plan_list__card {
  padding: 18px 20px;
}

.plan_list__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.plan_list__filter {
  width: 150px;
}

.plan_list__name {
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.plan_list__material_summary {
  display: -webkit-box;
  overflow: hidden;
  line-height: 1.6;
  color: var(--g2);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.plan_list__lines {
  padding: 10px 22px 16px 54px;
  background: var(--surface-sunken);
}

.plan_list__lines_title {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: var(--fw-semibold);
  color: var(--g2);
}

.plan_list__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 14px;
}

@media (max-width: 768px) {
  .plan_list__head {
    flex-direction: column;
  }

  .plan_list__card {
    padding: 14px;
  }

  .plan_list__lines {
    padding: 8px;
  }
}
</style>
