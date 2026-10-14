<template>
  <el-drawer
    :model-value="modelValue"
    title="场地详情"
    size="520px"
    @update:model-value="(v) => !v && emit('close')"
  >
    <div
      v-if="lab"
      class="lab_detail"
    >
      <div class="card lab_detail__head">
        <div class="lab_detail__title_row">
          <h3 class="t-title lab_detail__name">
            {{ lab.labName }}
          </h3>
          <span
            class="pill"
            :class="LAB_STATUS_MAP[lab.status]?.pill"
          >
            {{ LAB_STATUS_MAP[lab.status]?.label || lab.status }}
          </span>
        </div>
        <p class="t-sub">
          {{ lab.campusName }} / {{ lab.buildingName }} · {{ lab.floorNo }}-{{ lab.roomNo }}
        </p>
        <p class="t-hint lab_detail__source">
          {{ lab.statusSource || '状态由后端实时派生' }}
        </p>
      </div>

      <div class="card">
        <h4 class="lab_detail__section">
          基础信息
        </h4>
        <el-descriptions
          :column="2"
          border
        >
          <el-descriptions-item label="场地编码">
            {{ lab.labCode }}
          </el-descriptions-item>
          <el-descriptions-item label="场地类型">
            {{ LAB_TYPE_MAP[lab.labType]?.label || lab.labType }}
          </el-descriptions-item>
          <el-descriptions-item label="归口部门">
            {{ lab.departmentName || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="容纳人数">
            {{ lab.capacity }} 人
          </el-descriptions-item>
          <el-descriptions-item label="面积">
            {{ lab.areaSqm ?? '—' }} ㎡
          </el-descriptions-item>
          <el-descriptions-item label="启用状态">
            {{ lab.enabled === 1 ? '启用' : '停用' }}
          </el-descriptions-item>
          <el-descriptions-item
            label="位置描述"
            :span="2"
          >
            {{ lab.locationNote || '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="card">
        <div class="lab_detail__section_row">
          <h4 class="lab_detail__section">
            开放规则
          </h4>
          <span class="t-hint">上海本地节次时间（不做时区转换）</span>
        </div>
        <el-table
          :data="lab.openRules || []"
          size="small"
          class="table-scroll-x"
        >
          <el-table-column
            label="星期"
            width="70"
          >
            <template #default="{ row }">
              {{ weekdayLabel(row.weekday) }}
            </template>
          </el-table-column>
          <el-table-column label="开放时段">
            <template #default="{ row }">
              {{ row.openTime }} - {{ row.closeTime }}
            </template>
          </el-table-column>
          <el-table-column
            label="有效期"
            min-width="190"
          >
            <template #default="{ row }">
              {{ row.validFrom }} ~ {{ row.validTo }}
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="70"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="row.enabled === 1 ? 'pill--ok' : 'pill--muted'"
              >
                {{ row.enabled === 1 ? '生效' : '停用' }}
              </span>
            </template>
          </el-table-column>
          <template #empty>
            <span class="t-hint">暂未配置开放规则</span>
          </template>
        </el-table>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { LAB_STATUS_MAP, LAB_TYPE_MAP, weekdayLabel } from './space.constants'

defineProps({
  modelValue: { type: Boolean, default: false },
  lab: { type: Object, default: null }
})

const emit = defineEmits(['close'])
</script>

<style scoped>
.lab_detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0 4px 24px;
}

.lab_detail__head {
  padding: 18px;
}

.lab_detail__title_row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.lab_detail__name {
  font-size: 18px;
  margin: 0;
}

.lab_detail__source {
  margin-top: 8px;
}

.lab_detail__section {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.lab_detail__section_row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
</style>
