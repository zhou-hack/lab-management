<template>
  <div class="page equipment_directory">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          设备目录
        </h2>
        <p class="t-sub">
          在架可申请设备一览 · 学生单由指定教师初审后转管理员终审，批准后方可借出
        </p>
      </div>
    </div>

    <div class="card">
      <div class="equipment_directory__toolbar">
        <div class="equipment_directory__search">
          <el-autocomplete
            v-model="query.assetCode"
            :fetch-suggestions="(q, cb) => suggestField(q, cb, 'assetCode')"
            placeholder="资产编号"
            clearable
            @keyup.enter="search"
            @clear="search"
            @select="search"
          />
        </div>
        <div class="equipment_directory__search">
          <el-autocomplete
            v-model="query.equipmentName"
            :fetch-suggestions="(q, cb) => suggestField(q, cb, 'equipmentName')"
            placeholder="设备名称"
            clearable
            @keyup.enter="search"
            @clear="search"
            @select="search"
          />
        </div>
        <el-button @click="search">
          查询
        </el-button>
      </div>
    </div>

    <div class="card">
      <div
        v-if="loading && !records.length"
        class="equipment_directory__skeleton"
      >
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row" />
        <span class="skeleton equipment_directory__sk_row" />
      </div>
      <div
        v-else-if="records.length"
        class="table-scroll-x"
      >
        <el-table
          v-loading="loading"
          :data="records"
        >
          <el-table-column
            prop="assetCode"
            label="资产编号"
            min-width="110"
          />
          <el-table-column
            prop="equipmentName"
            label="设备名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="modelCode"
            label="型号码"
            min-width="110"
          />
          <el-table-column
            prop="departmentName"
            label="归口部门"
            min-width="110"
          />
          <el-table-column
            label="存放位置"
            min-width="140"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.locationDetail || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="保管人"
            width="90"
          >
            <template #default="{ row }">
              {{ row.custodianName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="价值分级"
            width="90"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="valueLevelMeta(row).pill"
              >
                {{ valueLevelMeta(row).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="设备状态"
            width="100"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="usageStateMeta(row).pill"
              >
                {{ usageStateMeta(row).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="被约时间"
            width="100"
          >
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                @click="openSlots(row)"
              >
                查看详情
              </el-button>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="110"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                v-permission="'equipment:booking:apply'"
                link
                type="primary"
                @click="openApply(row)"
              >
                申请使用
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div
        v-else
        class="empty"
      >
        <el-icon
          class="empty__icon"
          :size="40"
        >
          <Monitor />
        </el-icon>
        暂无在架设备
      </div>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="equipment_directory__pager"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @change="load"
      />
    </div>

    <!-- 申请使用（共享弹窗：目录行入口预选资产） -->
    <BookingApplyDialog
      ref="applyDialogRef"
      :preset="applyPreset"
    />

    <!-- 被约时间明细：该设备进行中与未来的预约时段 -->
    <el-dialog
      v-model="slotsDialog.visible"
      :title="`被约时间 · ${slotsDialog.assetText}`"
      width="520px"
      destroy-on-close
    >
      <div
        v-if="slotsDialog.loading"
        class="equipment_directory__slots_loading"
      >
        加载中…
      </div>
      <template v-else-if="slotsDialog.records.length">
        <div
          v-for="slot in slotsDialog.records"
          :key="slot.id"
          class="equipment_directory__slot"
        >
          <span class="equipment_directory__slot_time">{{ formatRange(slot.startAt, slot.endAt) }}</span>
          <span
            class="pill"
            :class="slot.status === 'CHECKED_OUT' ? 'pill--warn' : 'pill--brand'"
          >
            {{ slot.status === 'CHECKED_OUT' ? '已借出' : '已批准' }}
          </span>
        </div>
      </template>
      <div
        v-else
        class="empty"
      >
        未来无预约，可随时申请
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Monitor } from '@element-plus/icons-vue'
import { listAssetFutureBookings, pageAssets } from '@/api/equipment'
import { VALUE_LEVEL, enumMeta } from '@/utils/enums'
import { formatRange } from '@/utils/datetime'
import BookingApplyDialog from '@/components/BookingApplyDialog.vue'

/**
 * 设备目录（学生/教师用户端）：只读在架目录（服务端收敛为 IDLE 且启用视图），
 * 「申请使用」打开共享申请弹窗（预选当前行设备）。
 */
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  assetCode: '',
  equipmentName: ''
})
const records = ref([])
const total = ref(0)
const loading = ref(false)

function valueLevelMeta(row) {
  return row.valueLevel
    ? enumMeta(row.valueLevel, VALUE_LEVEL)
    : { label: '待分类', pill: 'pill--danger' }
}

/** 占用状态展示（后端按有效预约单实时计算） */
const USAGE_STATE = {
  IN_USE: { label: '使用中', pill: 'pill--warn' },
  RESERVED: { label: '未来被约', pill: 'pill--brand' },
  FREE: { label: '无预约', pill: 'pill--muted' }
}

function usageStateMeta(row) {
  return enumMeta(row.usageState, USAGE_STATE, '无预约')
}

/* ---------------- 被约时间明细 ---------------- */

const slotsDialog = reactive({ visible: false, loading: false, assetText: '', records: [] })

async function openSlots(row) {
  slotsDialog.assetText = `${row.assetCode} · ${row.equipmentName}`
  slotsDialog.records = []
  slotsDialog.visible = true
  slotsDialog.loading = true
  try {
    slotsDialog.records = (await listAssetFutureBookings(row.id)) || []
    // 用实时时段校正行占用状态：usageState 是列表加载时的快照，
    // 页面打开后新批准的预约不会自动反映（与后端 resolveUsageStates 同规则）
    const now = Date.now()
    const inUse = slotsDialog.records.some(
      (s) => s.status === 'CHECKED_OUT' || (Date.parse(s.startAt) <= now && Date.parse(s.endAt) > now)
    )
    const reserved = slotsDialog.records.some(
      (s) => s.status === 'APPROVED' && Date.parse(s.startAt) > now
    )
    row.usageState = inUse ? 'IN_USE' : reserved ? 'RESERVED' : 'FREE'
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    slotsDialog.loading = false
  }
}

async function load() {
  loading.value = true
  try {
    const params = { pageNum: query.pageNum, pageSize: query.pageSize }
    if (query.assetCode.trim()) {
      params.assetCode = query.assetCode.trim()
    }
    if (query.equipmentName.trim()) {
      params.equipmentName = query.equipmentName.trim()
    }
    const data = await pageAssets(params)
    records.value = data.records || []
    total.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

/**
 * typeahead 联想：按输入片段查目录视图（老师可及的收敛接口）前 10 条，
 * 取对应字段去重映射为 { value }；名称与后端过滤的型号名称同源，语义一致。
 * 失败静默返回空（查询错误由拦截器统一提示，输入辅助不重复打扰）。
 */
async function suggestField(queryString, cb, field) {
  try {
    const params = { pageNum: 1, pageSize: 10 }
    if (queryString && queryString.trim()) {
      params[field] = queryString.trim()
    }
    const data = await pageAssets(params)
    const seen = new Set()
    const list = []
    for (const row of data.records || []) {
      const text = row[field]
      if (text && !seen.has(text)) {
        seen.add(text)
        list.push({ value: text })
      }
    }
    cb(list)
  } catch {
    cb([])
  }
}

/* ---------------- 申请使用 ---------------- */

const applyDialogRef = ref()
const applyPreset = ref(null)

function openApply(row) {
  applyPreset.value = {
    id: row.id,
    assetCode: row.assetCode,
    equipmentName: row.equipmentName
  }
  applyDialogRef.value?.open()
}

onMounted(load)
</script>

<style scoped>
.equipment_directory__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

/* 搜索框外套固定宽度容器：el-autocomplete 会把 class 透传到内部 el-input，
   scoped 选择器命不中，故宽度控制在 wrapper 上 */
.equipment_directory__search {
  width: 180px;
  flex: none;
}

/* 加载骨架：模拟表格行位（规范 7.8） */
.equipment_directory__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 2px 10px;
}

.equipment_directory__sk_row {
  height: 36px;
  border-radius: var(--r-sm);
}

.equipment_directory__pager {
  margin-top: 14px;
  justify-content: flex-end;
}

/* 被约时间明细 */
.equipment_directory__slot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 2px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.equipment_directory__slot:last-child {
  border-bottom: none;
}

.equipment_directory__slot_time {
  font-variant-numeric: tabular-nums;
}

.equipment_directory__slots_loading {
  padding: 16px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}
</style>
