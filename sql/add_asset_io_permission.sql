-- ============================================================================
-- 迁移：设备台账导入/导出权限登记（2026-10-19）
-- ============================================================================
-- 台账新增 Excel 导入/导出功能，按 模块:资源:操作 规范登记两个权限并绑定 ADMIN(1)：
--   * equipment:asset:import  设备台账导入（168）
--   * equipment:asset:export 设备台账导出（169）
-- 及对应 ACTION 菜单（按钮层），挂到设备管理页 C_EQUIPMENT_ADMIN 下。
-- 编号沿用 equipment 段：权限 141-154 之后顺延 168/169（155-159 已保留给场地模块；
-- 原 160/161 与物料批量导入导出段 160-163 冲突，2026-10-19 让号，与
-- lab_management_all.sql 3.7/3.8 节定稿一致）；
-- 绑定 529/530、菜单 216/217、菜单绑定 614/615 取两库（dev/test）当前空闲段。
-- 旧版种子执行过「借用功能」（权限/绑定占用 155-161、529-535 段）的库请先执行
-- 「移除借用功能清理.sql」，否则固定编号插入会因唯一键冲突失败（可见即需清理）。
-- 脚本按固定 ID 插入，可重复执行：已存在（含逻辑删除残留）时跳过对应行。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 权限（equipment 段顺延）
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 168, 'equipment:asset:import', '设备台账导入', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 168);

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled)
SELECT 169, 'equipment:asset:export', '设备台账导出', 'equipment', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE id = 169);

-- 2. 角色绑定（仅 ADMIN）
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 529, 1, 168, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 529);

INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 530, 1, 169, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 530);

-- 3. ACTION 菜单（挂设备管理页，页面 ID 两库均为 163，按 menu_code 定位兜底；
--    按种子规范「ACTION 层 menu_code 即权限码」）
SET @page_id := (SELECT id FROM sys_menu WHERE menu_code = 'C_EQUIPMENT_ADMIN' AND deleted = 0 LIMIT 1);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 216, @page_id, 'equipment:asset:import', '设备导入', NULL, NULL, 168, 'ACTION', 2, 1
WHERE @page_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 216);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled)
SELECT 217, @page_id, 'equipment:asset:export', '设备导出', NULL, NULL, 169, 'ACTION', 3, 1
WHERE @page_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 217);

-- 3.1 兼容修正：早期版本以 F_ 前缀插入过的库改回权限码（幂等）
UPDATE sys_menu SET menu_code = 'equipment:asset:import' WHERE id = 216 AND menu_code = 'F_EQUIPMENT_ASSET_IMPORT';
UPDATE sys_menu SET menu_code = 'equipment:asset:export' WHERE id = 217 AND menu_code = 'F_EQUIPMENT_ASSET_EXPORT';

-- 4. 菜单角色绑定（仅 ADMIN）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 614, 1, 216, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 614);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 615, 1, 217, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE id = 615);
