-- item 模块：库位编码删除后可复用（40表不变，幂等）
-- 仅允许现行 new_system/new_system_test；全新空库只执行 new_system_baseline.sql。
-- 保留已删除库位及其审计字段，仅未删除行的 location_code 参与唯一约束。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_item_location_code_reuse //
CREATE PROCEDURE migrate_item_location_code_reuse()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '库位编码复用迁移仅允许new_system/new_system_test';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 40 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '库位编码复用迁移前置校验失败：目标库必须为40表';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'stock_location') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '库位编码复用迁移前置校验失败：stock_location不存在';
  END IF;

  IF EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'stock_location'
        AND index_name = 'uk_stock_location_code') THEN
    ALTER TABLE stock_location DROP INDEX uk_stock_location_code;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'stock_location'
        AND column_name = 'active_location_code') THEN
    ALTER TABLE stock_location
      ADD COLUMN active_location_code VARCHAR(64)
        GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN location_code ELSE NULL END) STORED
        COMMENT '未删除库位编码，删除后为NULL以允许复用' AFTER update_user_id;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'stock_location'
        AND index_name = 'uk_stock_location_active_code') THEN
    ALTER TABLE stock_location
      ADD UNIQUE KEY uk_stock_location_active_code (active_location_code);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'stock_location'
        AND column_name = 'active_location_code')
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'stock_location'
        AND index_name = 'uk_stock_location_active_code' AND non_unique = 0)
     OR EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'stock_location'
        AND index_name = 'uk_stock_location_code') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '库位编码复用迁移完成校验失败';
  END IF;
END //
CALL migrate_item_location_code_reuse() //
DROP PROCEDURE migrate_item_location_code_reuse //
DELIMITER ;
