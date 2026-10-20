<template>
  <div class="admin_home">
    <div class="card">
      <h2 class="t-title">
        管理端首页
      </h2>
      <p class="t-sub">
        实验室设备管理系统 · 后台视图（管理员）
      </p>
      <div class="admin_home__debug">
        <p class="t-body">
          当前用户：{{ store.userInfo?.realName || '（后端接入后显示）' }}
        </p>
        <p class="t-body">
          角色：{{ store.roles.join('、') || '—' }}
        </p>
        <p class="t-body">
          权限标识数：{{ store.perms.length }}
        </p>
      </div>
      <!-- 权限指令用法示例（后端接入后按 perms 控制显隐） -->
      <el-button
        v-permission="'equipment:edit'"
        type="primary"
        style="margin-top: 12px"
      >
        仅 equipment:edit 可见（示例）
      </el-button>
      <!-- 物料工作台入口：骨架阶段 /item 为公共静态路由 -->
      <el-button
        type="primary"
        style="margin-top: 12px"
        @click="goItem"
      >
        进入物料管理
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const store = useUserStore()
const router = useRouter()

function goItem() {
  router.push('/item')
}
</script>

<style scoped>
.admin_home__debug {
  margin-top: 14px;
  padding-top: 12px;
  border-top: .5px solid var(--hair);
}

.admin_home__debug p {
  margin: 4px 0;
}
</style>
