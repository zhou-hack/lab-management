-- ============================================================================
-- 场地预约「节次时间轴 + 批量周期预约」增量部署脚本（幂等，可重复执行）
-- 版本：2026-10-20
--
-- 背景：在既有场地预约（lab_booking/lab_slot + 权级流程三表）之上扩展：
--   1) class_period 节次定义表：日期下按节次一排小卡片（三色状态），
--      预约改按「日期 + 起始/结束节次」选择，start_at/end_at 由节次时刻推导；
--   2) lab_booking_series 周期预约规则表：老师一次提交「固定星期 + 节次 +
--      使用周期」，管理员一次批准后系统批量生成各周预约单与占用槽，
--      各周预约单通过 lab_booking.series_id 回指本规则；
--   3) lab_booking 增加业务描述列：course_name/class_name/teacher_id（课程、
--      班级、上课老师）、booking_date/start_period_id/end_period_id（预约
--      日期与节次，用于时间轴）、series_id（周期来源）。
--
-- 说明：仅新增结构与授权数据，不修改既有业务数据；旧单（start_at/end_at）
--       各周期列保持 NULL 兼容。审批/发起仍复用 booking:lab:create /
--       booking:lab:approve，不新增权限。须先于本脚本完成 booking_deploy、
--       booking_flow_deploy、sys_rbac 部署。
-- ============================================================================

-- 1) 节次定义表 --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS class_period (
    id          BIGINT      NOT NULL                COMMENT '主键（固定小 ID，与节次号一致）',
    period_no   INT         NOT NULL                COMMENT '节次号（全局唯一，升序）',
    period_name VARCHAR(32) NOT NULL                COMMENT '显示名（如 第1节）',
    start_clock TIME(0)     NOT NULL                COMMENT '开始时刻（上海时间）',
    end_clock   TIME(0)     NOT NULL                COMMENT '结束时刻（上海时间，须晚于 start_clock）',
    enabled     TINYINT     NOT NULL DEFAULT 1      COMMENT '启用：1启用 0停用',
    create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted     TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT   NULL                    COMMENT '创建人（审计字段不建外键）',
    update_user_id BIGINT   NULL                    COMMENT '最后操作人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_period_no (period_no),
    CONSTRAINT chk_class_period_clock CHECK (start_clock < end_clock),
    CONSTRAINT chk_class_period_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT chk_class_period_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='节次定义表（预约按日期+节次选择，CLOCK 为上海时刻）';

-- 节次种子（按《福州软件职业技术学院教学作息时间表》2026-10-08 起执行）
INSERT IGNORE INTO class_period (id, period_no, period_name, start_clock, end_clock) VALUES
(1, 1, '第1节', '08:25:00', '09:10:00'),
(2, 2, '第2节', '09:15:00', '10:00:00'),
(3, 3, '第3节', '10:10:00', '10:55:00'),
(4, 4, '第4节', '11:00:00', '11:45:00'),
(5, 5, '第5节', '13:50:00', '14:35:00'),
(6, 6, '第6节', '14:40:00', '15:25:00'),
(7, 7, '第7节', '15:35:00', '16:20:00'),
(8, 8, '第8节', '16:25:00', '17:10:00'),
(9, 9, '第9节', '18:30:00', '19:15:00'),
(10, 10, '第10节', '19:20:00', '20:05:00'),
(11, 11, '第11节', '20:10:00', '20:55:00'),
(12, 12, '第12节', '21:00:00', '21:45:00');

-- 2) 周期预约规则表（一次审批，批量生成各周预约单与占用槽）---------------------
CREATE TABLE IF NOT EXISTS lab_booking_series (
    id                BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
    lab_id            BIGINT       NOT NULL                COMMENT '预约场地（→ lab.id）',
    applicant_id      BIGINT       NOT NULL                COMMENT '申请人（→ sys_user.id）',
    course_name       VARCHAR(128) NULL                    COMMENT '课程名称',
    class_name        VARCHAR(128) NULL                    COMMENT '班级',
    teacher_id        BIGINT       NULL                    COMMENT '上课老师（→ sys_user.id，跨模块只建列不建外键）',
    purpose           VARCHAR(512) NOT NULL                COMMENT '用途说明',
    participant_count INT          NOT NULL                COMMENT '参与人数（>0，且不超过场地容量）',
    weekday           TINYINT      NOT NULL                COMMENT '星期：1周一 … 7周日（ISO 8601）',
    start_period_id   BIGINT       NOT NULL                COMMENT '起始节次（→ class_period.id）',
    end_period_id     BIGINT       NOT NULL                COMMENT '结束节次（→ class_period.id）',
    cycle_start_date  DATE         NOT NULL                COMMENT '周期开始日期（纯日期，上海）',
    cycle_end_date    DATE         NOT NULL                COMMENT '周期结束日期（纯日期，须不早于开始日期）',
    status            VARCHAR(32)  NOT NULL                COMMENT '状态：PENDING待审批/APPROVED已批准/REJECTED已驳回/CANCELLED已取消',
    reject_reason     VARCHAR(512) NULL                    COMMENT '驳回原因',
    cancel_reason     VARCHAR(512) NULL                    COMMENT '取消原因',
    cancel_type       VARCHAR(32)  NULL                    COMMENT '取消类型：MANUAL人工/AUTO_EXPIRED超时',
    cancelled_at      DATETIME(3)  NULL                    COMMENT '取消时间（UTC）',
    idempotency_key   VARCHAR(128) NOT NULL                COMMENT '幂等键（防重复提交）',
    create_time       DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time       DATETIME(3)  NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted           TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id    BIGINT       NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id    BIGINT       NULL                    COMMENT '最后操作人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_booking_series_idem (applicant_id, idempotency_key),
    KEY idx_lab_booking_series_lab_day (lab_id, weekday, status),
    KEY idx_lab_booking_series_applicant (applicant_id, create_time),
    CONSTRAINT fk_lab_booking_series_lab FOREIGN KEY (lab_id) REFERENCES lab (id),
    CONSTRAINT fk_lab_booking_series_applicant FOREIGN KEY (applicant_id) REFERENCES sys_user (id),
    CONSTRAINT fk_lab_booking_series_start_period FOREIGN KEY (start_period_id) REFERENCES class_period (id),
    CONSTRAINT fk_lab_booking_series_end_period FOREIGN KEY (end_period_id) REFERENCES class_period (id),
    CONSTRAINT chk_lab_booking_series_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT chk_lab_booking_series_cycle CHECK (cycle_start_date <= cycle_end_date),
    CONSTRAINT chk_lab_booking_series_count CHECK (participant_count > 0),
    CONSTRAINT chk_lab_booking_series_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT chk_lab_booking_series_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='场地周期预约规则（固定星期+节次+使用周期，一次审批批量落地）';

-- 3) lab_booking 增列（幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，
--      以 information_schema 守卫 + PREPARE/EXECUTE 动态执行，可重复运行）-----------
-- 说明：新增列一律追加在表尾；已存在列自动跳过，不修改既有数据。
SET @dp = DATABASE();
SET @ddl_col = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE lab_booking ADD COLUMN booking_date DATE NULL COMMENT ''预约日期（纯日期，上海日界）''',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = @dp AND table_name = 'lab_booking' AND column_name = 'booking_date');
PREPARE stmt_col FROM @ddl_col; EXECUTE stmt_col; DEALLOCATE PREPARE stmt_col;

SET @ddl_col = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE lab_booking ADD COLUMN start_period_id BIGINT NULL COMMENT ''起始节次（→ class_period.id）''',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = @dp AND table_name = 'lab_booking' AND column_name = 'start_period_id');
PREPARE stmt_col FROM @ddl_col; EXECUTE stmt_col; DEALLOCATE PREPARE stmt_col;

SET @ddl_col = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE lab_booking ADD COLUMN end_period_id BIGINT NULL COMMENT ''结束节次（→ class_period.id）''',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = @dp AND table_name = 'lab_booking' AND column_name = 'end_period_id');
PREPARE stmt_col FROM @ddl_col; EXECUTE stmt_col; DEALLOCATE PREPARE stmt_col;

SET @ddl_col = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE lab_booking ADD COLUMN series_id BIGINT NULL COMMENT ''来源周期规则（→ lab_booking_series.id；单次预约为 NULL）''',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = @dp AND table_name = 'lab_booking' AND column_name = 'series_id');
PREPARE stmt_col FROM @ddl_col; EXECUTE stmt_col; DEALLOCATE PREPARE stmt_col;

SET @ddl_col = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE lab_booking ADD COLUMN course_name VARCHAR(128) NULL COMMENT ''课程名称（提交预约时填写）''',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = @dp AND table_name = 'lab_booking' AND column_name = 'course_name');
PREPARE stmt_col FROM @ddl_col; EXECUTE stmt_col; DEALLOCATE PREPARE stmt_col;

SET @ddl_col = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE lab_booking ADD COLUMN class_name VARCHAR(128) NULL COMMENT ''班级（提交预约时填写）''',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = @dp AND table_name = 'lab_booking' AND column_name = 'class_name');
PREPARE stmt_col FROM @ddl_col; EXECUTE stmt_col; DEALLOCATE PREPARE stmt_col;

SET @ddl_col = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE lab_booking ADD COLUMN teacher_id BIGINT NULL COMMENT ''上课老师（→ sys_user.id，跨模块只建列不建外键）''',
        'DO 0')
    FROM information_schema.columns
    WHERE table_schema = @dp AND table_name = 'lab_booking' AND column_name = 'teacher_id');
PREPARE stmt_col FROM @ddl_col; EXECUTE stmt_col; DEALLOCATE PREPARE stmt_col;

-- 周期来源索引（存在则跳过：MySQL 8.0 不支持 CREATE INDEX IF NOT EXISTS，
-- 用 information_schema 守卫动态执行，保证幂等可重复）
SET @idx_sql = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_lab_booking_series ON lab_booking (series_id)',
        'DO 0')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'lab_booking'
      AND index_name = 'idx_lab_booking_series');
PREPARE stmt_idx FROM @idx_sql;
EXECUTE stmt_idx;
DEALLOCATE PREPARE stmt_idx;

-- 4) 部署后校验（人工执行）：
-- SELECT period_no, period_name, start_clock, end_clock FROM class_period ORDER BY period_no;
-- SELECT COUNT(*) FROM lab_booking_series;              -- 预期 0（新表）
-- SHOW COLUMNS FROM lab_booking LIKE '%period%';        -- 预期含 start_period_id/end_period_id
-- ============================================================================
