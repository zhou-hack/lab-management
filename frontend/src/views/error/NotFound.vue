<template>
  <div class="not_found">
    <div class="not_found__code">
      404
    </div>
    <p class="not_found__title">
      页面不存在或没有访问权限
    </p>
    <p class="not_found__desc t-sub">
      请检查地址是否正确，或返回首页重新选择功能入口
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
  gap: 10px;
  padding: 72px 20px;
  text-align: center;
}

.not_found__code {
  font-family: var(--font-num);
  font-size: 64px;
  font-weight: var(--fw-semibold);
  line-height: 1;
  letter-spacing: 2px;
  background: var(--brand-grad);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-variant-numeric: tabular-nums;
}

.not_found__title {
  margin: 6px 0 0;
  font-size: 15px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.not_found__desc {
  margin: 0 0 8px;
}
</style>
