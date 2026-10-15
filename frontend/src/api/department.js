import request from './request'

/** 部门下拉选项（启用中，平铺含 parentId，登录即可用） */
export function departmentOptions() {
  return request.get('/system/departments/options')
}

/**
 * 部门树选项（el-tree-select 数据：{ id, label, children }）
 * 按 parentId 将平铺选项组装为树
 */
export async function departmentTreeOptions() {
  const records = (await departmentOptions()) || []
  const nodeMap = {}
  const roots = []
  for (const record of records) {
    nodeMap[record.id] = { id: record.id, label: record.departmentName, children: [] }
  }
  for (const record of records) {
    const node = nodeMap[record.id]
    if (record.parentId && nodeMap[record.parentId]) {
      nodeMap[record.parentId].children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
}
