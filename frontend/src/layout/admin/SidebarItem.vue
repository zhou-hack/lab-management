<template>
  <!-- 菜单树节点：目录(DIRECTORY)/页面(PAGE) 递归渲染；动作(ACTION)不渲染 -->
  <el-sub-menu
    v-if="hasChildren"
    :index="item.routePath || String(item.id)"
  >
    <template #title>
      <el-icon><component :is="icon" /></el-icon>
      <span>{{ item.menuName }}</span>
    </template>
    <sidebar-item
      v-for="child in visibleChildren"
      :key="child.id"
      :item="child"
    />
  </el-sub-menu>
  <el-menu-item
    v-else
    :index="item.routePath"
  >
    <el-icon><component :is="icon" /></el-icon>
    <template #title>
      {{ item.menuName }}
    </template>
  </el-menu-item>
</template>

<script setup>
import { computed } from 'vue'
import {
  Avatar,
  Bell,
  Box,
  Calendar,
  DataLine,
  Document,
  Histogram,
  Key,
  Menu as MenuIcon,
  OfficeBuilding,
  Setting,
  Tools,
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
  (props.item.children || []).filter((c) => c.menuType !== 'ACTION')
)

const hasChildren = computed(() => visibleChildren.value.length > 0)

// 按 componentKey / menuName 关键字映射图标；未命中用通用菜单图标
const ICON_MAP = {
  SystemUserRoster: UserFilled,
  SystemDepartment: OfficeBuilding,
  SystemRole: Avatar,
  SystemScope: Key,
  SystemLog: Histogram,
  SystemPolicy: Setting,
  SystemGrantAudit: Document,
  GrantManage: Document
}

const KEYWORD_ICON = [
  [/用户|人员|名单/, User],
  [/部门|组织/, OfficeBuilding],
  [/角色|权限|授权/, Key],
  [/日志|统计|看板/, DataLine],
  [/器材|设备|工具/, Tools],
  [/场地|实验室|教室/, Calendar],
  [/物料|库存|耗材/, Box],
  [/通知|消息/, Bell],
  [/设置|配置|策略/, Setting]
]

const icon = computed(() => {
  if (props.item.componentKey && ICON_MAP[props.item.componentKey]) {
    return ICON_MAP[props.item.componentKey]
  }
  const name = props.item.menuName || ''
  const hit = KEYWORD_ICON.find(([re]) => re.test(name))
  return hit ? hit[1] : MenuIcon
})
</script>
