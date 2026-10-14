-- ============================================================================
-- 实验室设备管理系统 · 统一单据与审批建表脚本（实施分组 B）
-- 依据 docs/数据库设计.md（评审稿 v1.0）4.4 节。
-- ----------------------------------------------------------------------------
-- 内容：
--   1. biz_document 统一单据表（单号唯一；owner + 类型 + 幂等键唯一）；
--   2. approval_task 审批阶段待办表（单据+阶段+序号+轮次唯一；
--      WAITING/READY/APPROVED/REJECTED/CANCELLED）；
--   3. document_event 单据事件表（不可变过程历史，单据+请求键唯一）；
--   4. 为 user_booking_restriction 补充 source_document_id 外键
--      （sys_rbac.sql 头注约定：跨模块引用只建列，外键由单据脚本补充）。
-- ----------------------------------------------------------------------------
-- 设计要点（评审稿 4.4 节文首约定）：
--   * 业务表 status 是当前业务状态的唯一来源，biz_document 不另存一份 status；
--   * approval_task 是阶段待办，document_event 是不可变过程历史；
--   * 代批保存实际审批人和原 assigned_user_id；超时自动通过须先重新校验；
--   * 三表为追加型历史数据，deleted 恒 0，业务上不执行逻辑删除
--     （列保留仅为与公共实体基类对齐）；
--   * 本脚本为内部域，无权限与菜单初始数据。
-- 执行前提：
--   * MySQL 8.4.11 LTS，InnoDB / utf8mb4 / utf8mb4_0900_ai_ci；
--   * 必须先执行 sys_rbac.sql（依赖 sys_user / sys_department / sys_role /
--     user_booking_restriction）；空间外键依赖 固定设备.sql 时序上无强制，
--   但建议按 sys_rbac → 固定设备 → document_base 顺序执行；
--   * 服务端与连接会话时区均为 +00:00；
--   * 审计字段 create_user_id/update_user_id 统一不建外键；
--   * policy_id 为跨模块引用（business_policy 表属后续模块），只建列与索引，
--     不建外键，悬挂不校验。
-- ============================================================================
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. biz_document 统一单据表
--    来源人归属（owner_user_id）与经办人分开；幂等键由客户端提供，
--    不传时服务端生成 UUID，撞唯一键时返回原单。
-- ----------------------------------------------------------------------------
CREATE TABLE biz_document (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_no     VARCHAR(64)  NOT NULL                COMMENT '单据编号（前缀+雪花，唯一）',
    document_type   VARCHAR(32)  NOT NULL                COMMENT '单据类型：EQUIPMENT_BOOKING（后续扩展 LAB_BOOKING 等）',
    owner_user_id   BIGINT       NOT NULL                COMMENT '归属用户（→ sys_user.id）',
    department_id   BIGINT       NOT NULL                COMMENT '归属部门（→ sys_department.id）',
    policy_id       BIGINT       NULL                    COMMENT '审批策略（→ business_policy.id，后续模块，暂不建外键）',
    submitted_at    DATETIME(3)  NULL                    COMMENT '提交时间（UTC）',
    idempotency_key VARCHAR(128) NOT NULL                COMMENT '幂等键（客户端提供，缺省服务端生成 UUID）',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_document_no (document_no),
    UNIQUE KEY uk_biz_document_idem (owner_user_id, document_type, idempotency_key),
    KEY idx_biz_document_policy (policy_id),
    KEY idx_biz_document_owner (owner_user_id, document_type),
    KEY idx_biz_document_department (department_id),
    CONSTRAINT fk_biz_document_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_biz_document_department FOREIGN KEY (department_id) REFERENCES sys_department (id),
    CONSTRAINT chk_biz_document_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一单据表（所有审批型业务共用）';

-- ----------------------------------------------------------------------------
-- 2. approval_task 审批阶段待办表
--    每个审批阶段一行；上一阶段通过后服务端将下一阶段 WAITING→READY；
--    timeout_action 本期固定 'NONE'（定时催办/超时流转属后续 scheduled 模块）。
-- ----------------------------------------------------------------------------
CREATE TABLE approval_task (
    id                     BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id            BIGINT       NOT NULL                COMMENT '所属单据（→ biz_document.id）',
    stage_code             VARCHAR(64)  NOT NULL                COMMENT '审批阶段：TEACHER_REVIEW/ADMIN_REVIEW',
    sequence_no            INT          NOT NULL                COMMENT '阶段顺序号（从 1 起）',
    round_no               INT          NOT NULL                COMMENT '审批轮次（默认 1，驳回重报递增）',
    required_role          VARCHAR(64)  NOT NULL                COMMENT '要求角色码（→ sys_role.role_code）',
    assigned_user_id       BIGINT       NULL                    COMMENT '指定审批人（→ sys_user.id；NULL=按角色+范围匹配）',
    assigned_department_id BIGINT       NULL                    COMMENT '指定审批部门（→ sys_department.id）',
    status                 VARCHAR(32)  NOT NULL                COMMENT '任务状态：WAITING/READY/APPROVED/REJECTED/CANCELLED',
    available_at           DATETIME(3)  NOT NULL                COMMENT '任务生效时间（UTC，READY 时可操作）',
    remind_at              DATETIME(3)  NULL                    COMMENT '催办时间（UTC）',
    escalate_at            DATETIME(3)  NULL                    COMMENT '升级时间（UTC）',
    timeout_at             DATETIME(3)  NULL                    COMMENT '超时时间（UTC）',
    timeout_action         VARCHAR(32)  NOT NULL                COMMENT '超时动作：本期固定 NONE',
    decided_by             BIGINT       NULL                    COMMENT '实际审批人（→ sys_user.id；代批时与 assigned 不同）',
    decided_at             DATETIME(3)  NULL                    COMMENT '审批时间（UTC）',
    decision_comment       TEXT         NULL                    COMMENT '审批意见（驳回必填）',
    create_time            DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time            DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted                TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
    create_user_id         BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id         BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_task (document_id, stage_code, sequence_no, round_no),
    KEY idx_approval_task_assignee (assigned_user_id, status, available_at),
    KEY idx_approval_task_dept_role (assigned_department_id, required_role, status),
    KEY idx_approval_task_status_timeout (status, timeout_at),
    KEY idx_approval_task_document (document_id),
    CONSTRAINT fk_approval_task_document FOREIGN KEY (document_id) REFERENCES biz_document (id),
    CONSTRAINT fk_approval_task_role FOREIGN KEY (required_role) REFERENCES sys_role (role_code),
    CONSTRAINT fk_approval_task_assigned_user FOREIGN KEY (assigned_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_approval_task_assigned_dept FOREIGN KEY (assigned_department_id) REFERENCES sys_department (id),
    CONSTRAINT fk_approval_task_decided_by FOREIGN KEY (decided_by) REFERENCES sys_user (id),
    CONSTRAINT chk_approval_task_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批阶段待办表（阶段流转）';

-- ----------------------------------------------------------------------------
-- 3. document_event 单据事件表（不可变过程历史）
--    状态变更、代批、超时自动通过留痕；SYSTEM 操作不能冒充管理员；
--    request_key 为本次操作请求键，与单据组合唯一防重复写入。
-- ----------------------------------------------------------------------------
CREATE TABLE document_event (
    id              BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    document_id     BIGINT       NOT NULL                COMMENT '所属单据（→ biz_document.id）',
    event_type      VARCHAR(32)  NOT NULL                COMMENT '事件类型：SUBMIT/APPROVE/REJECT/CANCEL/CHECKOUT/RETURN/... ',
    from_status     VARCHAR(32)  NULL                    COMMENT '变更前业务状态',
    to_status       VARCHAR(32)  NULL                    COMMENT '变更后业务状态',
    actor_id        BIGINT       NULL                    COMMENT '操作人（→ sys_user.id；系统事件为 NULL）',
    actor_type      VARCHAR(32)  NOT NULL                COMMENT '操作人类型：USER/SYSTEM',
    occurred_at     DATETIME(3)  NOT NULL                COMMENT '发生时间（UTC）',
    reason          TEXT         NULL                    COMMENT '事由/备注',
    task_id         BIGINT       NULL                    COMMENT '关联审批任务（→ approval_task.id）',
    request_key     VARCHAR(128) NOT NULL                COMMENT '请求键（单次操作唯一，防事件重复写入）',
    create_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time     DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：恒 0（追加型历史，不逻辑删除）',
    create_user_id  BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id  BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_event (document_id, request_key),
    KEY idx_document_event_type (document_id, event_type, occurred_at),
    CONSTRAINT fk_document_event_document FOREIGN KEY (document_id) REFERENCES biz_document (id),
    CONSTRAINT fk_document_event_actor FOREIGN KEY (actor_id) REFERENCES sys_user (id),
    CONSTRAINT fk_document_event_task FOREIGN KEY (task_id) REFERENCES approval_task (id),
    CONSTRAINT chk_document_event_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单据事件表（不可变过程历史）';

-- ----------------------------------------------------------------------------
-- 4. 为 user_booking_restriction 补充单据外键
--    sys_rbac.sql 建表时 source_document_id 只建列与索引，头注约定由
--    单据脚本补充外键（RESTRICT，不级联删除）。
-- ----------------------------------------------------------------------------
ALTER TABLE user_booking_restriction
    ADD CONSTRAINT fk_user_booking_restriction_document
        FOREIGN KEY (source_document_id) REFERENCES biz_document (id);
