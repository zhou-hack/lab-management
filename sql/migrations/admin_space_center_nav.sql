-- ============================================================================
-- 管理员端导航整合（2026-10-23 定稿）：
-- 一级目录 1001 更名「场地中心」，下辖三个页面：
--   场地管理(1002) / 预约管理(3001) / 预约审批(3002)
-- 原「场地预约」空目录(3000)停用保留；聚合页「综合管理」(1010) 废弃，
-- 菜单与角色绑定逻辑删除（前端组件与路由白名单同步移除）。
-- 幂等：可重复执行；UPDATE 自然幂等，不依赖插入顺序。
-- ============================================================================
USE lab_management;

UPDATE sys_menu SET menu_name = '场地中心' WHERE id = 1001;
UPDATE sys_menu SET parent_id = 1001, sort_order = 1 WHERE id = 1002;
UPDATE sys_menu SET parent_id = 1001, sort_order = 2 WHERE id = 3001;
UPDATE sys_menu SET parent_id = 1001, sort_order = 3 WHERE id = 3002;
UPDATE sys_menu SET enabled = 0 WHERE id = 3000;

-- 废弃聚合页「综合管理」：逻辑删除菜单及角色绑定
UPDATE sys_role_menu SET deleted = 1, enabled = 0 WHERE menu_id = 1010;
UPDATE sys_menu SET deleted = 1, enabled = 0 WHERE id = 1010;
