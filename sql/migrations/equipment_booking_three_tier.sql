-- ============================================================================
-- 迁移：设备使用申请三权级 + 台账导入导出种子（幂等，可重复执行）
-- 版本：2026-10-23；来源：固定设备管理分支 add_equipment_booking_permission.sql /
--       add_asset_io_permission.sql 与 space_zhou_hack 分支 add_directory_category_permission.sql /
--       move_booking_admin_menu.sql（整合收编，演示账号段不收录）
-- 适用：2026-10-20 基线（equipment 权限至 154/168-169、菜单至 210-211）建成的库。
-- 内容：
--   1. 权限 170-171（台账导入导出）、172-179（使用申请三权级）；
--   2. 菜单 216-221（目录/我的申请/初审挂 NULL 根；审批 219 与导入导出 220/221 挂设备管理 210 下）；
--   3. 权限绑定 533-534（ADMIN 导入导出）、545-557（三权级）、558-559（教师/学生分类树只读）；
--   4. 菜单绑定 616-623；
--   5. 兼容修正：219 仍挂 NULL 根的旧库改挂 210（space_zhou_hack 定稿）。
-- 前置：无（仅种子）；执行后请重跑 sql/sys_ops_role.sql 刷新 OPS_ADMIN 全量绑定。
-- 幂等：全部 NOT EXISTS 守卫，UPDATE 自然幂等。
-- ============================================================================
USE lab_management;
SET NAMES utf8mb4;

-- 1. 权限（equipment 段 170-179；160-169 已让 item 导入导出/老师审核与报修 168/169）
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 170, 'equipment:asset:import', '设备台账导入', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 170);
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 171, 'equipment:asset:export', '设备台账导出', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 171);
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 172, 'equipment:booking:apply', '设备使用申请', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 172);
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 173, 'equipment:booking:list_own', '本人设备申请查询', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 173);
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 174, 'equipment:booking:cancel', '撤销本人设备申请', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 174);
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 175, 'equipment:booking:teacher_review', '设备申请教师初审', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 175);
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 176, 'equipment:booking:admin_review', '设备申请终审代批', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 176);
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 177, 'equipment:booking:checkout', '设备借出发放登记', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 177);
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 178, 'equipment:booking:return', '设备归还登记', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 178);
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 179, 'equipment:booking:list_all', '设备申请全量查询', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 179);

-- 2. 菜单（216-218 用户端挂 NULL 根；219/220/221 挂设备管理 210 下）
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 216, NULL, 'C_EQUIPMENT_DIRECTORY', '设备目录', '/equipment/directory', 'EquipmentDirectory', 149, 'PAGE', 21, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 216);
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 217, NULL, 'C_EQUIPMENT_MY_BOOKING', '我的设备申请', '/equipment/bookings/my', 'MyEquipmentBookings', 173, 'PAGE', 22, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 217);
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 218, NULL, 'C_EQUIPMENT_TEACHER_REVIEW', '设备申请初审', '/equipment/bookings/teacher', 'EquipmentTeacherApprove', 175, 'PAGE', 23, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 218);
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 219, 210, 'C_EQUIPMENT_BOOKING_ADMIN', '设备使用审批', '/admin/equipment/bookings', 'EquipmentBookingManage', 176, 'PAGE', 24, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 219);
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 220, 210, 'equipment:asset:import', '设备导入', NULL, NULL, 170, 'ACTION', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 220);
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 221, 210, 'equipment:asset:export', '设备导出', NULL, NULL, 171, 'ACTION', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 221);

-- 2.1 兼容修正：219 仍挂 NULL 根的旧库改挂设备管理 210 下（2026-10-22 定稿）
UPDATE sys_menu SET parent_id = 210 WHERE id = 219 AND parent_id IS NULL;

-- 3. 角色权限绑定（533-534 ADMIN 导入导出；545-557 三权级；558-559 分类树只读）
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 533, 1, 170, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 533);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 534, 1, 171, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 534);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 545, 1, 176, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 545);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 546, 1, 177, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 546);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 547, 1, 178, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 547);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 548, 1, 179, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 548);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 549, 2, 149, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 549);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 550, 2, 172, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 550);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 551, 2, 173, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 551);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 552, 2, 174, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 552);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 553, 2, 175, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 553);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 554, 3, 149, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 554);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 555, 3, 172, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 555);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 556, 3, 173, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 556);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 557, 3, 174, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 557);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 558, 2, 141, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 558);
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 559, 3, 141, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 559);

-- 4. 角色菜单绑定（616-623）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 616, 1, 219, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 616);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 617, 2, 216, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 617);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 618, 2, 217, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 618);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 619, 2, 218, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 619);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 620, 3, 216, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 620);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 621, 3, 217, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 621);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 622, 1, 220, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 622);
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 623, 1, 221, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 623);

-- 5) 校验（预期：权限 170-179 共 10 行；菜单 216-221 共 6 行且 219-221 parent=210）
-- SELECT id, permission_code FROM sys_permission WHERE id BETWEEN 170 AND 179;
-- SELECT id, menu_code, parent_id FROM sys_menu WHERE id BETWEEN 216 AND 221;
