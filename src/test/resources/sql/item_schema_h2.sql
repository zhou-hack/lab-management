-- 物料模块 H2 集成测试结构。与 booking_schema_h2.sql 组合执行；不含外键、MySQL 生成列或锁语义验证。

DROP TABLE IF EXISTS material_notification;
DROP TABLE IF EXISTS stock_movement;
DROP TABLE IF EXISTS material_settlement;
DROP TABLE IF EXISTS material_document_line;
DROP TABLE IF EXISTS material_document;
DROP TABLE IF EXISTS material_reservation;
DROP TABLE IF EXISTS material_claim_allocation;
DROP TABLE IF EXISTS material_claim_line;
DROP TABLE IF EXISTS material_claim;
DROP TABLE IF EXISTS material_booking_plan_line;
DROP TABLE IF EXISTS material_booking_plan;
DROP TABLE IF EXISTS stock_balance;
DROP TABLE IF EXISTS stock_location_responsible_history;
DROP TABLE IF EXISTS stock_location_admin;
DROP TABLE IF EXISTS fixed_asset;
DROP TABLE IF EXISTS stock_location;
DROP TABLE IF EXISTS material;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;

ALTER TABLE approval_task ALTER COLUMN timeout_action SET DEFAULT 'NONE';

CREATE TABLE sys_role (
    id BIGINT NOT NULL PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE TABLE sys_permission (
    id BIGINT NOT NULL PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE TABLE sys_user_role (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE TABLE sys_role_permission (
    id BIGINT NOT NULL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);

CREATE TABLE material (
    id BIGINT NOT NULL PRIMARY KEY,
    material_type VARCHAR(32) NOT NULL,
    track_mode VARCHAR(32) NOT NULL,
    sku_code VARCHAR(64),
    material_name VARCHAR(128) NOT NULL,
    specification VARCHAR(256),
    base_unit VARCHAR(32) NOT NULL,
    quantity_mode VARCHAR(32) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_material_sku ON material (sku_code);
CREATE INDEX idx_item_material_type ON material (material_type, enabled, deleted);

CREATE TABLE stock_location (
    id BIGINT NOT NULL PRIMARY KEY,
    location_code VARCHAR(64) NOT NULL,
    location_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT,
    active_location_code VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN location_code ELSE NULL END)
);
CREATE UNIQUE INDEX uk_item_location_active_code ON stock_location (active_location_code);

CREATE TABLE fixed_asset (
    id BIGINT NOT NULL PRIMARY KEY,
    material_id BIGINT NOT NULL,
    asset_code VARCHAR(64) NOT NULL,
    price DECIMAL(18,2),
    location_id BIGINT NOT NULL,
    position_text VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    custodian_user_id BIGINT,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_asset_code ON fixed_asset (asset_code);
CREATE INDEX idx_item_asset_material ON fixed_asset (material_id, status, deleted);
CREATE INDEX idx_item_asset_location ON fixed_asset (location_id, status, deleted);

CREATE TABLE stock_location_admin (
    id BIGINT NOT NULL PRIMARY KEY,
    location_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_location_admin ON stock_location_admin (location_id, user_id);
CREATE INDEX idx_item_location_admin_user ON stock_location_admin (user_id, location_id, deleted);

CREATE TABLE stock_location_responsible_history (
    id BIGINT NOT NULL PRIMARY KEY,
    location_id BIGINT NOT NULL,
    location_code VARCHAR(64) NOT NULL,
    location_name VARCHAR(128) NOT NULL,
    before_snapshot CLOB NOT NULL,
    after_snapshot CLOB NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE INDEX idx_item_location_responsible_history_location
    ON stock_location_responsible_history (location_id, occurred_at, id);
CREATE INDEX idx_item_location_responsible_history_occurred
    ON stock_location_responsible_history (occurred_at, id);

CREATE TABLE stock_balance (
    id BIGINT NOT NULL PRIMARY KEY,
    material_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    position_text VARCHAR(256),
    position_key VARCHAR(256) GENERATED ALWAYS AS (COALESCE(position_text, '')),
    owned_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    available_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_balance_position ON stock_balance (material_id, location_id, position_key);
CREATE INDEX idx_item_balance_location ON stock_balance (location_id, material_id);

CREATE TABLE material_booking_plan (
    id BIGINT NOT NULL PRIMARY KEY,
    owner_teacher_id BIGINT NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    remark VARCHAR(512),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE INDEX idx_item_plan_owner ON material_booking_plan (owner_teacher_id, enabled, deleted);

CREATE TABLE material_booking_plan_line (
    id BIGINT NOT NULL PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    default_quantity DECIMAL(18,3) NOT NULL,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_plan_line ON material_booking_plan_line (plan_id, material_id);

CREATE TABLE material_assistant_grant (
    id BIGINT NOT NULL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    start_at TIMESTAMP(3) NOT NULL,
    end_at TIMESTAMP(3) NOT NULL,
    revoked_at TIMESTAMP(3),
    revoked_by BIGINT,
    reason VARCHAR(512),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE INDEX idx_item_assistant_grant_teacher ON material_assistant_grant (teacher_id, start_at, end_at);
CREATE INDEX idx_item_assistant_grant_student ON material_assistant_grant (student_id, start_at, end_at);

CREATE TABLE material_claim (
    id BIGINT NOT NULL PRIMARY KEY,
    biz_document_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    purpose VARCHAR(512) NOT NULL,
    booking_date DATE NOT NULL,
    start_period_id BIGINT NOT NULL,
    end_period_id BIGINT NOT NULL,
    booking_start_at TIMESTAMP(3) NOT NULL,
    booking_end_at TIMESTAMP(3) NOT NULL,
    return_deadline TIMESTAMP(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    revision_no INT NOT NULL DEFAULT 1,
    approved_by BIGINT,
    approved_at TIMESTAMP(3),
    reject_reason VARCHAR(512),
    abandon_requested_by BIGINT,
    abandon_requested_at TIMESTAMP(3),
    abandon_reason VARCHAR(512),
    close_reason VARCHAR(32),
    closed_by BIGINT,
    closed_at TIMESTAMP(3),
    cancelled_by BIGINT,
    cancelled_at TIMESTAMP(3),
    pickup_mode VARCHAR(32) NOT NULL DEFAULT 'SELF',
    delegate_student_id BIGINT,
    delegate_grant_id BIGINT,
    delegated_at TIMESTAMP(3),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_claim_document ON material_claim (biz_document_id);
CREATE INDEX idx_item_claim_applicant ON material_claim (applicant_id, create_time);
CREATE INDEX idx_item_claim_status ON material_claim (status, booking_start_at, booking_end_at);
CREATE INDEX idx_item_claim_delegate ON material_claim (delegate_student_id, status, booking_start_at);

CREATE TABLE material_claim_line (
    id BIGINT NOT NULL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    requested_quantity DECIMAL(18,3) NOT NULL,
    issued_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_claim_line_version ON material_claim_line (claim_id, revision_no, material_id);
CREATE INDEX idx_item_claim_line_current ON material_claim_line (claim_id, revision_no);

CREATE TABLE material_claim_allocation (
    id BIGINT NOT NULL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    claim_line_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    location_id BIGINT NOT NULL,
    planned_quantity DECIMAL(18,3) NOT NULL,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_claim_allocation ON material_claim_allocation (claim_line_id, location_id);
CREATE INDEX idx_item_claim_allocation_claim ON material_claim_allocation (claim_id, revision_no, location_id);

CREATE TABLE material_reservation (
    id BIGINT NOT NULL PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    claim_line_id BIGINT NOT NULL,
    allocation_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    material_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    fixed_asset_id BIGINT,
    reservation_subject_id BIGINT NOT NULL DEFAULT 0,
    reservation_rule VARCHAR(32) NOT NULL,
    asset_total_snapshot INT,
    reserved_quantity DECIMAL(18,3) NOT NULL,
    consumed_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    released_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    reservation_start_at TIMESTAMP(3) NOT NULL,
    reservation_end_at TIMESTAMP(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    closed_at TIMESTAMP(3),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_reservation_subject ON material_reservation (allocation_id, reservation_subject_id);
CREATE INDEX idx_item_reservation_claim ON material_reservation (claim_id, revision_no);
CREATE INDEX idx_item_reservation_overlap ON material_reservation (material_id, location_id, status, reservation_start_at, reservation_end_at);
CREATE INDEX idx_item_reservation_asset ON material_reservation (fixed_asset_id, status, reservation_start_at, reservation_end_at);

CREATE TABLE material_document (
    id BIGINT NOT NULL PRIMARY KEY,
    biz_document_id BIGINT NOT NULL,
    document_no VARCHAR(64) NOT NULL,
    business_chain_id BIGINT,
    document_type VARCHAR(32) NOT NULL,
    business_scene VARCHAR(32) NOT NULL,
    document_seq INT,
    source_material_document_id BIGINT,
    location_id BIGINT,
    from_location_id BIGINT,
    to_location_id BIGINT,
    transfer_status VARCHAR(32),
    received_by BIGINT,
    received_at TIMESTAMP(3),
    review_status VARCHAR(32),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP(3),
    review_reason VARCHAR(512),
    operator_id BIGINT NOT NULL,
    responsible_teacher_id BIGINT,
    pickup_user_id BIGINT,
    occurred_at TIMESTAMP(3) NOT NULL,
    posted_at TIMESTAMP(3),
    process_status VARCHAR(32),
    return_status VARCHAR(32),
    overdue_reason VARCHAR(512),
    idempotency_key VARCHAR(128) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    remark VARCHAR(512),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_document_no ON material_document (document_no);
CREATE UNIQUE INDEX uk_item_document_biz ON material_document (biz_document_id);
CREATE UNIQUE INDEX uk_item_document_chain ON material_document (business_chain_id, document_type, document_seq);
CREATE UNIQUE INDEX uk_item_document_idem ON material_document (operator_id, document_type, idempotency_key);
CREATE INDEX idx_item_document_source ON material_document (source_material_document_id);
CREATE INDEX idx_item_document_review ON material_document (review_status, document_type, location_id);

CREATE TABLE material_document_line (
    id BIGINT NOT NULL PRIMARY KEY,
    material_document_id BIGINT NOT NULL,
    claim_line_id BIGINT,
    source_material_document_line_id BIGINT,
    material_id BIGINT NOT NULL,
    fixed_asset_id BIGINT,
    quantity DECIMAL(18,3) NOT NULL,
    position_text VARCHAR(256),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE INDEX idx_item_document_line_doc ON material_document_line (material_document_id);
CREATE INDEX idx_item_document_line_source ON material_document_line (source_material_document_line_id);
CREATE INDEX idx_item_document_line_claim ON material_document_line (claim_line_id);
CREATE INDEX idx_item_document_line_material ON material_document_line (material_id, material_document_id);

CREATE TABLE material_settlement (
    id BIGINT NOT NULL PRIMARY KEY,
    material_document_id BIGINT NOT NULL,
    source_material_document_line_id BIGINT,
    material_id BIGINT NOT NULL,
    fixed_asset_id BIGINT,
    settlement_type VARCHAR(32) NOT NULL,
    quantity DECIMAL(18,3) NOT NULL,
    target_asset_status VARCHAR(32),
    reason VARCHAR(512) NOT NULL,
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE INDEX idx_item_settlement_document ON material_settlement (material_document_id);
CREATE INDEX idx_item_settlement_source ON material_settlement (source_material_document_line_id);

CREATE TABLE stock_movement (
    id BIGINT NOT NULL PRIMARY KEY,
    movement_key VARCHAR(128) NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    material_id BIGINT NOT NULL,
    fixed_asset_id BIGINT,
    location_id BIGINT NOT NULL,
    quantity DECIMAL(18,3) NOT NULL,
    material_document_id BIGINT NOT NULL,
    material_document_line_id BIGINT,
    operator_id BIGINT NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    before_owned_quantity DECIMAL(18,3),
    after_owned_quantity DECIMAL(18,3),
    before_available_quantity DECIMAL(18,3),
    after_available_quantity DECIMAL(18,3),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_movement_key ON stock_movement (movement_key);
CREATE INDEX idx_item_movement_material ON stock_movement (material_id, location_id, occurred_at);
CREATE INDEX idx_item_movement_document ON stock_movement (material_document_id, material_document_line_id);

CREATE TABLE material_notification (
    id BIGINT NOT NULL PRIMARY KEY,
    dedupe_key VARCHAR(128) NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    notification_stage VARCHAR(32) NOT NULL,
    reminder_date DATE,
    claim_id BIGINT NOT NULL,
    material_document_id BIGINT,
    biz_document_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NOT NULL,
    read_at TIMESTAMP(3),
    create_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_user_id BIGINT,
    update_user_id BIGINT
);
CREATE UNIQUE INDEX uk_item_notification_dedupe ON material_notification (recipient_user_id, dedupe_key);
CREATE INDEX idx_item_notification_recipient ON material_notification (recipient_user_id, read_at, create_time);
