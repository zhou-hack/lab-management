<template>
  <el-dialog
    :model-value="modelValue"
    title="场地导入"
    width="640px"
    :close-on-click-modal="false"
    @close="onClose"
    @closed="resetAll"
  >
    <!-- 结果视图 -->
    <div
      v-if="result"
      class="space_import__result"
    >
      <div class="space_import__summary">
        <div class="space_import__summary_item">
          <p class="t-sub">
            数据行
          </p>
          <p class="space_import__summary_num">
            {{ result.total }}
          </p>
        </div>
        <div class="space_import__summary_item">
          <p class="t-sub">
            成功导入
          </p>
          <p class="space_import__summary_num space_import__summary_num--ok">
            {{ result.successCount }}
          </p>
        </div>
        <div class="space_import__summary_item">
          <p class="t-sub">
            导入失败
          </p>
          <p
            class="space_import__summary_num"
            :class="result.failedCount ? 'space_import__summary_num--danger' : ''"
          >
            {{ result.failedCount }}
          </p>
        </div>
      </div>

      <el-alert
        v-if="result.successCount > 0"
        type="success"
        :closable="false"
        show-icon
        :title="`成功导入 ${result.successCount} 条场地，已同步到场地档案`"
        class="space_import__alert"
      />

      <template v-if="result.failures.length">
        <p class="space_import__fail_title">
          失败明细（{{ result.failures.length }} 条，可下载修正后重新上传）
        </p>
        <el-table
          :data="result.failures"
          height="260"
          size="small"
          class="table-scroll-x"
        >
          <el-table-column
            prop="rowNum"
            label="行号"
            width="64"
            align="right"
          />
          <el-table-column
            prop="labName"
            label="场地名称"
            min-width="150"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ row.labName || '—' }}
            </template>
          </el-table-column>
          <el-table-column
            prop="reason"
            label="错误原因"
            min-width="220"
            show-overflow-tooltip
          />
        </el-table>
      </template>

      <div class="space_import__result_actions">
        <el-button @click="downloadFailures">
          下载失败明细
        </el-button>
        <el-button
          type="primary"
          @click="resetAll"
        >
          继续导入
        </el-button>
      </div>
    </div>

    <!-- 上传视图 -->
    <div v-else>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="space_import__alert"
      >
        <template #title>
          <div class="space_import__tips">
            <p>
              支持 CSV / Excel（.csv、.xls、.xlsx），单文件不超过 10MB、最多 5000 行。
            </p>
            <p>
              类型填「教室/实训室」，状态填「可用/维护中/停用」，开放时段格式 08:00~22:00（留空默认 08:00~22:00）。
            </p>
            <p>
              导出的 CSV 可直接改完再导入；重名（含已删除场地）与非法行不会入库，可下载失败明细修正。
            </p>
          </div>
        </template>
      </el-alert>

      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        :on-change="onFileChange"
        :on-exceed="onExceed"
        accept=".csv,.xls,.xlsx"
      >
        <el-icon class="el-icon--upload">
          <UploadFilled />
        </el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>点击选择</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            只能上传 .csv / .xls / .xlsx 文件，且不超过 10MB
          </div>
        </template>
      </el-upload>
    </div>

    <template #footer>
      <div v-if="!result">
        <el-button
          :loading="templateLoading"
          @click="onDownloadTemplate"
        >
          下载导入模板
        </el-button>
        <el-button @click="onClose">
          取消
        </el-button>
        <el-button
          type="primary"
          :disabled="!selectedFile"
          :loading="uploading"
          @click="onSubmit"
        >
          开始导入
        </el-button>
      </div>
      <div v-else>
        <el-button
          type="primary"
          @click="onClose"
        >
          完成
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { importSpaces, downloadImportTemplate } from '@/api/space'

/** 与后端 SpaceImportFileParser.HEADERS 对应的可编辑列（失败明细按此导出） */
const FAIL_HEADERS = ['场地名称', '类型', '位置', '容纳人数', '开放时段', '状态', '配套设备']
const MAX_SIZE = 10 * 1024 * 1024
const ALLOWED_EXT = ['.csv', '.xls', '.xlsx']

const emit = defineEmits(['close', 'success'])
defineProps({
  modelValue: { type: Boolean, default: false }
})

const uploadRef = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const templateLoading = ref(false)
const result = ref(null)

function validateFile(file) {
  const name = (file.name || '').toLowerCase()
  const extOk = ALLOWED_EXT.some((ext) => name.endsWith(ext))
  if (!extOk) {
    ElMessage.error('仅支持 .csv / .xls / .xlsx 文件')
    return false
  }
  if (file.size > MAX_SIZE) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

function onFileChange(uploadFile) {
  if (uploadFile.status === 'ready' && validateFile(uploadFile.raw)) {
    selectedFile.value = uploadFile.raw
  } else {
    selectedFile.value = null
    uploadRef.value?.clearFiles()
  }
}

function onExceed(files) {
  uploadRef.value?.clearFiles()
  const file = files[0]
  if (validateFile(file)) {
    uploadRef.value?.handleStart(file)
  }
}

async function onSubmit() {
  if (!selectedFile.value) {
    return
  }
  uploading.value = true
  try {
    result.value = await importSpaces(selectedFile.value)
    if (result.value.successCount > 0) {
      emit('success')
    }
  } catch {
    // 400/403/超限等错误已由 Axios 拦截器统一提示
  } finally {
    uploading.value = false
  }
}

async function onDownloadTemplate() {
  templateLoading.value = true
  try {
    const blob = await downloadImportTemplate()
    triggerDownload(blob, 'spaces_import_template.csv')
  } catch {
    // 错误提示由请求拦截器统一处理
  } finally {
    templateLoading.value = false
  }
}

/** 失败明细：错误行号 + 原始 7 列可编辑数据 + 错误原因（UTF-8 BOM，Excel 可直接打开修正） */
function downloadFailures() {
  if (!result.value?.failures?.length) {
    return
  }
  const header = ['错误行号', ...FAIL_HEADERS, '错误原因']
  const lines = [header.map(csvEscape).join(',')]
  for (const f of result.value.failures) {
    const row = [String(f.rowNum), ...FAIL_HEADERS.map((h) => f.raw?.[h] ?? '')]
    lines.push([...row, f.reason].map(csvEscape).join(','))
  }
  const blob = new Blob(['﻿' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  triggerDownload(blob, `spaces_import_failed_${Date.now()}.csv`)
}

function csvEscape(value) {
  const s = value == null ? '' : String(value)
  if (/[",\r\n]/.test(s)) {
    return `"${s.replace(/"/g, '""')}"`
  }
  return s
}

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

function resetAll() {
  result.value = null
  selectedFile.value = null
  uploadRef.value?.clearFiles()
}

function onClose() {
  emit('close')
}
</script>

<style scoped>
.space_import__alert {
  margin-bottom: 14px;
}

.space_import__tips p {
  margin: 2px 0;
  line-height: 1.6;
}

.space_import__summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}

.space_import__summary_item {
  padding: 14px 18px;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 10px;
  text-align: center;
}

.space_import__summary_num {
  margin: 6px 0 0;
  font-size: 26px;
  font-weight: var(--fw-semibold, 600);
  font-variant-numeric: tabular-nums;
}

.space_import__summary_num--ok {
  color: var(--ok, #2e9e5b);
}

.space_import__summary_num--danger {
  color: var(--danger, #e5484d);
}

.space_import__fail_title {
  margin: 14px 0 8px;
  font-size: 13px;
  font-weight: var(--fw-medium, 500);
}

.space_import__result_actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}
</style>
