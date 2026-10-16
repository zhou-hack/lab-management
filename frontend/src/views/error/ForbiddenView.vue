<template>
  <div class="forbidden">
    <div class="forbidden__code">
      403
    </div>
    <p class="forbidden__title">
      没有访问权限
    </p>
    <p class="forbidden__desc t-sub">
      当前账号无权访问该页面，请联系管理员开通权限，或返回首页选择可用功能
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
/* 与 NotFound 同规格空态：72px 上下留白，品牌渐变大码 + g1 标题 */
.forbidden {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 72px 20px;
  text-align: center;
}

.forbidden__code {
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

.forbidden__title {
  margin: 6px 0 0;
  font-size: 15px;
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.forbidden__desc {
  margin: 0 0 8px;
}
</style>
