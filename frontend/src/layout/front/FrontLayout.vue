<template>
  <div class="front_layout">
    <!-- 桌面端顶部通栏：品牌 + 水平菜单 + 用户下拉（与管理端一致的导航模式） -->
    <header class="front_header">
      <div class="front_header__inner">
        <div class="front_header__brand">
          <span class="front_header__mark">
            <el-icon><Tools /></el-icon>
          </span>
          <span class="front_header__title">实验室设备管理系统</span>
        </div>

        <nav class="front_header__menu">
          <button
            v-for="tab in tabs"
            :key="tab.path"
            type="button"
            class="front_header__item"
            :class="{ 'front_header__item--on': isActive(tab.path) }"
            @click="goTab(tab.path)"
          >
            {{ tab.label }}
          </button>
        </nav>

        <el-dropdown @command="onUserCommand">
          <span class="front_header__user">
            <span class="front_header__avatar">
              {{ (store.user?.realName || '用').slice(0, 1) }}
            </span>
            <span class="front_header__username">
              {{ store.user?.realName || '用户' }}
            </span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="setPassword">
                修改密码
              </el-dropdown-item>
              <el-dropdown-item command="logout">
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
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
import { ArrowDown, Tools } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

/** 水平菜单：首页固定在前；其后为菜单树中归属前台的页面（routePath 不以 /admin 开头） */
const tabs = computed(() => {
  const pages = []
  const walk = (nodes) => {
    for (const node of nodes || []) {
      if (node.menuType === 'PAGE' && node.routePath && !node.routePath.startsWith('/admin')) {
        pages.push({ path: node.routePath, label: node.menuName })
      }
      if (node.children && node.children.length) walk(node.children)
    }
  }
  walk(store.menus)
  return [{ path: '/home', label: '首页' }, ...pages]
})

function isActive(path) {
  return route.path === path || route.path.startsWith(`${path}/`)
}

function goTab(path) {
  if (route.path !== path) router.push(path)
}

async function onUserCommand(command) {
  if (command === 'setPassword') {
    router.push('/set_password')
    return
  }
  if (command !== 'logout') return
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

/* 顶部通栏：白底、0.5px 底线、56px 高（与管理端头部一致） */
.front_header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: var(--surface);
  border-bottom: .5px solid var(--hair);
}

.front_header__inner {
  display: flex;
  align-items: center;
  gap: 28px;
  max-width: 1200px;
  height: 56px;
  margin: 0 auto;
  padding: 0 20px;
}

.front_header__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.front_header__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: var(--brand-grad);
  color: #fff;
  font-size: 15px;
}

.front_header__title {
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
  white-space: nowrap;
}

.front_header__menu {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
}

.front_header__item {
  padding: 6px 14px;
  border: none;
  border-radius: var(--r-full);
  background: transparent;
  font-size: 14px;
  color: var(--g2);
  white-space: nowrap;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease);
}

.front_header__item:hover {
  background: var(--brand-a04);
  color: var(--brand);
}

.front_header__item--on {
  background: var(--brand-a08);
  color: var(--brand);
  font-weight: var(--fw-medium);
}

.front_header__user {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 10px 5px 5px;
  border-radius: var(--r-full);
  cursor: pointer;
  flex-shrink: 0;
  transition: background var(--dur-fast) var(--ease);
}

.front_header__user:hover {
  background: var(--brand-a08);
}

.front_header__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--brand-grad);
  color: #fff;
  font-size: 13px;
  font-weight: var(--fw-medium);
}

.front_header__username {
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.front_layout__body {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

@media (max-width: 768px) {
  .front_header__inner { gap: 12px; padding: 0 12px; }
  .front_header__title,
  .front_header__username { display: none; }
  .front_layout__body { padding: 14px; }
}
</style>
