-- ============================================================================
-- system 模块：人员管理统一视图 + 预约限制下线（幂等，可重复执行）
-- 版本：2026-10-21；来源：dev/basic_model 3d6c3b9（人员统一视图）与 15cdfa6（预约限制下线）
-- 适用：2026-10-20 基线（sys_rbac 31 项权限 + 12 菜单）之前建成的库。
-- 内容：
--   1. 菜单 2「用户管理」→「人员管理」（component_key SystemUserRoster，路由不变）；
--   2. 删除菜单 3「名单管理」（合并进人员管理，编号弃用不复用）；
--   3. 删除菜单 7「预约限制」与权限 17-19 system:restriction:*（页面与接口已下线，
--      user_booking_restriction 表与 UserBookingRestrictionMapper 保留供预约模块逾期冻结）。
-- 全新建库无需执行（正本 sys_rbac.sql 已含上述形态）。
-- ============================================================================

-- 1) 菜单 2 改人员管理统一视图
UPDATE sys_menu
SET menu_name = '人员管理', component_key = 'SystemUserRoster'
WHERE id = 2 AND menu_code = 'system_user';

-- 2) 回收指向待删菜单/权限的角色绑定（含 OPS_ADMIN 动态绑定段 5000+，防外键 1451）
DELETE FROM sys_role_menu WHERE menu_id IN (3, 7);
DELETE FROM sys_role_permission WHERE permission_id IN (17, 18, 19);

-- 3) 删除名单管理与预约限制菜单、预约限制权限（编号弃用不复用）
DELETE FROM sys_menu WHERE id IN (3, 7);
DELETE FROM sys_permission WHERE id IN (17, 18, 19)
  AND permission_code IN ('system:restriction:list', 'system:restriction:create', 'system:restriction:revoke');

-- 4) 校验（预期：均为 0 行 / 菜单 2 为人员管理）
-- SELECT id, menu_name, component_key FROM sys_menu WHERE id IN (2, 3, 7);
-- SELECT id, permission_code FROM sys_permission WHERE id IN (17, 18, 19);
-- SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (3, 7);
-- SELECT COUNT(*) FROM sys_role_permission WHERE permission_id IN (17, 18, 19);
