-- ============================================================================
-- 场地模块集成测试建表脚本（仅测试用，H2 MySQL 模式）。
-- 与 sql/biz_space.sql 的差异：H2 不支持 UTC_TIMESTAMP(3) 列默认值与
-- ON UPDATE CURRENT_TIMESTAMP(3)，时间列由 MyBatis-Plus 审计填充保证；
-- 生产建表仍以 sql/biz_space.sql（MySQL 8.4）为准。
-- ============================================================================

DROP TABLE IF EXISTS biz_space;

CREATE TABLE biz_space (
    id             BIGINT        NOT NULL COMMENT '主键（雪花 ID）',
    space_name     VARCHAR(128)  NOT NULL COMMENT '场地名称（全局唯一，删除后不复用）',
    space_type     VARCHAR(32)   NOT NULL COMMENT '类型：CLASSROOM/TRAINING_ROOM',
    location       VARCHAR(128)  NULL     COMMENT '位置（自由文本）',
    capacity       INT           NOT NULL COMMENT '容纳人数（>0）',
    facilities     TEXT          NULL     COMMENT '配套设备清单',
    open_start     TIME          NOT NULL COMMENT '每日开放开始（上海本地时间）',
    open_end       TIME          NOT NULL COMMENT '每日开放结束（上海本地时间）',
    status         VARCHAR(32)   NOT NULL COMMENT '状态：AVAILABLE/MAINTAINING/CLOSED',
    create_time    TIMESTAMP(3)  NOT NULL COMMENT '创建时间（UTC）',
    update_time    TIMESTAMP(3)  NOT NULL COMMENT '更新时间（UTC）',
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT        NULL     COMMENT '创建人',
    update_user_id BIGINT        NULL     COMMENT '最后操作人',
    PRIMARY KEY (id),
    CONSTRAINT chk_biz_space_type CHECK (space_type IN ('CLASSROOM', 'TRAINING_ROOM')),
    CONSTRAINT chk_biz_space_status CHECK (status IN ('AVAILABLE', 'MAINTAINING', 'CLOSED')),
    CONSTRAINT chk_biz_space_capacity CHECK (capacity > 0),
    CONSTRAINT chk_biz_space_deleted CHECK (deleted IN (0, 1)),
    CONSTRAINT chk_biz_space_window CHECK (open_start < open_end)
);

CREATE INDEX idx_biz_space_status ON biz_space (status);
CREATE INDEX idx_biz_space_type ON biz_space (space_type);
CREATE INDEX idx_biz_space_capacity ON biz_space (capacity);
