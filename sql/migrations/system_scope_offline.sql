-- ============================================================================
-- system 模块：数据范围页面与接口下线（幂等，可重复执行）
-- 版本：2026-10-22；来源：dev/basic_model 15ce5b4（数据范围管理模块下线）
-- 适用：2026-10-21 基线（sys_rbac 28 项权限 + 10 菜单）之前建成的库。
-- 内容：
--   1. 删除菜单 6「数据范围」与权限 15-16 system:scope:*（页面与接口已下线，
--      编号弃用不复用）；
--   2. sys_user_data_scope 表与 UserDataScopeMapper 保留，管理员不再受数据范围约束；
--      sys_scope.sql 的管理员 ALL+WRITE 种子无害保留，无需清理。
-- 全新建库无需执行（正本 sys_rbac.sql 已含上述形态）。
-- ============================================================================
USE lab_management;
SET NAMES utf8mb4;

-- 1) 回收指向待删菜单/权限的角色绑定（含 OPS_ADMIN 动态绑定段 5000+，防外键 1451）
DELETE FROM sys_role_menu WHERE menu_id = 6;
DELETE FROM sys_role_permission WHERE permission_id IN (15, 16);

-- 2) 删除数据范围菜单与权限（编号弃用不复用）
DELETE FROM sys_menu WHERE id = 6;
DELETE FROM sys_permission WHERE id IN (15, 16)
  AND permission_code IN ('system:scope:list', 'system:scope:grant');

-- 3) 校验（预期：均为 0 行）
-- SELECT id, menu_name FROM sys_menu WHERE id = 6;
-- SELECT id, permission_code FROM sys_permission WHERE id IN (15, 16);
-- SELECT COUNT(*) FROM sys_role_menu WHERE menu_id = 6;
-- SELECT COUNT(*) FROM sys_role_permission WHERE permission_id IN (15, 16);
