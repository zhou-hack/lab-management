-- ============================================================================
-- 实验室设备管理系统 · 业务策略参数表脚本（公共支撑）
-- 依据 docs/数据库设计.md（评审稿 v1.0）4.9 节 business_policy；随 roles 模块先行落地。
-- ----------------------------------------------------------------------------
-- 规则：码+版本唯一；同码生效区间不重叠、发布后不可修改，均由服务端校验；
--       读取按 policy_code 取"当前时间命中生效区间的最新启用版本"。
-- 执行前提：sys_rbac.sql 已执行；时区约定同 sys_rbac.sql。
-- ============================================================================

CREATE TABLE business_policy (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    policy_code     VARCHAR(64)  NOT NULL                COMMENT '策略编码',
    policy_version  INT          NOT NULL                COMMENT '版本号（同码递增，从 1 起）',
    policy_name     VARCHAR(128) NOT NULL                COMMENT '策略名称',
    effective_from  DATETIME(3)  NOT NULL                COMMENT '生效开始（UTC）',
    effective_to    DATETIME(3)  NULL                    COMMENT '生效结束（UTC，NULL=长期）',
    config          JSON         NOT NULL                COMMENT '策略参数（固定结构 JSON）',
    enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_policy_code_version (policy_code, policy_version),
    CONSTRAINT chk_business_policy_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_business_policy_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务策略参数表（版本化）';

-- ============================================================================
-- 初始数据：认证与安全策略（docs/RBAC实施清单.md 第五节决策 3）
-- ============================================================================
INSERT INTO business_policy (id, policy_code, policy_version, policy_name, effective_from, effective_to, config, enabled) VALUES
(1, 'LOGIN_MAX_FAIL',                 1, '登录连续失败锁定阈值（次）',     '2026-10-14 00:00:00.000', NULL, JSON_OBJECT('maxFailCount', 5),     1),
(2, 'LOGIN_LOCK_MINUTES',             1, '登录锁定时长（分钟）',           '2026-10-14 00:00:00.000', NULL, JSON_OBJECT('lockMinutes', 30),     1),
(4, 'SMS_CODE_TTL_SECONDS',           1, '短信验证码有效期（秒）',         '2026-10-14 00:00:00.000', NULL, JSON_OBJECT('ttlSeconds', 300),     1),
(5, 'SMS_RESEND_INTERVAL_SECONDS',    1, '短信验证码重发间隔（秒）',       '2026-10-14 00:00:00.000', NULL, JSON_OBJECT('intervalSeconds', 60), 1),
(6, 'SMS_DAILY_LIMIT',                1, '短信验证码日发送上限（条）',     '2026-10-14 00:00:00.000', NULL, JSON_OBJECT('dailyLimit', 10),      1),
(7, 'SMS_CODE_MAX_FAIL',              1, '验证码错误作废次数（次）',       '2026-10-14 00:00:00.000', NULL, JSON_OBJECT('maxFailCount', 5),     1),
(8, 'SESSION_TIMEOUT_STUDENT_TEACHER', 1, '学生/老师会话有效期（秒）',     '2026-10-14 00:00:00.000', NULL, JSON_OBJECT('timeoutSeconds', 28800), 1),
(9, 'SESSION_TIMEOUT_ADMIN',          1, '管理员会话有效期（秒）',         '2026-10-14 00:00:00.000', NULL, JSON_OBJECT('timeoutSeconds', 28800), 1);
