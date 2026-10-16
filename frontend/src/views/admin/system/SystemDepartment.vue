<template>
  <div class="dept_page">
    <!-- 操作栏 -->
    <el-card class="dept_page__card">
      <div class="dept_page__bar">
        <p class="dept_page__hint t-hint">
          部门树支撑用户与名单归属；停用中的部门不可挂载新部门、新用户或新名单。
        </p>
        <el-button
          v-permission="'system:department:create'"
          type="primary"
          @click="openCreate(null)"
        >
          新增根部门
        </el-button>
      </div>
    </el-card>

    <!-- 部门树 -->
    <el-card class="dept_page__card">
      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        default-expand-all
      >
        <el-table-column
          prop="departmentName"
          label="部门名称"
          min-width="220"
        />
        <el-table-column
          prop="departmentCode"
          label="编码"
          min-width="140"
        />
        <el-table-column
          label="类型"
          width="130"
        >
          <template #default="{ row }">
            <el-tag
              :type="typeTag(row.departmentType)"
              size="small"
            >
              {{ typeLabel(row.departmentType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.enabled ? 'success' : 'warning'"
              size="small"
            >
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="220"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              v-permission="'system:department:create'"
              link
              type="primary"
              :disabled="!row.enabled"
              @click="openCreate(row)"
            >
              新增下级
            </el-button>
            <el-button
              v-permission="'system:department:update'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'system:department:enable'"
              link
              :type="row.enabled ? 'danger' : 'success'"
              @click="toggleEnabled(row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无部门" />
        </template>
      </el-table>
    </el-card>

    <!-- 新增 -->
    <el-dialog
      v-model="createVisible"
      :title="createParent ? `新增下级部门（${createParent.departmentName}）` : '新增根部门'"
      width="440px"
      destroy-on-close
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-width="86px"
      >
        <el-form-item
          label="部门编码"
          prop="departmentCode"
        >
          <el-input
            v-model="createForm.departmentCode"
            placeholder="如 assets_lab"
          />
        </el-form-item>
        <el-form-item
          label="部门名称"
          prop="departmentName"
        >
          <el-input v-model="createForm.departmentName" />
        </el-form-item>
        <el-form-item
          label="类型"
          prop="departmentType"
        >
          <el-select
            v-model="createForm.departmentType"
            style="width: 100%"
          >
            <el-option
              label="学校"
              value="SCHOOL"
            />
            <el-option
              label="学院"
              value="COLLEGE"
            />
            <el-option
              label="资产处室"
              value="ASSET_OFFICE"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitCreate"
        >
          新增
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑 -->
    <el-dialog
      v-model="editVisible"
      title="编辑部门"
      width="440px"
      destroy-on-close
    >
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="86px"
      >
        <el-form-item label="部门编码">
          <el-input
            :model-value="editRow?.departmentCode"
            disabled
          />
        </el-form-item>
        <el-form-item
          label="部门名称"
          prop="departmentName"
        >
          <el-input v-model="editForm.departmentName" />
        </el-form-item>
        <el-form-item label="上级部门">
          <el-tree-select
            v-model="editForm.parentId"
            :data="parentOptions"
            :props="{ label: 'departmentName', value: 'id', children: 'children' }"
            :render-after-expand="false"
            check-strictly
            clearable
            placeholder="留空为根部门"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item
          label="类型"
          prop="departmentType"
        >
          <el-select
            v-model="editForm.departmentType"
            style="width: 100%"
          >
            <el-option
              label="学校"
              value="SCHOOL"
            />
            <el-option
              label="学院"
              value="COLLEGE"
            />
            <el-option
              label="资产处室"
              value="ASSET_OFFICE"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p class="dept_page__tip t-hint">
        上级部门不能选择自身或自身的下级部门。
      </p>
      <template #footer>
        <el-button @click="editVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitEdit"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createDepartment,
  getDepartmentTree,
  updateDepartment,
  updateDepartmentEnabled
} from '@/api/system/department'

const tree = ref([])
const loading = ref(false)
const submitting = ref(false)

async function load() {
  loading.value = true
  try {
    tree.value = await getDepartmentTree()
  } finally {
    loading.value = false
  }
}

function typeLabel(type) {
  return { SCHOOL: '学校', COLLEGE: '学院', ASSET_OFFICE: '资产处室' }[type] || type
}

function typeTag(type) {
  return { SCHOOL: 'primary', COLLEGE: 'success', ASSET_OFFICE: 'warning' }[type] || 'info'
}

// ---------- 新增 ----------
const createVisible = ref(false)
const createFormRef = ref()
const createParent = ref(null)
const createForm = reactive({ departmentCode: '', departmentName: '', departmentType: 'COLLEGE' })

const createRules = {
  departmentCode: [
    { required: true, message: '请输入部门编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{2,64}$/, message: '2-64 位字母、数字或下划线', trigger: 'blur' }
  ],
  departmentName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  departmentType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function openCreate(parent) {
  createParent.value = parent
  createForm.departmentCode = ''
  createForm.departmentName = ''
  createForm.departmentType = 'COLLEGE'
  createVisible.value = true
}

async function submitCreate() {
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await createDepartment({ ...createForm, parentId: createParent.value?.id || null })
    ElMessage.success('已新增')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

// ---------- 编辑 ----------
const editVisible = ref(false)
const editFormRef = ref()
const editRow = ref(null)
const editForm = reactive({ departmentName: '', parentId: null, departmentType: 'COLLEGE' })

const editRules = {
  departmentName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  departmentType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

/** 编辑时上级可选树：排除自身及其子树（前端先行过滤，服务端仍会强校验） */
const parentOptions = computed(() => {
  if (!editRow.value) return tree.value
  const excludeId = editRow.value.id
  const filter = (nodes) =>
    nodes
      .filter((n) => n.id !== excludeId)
      .map((n) => ({ ...n, children: filter(n.children || []) }))
  return filter(tree.value)
})

function openEdit(row) {
  editRow.value = row
  editForm.departmentName = row.departmentName
  editForm.parentId = row.parentId || null
  editForm.departmentType = row.departmentType
  editVisible.value = true
}

async function submitEdit() {
  try {
    await editFormRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await updateDepartment(editRow.value.id, { ...editForm, parentId: editForm.parentId || null })
    ElMessage.success('已保存')
    editVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

// ---------- 启用/停用 ----------
async function toggleEnabled(row) {
  const disabling = row.enabled === 1
  try {
    await ElMessageBox.confirm(
      disabling
        ? `停用「${row.departmentName}」后不可挂载新部门、新用户或新名单；存在启用下级或被引用时将停用失败。确定停用吗？`
        : `确定重新启用「${row.departmentName}」吗？`,
      '提示',
      { type: 'warning', confirmButtonText: disabling ? '停用' : '启用' }
    )
  } catch {
    return
  }
  await updateDepartmentEnabled(row.id, disabling ? 0 : 1)
  ElMessage.success(disabling ? '已停用' : '已启用')
  load()
}

onMounted(load)
</script>

<style scoped>
.dept_page__card + .dept_page__card {
  margin-top: 14px;
}

.dept_page__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.dept_page__hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
}

.dept_page__tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
}
</style>
