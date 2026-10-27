<template>
  <!-- 菜单树节点：目录(M)/菜单(C) 递归渲染；隐藏项(visible=1)与按钮(F)不渲染 -->
  <!-- 子项 = 虚拟子菜单（前端结构，不落库）+ 数据库子菜单（如设备使用审批挂设备管理下） -->
  <el-sub-menu
    v-if="hasChildren || virtualChildren.length"
    :index="item.path || String(item.menuId)"
  >
    <template #title>
      <el-icon v-if="icon">
        <component :is="icon" />
      </el-icon>
      <span>{{ item.menuName }}</span>
    </template>
    <el-menu-item
      v-for="child in virtualChildren"
      :key="child.route"
      :index="child.route"
    >
      <template #title>
        {{ child.label }}
      </template>
    </el-menu-item>
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
  Files,
  Grid,
  HomeFilled,
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

/**
 * 虚拟子菜单：单页聚合菜单折叠收纳区块子项（2026-10-22）。
 * 设备管理拆分为「设备库存」「设备台账」两个独立页面，侧栏折叠收纳并跳转对应页面；
 * 仅前端结构，不新增数据库菜单。路由与 dynamic.js EQUIPMENT_SECTION_ROUTES 对应。
 * 「设备库存」2026-10-26 起暂时移除入口（保留代码，恢复时取消注释该项）。
 */
const VIRTUAL_SUBMENUS = {
  '/admin/equipment': [
    // { label: '设备库存', route: '/admin/equipment/model' },
    { label: '设备台账', route: '/admin/equipment/asset' }
  ]
}

const virtualChildren = computed(() => VIRTUAL_SUBMENUS[props.item.path] || [])

/* 图标：componentKey 白名单优先，未命中按菜单名关键字兜底（规范 7.4） */
const ICON_BY_KEY = {
  '/home': HomeFilled,
  '/admin/equipment': Monitor
}

const KEYWORD_ICONS = [
  ['首页', HomeFilled],
  ['设备', Monitor],
  ['库存', Grid],
  ['设备台账', Notebook],
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
