<template>
  <div class="pickup-page">
    <header class="page-header">
      <h1 class="page-title">
        物料代领
      </h1>
      <p class="page-subtitle">
        学生代理代领：只能引用老师已审核通过的申领单，按源单剩余可代领量领取；
        领取操作由后端按 assistant_grant 有效授权动态放行，未授权将被拒
      </p>
    </header>
    <RequestPanel ref="requestRef" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import RequestPanel from './panels/RequestPanel.vue'

const requestRef = ref(null)
</script>

<style scoped>
.pickup-page {
  max-width: 1440px;
  margin: 0 auto;
  padding: 20px 24px 32px;
}

.page-header {
  margin-bottom: 12px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
