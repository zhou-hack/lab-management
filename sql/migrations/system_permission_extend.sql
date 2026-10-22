-- ============================================================================
-- 迁移脚本：system 模块权限扩展 28-31（2026-10-20 收编 dev/basic_model 与 dev/auth）
-- 适用：建库早于 2026-10-20 的旧库（sys_rbac 仅 27 项 system 权限）；
--      新库直接执行 sys_rbac.sql 正本（已含 28-31），勿执行本脚本。
-- 内容：28 system:role:assign（调整角色权限）、29 system:roster:export（名单导出）、
--      30 system:roster:import（名单导入）、31 system:user:bind_role（绑定管理员角色）。
-- 绑定：按 2026-10-19 需求定稿，system 操作不授 ADMIN，OPS_ADMIN(5) 动态绑定覆盖；
--      旧库 OPS_ADMIN 绑定为建库时刻生成，须同步补绑（防"权限加了但运维 403"）。
-- 幂等：INSERT IGNORE + NOT EXISTS 双保险，可重复执行。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/system_permission_extend.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 权限种子（27 reset_password 基线已有，不在本脚本范围）
INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(28, 'system:role:assign',    '调整角色权限',   'system', 1),
(29, 'system:roster:export',  '名单导出',       'system', 1),
(30, 'system:roster:import',  '名单导入',       'system', 1),
(31, 'system:user:bind_role', '绑定管理员角色', 'system', 1);

-- 2) OPS_ADMIN(5) 补绑（id 取当前最大值顺延）
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY p.id), 5, p.id, 1
FROM sys_permission p
WHERE p.id IN (28, 29, 30, 31)
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 5 AND rp.permission_id = p.id);

-- 3) 校验：以下查询应符合预期
-- SELECT COUNT(*) FROM sys_permission WHERE id IN (28,29,30,31);                          -- 预期 4
-- SELECT COUNT(*) FROM sys_role_permission WHERE role_id=5 AND permission_id IN (28,29,30,31); -- 预期 4
