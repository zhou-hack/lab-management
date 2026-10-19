<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="600px"
    @closed="resetState"
  >
    <div class="muted small mb8">
      {{ tips }}
    </div>
    <div class="mb8">
      <el-button
        size="small"
        :loading="templateLoading === 'xlsx'"
        @click="downloadTemplate('xlsx')"
      >
        下载 xlsx 模板
      </el-button>
      <el-button
        size="small"
        :loading="templateLoading === 'csv'"
        @click="downloadTemplate('csv')"
      >
        下载 csv 模板
      </el-button>
    </div>
    <el-upload
      ref="uploadRef"
      drag
      accept=".xlsx,.csv"
      :limit="1"
      :auto-upload="false"
      :file-list="fileList"
      :on-change="onFileChange"
      :on-remove="() => (fileList = [])"
    >
      <div class="el-upload__text">
        拖拽文件到此处，或点击选择（仅支持 .xlsx / .csv）
      </div>
    </el-upload>

    <div
      v-if="result"
      class="mt8"
    >
      <el-alert
        :title="resultTitle"
        :type="result.failureCount === 0 ? 'success' : 'warning'"
        :closable="false"
      />
      <div
        v-if="result.documentId"
        class="muted small mt4"
      >
        已生成草稿单 {{ result.documentId }}，请到「库存台账 → 单据管理」审批过账后生效。
      </div>
      <el-table
        v-if="result.failures.length"
        :data="result.failures"
        border
        size="small"
        class="mt8"
        max-height="260"
      >
        <el-table-column
          prop="rowNo"
          label="行号"
          width="80"
          align="center"
        />
        <el-table-column
          prop="reason"
          label="失败原因"
          min-width="300"
          show-overflow-tooltip
        />
      </el-table>
    </div>

    <template #footer>
      <el-button @click="visible = false">
        关闭
      </el-button>
      <el-button
        type="primary"
        :disabled="!selectedFile"
        :loading="importing"
        @click="submitImport"
      >
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { saveBlobFile } from '@/utils/download'

/**
 * 通用批量导入弹窗（物料 / 物料分类共用）
 * - uploadFn(file)：调用后端导入接口，返回 ImportResultVO（total/successCount/failureCount/failures）
 * - templateFn(format)：下载模板接口，返回 { blob, fileName }（request 拦截器 blob 分支）
 * 成功后 emit('imported')，父组件刷新列表
 */
const props = defineProps({
  title: { type: String, required: true },
  tips: { type: String, default: '' },
  uploadFn: { type: Function, required: true },
  templateFn: { type: Function, required: true },
  fallbackTemplateName: { type: String, default: '导入模板' }
})

const emit = defineEmits(['imported'])

const visible = ref(false)
const fileList = ref([])
const selectedFile = ref(null)
const importing = ref(false)
const templateLoading = ref('')
const result = ref(null)

const resultTitle = computed(() => {
  if (!result.value) return ''
  const r = result.value
  return `导入完成：共 ${r.total} 行，成功 ${r.successCount} 行，失败 ${r.failureCount} 行`
})

function open() {
  resetState()
  visible.value = true
}

function resetState() {
  fileList.value = []
  selectedFile.value = null
  result.value = null
  importing.value = false
  templateLoading.value = ''
}

function onFileChange(file, files) {
  const name = file.name || ''
  if (!/\.xlsx$/i.test(name) && !/\.csv$/i.test(name)) {
    ElMessage.warning('仅支持 .xlsx 或 .csv 文件')
    fileList.value = []
    selectedFile.value = null
    return
  }
  fileList.value = files.slice(-1)
  selectedFile.value = file.raw
}

async function downloadTemplate(format) {
  templateLoading.value = format
  try {
    const file = await props.templateFn(format)
    saveBlobFile(file, `${props.fallbackTemplateName}.${format}`)
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    templateLoading.value = ''
  }
}

async function submitImport() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importing.value = true
  try {
    const r = await props.uploadFn(selectedFile.value)
    result.value = r
    if (r.failureCount === 0 && r.successCount > 0) {
      ElMessage.success(`导入成功：${r.successCount} 行`)
    }
    if (r.successCount > 0) {
      emit('imported')
    }
  } catch {
    // 失败已由全局拦截器提示
  } finally {
    importing.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.mb8 {
  margin-bottom: 8px;
}

.mt4 {
  margin-top: 4px;
}
</style>
