<template>
  <div class="placeholder_view">
    <div class="card placeholder_view__card">
      <div class="placeholder_view__icon">
        <el-icon><Tools /></el-icon>
      </div>
      <h3 class="placeholder_view__title">
        「{{ title }}」建设中
      </h3>
      <p class="placeholder_view__desc t-sub">
        该模块正在开发中，页面骨架与权限链路已就绪，功能将按实施顺序逐页交付。
      </p>
      <div class="placeholder_view__tags">
        <span class="pill pill--brand">路由已注册</span>
        <span class="pill pill--ok">权限已校验</span>
        <span class="pill pill--muted">待开发</span>
      </div>
      <el-button
        class="placeholder_view__btn"
        @click="router.back()"
      >
        返回上一页
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tools } from '@element-plus/icons-vue'

// 占位页：系统管理页面逐页落地前，保证菜单-路由-组件链路可导航
const route = useRoute()
const router = useRouter()
const title = computed(() => route.meta.title || '页面')
</script>

<style scoped>
.placeholder_view {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
}

.placeholder_view__card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  max-width: 460px;
  padding: 46px 32px;
  text-align: center;
}

.placeholder_view__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  border-radius: 18px;
  background: var(--brand-grad-soft);
  color: var(--brand);
  font-size: 28px;
}

.placeholder_view__title {
  margin: 0;
  font-size: 17px;
  font-weight: var(--fw-semibold);
  color: var(--g1);
}

.placeholder_view__desc {
  margin: 0;
  max-width: 340px;
  line-height: 1.7;
}

.placeholder_view__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 2px;
}

.placeholder_view__btn {
  margin-top: 8px;
}
</style>
