-- item 模块：耗材 SKU 编码统一置空（表结构不变，幂等）
-- 仅允许现行 new_system/new_system_test；固定资产 SKU 与资产编码不变。

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_item_consumable_sku_clear //
CREATE PROCEDURE migrate_item_consumable_sku_clear()
BEGIN
  IF DATABASE() NOT IN ('new_system', 'new_system_test') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '耗材SKU清理仅允许new_system/new_system_test';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables
      WHERE table_schema = DATABASE() AND table_name = 'material') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '耗材SKU清理前置校验失败：material不存在';
  END IF;

  UPDATE material
  SET sku_code = NULL
  WHERE material_type = 'CONSUMABLE' AND sku_code IS NOT NULL;

  IF EXISTS (SELECT 1 FROM material
      WHERE material_type = 'CONSUMABLE' AND sku_code IS NOT NULL) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '耗材SKU清理完成校验失败';
  END IF;
END //
CALL migrate_item_consumable_sku_clear() //
DROP PROCEDURE migrate_item_consumable_sku_clear //
DELIMITER ;
