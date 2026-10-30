import { hasPermission } from '@/utils/permission'
import { ITEM_WORKBENCH_TABS } from './workbench.config'

export function getVisibleWorkbenchTabs(permissions, tabs = ITEM_WORKBENCH_TABS) {
  return tabs.filter((tab) => hasPermission(tab.permissions, permissions))
}

export function resolveWorkbenchTab(requestedTab, visibleTabs) {
  const firstKey = visibleTabs[0]?.key || ''
  return visibleTabs.some((tab) => tab.key === requestedTab) ? requestedTab : firstKey
}
