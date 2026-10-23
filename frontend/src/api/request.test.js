import { describe, it, expect, vi, beforeEach } from 'vitest'

const mocks = vi.hoisted(() => ({
  handlers: { req: [], res: [] },
  messages: { error: vi.fn() },
  axios: {
    create: () => ({
      interceptors: {
        request: { use: (fn) => mocks.handlers.req.push(fn) },
        response: { use: (ok, err) => mocks.handlers.res.push({ ok, err }) }
      },
      get: vi.fn(),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn()
    })
  }
}))

vi.mock('axios', () => ({ default: mocks.axios }))
vi.mock('element-plus', () => ({ ElMessage: mocks.messages }))

import request from './request'

describe('request.js 统一错误拦截', () => {
  let errorHandler

  beforeEach(() => {
    mocks.messages.error.mockClear()
    errorHandler = mocks.handlers.res[0].err
  })

  it('统一请求实例已注册响应拦截器', () => {
    expect(request).toBeDefined()
    expect(mocks.handlers.res.length).toBeGreaterThan(0)
  })

  it('后端 4xx 业务错误显示真实 msg（不误报为网络异常）', async () => {
    const err = {
      response: { status: 400, data: { code: 40000, msg: '学生申请必须指定审批教师' } }
    }
    await expect(errorHandler(err)).rejects.toBe(err)
    expect(mocks.messages.error).toHaveBeenCalledWith('学生申请必须指定审批教师')
  })

  it('后端 500 JSON 错误同样显示真实 msg', async () => {
    const err = {
      response: { status: 500, data: { code: 50000, msg: '系统繁忙，请稍后重试' } }
    }
    await expect(errorHandler(err)).rejects.toBe(err)
    expect(mocks.messages.error).toHaveBeenCalledWith('系统繁忙，请稍后重试')
  })

  it('无响应体的网络错误显示网络异常', async () => {
    const err = { message: 'Network Error' }
    await expect(errorHandler(err)).rejects.toBe(err)
    expect(mocks.messages.error).toHaveBeenCalledWith('网络异常，请稍后重试')
  })

  it('超时错误显示请求超时', async () => {
    const err = { code: 'ECONNABORTED', message: 'timeout of 10000ms exceeded' }
    await expect(errorHandler(err)).rejects.toBe(err)
    expect(mocks.messages.error).toHaveBeenCalledWith('请求超时，请重试')
  })
})
