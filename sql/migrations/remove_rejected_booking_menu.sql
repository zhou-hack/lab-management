-- ============================================================================
-- 迁移脚本：删除学生端「查看驳回申请」独立菜单（2026-10-22 新增后回滚）
-- 需求：菜单下线，学生端不再展示「查看驳回申请」入口；
--       复用接口与权限（/equipment/bookings/my?status=REJECTED、173）保留，
--       RejectedBookings 页面组件/测试文件与前端白名单条目已删除。
-- 幂等：按固定 ID 删除，可重复执行；不存在时 DELETE 影响 0 行，不影响其他数据。
-- ============================================================================

-- 1) 删除学生端角色菜单绑定（STUDENT=3，menu_id=222）
DELETE FROM sys_role_menu
WHERE id = 624
   OR (role_id = 3 AND menu_id = 222);

-- 2) 删除菜单记录（sys_menu 222：查看驳回申请）
DELETE FROM sys_menu
WHERE id = 222;

-- 3) 校验：不应再存在该菜单及其角色绑定
-- SELECT id, menu_code, menu_name, route_path FROM sys_menu WHERE id = 222;
-- SELECT id, role_id, menu_id FROM sys_role_menu WHERE menu_id = 222;
-- 预期：均为 0 行
