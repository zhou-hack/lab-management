<template>
  <div class="equip_section category_manage">
    <div class="sechead">
      <div class="category_manage__sechead_text">
        分类目录
        <span class="t-hint">共 {{ totalCount }} 个分类</span>
      </div>
      <el-button
        v-permission="'equipment:category:create'"
        type="primary"
        size="small"
        @click="openCreate(null)"
      >
        新增根分类
      </el-button>
    </div>
    <!-- 加载骨架（规范 7.8）：模拟树形行位，避免白屏与纯 spinner -->
    <div
      v-if="loading"
      class="category_manage__skeleton"
    >
      <span class="skeleton category_manage__sk_row" />
      <span class="skeleton category_manage__sk_row category_manage__sk_row--indent" />
      <span class="skeleton category_manage__sk_row category_manage__sk_row--indent" />
      <span class="skeleton category_manage__sk_row" />
      <span class="skeleton category_manage__sk_row" />
    </div>
    <el-tree
      v-else-if="tree.length"
      :data="tree"
      node-key="id"
      default-expand-all
      :expand-on-click-node="false"
    >
      <template #default="{ data }">
        <div
          class="category_manage__node"
          :class="{ 'category_manage__node--off': data.enabled !== 1 }"
        >
          <span class="category_manage__node_icon">
            <el-icon :size="15">
              <Collection />
            </el-icon>
          </span>
          <span class="category_manage__node_name">{{ data.categoryName }}</span>
          <span class="category_manage__node_code">{{ data.categoryCode }}</span>
          <span
            v-if="childCount(data)"
            class="category_manage__node_children"
          >{{ childCount(data) }} 个子级</span>
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
    <div
      v-if="!loading && !tree.length"
      class="empty"
    >
      <el-icon
        class="empty__icon"
        :size="40"
      >
        <Files />
      </el-icon>
      暂无分类，请从根分类开始建立
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
import { Collection, Files } from '@element-plus/icons-vue'
import { categoryTree, createCategory, deleteCategory, updateCategory } from '@/api/equipment'

/**
 * 分类目录抽屉内容（嵌于设备型号面板的「管理分类」抽屉内）。
 * 挂载即加载全树（抽屉 destroy-on-close，每次打开重新拉取）；
 * 任何变更成功后 emit('changed') 通知父级刷新分类筛选与型号分组。
 */
const emit = defineEmits(['changed'])

const loading = ref(false)
const tree = ref([])

/** 全树节点总数（递归） */
const totalCount = computed(() => {
  let count = 0
  const walk = (nodes) => {
    for (const node of nodes || []) {
      count += 1
      walk(node.children)
    }
  }
  walk(tree.value)
  return count
})

/** 直接子级数量（仅统计启用子级，与视觉强调一致） */
function childCount(data) {
  return (data.children || []).filter((c) => c.enabled === 1).length
}

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

onMounted(loadTree)

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
    emit('changed')
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
    emit('changed')
  } catch {
    // 失败提示由请求拦截器统一处理
  }
}
</script>

<style scoped>
.category_manage__sechead_text {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

/* 加载骨架：模拟树形行位（规范 7.8） */
.category_manage__skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 2px 10px;
}

.category_manage__sk_row {
  height: 36px;
  border-radius: var(--r-sm);
}

.category_manage__sk_row--indent {
  width: 86%;
  margin-left: 24px;
}

/* 树行：44px 行高 + 圆角 hover 底（行级交互反馈） */
.category_manage :deep(.el-tree) {
  --el-tree-node-content-height: 44px;
}

.category_manage :deep(.el-tree-node__content) {
  border-radius: var(--r-sm);
  transition: background-color var(--dur-fast) var(--ease);
}

.category_manage :deep(.el-tree-node__content:hover) {
  background: var(--brand-a04);
}

/* 节点内容：图标容器 + 名称 + 编码徽章 + 子级数 + 状态/操作 */
.category_manage__node {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 10px;
  min-width: 0;
  height: 100%;
  padding-right: 10px;
}

.category_manage__node_icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--brand-a08);
  color: var(--brand);
  flex-shrink: 0;
}

.category_manage__node--off .category_manage__node_icon {
  background: var(--muted-bg);
  color: var(--muted);
}

.category_manage__node--off .category_manage__node_name {
  color: var(--g3);
}

.category_manage__node_name {
  font-size: 14px;
  font-weight: var(--fw-medium);
  color: var(--g1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 编码徽章：sunken 底 + 发丝线 + 等宽数字 */
.category_manage__node_code {
  font-family: var(--font-num);
  font-size: 11px;
  color: var(--g3);
  background: var(--surface-sunken);
  border: .5px solid var(--hair);
  border-radius: var(--r-xs);
  padding: 1px 7px;
  white-space: nowrap;
  flex-shrink: 0;
}

.category_manage__node--off .category_manage__node_code {
  color: var(--g4);
}

/* 子级计数：弱化元数据，等宽数字 */
.category_manage__node_children {
  font-size: 11.5px;
  color: var(--g3);
  white-space: nowrap;
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.category_manage__node_actions {
  display: none;
  flex-shrink: 0;
  white-space: nowrap;
}

.category_manage :deep(.el-tree-node__content:hover) .category_manage__node_actions {
  display: inline-flex;
}
</style>
