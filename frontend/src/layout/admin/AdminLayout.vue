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
        :default-openeds="openedMenus"
        :collapse="collapsed"
        router
      >
        <!-- 学生（无物料权限）仅见首页入口 -->
        <el-menu-item
          v-if="!showItemMenu"
          index="/admin/home"
        >
          <el-icon><HomeFilled /></el-icon>
          <template #title>
            首页
          </template>
        </el-menu-item>
        <!-- 骨架环境：本分支仅物料模块，侧边栏只放物料管理（管理员/老师可见）：
             管理员按业务分组直达面板（2026-10-23 需求：四个分组替换原「物料工作台」入口，
             工作台内部左侧菜单同步移除），老师进教师端物料预约 -->
        <el-sub-menu
          v-if="showItemMenu"
          index="item"
        >
          <template #title>
            <el-icon><Goods /></el-icon>
            <span>物料管理</span>
          </template>
          <template v-if="store.roles.includes('ADMIN')">
            <el-sub-menu index="item_basic">
              <template #title>
                <el-icon><Setting /></el-icon>
                <span>基础数据</span>
              </template>
              <el-menu-item index="/admin/item/workspace/basic_sku">
                物料档案
              </el-menu-item>
              <el-menu-item index="/admin/item/workspace/basic_location">
                库位管理
              </el-menu-item>
            </el-sub-menu>
            <el-sub-menu index="item_query">
              <template #title>
                <el-icon><DataAnalysis /></el-icon>
                <span>库存查询</span>
              </template>
              <el-menu-item index="/admin/item/workspace/balance">
                物料存量
              </el-menu-item>
              <el-menu-item index="/admin/item/workspace/movement">
                库存流水
              </el-menu-item>
            </el-sub-menu>
            <el-sub-menu index="item_business">
              <template #title>
                <el-icon><Tickets /></el-icon>
                <span>库存业务</span>
              </template>
              <el-menu-item index="/admin/item/workspace/request">
                物料预约
              </el-menu-item>
            </el-sub-menu>
            <el-sub-menu index="item_settle">
              <template #title>
                <el-icon><List /></el-icon>
                <span>核销管理</span>
              </template>
              <el-menu-item index="/admin/item/workspace/settlement">
                物料核销
              </el-menu-item>
            </el-sub-menu>
          </template>
          <el-menu-item
            v-if="store.roles.includes('TEACHER')"
            index="/admin/item/teacher"
          >
            物料预约（教师端）
          </el-menu-item>
        </el-sub-menu>
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
              {{ (store.userInfo?.realName || '用').slice(0, 1) }}
            </span>
            <span class="admin_layout__username">
              {{ store.userInfo?.realName || '用户' }}
            </span>
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
import { ArrowDown, DataAnalysis, Expand, Fold, Goods, HomeFilled, List, Setting, Tickets, Tools } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// 窗口 <1024 默认收起侧边栏（规范第 10 节，桌面断点）
const collapsed = ref(window.innerWidth < 1024)

// 物料管理入口：仅管理员/老师可见；学生无物料管理权限（产品定稿）
const showItemMenu = computed(() => store.roles.includes('ADMIN') || store.roles.includes('TEACHER'))

// 面板路由参数 → 侧边栏分组 index：进入页面时自动展开当前面板所在分组
const PANEL_GROUP = {
  basic_sku: 'item_basic',
  basic_location: 'item_basic',
  balance: 'item_query',
  movement: 'item_query',
  request: 'item_business',
  settlement: 'item_settle'
}

/** 默认展开：物料管理 + 当前面板所属分组（非面板页仅展开物料管理） */
const openedMenus = computed(() => {
  const group = PANEL_GROUP[route.params.panel]
  return group ? ['item', group] : ['item']
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
