<template>
  <div class="campus_page">
    <!-- 校区筛选栏 -->
    <el-card class="campus_page__card">
      <div class="campus_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="校区">
            <el-input
              v-model="campusQuery.keyword"
              placeholder="编码 / 名称"
              clearable
              style="width: 180px"
              @keyup.enter="searchCampus"
              @clear="searchCampus"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="campusQuery.enabled"
              placeholder="全部"
              clearable
              style="width: 110px"
              @change="searchCampus"
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
              @click="searchCampus"
            >
              查询
            </el-button>
            <el-button @click="resetCampus">
              重置
            </el-button>
          </el-form-item>
        </el-form>
        <div v-permission="'space:campus:create'">
          <el-button
            type="primary"
            @click="openCampusDialog()"
          >
            新增校区
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 校区表格 -->
    <el-card class="campus_page__card">
      <el-table
        v-loading="campusLoading"
        :data="campusRows"
      >
        <el-table-column
          prop="campusCode"
          label="校区编码"
          width="130"
        />
        <el-table-column
          prop="campusName"
          label="校区名称"
          min-width="160"
        />
        <el-table-column
          prop="address"
          label="地址"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          prop="buildingCount"
          label="楼栋数"
          width="90"
          align="center"
        />
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
          label="创建时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="170"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              size="small"
              type="primary"
              @click="filterBuildingsByCampus(row)"
            >
              楼栋
            </el-button>
            <el-button
              v-permission="'space:campus:update'"
              link
              size="small"
              type="primary"
              @click="openCampusDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'space:campus:enable'"
              link
              size="small"
              :type="row.enabled === 1 ? 'danger' : 'success'"
              @click="toggleCampus(row)"
            >
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无校区" />
        </template>
      </el-table>
      <div class="campus_page__pager">
        <el-pagination
          v-model:current-page="campusQuery.pageNum"
          v-model:page-size="campusQuery.pageSize"
          :total="campusTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadCampuses"
          @current-change="loadCampuses"
        />
      </div>
    </el-card>

    <!-- 楼栋筛选栏 -->
    <el-card
      ref="buildingSectionRef"
      class="campus_page__card"
    >
      <div class="campus_page__filter">
        <el-form
          inline
          @submit.prevent
        >
          <el-form-item label="所属校区">
            <el-select
              v-model="buildingQuery.campusId"
              placeholder="全部校区"
              clearable
              style="width: 160px"
              @change="searchBuilding"
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
            <el-input
              v-model="buildingQuery.keyword"
              placeholder="编码 / 名称"
              clearable
              style="width: 180px"
              @keyup.enter="searchBuilding"
              @clear="searchBuilding"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="buildingQuery.enabled"
              placeholder="全部"
              clearable
              style="width: 110px"
              @change="searchBuilding"
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
              @click="searchBuilding"
            >
              查询
            </el-button>
            <el-button @click="resetBuilding">
              重置
            </el-button>
          </el-form-item>
        </el-form>
        <div v-permission="'space:building:create'">
          <el-button
            type="primary"
            @click="openBuildingDialog()"
          >
            新增楼栋
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 楼栋表格 -->
    <el-card class="campus_page__card">
      <el-table
        v-loading="buildingLoading"
        :data="buildingRows"
      >
        <el-table-column
          prop="campusName"
          label="所属校区"
          width="150"
        />
        <el-table-column
          prop="buildingCode"
          label="楼栋编码"
          width="130"
        />
        <el-table-column
          prop="buildingName"
          label="楼栋名称"
          min-width="170"
        />
        <el-table-column
          prop="labCount"
          label="实验室数"
          width="100"
          align="center"
        />
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
          label="创建时间"
          min-width="165"
        >
          <template #default="{ row }">
            {{ formatInstant(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="140"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'space:building:update'"
              link
              size="small"
              type="primary"
              @click="openBuildingDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'space:building:enable'"
              link
              size="small"
              :type="row.enabled === 1 ? 'danger' : 'success'"
              @click="toggleBuilding(row)"
            >
              {{ row.enabled === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无楼栋" />
        </template>
      </el-table>
      <div class="campus_page__pager">
        <el-pagination
          v-model:current-page="buildingQuery.pageNum"
          v-model:page-size="buildingQuery.pageSize"
          :total="buildingTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadBuildings"
          @current-change="loadBuildings"
        />
      </div>
    </el-card>

    <!-- 校区新增/编辑 -->
    <el-dialog
      v-model="campusDialogVisible"
      :title="campusForm.id ? '编辑校区' : '新增校区'"
      width="460px"
      destroy-on-close
    >
      <el-form
        ref="campusFormRef"
        :model="campusForm"
        :rules="campusRules"
        label-width="90px"
      >
        <el-form-item
          label="校区编码"
          prop="campusCode"
        >
          <el-input
            v-model="campusForm.campusCode"
            :disabled="!!campusForm.id"
            placeholder="字母、数字或下划线"
          />
        </el-form-item>
        <el-form-item
          label="校区名称"
          prop="campusName"
        >
          <el-input v-model="campusForm.campusName" />
        </el-form-item>
        <el-form-item
          label="地址"
          prop="address"
        >
          <el-input
            v-model="campusForm.address"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="campusDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCampus"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 楼栋新增/编辑 -->
    <el-dialog
      v-model="buildingDialogVisible"
      :title="buildingForm.id ? '编辑楼栋' : '新增楼栋'"
      width="460px"
      destroy-on-close
    >
      <el-form
        ref="buildingFormRef"
        :model="buildingForm"
        :rules="buildingRules"
        label-width="90px"
      >
        <el-form-item
          label="所属校区"
          prop="campusId"
        >
          <el-select
            v-model="buildingForm.campusId"
            placeholder="选择校区"
            style="width: 100%"
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
          label="楼栋编码"
          prop="buildingCode"
        >
          <el-input
            v-model="buildingForm.buildingCode"
            :disabled="!!buildingForm.id"
            placeholder="字母、数字或下划线，同校区唯一"
          />
        </el-form-item>
        <el-form-item
          label="楼栋名称"
          prop="buildingName"
        >
          <el-input v-model="buildingForm.buildingName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="buildingDialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitBuilding"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createBuilding,
  createCampus,
  getCampusOptions,
  pageBuildings,
  pageCampuses,
  updateBuilding,
  updateBuildingEnabled,
  updateCampus,
  updateCampusEnabled
} from '@/api/space'
import { formatInstant } from '@/utils/datetime'

// ---------- 校区 ----------
const campusQuery = reactive({ pageNum: 1, pageSize: 20, keyword: '', enabled: undefined })
const campusRows = ref([])
const campusTotal = ref(0)
const campusLoading = ref(false)
const campusOptions = ref([])

async function loadCampuses() {
  campusLoading.value = true
  try {
    const params = { ...campusQuery }
    if (!params.keyword) params.keyword = undefined
    if (params.enabled === '' || params.enabled === null) params.enabled = undefined
    const data = await pageCampuses(params)
    campusRows.value = data.records
    campusTotal.value = data.total
  } finally {
    campusLoading.value = false
  }
}

function searchCampus() {
  campusQuery.pageNum = 1
  loadCampuses()
}

function resetCampus() {
  campusQuery.keyword = ''
  campusQuery.enabled = undefined
  searchCampus()
}

async function loadCampusOptions() {
  campusOptions.value = await getCampusOptions()
}

// ---------- 楼栋 ----------
const buildingSectionRef = ref()
const buildingQuery = reactive({ pageNum: 1, pageSize: 20, campusId: undefined, keyword: '', enabled: undefined })
const buildingRows = ref([])
const buildingTotal = ref(0)
const buildingLoading = ref(false)

async function loadBuildings() {
  buildingLoading.value = true
  try {
    const params = { ...buildingQuery }
    if (!params.keyword) params.keyword = undefined
    if (!params.campusId) params.campusId = undefined
    if (params.enabled === '' || params.enabled === null) params.enabled = undefined
    const data = await pageBuildings(params)
    buildingRows.value = data.records
    buildingTotal.value = data.total
  } finally {
    buildingLoading.value = false
  }
}

function searchBuilding() {
  buildingQuery.pageNum = 1
  loadBuildings()
}

function resetBuilding() {
  buildingQuery.campusId = undefined
  buildingQuery.keyword = ''
  buildingQuery.enabled = undefined
  searchBuilding()
}

function filterBuildingsByCampus(row) {
  buildingQuery.campusId = row.id
  searchBuilding()
  buildingSectionRef.value?.$el?.scrollIntoView({ behavior: 'smooth' })
}

// ---------- 校区对话框 ----------
const submitting = ref(false)
const campusDialogVisible = ref(false)
const campusFormRef = ref()
const campusForm = reactive({ id: null, campusCode: '', campusName: '', address: '' })

const campusRules = {
  campusCode: [
    { required: true, message: '请输入校区编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{1,64}$/, message: '需为 1-64 位字母、数字或下划线', trigger: 'blur' }
  ],
  campusName: [{ required: true, message: '请输入校区名称', trigger: 'blur' }]
}

function openCampusDialog(row) {
  campusForm.id = row ? row.id : null
  campusForm.campusCode = row ? row.campusCode : ''
  campusForm.campusName = row ? row.campusName : ''
  campusForm.address = row ? row.address || '' : ''
  campusDialogVisible.value = true
}

async function submitCampus() {
  try {
    await campusFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (campusForm.id) {
      await updateCampus(campusForm.id, { campusName: campusForm.campusName, address: campusForm.address || null })
      ElMessage.success('校区已更新')
    } else {
      await createCampus({
        campusCode: campusForm.campusCode,
        campusName: campusForm.campusName,
        address: campusForm.address || null
      })
      ElMessage.success('校区已创建')
    }
    campusDialogVisible.value = false
    loadCampuses()
    loadCampusOptions()
  } finally {
    submitting.value = false
  }
}

async function toggleCampus(row) {
  const target = row.enabled === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      target === 0
        ? `停用后「${row.campusName}」不可再挂载新楼栋，确定停用吗？`
        : `确定启用「${row.campusName}」吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await updateCampusEnabled(row.id, target)
  ElMessage.success(target === 1 ? '已启用' : '已停用')
  loadCampuses()
  loadCampusOptions()
}

// ---------- 楼栋对话框 ----------
const buildingDialogVisible = ref(false)
const buildingFormRef = ref()
const buildingForm = reactive({ id: null, campusId: '', buildingCode: '', buildingName: '' })

const buildingRules = {
  campusId: [{ required: true, message: '请选择所属校区', trigger: 'change' }],
  buildingCode: [
    { required: true, message: '请输入楼栋编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{1,64}$/, message: '需为 1-64 位字母、数字或下划线', trigger: 'blur' }
  ],
  buildingName: [{ required: true, message: '请输入楼栋名称', trigger: 'blur' }]
}

async function openBuildingDialog(row) {
  if (!campusOptions.value.length) {
    await loadCampusOptions()
  }
  buildingForm.id = row ? row.id : null
  buildingForm.campusId = row ? row.campusId : (buildingQuery.campusId || '')
  buildingForm.buildingCode = row ? row.buildingCode : ''
  buildingForm.buildingName = row ? row.buildingName : ''
  buildingDialogVisible.value = true
}

async function submitBuilding() {
  try {
    await buildingFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (buildingForm.id) {
      await updateBuilding(buildingForm.id, {
        campusId: Number(buildingForm.campusId),
        buildingName: buildingForm.buildingName
      })
      ElMessage.success('楼栋已更新')
    } else {
      await createBuilding({
        campusId: Number(buildingForm.campusId),
        buildingCode: buildingForm.buildingCode,
        buildingName: buildingForm.buildingName
      })
      ElMessage.success('楼栋已创建')
    }
    buildingDialogVisible.value = false
    loadBuildings()
    loadCampuses()
  } finally {
    submitting.value = false
  }
}

async function toggleBuilding(row) {
  const target = row.enabled === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      target === 0
        ? `停用后「${row.buildingName}」不可再挂载新实验室，确定停用吗？`
        : `确定启用「${row.buildingName}」吗？`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await updateBuildingEnabled(row.id, target)
  ElMessage.success(target === 1 ? '已启用' : '已停用')
  loadBuildings()
}

onMounted(() => {
  loadCampuses()
  loadBuildings()
  loadCampusOptions()
})
</script>

<style scoped>
.campus_page__card + .campus_page__card {
  margin-top: 14px;
}

.campus_page__filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.campus_page__filter :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 14px;
}

.campus_page__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
