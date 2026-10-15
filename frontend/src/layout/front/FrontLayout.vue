<template>
  <div class="front_layout">
    <header class="front_nav">
      <button
        v-if="showBack"
        class="front_nav__back"
        aria-label="返回"
        @click="goBack"
      >
        ‹
      </button>
      <span
        v-else
        class="front_nav__back front_nav__back--placeholder"
      />
      <b class="front_nav__title">{{ title }}</b>
      <span class="front_nav__back front_nav__back--placeholder" />
    </header>
    <main class="front_layout__body">
      <router-view />
    </main>

    <!-- 底部标签栏：首页 + 菜单下发的页面 + 我的 -->
    <nav class="front_tabbar">
      <button
        v-for="tab in tabs"
        :key="tab.path"
        type="button"
        class="front_tab"
        :class="{ 'front_tab--on': isActive(tab.path) }"
        @click="goTab(tab.path)"
      >
        <el-icon :size="19">
          <component :is="tab.icon" />
        </el-icon>
        <span class="front_tab__label">{{ tab.label }}</span>
      </button>
      <el-popover
        v-model:visible="mineVisible"
        placement="top-end"
        :width="200"
        trigger="click"
      >
        <template #reference>
          <button
            type="button"
            class="front_tab"
            @click="mineVisible = !mineVisible"
          >
            <el-icon :size="19">
              <User />
            </el-icon>
            <span class="front_tab__label">我的</span>
          </button>
        </template>
        <div class="mine">
          <div class="mine__name">
            {{ store.user?.realName || '用户' }}
          </div>
          <div class="mine__role">
            {{ roleText }}
          </div>
          <el-button
            class="mine__logout"
            size="small"
            @click="quit"
          >
            退出登录
          </el-button>
        </div>
      </el-popover>
    </nav>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar, Document, HomeFilled, Menu, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const title = computed(() => route.meta.title || '实验室设备管理系统')
// 首页不显示返回；history.state.back 为空说明是直接打开/刷新进入，无可回退栈
const showBack = computed(() => route.path !== '/home' && !!router.options.history.state.back)

/** 已知路径的图标映射；其余前端页面用默认图标 */
const TAB_ICONS = {
  '/booking/labs': Calendar,
  '/grant': Document
}

/** 首页固定在前；其后为菜单树中归属前台布局的页面（routePath 不以 /admin 开头） */
const tabs = computed(() => {
  const pages = []
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (node.menuType === 'PAGE' && node.routePath && !node.routePath.startsWith('/admin')) {
        pages.push({
          path: node.routePath,
          label: node.menuName,
          icon: TAB_ICONS[node.routePath] || Menu
        })
      }
      if (node.children && node.children.length) walk(node.children)
    }
  }
  walk(store.menus)
  return [{ path: '/home', label: '首页', icon: HomeFilled }, ...pages]
})

const ROLE_TEXT = { ADMIN: '管理员', TEACHER: '教师', STUDENT: '学生', ASSISTANT: '教师助理' }
const roleText = computed(() => store.roles.map((r) => ROLE_TEXT[r] || r).join(' · '))

const mineVisible = ref(false)

function isActive(path) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

function goTab(path) {
  if (route.path !== path) router.push(path)
}

function goBack() {
  router.back()
}

/** 退出登录并回登录页 */
async function quit() {
  mineVisible.value = false
  await store.logout()
  router.push('/login')
}
</script>

<style scoped>
.front_layout {
  min-height: 100vh;
  background: var(--bg);
}

/* 白底导航栏：48px 高、16px·600 居中标题、0.5px 底线（规范 7.4） */
.front_nav {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 12px;
  background: var(--surface);
  border-bottom: .5px solid var(--hair);
}

.front_nav__title {
  font-size: 16px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.front_nav__back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: var(--g2);
  cursor: pointer;
  border-radius: var(--r-full);
  transition: background var(--dur-fast) var(--ease);
}

.front_nav__back:not(.front_nav__back--placeholder):hover {
  background: var(--brand-a04);
}

.front_nav__back:not(.front_nav__back--placeholder):active {
  background: var(--brand-a08);
  transform: scale(.97);
}

.front_nav__back--placeholder {
  cursor: default;
}

.front_layout__body {
  /* 为底部标签栏留出空间 */
  padding-bottom: calc(58px + max(env(safe-area-inset-bottom), 12px));
}

/* 底部标签栏：白底 + 0.5px 顶线，选中项品牌蓝 */
.front_tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 20;
  display: flex;
  height: calc(58px + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  background: var(--surface);
  border-top: .5px solid var(--hair);
}

.front_tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border: none;
  background: transparent;
  color: var(--g3);
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease);
}

.front_tab:active {
  transform: scale(.96);
}

.front_tab--on {
  color: var(--brand);
  font-weight: var(--fw-medium);
}

.front_tab__label {
  font-size: 11px;
}

.mine {
  text-align: center;
}

.mine__name {
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.mine__role {
  margin: 3px 0 12px;
  font-size: 12px;
  color: var(--g3);
}

.mine__logout {
  width: 100%;
  border-radius: var(--r-full);
}
</style>
