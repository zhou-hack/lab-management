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
      <button
        class="front_nav__back"
        aria-label="退出登录"
        title="退出登录"
        @click="onLogout"
      >
        <el-icon :size="17">
          <SwitchButton />
        </el-icon>
      </button>
    </header>
    <main class="front_layout__body">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const title = computed(() => route.meta.title || '实验室设备管理系统')
// 首页不显示返回；history.state.back 为空说明是直接打开/刷新进入，无可回退栈
const showBack = computed(() => route.path !== '/home' && !!router.options.history.state.back)

function goBack() {
  router.back()
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
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
  padding-bottom: max(env(safe-area-inset-bottom), 12px);
}
</style>
