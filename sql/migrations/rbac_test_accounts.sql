-- ============================================================================
-- 迁移脚本：教师/学生测试账号建档与角色绑定
-- 业务说明（2026-10-22）：为登录与权限联调提供教师、学生各一个测试账号。
--   遵循 RBAC 定稿：账号身份由预录名单派生，客户端不得指定角色；
--   ASSISTANT 不持久化，由有效 assistant_grant 动态计算，本脚本不涉及。
-- 账号清单（密码符合「8-64 位且同时包含字母和数字」规则）：
--   教师  teacher01 / Teacher2026  工号 T20260001  角色 TEACHER(id=2)
--   学生  student01 / Student2026  学号 S20260001  角色 STUDENT(id=3)
--   两账号均挂靠部门「设备处」(id=1)，手机号 13800000001/13800000002 为占位值。
-- 幂等：可重复执行；已存在的主键/唯一键记录跳过，不覆盖已修改的密码散列。
-- 注意：仅用于 dev/test 环境联调；生产部署前必须删除或替换为真实名单建档。
-- ============================================================================

-- 1) 预录名单（身份派生来源）
INSERT IGNORE INTO sys_person_roster
  (id, person_no, person_type, real_name, phone, department_id, enabled, create_user_id, update_user_id)
VALUES
  (1, 'T20260001', 'TEACHER', '测试教师', '13800000001', 1, 1, 1, 1),
  (2, 'S20260001', 'STUDENT', '测试学生', '13800000002', 1, 1, 1, 1);

-- 2) 用户建档（password_hash 为上述初始密码的 BCrypt 散列）
INSERT IGNORE INTO sys_user
  (id, roster_id, user_type, real_name, account_name, phone, person_no, department_id,
   password_hash, failed_login_count, enabled, create_user_id, update_user_id)
VALUES
  (2, 1, 'TEACHER', '测试教师', 'teacher01', '13800000001', 'T20260001', 1,
   '$2a$10$y0/lNCt69ojqaIBHUxpuKOoY6wqkKKWbCAOjvCvl2xy90bX2x4CAq', 0, 1, 1, 1),
  (3, 2, 'STUDENT', '测试学生', 'student01', '13800000002', 'S20260001', 1,
   '$2a$10$vXr2ZDi.6Q1Y8Kw/YSB3ROMdZrdirT/32D3uqfxbWmaGr/UNRRzRm', 0, 1, 1, 1);

-- 3) 角色绑定（教师→TEACHER id=2；学生→STUDENT id=3）
INSERT IGNORE INTO sys_user_role
  (id, user_id, role_id, enabled, create_user_id, update_user_id)
VALUES
  (3, 2, 2, 1, 1, 1),
  (4, 3, 3, 1, 1, 1);

-- 4) 校验：两账号的角色绑定结果
-- SELECT u.account_name, u.user_type, r.role_code
-- FROM sys_user u
-- JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
-- JOIN sys_role r ON r.id = ur.role_id
-- WHERE u.id IN (2, 3) AND u.deleted = 0;
-- 预期：teacher01-TEACHER、student01-STUDENT 共 2 行
