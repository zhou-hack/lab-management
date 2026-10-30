<template>
  <el-dialog
    :model-value="modelValue"
    :title="`维修时段：${space ? space.labName : ''}`"
    width="640px"
    destroy-on-close
    @update:model-value="(v) => !v && emit('close')"
  >
    <p class="mw__hint t-hint">
      维修时段内该场地禁止新预约；与已批准预约重叠时将被拒绝，需先取消重叠预约。取消时段即时生效。
    </p>

    <!-- 生效中的维修时段 -->
    <h4 class="mw__section">
      生效中的维修时段（{{ windows.length }}）
    </h4>
    <el-table
      v-loading="loading"
      :data="windows"
      size="small"
    >
      <el-table-column
        label="维修时段（上海）"
        min-width="200"
      >
        <template #default="{ row }">
          {{ formatRange(row.startAt, row.endAt) }}
        </template>
      </el-table-column>
      <el-table-column
        prop="reason"
        label="事由"
        min-width="140"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.reason || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="80"
      >
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            @click="onCancel(row)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="t-hint">暂无生效中的维修时段</span>
      </template>
    </el-table>

    <!-- 新增维修时段 -->
    <h4 class="mw__section">
      新增维修时段
    </h4>
    <el-form
      label-position="top"
      @submit.prevent
    >
      <el-form-item label="起止时间（上海本地）">
        <el-date-picker
          v-model="range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="维修开始"
          end-placeholder="维修结束"
          format="YYYY-MM-DD HH:mm"
          value-format="YYYY-MM-DD HH:mm"
          :disabled-date="disablePast"
          class="mw__range"
        />
      </el-form-item>
      <el-form-item label="维修事由（选填）">
        <el-input
          v-model="reason"
          maxlength="512"
          placeholder="如：空调检修 / 地板打蜡"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('close')">
        关闭
      </el-button>
      <el-button
        type="primary"
        :loading="saving"
        @click="onCreate"
      >
        新增时段
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelMaintenanceWindow, createMaintenanceWindow, listMaintenanceWindows } from '@/api/space'
import { formatRange, todayShanghai, toUtcIso } from '@/utils/datetime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 场地行（SpaceListVO：id/labName） */
  space: { type: Object, default: null }
})

const emit = defineEmits(['close', 'changed'])

const windows = ref([])
const loading = ref(false)
const saving = ref(false)
const range = ref(null)
const reason = ref('')

function disablePast(date) {
  const [y, m, d] = todayShanghai().split('-').map(Number)
  return date.getTime() < Date.UTC(y, m - 1, d)
}

async function load() {
  if (!props.space) return
  loading.value = true
  try {
    windows.value = await listMaintenanceWindows(props.space.id)
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  if (!range.value || range.value.length !== 2) {
    ElMessage.warning('请选择维修起止时间')
    return
  }
  const startAt = toUtcIso(range.value[0])
  const endAt = toUtcIso(range.value[1])
  saving.value = true
  try {
    await createMaintenanceWindow(props.space.id, { startAt, endAt, reason: reason.value || undefined })
    ElMessage.success('维修时段已设置')
    range.value = null
    reason.value = ''
    await load()
    emit('changed')
  } catch {
    // 409（撞预约/撞维修时段）等错误提示由 Axios 拦截器统一展示后端 msg
  } finally {
    saving.value = false
  }
}

async function onCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消维修时段「${formatRange(row.startAt, row.endAt)}」吗？`,
      '取消维修时段',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }
  await cancelMaintenanceWindow(props.space.id, row.id)
  ElMessage.success('维修时段已取消')
  await load()
  emit('changed')
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      range.value = null
      reason.value = ''
      load()
    }
  }
)
</script>

<style scoped>
.mw__hint {
  margin: 0 0 12px;
  line-height: 1.7;
}

.mw__section {
  margin: 14px 0 10px;
  font-size: 14px;
}

.mw__range {
  width: 100%;
}
</style>
