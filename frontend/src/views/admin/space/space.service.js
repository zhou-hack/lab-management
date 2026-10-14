/**
 * 场地模块服务门面：
 * - 测试登录（token === 'test_token'）：走本地演示数据 space.demo.js，保证无后端时原型可演示；
 * - 真实登录：走 api/space.js（/api/v1/space/spaces/*）。
 * 后端就绪后页面无需改动，仅删除测试登录入口即可。
 */
import { getToken } from '@/utils/token'
import {
  pageSpaces,
  getSpaceStats,
  getSpaceDetail,
  createSpace,
  updateSpace,
  enableSpace,
  disableSpace,
  deleteSpace
} from '@/api/space'
import { demoSpaceApi } from './space.demo'

const isDemo = () => getToken() === 'test_token'

export function fetchSpacePage(params) {
  return isDemo() ? demoSpaceApi.pageSpaces(params) : pageSpaces(params)
}

export function fetchSpaceStats() {
  return isDemo() ? demoSpaceApi.spaceStats() : getSpaceStats()
}

export function fetchSpaceDetail(id) {
  return isDemo() ? demoSpaceApi.getSpaceDetail(id) : getSpaceDetail(id)
}

export function saveSpace(id, data) {
  return isDemo()
    ? id
      ? demoSpaceApi.updateSpace(id, data)
      : demoSpaceApi.createSpace(data)
    : id
      ? updateSpace(id, data)
      : createSpace(data)
}

export function switchSpaceEnabled(id, enabled) {
  return isDemo()
    ? enabled
      ? demoSpaceApi.enableSpace(id)
      : demoSpaceApi.disableSpace(id)
    : enabled
      ? enableSpace(id)
      : disableSpace(id)
}

export function removeSpace(id) {
  return isDemo() ? demoSpaceApi.deleteSpace(id) : deleteSpace(id)
}
