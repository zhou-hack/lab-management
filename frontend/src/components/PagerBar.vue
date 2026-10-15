<template>
  <el-pagination
    class="pager"
    layout="total, prev, pager, next, sizes"
    :page-sizes="[10, 20, 50, 100]"
    :total="list.total"
    :current-page="list.pageNum"
    :page-size="list.pageSize"
    @current-change="list.onPageChange"
    @size-change="list.onSizeChange"
  />
</template>

<script setup>
/**
 * 分页条（配合 utils/usePagedList 使用）
 * 用法：<PagerBar :list="pager" />，页大小变化自动回第 1 页
 */
defineProps({
  list: { type: Object, required: true }
})
</script>
