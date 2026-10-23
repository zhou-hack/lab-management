<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        class="w220"
        placeholder="名称关键词"
        @change="pager.search"
      />
      <el-select
        v-model="enabled"
        clearable
        placeholder="全部状态"
        @change="pager.search"
      >
        <el-option
          label="全部状态"
          value=""
        />
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
        v-permission="'item:location:create'"
        type="primary"
        @click="openCreate"
      >
        新增库位
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
        prop="locationName"
        label="库位名称"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="locationDetail"
        label="位置详述"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        label="保管人"
        width="120"
        align="center"
      >
        <template #default="{ row }">
          {{ row.custodianName || row.custodianId || '—' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="labId"
        label="实验室 ID"
        width="170"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ row.labId || '（未挂接）' }}
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
        label="更新时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.updateTime) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="200"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            v-permission="'item:location:update'"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'item:location:enable'"
            link
            :type="row.enabled ? 'warning' : 'success'"
            @click="doToggle(row)"
          >
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-permission="'item:location:delete'"
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

    <!-- 新增 / 编辑库位 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑库位' : '新增库位'"
      width="520px"
    >
      <el-form label-width="100px">
        <el-form-item label="实验室 ID">
          <el-input
            v-model="form.labId"
            class="w240"
            maxlength="19"
            placeholder="纯数字 ID，空间模块建成前可空"
          />
        </el-form-item>
        <el-form-item
          label="库位名称"
          required
        >
          <el-input
            v-model="form.locationName"
            class="w240"
            maxlength="128"
            placeholder="如 一号实验室 A 区药品柜"
          />
        </el-form-item>
        <el-form-item label="位置详述">
          <el-input
            v-model="form.locationDetail"
            type="textarea"
            :rows="2"
            maxlength="255"
            placeholder="如 A 区 3 层左柜"
          />
        </el-form-item>
        <el-form-item label="保管人">
          <UserSelect
            v-model="form.custodianId"
            class="w240"
            :preset-name="form.custodianName"
            placeholder="输入姓名/账号搜索，每库位一名"
          />
        </el-form-item>
      </el-form>
      <div class="muted small">
        库位被库存余额 / 单据明细引用时禁止删除。保管人按姓名/账号/学工号前缀搜索。
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createStockLocation,
  deleteStockLocation,
  pageLocations,
  updateStockLocation,
  updateLocationStatus
} from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { fmtShanghai } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import UserSelect from '@/components/UserSelect.vue'

const keyword = ref('')
const enabled = ref(null)

const pager = usePagedList((params) =>
  pageLocations({
    ...params,
    keyword: keyword.value || undefined,
    enabled: enabled.value || undefined
  })
)

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref(null)
const form = reactive(emptyForm())

function emptyForm() {
  return { id: '', labId: '', locationName: '', locationDetail: '', custodianId: '', custodianName: '' }
}

function openCreate() {
  editing.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    id: row.id,
    labId: row.labId || '',
    locationName: row.locationName,
    locationDetail: row.locationDetail || '',
    custodianId: row.custodianId || '',
    custodianName: row.custodianName || ''
  })
  dialogVisible.value = true
}

async function submitForm() {
  if (form.labId && !/^\d{1,19}$/.test(form.labId)) {
    ElMessage.warning('实验室 ID 必须为纯数字')
    return
  }
  if (!form.locationName) {
    ElMessage.warning('库位名称不能为空')
    return
  }
  submitting.value = true
  try {
    const payload = {
      labId: form.labId || undefined,
      locationName: form.locationName,
      locationDetail: form.locationDetail || undefined,
      custodianId: form.custodianId || undefined
    }
    if (editing.value) {
      await updateStockLocation({ id: form.id, ...payload })
      ElMessage.success('库位已更新')
    } else {
      await createStockLocation(payload)
      ElMessage.success('库位已创建')
    }
    dialogVisible.value = false
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    submitting.value = false
  }
}

async function doToggle(row) {
  const target = !row.enabled
  const ok = await confirmAction(
    target ? `确认启用库位「${row.locationName}」？` : `确认停用库位「${row.locationName}」？停用后单据不可选该库位。`
  )
  if (!ok) return
  try {
    await updateLocationStatus(row.id, target)
    ElMessage.success(target ? '已启用' : '已停用')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doDelete(row) {
  const ok = await confirmAction(
    `确认删除库位「${row.locationName}」？被余额 / 单据明细引用时将被拒绝。`,
    '删除确认'
  )
  if (!ok) return
  try {
    await deleteStockLocation(row.id)
    ElMessage.success('已删除')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

onMounted(pager.load)

defineExpose({ refresh: pager.load })
</script>
