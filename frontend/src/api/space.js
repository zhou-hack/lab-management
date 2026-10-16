import request from './request'

/**
 * 空间管理接口（校区 / 楼栋 / 实验室 / 开放规则）
 */

// ---------- 校区 ----------

/** 校区分页列表 → PageResult<CampusPageItemVO> */
export function pageCampuses(params) {
  return request.get('/space/campuses', { params })
}

/** 启用校区下拉选项 → CampusOptionVO[] */
export function getCampusOptions() {
  return request.get('/space/campuses/options')
}

/** 新增校区 */
export function createCampus(data) {
  return request.post('/space/campuses', data)
}

/** 编辑校区（编码不可改） */
export function updateCampus(id, data) {
  return request.put(`/space/campuses/${id}`, data)
}

/** 启用/停用校区（存在启用楼栋时禁止停用） */
export function updateCampusEnabled(id, enabled) {
  return request.put(`/space/campuses/${id}/enabled`, { enabled })
}

// ---------- 楼栋 ----------

/** 楼栋分页列表 → PageResult<BuildingPageItemVO> */
export function pageBuildings(params) {
  return request.get('/space/buildings', { params })
}

/** 启用楼栋下拉选项（可按校区过滤）→ BuildingOptionVO[] */
export function getBuildingOptions(campusId) {
  return request.get('/space/buildings/options', { params: { campusId: campusId || undefined } })
}

/** 新增楼栋 */
export function createBuilding(data) {
  return request.post('/space/buildings', data)
}

/** 编辑楼栋（编码不可改；允许调整所属校区） */
export function updateBuilding(id, data) {
  return request.put(`/space/buildings/${id}`, data)
}

/** 启用/停用楼栋（存在启用实验室时禁止停用） */
export function updateBuildingEnabled(id, enabled) {
  return request.put(`/space/buildings/${id}/enabled`, { enabled })
}

// ---------- 实验室 ----------

/** 实验室分页目录 → PageResult<LabPageItemVO> */
export function pageLabs(params) {
  return request.get('/space/labs', { params })
}

/** 新增实验室 */
export function createLab(data) {
  return request.post('/space/labs', data)
}

/** 编辑实验室（编码不可改；调整管理部门需同时具备新旧部门写范围） */
export function updateLab(id, data) {
  return request.put(`/space/labs/${id}`, data)
}

/** 启用/停用实验室 */
export function updateLabEnabled(id, enabled) {
  return request.put(`/space/labs/${id}/enabled`, { enabled })
}

// ---------- 开放规则 ----------

/** 实验室开放规则列表 → LabOpenRuleVO[] */
export function getOpenRules(labId) {
  return request.get(`/space/labs/${labId}/open_rules`)
}

/** 新增开放规则（同日时间窗） */
export function createOpenRule(labId, data) {
  return request.post(`/space/labs/${labId}/open_rules`, data)
}

/** 删除开放规则 */
export function deleteOpenRule(ruleId) {
  return request.delete(`/space/labs/open_rules/${ruleId}`)
}
