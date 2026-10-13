<template>
  <div class="front_home">
    <div class="card">
      <h2 class="t-title">
        欢迎使用
      </h2>
      <p class="t-sub">
        实验室设备管理系统 · 前台视图（学生 / 老师）
      </p>
      <div class="front_home__debug">
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
    </div>
  </div>
</template>

<script setup>
import { useUserStore } from '@/store/user'

const store = useUserStore()
</script>

<style scoped>
.front_home {
  padding: 14px;
}

.front_home__debug {
  margin-top: 14px;
  padding-top: 12px;
  border-top: .5px solid var(--hair);
}

.front_home__debug p {
  margin: 4px 0;
}
</style>
