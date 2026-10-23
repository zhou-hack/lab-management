-- ============================================================================
-- v0.1 整合统一对齐迁移（旧库 → lab_management_all.sql 2026-10-19 定稿编号）
-- ============================================================================
-- 适用：已执行过旧版种子（设备菜单 200/202/203/204 层级结构、物料 101-137、
--      booking 3001-3004 旧绑定）的运行库；新库直接执行 lab_management_all.sql
--      正本，勿执行本脚本。
-- 前置：先执行 flatten_equipment_menu.sql（按 menu_code 完成扁平化结构转换），
--      本脚本在其基础上把设备菜单 ID 对齐到主种子（202→210、204→211），
--      物理清除已逻辑删除的旧层级菜单（200/203），腾出编号给物料 delete 段。
-- 内容：
--   1) 设备菜单 ID 对齐主种子（202→210、204→211；同步 sys_role_menu/parent_id）
--   2) 物理删除旧层级死数据（菜单 200/203 及其角色绑定）
--   3) 物料 delete 段（权限 138-140、菜单 200-202、绑定 238-240、菜单绑定 350-352）
--   4) 物料代领菜单 205 + 学生代理绑定（249-250/361）
--   5) system 权限补齐 27-30（重置密码/调整角色权限/名单导出/名单导入）及 ADMIN 绑定
--      （28 system:role:assign 按定稿仅 OPS_ADMIN，由 sys_ops_role.sql 动态绑定）
--   6) booking 绑定对齐 2026-10-17 定稿：学生回收 list(3002)，ADMIN 回收
--      create/cancel(3001/3003)，补 ADMIN 只读(3009,1,3005)
-- 幂等：ID 对齐为定点 UPDATE/DELETE（已对齐则空跑）；种子一律 INSERT IGNORE。
-- 配套：执行完本脚本后按序执行 add_asset_io_permission.sql、
--      add_equipment_booking_permission.sql、item_transfer_permission.sql、
--      item_stock_transfer_permission.sql、item_request_proxy.sql，
--      最后重跑 sys_ops_role.sql 刷新 OPS_ADMIN 全量绑定。
-- ============================================================================
SET NAMES utf8mb4;

-- 0) OPS_ADMIN(5) 绑定整组清空（由 sys_ops_role.sql 末尾重跑重建，
--    同时解除对菜单 200/203 的引用以便物理删除）
DELETE FROM sys_role_menu WHERE role_id = 5;
DELETE FROM sys_role_permission WHERE role_id = 5;

-- 1) 设备菜单 ID 对齐主种子 -------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;

-- 1.1 引用改挂：设备管理页 202 → 210、设备登记 204 → 211
UPDATE sys_role_menu SET menu_id = 210 WHERE menu_id = 202;
UPDATE sys_role_menu SET menu_id = 211 WHERE menu_id = 204;
UPDATE sys_menu SET parent_id = 210 WHERE parent_id = 202;
UPDATE sys_menu SET id = 210 WHERE id = 202;
UPDATE sys_menu SET id = 211 WHERE id = 204;

-- 1.2 物理删除旧层级死数据（flatten 后 deleted=1 的目录与台账页及其绑定）
DELETE FROM sys_role_menu WHERE menu_id IN (200, 203);
DELETE FROM sys_menu WHERE id IN (200, 203) AND deleted = 1;

SET FOREIGN_KEY_CHECKS = 1;

-- 2) 物料 delete 段（与 lab_management_all.sql 4.11 一致）-------------------
INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(138, 'item:category:delete', '物料分类删除', 'item', 1),
(139, 'item:material:delete', '物料档案删除', 'item', 1),
(140, 'item:location:delete', '库位删除',     'item', 1);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(200, 152, 'item:category:delete', '物料分类删除', NULL, NULL, 138, 'ACTION', 5, 1),
(201, 153, 'item:material:delete', '物料档案删除', NULL, NULL, 139, 'ACTION', 5, 1),
(202, 154, 'item:location:delete', '库位删除',     NULL, NULL, 140, 'ACTION', 5, 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(238, 1, 138, 1), (239, 1, 139, 1), (240, 1, 140, 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(350, 1, 200, 1), (351, 1, 201, 1), (352, 1, 202, 1);

-- 3) 物料代领菜单 205 + 学生代理绑定（与正本 5.8/5.9 一致；
--    2026-10-22 菜单改绑 ASSISTANT：未授权学生不显示，动态角色随授权并入）---
INSERT IGNORE INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(205, 151, 'ITEM_ASSISTANT_PICKUP', '物料代领', '/admin/item/assistant_pickup', 'ItemAssistantPickup', 122, 'PAGE', 2, 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(249, 3, 122, 1), (250, 3, 126, 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(361, 4, 205, 1);

-- 4) system 权限补齐 27-30 及 ADMIN 绑定（与 sys_rbac.sql 一致；
--    28 system:role:assign 不绑 ADMIN，仅 OPS_ADMIN 动态绑定）---------------
INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(27, 'system:user:reset_password', '重置密码',    'system', 1),
(29, 'system:roster:export',    '名单导出',       'system', 1),
(30, 'system:roster:import',    '名单导入',       'system', 1),
(28, 'system:role:assign',       '调整角色权限',   'system', 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(51, 1, 27, 1), (84, 1, 29, 1), (85, 1, 30, 1);

-- 5) booking 绑定对齐 2026-10-17 定稿 ----------------------------------------
--    学生仅 view(3005)：回收 list(3002)（3001/3003 由 booking_flow_deploy.sql 回收）
DELETE FROM sys_role_permission WHERE role_id = 3 AND permission_id = 3002;
--    ADMIN 仅 list+approve（3002/3004）+ view(3005)：回收 create/cancel(3001/3003)
DELETE FROM sys_role_permission WHERE role_id = 1 AND permission_id IN (3001, 3003);
--    补 ADMIN 只读绑定（id 3009 在学生 3002 回收后空闲）
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(3009, 1, 3005, 1);

-- 6) 部署后人工校验：
-- SELECT id, menu_code, deleted FROM sys_menu WHERE id BETWEEN 200 AND 221 ORDER BY id;
-- 预期：200-202 物料 delete、205 物料代领、210 设备管理、211 设备登记。
-- SELECT role_id, permission_id FROM sys_role_permission WHERE permission_id BETWEEN 3001 AND 3005 ORDER BY role_id, permission_id;
-- 预期：学生(3) 仅 3005；教师(2) 3001/3002/3003/3005；管理员(1) 3002/3004/3005。
