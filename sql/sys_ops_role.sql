-- ============================================================================
-- 实验室设备管理系统 · 运维管理员角色增量脚本
-- 依据 docs/数据库设计.md（评审稿 v1.0）与 docs/RBAC实施清单.md；归属 roles 模块。
-- ----------------------------------------------------------------------------
-- 设计要点：
--   1. OPS_ADMIN 为预置最高级运维角色（id=5），不开放页面创建，仅本脚本预置；
--      普通 ADMIN 无法通过用户管理创建或提升为运维管理员（createAdmin 固定绑 ADMIN）；
--   2. 权限为全部权限（当前 31 项：system 1-31），菜单与 ADMIN
--      同集（系统管理 9 页）；后续模块新增权限随各自脚本同步绑定
--      OPS_ADMIN，菜单按需登记；
--   2.1 OPS_ADMIN 角色的权限绑定锁定，不开放页面调整（40900）；其余 4 个预置角色
--      的权限绑定可由运维管理员在角色权限页整集替换（system:role:assign，仅绑
--      OPS_ADMIN）；
--   3. 运维管理员面向全系统数据与配置，不受 sys_user_data_scope 数据范围约束
--      （实现层豁免见 SysScopeServiceImpl.isOpsAdmin）；
--   4. 预置账号 opsadmin（id=2，user_type=ADMIN，8 小时会话、账密登录）；
--      password_hash 为占位值，部署前必须替换为 BCrypt 密文并强制首登修改。
-- 执行前提：sys_rbac.sql 已执行（依赖其角色/权限/菜单/部门种子数据）。
-- ============================================================================

-- 预置角色：运维管理员
INSERT INTO sys_role (id, role_code, role_name, enabled) VALUES
(5, 'OPS_ADMIN', '运维管理员', 1);

-- 预置运维管理员账号（密码占位，部署前必须替换）
INSERT INTO sys_user (id, roster_id, user_type, real_name, account_name, phone, person_no,
                      department_id, password_hash, failed_login_count, enabled)
VALUES (2, NULL, 'ADMIN', '运维管理员', 'opsadmin', NULL, NULL,
        1, '$2a$10$REPLACE_WITH_BCRYPT_HASH', 0, 1);

-- 账号角色绑定
INSERT INTO sys_user_role (id, user_id, role_id, enabled) VALUES (2, 2, 5, 1);

-- 角色权限绑定：全部权限（system 1-31，共 31 项）
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(49, 5, 1, 1), (52, 5, 2, 1), (50, 5, 3, 1), (54, 5, 4, 1), (53, 5, 5, 1),
(55, 5, 6, 1), (56, 5, 7, 1), (57, 5, 8, 1), (58, 5, 9, 1), (59, 5, 10, 1),
(60, 5, 11, 1), (61, 5, 12, 1), (62, 5, 13, 1), (27, 5, 14, 1), (63, 5, 15, 1),
(64, 5, 16, 1), (28, 5, 17, 1), (29, 5, 18, 1), (30, 5, 19, 1), (31, 5, 20, 1),
(32, 5, 21, 1), (33, 5, 22, 1), (65, 5, 23, 1), (66, 5, 24, 1), (67, 5, 25, 1),
(34, 5, 26, 1), (68, 5, 27, 1), (81, 5, 28, 1), (82, 5, 29, 1), (83, 5, 30, 1),
(87, 5, 31, 1);

-- 角色菜单绑定：与 ADMIN 同集（系统管理目录 + 9 页，共 10 项）
INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(12, 5, 1, 1), (21, 5, 2, 1), (22, 5, 3, 1), (23, 5, 4, 1), (13, 5, 5, 1),
(24, 5, 6, 1), (14, 5, 7, 1), (15, 5, 8, 1), (16, 5, 9, 1), (17, 5, 10, 1);
