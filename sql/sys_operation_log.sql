-- ============================================================================
-- 实验室设备管理系统 · 操作日志建表脚本
-- 依据 docs/数据库设计.md（评审稿 v1.0）4.9 节 sys_operation_log；归属 roles 模块（公共支撑）。
-- ----------------------------------------------------------------------------
-- 设计要点：
--   1. 只追加：deleted 固定 0，不提供业务更新/删除通道，update_time 保持创建值；
--   2. actor_id 为操作者（系统任务/匿名可空），document_id 为跨模块引用，
--      只建列与索引，外键由 biz_document 所属模块脚本补充；
--   3. 密码、验证码、token 等敏感值不记录；before_value/after_value 一期预留为 NULL；
--   4. 动作码与权限码同规范（模块:资源:操作），认证动作为 auth:login / auth:sms_login / auth:logout。
-- 执行前提：sys_rbac.sql 已执行（依赖 sys_user）；时区约定同 sys_rbac.sql。
-- ============================================================================

CREATE TABLE sys_operation_log (
    id             BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    actor_id       BIGINT       NULL                    COMMENT '操作者（→ sys_user.id；系统任务/匿名可空）',
    actor_type     VARCHAR(32)  NOT NULL                COMMENT '操作者类型：STUDENT/TEACHER/ADMIN/SYSTEM/UNKNOWN',
    document_id    BIGINT       NULL                    COMMENT '关联单据（跨模块引用，仅列+索引，外键由 biz_document 所属模块补充）',
    module_code    VARCHAR(64)  NOT NULL                COMMENT '模块码',
    action_code    VARCHAR(64)  NOT NULL                COMMENT '动作码（与权限码同规范）',
    target_table   VARCHAR(64)  NOT NULL                COMMENT '目标表',
    target_id      BIGINT       NULL                    COMMENT '目标行 ID',
    request_id     VARCHAR(128) NOT NULL                COMMENT '请求关联 ID',
    client_ip      VARCHAR(45)  NULL                    COMMENT '客户端 IP',
    before_value   JSON         NULL                    COMMENT '变更前快照（一期预留）',
    after_value    JSON         NULL                    COMMENT '变更后快照（一期预留）',
    success        TINYINT      NOT NULL                COMMENT '成功：1成功 0失败',
    occurred_at    DATETIME(3)  NOT NULL                COMMENT '发生时间（UTC）',
    create_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '更新时间（UTC，追加表保持创建值）',
    deleted        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：追加表固定 0',
    create_user_id BIGINT       NULL                    COMMENT '创建人（同 actor_id）',
    update_user_id BIGINT       NULL                    COMMENT '最后操作人（追加表不使用）',
    PRIMARY KEY (id),
    KEY idx_sys_operation_log_actor_time (actor_id, occurred_at),
    KEY idx_sys_operation_log_document_time (document_id, occurred_at),
    KEY idx_sys_operation_log_module_action (module_code, action_code),
    CONSTRAINT fk_sys_operation_log_actor FOREIGN KEY (actor_id) REFERENCES sys_user (id),
    CONSTRAINT chk_sys_operation_log_success CHECK (success IN (0, 1)),
    CONSTRAINT chk_sys_operation_log_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表（只追加，管理员审计）';
