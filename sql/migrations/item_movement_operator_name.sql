-- ----------------------------------------------------------------------------
-- 库存流水经办人姓名补列（2026-10-23 需求：新建库存流水须填写入库人/出库人个人姓名）
-- 适用：建库早于 2026-10-23 的旧库；全新建库无需执行（基线已含）
-- 幂等：information_schema 判存守卫，可重复执行
-- ----------------------------------------------------------------------------
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'stock_movement'
      AND COLUMN_NAME = 'operator_name'
);
SET @ddl = IF(@col_exists = 0,
    CONCAT('ALTER TABLE stock_movement ADD COLUMN operator_name VARCHAR(64) NULL ',
           'COMMENT ''经办人姓名（台账直接登记时填写；空则按单据 operator_id 解析）'' ',
           'AFTER movement_key'),
    'SELECT ''stock_movement.operator_name 已存在，跳过'' AS notice');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
