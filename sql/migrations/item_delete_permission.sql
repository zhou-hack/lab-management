-- ============================================================================
-- 迁移脚本：item delete 权限段补登记 + 学生代理绑定改号（2026-10-20 整合）
-- 适用：建库早于 2026-10-20 整合版的旧库；新库直接执行正本（4.11 段与学生代理
--       新号已含），勿执行本脚本。
-- 内容：与正本 4.11 段及学生代理绑定段一致——
--       * delete 段：权限 138-140、菜单 200-202、绑定 238-240、菜单绑定 350-352；
--       * 学生代理改号：绑定 238-239 → 249-250、菜单绑定 350 → 361
--         （旧号与 delete 段撞号，2026-10-20 整合改号）。
-- 前置：若运行库菜单 202/204 为 equipment 占用（menu_code=C_EQUIPMENT_ADMIN /
--       F_EQUIPMENT_ASSET_CREATE），先执行 equipment_repair_renumber.sql 释放 202。
-- 幂等：目标编号先 DELETE 再 INSERT，可安全重放；菜单按 menu_code 守卫只动 item 侧。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_delete_permission.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 清理目标编号（含学生代理旧号 238-239/350；delete 段重放）。
--    注意：OPS_ADMIN(5) 由 sys_ops_role.sql 动态绑定了库内全部权限/菜单，
--    删除目标权限/菜单前必须先解除其指向绑定，否则触发外键 1451；
--    菜单 202 可能为 equipment 占用（旧库 C_EQUIPMENT_ADMIN），解绑按 menu_code
--    守卫只动 item 侧——若属这种情况，先执行 equipment_repair_renumber.sql 释放 202。
DELETE FROM sys_role_menu WHERE id IN (350, 351, 352, 361);
DELETE FROM sys_role_permission WHERE id IN (238, 239, 240, 249, 250);
DELETE FROM sys_role_menu WHERE role_id = 5 AND menu_id IN (200, 201);
DELETE rm FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE rm.role_id = 5 AND rm.menu_id = 202
   AND m.menu_code IN ('item:category:delete', 'item:material:delete', 'item:location:delete');
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id IN (138, 139, 140);
DELETE FROM sys_menu WHERE id IN (200, 201, 202)
  AND menu_code IN ('item:category:delete', 'item:material:delete', 'item:location:delete');
DELETE FROM sys_permission WHERE id IN (138, 139, 140);

-- 2) delete 段种子（与正本 4.11 一致）
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(138, 'item:category:delete', '物料分类删除', 'item', 1),
(139, 'item:material:delete', '物料档案删除', 'item', 1),
(140, 'item:location:delete', '库位删除',     'item', 1);

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(200, 152, 'item:category:delete', '物料分类删除', NULL, NULL, 138, 'ACTION', 5, 1),
(201, 153, 'item:material:delete', '物料档案删除', NULL, NULL, 139, 'ACTION', 5, 1),
(202, 154, 'item:location:delete', '库位删除',     NULL, NULL, 140, 'ACTION', 5, 1);

INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(238, 1, 138, 1), (239, 1, 139, 1), (240, 1, 140, 1);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(350, 1, 200, 1), (351, 1, 201, 1), (352, 1, 202, 1);

-- 3) 学生代理新号（与正本一致：249-250/361）
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(249, 3, 122, 1), (250, 3, 126, 1);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(361, 3, 205, 1);

-- 4) 为 OPS_ADMIN(5) 补绑本次新增权限/菜单（其全量动态绑定在第 1 步被解除后
--    不会自动恢复；id 取当前最大值顺延，唯一索引 + NOT EXISTS 双保险幂等）
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY p.id), 5, p.id, 1
FROM sys_permission p
WHERE p.id IN (138, 139, 140)
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 5 AND rp.permission_id = p.id);

SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_menu);
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY m.id), 5, m.id, 1
FROM sys_menu m
WHERE m.id IN (200, 201, 202)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.role_id = 5 AND rm.menu_id = m.id);

-- 5) 校验：以下查询应符合预期
-- SELECT COUNT(*) FROM sys_permission WHERE id IN (138,139,140);            -- 预期 3
-- SELECT COUNT(*) FROM sys_role_permission WHERE id IN (238,239,240,249,250); -- 预期 5
-- SELECT COUNT(*) FROM sys_role_menu WHERE id IN (350,351,352,361);          -- 预期 4
-- SELECT COUNT(*) FROM sys_role_permission WHERE role_id=5 AND permission_id IN (138,139,140); -- 预期 3
-- SELECT COUNT(*) FROM sys_role_menu WHERE role_id=5 AND menu_id IN (200,201,202);             -- 预期 3
