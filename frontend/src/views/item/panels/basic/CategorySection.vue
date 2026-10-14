<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        class="w220"
        placeholder="编码 / 名称关键词"
        @change="pager.search"
      />
      <el-select
        v-model="enabled"
        clearable
        placeholder="全部状态"
        class="w140"
        @change="pager.search"
      >
        <el-option
          label="启用"
          value="true"
        />
        <el-option
          label="停用"
          value="false"
        />
      </el-select>
      <el-button
        v-permission="'item:category:create'"
        type="primary"
        @click="openCreate"
      >
        新增分类
      </el-button>
      <el-button @click="pager.search">
        查询
      </el-button>
    </div>

    <el-table
      v-loading="pager.loading"
      :data="pager.rows"
      border
    >
      <el-table-column
        prop="categoryCode"
        label="分类编码"
        width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="categoryName"
        label="分类名称"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        label="父分类"
        min-width="160"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.parentId ? (categoryNameMap[row.parentId] || `${row.parentId.slice(0, 8)}…`) : '（根节点）' }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="200"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-permission="'item:category:update'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'item:category:enable'"
            link
            :type="row.enabled ? 'warning' : 'success'"
            @click="doToggle(row)"
          >
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-permission="'item:category:delete'"
            link
            type="danger"
            @click="doDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <!-- 新增 / 编辑分类 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑分类' : '新增分类'"
      width="480px"
    >
      <el-form label-width="90px">
        <el-form-item
          label="分类编码"
          required
        >
          <el-input
            v-model="form.categoryCode"
            class="w240"
            maxlength="64"
            placeholder="如 CAT-CHEM（删除后不可复用）"
          />
        </el-form-item>
        <el-form-item
          label="分类名称"
          required
        >
          <el-input
            v-model="form.categoryName"
            class="w240"
            maxlength="128"
            placeholder="如 化学试剂"
          />
        </el-form-item>
        <el-form-item label="父分类">
          <el-select
            v-model="form.parentId"
            clearable
            filterable
            class="w240"
            placeholder="不选为根节点"
          >
            <el-option
              v-for="c in parentOptions"
              :key="c.id"
              :label="`${c.categoryCode} · ${c.categoryName}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="muted small">
        分类编码全局唯一；存在子分类或被物料引用时禁止删除。
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="submitForm"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createCategory,
  deleteCategory,
  listEnabledCategories,
  pageCategories,
  updateCategory,
  updateCategoryStatus
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { fmtShanghai } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'

const keyword = ref('')
const enabled = ref(null)

const pager = usePagedList((params) =>
  pageCategories({
    ...params,
    keyword: keyword.value || undefined,
    enabled: enabled.value === null || enabled.value === undefined ? undefined : enabled.value
  })
)

const allCategories = ref([])
const categoryNameMap = computed(() =>
  allCategories.value.reduce((map, c) => {
    map[c.id] = `${c.categoryCode} · ${c.categoryName}`
    return map
  }, {})
)

async function loadAllCategories() {
  try {
    allCategories.value = await listEnabledCategories()
  } catch {
    // 失败已由全局拦截器提示
  }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const form = reactive({ id: '', categoryCode: '', categoryName: '', parentId: null })

/** 父分类可选项：编辑时排除自身，避免把自己设为父分类形成环 */
const parentOptions = computed(() =>
  editing.value ? allCategories.value.filter((c) => c.id !== editing.value.id) : allCategories.value
)

function openCreate() {
  editing.value = null
  form.id = ''
  form.categoryCode = ''
  form.categoryName = ''
  form.parentId = null
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  form.id = row.id
  form.categoryCode = row.categoryCode
  form.categoryName = row.categoryName
  form.parentId = row.parentId || null
  dialogVisible.value = true
}

async function submitForm() {
  if (!form.categoryCode) {
    ElMessage.warning('分类编码不能为空')
    return
  }
  if (!form.categoryName) {
    ElMessage.warning('分类名称不能为空')
    return
  }
  submitting.value = true
  try {
    const payload = {
      categoryCode: form.categoryCode,
      categoryName: form.categoryName,
      parentId: form.parentId || undefined
    }
    if (editing.value) {
      await updateCategory({ id: form.id, ...payload })
      ElMessage.success('分类已更新')
    } else {
      await createCategory(payload)
      ElMessage.success('分类已创建')
    }
    dialogVisible.value = false
    pager.load()
    loadAllCategories()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doToggle(row) {
  const target = !row.enabled
  const ok = await confirmAction(
    target ? `确认启用分类「${row.categoryName}」？` : `确认停用分类「${row.categoryName}」？停用后新建物料不可选该分类。`
  )
  if (!ok) return
  try {
    await updateCategoryStatus(row.id, target)
    ElMessage.success(target ? '已启用' : '已停用')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doDelete(row) {
  const ok = await confirmAction(
    `确认删除分类「${row.categoryName}」？逻辑删除后编码不可复用；存在子分类或被物料引用时将被拒绝。`,
    '删除确认'
  )
  if (!ok) return
  try {
    await deleteCategory(row.id)
    ElMessage.success('已删除')
    pager.load()
    loadAllCategories()
  } catch {
    // 失败已由全局拦截器提示
  }
}

onMounted(() => {
  loadAllCategories()
  pager.load()
})

defineExpose({ refresh: pager.load })
</script>
