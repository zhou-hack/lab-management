<template>
  <div class="space_manage">
    <el-tabs
      v-model="activeTab"
      @tab-change="onTabChange"
    >
      <!-- 校区 -->
      <el-tab-pane
        label="校区"
        name="campus"
      >
        <div class="card">
          <div class="space_manage__toolbar">
            <el-input
              v-model="campusQuery.keyword"
              class="space_manage__filter"
              placeholder="校区编码 / 名称"
              clearable
              @keyup.enter="searchCampuses"
              @clear="searchCampuses"
            />
            <el-button @click="searchCampuses">
              查询
            </el-button>
            <el-button
              v-permission="'space:campus:create'"
              type="primary"
              class="space_manage__toolbar_action"
              @click="openCampusCreate"
            >
              新增校区
            </el-button>
          </div>
          <div class="table-scroll-x">
            <el-table
              v-loading="campusLoading"
              :data="campusRecords"
            >
              <el-table-column
                prop="campusCode"
                label="校区编码"
                min-width="120"
              />
              <el-table-column
                prop="campusName"
                label="校区名称"
                min-width="150"
              />
              <el-table-column
                label="地址"
                min-width="200"
                show-overflow-tooltip
              >
                <template #default="{ row }">
                  {{ row.address || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="状态"
                width="90"
              >
                <template #default="{ row }">
                  <span
                    class="pill"
                    :class="row.enabled === 1 ? 'pill--ok' : 'pill--muted'"
                  >
                    {{ row.enabled === 1 ? '启用' : '停用' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                width="120"
                fixed="right"
              >
                <template #default="{ row }">
                  <el-button
                    v-permission="'space:campus:update'"
                    link
                    type="primary"
                    @click="openCampusEdit(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-permission="'space:campus:delete'"
                    link
                    type="danger"
                    @click="removeCampus(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <el-pagination
            v-model:current-page="campusQuery.pageNum"
            v-model:page-size="campusQuery.pageSize"
            class="space_manage__pager"
            :total="campusTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @change="loadCampuses"
          />
        </div>
      </el-tab-pane>

      <!-- 楼栋 -->
      <el-tab-pane
        label="楼栋"
        name="building"
      >
        <div class="card">
          <div class="space_manage__toolbar">
            <el-input
              v-model="buildingQuery.keyword"
              class="space_manage__filter"
              placeholder="楼栋编码 / 名称"
              clearable
              @keyup.enter="searchBuildings"
              @clear="searchBuildings"
            />
            <el-select
              v-model="buildingQuery.campusId"
              class="space_manage__filter"
              placeholder="全部校区"
              clearable
              @change="searchBuildings"
            >
              <el-option
                v-for="campus in campusOptions"
                :key="campus.id"
                :label="campus.campusName"
                :value="campus.id"
              />
            </el-select>
            <el-button @click="searchBuildings">
              查询
            </el-button>
            <el-button
              v-permission="'space:building:create'"
              type="primary"
              class="space_manage__toolbar_action"
              @click="openBuildingCreate"
            >
              新增楼栋
            </el-button>
          </div>
          <div class="table-scroll-x">
            <el-table
              v-loading="buildingLoading"
              :data="buildingRecords"
            >
              <el-table-column
                prop="buildingCode"
                label="楼栋编码"
                min-width="120"
              />
              <el-table-column
                prop="buildingName"
                label="楼栋名称"
                min-width="150"
              />
              <el-table-column
                prop="campusName"
                label="所属校区"
                min-width="140"
              />
              <el-table-column
                label="状态"
                width="90"
              >
                <template #default="{ row }">
                  <span
                    class="pill"
                    :class="row.enabled === 1 ? 'pill--ok' : 'pill--muted'"
                  >
                    {{ row.enabled === 1 ? '启用' : '停用' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                width="120"
                fixed="right"
              >
                <template #default="{ row }">
                  <el-button
                    v-permission="'space:building:update'"
                    link
                    type="primary"
                    @click="openBuildingEdit(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-permission="'space:building:delete'"
                    link
                    type="danger"
                    @click="removeBuilding(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <el-pagination
            v-model:current-page="buildingQuery.pageNum"
            v-model:page-size="buildingQuery.pageSize"
            class="space_manage__pager"
            :total="buildingTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @change="loadBuildings"
          />
        </div>
      </el-tab-pane>

      <!-- 实验室 -->
      <el-tab-pane
        label="实验室"
        name="lab"
      >
        <div class="card">
          <div class="space_manage__toolbar">
            <el-input
              v-model="labQuery.keyword"
              class="space_manage__filter"
              placeholder="实验室编码 / 名称"
              clearable
              @keyup.enter="searchLabs"
              @clear="searchLabs"
            />
            <el-select
              v-model="labQuery.campusId"
              class="space_manage__filter"
              placeholder="全部校区"
              clearable
              @change="onLabCampusChange"
            >
              <el-option
                v-for="campus in campusOptions"
                :key="campus.id"
                :label="campus.campusName"
                :value="campus.id"
              />
            </el-select>
            <el-select
              v-model="labQuery.buildingId"
              class="space_manage__filter"
              placeholder="全部楼栋"
              clearable
              @change="searchLabs"
            >
              <el-option
                v-for="building in labBuildingOptions"
                :key="building.id"
                :label="building.buildingName"
                :value="building.id"
              />
            </el-select>
            <el-button @click="searchLabs">
              查询
            </el-button>
            <el-button
              v-permission="'space:lab:create'"
              type="primary"
              class="space_manage__toolbar_action"
              @click="openLabCreate"
            >
              新增实验室
            </el-button>
          </div>
          <div class="table-scroll-x">
            <el-table
              v-loading="labLoading"
              :data="labRecords"
            >
              <el-table-column
                prop="labCode"
                label="实验室编码"
                min-width="110"
              />
              <el-table-column
                prop="labName"
                label="实验室名称"
                min-width="140"
              />
              <el-table-column
                prop="labType"
                label="类型"
                min-width="80"
              />
              <el-table-column
                prop="buildingName"
                label="楼栋"
                min-width="110"
              />
              <el-table-column
                prop="campusName"
                label="校区"
                min-width="100"
              />
              <el-table-column
                prop="departmentName"
                label="归口部门"
                min-width="110"
              />
              <el-table-column
                label="楼层 / 房号"
                min-width="110"
              >
                <template #default="{ row }">
                  {{ row.floorNo }} · {{ row.roomNo }}
                </template>
              </el-table-column>
              <el-table-column
                label="容纳人数"
                width="90"
              >
                <template #default="{ row }">
                  {{ row.capacity }} 人
                </template>
              </el-table-column>
              <el-table-column
                label="面积（㎡）"
                width="100"
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
                  <span
                    class="pill"
                    :class="row.enabled === 1 ? 'pill--ok' : 'pill--muted'"
                  >
                    {{ row.enabled === 1 ? '启用' : '停用' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                label="操作"
                width="120"
                fixed="right"
              >
                <template #default="{ row }">
                  <el-button
                    v-permission="'space:lab:update'"
                    link
                    type="primary"
                    @click="openLabEdit(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    v-permission="'space:lab:delete'"
                    link
                    type="danger"
                    @click="removeLab(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <el-pagination
            v-model:current-page="labQuery.pageNum"
            v-model:page-size="labQuery.pageSize"
            class="space_manage__pager"
            :total="labTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @change="loadLabs"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 校区弹窗 -->
    <el-dialog
      v-model="campusDialog.visible"
      :title="campusDialog.mode === 'create' ? '新增校区' : '编辑校区'"
      width="520px"
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
            :disabled="campusDialog.mode === 'edit'"
            maxlength="64"
            placeholder="如 XQ001（创建后不可改）"
          />
        </el-form-item>
        <el-form-item
          label="校区名称"
          prop="campusName"
        >
          <el-input
            v-model="campusForm.campusName"
            maxlength="128"
            placeholder="如 东校区"
          />
        </el-form-item>
        <el-form-item label="地址">
          <el-input
            v-model="campusForm.address"
            maxlength="255"
            placeholder="校区地址（选填）"
          />
        </el-form-item>
        <el-form-item
          v-if="campusDialog.mode === 'edit'"
          label="启用状态"
        >
          <el-switch
            v-model="campusForm.enabled"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="campusDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="campusDialog.saving"
          @click="saveCampus"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 楼栋弹窗 -->
    <el-dialog
      v-model="buildingDialog.visible"
      :title="buildingDialog.mode === 'create' ? '新增楼栋' : '编辑楼栋'"
      width="520px"
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
            :disabled="buildingDialog.mode === 'edit'"
            placeholder="请选择校区"
          >
            <el-option
              v-for="campus in campusOptions"
              :key="campus.id"
              :label="campus.campusName"
              :value="campus.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="楼栋编码"
          prop="buildingCode"
        >
          <el-input
            v-model="buildingForm.buildingCode"
            :disabled="buildingDialog.mode === 'edit'"
            maxlength="64"
            placeholder="如 LD01（创建后不可改）"
          />
        </el-form-item>
        <el-form-item
          label="楼栋名称"
          prop="buildingName"
        >
          <el-input
            v-model="buildingForm.buildingName"
            maxlength="128"
            placeholder="如 第一实验楼"
          />
        </el-form-item>
        <el-form-item
          v-if="buildingDialog.mode === 'edit'"
          label="启用状态"
        >
          <el-switch
            v-model="buildingForm.enabled"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="buildingDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="buildingDialog.saving"
          @click="saveBuilding"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 实验室弹窗 -->
    <el-dialog
      v-model="labDialog.visible"
      :title="labDialog.mode === 'create' ? '新增实验室' : '编辑实验室'"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="labFormRef"
        :model="labForm"
        :rules="labRules"
        label-width="90px"
      >
        <el-form-item
          label="所在楼栋"
          prop="buildingId"
        >
          <el-select
            v-model="labForm.buildingId"
            :disabled="labDialog.mode === 'edit'"
            placeholder="请选择楼栋"
          >
            <el-option
              v-for="building in buildingOptions"
              :key="building.id"
              :label="`${building.campusName} · ${building.buildingName}`"
              :value="building.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="归口部门"
          prop="departmentId"
        >
          <el-tree-select
            v-model="labForm.departmentId"
            :data="departmentOptions"
            node-key="id"
            check-strictly
            placeholder="请选择归口部门"
          />
        </el-form-item>
        <el-form-item
          label="实验室编码"
          prop="labCode"
        >
          <el-input
            v-model="labForm.labCode"
            :disabled="labDialog.mode === 'edit'"
            maxlength="64"
            placeholder="如 SYS101（创建后不可改）"
          />
        </el-form-item>
        <el-form-item
          label="实验室名称"
          prop="labName"
        >
          <el-input
            v-model="labForm.labName"
            maxlength="128"
            placeholder="如 物理基础实验室"
          />
        </el-form-item>
        <el-form-item
          label="实验室类型"
          prop="labType"
        >
          <el-input
            v-model="labForm.labType"
            maxlength="32"
            placeholder="如 教学 / 科研 / 公共服务"
          />
        </el-form-item>
        <el-form-item
          label="楼层号"
          prop="floorNo"
        >
          <el-input
            v-model="labForm.floorNo"
            maxlength="16"
            placeholder="如 3"
          />
        </el-form-item>
        <el-form-item
          label="房间号"
          prop="roomNo"
        >
          <el-input
            v-model="labForm.roomNo"
            :disabled="labDialog.mode === 'edit'"
            maxlength="64"
            placeholder="如 301（换房间须删除后新建）"
          />
        </el-form-item>
        <el-form-item
          label="容纳人数"
          prop="capacity"
        >
          <el-input-number
            v-model="labForm.capacity"
            :min="1"
            :precision="0"
          />
        </el-form-item>
        <el-form-item label="面积（㎡）">
          <el-input-number
            v-model="labForm.areaSqm"
            :min="0"
            :precision="2"
          />
        </el-form-item>
        <el-form-item label="位置说明">
          <el-input
            v-model="labForm.locationNote"
            maxlength="255"
            placeholder="选填"
          />
        </el-form-item>
        <el-form-item
          v-if="labDialog.mode === 'edit'"
          label="启用状态"
        >
          <el-switch
            v-model="labForm.enabled"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="labDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="labDialog.saving"
          @click="saveLab"
        >
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createBuilding,
  createCampus,
  createLab,
  deleteBuilding,
  deleteCampus,
  deleteLab,
  pageBuildings,
  pageCampuses,
  pageLabs,
  updateBuilding,
  updateCampus,
  updateLab
} from '@/api/space'
import { departmentTreeOptions } from '@/api/department'

const activeTab = ref('campus')

/* ---------------- 通用：校区/楼栋下拉选项 ---------------- */

const campusOptions = ref([])
const buildingOptions = ref([])
const departmentOptions = ref([])

async function loadCampusOptions() {
  try {
    const data = await pageCampuses({ pageNum: 1, pageSize: 100 })
    campusOptions.value = data.records || []
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

async function loadBuildingOptions() {
  try {
    const data = await pageBuildings({ pageNum: 1, pageSize: 100 })
    buildingOptions.value = data.records || []
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

async function loadDepartmentOptions() {
  try {
    departmentOptions.value = await departmentTreeOptions()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/* ---------------- 校区 ---------------- */

const campusQuery = reactive({ pageNum: 1, pageSize: 20, keyword: '' })
const campusRecords = ref([])
const campusTotal = ref(0)
const campusLoading = ref(false)

async function loadCampuses() {
  campusLoading.value = true
  try {
    const params = { pageNum: campusQuery.pageNum, pageSize: campusQuery.pageSize }
    if (campusQuery.keyword.trim()) {
      params.keyword = campusQuery.keyword.trim()
    }
    const data = await pageCampuses(params)
    campusRecords.value = data.records || []
    campusTotal.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    campusLoading.value = false
  }
}

function searchCampuses() {
  campusQuery.pageNum = 1
  loadCampuses()
}

const campusDialog = reactive({ visible: false, mode: 'create', saving: false })
const campusFormRef = ref()
const campusForm = reactive({ id: '', campusCode: '', campusName: '', address: '', enabled: 1 })
const campusRules = {
  campusCode: [{ required: true, message: '请输入校区编码', trigger: 'blur' }],
  campusName: [{ required: true, message: '请输入校区名称', trigger: 'blur' }]
}

function openCampusCreate() {
  campusDialog.mode = 'create'
  Object.assign(campusForm, { id: '', campusCode: '', campusName: '', address: '', enabled: 1 })
  campusDialog.visible = true
}

function openCampusEdit(row) {
  campusDialog.mode = 'edit'
  Object.assign(campusForm, {
    id: row.id,
    campusCode: row.campusCode,
    campusName: row.campusName,
    address: row.address || '',
    enabled: row.enabled
  })
  campusDialog.visible = true
}

async function saveCampus() {
  try {
    await campusFormRef.value.validate()
  } catch {
    return
  }
  campusDialog.saving = true
  try {
    if (campusDialog.mode === 'create') {
      await createCampus({
        campusCode: campusForm.campusCode.trim(),
        campusName: campusForm.campusName.trim(),
        address: campusForm.address.trim() || null
      })
    } else {
      await updateCampus(campusForm.id, {
        campusName: campusForm.campusName.trim(),
        address: campusForm.address.trim() || null,
        enabled: campusForm.enabled
      })
    }
    ElMessage.success('保存成功')
    campusDialog.visible = false
    loadCampuses()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    campusDialog.saving = false
  }
}

async function removeCampus(row) {
  try {
    await ElMessageBox.confirm(`确定删除校区「${row.campusName}」吗？删除后编码不可复用。`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteCampus(row.id)
    ElMessage.success('删除成功')
    loadCampuses()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/* ---------------- 楼栋 ---------------- */

const buildingQuery = reactive({ pageNum: 1, pageSize: 20, keyword: '', campusId: '' })
const buildingRecords = ref([])
const buildingTotal = ref(0)
const buildingLoading = ref(false)

async function loadBuildings() {
  buildingLoading.value = true
  try {
    const params = { pageNum: buildingQuery.pageNum, pageSize: buildingQuery.pageSize }
    if (buildingQuery.keyword.trim()) {
      params.keyword = buildingQuery.keyword.trim()
    }
    if (buildingQuery.campusId) {
      params.campusId = buildingQuery.campusId
    }
    const data = await pageBuildings(params)
    buildingRecords.value = data.records || []
    buildingTotal.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    buildingLoading.value = false
  }
}

function searchBuildings() {
  buildingQuery.pageNum = 1
  loadBuildings()
}

const buildingDialog = reactive({ visible: false, mode: 'create', saving: false })
const buildingFormRef = ref()
const buildingForm = reactive({ id: '', campusId: '', buildingCode: '', buildingName: '', enabled: 1 })
const buildingRules = {
  campusId: [{ required: true, message: '请选择所属校区', trigger: 'change' }],
  buildingCode: [{ required: true, message: '请输入楼栋编码', trigger: 'blur' }],
  buildingName: [{ required: true, message: '请输入楼栋名称', trigger: 'blur' }]
}

function openBuildingCreate() {
  buildingDialog.mode = 'create'
  Object.assign(buildingForm, { id: '', campusId: '', buildingCode: '', buildingName: '', enabled: 1 })
  buildingDialog.visible = true
}

function openBuildingEdit(row) {
  buildingDialog.mode = 'edit'
  Object.assign(buildingForm, {
    id: row.id,
    campusId: row.campusId,
    buildingCode: row.buildingCode,
    buildingName: row.buildingName,
    enabled: row.enabled
  })
  buildingDialog.visible = true
}

async function saveBuilding() {
  try {
    await buildingFormRef.value.validate()
  } catch {
    return
  }
  buildingDialog.saving = true
  try {
    if (buildingDialog.mode === 'create') {
      await createBuilding({
        campusId: buildingForm.campusId,
        buildingCode: buildingForm.buildingCode.trim(),
        buildingName: buildingForm.buildingName.trim()
      })
    } else {
      await updateBuilding(buildingForm.id, {
        buildingName: buildingForm.buildingName.trim(),
        enabled: buildingForm.enabled
      })
    }
    ElMessage.success('保存成功')
    buildingDialog.visible = false
    loadBuildings()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    buildingDialog.saving = false
  }
}

async function removeBuilding(row) {
  try {
    await ElMessageBox.confirm(`确定删除楼栋「${row.buildingName}」吗？删除后编码不可复用。`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteBuilding(row.id)
    ElMessage.success('删除成功')
    loadBuildings()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/* ---------------- 实验室 ---------------- */

const labQuery = reactive({ pageNum: 1, pageSize: 20, keyword: '', campusId: '', buildingId: '' })
const labRecords = ref([])
const labTotal = ref(0)
const labLoading = ref(false)

/** 实验室筛选的楼栋选项随校区联动 */
const labBuildingOptions = computed(() => {
  if (!labQuery.campusId) return buildingOptions.value
  return buildingOptions.value.filter((building) => building.campusId === labQuery.campusId)
})

async function loadLabs() {
  labLoading.value = true
  try {
    const params = { pageNum: labQuery.pageNum, pageSize: labQuery.pageSize }
    if (labQuery.keyword.trim()) {
      params.keyword = labQuery.keyword.trim()
    }
    if (labQuery.campusId) {
      params.campusId = labQuery.campusId
    }
    if (labQuery.buildingId) {
      params.buildingId = labQuery.buildingId
    }
    const data = await pageLabs(params)
    labRecords.value = data.records || []
    labTotal.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    labLoading.value = false
  }
}

function searchLabs() {
  labQuery.pageNum = 1
  loadLabs()
}

function onLabCampusChange() {
  labQuery.buildingId = ''
  searchLabs()
}

const labDialog = reactive({ visible: false, mode: 'create', saving: false })
const labFormRef = ref()
const labForm = reactive({
  id: '',
  buildingId: '',
  departmentId: '',
  labCode: '',
  labName: '',
  labType: '',
  floorNo: '',
  roomNo: '',
  capacity: 1,
  areaSqm: null,
  locationNote: '',
  enabled: 1
})
const labRules = {
  buildingId: [{ required: true, message: '请选择所在楼栋', trigger: 'change' }],
  departmentId: [{ required: true, message: '请选择归口部门', trigger: 'change' }],
  labCode: [{ required: true, message: '请输入实验室编码', trigger: 'blur' }],
  labName: [{ required: true, message: '请输入实验室名称', trigger: 'blur' }],
  labType: [{ required: true, message: '请输入实验室类型', trigger: 'blur' }],
  floorNo: [{ required: true, message: '请输入楼层号', trigger: 'blur' }],
  roomNo: [{ required: true, message: '请输入房间号', trigger: 'blur' }],
  capacity: [{ required: true, message: '请输入容纳人数', trigger: 'change' }]
}

function openLabCreate() {
  labDialog.mode = 'create'
  Object.assign(labForm, {
    id: '',
    buildingId: '',
    departmentId: '',
    labCode: '',
    labName: '',
    labType: '',
    floorNo: '',
    roomNo: '',
    capacity: 1,
    areaSqm: null,
    locationNote: '',
    enabled: 1
  })
  if (!buildingOptions.value.length) {
    loadBuildingOptions()
  }
  if (!departmentOptions.value.length) {
    loadDepartmentOptions()
  }
  labDialog.visible = true
}

function openLabEdit(row) {
  labDialog.mode = 'edit'
  Object.assign(labForm, {
    id: row.id,
    buildingId: row.buildingId,
    departmentId: row.departmentId,
    labCode: row.labCode,
    labName: row.labName,
    labType: row.labType || '',
    floorNo: row.floorNo,
    roomNo: row.roomNo,
    capacity: row.capacity,
    areaSqm: row.areaSqm,
    locationNote: row.locationNote || '',
    enabled: row.enabled
  })
  if (!buildingOptions.value.length) {
    loadBuildingOptions()
  }
  if (!departmentOptions.value.length) {
    loadDepartmentOptions()
  }
  labDialog.visible = true
}

async function saveLab() {
  try {
    await labFormRef.value.validate()
  } catch {
    return
  }
  labDialog.saving = true
  try {
    if (labDialog.mode === 'create') {
      await createLab({
        buildingId: labForm.buildingId,
        departmentId: labForm.departmentId,
        labCode: labForm.labCode.trim(),
        labName: labForm.labName.trim(),
        labType: labForm.labType.trim(),
        floorNo: labForm.floorNo.trim(),
        roomNo: labForm.roomNo.trim(),
        capacity: labForm.capacity,
        areaSqm: labForm.areaSqm ?? null,
        locationNote: labForm.locationNote.trim() || null
      })
    } else {
      await updateLab(labForm.id, {
        labName: labForm.labName.trim(),
        labType: labForm.labType.trim(),
        floorNo: labForm.floorNo.trim(),
        capacity: labForm.capacity,
        areaSqm: labForm.areaSqm ?? null,
        locationNote: labForm.locationNote.trim() || null,
        enabled: labForm.enabled
      })
    }
    ElMessage.success('保存成功')
    labDialog.visible = false
    loadLabs()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    labDialog.saving = false
  }
}

async function removeLab(row) {
  try {
    await ElMessageBox.confirm(`确定删除实验室「${row.labName}」吗？删除后编码不可复用。`, '提示', {
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteLab(row.id)
    ElMessage.success('删除成功')
    loadLabs()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

/* ---------------- Tab 切换 ---------------- */

function onTabChange(name) {
  if (name === 'building') {
    searchBuildings()
  } else if (name === 'lab') {
    if (!buildingOptions.value.length) {
      loadBuildingOptions()
    }
    searchLabs()
  }
}

onMounted(() => {
  loadCampuses()
  loadCampusOptions()
})
</script>

<style scoped>
.space_manage__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.space_manage__filter {
  width: 200px;
}

/* 提高选择器特异性以覆盖 .el-button + .el-button 的相邻 margin */
.space_manage__toolbar .space_manage__toolbar_action {
  margin-left: auto;
}

.space_manage__pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
