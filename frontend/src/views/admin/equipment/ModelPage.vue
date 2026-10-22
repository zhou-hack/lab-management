<template>
  <div class="page model_page">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          设备库存
        </h2>
        <p class="t-sub">
          型号按分类分组维护 · 分类目录在「管理分类」抽屉内管理
        </p>
      </div>
    </div>
    <ModelSection :active="true" />
  </div>
</template>

<script setup>
import ModelSection from '@/views/admin/equipment/ModelManage.vue'

/**
 * 设备库存独立页（2026-10-22）：设备管理拆分为「设备库存」「设备台账」两个独立页面之一，
 * 路由 /admin/equipment/model。区块组件挂载即加载数据。
 */
</script>
