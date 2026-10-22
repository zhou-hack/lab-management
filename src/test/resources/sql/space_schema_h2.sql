-- ============================================================================
-- 场地模块集成测试建表脚本（仅测试用，H2 MySQL 模式）。
-- 与 sql/lab_management_all.sql 的差异：H2 不支持 UTC_TIMESTAMP(3) 列默认值与
-- ON UPDATE CURRENT_TIMESTAMP(3)，时间列由 MyBatis-Plus 审计填充保证；
-- 生产建表仍以 sql/lab_management_all.sql（MySQL 8.4）为准。
-- ============================================================================

DROP TABLE IF EXISTS lab;

CREATE TABLE lab (
    id             BIGINT        NOT NULL COMMENT '主键（雪花 ID）',
    lab_name     VARCHAR(128)  NOT NULL COMMENT '场地名称（全局唯一，删除后不复用）',
    lab_type     VARCHAR(32)   NOT NULL COMMENT '类型：CLASSROOM/TRAINING_ROOM',
    location_detail       VARCHAR(128)  NULL     COMMENT '位置（自由文本）',
    capacity       INT           NOT NULL COMMENT '容纳人数（>0）',
    facilities     TEXT          NULL     COMMENT '配套设备清单',
    open_start     TIME          NOT NULL COMMENT '每日开放开始（上海时间）',
    open_end       TIME          NOT NULL COMMENT '每日开放结束（上海时间）',
    status         VARCHAR(32)   NOT NULL COMMENT '状态：AVAILABLE/MAINTAINING/CLOSED',
    create_time    TIMESTAMP(3)  NOT NULL COMMENT '创建时间（UTC）',
    update_time    TIMESTAMP(3)  NOT NULL COMMENT '更新时间（UTC）',
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT        NULL     COMMENT '创建人',
    update_user_id BIGINT        NULL     COMMENT '最后操作人',
    PRIMARY KEY (id),
    CONSTRAINT chk_lab_type CHECK (lab_type IN ('CLASSROOM', 'TRAINING_ROOM')),
    CONSTRAINT chk_lab_status CHECK (status IN ('AVAILABLE', 'MAINTAINING', 'CLOSED')),
    CONSTRAINT chk_lab_capacity CHECK (capacity > 0),
    CONSTRAINT chk_lab_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_lab_window CHECK (open_start < open_end)
);

CREATE INDEX idx_lab_status ON lab (status);
CREATE INDEX idx_lab_type ON lab (lab_type);
CREATE INDEX idx_lab_capacity ON lab (capacity);
