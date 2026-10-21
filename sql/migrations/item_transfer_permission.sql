-- ============================================================================
-- 物料批量导入/导出权限增量脚本（2026-10-17）
--   适用：已执行 lab_management_all.sql（v0.1 整合版）或物料一/二阶段种子、
--         但尚未登记导入导出权限的运行库（本仓库开发库已验证）。
--   内容：与 lab_management_all.sql 5.9 段完全一致——
--         权限 160-163、菜单 206-209、权限绑定 245-248、菜单绑定 357-360。
--   说明：物料/分类 delete 权限（138-140/200-202/238-240/350-352）已先行
--         登记于运行库，本脚本不重复插入；全新库直接执行正本，无需本脚本。
--   执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_transfer_permission.sql
--   幂等：INSERT IGNORE，已存在的同 ID 种子跳过，可重复执行（2026-10-20 收编时改造）。
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

-- OPS_ADMIN(5) 补绑：其全量动态绑定由 sys_ops_role.sql 在建库时刻生成，
-- 旧库新增种子需同步补绑（id 取当前最大值顺延，唯一索引 + NOT EXISTS 双保险幂等）
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY p.id), 5, p.id, 1
FROM sys_permission p
WHERE p.id IN (160, 161, 162, 163)
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 5 AND rp.permission_id = p.id);

SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_menu);
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY m.id), 5, m.id, 1
FROM sys_menu m
WHERE m.id IN (206, 207, 208, 209)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.role_id = 5 AND rm.menu_id = m.id);
