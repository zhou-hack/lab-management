-- ============================================================================
-- roles 模块：OPS_ADMIN 菜单收敛为系统管理子树（幂等，可重复执行）
-- 版本：2026-10-22；背景：OPS_ADMIN 菜单原为库内全部启用项的动态绑定，
-- 导致运维账号可见场地预约、设备申请初审、设备使用审批等业务入口。
-- 产品定稿：功能归属业务角色，运维不操作业务；OPS_ADMIN 权限绑定维持全量
-- （兜底不受影响），仅菜单收敛为系统管理子树（目录 1 及其子页）。
-- 与 sql/sys_ops_role.sql 正本同步（2026-10-22 修订）。
-- 全新建库无需执行（正本已为新口径）。
-- ============================================================================

-- 菜单绑定整组重建为系统管理子树（先删后插保证幂等）
DELETE FROM sys_role_menu WHERE role_id = 5;

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 5000 + ROW_NUMBER() OVER (ORDER BY id), 5, id, 1
FROM sys_menu
WHERE enabled = 1 AND deleted = 0
  AND (id = 1 OR parent_id = 1);

-- 校验（预期：仅菜单 1 系统管理及其子页 2/4/5/6/8/9/10）
-- SELECT rm.menu_id, m.menu_name
-- FROM sys_role_menu rm JOIN sys_menu m ON m.id = rm.menu_id
-- WHERE rm.role_id = 5 ORDER BY rm.menu_id;
