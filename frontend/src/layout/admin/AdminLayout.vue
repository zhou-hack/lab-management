<template>
  <el-container class="admin_layout">
    <el-aside
      :width="collapsed ? '64px' : '220px'"
      class="admin_layout__aside"
    >
      <div class="admin_layout__logo">
        <span class="admin_layout__logo_mark">
          <el-icon><Tools /></el-icon>
        </span>
        <span v-show="!collapsed">实验室设备管理系统</span>
      </div>
      <el-menu
        class="admin_layout__menu"
        :default-active="route.path"
        :collapse="collapsed"
        router
      >
        <el-menu-item index="/admin/home">
          <el-icon><HomeFilled /></el-icon>
          <template #title>
            首页
          </template>
        </el-menu-item>
        <sidebar-item
          v-for="menu in menuTree"
          :key="menu.id"
          :item="menu"
        />
        <div
          v-if="!menuTree.length && !collapsed"
          class="admin_layout__menu_empty t-hint"
        >
          暂无可用菜单
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
            <el-breadcrumb-item>{{ route.meta.title || '首页' }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <el-dropdown @command="onUserCommand">
          <span class="admin_layout__user">
            <span class="admin_layout__avatar">
              {{ (store.user?.realName || '用').slice(0, 1) }}
            </span>
            <span class="admin_layout__username">
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
import { ArrowDown, Expand, Fold, HomeFilled, Tools } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// 窗口 <1024 默认收起侧边栏（规范第 10 节，桌面断点）
const collapsed = ref(window.innerWidth < 1024)

// 侧边菜单：角色菜单树过滤动作（ACTION）节点；目录无有效子页时隐藏；
// 固定首页由布局自身渲染，后端若下发 /admin/home 节点需去重（规范 7.4）
const menuTree = computed(() => {
  const filterTree = (nodes) =>
    (nodes || [])
      .filter((n) => n.menuType !== 'ACTION')
      .filter((n) => n.routePath !== '/admin/home')
      .map((n) => ({ ...n, children: filterTree(n.children) }))
      .filter((n) => n.menuType === 'PAGE' || n.children.length)
  return filterTree(store.menus)
})

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
  gap: 8px;
  height: 56px;
  padding: 0 12px;
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
  border-bottom: .5px solid var(--hair);
  white-space: nowrap;
  overflow: hidden;
}

.admin_layout__logo_mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: var(--brand-grad);
  color: #fff;
  font-size: 15px;
}

.admin_layout__menu {
  border-right: none;
  padding: 6px 0;
  /* 菜单独立滚动区：固定高度 + 内部滚动。展开长菜单（如系统管理九个子项）时
     不再撑高整个文档触发整页回流，消除展开瞬间的卡顿；
     scrollbar-gutter 预留槽位，滚动条出现/消失不挤压菜单布局 */
  height: calc(100vh - 56px);
  overflow-y: auto;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: var(--g4) transparent;
}

.admin_layout__menu::-webkit-scrollbar {
  width: 6px;
}

.admin_layout__menu::-webkit-scrollbar-thumb {
  background: var(--g4);
  border-radius: 3px;
}

.admin_layout__menu::-webkit-scrollbar-track {
  background: transparent;
}

.admin_layout__menu_empty {
  padding: 24px 16px;
  text-align: center;
}

/* 头部：56px、白底、0.5px 底线 */
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

.admin_layout__collapse_btn {
  padding: 6px;
  color: var(--g2);
}
.admin_layout__collapse_btn:hover {
  background: var(--brand-a08);
  color: var(--brand);
}

.admin_layout__user {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 10px 5px 5px;
  border-radius: var(--r-full);
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease);
}
.admin_layout__user:hover {
  background: var(--brand-a08);
}

.admin_layout__avatar {
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

.admin_layout__username {
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.admin_layout__main {
  background: var(--bg);
  padding: 20px;
}

@media (max-width: 768px) {
  .admin_layout__header { padding: 0 12px; }
  .admin_layout__main { padding: 14px; }
  .admin_layout__username { display: none; }
}
</style>
