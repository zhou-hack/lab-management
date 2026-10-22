-- ============================================================================
-- 库存余额导入/导出权限增量脚本（2026-10-17，2026-10-20 移除盘点部分）
--   适用：已执行 lab_management_all.sql（含 5.10 段）或 item_transfer_permission.sql、
--         但尚未登记库存余额导入导出权限的运行库。
--   内容：与 lab_management_all.sql 5.10 段完全一致——
--         权限 164-165、菜单 212-213、权限绑定 260-261、菜单绑定 362-363。
--   说明：盘点快照导出（166/214/262/364）按 2026-10-19 需求定稿砍除，
--         编号弃用不复用；曾执行过旧版（含 166）的库改走
--         sql/migrations/item_stocktake_drop.sql 统一回收。
--   执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_stock_transfer_permission.sql
--   幂等：INSERT IGNORE，已存在的同 ID 种子跳过，可重复执行（2026-10-20 收编时改造）。
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(164, 'item:balance:import', '期初余额批量导入', 'item', 1),
(165, 'item:balance:export', '库存余额导出',     'item', 1);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(212, 156, 'item:balance:import', '期初余额批量导入', NULL, NULL, 164, 'ACTION', 3, 1),
(213, 156, 'item:balance:export', '库存余额导出',     NULL, NULL, 165, 'ACTION', 4, 1);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(260, 1, 164, 1), (261, 1, 165, 1);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(362, 1, 212, 1), (363, 1, 213, 1);

-- OPS_ADMIN(5) 补绑：其全量动态绑定由 sys_ops_role.sql 在建库时刻生成，
-- 旧库新增种子需同步补绑（id 取当前最大值顺延，唯一索引 + NOT EXISTS 双保险幂等）
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY p.id), 5, p.id, 1
FROM sys_permission p
WHERE p.id IN (164, 165)
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 5 AND rp.permission_id = p.id);

SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_menu);
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY m.id), 5, m.id, 1
FROM sys_menu m
WHERE m.id IN (212, 213)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.role_id = 5 AND rm.menu_id = m.id);
