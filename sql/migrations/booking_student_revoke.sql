-- ============================================================================
-- 迁移脚本：回收学生（role_id=3 STUDENT）的场地预约权限与菜单
-- 业务规则（2026-10-17）：场地预约仅向教师/管理员开放，学生端无任何预约入口。
-- 适用：曾执行过含「TEACHER/STUDENT 均授予 booking:lab:*」旧版种子的库。
-- 幂等：可重复执行，不影响教师（role_id=2）与管理员（role_id=1）的预约权限。
-- 注意：仅回收授权，不删除学生已产生的历史预约数据（仍可由管理员侧追溯）。
-- ============================================================================

-- 1) 回收学生的预约功能权限：申请(3001)/记录(3002)/撤销(3003)
DELETE FROM sys_role_permission
WHERE role_id = 3
  AND permission_id IN (3001, 3002, 3003);

-- 2) 回收学生的用户端「场地预约」菜单（菜单 id=3003，/booking/labs）
DELETE FROM sys_role_menu
WHERE role_id = 3
  AND menu_id = 3003;

-- 3) 校验：学生不应再有任何 booking 模块（3001-3004）授权
-- SELECT rp.role_id, rp.permission_id, p.permission_code
-- FROM sys_role_permission rp
-- JOIN sys_permission p ON p.id = rp.permission_id
-- WHERE rp.role_id = 3 AND p.module_code = 'booking';
-- 预期：0 行
