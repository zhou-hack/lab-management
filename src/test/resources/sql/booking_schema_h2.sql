-- ============================================================================
-- 场地预约模块集成测试建表脚本（仅测试用，H2 MySQL 模式）。
-- 与 sql/lab_booking.sql 的差异：H2 不支持 UTC_TIMESTAMP(3) 列默认值与
-- ON UPDATE CURRENT_TIMESTAMP(3)，时间列由 MyBatis-Plus 审计填充保证；
-- 生产建表仍以 sql/lab_booking.sql（MySQL 8.4）为准。
-- 同时包含最小 sys_user（申请人姓名回填）与 lab（预约目标场地）。
-- ============================================================================

DROP TABLE IF EXISTS lab_slot;
DROP TABLE IF EXISTS lab_booking;
DROP TABLE IF EXISTS document_event;
DROP TABLE IF EXISTS approval_task;
DROP TABLE IF EXISTS biz_document;
DROP TABLE IF EXISTS lab;
DROP TABLE IF EXISTS sys_user;

-- 最小用户表（仅姓名回填所需列，结构对齐 sys_rbac.sql 的 sys_user）
CREATE TABLE sys_user (
    id                  BIGINT       NOT NULL,
    user_type           VARCHAR(32)  NOT NULL,
    roster_id           BIGINT       NULL,
    account_name        VARCHAR(64)  NOT NULL,
    phone               VARCHAR(32)  NULL,
    password_hash       VARCHAR(128) NOT NULL,
    person_no           VARCHAR(64)  NULL,
    real_name           VARCHAR(128) NOT NULL,
    department_id       BIGINT       NULL,
    enabled             TINYINT      NOT NULL DEFAULT 1,
    failed_login_count  INT          NULL,
    locked_until        TIMESTAMP(3) NULL,
    password_changed_at TIMESTAMP(3) NULL,
    create_time         TIMESTAMP(3) NOT NULL,
    update_time         TIMESTAMP(3) NOT NULL,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    create_user_id      BIGINT       NULL,
    update_user_id      BIGINT       NULL,
    PRIMARY KEY (id)
);

CREATE TABLE lab (
    id             BIGINT        NOT NULL,
    lab_name     VARCHAR(128)  NOT NULL,
    lab_type     VARCHAR(32)   NOT NULL,
    location_detail       VARCHAR(128)  NULL,
    capacity       INT           NOT NULL,
    facilities     TEXT          NULL,
    open_start     TIME          NOT NULL,
    open_end       TIME          NOT NULL,
    status         VARCHAR(32)   NOT NULL,
    create_time    TIMESTAMP(3)  NOT NULL,
    update_time    TIMESTAMP(3)  NOT NULL,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    create_user_id BIGINT        NULL,
    update_user_id BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_lab_status CHECK (status IN ('AVAILABLE', 'MAINTAINING', 'CLOSED'))
);

CREATE TABLE lab_booking (
    id                 BIGINT       NOT NULL,
    lab_id             BIGINT       NOT NULL,
    applicant_id       BIGINT       NOT NULL,
    purpose            VARCHAR(512) NOT NULL,
    participant_count  INT          NOT NULL,
    start_at           TIMESTAMP(3) NOT NULL,
    end_at             TIMESTAMP(3) NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    endorse_teacher_id BIGINT       NULL,
    endorse_at         TIMESTAMP(3) NULL,
    endorse_comment    VARCHAR(512) NULL,
    reject_reason      VARCHAR(512) NULL,
    cancel_reason      VARCHAR(512) NULL,
    cancel_type        VARCHAR(32)  NULL,
    cancelled_at       TIMESTAMP(3) NULL,
    create_time        TIMESTAMP(3) NOT NULL,
    update_time        TIMESTAMP(3) NOT NULL,
    deleted            TINYINT      NOT NULL DEFAULT 0,
    create_user_id     BIGINT       NULL,
    update_user_id     BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_lab_booking_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_lab_booking_count CHECK (participant_count > 0),
    CONSTRAINT chk_lab_booking_window CHECK (start_at < end_at)
);

CREATE INDEX idx_lab_booking_applicant ON lab_booking (applicant_id, create_time);
CREATE INDEX idx_lab_booking_lab_status ON lab_booking (lab_id, status, start_at);

CREATE TABLE lab_slot (
    id              BIGINT       NOT NULL,
    lab_id          BIGINT       NOT NULL,
    booking_id      BIGINT       NOT NULL,
    start_at        TIMESTAMP(3) NOT NULL,
    end_at          TIMESTAMP(3) NOT NULL,
    released_at     TIMESTAMP(3) NULL,
    release_reason  VARCHAR(512) NULL,
    create_time     TIMESTAMP(3) NOT NULL,
    update_time     TIMESTAMP(3) NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    create_user_id  BIGINT       NULL,
    update_user_id  BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lab_slot_booking UNIQUE (booking_id),
    CONSTRAINT chk_lab_slot_window CHECK (start_at < end_at)
);

CREATE INDEX idx_lab_slot_conflict ON lab_slot (lab_id, released_at, start_at, end_at);

-- ============================================================================
-- 权级流程三表（对应 sql/booking_flow_deploy.sql，H2 不建外键）
-- ============================================================================

CREATE TABLE biz_document (
    id              BIGINT       NOT NULL,
    document_no     VARCHAR(64)  NOT NULL,
    document_type   VARCHAR(32)  NOT NULL,
    owner_user_id   BIGINT       NOT NULL,
    department_id   BIGINT       NOT NULL,
    policy_id       BIGINT       NULL,
    submitted_at    TIMESTAMP(3) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    create_time     TIMESTAMP(3) NOT NULL,
    update_time     TIMESTAMP(3) NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    create_user_id  BIGINT       NULL,
    update_user_id  BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_biz_document_no UNIQUE (document_no),
    CONSTRAINT uk_biz_document_idem UNIQUE (owner_user_id, document_type, idempotency_key)
);

CREATE TABLE approval_task (
    id                     BIGINT       NOT NULL,
    document_id            BIGINT       NOT NULL,
    stage_code             VARCHAR(64)  NOT NULL,
    sequence_no            INT          NOT NULL,
    round_no               INT          NOT NULL,
    required_role          VARCHAR(64)  NOT NULL,
    assigned_user_id       BIGINT       NULL,
    assigned_department_id BIGINT       NULL,
    status                 VARCHAR(32)  NOT NULL,
    available_at           TIMESTAMP(3) NOT NULL,
    remind_at              TIMESTAMP(3) NULL,
    escalate_at            TIMESTAMP(3) NULL,
    timeout_at             TIMESTAMP(3) NULL,
    timeout_action         VARCHAR(32)  NOT NULL,
    decided_by             BIGINT       NULL,
    decided_at             TIMESTAMP(3) NULL,
    decision_comment       CLOB         NULL,
    create_time            TIMESTAMP(3) NOT NULL,
    update_time            TIMESTAMP(3) NOT NULL,
    deleted                TINYINT      NOT NULL DEFAULT 0,
    create_user_id         BIGINT       NULL,
    update_user_id         BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_approval_task UNIQUE (document_id, stage_code, sequence_no, round_no)
);

CREATE TABLE document_event (
    id              BIGINT       NOT NULL,
    document_id     BIGINT       NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    from_status     VARCHAR(32)  NULL,
    to_status       VARCHAR(32)  NULL,
    actor_id        BIGINT       NULL,
    actor_type      VARCHAR(32)  NOT NULL,
    occurred_at     TIMESTAMP(3) NOT NULL,
    reason          CLOB         NULL,
    task_id         BIGINT       NULL,
    request_key     VARCHAR(128) NOT NULL,
    create_time     TIMESTAMP(3) NOT NULL,
    update_time     TIMESTAMP(3) NOT NULL,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    create_user_id  BIGINT       NULL,
    update_user_id  BIGINT       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_document_event UNIQUE (document_id, request_key)
);

CREATE INDEX idx_document_event_type ON document_event (document_id, event_type, occurred_at);
