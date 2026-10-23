<template>
  <div class="sc">
    <div class="sc__head">
      <div>
        <h2 class="t-title sc__title">
          空间场地综合管理
        </h2>
        <p class="t-sub">
          场地档案、预约订单与审批待办同屏管理，无需跨页面跳转
        </p>
      </div>
      <el-badge
        v-if="pendingCount > 0"
        :value="pendingCount"
        type="warning"
        class="sc__badge"
      >
        <el-button
          type="warning"
          plain
          @click="activeTab = 'approval'"
        >
          待审批提醒
        </el-button>
      </el-badge>
    </div>

    <el-tabs
      v-model="activeTab"
      class="sc__tabs"
      @tab-change="onTabChange"
    >
      <el-tab-pane
        label="场地管理"
        name="labs"
      >
        <LabList v-if="loaded.labs" />
      </el-tab-pane>
      <el-tab-pane
        label="预约管理"
        name="orders"
      >
        <AdminBookingList v-if="loaded.orders" />
      </el-tab-pane>
      <el-tab-pane name="approval">
        <template #label>
          <el-badge
            :value="pendingCount"
            :hidden="!pendingCount"
            type="warning"
          >
            预约审批
          </el-badge>
        </template>
        <BookingApproval
          v-if="loaded.approval"
          @decided="refreshPending"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import LabList from './LabList.vue'
import AdminBookingList from '../booking/BookingList.vue'
import BookingApproval from '../booking/BookingApproval.vue'
import { getBookingStats } from '@/api/booking'

/**
 * 空间场地综合管理（聚合视图）：场地管理 / 预约管理 / 预约审批 三功能
 * 页签同屏切换，待审批数实时提醒；页签懒加载避免一次性挂载三个重视图。
 */
const activeTab = ref('labs')
const loaded = reactive({ labs: true, orders: false, approval: false })
const pendingCount = ref(0)

function onTabChange(name) {
  loaded[name] = true
  if (name === 'approval') refreshPending()
}

async function refreshPending() {
  try {
    const stats = await getBookingStats()
    pendingCount.value = Number(stats?.pending) || 0
  } catch {
    pendingCount.value = 0
  }
}

onMounted(refreshPending)
</script>

<style scoped>
.sc {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sc__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.sc__title {
  margin: 0 0 4px;
  font-size: 21px;
}

.sc__tabs :deep(.el-tabs__item) {
  font-size: 14px;
}
</style>
