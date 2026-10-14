<template>
  <div class="front_home">
    <div class="card">
      <h2 class="t-title">
        欢迎使用
      </h2>
      <p class="t-sub">
        {{ store.userInfo?.realName || '同学' }}，这里是实验室固定设备借用服务
      </p>
    </div>

    <div class="front_home__grid">
      <button
        v-for="entry in entries"
        :key="entry.path"
        class="card card--hover front_home__entry"
        @click="go(entry.path)"
      >
        <span class="front_home__entry_icon">
          <el-icon><component :is="entry.icon" /></el-icon>
        </span>
        <span class="t-strong front_home__entry_title">{{ entry.title }}</span>
        <span class="t-hint">{{ entry.desc }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { Checked, Promotion, Tickets } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { hasPermission } from '@/utils/permission'

const router = useRouter()
const store = useUserStore()

/** FrontLayout 不渲染菜单，前台各入口由首页卡片导航；按权限过滤显隐 */
const entries = computed(() => {
  const all = [
    {
      perms: 'equipment:booking:create',
      title: '发起借用',
      desc: '选择设备提交借用申请',
      path: '/equipment/booking/create',
      icon: markRaw(Promotion)
    },
    {
      perms: 'equipment:booking:list',
      title: '我的借用',
      desc: '查看借用进度与撤销',
      path: '/equipment/bookings/mine',
      icon: markRaw(Tickets)
    },
    {
      perms: 'equipment:booking:teacher_approve',
      title: '借用审批',
      desc: '处理学生的借用申请',
      path: '/equipment/approvals',
      icon: markRaw(Checked)
    }
  ]
  return all.filter((entry) => hasPermission(entry.perms, store.perms))
})

function go(path) {
  router.push(path)
}
</script>

<style scoped>
.front_home {
  padding: 14px;
}

.front_home__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.front_home__entry {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.front_home__entry:active {
  transform: scale(.98);
}

.front_home__entry_icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-bottom: 4px;
  border-radius: var(--r-md);
  background: var(--brand-grad-soft);
  color: var(--brand);
  font-size: 18px;
}

.front_home__entry_title {
  font-size: 15px;
}
</style>
