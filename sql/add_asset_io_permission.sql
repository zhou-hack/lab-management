-- ============================================================================
-- 迁移：设备台账导入/导出权限登记（2026-10-19；编号 2026-10-20 二次对齐远端种子）
-- ============================================================================
-- 台账新增 Excel 导入/导出功能，按 模块:资源:操作 规范登记两个权限并绑定 ADMIN(1)：
--   * equipment:asset:import  设备台账导入（170）
--   * equipment:asset:export 设备台账导出（171）
-- 及对应 ACTION 菜单（按钮层），挂到设备管理页 C_EQUIPMENT_ADMIN 下。
-- 编号段（2026-10-20 对齐远端 dev/database@9f757af 最新种子，避开其占用）：
--   权限 170/171（远端 141-154 设备基础、160-169 已让给 item 导入导出/老师审核与
--     设备报修 168/169；170 起空闲，booking 本组 172-179 之后）
--   绑定 533/534（远端 role_permission 515-532 已占）
--   菜单 220/221（远端 sys_menu 151-199/200-215 已占，booking 216-219 之后空闲）
--   菜单绑定 622/623（远端 608/610/1101-1105/3001-3005；booking 616-621 之后）
-- 脚本按固定 ID 插入，可重复执行：已存在（含逻辑删除残留）时跳过对应行。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 权限（equipment 段顺延）
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 170, 'equipment:asset:import', '设备台账导入', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 170);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 171, 'equipment:asset:export', '设备台账导出', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 171);

-- 2. 角色绑定（仅 ADMIN）
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 533, 1, 170, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 533);

INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 534, 1, 171, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 534);

-- 3. ACTION 菜单（挂设备管理页，按 menu_code 定位兜底；
--    按种子规范「ACTION 层 menu_code 即权限码」）
SET @page_id := (SELECT id FROM sys_menu WHERE menu_code = 'C_EQUIPMENT_ADMIN' AND deleted = 0 LIMIT 1);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 220, @page_id, 'equipment:asset:import', '设备导入', NULL, NULL, 170, 'ACTION', 2, 1
WHERE @page_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 220);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 221, @page_id, 'equipment:asset:export', '设备导出', NULL, NULL, 171, 'ACTION', 3, 1
WHERE @page_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 221);

-- 3.1 兼容修正：早期版本以 F_ 前缀插入过的库改回权限码（幂等）
UPDATE sys_menu SET menu_code = 'equipment:asset:import' WHERE id = 220 AND menu_code = 'F_EQUIPMENT_ASSET_IMPORT';
UPDATE sys_menu SET menu_code = 'equipment:asset:export' WHERE id = 221 AND menu_code = 'F_EQUIPMENT_ASSET_EXPORT';

-- 4. 菜单角色绑定（仅 ADMIN）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 622, 1, 220, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 622);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 623, 1, 221, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 623);
