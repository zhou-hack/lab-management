<template>
  <el-container class="admin_layout">
    <el-aside
      :width="collapsed ? '64px' : '220px'"
      class="admin_layout__aside"
    >
      <div class="admin_layout__logo">
        <span v-show="!collapsed">实验室设备管理</span>
      </div>
      <el-menu
        class="admin_layout__menu"
        :default-active="route.path"
        :default-openeds="openedMenus"
        :collapse="collapsed"
        router
      >
        <sidebar-item
          v-for="menu in adminMenus"
          :key="menu.menuId"
          :item="menu"
        />
        <div
          v-if="!adminMenus.length && !collapsed"
          class="admin_layout__menu_empty t-hint"
        >
          暂无菜单（后端接入后按角色下发）
        </div>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin_layout__header">
        <div class="admin_layout__header_left">
          <el-button
            text
            class="admin_layout__collapse_btn"
            @click="collapsed = !collapsed"
          >
            <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="(crumb, idx) in breadcrumbs"
              :key="idx"
            >
              {{ crumb }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="admin_layout__header_right">
          <IdentitySwitch v-if="store.identities.length > 1" />
          <el-dropdown @command="onUserCommand">
            <span class="admin_layout__user">
              {{ store.userInfo?.realName || '用户' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="admin_layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, Expand, Fold } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import SidebarItem from './SidebarItem.vue'
import IdentitySwitch from '@/components/IdentitySwitch.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// 窄屏（<1024）默认收起侧边栏，移动自适应（规范第 10 节）
const collapsed = ref(window.innerWidth < 1024)

// 后台菜单：按菜单树原样渲染（保留一级目录层级，如「场地中心」折叠大类）
const adminMenus = computed(() => {
  const filterTree = (nodes) =>
    (nodes || [])
      .filter((n) => n.menuType !== 'F' && n.visible !== '1')
      .map((n) => ({ ...n, children: filterTree(n.children) }))
      .filter((n) => n.menuType === 'C' || n.children.length)
  return filterTree(store.menus || [])
})

/** 当前激活页面所在的父级目录（刷新后父级保持展开，如「场地中心」） */
const openedMenus = computed(() => {
  const opened = []
  const walk = (nodes) => {
    for (const n of nodes || []) {
      if ((n.children || []).some((c) => c.path === route.path)) {
        opened.push(n.path || String(n.menuId))
        return true
      }
      if (n.children?.length && walk(n.children)) return true
    }
    return false
  }
  walk(store.menus || [])
  return opened
})

/** 面包屑：按菜单树父链展示（如「场地中心 / 场地管理」），未命中回退路由标题 */
const breadcrumbs = computed(() => {
  const chain = []
  const walk = (nodes) => {
    for (const n of nodes || []) {
      if (n.menuType === 'C' && n.path === route.path) {
        chain.push(n)
        return true
      }
      if (n.children?.length && walk(n.children)) {
        chain.unshift(n)
        return true
      }
    }
    return false
  }
  if (walk(store.menus || [])) {
    return chain.map((n) => n.menuName)
  }
  return [route.meta.title || '首页']
})

async function onUserCommand(command) {
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
.admin_layout {
  min-height: 100vh;
}

.admin_layout__aside {
  background: var(--surface);
  border-right: .5px solid var(--hair);
  transition: width var(--dur) var(--ease);
}

.admin_layout__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56px;
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
  border-bottom: .5px solid var(--hair);
  white-space: nowrap;
  overflow: hidden;
}

.admin_layout__menu {
  border-right: none;
}

.admin_layout__menu_empty {
  padding: 24px 16px;
  text-align: center;
}

.admin_layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 20px;
  background: var(--surface);
  border-bottom: .5px solid var(--hair);
}

.admin_layout__header_left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin_layout__header_right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin_layout__collapse_btn {
  padding: 6px;
}

.admin_layout__user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.admin_layout__main {
  background: var(--bg);
  padding: 20px;
}
</style>
