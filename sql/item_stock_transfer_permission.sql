-- ============================================================================
-- 库存余额 + 盘点导入/导出权限增量脚本（2026-10-17）
--   适用：已执行 lab_management_all.sql（含 5.10 段）或 item_transfer_permission.sql、
--         但尚未登记库存/盘点导入导出权限的运行库。
--   内容：与 lab_management_all.sql 5.11 段完全一致——
--         权限 164-166、菜单 212-214、权限绑定 260-262、菜单绑定 362-364。
--   说明：盘点实盘回导复用既有 item:stocktake:count 权限（135），不新增；
--         全新库直接执行正本，无需本脚本。
--   执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/item_stock_transfer_permission.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(164, 'item:balance:import',   '期初余额批量导入', 'item', 1),
(165, 'item:balance:export',   '库存余额导出',     'item', 1),
(166, 'item:stocktake:export', '盘点快照导出',     'item', 1);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(212, 156, 'item:balance:import',   '期初余额批量导入', NULL, NULL, 164, 'ACTION', 3, 1),
(213, 156, 'item:balance:export',   '库存余额导出',     NULL, NULL, 165, 'ACTION', 4, 1),
(214, 183, 'item:stocktake:export', '盘点快照导出',     NULL, NULL, 166, 'ACTION', 6, 1);

INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(260, 1, 164, 1), (261, 1, 165, 1), (262, 1, 166, 1);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(362, 1, 212, 1), (363, 1, 213, 1), (364, 1, 214, 1);
