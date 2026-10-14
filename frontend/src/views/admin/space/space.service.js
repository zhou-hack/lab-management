/**
 * 场地模块服务门面：
 * - 测试登录（token === 'test_token'）：走本地演示数据 space.demo.js，保证无后端时原型可演示；
 * - 真实登录：走 api/space.js（/api/v1/space/*）。
 * 后端就绪后页面无需改动，仅删除测试登录入口即可。
 */
import { getToken } from '@/utils/token'
import {
  pageLabs,
  getLabDetail,
  createLab,
  updateLab,
  enableLab,
  disableLab,
  deleteLab,
  getCampusOptions,
  getBuildingOptions
} from '@/api/space'
import { demoSpaceApi } from './space.demo'

const isDemo = () => getToken() === 'test_token'

export function fetchCampusOptions() {
  return isDemo() ? demoSpaceApi.campusOptions() : getCampusOptions(true)
}

export function fetchBuildingOptions(campusId) {
  return isDemo() ? demoSpaceApi.buildingOptions(campusId) : getBuildingOptions(campusId, true)
}

/** 归口部门选项：部门模块未落地，演示期返回固定数据；后端接口落地后替换 */
export function fetchDepartmentOptions() {
  return isDemo()
    ? demoSpaceApi.departmentOptions()
    : Promise.resolve([{ id: '1', name: '设备处' }])
}

export function fetchLabPage(params) {
  return isDemo() ? demoSpaceApi.pageLabs(params) : pageLabs(params)
}

export function fetchLabDetail(id) {
  return isDemo() ? demoSpaceApi.getLabDetail(id) : getLabDetail(id)
}

export function saveLab(id, data) {
  return isDemo()
    ? id
      ? demoSpaceApi.updateLab(id, data)
      : demoSpaceApi.createLab(data)
    : id
      ? updateLab(id, data)
      : createLab(data)
}

export function switchLabEnabled(id, enabled) {
  return isDemo()
    ? enabled
      ? demoSpaceApi.enableLab(id)
      : demoSpaceApi.disableLab(id)
    : enabled
      ? enableLab(id)
      : disableLab(id)
}

export function removeLab(id) {
  return isDemo() ? demoSpaceApi.deleteLab(id) : deleteLab(id)
}
