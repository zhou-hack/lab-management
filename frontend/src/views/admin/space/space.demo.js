/**
 * 场地模块原型演示数据（后端未接入时使用）。
 * 与 sql/sys_space.sql 初始数据保持一致：主校区 / A 楼、C 楼、F 楼 / A703、C703、F220。
 * 仅当测试登录（token === 'test_token'）时启用；真实登录走 api/space.js。
 */

const campuses = [
  { id: '1', label: '主校区', parentId: null }
]

const buildings = [
  { id: '1', label: 'A 楼', parentId: '1' },
  { id: '2', label: 'C 楼', parentId: '1' },
  { id: '3', label: 'F 楼', parentId: '1' }
]

const departments = [
  { id: '1', name: '设备处' }
]

function weekdayRules(labId) {
  const rules = []
  let rid = 0
  for (let day = 1; day <= 5; day += 1) {
    rules.push(
      {
        id: String(++rid),
        labId: String(labId),
        weekday: day,
        openTime: '08:00',
        closeTime: '12:00',
        validFrom: '2026-09-01',
        validTo: '2027-08-31',
        enabled: 1
      },
      {
        id: String(++rid),
        labId: String(labId),
        weekday: day,
        openTime: '14:00',
        closeTime: '17:00',
        validFrom: '2026-09-01',
        validTo: '2027-08-31',
        enabled: 1
      }
    )
  }
  return rules
}

let labs = [
  {
    id: '1',
    buildingId: '1',
    buildingName: 'A 楼',
    campusId: '1',
    campusName: '主校区',
    departmentId: '1',
    departmentName: '设备处',
    labCode: 'A703',
    labName: 'A703 网络实训室',
    labType: 'COMPUTER_ROOM',
    floorNo: 'F7',
    roomNo: '703',
    capacity: 50,
    areaSqm: 120.0,
    locationNote: 'A 楼七层东侧，网络综合布线实训环境',
    enabled: 1,
    status: 'AVAILABLE',
    openRules: weekdayRules(1),
    createTime: '2026-09-01T00:00:00.000Z',
    updateTime: '2026-09-01T00:00:00.000Z'
  },
  {
    id: '2',
    buildingId: '2',
    buildingName: 'C 楼',
    campusId: '1',
    campusName: '主校区',
    departmentId: '1',
    departmentName: '设备处',
    labCode: 'C703',
    labName: 'C703 电子电工机房',
    labType: 'ELECTRONIC_LAB',
    floorNo: 'F7',
    roomNo: '703',
    capacity: 45,
    areaSqm: 110.0,
    locationNote: 'C 楼七层，电子电工一体化教学',
    enabled: 1,
    status: 'AVAILABLE',
    openRules: [],
    createTime: '2026-09-01T00:00:00.000Z',
    updateTime: '2026-09-01T00:00:00.000Z'
  },
  {
    id: '3',
    buildingId: '3',
    buildingName: 'F 楼',
    campusId: '1',
    campusName: '主校区',
    departmentId: '1',
    departmentName: '设备处',
    labCode: 'F220',
    labName: 'F220 实训室',
    labType: 'TRAINING_ROOM',
    floorNo: 'F2',
    roomNo: '220',
    capacity: 40,
    areaSqm: 96.0,
    locationNote: 'F 楼二层，通用实训场地',
    enabled: 1,
    status: 'AVAILABLE',
    openRules: [],
    createTime: '2026-09-01T00:00:00.000Z',
    updateTime: '2026-09-01T00:00:00.000Z'
  }
]

let seq = 100

function delay(data, ms = 200) {
  return new Promise((resolve) => setTimeout(() => resolve(structuredClone(data)), ms))
}

function deriveStatus(lab) {
  lab.status = lab.enabled === 1 ? 'AVAILABLE' : 'DISABLED'
}

export const demoSpaceApi = {
  campusOptions: () => delay(campuses),
  buildingOptions: (campusId) =>
    delay(campusId ? buildings.filter((b) => b.parentId === String(campusId)) : buildings),
  departmentOptions: () => delay(departments),

  pageLabs(params) {
    let rows = labs.slice()
    if (params.labName) rows = rows.filter((l) => l.labName.includes(params.labName.trim()))
    if (params.labCode) rows = rows.filter((l) => l.labCode === params.labCode.trim())
    if (params.campusId) rows = rows.filter((l) => l.campusId === String(params.campusId))
    if (params.buildingId) rows = rows.filter((l) => l.buildingId === String(params.buildingId))
    if (params.enabled !== undefined && params.enabled !== null && params.enabled !== '') {
      rows = rows.filter((l) => l.enabled === Number(params.enabled))
    }
    if (params.labType) {
      const types = Array.isArray(params.labType) ? params.labType : [params.labType]
      rows = rows.filter((l) => types.includes(l.labType))
    }
    const pageNum = Number(params.pageNum) || 1
    const pageSize = Number(params.pageSize) || 20
    const total = rows.length
    const records = rows.slice((pageNum - 1) * pageSize, pageNum * pageSize)
    return delay({ records, total: Number(total), pageNum, pageSize })
  },

  getLabDetail: (id) => {
    const lab = labs.find((l) => l.id === String(id))
    if (!lab) return Promise.reject(new Error('场地不存在'))
    return delay({ ...lab, statusSource: '由场地实体启用状态派生（占用/维护来源待预约、设备模块接入）' })
  },

  createLab: (data) => {
    if (labs.some((l) => l.labCode === data.labCode)) {
      return Promise.reject(new Error(`场地编码已存在：${data.labCode}`))
    }
    const building = buildings.find((b) => b.id === String(data.buildingId))
    const campus = campuses.find((c) => c.id === building?.parentId)
    const dept = departments.find((d) => d.id === String(data.departmentId))
    const lab = {
      id: String(++seq),
      ...data,
      buildingId: String(data.buildingId),
      departmentId: String(data.departmentId),
      buildingName: building?.label,
      campusId: campus?.id,
      campusName: campus?.label,
      departmentName: dept?.name,
      openRules: [],
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString()
    }
    deriveStatus(lab)
    labs.unshift(lab)
    return delay(lab)
  },

  updateLab: (id, data) => {
    const lab = labs.find((l) => l.id === String(id))
    if (!lab) return Promise.reject(new Error('场地不存在'))
    Object.assign(lab, data, { updateTime: new Date().toISOString() })
    deriveStatus(lab)
    return delay(lab)
  },

  enableLab: (id) => {
    const lab = labs.find((l) => l.id === String(id))
    if (lab) {
      lab.enabled = 1
      deriveStatus(lab)
    }
    return delay(null)
  },

  disableLab: (id) => {
    const lab = labs.find((l) => l.id === String(id))
    if (lab) {
      lab.enabled = 0
      deriveStatus(lab)
    }
    return delay(null)
  },

  deleteLab: (id) => {
    labs = labs.filter((l) => l.id !== String(id))
    return delay(null)
  }
}
