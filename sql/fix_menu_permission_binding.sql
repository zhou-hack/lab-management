-- ============================================================================
-- 迁移：修复菜单 permission_id 悬空绑定（2026-10-22）
-- ============================================================================
-- 背景：编号对齐（migrate_ids_align_remote）后，以下菜单的 permission_id
--       仍指向旧编号（163/165/166/160/161），在 sys_permission 中不存在
--       （悬空），导致 selectUserMenus 联查 permission_code 为 NULL：
--         * 217「我的设备申请」→ 应为 173 equipment:booking:list_own（本人查询）
--         * 218「设备申请初审」→ 应为 175 equipment:booking:teacher_review（教师初审）
--         * 219「设备使用审批」→ 应为 176 equipment:booking:admin_review（管理员终审）
--         * 220/221 设备导入/导出 → 应为 170/171 equipment:asset:import/export
-- 修复后前端路由守卫按 meta.perms 校验：教师端（初审）仅 teacher_review
-- 权限者可见，学生端页面由对应权限门控，角色权限边界明确。
-- 幂等：仅当仍为旧编号时更新，重复执行无副作用。
-- ============================================================================
SET NAMES utf8mb4;

UPDATE sys_menu SET permission_id = 173 WHERE id = 217 AND permission_id = 163;
UPDATE sys_menu SET permission_id = 175 WHERE id = 218 AND permission_id = 165;
UPDATE sys_menu SET permission_id = 176 WHERE id = 219 AND permission_id = 166;
UPDATE sys_menu SET permission_id = 170 WHERE id = 220 AND permission_id = 160;
UPDATE sys_menu SET permission_id = 171 WHERE id = 221 AND permission_id = 161;
