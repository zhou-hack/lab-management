-- item 模块：新申领外部业务链编号（40表不变，幂等）
-- 仅允许现行 new_system/new_system_test；全新空库只执行 new_system_baseline.sql。
-- 仅新申领写 ALL+yyyyMMdd+5位随机数字；历史行保持NULL，不回填历史ITC/IM或数值业务链ID。
-- 外部编号不替代共享biz_document_id或material_document.business_chain_id内部外键。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_item_document_numbers //
CREATE PROCEDURE migrate_item_document_numbers()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '单据编号迁移仅允许new_system/new_system_test';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 40 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '单据编号迁移前置校验失败：目标库必须为40表';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'material_claim') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '单据编号迁移前置校验失败：material_claim不存在';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND column_name = 'business_chain_no') THEN
    ALTER TABLE material_claim
      ADD COLUMN business_chain_no VARCHAR(64) DEFAULT NULL
        COMMENT '新申领外部业务链编号（ALL+yyyyMMdd+5位随机数字），历史为NULL' AFTER biz_document_id;
  END IF;

  IF EXISTS (SELECT 1 FROM material_claim
      WHERE business_chain_no IS NOT NULL
      GROUP BY business_chain_no HAVING COUNT(*) > 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '单据编号迁移失败：business_chain_no存在非空重复值，未修改历史数据';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'uk_material_claim_business_chain_no') THEN
    ALTER TABLE material_claim
      ADD UNIQUE KEY uk_material_claim_business_chain_no (business_chain_no);
  END IF;

  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND column_name = 'business_chain_no' AND data_type = 'varchar'
        AND character_maximum_length = 64 AND is_nullable = 'YES') <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '单据编号迁移完成校验失败：business_chain_no列定义不正确';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'uk_material_claim_business_chain_no') <> 1
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'uk_material_claim_business_chain_no' AND non_unique = 0
        AND seq_in_index = 1 AND column_name = 'business_chain_no') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '单据编号迁移完成校验失败：business_chain_no唯一索引不正确';
  END IF;
END //
CALL migrate_item_document_numbers() //
DROP PROCEDURE migrate_item_document_numbers //
DELIMITER ;
