import request from '../request'

/**
 * 系统管理-参数配置接口（业务策略版本化，发布后不可修改）
 */

/** 策略分页列表 → PageResult<PolicyPageItemVO> */
export function pagePolicies(params) {
  return request.get('/system/policies', { params })
}

/** 全部策略编码的参数字段元数据 → { [policyCode]: [{ key, label, type, min, max, unit }] } */
export function listPolicySchemas() {
  return request.get('/system/policies/schemas')
}

/** 发布新版本 → 新版本号 */
export function publishPolicy(data) {
  return request.post('/system/policies/publish', data)
}
