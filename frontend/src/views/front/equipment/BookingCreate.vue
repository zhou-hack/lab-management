<template>
  <div class="booking_create">
    <!-- 第一步：选择设备 -->
    <div class="card booking_create__section">
      <h3 class="t-body t-strong">
        1 · 选择设备
      </h3>
      <div class="booking_create__filters">
        <el-input
          v-model="assetQuery.keyword"
          class="booking_create__filter"
          placeholder="名称 / 编号 / 序列号"
          clearable
          @keyup.enter="searchAssets"
          @clear="searchAssets"
        />
        <el-select
          v-model="assetQuery.labId"
          class="booking_create__filter"
          placeholder="全部实验室"
          clearable
          filterable
          @change="searchAssets"
        >
          <el-option
            v-for="lab in labOptions"
            :key="lab.id"
            :label="`${lab.labName}（${lab.buildingName}）`"
            :value="lab.id"
          />
        </el-select>
        <el-button @click="searchAssets">
          查询
        </el-button>
      </div>
      <div class="table-scroll-x">
        <el-table
          v-loading="assetLoading"
          :data="assetRecords"
        >
          <el-table-column
            prop="equipmentName"
            label="设备名称"
            min-width="140"
          />
          <el-table-column
            prop="assetCode"
            label="资产编号"
            min-width="110"
          />
          <el-table-column
            prop="labName"
            label="实验室"
            min-width="120"
          />
          <el-table-column
            label="价值分级"
            width="90"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="valueLevelMeta(row).pill"
              >
                {{ valueLevelMeta(row).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="状态"
            width="80"
          >
            <template #default="{ row }">
              <span
                class="pill"
                :class="enumMeta(row.status, EQUIPMENT_STATUS).pill"
              >
                {{ enumMeta(row.status, EQUIPMENT_STATUS).label }}
              </span>
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="80"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                link
                :type="isSelected(row) ? 'success' : 'primary'"
                :disabled="!row.valueLevel || isSelected(row)"
                @click="selectAsset(row)"
              >
                {{ isSelected(row) ? '已选' : '选择' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-pagination
        v-model:current-page="assetQuery.pageNum"
        v-model:page-size="assetQuery.pageSize"
        class="booking_create__pager"
        :total="assetTotal"
        layout="prev, pager, next"
        small
        @change="loadAssets"
      />
      <p class="t-hint">
        仅展示可借用设备目录；「待分类」设备须由管理员完成价值定级后才可借用
      </p>
    </div>

    <!-- 第二步：借用信息 -->
    <div class="card booking_create__section">
      <h3 class="t-body t-strong">
        2 · 借用信息
      </h3>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item label="已选设备">
          <div class="booking_create__selected">
            <template v-if="selectedAsset">
              <span class="t-strong">{{ selectedAsset.equipmentName }}</span>
              <span class="t-sub">
                {{ selectedAsset.assetCode }} · {{ selectedAsset.labName }}
              </span>
              <el-button
                link
                type="danger"
                @click="clearAsset"
              >
                移除
              </el-button>
            </template>
            <span
              v-else
              class="t-hint"
            >请从上方设备目录中选择</span>
          </div>
        </el-form-item>
        <el-form-item
          v-if="isStudent"
          label="审批老师"
          prop="approvalTeacherId"
        >
          <el-select
            v-model="form.approvalTeacherId"
            placeholder="借用单将先由该老师审批"
          >
            <el-option
              v-for="teacher in teacherList"
              :key="teacher.id"
              :label="`${teacher.realName}（${teacher.personNo}）`"
              :value="teacher.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="借用时间"
          prop="range"
        >
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            format="YYYY-MM-DD HH:mm"
            value-format="YYYY-MM-DD HH:mm"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item
          label="借用用途"
          prop="purpose"
        >
          <el-input
            v-model="form.purpose"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请说明借用事由，如：毕业设计数据采集"
          />
        </el-form-item>
        <el-button
          class="booking_create__submit"
          type="primary"
          size="large"
          :loading="submitting"
          @click="submit"
        >
          提交借用申请
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createBooking, pageAssets, teacherOptions } from '@/api/equipment'
import { labOptions as fetchLabOptions } from '@/api/space'
import { shWallClockToUtcIso } from '@/utils/datetime'
import { EQUIPMENT_STATUS, VALUE_LEVEL, enumMeta } from '@/utils/enums'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

const isStudent = computed(() => store.roles.includes('STUDENT'))

/* ---------------- 设备目录 ---------------- */

const assetQuery = reactive({ pageNum: 1, pageSize: 10, keyword: '', labId: '' })
const assetRecords = ref([])
const assetTotal = ref(0)
const assetLoading = ref(false)
const labOptions = ref([])
const teacherList = ref([])
const selectedAsset = ref(null)

/** 幂等键：本会话内重复提交（含双击）命中后返回原单 */
const idempotencyKey = crypto.randomUUID()

function valueLevelMeta(row) {
  return row.valueLevel
    ? enumMeta(row.valueLevel, VALUE_LEVEL)
    : { label: '待分类', pill: 'pill--danger' }
}

function isSelected(row) {
  return !!selectedAsset.value && selectedAsset.value.id === row.id
}

async function loadAssets() {
  assetLoading.value = true
  try {
    const params = { pageNum: assetQuery.pageNum, pageSize: assetQuery.pageSize }
    if (assetQuery.keyword.trim()) {
      params.keyword = assetQuery.keyword.trim()
    }
    if (assetQuery.labId) {
      params.labId = assetQuery.labId
    }
    const data = await pageAssets(params)
    assetRecords.value = data.records || []
    assetTotal.value = data.total || 0
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    assetLoading.value = false
  }
}

function searchAssets() {
  assetQuery.pageNum = 1
  loadAssets()
}

function selectAsset(row) {
  selectedAsset.value = row
  form.assetId = row.id
}

function clearAsset() {
  selectedAsset.value = null
  form.assetId = ''
}

/* ---------------- 借用表单 ---------------- */

const formRef = ref()
const submitting = ref(false)
const form = reactive({ assetId: '', approvalTeacherId: '', purpose: '', range: [] })

const rules = {
  assetId: [{ required: true, message: '请选择借用设备', trigger: 'change' }],
  approvalTeacherId: [
    {
      validator: (rule, value, callback) => {
        if (isStudent.value && !value) {
          callback(new Error('请选择审批老师'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  range: [
    { required: true, type: 'array', message: '请选择借用时间', trigger: 'change' },
    {
      validator: (rule, value, callback) => {
        if (Array.isArray(value) && value[0] && value[1] && String(value[0]) >= String(value[1])) {
          callback(new Error('结束时间必须晚于开始时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  purpose: [
    { required: true, message: '请填写借用用途', trigger: 'blur' },
    { max: 500, message: '借用用途不能超过 500 字', trigger: 'blur' }
  ]
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  let startAt
  let endAt
  try {
    startAt = shWallClockToUtcIso(form.range[0])
    endAt = shWallClockToUtcIso(form.range[1])
  } catch {
    ElMessage.error('借用时间格式无效，请重新选择')
    return
  }
  submitting.value = true
  try {
    await createBooking({
      assetId: form.assetId,
      approvalTeacherId: isStudent.value ? form.approvalTeacherId : null,
      purpose: form.purpose.trim(),
      startAt,
      endAt,
      idempotencyKey
    })
    ElMessage.success('提交成功，可在「我的借用」查看进度')
    router.push('/equipment/bookings/mine')
  } catch {
    // 失败提示由请求拦截器统一处理
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadAssets()
  fetchLabOptions().then((data) => {
    labOptions.value = data || []
  }).catch(() => {
    // 失败提示由请求拦截器统一处理
  })
  if (isStudent.value) {
    teacherOptions().then((data) => {
      teacherList.value = data || []
    }).catch(() => {
      // 失败提示由请求拦截器统一处理
    })
  }
})
</script>

<style scoped>
.booking_create {
  padding: 14px;
  display: grid;
  gap: 12px;
}

.booking_create__section h3 {
  margin: 0 0 12px;
}

.booking_create__filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.booking_create__filter {
  width: 180px;
}

.booking_create__pager {
  margin-top: 12px;
  justify-content: center;
}

.booking_create__selected {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border-radius: var(--r-md);
  background: var(--brand-grad-soft);
}

.booking_create__submit {
  width: 100%;
  height: 44px;
  margin-top: 4px;
  border-radius: var(--r-full);
  font-size: 15px;
}
</style>
