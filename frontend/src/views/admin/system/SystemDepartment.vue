<template>
  <div class="dept_page page">
    <!-- 左：部门树 -->
    <aside class="dept_page__side">
      <div class="dept_side__header">
        <span>部门树</span>
        <el-button
          v-permission="'system:department:create'"
          type="primary"
          size="small"
          @click="openCreate(null)"
        >
          新增根部门
        </el-button>
      </div>
      <el-tree
        v-loading="loading"
        :data="tree"
        node-key="id"
        :props="{ label: 'departmentName', children: 'children' }"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        class="dept_side__tree"
        @node-click="selectNode"
      >
        <template #default="{ data }">
          <span class="dept_side__node">
            <span class="dept_side__node_name">{{ data.departmentName }}</span>
            <el-tag
              v-if="!data.enabled"
              type="warning"
              size="small"
              effect="plain"
            >
              停用
            </el-tag>
          </span>
        </template>
      </el-tree>
      <p class="dept_side__hint">
        停用中的部门不可挂载新部门、新用户或新名单
      </p>
    </aside>

    <!-- 右：部门详情与成员 -->
    <section
      v-if="current"
      class="dept_page__main"
    >
      <header class="dept_main__header">
        <div>
          <div class="dept_main__title">
            {{ current.departmentName }}
            <el-tag
              :type="typeTag(current.departmentType)"
              size="small"
              effect="plain"
            >
              {{ typeLabel(current.departmentType) }}
            </el-tag>
            <el-tag
              :type="current.enabled ? 'success' : 'warning'"
              size="small"
              effect="plain"
            >
              {{ current.enabled ? '启用' : '停用' }}
            </el-tag>
          </div>
          <div class="dept_main__meta">
            编码 {{ current.departmentCode }} · 直属成员 {{ memberTotal }} 人
          </div>
        </div>
        <div class="dept_main__actions">
          <el-button
            v-permission="'system:department:create'"
            type="primary"
            :disabled="!current.enabled"
            @click="openCreate(current)"
          >
            新增下级
          </el-button>
          <el-button
            v-permission="'system:department:update'"
            @click="openEdit(current)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'system:department:enable'"
            :type="current.enabled ? 'danger' : 'success'"
            @click="toggleEnabled(current)"
          >
            {{ current.enabled ? '停用' : '启用' }}
          </el-button>
        </div>
      </header>

      <div class="dept_main__members">
        <div class="dept_members__title">
          直属成员
          <span class="dept_members__tip">成员的新增与维护请前往「人员管理」，按部门筛选即可查看同一批人</span>
        </div>
        <el-table
          v-loading="memberLoading"
          :data="members"
        >
          <el-table-column
            label="学工号"
            min-width="110"
          >
            <template #default="{ row }">
              {{ row.personNo || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="realName"
            label="姓名"
            min-width="110"
            show-overflow-tooltip
          />
          <el-table-column
            label="身份"
            width="90"
          >
            <template #default="{ row }">
              <el-tag
                :type="PERSON_TYPE_TAG[row.personType] || 'info'"
                size="small"
                effect="plain"
              >
                {{ PERSON_TYPE_LABELS[row.personType] || row.personType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            label="手机号"
            min-width="120"
          >
            <template #default="{ row }">
              {{ row.phone || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="登录账号"
            min-width="120"
          >
            <template #default="{ row }">
              {{ row.accountName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="90"
          >
            <template #default="{ row }">
              <el-tag
                :type="STATUS_META[row.status]?.type || 'info'"
                size="small"
              >
                {{ STATUS_META[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="该部门暂无直属成员" />
          </template>
        </el-table>
        <div
          v-if="memberTotal > memberQuery.pageSize"
          class="dept_members__pager"
        >
          <el-pagination
            v-model:current-page="memberQuery.pageNum"
            v-model:page-size="memberQuery.pageSize"
            :total="memberTotal"
            layout="total, prev, pager, next"
            @current-change="loadMembers"
          />
        </div>
      </div>
    </section>
    <section
      v-else
      class="dept_page__main dept_page__main--empty"
    >
      <el-empty description="请选择左侧部门" />
    </section>

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
            v-model.trim="createForm.departmentCode"
            placeholder="留空自动生成（DEPT_0001 起），或手填如 assets_lab"
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
import { pagePersons } from '@/api/system/person'

const PERSON_TYPE_LABELS = { STUDENT: '学生', TEACHER: '老师', ADMIN: '管理员' }
const PERSON_TYPE_TAG = { STUDENT: 'success', TEACHER: 'primary', ADMIN: 'warning' }
const STATUS_META = {
  UNFILED: { label: '未建档', type: 'info' },
  NORMAL: { label: '正常', type: 'success' },
  ROSTER_DISABLED: { label: '名单停用', type: 'warning' },
  USER_DISABLED: { label: '已停用', type: 'danger' }
}

const tree = ref([])
const loading = ref(false)
const submitting = ref(false)
const current = ref(null)

async function load(selectId) {
  loading.value = true
  try {
    tree.value = await getDepartmentTree()
    // 默认选中指定部门或第一个根节点
    const targetId = selectId || current.value?.id
    const found = targetId ? findNode(tree.value, targetId) : null
    const target = found || tree.value[0] || null
    current.value = target
    if (target) {
      memberQuery.pageNum = 1
      await loadMembers()
    }
  } finally {
    loading.value = false
  }
}

function findNode(nodes, id) {
  for (const n of nodes) {
    if (n.id === id) return n
    const hit = findNode(n.children || [], id)
    if (hit) return hit
  }
  return null
}

function selectNode(data) {
  if (current.value?.id === data.id) return
  current.value = data
  memberQuery.pageNum = 1
  loadMembers()
}

function typeLabel(type) {
  return { SCHOOL: '学校', COLLEGE: '学院', ASSET_OFFICE: '资产处室' }[type] || type
}

function typeTag(type) {
  return { SCHOOL: 'primary', COLLEGE: 'success', ASSET_OFFICE: 'warning' }[type] || 'info'
}

// ---------- 直属成员 ----------
const members = ref([])
const memberTotal = ref(0)
const memberLoading = ref(false)
const memberQuery = reactive({ pageNum: 1, pageSize: 20 })

async function loadMembers() {
  if (!current.value) return
  memberLoading.value = true
  try {
    const data = await pagePersons({ ...memberQuery, departmentId: current.value.id })
    members.value = data.records
    memberTotal.value = data.total
  } finally {
    memberLoading.value = false
  }
}

// ---------- 新增 ----------
const createVisible = ref(false)
const createFormRef = ref()
const createParent = ref(null)
const createForm = reactive({ departmentCode: '', departmentName: '', departmentType: 'COLLEGE' })

const createRules = {
  departmentCode: [
    { pattern: /^[A-Za-z0-9_]{2,64}$/, message: '2-64 位字母、数字或下划线，留空自动生成', trigger: 'blur' }
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

onMounted(() => load())
</script>

<style scoped>
.dept_page {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

/* 左侧部门树：sticky 跟随滚动保持可见，树内容超高时内部独立滚动 */
.dept_page__side {
  display: flex;
  flex-direction: column;
  width: 280px;
  flex-shrink: 0;
  align-self: flex-start;
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 104px);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}

.dept_side__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.dept_side__tree {
  flex: 1;
  padding: 12px 10px;
  min-height: 200px;
  overflow: auto;
}

/* 树节点放宽行高与间距，避免密集 */
.dept_side__tree :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: 6px;
}

.dept_side__tree :deep(.el-tree-node) {
  margin-bottom: 2px;
}

.dept_side__node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dept_side__hint {
  margin: 0;
  padding: 10px 16px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-lighter);
}

/* 右侧详情 */
.dept_page__main {
  flex: 1;
  min-width: 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px 20px;
}

.dept_page__main--empty {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

.dept_main__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.dept_main__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.dept_main__meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.dept_main__actions {
  display: flex;
  gap: 10px;
}

.dept_main__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

/* 成员列表 */
.dept_members__title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 14px 0 10px;
  font-size: 14px;
  font-weight: 600;
}

.dept_members__tip {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.dept_members__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.dept_page__tip {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.7;
}
</style>
