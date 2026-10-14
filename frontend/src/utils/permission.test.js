import { describe, it, expect } from 'vitest'
import { hasPermission } from './permission'

describe('v-permission 权限判断', () => {
  const perms = ['equipment:list', 'equipment:edit']

  it('拥有权限返回 true', () => {
    expect(hasPermission('equipment:edit', perms)).toBe(true)
  })

  it('无权限返回 false', () => {
    expect(hasPermission('equipment:delete', perms)).toBe(false)
  })

  it('数组任一命中即通过', () => {
    expect(hasPermission(['booking:audit', 'equipment:list'], perms)).toBe(true)
  })

  it('空值与空数组放行', () => {
    expect(hasPermission('', perms)).toBe(true)
    expect(hasPermission([], perms)).toBe(true)
  })

  it('未登录或权限未加载（空权限集）放行', () => {
    expect(hasPermission('item:material:delete', [])).toBe(true)
    expect(hasPermission('item:material:delete', null)).toBe(true)
  })
})
