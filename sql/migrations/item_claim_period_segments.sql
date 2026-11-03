-- item 模块：ROOT多段节次与按段预占（40表 -> 41表，幂等）
-- 仅允许现行 new_system/new_system_test；全新空库只执行 new_system_baseline.sql。
-- 不回填历史STANDALONE段记录；历史material_reservation.claim_segment_id保持NULL。
-- 重放仅补齐缺失结构，不插入、更新或改写历史业务数据。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_item_claim_period_segments //
CREATE PROCEDURE migrate_item_claim_period_segments()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领节次段迁移仅允许new_system/new_system_test';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') NOT IN (40, 41) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领节次段迁移前置校验失败：目标库必须为40表，重放时为41表';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') = 41
     AND NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'material_claim_segment') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领节次段迁移前置校验失败：41表目标库必须已包含material_claim_segment';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
        AND table_name IN ('material_claim', 'material_reservation', 'class_period')) <> 3
     OR NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND column_name = 'claim_kind') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领节次段迁移前置校验失败：申领、预占、节次表或库位拆分列不存在';
  END IF;

  CREATE TABLE IF NOT EXISTS material_claim_segment (
    id BIGINT NOT NULL COMMENT '申领节次段ID（雪花ID）',
    claim_id BIGINT NOT NULL COMMENT 'ROOT申领ID',
    revision_no INT NOT NULL COMMENT '申领内容版本',
    segment_seq INT NOT NULL COMMENT '同一版本内段序号，从1开始',
    start_period_id BIGINT NOT NULL COMMENT '段开始节次ID',
    end_period_id BIGINT NOT NULL COMMENT '段结束节次ID',
    booking_start_at DATETIME(3) NOT NULL COMMENT '段预约开始（UTC快照）',
    booking_end_at DATETIME(3) NOT NULL COMMENT '段预约结束（UTC快照，半开区间）',
    create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
    update_user_id BIGINT DEFAULT NULL COMMENT '最后操作人，审计不建外键',
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_claim_segment_revision (claim_id, revision_no, segment_seq),
    KEY idx_material_claim_segment_start_period (start_period_id),
    KEY idx_material_claim_segment_end_period (end_period_id),
    CONSTRAINT fk_material_claim_segment_claim FOREIGN KEY (claim_id)
      REFERENCES material_claim (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_material_claim_segment_start_period FOREIGN KEY (start_period_id)
      REFERENCES class_period (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_material_claim_segment_end_period FOREIGN KEY (end_period_id)
      REFERENCES class_period (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_material_claim_segment_seq CHECK (segment_seq > 0),
    CONSTRAINT chk_material_claim_segment_window CHECK (booking_start_at < booking_end_at),
    CONSTRAINT chk_material_claim_segment_deleted CHECK (deleted IN (0, 1))
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ROOT申领非连续节次的连续段表';

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND column_name = 'claim_segment_id') THEN
    ALTER TABLE material_reservation
      ADD COLUMN claim_segment_id BIGINT DEFAULT NULL
        COMMENT '申领节次段ID；历史无段预占保持NULL' AFTER allocation_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'idx_material_reservation_segment') THEN
    ALTER TABLE material_reservation
      ADD KEY idx_material_reservation_segment (claim_segment_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_reservation'
        AND constraint_name = 'fk_material_reservation_segment') THEN
    ALTER TABLE material_reservation
      ADD CONSTRAINT fk_material_reservation_segment FOREIGN KEY (claim_segment_id)
        REFERENCES material_claim_segment (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;

  IF EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject')
     AND ((SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject') <> 3
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject' AND non_unique = 0
        AND seq_in_index = 1 AND column_name = 'allocation_id')
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject' AND non_unique = 0
        AND seq_in_index = 2 AND column_name = 'claim_segment_id')
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject' AND non_unique = 0
        AND seq_in_index = 3 AND column_name = 'reservation_subject_id')) THEN
    ALTER TABLE material_reservation DROP INDEX uk_material_reservation_subject;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject') THEN
    ALTER TABLE material_reservation
      ADD UNIQUE KEY uk_material_reservation_subject
        (allocation_id, claim_segment_id, reservation_subject_id);
  END IF;

  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 41 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领节次段迁移完成校验失败：目标库必须为41表';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim_segment'
        AND column_name IN ('id', 'claim_id', 'revision_no', 'segment_seq',
          'start_period_id', 'end_period_id', 'booking_start_at', 'booking_end_at')
        AND is_nullable = 'NO') <> 8
     OR (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND column_name = 'claim_segment_id' AND data_type = 'bigint'
        AND is_nullable = 'YES') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领节次段迁移完成校验失败：段表或预占段列定义不正确';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim_segment'
        AND index_name = 'uk_material_claim_segment_revision') <> 3
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim_segment'
        AND index_name = 'uk_material_claim_segment_revision' AND non_unique = 0
        AND seq_in_index = 1 AND column_name = 'claim_id')
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim_segment'
        AND index_name = 'uk_material_claim_segment_revision' AND non_unique = 0
        AND seq_in_index = 2 AND column_name = 'revision_no')
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim_segment'
        AND index_name = 'uk_material_claim_segment_revision' AND non_unique = 0
        AND seq_in_index = 3 AND column_name = 'segment_seq') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领节次段迁移完成校验失败：段版本唯一键不正确';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject') <> 3
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject' AND non_unique = 0
        AND seq_in_index = 1 AND column_name = 'allocation_id')
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject' AND non_unique = 0
        AND seq_in_index = 2 AND column_name = 'claim_segment_id')
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_reservation'
        AND index_name = 'uk_material_reservation_subject' AND non_unique = 0
        AND seq_in_index = 3 AND column_name = 'reservation_subject_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领节次段迁移完成校验失败：预占段唯一键不正确';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND constraint_type = 'FOREIGN KEY'
        AND ((table_name = 'material_claim_segment' AND constraint_name IN
          ('fk_material_claim_segment_claim', 'fk_material_claim_segment_start_period',
           'fk_material_claim_segment_end_period'))
        OR (table_name = 'material_reservation'
          AND constraint_name = 'fk_material_reservation_segment'))) <> 4
     OR (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim_segment'
        AND constraint_type = 'CHECK' AND constraint_name IN
          ('chk_material_claim_segment_seq', 'chk_material_claim_segment_window',
           'chk_material_claim_segment_deleted')) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领节次段迁移完成校验失败：外键或CHECK不完整';
  END IF;
END //
CALL migrate_item_claim_period_segments() //
DROP PROCEDURE migrate_item_claim_period_segments //
DELIMITER ;
