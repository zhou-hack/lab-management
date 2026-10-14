/**
 * 场地模块原型演示数据（后端未接入时使用）。
 * 数据结构与 sql/biz_space.sql 初始数据保持一致；为展示状态三态与统计卡片，
 * C703 演示为维护中（SQL 种子为 AVAILABLE，接入真实后端后以库内数据为准）。
 * 仅当测试登录（token === 'test_token'）时启用；真实登录走 api/space.js。
 */

let spaces = [
  {
    id: '1',
    spaceName: 'A703 网络实训室',
    spaceType: 'TRAINING_ROOM',
    location: 'A 楼七层 A703',
    capacity: 50,
    facilities: '网络综合布线实训环境：56 口配线架 12 组、路由交换设备 12 套、实训工位 50 个',
    openStart: '08:00',
    openEnd: '21:00',
    status: 'AVAILABLE',
    createTime: '2026-09-01T00:00:00.000Z',
    updateTime: '2026-09-01T00:00:00.000Z'
  },
  {
    id: '2',
    spaceName: 'C703 电子电工实训室',
    spaceType: 'TRAINING_ROOM',
    location: 'C 楼七层 C703',
    capacity: 45,
    facilities: '示波器 20 台、直流稳压电源 20 台、电工电子实训台 12 位',
    openStart: '08:00',
    openEnd: '21:00',
    status: 'MAINTAINING',
    createTime: '2026-09-01T00:00:00.000Z',
    updateTime: '2026-10-10T00:00:00.000Z'
  },
  {
    id: '3',
    spaceName: '教学楼201多媒体教室',
    spaceType: 'CLASSROOM',
    location: '教学楼二层 201',
    capacity: 60,
    facilities: '投影仪 1 台、音响系统 1 套、中控讲台 1 位',
    openStart: '08:00',
    openEnd: '21:00',
    status: 'AVAILABLE',
    createTime: '2026-09-01T00:00:00.000Z',
    updateTime: '2026-09-01T00:00:00.000Z'
  }
]

let seq = 100

function delay(data, ms = 200) {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(data)), ms))
}

function stats() {
  const result = { total: spaces.length, available: 0, maintaining: 0, closed: 0 }
  for (const s of spaces) {
    if (s.status === 'AVAILABLE') result.available += 1
    else if (s.status === 'MAINTAINING') result.maintaining += 1
    else if (s.status === 'CLOSED') result.closed += 1
  }
  return result
}

export const demoSpaceApi = {
  pageSpaces(params) {
    let rows = spaces.slice()
    if (params.spaceName) rows = rows.filter((s) => s.spaceName.includes(params.spaceName.trim()))
    if (params.location) rows = rows.filter((s) => s.location.includes(params.location.trim()))
    if (params.spaceType) rows = rows.filter((s) => s.spaceType === params.spaceType)
    if (params.status) rows = rows.filter((s) => s.status === params.status)
    if (params.minCapacity) rows = rows.filter((s) => s.capacity >= Number(params.minCapacity))
    if (params.maxCapacity) rows = rows.filter((s) => s.capacity <= Number(params.maxCapacity))
    const pageNum = Number(params.pageNum) || 1
    const pageSize = Number(params.pageSize) || 20
    const records = rows.slice((pageNum - 1) * pageSize, pageNum * pageSize)
    return delay({ records, total: Number(rows.length), pageNum, pageSize })
  },

  spaceStats: () => delay(stats()),

  getSpaceDetail: (id) => {
    const space = spaces.find((s) => s.id === String(id))
    if (!space) return Promise.reject(new Error('场地不存在'))
    return delay(space)
  },

  createSpace: (data) => {
    if (spaces.some((s) => s.spaceName === data.spaceName)) {
      return Promise.reject(new Error(`场地名称已存在：${data.spaceName}`))
    }
    const space = {
      id: String(++seq),
      ...data,
      openStart: data.openStart || '08:00',
      openEnd: data.openEnd || '21:00',
      status: data.status || 'AVAILABLE',
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString()
    }
    spaces.unshift(space)
    return delay(space)
  },

  updateSpace: (id, data) => {
    const space = spaces.find((s) => s.id === String(id))
    if (!space) return Promise.reject(new Error('场地不存在'))
    if (spaces.some((s) => s.id !== String(id) && s.spaceName === data.spaceName)) {
      return Promise.reject(new Error(`场地名称已被占用：${data.spaceName}`))
    }
    Object.assign(space, data, { updateTime: new Date().toISOString() })
    return delay(space)
  },

  enableSpace: (id) => {
    const space = spaces.find((s) => s.id === String(id))
    if (space) space.status = 'AVAILABLE'
    return delay(null)
  },

  disableSpace: (id) => {
    const space = spaces.find((s) => s.id === String(id))
    if (space) space.status = 'CLOSED'
    return delay(null)
  },

  deleteSpace: (id) => {
    spaces = spaces.filter((s) => s.id !== String(id))
    return delay(null)
  }
}
