<template>
  <el-tabs
    v-model="active"
    class="basic-tabs"
  >
    <el-tab-pane
      label="物料分类"
      name="category"
      lazy
    >
      <CategorySection ref="categoryRef" />
    </el-tab-pane>
    <el-tab-pane
      label="物料档案"
      name="sku"
      lazy
    >
      <SkuSection ref="skuRef" />
    </el-tab-pane>
    <el-tab-pane
      label="库位管理"
      name="location"
      lazy
    >
      <LocationSection ref="locationRef" />
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { ref } from 'vue'
import CategorySection from './basic/CategorySection.vue'
import SkuSection from './basic/SkuSection.vue'
import LocationSection from './basic/LocationSection.vue'

/**
 * 基础数据管理（分类 / 物料档案 / 库位）
 * 三段各自独立分页与 CRUD 弹窗，内部 tab 懒加载
 */
const active = ref('category')
const categoryRef = ref(null)
const skuRef = ref(null)
const locationRef = ref(null)

const sectionRefs = {
  category: categoryRef,
  sku: skuRef,
  location: locationRef
}

/** 工作台切回该页签时刷新当前活动段 */
function refresh() {
  const section = sectionRefs[active.value]
  if (section && section.value) {
    section.value.refresh()
  }
}

defineExpose({ refresh })
</script>

<style scoped>
.basic-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}
</style>
