<template>
  <div class="page admin_home">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          {{ greeting }}，{{ store.userInfo?.realName || '管理员' }}
        </h2>
        <p class="t-sub">
          {{ dateLine }} · 实验室设备管理系统
        </p>
      </div>
    </div>

    <!-- 功能入口：三列桌面网格（规范 7.5） -->
    <div class="admin_home__grid">
      <div
        v-for="entry in visibleEntries"
        :key="entry.path"
        class="card card--hover admin_home__entry"
        tabindex="0"
        @click="router.push(entry.path)"
        @keyup.enter="router.push(entry.path)"
      >
        <div class="icon_box">
          <el-icon :size="19">
            <component :is="entry.icon" />
          </el-icon>
        </div>
        <div class="admin_home__entry_text">
          <span class="admin_home__entry_title">{{ entry.title }}</span>
          <span class="admin_home__entry_sub">{{ entry.sub }}</span>
        </div>
      </div>
    </div>

    <!-- 待办 / 记录：双列卡，空态优先（规范 7.8：接口未接入展示真实空态） -->
    <div class="admin_home__columns">
      <div class="card">
        <div class="sechead">
          待办事项
        </div>
        <div class="empty">
          <el-icon
            class="empty__icon"
            :size="40"
          >
            <Bell />
          </el-icon>
          暂无待办事项
        </div>
      </div>
      <div class="card">
        <div class="sechead">
          操作记录
        </div>
        <div class="empty">
          <el-icon
            class="empty__icon"
            :size="40"
          >
            <Document />
          </el-icon>
          暂无操作记录
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Collection, Document, Grid, Notebook, OfficeBuilding } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

/* 功能入口：仅展示当前账号有列表权限的模块 */
const ENTRIES = [
  {
    path: '/admin/space',
    title: '空间管理',
    sub: '校区 · 楼栋 · 实验室',
    icon: OfficeBuilding,
    perms: ['space:campus:list', 'space:lab:list']
  },
  {
    path: '/admin/equipment/category',
    title: '设备分类',
    sub: '多级分类树维护',
    icon: Collection,
    perms: ['equipment:category:list']
  },
  {
    path: '/admin/equipment/model',
    title: '设备型号',
    sub: '型号码与规格参数',
    icon: Grid,
    perms: ['equipment:model:list']
  },
  {
    path: '/admin/equipment/asset',
    title: '设备台账',
    sub: '资产登记 · 迁移 · 定级',
    icon: Notebook,
    perms: ['equipment:asset:list']
  }
]

const visibleEntries = computed(() =>
  ENTRIES.filter((entry) => entry.perms.some((perm) => store.perms.includes(perm)))
)

/* 问候语按时段，日期按本地日历展示 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 9) return '早上好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const dateLine = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long'
}).format(new Date())
</script>

<style scoped>
/* 三列桌面网格，≤900px 收两列（规范 7.5） */
.admin_home__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.admin_home__entry {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.admin_home__entry:active {
  transform: scale(.98);
}

.admin_home__entry_text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.admin_home__entry_title {
  font-size: 14.5px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.admin_home__entry_sub {
  font-size: 12px;
  color: var(--g3);
}

/* 待办/记录双列，窄屏收单列 */
.admin_home__columns {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

@media (max-width: 900px) {
  .admin_home__grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .admin_home__columns {
    grid-template-columns: 1fr;
  }
}
</style>
