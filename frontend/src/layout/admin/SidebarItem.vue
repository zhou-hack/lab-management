<template>
  <!-- 菜单树节点：目录(M)/菜单(C) 递归渲染；隐藏项(visible=1)与按钮(F)不渲染 -->
  <el-sub-menu
    v-if="hasChildren"
    :index="item.path || String(item.menuId)"
  >
    <template #title>
      <span>{{ item.menuName }}</span>
    </template>
    <sidebar-item
      v-for="child in visibleChildren"
      :key="child.menuId"
      :item="child"
    />
  </el-sub-menu>
  <el-menu-item
    v-else
    :index="item.path"
  >
    <span>{{ item.menuName }}</span>
  </el-menu-item>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

const visibleChildren = computed(() =>
  (props.item.children || []).filter((c) => c.menuType !== 'F' && c.visible !== '1')
)

const hasChildren = computed(() => visibleChildren.value.length > 0)
</script>
