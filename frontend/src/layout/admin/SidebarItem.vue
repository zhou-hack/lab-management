<template>
  <!-- 菜单树节点：目录(M)/菜单(C) 递归渲染；隐藏项(visible=1)与按钮(F)不渲染 -->
  <el-sub-menu
    v-if="hasChildren"
    :index="item.path || String(item.menuId)"
  >
    <template #title>
      <el-icon v-if="icon">
        <component :is="icon" />
      </el-icon>
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
    <el-icon v-if="icon">
      <component :is="icon" />
    </el-icon>
    <template #title>
      {{ item.menuName }}
    </template>
  </el-menu-item>
</template>

<script setup>
import { computed } from 'vue'
import {
  Collection,
  Files,
  Grid,
  HomeFilled,
  Location,
  Monitor,
  Notebook,
  OfficeBuilding,
  Setting,
  User,
  UserFilled
} from '@element-plus/icons-vue'

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

/* 图标：componentKey 白名单优先，未命中按菜单名关键字兜底（规范 7.4） */
const ICON_BY_KEY = {
  '/admin/home': HomeFilled,
  '/admin/space': OfficeBuilding,
  '/admin/space/labs': Location,
  '/admin/equipment': Monitor,
  '/admin/equipment/category': Collection,
  '/admin/equipment/model': Grid,
  '/admin/equipment/asset': Notebook
}

const KEYWORD_ICONS = [
  ['首页', HomeFilled],
  ['空间', OfficeBuilding],
  ['场地', Location],
  ['实验室', Location],
  ['设备', Monitor],
  ['分类', Collection],
  ['型号', Grid],
  ['台账', Notebook],
  ['用户', User],
  ['角色', UserFilled],
  ['菜单', Files],
  ['部门', OfficeBuilding],
  ['设置', Setting]
]

const icon = computed(() => {
  const key = props.item.componentKey || ''
  if (ICON_BY_KEY[key]) {
    return ICON_BY_KEY[key]
  }
  const name = props.item.menuName || ''
  const hit = KEYWORD_ICONS.find(([keyword]) => name.includes(keyword))
  return hit ? hit[1] : null
})
</script>
