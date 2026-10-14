<template>
  <div class="not_found">
    <div class="not_found__icon">
      ∅
    </div>
    <p class="t-sub">
      页面不存在或没有访问权限
    </p>
    <el-button
      type="primary"
      round
      @click="goHome"
    >
      返回首页
    </el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { getToken } from '@/utils/token'

const router = useRouter()

function goHome() {
  router.push(getToken() ? '/' : '/login')
}
</script>

<style scoped>
/* 空态：60px 上下留白，g4 图标 + g3 文案（规范 7.8） */
.not_found {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 20px;
}

.not_found__icon {
  font-size: 48px;
  color: var(--g4);
}
</style>
