-- ============================================================================
-- 迁移：教师/学生端设备目录分类树只读权限（2026-10-22）
-- ============================================================================
-- 背景：设备目录页（EquipmentDirectory）挂载即加载分类树
--       GET /equipment/categories（权限 equipment:category:list=141）做
--       分类筛选/分组。该权限此前仅 ADMIN 持有，学生/教师访问时接口返回
--       403 → 前端统一弹「无权限」。
-- 修复：给 TEACHER(2)、STUDENT(3) 角色绑定只读分类查询权限（不授管理权）。
-- 编号：sys_role_permission 558/559（545-557 已用于设备申请权限，之后空闲）。
-- 幂等：仅当不存在时插入，可重复执行。
-- ============================================================================
SET NAMES utf8mb4;

INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 558, 2, 141, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 558);

INSERT INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 559, 3, 141, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission WHERE id = 559);
