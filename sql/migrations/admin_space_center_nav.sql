-- ============================================================================
-- 管理员端统一导航整合（2026-10-23）：
-- 「场地预约」管理组（3000）下的预约管理/预约审批并入「空间管理」组（1001），
-- 1001 更名「空间场地管理」；新增「综合管理」聚合视图入口（页签同屏切换）；
-- 空目录 3000 停用保留（回滚：enabled 改回 1 并还原 parent_id/sort_order 即可）。
-- 幂等：可重复执行；UPDATE 自然幂等，INSERT IGNORE 跳过已存在主键。
-- ============================================================================
USE lab_management;

UPDATE sys_menu SET menu_name = '空间场地管理' WHERE id = 1001;
UPDATE sys_menu SET parent_id = 1001, sort_order = 3 WHERE id = 3001;
UPDATE sys_menu SET parent_id = 1001, sort_order = 4 WHERE id = 3002;
UPDATE sys_menu SET sort_order = 2 WHERE id = 1002;
UPDATE sys_menu SET enabled = 0 WHERE id = 3000;

INSERT IGNORE INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key,
                      permission_id, menu_type, sort_order, enabled, create_user_id, update_user_id)
VALUES (1010, 1001, 'space_center', '综合管理', '/admin/space/center', 'admin/space/SpaceCenter',
        NULL, 'PAGE', 1, 1, 1, 1);

-- sys_role_menu.id 无自增无默认值，必须显式给 id（1010 未被占用）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled, create_user_id, update_user_id)
VALUES (1010, 1, 1010, 1, 1, 1);
