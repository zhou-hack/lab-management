<template>
  <div class="front_home">
    <!-- 页头：问候 + 角色（纯排版，无渐变 hero） -->
    <header class="front_home__header">
      <div>
        <h1 class="front_home__hello">
          {{ greeting }}，{{ store.user?.realName || '同学' }}
        </h1>
        <p class="t-sub">
          {{ roleText }} · 在线预约器材与场地，实时查看办理进度
        </p>
      </div>
    </header>

    <!-- 功能入口：桌面网格卡片 -->
    <section class="front_home__section">
      <h3 class="sechead">
        常用功能
      </h3>
      <div class="front_home__grid">
        <button
          v-for="entry in entries"
          :key="entry.name"
          class="entry card"
          :class="{ 'entry--disabled': !entry.path }"
          type="button"
          @click="go(entry)"
        >
          <span
            class="icon_box"
            :class="`icon_box--${entry.tone}`"
          >
            <el-icon><component :is="entry.icon" /></el-icon>
          </span>
          <span class="entry__text">
            <span class="entry__name">{{ entry.name }}</span>
            <span class="entry__desc">{{ entry.desc }}</span>
          </span>
        </button>
      </div>
    </section>

    <div class="front_home__columns">
      <!-- 待办：空态（接口接入前不展示虚构数据） -->
      <section class="front_home__section">
        <div class="card card--pad0">
          <div class="card__head">
            <span class="card__title">待我处理</span>
          </div>
          <div class="card__body">
            <div class="empty">
              <div class="empty__icon">
                ✓
              </div>
              <div class="empty__text">
                暂无待办事项，一切顺利
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 最近使用记录：空态 -->
      <section class="front_home__section">
        <div class="card card--pad0">
          <div class="card__head">
            <span class="card__title">最近使用记录</span>
          </div>
          <div class="card__body">
            <div class="empty">
              <div class="empty__icon">
                ∅
              </div>
              <div class="empty__text">
                暂无使用记录
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Calendar, Document, Box, Tools, DataLine } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const store = useUserStore()

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const ROLE_TEXT = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生', ASSISTANT: '教师助理' }
const roleText = computed(() => store.roles.map((r) => ROLE_TEXT[r] || r).join(' · ') || '实验室用户')

// 已落地的模块指向真实页面；未落地模块置灰（path 为空）
const entries = [
  { name: '场地预约', desc: '申请实验室使用时段', icon: Calendar, tone: 'ok', path: '/booking/labs' },
  { name: '我的申请', desc: '查看审批进度与单据', icon: Document, tone: 'brand', path: '/booking/labs' },
  { name: '器材借用', desc: '浏览并提交器材预约', icon: Tools, tone: 'brand', path: '' },
  { name: '物料代办', desc: '课前领用与课后回库', icon: Box, tone: 'muted', path: '' },
  { name: '使用记录', desc: '借出归还与拍照留痕', icon: DataLine, tone: 'warn', path: '' },
  { name: '通知消息', desc: '审批与催办提醒', icon: Bell, tone: 'ok', path: '' }
]

function go(entry) {
  if (!entry.path) {
    ElMessage.info('该功能建设中，敬请期待')
    return
  }
  router.push(entry.path)
}
</script>

<style scoped>
.front_home__header {
  padding: 8px 2px 18px;
}

.front_home__hello {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.front_home__section {
  margin-bottom: 18px;
}

/* 功能入口：桌面三列网格，卡片悬浮描边变蓝 */
.front_home__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.entry {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease);
}

.entry:not(.entry--disabled):hover {
  border-color: var(--hair-brand);
  transform: translateY(-2px);
}

.entry:not(.entry--disabled):active {
  transform: scale(.98);
}

.entry--disabled {
  opacity: .55;
  cursor: default;
}

.entry__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.entry__name {
  font-size: 14.5px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.entry__desc {
  font-size: 12px;
  color: var(--g3);
}

/* 下半区双列 */
.front_home__columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.card--pad0 {
  padding: 0;
}

.card--pad0 .card__body {
  padding: 4px 18px 8px;
}

@media (max-width: 900px) {
  .front_home__grid { grid-template-columns: repeat(2, 1fr); }
  .front_home__columns { grid-template-columns: 1fr; }
}
</style>
