-- item 模块：父子申领共享业务链编号（41表不变，幂等）
-- CHILD 继承 ROOT 的 business_chain_no；历史子单按父单回填。
-- 仅允许现行 new_system/new_system_test；全新空库只执行 new_system_baseline.sql。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_item_claim_business_chain_share //
CREATE PROCEDURE migrate_item_claim_business_chain_share()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '父子业务链迁移仅允许new_system/new_system_test';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 41 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '父子业务链迁移前置校验失败：目标库必须为41表';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND column_name IN ('business_chain_no', 'claim_kind', 'parent_claim_id')) <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '父子业务链迁移前置校验失败：申领业务链或父子结构列不存在';
  END IF;

  IF EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'uk_material_claim_business_chain_no') THEN
    ALTER TABLE material_claim DROP INDEX uk_material_claim_business_chain_no;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'idx_material_claim_business_chain_no') THEN
    ALTER TABLE material_claim
      ADD KEY idx_material_claim_business_chain_no (business_chain_no);
  END IF;

  UPDATE material_claim child
  JOIN material_claim root ON root.id = child.parent_claim_id
  SET child.business_chain_no = root.business_chain_no,
      child.update_time = UTC_TIMESTAMP(3)
  WHERE child.claim_kind = 'CHILD'
    AND NOT (child.business_chain_no <=> root.business_chain_no);

  IF EXISTS (SELECT 1 FROM material_claim child
      JOIN material_claim root ON root.id = child.parent_claim_id
      WHERE child.claim_kind = 'CHILD'
        AND NOT (child.business_chain_no <=> root.business_chain_no)) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '父子业务链迁移完成校验失败：仍存在子单与父单业务链不一致';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'uk_material_claim_business_chain_no') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '父子业务链迁移完成校验失败：业务链唯一索引仍存在';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'idx_material_claim_business_chain_no') <> 1
     OR NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'material_claim'
        AND index_name = 'idx_material_claim_business_chain_no'
        AND non_unique = 1 AND column_name = 'business_chain_no') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '父子业务链迁移完成校验失败：业务链普通索引不正确';
  END IF;
  IF (SELECT COUNT(*) FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') <> 41 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '父子业务链迁移完成校验失败：表总数必须保持41';
  END IF;
END //
CALL migrate_item_claim_business_chain_share() //
DROP PROCEDURE migrate_item_claim_business_chain_share //
DELIMITER ;
