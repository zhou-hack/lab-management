-- ============================================================================
-- 迁移脚本：equipment 报修权限与设备菜单改号（2026-10-20 整合冲突裁决）
-- 适用：已执行 2026-10-19 版正本（报修权限 160-161、设备菜单 202/204、
--       绑定 529-532/608/610）的旧库；新库直接执行正本（已为新号），勿执行本脚本。
-- 背景：2026-10-20 整合 material-manage 分支时编号冲突裁决——
--       * 报修权限 160-161 与 item 批量导入导出（160-163，运行库已启用）撞号，
--         改号 168-169（160-161 对 equipment 弃用，item 的 160-163 不动）；
--       * 设备菜单 202/204 与 item delete 菜单 200-202 撞号，移至 210-211。
-- 幂等：按 module_code / menu_code 守卫只动 equipment 侧记录；目标新号先 DELETE
--       再 INSERT，可安全重放。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/equipment_repair_renumber.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 报修权限改号 160-161 → 168-169（只删 module_code='equipment' 的旧号，item 的 160-161 不动）。
--    注意：OPS_ADMIN(5) 由 sys_ops_role.sql 动态绑定了库内全部权限/菜单，
--    删除目标权限/菜单前必须先解除其指向绑定，否则触发外键 1451；
--    解绑同样按 module_code 守卫——若 160-161 已被 item 导入导出占用，ops 对其绑定不动。
DELETE FROM sys_role_permission WHERE id IN (529, 530, 531, 532);
DELETE FROM sys_role_permission WHERE role_id = 5 AND permission_id IN (168, 169);
DELETE rp FROM sys_role_permission rp
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE rp.role_id = 5 AND rp.permission_id IN (160, 161) AND p.module_code = 'equipment';
DELETE FROM sys_permission WHERE id IN (168, 169);
DELETE FROM sys_permission WHERE id IN (160, 161) AND module_code = 'equipment';

INSERT INTO sys_permission (id, permission_code, permission_name, module_code) VALUES
(168, 'equipment:repair:create',   '设备报修登记', 'equipment'),
(169, 'equipment:repair:list',     '设备报修查询', 'equipment');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(529, 2, 168),
(530, 2, 169),
(531, 3, 168),
(532, 3, 169);

-- 2) 设备菜单改号 202/204 → 210/211（按 menu_code 守卫只删 equipment 侧；
--    先删角色绑定与子菜单，再删父菜单；item delete 菜单 200-202 及其绑定不动）
DELETE FROM sys_role_menu WHERE id IN (608, 610);
DELETE FROM sys_role_menu WHERE role_id = 5 AND menu_id IN (210, 211);
DELETE rm FROM sys_role_menu rm
  JOIN sys_menu m ON m.id = rm.menu_id
 WHERE rm.role_id = 5 AND rm.menu_id IN (202, 204)
   AND m.menu_code IN ('C_EQUIPMENT_ADMIN', 'F_EQUIPMENT_ASSET_CREATE');
-- sys_menu 自引用外键 fk_sys_menu_parent：先删子菜单后删父菜单，
-- 单条批量 DELETE 在父行先处理时会报 1451，拆条保证顺序
DELETE FROM sys_menu WHERE id = 211;
DELETE FROM sys_menu WHERE id = 210;
DELETE FROM sys_menu WHERE id = 204 AND menu_code = 'F_EQUIPMENT_ASSET_CREATE';
DELETE FROM sys_menu WHERE id = 202 AND menu_code = 'C_EQUIPMENT_ADMIN';

INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order) VALUES
(210, NULL, 'C_EQUIPMENT_ADMIN',    '设备管理', '/admin/equipment',         'EquipmentManage', 149,  'PAGE',      20),
(211, 210,  'F_EQUIPMENT_ASSET_CREATE', '设备登记', NULL, NULL, 150, 'ACTION', 1);

INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES
(608, 1, 210),
(610, 1, 211);

-- 3) 为 OPS_ADMIN(5) 补绑本次新增权限/菜单（其全量动态绑定在删除步骤被解除后
--    不会自动恢复；id 取当前最大值顺延，唯一索引 + NOT EXISTS 双保险幂等）
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY p.id), 5, p.id, 1
FROM sys_permission p
WHERE p.id IN (168, 169)
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 5 AND rp.permission_id = p.id);

SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_menu);
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY m.id), 5, m.id, 1
FROM sys_menu m
WHERE m.id IN (210, 211)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.role_id = 5 AND rm.menu_id = m.id);

-- 4) 校验：以下查询应符合预期
-- SELECT id, permission_code FROM sys_permission WHERE id IN (160,161,168,169);
--   -- 预期：160-161 仅可能出现 item 的导入导出码，equipment:repair:* 只在 168-169
-- SELECT id, menu_code FROM sys_menu WHERE id IN (202,204,210,211);
--   -- 预期：C_EQUIPMENT_ADMIN / F_EQUIPMENT_ASSET_CREATE 只在 210-211
