<template>
  <el-container class="admin_layout">
    <el-aside
      :width="collapsed ? '64px' : '220px'"
      class="admin_layout__aside"
    >
      <div class="admin_layout__logo">
        <span class="admin_layout__logo_icon" />
        <span
          v-show="!collapsed"
          class="admin_layout__logo_name"
        >
          实验室设备管理
        </span>
      </div>
      <el-menu
        class="admin_layout__menu"
        :default-active="route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        router
      >
        <!-- 固定「首页」在最上方（规范 7.4） -->
        <el-menu-item index="/admin/home">
          <el-icon><HomeFilled /></el-icon>
          <template #title>
            首页
          </template>
        </el-menu-item>
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
            <el-breadcrumb-item :to="{ path: '/admin/home' }">
              首页
            </el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.path !== '/admin/home'">
              {{ route.meta.title || '' }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <el-dropdown @command="onUserCommand">
          <span class="admin_layout__user">
            <span class="admin_layout__avatar">{{ avatarChar }}</span>
            <span
              v-if="!narrow"
              class="admin_layout__user_name"
            >
              {{ store.userInfo?.realName || '用户' }}
            </span>
            <el-icon class="admin_layout__user_arrow">
              <ArrowDown />
            </el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">
                修改密码
              </el-dropdown-item>
              <el-dropdown-item
                command="logout"
                divided
              >
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Expand, Fold, HomeFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

// 窄屏（<1024）默认收起侧边栏（规范 7.4 / 第 10 节）
const collapsed = ref(window.innerWidth < 1024)
// <768 顶部通栏隐藏用户名（规范第 10 节）
const narrow = ref(window.innerWidth < 768)

function syncNarrow() {
  narrow.value = window.innerWidth < 768
}
onMounted(() => window.addEventListener('resize', syncNarrow))
onBeforeUnmount(() => window.removeEventListener('resize', syncNarrow))

// 头像 = 姓名首字（规范 7.4：28px 渐变圆形头像）
const avatarChar = computed(() => {
  const name = store.userInfo?.realName || '用'
  return name.charAt(0)
})

// 后台菜单：取菜单树中 path 以 /admin 开头的子树（目录 M / 页面 C）
const adminMenus = computed(() => {
  // 固定首页由布局自身渲染，后端下发的 /admin/home 节点去重（规范 7.4）
  const filterTree = (nodes) =>
    (nodes || [])
      .filter((n) => n.menuType !== 'F' && n.visible !== '1')
      .filter((n) => (n.path || n.componentKey) !== '/admin/home')
      .map((n) => ({ ...n, children: filterTree(n.children) }))
      .filter((n) => n.menuType === 'C' || n.children.length)
  const adminRoot = (store.menus || []).find((m) => (m.path || '').startsWith('/admin'))
  return adminRoot ? filterTree(adminRoot.children) : filterTree(store.menus)
})

async function onUserCommand(command) {
  if (command === 'password') {
    // 接口未接入：如实提示，不构造假流程（规范 7.8）
    ElMessage.info('修改密码功能暂未开放')
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

/* logo 区：56px，28px 渐变方块图标 + 系统名（折叠时仅图标） */
.admin_layout__logo {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 56px;
  padding: 0 18px;
  border-bottom: .5px solid var(--hair);
  overflow: hidden;
  white-space: nowrap;
}

.admin_layout__logo_icon {
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: var(--brand-grad);
  flex-shrink: 0;
}

.admin_layout__logo_name {
  font-size: 15px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.admin_layout__menu {
  border-right: none;
}

.admin_layout__menu_empty {
  padding: 24px 16px;
  text-align: center;
}

/* 顶部通栏：56px、白底、0.5px 底线 */
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
}

/* 用户下拉：28px 渐变圆形头像（姓名首字）+ 姓名 */
.admin_layout__user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
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
  flex-shrink: 0;
}

.admin_layout__user_name {
  font-size: 14px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.admin_layout__user_arrow {
  color: var(--g3);
  font-size: 12px;
}

/* 内容区：极浅灰底，padding 20px（规范 7.4） */
.admin_layout__main {
  background: var(--bg);
  padding: 20px;
}
</style>
