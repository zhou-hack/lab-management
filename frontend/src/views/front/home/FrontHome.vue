<template>
  <div class="front_home">
    <div class="card">
      <h2 class="t-title">
        欢迎使用
      </h2>
      <p class="t-sub">
        实验室设备管理系统 · {{ roleLabel }}
      </p>
      <p
        v-if="!hasEntry"
        class="t-hint front_home__hint"
      >
        当前暂无可用功能入口；物料代领需老师授权后才会显示。
      </p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store/user'

const store = useUserStore()

const roleLabel = computed(() => (store.roles.includes('STUDENT') ? '学生端' : '前台视图'))
// 骨架环境：本分支仅物料模块；学生无物料管理权限（产品定稿），首页仅作落地页
const hasEntry = computed(() => store.roles.includes('ADMIN') || store.roles.includes('TEACHER'))
</script>

<style scoped>
.front_home {
  padding: 14px;
}

.front_home__hint {
  margin-top: 14px;
  padding-top: 12px;
  border-top: .5px solid var(--hair);
}
</style>
