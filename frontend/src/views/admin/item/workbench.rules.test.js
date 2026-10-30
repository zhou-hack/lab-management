import { describe, expect, it, vi } from 'vitest'
import { ITEM_WORKBENCH_TABS } from './workbench.config'
import { getVisibleWorkbenchTabs, resolveWorkbenchTab } from './workbench.rules'

describe('物料工作台页签权限', () => {
  it('配置 12 个页签且每个页签仅由配置的权限开放', () => {
    expect(ITEM_WORKBENCH_TABS.map((tab) => tab.key)).toEqual([
      'materials',
      'consumables',
      'assets',
      'locations',
      'responsible_history',
      'inbound',
      'documents',
      'transfers',
      'movements',
      'claims',
      'settlements',
      'overdues'
    ])

    for (const tab of ITEM_WORKBENCH_TABS) {
      const visible = getVisibleWorkbenchTabs([tab.permissions[0]])
      const expected = tab.permissions[0] === 'item:location:list'
        ? ['locations', 'responsible_history']
        : [tab.key]
      expect(visible.map((item) => item.key), tab.key).toEqual(expected)
    }
  })

  it('库位移动与库存核销的多个权限按 OR 判断', () => {
    expect(getVisibleWorkbenchTabs(['item:transfer:create']).map((tab) => tab.key)).toEqual(['transfers'])
    expect(getVisibleWorkbenchTabs(['item:transfer:receive']).map((tab) => tab.key)).toEqual(['transfers'])
    expect(getVisibleWorkbenchTabs(['item:settlement:create']).map((tab) => tab.key)).toEqual(['settlements'])
    expect(getVisibleWorkbenchTabs(['item:settlement:review']).map((tab) => tab.key)).toEqual(['settlements'])
  })

  it('无权限页签不会进入可见集合，也不会触发 loader', () => {
    const allowedLoader = vi.fn()
    const deniedLoader = vi.fn()
    const tabs = [
      { key: 'allowed', permissions: ['allowed'], loader: allowedLoader },
      { key: 'denied', permissions: ['denied'], loader: deniedLoader }
    ]

    const visible = getVisibleWorkbenchTabs(['allowed'], tabs)
    const activeKey = resolveWorkbenchTab('denied', visible)
    visible.find((tab) => tab.key === activeKey)?.loader()

    expect(allowedLoader).toHaveBeenCalledOnce()
    expect(deniedLoader).not.toHaveBeenCalled()
  })
})

describe('物料工作台页签回退', () => {
  it('非法或无权限 tab 回退到第一个可见页签', () => {
    const visible = getVisibleWorkbenchTabs(['item:stock:list', 'item:movement:list'])

    expect(resolveWorkbenchTab('not-exists', visible)).toBe('consumables')
    expect(resolveWorkbenchTab('materials', visible)).toBe('consumables')
    expect(resolveWorkbenchTab(undefined, visible)).toBe('consumables')
  })

  it('tab=overdues 在有权限时保持有效', () => {
    const visible = getVisibleWorkbenchTabs(['item:stock:list', 'item:overdue:list'])

    expect(resolveWorkbenchTab('overdues', visible)).toBe('overdues')
  })

  it('完全无权限时不产生活动页签', () => {
    const visible = getVisibleWorkbenchTabs([])

    expect(visible).toEqual([])
    expect(resolveWorkbenchTab('overdues', visible)).toBe('')
  })
})
