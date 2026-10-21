import { describe, it, expect, beforeEach } from 'vitest'
import { getToken, setToken, getRefreshToken, setRefreshToken, removeToken } from './token'

describe('token 工具（sessionStorage，access + refresh 双 token）', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('初始为空', () => {
    expect(getToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('写入后可读取', () => {
    setToken('access_abc')
    setRefreshToken('refresh_xyz')
    expect(getToken()).toBe('access_abc')
    expect(getRefreshToken()).toBe('refresh_xyz')
  })

  it('删除后双 token 均返回 null', () => {
    setToken('access_abc')
    setRefreshToken('refresh_xyz')
    removeToken()
    expect(getToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })
})
