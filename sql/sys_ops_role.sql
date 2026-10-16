-- ============================================================================
-- 实验室设备管理系统 · 运维管理员角色增量脚本
-- 依据 docs/数据库设计.md（评审稿 v1.1）与 docs/RBAC实施清单.md；归属 roles 模块。
-- ----------------------------------------------------------------------------
-- 设计要点：
--   1. OPS_ADMIN 为预置最高级运维角色（id=5），不开放页面创建，仅本脚本预置；
--      普通 ADMIN 无法通过用户管理创建或提升为运维管理员（createAdmin 固定绑 ADMIN）；
--   2. 权限与菜单为执行时刻库内全部启用项（INSERT...SELECT 动态绑定，ID 段
--      5000+ 避免与模块脚本固定小 ID 冲突）；后续模块新增权限随各自脚本同步绑定；
--   3. 运维管理员面向全系统数据与配置，不受 sys_user_data_scope 数据范围约束
--      （实现层豁免见 SysScopeServiceImpl.isOpsAdmin）；
--   4. 预置账号 opsadmin（id=2，user_type=ADMIN，账密登录）；
--      password_hash 为占位值，部署前必须替换为 BCrypt 密文并强制首登修改。
-- 执行前提：sys_rbac.sql 与全部模块脚本已执行（依赖其角色/权限/菜单种子数据）。
--   重复执行：角色/账号/绑定按固定主键幂等（INSERT IGNORE）。
-- ============================================================================

-- 预置角色：运维管理员
INSERT IGNORE INTO sys_role (id, role_code, role_name, enabled) VALUES
(5, 'OPS_ADMIN', '运维管理员', 1);

-- 预置运维管理员账号（密码占位，部署前必须替换）
INSERT IGNORE INTO sys_user (id, roster_id, user_type, real_name, account_name, phone, person_no,
                             department_id, password_hash, failed_login_count, enabled)
VALUES (2, NULL, 'ADMIN', '运维管理员', 'opsadmin', NULL, NULL,
        1, '$2a$10$REPLACE_WITH_BCRYPT_HASH', 0, 1);

-- 账号角色绑定
INSERT IGNORE INTO sys_user_role (id, user_id, role_id, enabled) VALUES (2, 2, 5, 1);

-- 角色权限绑定：库内全部启用权限（动态 ID 段 5000+，重复执行幂等）
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT 5000 + ROW_NUMBER() OVER (ORDER BY id), 5, id, 1
FROM sys_permission
WHERE enabled = 1 AND deleted = 0;

-- 角色菜单绑定：库内全部启用菜单（动态 ID 段 5000+，重复执行幂等）
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT 5000 + ROW_NUMBER() OVER (ORDER BY id), 5, id, 1
FROM sys_menu
WHERE enabled = 1 AND deleted = 0;
