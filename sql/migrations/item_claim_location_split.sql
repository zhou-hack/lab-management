-- item 模块：申领按库位拆分的父子单结构（40表不变，幂等）
-- 仅允许现行 new_system/new_system_test；全新空库只执行 new_system_baseline.sql。
-- 新增列时历史行由数据库默认STANDALONE；不回填父子关系或库位，不改写其他历史业务数据。
-- 重放仅补齐缺失结构，不更新claim_kind，后续ROOT/CHILD不会被重置。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_item_claim_location_split //
CREATE PROCEDURE migrate_item_claim_location_split()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领库位拆分迁移仅允许new_system/new_system_test';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 40 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领库位拆分迁移前置校验失败：目标库必须为40表';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'material_claim')
     OR NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'stock_location') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领库位拆分迁移前置校验失败：material_claim或stock_location不存在';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND column_name = 'claim_kind') THEN
    ALTER TABLE material_claim
      ADD COLUMN claim_kind VARCHAR(32) NOT NULL DEFAULT 'STANDALONE'
        COMMENT 'STANDALONE独立申领/ROOT拆分父单/CHILD库位子单' AFTER business_chain_no;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND column_name = 'parent_claim_id') THEN
    ALTER TABLE material_claim
      ADD COLUMN parent_claim_id BIGINT DEFAULT NULL
        COMMENT '父申领ID，仅CHILD非空' AFTER claim_kind;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND column_name = 'location_id') THEN
    ALTER TABLE material_claim
      ADD COLUMN location_id BIGINT DEFAULT NULL
        COMMENT '子单所属库位ID，仅CHILD非空' AFTER parent_claim_id;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'uk_material_claim_parent_location') THEN
    ALTER TABLE material_claim
      ADD UNIQUE KEY uk_material_claim_parent_location (parent_claim_id, location_id);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'idx_material_claim_location') THEN
    ALTER TABLE material_claim
      ADD KEY idx_material_claim_location (location_id);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim'
        AND constraint_name = 'fk_material_claim_parent') THEN
    ALTER TABLE material_claim
      ADD CONSTRAINT fk_material_claim_parent FOREIGN KEY (parent_claim_id)
        REFERENCES material_claim (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim'
        AND constraint_name = 'fk_material_claim_location') THEN
    ALTER TABLE material_claim
      ADD CONSTRAINT fk_material_claim_location FOREIGN KEY (location_id)
        REFERENCES stock_location (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim'
        AND constraint_name = 'chk_material_claim_kind') THEN
    ALTER TABLE material_claim
      ADD CONSTRAINT chk_material_claim_kind
        CHECK (claim_kind IN ('STANDALONE', 'ROOT', 'CHILD'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim'
        AND constraint_name = 'chk_material_claim_split') THEN
    ALTER TABLE material_claim
      ADD CONSTRAINT chk_material_claim_split CHECK (
        (claim_kind = 'CHILD' AND parent_claim_id IS NOT NULL
         AND location_id IS NOT NULL AND parent_claim_id <> id)
        OR (claim_kind IN ('STANDALONE', 'ROOT')
            AND parent_claim_id IS NULL AND location_id IS NULL)
      );
  END IF;

  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 40 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领库位拆分迁移完成校验失败：表总数必须保持40';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND column_name = 'claim_kind' AND data_type = 'varchar'
        AND character_maximum_length = 32 AND is_nullable = 'NO'
        AND column_default = 'STANDALONE') <> 1
     OR (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND column_name IN ('parent_claim_id', 'location_id')
        AND data_type = 'bigint' AND is_nullable = 'YES') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领库位拆分迁移完成校验失败：新增列定义不正确';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'uk_material_claim_parent_location') <> 2
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'uk_material_claim_parent_location' AND non_unique = 0
        AND seq_in_index = 1 AND column_name = 'parent_claim_id')
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'uk_material_claim_parent_location' AND non_unique = 0
        AND seq_in_index = 2 AND column_name = 'location_id')
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'idx_material_claim_location' AND non_unique = 1
        AND seq_in_index = 1 AND column_name = 'location_id') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领库位拆分迁移完成校验失败：唯一键或库位索引不正确';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim'
        AND constraint_name IN ('fk_material_claim_parent', 'fk_material_claim_location')
        AND constraint_type = 'FOREIGN KEY') <> 2
     OR (SELECT COUNT(*) FROM information_schema.table_constraints
      WHERE constraint_schema = DATABASE() AND table_name = 'material_claim'
        AND constraint_name IN ('chk_material_claim_kind', 'chk_material_claim_split')
        AND constraint_type = 'CHECK') <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '申领库位拆分迁移完成校验失败：外键或CHECK不完整';
  END IF;
END //
CALL migrate_item_claim_location_split() //
DROP PROCEDURE migrate_item_claim_location_split //
DELIMITER ;
