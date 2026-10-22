<template>
  <div>
    <div class="toolbar">
      <el-select
        v-model="query.movementType"
        clearable
        placeholder="全部类型"
        @change="pager.search"
      >
        <el-option
          label="全部类型"
          value=""
        />
        <el-option
          v-for="(cfg, value) in movementTypeMap"
          :key="value"
          :label="cfg.label"
          :value="value"
        />
      </el-select>
      <el-select
        v-model="query.status"
        clearable
        placeholder="全部状态"
        @change="pager.search"
      >
        <el-option
          label="全部状态"
          value=""
        />
        <el-option
          v-for="(cfg, value) in documentStatusMap"
          :key="value"
          :label="cfg.label"
          :value="value"
        />
      </el-select>
      <el-date-picker
        v-model="query.timeRange"
        type="datetimerange"
        value-format="YYYY-MM-DD HH:mm:ss"
        start-placeholder="业务时间起"
        end-placeholder="业务时间止"
        class="w360"
        @change="pager.search"
      />
      <el-button
        v-permission="'item:stock:create'"
        type="primary"
        @click="openCreate()"
      >
        新建单据
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
        prop="id"
        label="单据 ID"
        width="190"
        show-overflow-tooltip
      />
      <el-table-column
        label="类型"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.movementType"
            :map="movementTypeMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="90"
        align="center"
      >
        <template #default="{ row }">
          <StatusTag
            :value="row.status"
            :map="documentStatusMap"
          />
        </template>
      </el-table-column>
      <el-table-column
        prop="vendorName"
        label="供应商"
        width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="reason"
        label="事由"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        label="经办人"
        width="120"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ displayName(row.operatorName, row.operatorId) }}
        </template>
      </el-table-column>
      <el-table-column
        label="业务时间"
        width="170"
      >
        <template #default="{ row }">
          {{ fmtShanghai(row.occurredAt) }}
        </template>
      </el-table-column>
      <el-table-column
        label="操作"
        width="300"
        fixed="right"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openDetail(row.id)"
          >
            详情
          </el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            v-permission="'item:stock:approve'"
            link
            type="primary"
            @click="doApprove(row)"
          >
            审批
          </el-button>
          <el-button
            v-if="row.status === 'APPROVED'"
            v-permission="'item:stock:post'"
            link
            type="success"
            @click="doPost(row)"
          >
            过账
          </el-button>
          <el-button
            v-if="row.status === 'POSTED' && REVERSABLE_TYPES.includes(row.movementType)"
            v-permission="'item:stock:create'"
            link
            type="warning"
            @click="openCreate('REVERSAL', row.id)"
          >
            冲销
          </el-button>
          <el-button
            v-if="row.status === 'DRAFT' || row.status === 'APPROVED'"
            v-permission="'item:stock:cancel'"
            link
            type="danger"
            @click="doCancel(row)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <PagerBar :list="pager" />

    <DocumentCreateDialog
      v-model:visible="createVisible"
      :preset-type="presetType"
      :preset-id="presetId"
      @created="pager.load"
    />
    <DocumentDetailDrawer
      v-model:visible="detailVisible"
      :document-id="detailId"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { approveDocument, cancelDocument, pageDocuments, postDocument } from '@/api/item'
import { usePagedList } from '@/utils/usePagedList'
import { confirmAction } from '@/utils/confirm'
import { documentStatusMap, movementTypeMap } from '@/utils/statusMap'
import { fmtShanghai, shanghaiToUtcIso } from '@/utils/format'
import PagerBar from '@/components/PagerBar.vue'
import StatusTag from '@/components/StatusTag.vue'
import DocumentCreateDialog from '../components/DocumentCreateDialog.vue'
import DocumentDetailDrawer from '../components/DocumentDetailDrawer.vue'

/** 可冲销的已过账单据类型（与后端 REVERSABLE 定义一致） */
const REVERSABLE_TYPES = ['OPENING', 'PURCHASE_IN', 'ISSUE', 'SCRAP']

const query = reactive({ movementType: null, status: null, timeRange: null })

const pager = usePagedList((params) => {
  const range = query.timeRange || []
  return pageDocuments({
    ...params,
    movementType: query.movementType || undefined,
    status: query.status || undefined,
    occurredFrom: range[0] ? shanghaiToUtcIso(range[0]) : undefined,
    occurredTo: range[1] ? shanghaiToUtcIso(range[1]) : undefined
  })
})

const detailVisible = ref(false)
const detailId = ref('')

const createVisible = ref(false)
const presetType = ref('')
const presetId = ref('')

function openDetail(id) {
  detailId.value = id
  detailVisible.value = true
}

/** 裸 @click 会把 MouseEvent 传进来，仅接受字符串预置类型（REVERSAL 冲销入口） */
function openCreate(presetTypeArg, presetIdArg) {
  presetType.value = typeof presetTypeArg === 'string' ? presetTypeArg : ''
  presetId.value = typeof presetIdArg === 'string' ? presetIdArg : ''
  createVisible.value = true
}

async function doApprove(row) {
  const ok = await confirmAction(`审批通过单据 ${row.id}？通过后单据进入「已批准」，可执行过账。`, '审批确认', {
    type: 'info'
  })
  if (!ok) return
  try {
    await approveDocument(row.id)
    ElMessage.success('已批准，可执行过账')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doPost(row) {
  const ok = await confirmAction('过账将写入台账并更新余额（移动加权均价），确认执行？', '过账确认')
  if (!ok) return
  try {
    await postDocument(row.id)
    ElMessage.success('过账成功')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

async function doCancel(row) {
  const ok = await confirmAction('确认取消该单据？取消后不可恢复。', '取消确认')
  if (!ok) return
  try {
    await cancelDocument(row.id)
    ElMessage.success('已取消')
    pager.load()
  } catch {
    // 失败已由全局拦截器提示
  }
}

onMounted(pager.load)

defineExpose({ refresh: pager.load })

/** 姓名/ID 均缺失（经办人未登记）时显示占位 */
function displayName(name, id) {
  if (name) return name
  return id || '—'
}
</script>

<style scoped>
.w360 {
  width: 360px;
}
</style>
