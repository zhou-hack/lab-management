<template>
  <div class="page asset_page">
    <div class="page_header">
      <div>
        <h2 class="t-title">
          设备台账
        </h2>
        <p class="t-sub">
          设备台账按型号分组 · 支持查询、新增、编辑、删除、迁移与一键导入导出
        </p>
      </div>
    </div>
    <AssetSection :active="true" />
  </div>
</template>

<script setup>
import AssetSection from '@/views/admin/equipment/AssetManage.vue'

/**
 * 设备台账独立页（2026-10-22）：设备管理拆分为「设备库存」「设备台账」两个独立页面之一，
 * 路由 /admin/equipment/asset。区块组件挂载即加载数据。
 */
</script>
