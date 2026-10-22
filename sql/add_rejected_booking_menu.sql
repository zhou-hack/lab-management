-- ============================================================================
-- 迁移：学生端「查看驳回申请」独立菜单（2026-10-22）
-- ============================================================================
-- 需求：学生端查看本人被驳回的申请及驳回原因（驳回详情）。
-- 实现：复用本人申请查询接口（/equipment/bookings/my?status=REJECTED，
--       权限 equipment:booking:list_own=173，已绑定 STUDENT 556），
--       前端新增 RejectedBookings 页（route /equipment/bookings/rejected）。
-- 编号规则（避开已占段）：
--   * sys_menu：216-219 设备申请页面、220/221 台账导入导出按钮已占；
--     222 空闲（物料等模块菜单 151-205，互不冲突）
--   * sys_role_menu：616-621 设备申请绑定、622/623 导入导出绑定已占；
--     624 空闲（物料绑定 301-344，互不冲突）
--   * 权限不新增：复用 173（本人申请查询），驳回原因由服务端联查
--     document_event REJECT 事件随列表下发
-- 脚本按固定 ID 插入，可重复执行：已存在时跳过对应行。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 菜单（222，挂 NULL 根；PAGE 层 menu_code 用 C_ 前缀种子规范）
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 222, NULL, 'C_EQUIPMENT_REJECTED', '查看驳回申请', '/equipment/bookings/rejected', 'RejectedBookings', 173, 'PAGE', 25, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 222);

-- 2. 角色菜单绑定（STUDENT=3；教师单免初审无驳回，不绑）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 624, 3, 222, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 624);
