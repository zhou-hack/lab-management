<template>
  <el-drawer
    :model-value="modelValue"
    title="场地详情"
    size="520px"
    @update:model-value="(v) => !v && emit('close')"
  >
    <div
      v-if="space"
      class="space_detail"
    >
      <div class="card space_detail__head">
        <div class="space_detail__title_row">
          <h3 class="t-title space_detail__name">
            {{ space.spaceName }}
          </h3>
          <span
            class="pill"
            :class="SPACE_STATUS_MAP[space.status]?.pill"
          >
            {{ SPACE_STATUS_MAP[space.status]?.label || space.status }}
          </span>
        </div>
        <p class="t-sub">
          {{ space.location || '位置未填写' }} · {{ SPACE_TYPE_MAP[space.spaceType]?.label || space.spaceType }}
        </p>
      </div>

      <div class="card">
        <h4 class="space_detail__section">
          基础信息
        </h4>
        <el-descriptions
          :column="2"
          border
        >
          <el-descriptions-item label="场地类型">
            {{ SPACE_TYPE_MAP[space.spaceType]?.label || space.spaceType }}
          </el-descriptions-item>
          <el-descriptions-item label="容纳人数">
            {{ space.capacity }} 人
          </el-descriptions-item>
          <el-descriptions-item
            label="位置"
            :span="2"
          >
            {{ space.location || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="开放时段">
            {{ space.openStart }} - {{ space.openEnd }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            {{ SPACE_STATUS_MAP[space.status]?.label || space.status }}
          </el-descriptions-item>
          <el-descriptions-item
            label="创建时间"
          >
            {{ formatShanghai(space.createTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatShanghai(space.updateTime) }}
          </el-descriptions-item>
        </el-descriptions>
        <p class="t-hint space_detail__tz">
          开放时段为上海本地时间；时间点为 UTC 存储，此处按上海时区展示
        </p>
      </div>

      <div class="card">
        <h4 class="space_detail__section">
          设备清单
        </h4>
        <p class="space_detail__facilities">
          {{ space.facilities || '暂未登记配套设备' }}
        </p>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { SPACE_STATUS_MAP, SPACE_TYPE_MAP } from './space.constants'
import { formatInstant } from '@/utils/datetime'

defineProps({
  modelValue: { type: Boolean, default: false },
  space: { type: Object, default: null }
})

const emit = defineEmits(['close'])

/** UTC ISO 时间点按上海时区展示（不依赖浏览器本地时区） */
function formatShanghai(value) {
  return value ? formatInstant(value) : '—'
}
</script>

<style scoped>
.space_detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0 4px 24px;
}

.space_detail__head {
  padding: 18px;
}

.space_detail__title_row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.space_detail__name {
  font-size: 18px;
  margin: 0;
}

.space_detail__section {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.space_detail__tz {
  margin: 10px 0 0;
}

.space_detail__facilities {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
