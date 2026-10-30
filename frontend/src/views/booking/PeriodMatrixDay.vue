<template>
  <div
    v-loading="loading"
    class="pmx"
  >
    <p
      v-if="!date"
      class="t-hint pmx__empty"
    >
      请先选择日期
    </p>
    <template v-else>
      <!-- 工具条：检索 + 类型 + 仅看可约 + 折叠控制 -->
      <div class="pmx__toolbar">
        <el-input
          v-model="keyword"
          placeholder="检索场地名称 / 位置"
          clearable
          class="pmx__search"
        >
          <template #prefix>
            <span class="pmx__search_icon">⌕</span>
          </template>
        </el-input>
        <el-radio-group
          v-model="typeFilter"
          size="small"
        >
          <el-radio-button value="">
            全部类型
          </el-radio-button>
          <el-radio-button value="CLASSROOM">
            教室
          </el-radio-button>
          <el-radio-button value="TRAINING_ROOM">
            实训室
          </el-radio-button>
        </el-radio-group>
        <el-checkbox
          v-model="onlyBookable"
          size="small"
        >
          仅看当前可约
        </el-checkbox>
        <el-button
          link
          type="primary"
          size="small"
          @click="toggleAll"
        >
          {{ allCollapsed ? '展开全部' : '折叠全部' }}
        </el-button>
        <span class="pmx__count t-hint">{{ visibleCount }} / {{ labs.length }} 场地</span>
      </div>

      <p
        v-if="!groups.length && !loading"
        class="t-hint pmx__empty"
      >
        无匹配场地，可调整筛选条件
      </p>

      <div
        v-else
        class="pmx__groups"
      >
        <div
          v-for="g in groups"
          :key="g.region"
          class="pmx__group"
        >
          <!-- 组头：区域名 + 空闲聚合 + 折叠开关 -->
          <button
            type="button"
            class="pmx__group_head"
            @click="toggleGroup(g.region)"
          >
            <span
              class="pmx__group_arrow"
              :class="{ 'pmx__group_arrow--open': !collapsed[g.region] }"
            >▸</span>
            <b>{{ g.region }}</b>
            <span class="pmx__group_stat">空闲 {{ g.freeCount }}/{{ g.labs.length }}</span>
            <span
              v-if="g.occupiedCount"
              class="pmx__group_stat pmx__group_stat--busy"
            >占用中 {{ g.occupiedCount }}</span>
          </button>

          <!-- 聚合行（折叠态）：各节次该组空闲场地数 -->
          <table
            v-if="collapsed[g.region]"
            class="pmx__table"
          >
            <tbody>
              <tr>
                <th class="pmx__rowhead pmx__rowhead--agg">
                  组内空闲
                </th>
                <td
                  v-for="p in periods"
                  :key="p.periodNo"
                  class="pmx__cell pmx__cell--agg"
                  :class="{ 'pmx__cell--agg_zero': g.freeByPeriod[p.periodNo] === 0 }"
                  :title="`${g.region} ${p.periodName}：空闲 ${g.freeByPeriod[p.periodNo] ?? 0}/${g.labs.length} 间`"
                >
                  {{ g.freeByPeriod[p.periodNo] ?? 0 }}/{{ g.labs.length }}
                </td>
              </tr>
            </tbody>
          </table>

          <!-- 明细行（展开态）：场地 × 节次；表头为节次名+时刻（sticky） -->
          <table
            v-else
            class="pmx__table"
          >
            <thead>
              <tr>
                <th class="pmx__rowhead pmx__rowhead--corner">
                  场地 \ 节次
                </th>
                <th
                  v-for="p in periods"
                  :key="p.periodNo"
                  class="pmx__colhead"
                  :class="{ 'pmx__colhead--active': Number(activePeriodNo) === Number(p.periodNo) }"
                >
                  <b>{{ p.periodName }}</b>
                  <span>{{ p.startClock }}-{{ p.endClock }}</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="lab in g.labs"
                :key="lab.spaceId"
              >
                <th
                  class="pmx__rowhead"
                  :class="{ 'pmx__rowhead--active': String(lab.spaceId) === String(activeSpaceId) }"
                >
                  <span class="pmx__lab_top">
                    <span
                      class="pmx__lab_name"
                      :title="lab.labName"
                    >{{ lab.labName }}</span>
                    <span
                      class="pill pmx__state"
                      :class="stateOf(lab).pill"
                    >{{ stateOf(lab).label }}</span>
                  </span>
                  <span class="pmx__lab_meta">{{ lab.labTypeName || '场地' }} · {{ lab.capacity }}人<template v-if="lab.locationDetail"> · {{ lab.locationDetail }}</template></span>
                </th>
                <td
                  v-for="p in periods"
                  :key="p.periodNo"
                  class="pmx__cell"
                  :class="cellClass(lab, p)"
                  :title="cellTip(lab, p)"
                  @click="onCellClick(lab, p)"
                >
                  <span
                    v-if="cellOf(lab, p).state === 'occupied'"
                    class="pmx__who"
                  >
                    {{ cellOf(lab, p).text }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { nowShanghaiClock } from '@/utils/datetime'
import { buildPeriodCellMap, labStateOf, LAB_STATE_MAP } from './booking.constants'

/**
 * 节次×场地 日矩阵（行列转置版：行=场地按区域分组，列=节次一屏放下）。
 * 面向大量场地场景：关键词检索 / 类型筛选 / 仅看可约 / 分组折叠展开 /
 * 折叠态组级聚合（各节次空闲数）。单元格状态口径由
 * booking.constants.buildPeriodCellMap 统一派生（父组件区间校验同源）。
 * 教师点选空格发起区间选择，学生只读。
 */
const props = defineProps({
  periods: { type: Array, default: () => [] },
  labs: { type: Array, default: () => [] },
  date: { type: String, default: '' },
  today: { type: String, required: true },
  readonly: { type: Boolean, default: false },
  activeSpaceId: { type: [String, Number], default: '' },
  selectedRange: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  /** 分钟级跳变时钟（父组件定时器驱动）：触发 past/pending 状态随时刻流转 */
  nowTick: { type: Number, default: 0 },
  /** 节次筛选联动的列高亮（periodNo；空值不高亮） */
  activePeriodNo: { type: [String, Number], default: null }
})

const emit = defineEmits(['pick'])

const keyword = ref('')
const typeFilter = ref('')
const onlyBookable = ref(false)
/** 区域折叠态：缺省展开（key 出现即为折叠） */
const collapsed = reactive({})

/** 全量单元格一次派生；nowTick 参与依赖，分钟级自动重算 */
const cellMap = computed(() => {
  void props.nowTick
  return buildPeriodCellMap(props.labs, props.periods, props.date, props.today, nowShanghaiClock())
})

function cellOf(lab, p) {
  return cellMap.value.get(`${lab.spaceId}:${p.periodNo}`) || { state: 'free', text: '' }
}

/** 行头当前状态徽章（原占用总览四态口径；nowTick 驱动随时刻流转） */
function stateOf(lab) {
  void props.nowTick
  return LAB_STATE_MAP[labStateOf(lab, props.date, props.today, Date.now())]
}

/** 区域提取：与占用总览同口径（实训楼/教学楼/X楼，否则其他区域） */
function regionOf(lab) {
  const src = `${lab.locationDetail || ''} ${lab.labName || ''}`
  const m = src.match(/(实训楼|教学楼|[A-Z]\s*楼)/i)
  return m ? m[1].replace(/\s+/g, '') : '其他区域'
}

/** 工具条过滤后的场地 */
const filteredLabs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return (props.labs || []).filter((lab) => {
    if (typeFilter.value && lab.labType !== typeFilter.value) return false
    if (kw && !`${lab.labName} ${lab.locationDetail || ''}`.toLowerCase().includes(kw)) return false
    if (onlyBookable.value) {
      // 存在至少一个可点选（free）单元格才保留
      const hasFree = props.periods.some((p) => cellOf(lab, p).state === 'free')
      if (!hasFree) return false
    }
    return true
  })
})

const visibleCount = computed(() => filteredLabs.value.length)

/** 分组 + 组内聚合（折叠态的各节次空闲数） */
const groups = computed(() => {
  const byRegion = new Map()
  for (const lab of filteredLabs.value) {
    const region = regionOf(lab)
    if (!byRegion.has(region)) byRegion.set(region, [])
    byRegion.get(region).push(lab)
  }
  return [...byRegion.entries()]
    .sort((a, b) => a[0].localeCompare(b[0], 'zh'))
    .map(([region, labs]) => {
      const freeByPeriod = {}
      let freeLabs = 0
      let occupiedLabs = 0
      for (const lab of labs) {
        let labFree = 0
        let labOccupied = 0
        for (const p of props.periods) {
          const { state } = cellOf(lab, p)
          if (state === 'free') {
            labFree++
            freeByPeriod[p.periodNo] = (freeByPeriod[p.periodNo] || 0) + 1
          } else if (state === 'occupied') {
            labOccupied++
          }
        }
        if (labFree > 0) freeLabs++
        if (labOccupied > 0) occupiedLabs++
      }
      return { region, labs, freeByPeriod, freeCount: freeLabs, occupiedCount: occupiedLabs }
    })
})

const allCollapsed = computed(() =>
  groups.value.length > 0 && groups.value.every((g) => collapsed[g.region]))

function toggleGroup(region) {
  collapsed[region] = !collapsed[region]
}

function toggleAll() {
  const target = !allCollapsed.value
  for (const g of groups.value) {
    collapsed[g.region] = target
  }
}

function isSelected(lab, p) {
  if (String(lab.spaceId) !== String(props.activeSpaceId) || !props.selectedRange.length) return false
  const [start, end] = props.selectedRange
  return p.periodNo >= start && p.periodNo <= end
}

function cellClass(lab, p) {
  const { state } = cellOf(lab, p)
  return [`pmx__cell--${state}`, { 'pmx__cell--selected': isSelected(lab, p) }]
}

const STATE_TIP = {
  off: '场地不可用',
  past: '已过去 · 不可选',
  occupied: '已占用',
  pending: '下一节即将占用 · 谨慎选择',
  free: '空闲 · 可点选'
}

function cellTip(lab, p) {
  const { state, text } = cellOf(lab, p)
  const base = `${lab.labName} ${p.periodName}（${p.startClock}-${p.endClock}）`
  return state === 'occupied' ? `${base} · 已占用：${text}` : `${base} · ${STATE_TIP[state]}`
}

function onCellClick(lab, p) {
  const { state } = cellOf(lab, p)
  if (props.readonly || state !== 'free') return
  emit('pick', { lab, periodNo: p.periodNo })
}
</script>

<style scoped>
.pmx__empty {
  padding: 24px 0;
  text-align: center;
}

.pmx__toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.pmx__search {
  width: 220px;
}

.pmx__search_icon {
  color: #9ca3af;
}

.pmx__count {
  margin-left: auto;
}

.pmx__groups {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pmx__group_head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 10px;
  margin-bottom: 4px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #f8fafc;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  text-align: left;
}

.pmx__group_head:hover {
  border-color: #8db8f7;
  background: #f3f7ff;
}

.pmx__group_arrow {
  display: inline-block;
  transition: transform 0.15s ease;
  color: #9ca3af;
}

.pmx__group_arrow--open {
  transform: rotate(90deg);
}

.pmx__group_stat {
  font-size: 12px;
  color: #16a34a;
  font-weight: 500;
}

.pmx__group_stat--busy {
  color: #d4380d;
}

.pmx__table {
  border-collapse: separate;
  border-spacing: 3px;
  width: 100%;
  table-layout: fixed;
}

/* 节次列表头：sticky 顶部，纵向滚动场地时保持节次可读 */
.pmx__table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
}

.pmx__colhead {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 3px 4px;
  text-align: center;
  vertical-align: middle;
}

.pmx__colhead b {
  display: block;
  font-size: 12px;
  color: #374151;
}

.pmx__colhead span {
  font-size: 10.5px;
  color: #9ca3af;
  font-weight: 400;
}

.pmx__colhead--active {
  background: #ecf3ff;
  border-color: #2f6bff;
}

.pmx__lab_top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.pmx__state {
  flex-shrink: 0;
  font-size: 10.5px;
  padding: 0 6px;
}

.pmx__rowhead {
  background: #fafbfc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 4px 10px;
  text-align: left;
  width: 168px;
  vertical-align: middle;
}

.pmx__rowhead--active {
  background: #ecf3ff;
  border-color: #8db8f7;
}

.pmx__rowhead--agg {
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
}

.pmx__lab_name {
  display: block;
  font-size: 12.5px;
  font-weight: 600;
  color: #374151;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pmx__lab_meta {
  display: block;
  font-size: 11px;
  color: #9ca3af;
  font-weight: 400;
}

.pmx__cell {
  height: 34px;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
  font-size: 11px;
  text-align: center;
  color: #6b7280;
  padding: 0 4px;
  transition: background 0.15s ease;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pmx__cell--free {
  background: #f0faf2;
  border-color: #b7e4c7;
  cursor: pointer;
}

.pmx__cell--free:hover {
  background: #d8f3e0;
  border-color: #52c41a;
}

.pmx__cell--occupied {
  background: #fff1f0;
  border-color: #ffa39e;
  color: #cf1322;
  cursor: not-allowed;
}

.pmx__cell--pending {
  background: #fffbe6;
  border-color: #ffe58f;
  cursor: not-allowed;
}

.pmx__cell--past {
  background: #f5f5f5;
  color: #c0c4cc;
  cursor: not-allowed;
}

.pmx__cell--off {
  background: #f5f5f5;
  border-style: dashed;
  color: #c0c4cc;
  cursor: not-allowed;
}

.pmx__cell--selected {
  outline: 2px solid #2f6bff;
  outline-offset: -1px;
}

.pmx__cell--agg {
  background: #f8fafc;
  color: #16a34a;
  font-weight: 600;
  cursor: default;
}

.pmx__cell--agg_zero {
  color: #c0c4cc;
}

.pmx__who {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 窄屏适配：行头收窄、单元格压缩，保证 12 节次完整可见 */
@media (max-width: 900px) {
  .pmx__rowhead {
    width: 120px;
    padding: 3px 6px;
  }

  .pmx__lab_meta {
    display: none;
  }

  .pmx__cell {
    height: 30px;
    font-size: 10px;
    padding: 0 2px;
  }

  .pmx__colhead span {
    display: none;
  }
}
</style>
