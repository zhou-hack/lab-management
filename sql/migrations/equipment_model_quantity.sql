-- ============================================================================
-- 迁移：设备型号增加「数量」字段（2026-10-16 定稿，2026-10-23 收编整理）
-- 来源：固定设备管理分支 add_model_quantity.sql（幂等化改写）
-- 适用：2026-10-20 前建成、equipment_model 无 quantity 列的旧库；
--       2026-10-23 起全新建库无需执行（正本 3.2 节已含该列）。
-- 内容：
--   1. equipment_model 增加 quantity INT 列（规格说明之后、计量单位之前）；
--   2. 存量型号按未删除台账数回填（重复执行结果不变）。
-- 幂等：information_schema 判存守卫，可重复执行。
-- ============================================================================
USE lab_management;
SET NAMES utf8mb4;

-- 1. 型号表增加数量列（登记设备台数）
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'equipment_model' AND COLUMN_NAME = 'quantity'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE equipment_model ADD COLUMN quantity INT NOT NULL DEFAULT 0 COMMENT ''数量（登记设备台数）'' AFTER specification',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 存量型号按台账回填数量（未删除资产数）
UPDATE equipment_model m
SET m.quantity = (
    SELECT COUNT(*)
    FROM equipment_asset a
    WHERE a.model_id = m.id
      AND a.deleted = 0
);

-- 3) 校验：SHOW COLUMNS FROM equipment_model LIKE 'quantity';
