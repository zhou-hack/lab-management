import { describe, it, expect, beforeEach } from 'vitest'
import { getToken, setToken, removeToken } from './token'

describe('token 工具（sessionStorage）', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('初始为空', () => {
    expect(getToken()).toBeNull()
  })

  it('写入后可读取', () => {
    setToken('token_abc')
    expect(getToken()).toBe('token_abc')
  })

  it('删除后返回 null', () => {
    setToken('token_abc')
    removeToken()
    expect(getToken()).toBeNull()
  })
})
