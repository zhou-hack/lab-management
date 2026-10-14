<template>
  <div class="category_manage">
    <div class="card">
      <div class="category_manage__toolbar">
        <span class="t-sub">多级设备分类树，设备型号按分类挂靠；编码创建后不可修改、删除后不可复用</span>
        <el-button
          v-permission="'equipment:category:create'"
          type="primary"
          class="category_manage__toolbar_action"
          @click="openCreate(null)"
        >
          新增根分类
        </el-button>
      </div>
      <div
        v-loading="loading"
        class="table-scroll-x"
      >
        <el-tree
          v-if="tree.length"
          :data="tree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <div class="category_manage__node">
              <span class="t-body category_manage__node_label">
                <span class="t-hint">{{ data.categoryCode }}</span>
                {{ data.categoryName }}
              </span>
              <span
                v-if="data.enabled !== 1"
                class="pill pill--muted"
              >
                已停用
              </span>
              <span class="category_manage__node_actions">
                <el-button
                  v-permission="'equipment:category:create'"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openCreate(data)"
                >
                  新增子级
                </el-button>
                <el-button
                  v-permission="'equipment:category:update'"
                  link
                  type="primary"
                  size="small"
                  @click.stop="openEdit(data)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="'equipment:category:delete'"
                  link
                  type="danger"
                  size="small"
                  @click.stop="removeCategory(data)"
                >
                  删除
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty
          v-if="!loading && !tree.length"
          description="暂无分类，请从根分类开始建立"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新增分类' : '编辑分类'"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
      >
        <el-form-item label="父分类">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            node-key="id"
            check-strictly
            clearable
            placeholder="不选则为根分类"
          />
        </el-form-item>
        <el-form-item
          label="分类编码"
          prop="categoryCode"
        >
          <el-input
            v-model="form.categoryCode"
            :disabled="dialog.mode === 'edit'"
            maxlength="64"
            placeholder="如 PC_LAPTOP（创建后不可改）"
          />
        </el-form-item>
        <el-form-item
          label="分类名称"
          prop="categoryName"
        >
          <el-input
            v-model="form.categoryName"
            maxlength="128"
            placeholder="如 笔记本电脑"
          />
        </el-form-item>
        <el-form-item
          v-if="dialog.mode === 'edit'"
          label="启用状态"
        >
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="dialog.saving"
          @click="save"
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
import { categoryTree, createCategory, deleteCategory, updateCategory } from '@/api/equipment'

const loading = ref(false)
const tree = ref([])

async function loadTree() {
  loading.value = true
  try {
    tree.value = (await categoryTree()) || []
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}

/**
 * 克隆分类树为 el-tree-select 数据（{ id, label, children }）。
 * excludeId 存在时剔除该节点及其整棵子树，用于编辑时避免选到自己的后代形成循环。
 */
function cloneLabeled(nodes, excludeId) {
  const result = []
  for (const node of nodes || []) {
    if (excludeId !== null && String(node.id) === String(excludeId)) {
      continue
    }
    result.push({
      id: node.id,
      label: `${node.categoryName}（${node.categoryCode}）`,
      children: cloneLabeled(node.children, excludeId)
    })
  }
  return result
}

const dialog = reactive({ visible: false, mode: 'create', saving: false })
const formRef = ref()
const form = reactive({ id: '', parentId: null, categoryCode: '', categoryName: '', enabled: 1 })
const rules = {
  categoryCode: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const parentOptions = computed(() =>
  cloneLabeled(tree.value, dialog.mode === 'edit' ? form.id : null)
)

function openCreate(parent) {
  dialog.mode = 'create'
  Object.assign(form, {
    id: '',
    parentId: parent ? parent.id : null,
    categoryCode: '',
    categoryName: '',
    enabled: 1
  })
  dialog.visible = true
}

function openEdit(data) {
  dialog.mode = 'edit'
  Object.assign(form, {
    id: data.id,
    parentId: data.parentId || null,
    categoryCode: data.categoryCode,
    categoryName: data.categoryName,
    enabled: data.enabled
  })
  dialog.visible = true
}

async function save() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  dialog.saving = true
  try {
    if (dialog.mode === 'create') {
      await createCategory({
        parentId: form.parentId || null,
        categoryCode: form.categoryCode.trim(),
        categoryName: form.categoryName.trim()
      })
    } else {
      await updateCategory(form.id, {
        parentId: form.parentId || null,
        categoryName: form.categoryName.trim(),
        enabled: form.enabled
      })
    }
    ElMessage.success('保存成功')
    dialog.visible = false
    loadTree()
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    dialog.saving = false
  }
}

async function removeCategory(data) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${data.categoryName}」吗？删除后编码不可复用。`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteCategory(data.id)
    ElMessage.success('删除成功')
    loadTree()
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}

onMounted(loadTree)
</script>

<style scoped>
.category_manage__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

/* 提高选择器特异性以覆盖 .el-button + .el-button 的相邻 margin */
.category_manage__toolbar .category_manage__toolbar_action {
  margin-left: auto;
  flex-shrink: 0;
}

.category_manage__node {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding-right: 8px;
}

.category_manage__node_label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category_manage__node_actions {
  display: none;
  flex-shrink: 0;
  white-space: nowrap;
}

.category_manage__node:hover .category_manage__node_actions {
  display: inline-flex;
}

/* 触摸设备无 hover，操作常驻显示 */
@media (hover: none), (pointer: coarse) {
  .category_manage__node_actions {
    display: inline-flex;
  }
}
</style>
