<template>
  <div class="lab_page">
    <!-- 筛选栏 -->
    <el-card class="lab_page__card">
      <div class="lab_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="编码 / 名称 / 房间号"
              clearable
              style="width: 180px"
              @keyup.enter="search"
              @clear="search"
            />
          </el-form-item>
          <el-form-item label="校区">
            <el-select
              v-model="query.campusId"
              placeholder="全部"
              clearable
              style="width: 140px"
              @change="onCampusChange"
            >
              <el-option
                v-for="c in campusOptions"
                :key="c.id"
                :label="c.campusName"
                :value="c.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="楼栋">
            <el-select
              v-model="query.buildingId"
              placeholder="全部"
              clearable
              style="width: 160px"
              @change="search"
            >
              <el-option
                v-for="b in buildingOptions"
                :key="b.id"
                :label="b.buildingName"
                :value="b.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="管理部门">
            <el-select
              v-model="query.departmentId"
              placeholder="全部"
              clearable
              filterable
              style="width: 160px"
              @change="search"
            >
              <el-option
                v-for="d in departmentOptions"
                :key="d.id"
                :label="d.departmentName"
                :value="d.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="类型">
            <el-select
              v-model="query.labType"
              placeholder="全部"
              clearable
              style="width: 130px"
              @change="search"
            >
              <el-option
                v-for="t in labTypes"
                :key="t.value"
                :label="t.label"
                :value="t.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="query.enabled"
              placeholder="全部"
              clearable
              style="width: 100px"
              @change="search"
            >
              <el-option
                label="启用"
                :value="1"
              />
              <el-option
                label="停用"
                :value="0"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              @click="search"
            >
              查询
            </el-button>
            <el-button @click="reset">
              重置
            </el-button>
          </el-form-item>
        </el-form>
        <div v-permission="'space:lab:create'">
          <el-button
            type="primary"
            @click="openLabDialog()"
          >
            新增实验室
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 实验室表格 -->
    <el-card class="lab_page__card">
      <el-table
        v-loading="loading"
        :data="rows"
      >
        <el-table-column
          prop="labCode"
          label="编码"
          width="120"
        />
        <el-table-column
          label="实验室"
          min-width="180"
        >
          <template #default="{ row }">
            {{ row.labName }}
            <el-tag
              size="small"
              :type="labTypeTag(row.labType)"
              class="lab_page__type_tag"
            >
              {{ labTypeLabel(row.labType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="位置"
          min-width="220"
        >
          <template #default="{ row }">
            {{ row.campusName }} · {{ row.buildingName }} {{ row.floorNo }}层 {{ row.roomNo }}
          </template>
        </el-table-column>
        <el-table-column
          prop="departmentName"
          label="管理部门"
          min-width="130"
          show-overflow-tooltip
        />
        <el-table-column
          prop="capacity"
          label="容量"
          width="80"
          align="center"
        />
        <el-table-column
          label="面积(㎡)"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            {{ row.areaSqm ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.enabled === 1 ? 'success' : 'info'"
              size="small"
            >
              {{ row.enabled === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="200"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              size="small"
              type="primary"
              @click="openRuleDrawer(row)"
            >
              开放规则
            </el-button>
            <el-button
              v-permission="'space:lab:update'"
              link
              size="small"
              type="primary"
              @click="openLabDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'space:lab:enable'"
              link
              size="small"
              :type="row.enabled === 1 ? 'danger' : 'success'"
              @click="toggleLab(row)"
            >
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无实验室" />
        </template>
      </el-table>
      <div class="lab_page__pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 实验室新增/编辑 -->
    <el-dialog
      v-model="labDialogVisible"
      :title="labForm.id ? '编辑实验室' : '新增实验室'"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="labFormRef"
        :model="labForm"
        :rules="labRules"
        label-width="96px"
      >
        <el-form-item
          label="校区"
          prop="campusId"
        >
          <el-select
            v-model="labForm.campusId"
            placeholder="选择校区"
            style="width: 100%"
            @change="onFormCampusChange"
          >
            <el-option
              v-for="c in campusOptions"
              :key="c.id"
              :label="c.campusName"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="所属楼栋"
          prop="buildingId"
        >
          <el-select
            v-model="labForm.buildingId"
            placeholder="先选校区"
            style="width: 100%"
          >
            <el-option
              v-for="b in formBuildingOptions"
              :key="b.id"
              :label="b.buildingName"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="管理部门"
          prop="departmentId"
        >
          <el-select
            v-model="labForm.departmentId"
            placeholder="选择部门"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="d in departmentOptions"
              :key="d.id"
              :label="d.departmentName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="实验室编码"
          prop="labCode"
        >
          <el-input
            v-model="labForm.labCode"
            :disabled="!!labForm.id"
            placeholder="字母、数字或下划线，全局唯一"
          />
        </el-form-item>
        <el-form-item
          label="实验室名称"
          prop="labName"
        >
          <el-input v-model="labForm.labName" />
        </el-form-item>
        <el-form-item
          label="类型"
          prop="labType"
        >
          <el-select
            v-model="labForm.labType"
            style="width: 100%"
          >
            <el-option
              v-for="t in labTypes"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="楼层"
          prop="floorNo"
        >
          <el-input
            v-model="labForm.floorNo"
            placeholder="如 3"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item
          label="房间号"
          prop="roomNo"
        >
          <el-input
            v-model="labForm.roomNo"
            placeholder="如 301"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item
          label="容纳人数"
          prop="capacity"
        >
          <el-input-number
            v-model="labForm.capacity"
            :min="1"
            :max="100000"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item
          label="建筑面积"
          prop="areaSqm"
        >
          <el-input-number
            v-model="labForm.areaSqm"
            :min="0"
            :precision="2"
            :controls="false"
            placeholder="平方米（选填）"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item
          label="位置说明"
          prop="locationNote"
        >
          <el-input
            v-model="labForm.locationNote"
            type="textarea"
            :rows="2"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="labDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitLab"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 开放规则抽屉 -->
    <el-drawer
      v-model="ruleDrawerVisible"
      :title="`开放规则 · ${ruleLab?.labName || ''}`"
      size="560px"
    >
      <div class="lab_page__rules">
        <el-table
          v-loading="ruleLoading"
          :data="ruleRows"
          size="small"
        >
          <el-table-column
            label="星期"
            width="80"
          >
            <template #default="{ row }">
              {{ weekdayLabel(row.weekday) }}
            </template>
          </el-table-column>
          <el-table-column
            label="开放时段"
            width="170"
          >
            <template #default="{ row }">
              {{ row.openTime.slice(0, 5) }} - {{ row.closeTime.slice(0, 5) }}
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
            label="操作"
            width="70"
          >
            <template #default="{ row }">
              <el-button
                v-permission="'space:lab:update'"
                link
                size="small"
                type="danger"
                @click="removeRule(row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无开放规则" />
          </template>
        </el-table>

        <template v-if="canEditRule">
          <el-divider content-position="left">
            新增规则
          </el-divider>
          <el-form
            ref="ruleFormRef"
            :model="ruleForm"
            :rules="ruleRules"
            label-width="88px"
          >
            <el-form-item
              label="星期"
              prop="weekday"
            >
              <el-select
                v-model="ruleForm.weekday"
                style="width: 140px"
              >
                <el-option
                  v-for="w in 7"
                  :key="w"
                  :label="weekdayLabel(w)"
                  :value="w"
                />
              </el-select>
            </el-form-item>
            <el-form-item
              label="开放时段"
              prop="timeRange"
            >
              <el-time-picker
                v-model="ruleForm.timeRange"
                is-range
                format="HH:mm"
                value-format="HH:mm:ss"
                range-separator="至"
                start-placeholder="开放时间"
                end-placeholder="关闭时间"
              />
            </el-form-item>
            <el-form-item
              label="有效期"
              prop="dateRange"
            >
              <el-date-picker
                v-model="ruleForm.dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="ruleSubmitting"
                @click="submitRule"
              >
                添加规则
              </el-button>
              <span class="lab_page__rule_hint t-hint">一期仅同日时间窗；同日多段请分行添加</span>
            </el-form-item>
          </el-form>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createLab,
  createOpenRule,
  deleteOpenRule,
  getBuildingOptions,
  getCampusOptions,
  getOpenRules,
  pageLabs,
  updateLab,
  updateLabEnabled
} from '@/api/space'
import { getDepartmentOptions } from '@/api/system/user'
import { hasPermission } from '@/utils/permission'
import { useUserStore } from '@/store/user'

const labTypes = [
  { value: 'TEACHING', label: '教学实验室', tag: 'primary' },
  { value: 'RESEARCH', label: '科研实验室', tag: 'success' },
  { value: 'OPEN', label: '开放实验室', tag: 'warning' },
  { value: 'COMPREHENSIVE', label: '综合实验室', tag: 'info' }
]

function labTypeLabel(value) {
  return labTypes.find((t) => t.value === value)?.label || value
}

function labTypeTag(value) {
  return labTypes.find((t) => t.value === value)?.tag || 'info'
}

function weekdayLabel(value) {
  return ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'][value] || value
}

// ---------- 列表 ----------
const query = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  campusId: undefined,
  buildingId: undefined,
  departmentId: undefined,
  labType: '',
  enabled: undefined
})
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const campusOptions = ref([])
const buildingOptions = ref([])
const departmentOptions = ref([])

async function load() {
  loading.value = true
  try {
    const params = { ...query }
    for (const key of ['keyword', 'labType']) {
      if (!params[key]) params[key] = undefined
    }
    for (const key of ['campusId', 'buildingId', 'departmentId', 'enabled']) {
      if (params[key] === '' || params[key] === null || params[key] === undefined) params[key] = undefined
    }
    const data = await pageLabs(params)
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function reset() {
  query.keyword = ''
  query.campusId = undefined
  query.buildingId = undefined
  query.departmentId = undefined
  query.labType = ''
  query.enabled = undefined
  loadBuildingOptions()
  search()
}

async function loadBuildingOptions(campusId) {
  buildingOptions.value = await getBuildingOptions(campusId)
}

function onCampusChange() {
  query.buildingId = undefined
  loadBuildingOptions(query.campusId)
  search()
}

// ---------- 实验室对话框 ----------
const submitting = ref(false)
const labDialogVisible = ref(false)
const labFormRef = ref()
const formBuildingOptions = ref([])
const labForm = reactive({
  id: null,
  campusId: '',
  buildingId: '',
  departmentId: '',
  labCode: '',
  labName: '',
  labType: 'TEACHING',
  floorNo: '',
  roomNo: '',
  capacity: 30,
  areaSqm: undefined,
  locationNote: ''
})

const labRules = {
  campusId: [{ required: true, message: '请选择校区', trigger: 'change' }],
  buildingId: [{ required: true, message: '请选择所属楼栋', trigger: 'change' }],
  departmentId: [{ required: true, message: '请选择管理部门', trigger: 'change' }],
  labCode: [
    { required: true, message: '请输入实验室编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{1,64}$/, message: '需为 1-64 位字母、数字或下划线', trigger: 'blur' }
  ],
  labName: [{ required: true, message: '请输入实验室名称', trigger: 'blur' }],
  labType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  floorNo: [{ required: true, message: '请输入楼层', trigger: 'blur' }],
  roomNo: [{ required: true, message: '请输入房间号', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容纳人数', trigger: 'blur' }]
}

async function openLabDialog(row) {
  labForm.id = row ? row.id : null
  labForm.buildingId = row ? row.buildingId : ''
  labForm.departmentId = row ? row.departmentId : ''
  labForm.labCode = row ? row.labCode : ''
  labForm.labName = row ? row.labName : ''
  labForm.labType = row ? row.labType : 'TEACHING'
  labForm.floorNo = row ? row.floorNo : ''
  labForm.roomNo = row ? row.roomNo : ''
  labForm.capacity = row ? row.capacity : 30
  labForm.areaSqm = row && row.areaSqm !== null ? Number(row.areaSqm) : undefined
  labForm.locationNote = row ? row.locationNote || '' : ''
  // 编辑时按当前楼栋反查校区并预取该校区楼栋
  if (row) {
    const all = await getBuildingOptions()
    const hit = all.find((b) => b.id === row.buildingId)
    labForm.campusId = hit ? hit.campusId : ''
    formBuildingOptions.value = labForm.campusId ? await getBuildingOptions(labForm.campusId) : all
  } else {
    labForm.campusId = query.campusId || ''
    formBuildingOptions.value = labForm.campusId ? await getBuildingOptions(labForm.campusId) : []
  }
  labDialogVisible.value = true
}

async function onFormCampusChange() {
  labForm.buildingId = ''
  formBuildingOptions.value = labForm.campusId ? await getBuildingOptions(labForm.campusId) : []
}

async function submitLab() {
  try {
    await labFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const payload = {
      buildingId: Number(labForm.buildingId),
      departmentId: Number(labForm.departmentId),
      labName: labForm.labName,
      labType: labForm.labType,
      floorNo: labForm.floorNo,
      roomNo: labForm.roomNo,
      capacity: labForm.capacity,
      areaSqm: labForm.areaSqm ?? null,
      locationNote: labForm.locationNote || null
    }
    if (labForm.id) {
      await updateLab(labForm.id, payload)
      ElMessage.success('实验室已更新')
    } else {
      await createLab({ ...payload, labCode: labForm.labCode })
      ElMessage.success('实验室已创建')
    }
    labDialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function toggleLab(row) {
  const target = row.enabled === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      target === 0
        ? `停用后「${row.labName}」不可再新增开放规则与预约，确定停用吗？`
        : `确定启用「${row.labName}」吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await updateLabEnabled(row.id, target)
  ElMessage.success(target === 1 ? '已启用' : '已停用')
  load()
}

// ---------- 开放规则 ----------
const store = useUserStore()
const canEditRule = computed(() => hasPermission('space:lab:update', store.permissions))
const ruleDrawerVisible = ref(false)
const ruleLab = ref(null)
const ruleRows = ref([])
const ruleLoading = ref(false)
const ruleSubmitting = ref(false)
const ruleFormRef = ref()
const ruleForm = reactive({ weekday: 1, timeRange: null, dateRange: null })

const ruleRules = {
  weekday: [{ required: true, message: '请选择星期', trigger: 'change' }],
  timeRange: [{ required: true, message: '请选择开放时段', trigger: 'change' }],
  dateRange: [{ required: true, message: '请选择有效期', trigger: 'change' }]
}

async function openRuleDrawer(row) {
  ruleLab.value = row
  ruleDrawerVisible.value = true
  ruleForm.weekday = 1
  ruleForm.timeRange = null
  ruleForm.dateRange = null
  await loadRules()
}

async function loadRules() {
  ruleLoading.value = true
  try {
    ruleRows.value = await getOpenRules(ruleLab.value.id)
  } finally {
    ruleLoading.value = false
  }
}

async function submitRule() {
  try {
    await ruleFormRef.value.validate()
  } catch {
    return
  }
  ruleSubmitting.value = true
  try {
    await createOpenRule(ruleLab.value.id, {
      weekday: ruleForm.weekday,
      openTime: ruleForm.timeRange[0],
      closeTime: ruleForm.timeRange[1],
      validFrom: ruleForm.dateRange[0],
      validTo: ruleForm.dateRange[1]
    })
    ElMessage.success('规则已添加')
    ruleForm.timeRange = null
    await loadRules()
  } finally {
    ruleSubmitting.value = false
  }
}

async function removeRule(row) {
  try {
    await ElMessageBox.confirm('确定删除该开放规则吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await deleteOpenRule(row.id)
  ElMessage.success('已删除')
  loadRules()
}

onMounted(async () => {
  load()
  campusOptions.value = await getCampusOptions()
  loadBuildingOptions()
  departmentOptions.value = await getDepartmentOptions()
})
</script>

<style scoped>
.lab_page__card + .lab_page__card {
  margin-top: 14px;
}

.lab_page__filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.lab_page__filter :deep(.el-form-item) {
  margin-bottom: 8px;
  margin-right: 14px;
}

.lab_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.lab_page__type_tag {
  margin-left: 6px;
}

.lab_page__rule_hint {
  margin-left: 12px;
  font-size: 12px;
}
</style>
