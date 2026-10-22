<template>
  <div class="front_home">
    <!-- 渐变页头：白字标题 + 一行半透明副标题 + 右上角通知铃铛（规范 7.4） -->
    <header class="hero">
      <div class="hero__bar">
        <div>
          <div class="hero__hello t-hero-hello">
            {{ greeting }}，{{ store.user?.realName || '同学' }}
          </div>
          <div class="hero__role">
            {{ roleText }}
          </div>
        </div>
        <button
          class="hero__bell"
          aria-label="通知"
          @click="onNotify"
        >
          <el-icon><Bell /></el-icon>
          <span
            v-if="unread > 0"
            class="badge"
          />
        </button>
      </div>
      <p class="hero__sub">
        实验室设备管理系统 · 在线预约器材与场地，实时查看办理进度
      </p>
    </header>

    <div class="front_home__body hero__overlap">
      <!-- 快捷概览：三格指标，首卡上叠压进渐变区 -->
      <section class="card overview">
        <div
          v-for="item in overview"
          :key="item.label"
          class="overview__cell"
        >
          <div class="overview__num t-num">
            {{ item.value }}<span class="t-num__unit">{{ item.unit }}</span>
          </div>
          <div class="overview__label">
            {{ item.label }}
          </div>
        </div>
      </section>

      <!-- 功能入口宫格：两列，42×42 图标容器 + 主副文案（规范 7.5） -->
      <section class="front_home__section">
        <h3 class="sechead">
          常用功能
        </h3>
        <div class="entry_grid">
          <button
            v-for="entry in entries"
            :key="entry.name"
            class="entry"
            type="button"
            @click="go(entry.path)"
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

      <!-- 待办列表：白卡 + 0.5px 细线行分隔 -->
      <section class="front_home__section">
        <div class="card card--pad0">
          <div class="card__head">
            <span class="card__title">待我处理</span>
            <span class="pill pill--brand">
              {{ todos.length }} 项
            </span>
          </div>
          <div class="card__body">
            <template v-if="loading">
              <div
                v-for="n in 3"
                :key="n"
                class="skeleton skeleton__row"
              />
            </template>
            <template v-else-if="todos.length">
              <div
                v-for="todo in todos"
                :key="todo.id"
                class="list_row"
              >
                <span
                  class="icon_box icon_box--grad icon_box--lg"
                  :class="`icon_box--${todo.tone}`"
                >
                  {{ todo.tag }}
                </span>
                <div class="list_row__main">
                  <div class="list_row__title">
                    {{ todo.title }}
                  </div>
                  <div class="list_row__meta">
                    {{ todo.meta }}
                  </div>
                </div>
                <span
                  class="pill"
                  :class="`pill--${todo.tone}`"
                >
                  {{ todo.status }}
                </span>
              </div>
            </template>
            <div
              v-else
              class="empty"
            >
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

      <!-- 最近使用记录：键值对摘要 -->
      <section class="front_home__section">
        <div class="card">
          <div class="card__title front_home__cardtitle">
            最近使用记录
          </div>
          <div class="list_row">
            <span class="icon_box icon_box--grad icon_box--lg">A3</span>
            <div class="list_row__main">
              <div class="list_row__title">
                数字示波器
              </div>
              <div class="list_row__meta">
                A3-实验楼 302<span class="dot">·</span>10-12 14:00 – 16:00
              </div>
            </div>
            <span class="pill pill--ok">已归还</span>
          </div>
          <div class="kv_row">
            <div class="kv">
              <span class="kv__label">借用器材</span>
              <span class="kv__value">3 件</span>
            </div>
            <div class="kv">
              <span class="kv__label">使用时长</span>
              <span class="kv__value">2 小时</span>
            </div>
            <div class="kv">
              <span class="kv__label">器材状态</span>
              <span class="kv__value">完好</span>
            </div>
            <div class="kv">
              <span class="kv__label">经办老师</span>
              <span class="kv__value">李老师</span>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  Calendar,
  Document,
  Box,
  Tools,
  DataLine
} from '@element-plus/icons-vue'
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

const ROLE_TEXT = {
  ADMIN: '管理员',
  TEACHER: '教师',
  STUDENT: '学生',
  ASSISTANT: '教师助理'
}
const roleText = computed(
  () => store.roles.map((r) => ROLE_TEXT[r] || r).join(' · ') || '实验室用户'
)

const unread = ref(2)

const overview = ref([
  { label: '进行中预约', value: 1, unit: '项' },
  { label: '待审批', value: 2, unit: '项' },
  { label: '可用器材', value: 86, unit: '台' }
])

const entries = [
  { name: '器材借用', desc: '浏览并提交器材预约', icon: Tools, tone: 'brand', path: '/home' },
  { name: '场地预约', desc: '申请实验室使用时段', icon: Calendar, tone: 'ok', path: '/home' },
  { name: '我的申请', desc: '查看审批进度与单据', icon: Document, tone: 'warn', path: '/home' },
  { name: '物料代办', desc: '课前领用与课后回库', icon: Box, tone: 'muted', path: '/home' },
  { name: '使用记录', desc: '借出归还与拍照留痕', icon: DataLine, tone: 'brand', path: '/home' }
]

const loading = ref(true)
const todos = ref([])

// 首页数据源接入前先用本地占位，接口就绪后替换为真实请求
onMounted(() => {
  setTimeout(() => {
    todos.value = [
      {
        id: 1,
        tag: '审',
        title: '数字示波器 · 借用申请',
        meta: 'A3-实验楼 302 · 10-15 09:00 起',
        status: '待老师审批',
        tone: 'warn'
      },
      {
        id: 2,
        tag: '借',
        title: '万用表 · 已通过，待确认借出',
        meta: 'A2-实验楼 105 · 请在时段内到场确认',
        status: '待借出',
        tone: 'brand'
      },
      {
        id: 3,
        tag: '还',
        title: '函数信号发生器 · 待确认归还',
        meta: 'A3-实验楼 302 · 10-14 16:00 前归还',
        status: '待归还',
        tone: 'danger'
      }
    ]
    loading.value = false
  }, 420)
})

function go(path) {
  router.push(path)
}

function onNotify() {
  unread.value = 0
  ElMessage.success('暂无新的通知')
}
</script>

<style scoped>
.front_home {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 24px;
}

.hero__hello {
  font-size: 19px;
  font-weight: var(--fw-semibold);
  color: #fff;
}
.hero__role {
  margin-top: 3px;
  font-size: 12px;
  color: rgba(255, 255, 255, .84);
}

.front_home__body {
  padding: 0 14px;
}

/* 概览三格：等分 + 单元格间发丝线 */
.overview {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  padding: 16px 6px;
}
.overview__cell {
  text-align: center;
  padding: 0 4px;
}
.overview__cell + .overview__cell {
  border-left: .5px solid var(--hair);
}
.overview__num {
  font-size: 24px;
}
.overview__label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--g3);
}

.front_home__section {
  margin-top: 18px;
}

.card--pad0 {
  padding: 0;
}
.card--pad0 .card__body {
  padding: 4px 18px 8px;
}

.front_home__cardtitle {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.skeleton__row {
  height: 46px;
  margin: 8px 0;
}
</style>
