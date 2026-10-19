-- ============================================================================
-- 物料批量导入/导出权限增量脚本（2026-10-17）
--   适用：已执行 lab_management_all.sql（v0.1 整合版）或物料一/二阶段种子、
--         但尚未登记导入导出权限的运行库（本仓库开发库已验证）。
--   内容：与 lab_management_all.sql 5.10 段完全一致——
--         权限 160-163、菜单 206-209、权限绑定 245-248、菜单绑定 357-360。
--   说明：物料/分类 delete 权限（138-140/200-202/238-240/350-352）已先行
--         登记于运行库，本脚本不重复插入；全新库直接执行正本，无需本脚本。
--   执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/item_transfer_permission.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(160, 'item:material:import', '物料批量导入',     'item', 1),
(161, 'item:material:export', '物料批量导出',     'item', 1),
(162, 'item:category:import', '物料分类批量导入', 'item', 1),
(163, 'item:category:export', '物料分类批量导出', 'item', 1);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(206, 152, 'item:category:import', '物料分类批量导入', NULL, NULL, 162, 'ACTION', 6, 1),
(207, 152, 'item:category:export', '物料分类批量导出', NULL, NULL, 163, 'ACTION', 7, 1),
(208, 153, 'item:material:import', '物料批量导入',     NULL, NULL, 160, 'ACTION', 6, 1),
(209, 153, 'item:material:export', '物料批量导出',     NULL, NULL, 161, 'ACTION', 7, 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(245, 1, 160, 1), (246, 1, 161, 1), (247, 1, 162, 1), (248, 1, 163, 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(357, 1, 206, 1), (358, 1, 207, 1), (359, 1, 208, 1), (360, 1, 209, 1);
