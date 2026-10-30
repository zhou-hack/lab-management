-- ============================================================================
-- space 模块：场地维修时段建表（幂等，可重复执行）
-- 版本：2026-10-24；背景：AVAILABLE 场地的短期维修窗口。窗口内禁止新预约；
-- 与已批准预约重叠时拒绝设置；一个场地可有多个未来时段，取消置 cancelled_at。
-- 全新建库无需执行（lab_management_all.sql 正本 1.3 已含本表）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS lab_maintenance_window (
    id             BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    lab_id         BIGINT       NOT NULL                COMMENT '场地（→ lab.id）',
    start_at       DATETIME(3)  NOT NULL                COMMENT '维修开始（UTC）',
    end_at         DATETIME(3)  NOT NULL                COMMENT '维修结束（UTC）',
    reason         VARCHAR(512) NULL                    COMMENT '维修事由',
    cancelled_at   DATETIME(3)  NULL                    COMMENT '取消时间（UTC；NULL=生效中）',
    create_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted        TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id BIGINT       NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    KEY idx_mw_lab_time (lab_id, start_at, end_at),
    CONSTRAINT fk_mw_lab FOREIGN KEY (lab_id) REFERENCES lab (id),
    CONSTRAINT chk_mw_time CHECK (start_at < end_at),
    CONSTRAINT chk_mw_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地维修时段（短期窗口，窗口内禁止新预约）';

-- 校验（预期：表存在）
-- SHOW TABLES LIKE 'lab_maintenance_window';
