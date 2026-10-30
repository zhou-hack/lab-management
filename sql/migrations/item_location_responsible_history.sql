-- item 模块：库位负责人变动历史（39表 -> 40表，幂等）
-- 仅允许现行 new_system/new_system_test；全新空库只执行 new_system_baseline.sql。
-- fixed_asset.custodian_user_id 保留为遗留可空列，不删除、不回填、不再由应用读写。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_item_location_responsible_history //
CREATE PROCEDURE migrate_item_location_responsible_history()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '库位负责人历史迁移仅允许new_system/new_system_test';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') NOT IN (39, 40) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '库位负责人历史迁移前置校验失败：目标库必须为39或40表';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'stock_location')
     OR NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'stock_location_admin')
     OR NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'fixed_asset' AND column_name = 'custodian_user_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '库位负责人历史迁移前置校验失败：库位、负责人关系或遗留资产负责人列不存在';
  END IF;

  CREATE TABLE IF NOT EXISTS stock_location_responsible_history (
    id BIGINT NOT NULL COMMENT '负责人变动历史ID（雪花ID）',
    location_id BIGINT NOT NULL COMMENT '库位ID',
    location_code VARCHAR(64) NOT NULL COMMENT '变动时库位编码快照',
    location_name VARCHAR(128) NOT NULL COMMENT '变动时库位名称快照',
    before_snapshot TEXT NOT NULL COMMENT '变更前负责人JSON数组（userId/realName/accountName）',
    after_snapshot TEXT NOT NULL COMMENT '变更后负责人JSON数组（userId/realName/accountName）',
    operator_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(128) NOT NULL COMMENT '操作人姓名快照',
    occurred_at DATETIME(3) NOT NULL COMMENT '实际变动时间（UTC）',
    create_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '追加表保持创建值',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '负责人历史恒保留，固定0',
    create_user_id BIGINT DEFAULT NULL COMMENT '创建人，审计不建外键',
    update_user_id BIGINT DEFAULT NULL COMMENT '追加表不使用',
    PRIMARY KEY (id),
    KEY idx_location_responsible_history_location (location_id, occurred_at, id),
    KEY idx_location_responsible_history_occurred (occurred_at, id),
    KEY idx_location_responsible_history_operator (operator_id),
    CONSTRAINT fk_location_responsible_history_location FOREIGN KEY (location_id) REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_location_responsible_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_location_responsible_history_before_json CHECK (JSON_VALID(before_snapshot)),
    CONSTRAINT chk_location_responsible_history_after_json CHECK (JSON_VALID(after_snapshot)),
    CONSTRAINT chk_location_responsible_history_deleted CHECK (deleted = 0)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库位负责人变动历史表（只追加）';

  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 40 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '库位负责人历史迁移完成校验失败：目标库必须为40表';
  END IF;
END //
CALL migrate_item_location_responsible_history() //
DROP PROCEDURE migrate_item_location_responsible_history //
DELIMITER ;
