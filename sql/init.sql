-- ============================================================================
-- 实验室设备管理系统 · 一键初始化脚本（入口）
-- ----------------------------------------------------------------------------
-- 用途：获取数据库凭据后，在全新环境执行本文件即可完成建库、建表（含索引）
--   与初始数据装载，直接用于真实联调。表结构与初始数据的权威定义分别在
--   sql/sys_rbac.sql（RBAC 11 表，已定稿）与 sql/biz_space.sql（场地模块），
--   本脚本不复制其内容，避免两处维护。
-- ----------------------------------------------------------------------------
-- 执行方式（在仓库根目录启动 mysql 客户端，source 相对路径以工作目录为基准）：
--   mysql -u <用户名> -p < sql/init.sql
--   或客户端内：SOURCE sql/init.sql;
-- 图形客户端不支持 SOURCE 时，按顺序手工执行：
--   1. 本文件头部的建库语句
--   2. sql/sys_rbac.sql
--   3. sql/sys_policy.sql
--   4. sql/assistant_grant.sql
--   5. sql/biz_space.sql
--   6. sql/lab_booking.sql
-- ----------------------------------------------------------------------------
-- 幂等性：本脚本与各被引用脚本均为全新环境设计（CREATE TABLE 非
--   IF NOT EXISTS、INSERT 固定小 ID），不可重复执行；重置环境先执行：
--   DROP DATABASE lab_management;（会清除全部业务数据，慎用）
-- ----------------------------------------------------------------------------
-- 环境要求：
--   * MySQL 8.4.11 LTS，服务端与连接会话时区均为 +00:00
--     （Connector/J 参数 connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true，
--     见 docs/development.md「后端与数据库」节）；
--   * 应用连接库名与凭据见 application-dev.yml（环境变量 DB_USERNAME/DB_PASSWORD
--     覆盖，默认 root/root，部署前必须替换真实凭据并启用密码策略）；
--   * 初始管理员 admin（id=1）密码哈希为占位值，联调前必须替换并强制首登修改
--     （见 sys_rbac.sql 初始数据说明）。
-- ============================================================================

-- 1. 建库（utf8mb4 与定稿脚本排序规则一致）
CREATE DATABASE IF NOT EXISTS lab_management
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE lab_management;

-- 2. RBAC 权限体系（11 表：组织/名单/用户/角色/权限/菜单/三组关联/数据范围/预约限制
--    + 预置部门、4 角色、管理员 admin 及其角色绑定与 ALL+WRITE 数据范围）
SOURCE sql/sys_rbac.sql;

-- 3. 业务策略参数（business_policy 单表 + 认证安全策略 9 条）
SOURCE sql/sys_policy.sql;

-- 4. 助理授权（assistant_grant 两表，ASSISTANT 动态角色依据，无初始数据）
SOURCE sql/assistant_grant.sql;

-- 5. 场地模块（biz_space 单表 + 场地 3 条、权限 1001-1005、菜单 1001-1005、
--    角色权限与角色菜单授权、sys_user_data_scope 跨模块外键补充）
SOURCE sql/biz_space.sql;

-- 6. 场地预约（lab_booking + lab_slot 两表 + admin 演示密码重置 + 预约权限 3001-3007、
--    菜单 3201-3205 与角色绑定；lab_usage 使用台账延后）
SOURCE sql/lab_booking.sql;

-- 7. 初始化自检（预期值见行尾注释；任何一列不符说明初始化不完整）
SELECT COUNT(*) AS dept_cnt FROM sys_department;                          -- 预期 1（设备处）
SELECT COUNT(*) AS role_cnt FROM sys_role;                                -- 预期 4（ADMIN/TEACHER/STUDENT/ASSISTANT）
SELECT COUNT(*) AS user_cnt FROM sys_user;                                -- 预期 1（admin）
SELECT COUNT(*) AS policy_cnt FROM business_policy WHERE deleted = 0;     -- 预期 9（认证安全策略）
SELECT COUNT(*) AS space_cnt FROM biz_space WHERE deleted = 0;            -- 预期 3（两实训室+一教室）
SELECT COUNT(*) AS space_perm_cnt FROM sys_permission WHERE module_code = 'space'; -- 预期 5
SELECT COUNT(*) AS space_menu_cnt FROM sys_menu WHERE menu_code LIKE 'space%';      -- 预期 5
SELECT COUNT(*) AS booking_perm_cnt FROM sys_permission WHERE module_code = 'booking'; -- 预期 7
SELECT COUNT(*) AS booking_menu_cnt FROM sys_menu WHERE menu_code LIKE 'booking%';    -- 预期 5
SELECT COUNT(*) AS role_perm_cnt FROM sys_role_permission;                -- 预期 43（RBAC 26 + 场地 9 + 预约 8）
SELECT COUNT(*) AS booking_cnt FROM lab_booking;                         -- 预期 0（无初始预约）
SELECT COUNT(*) AS slot_cnt FROM lab_slot;                                -- 预期 0（无初始占用）
