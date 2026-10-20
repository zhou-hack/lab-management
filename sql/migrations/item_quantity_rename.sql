-- ============================================================================
-- 迁移脚本：stock_document_line.qty / material_settlement.qty 改名 quantity（2026-10-20）
-- 适用：建库早于 2026-10-20 的旧库（正本两表数量列为 qty）；
--      正本 2026-10-20 起与 Java 实体统一为 quantity，新库直接执行正本，勿执行本脚本。
-- 背景：Java 实体（release/0_2 与 material-manage 分支）字段为 quantity，
--      旧列名 qty 导致 MyBatis 映射查询报错；CHECK 约束引用旧列名，须先 DROP 再改名重建。
-- 幂等：列已是 quantity 则跳过（information_schema 判存）；CHECK 判存后再 DROP/ADD。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_quantity_rename.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) stock_document_line：qty → quantity
SET @has_qty = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'stock_document_line'
                  AND COLUMN_NAME = 'qty');
SET @has_chk = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'stock_document_line'
                  AND CONSTRAINT_NAME = 'chk_item_stock_doc_line_qty');
SET @ddl = IF(@has_chk > 0,
              'ALTER TABLE stock_document_line DROP CHECK chk_item_stock_doc_line_qty',
              'SELECT ''skip: chk_item_stock_doc_line_qty not exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_qty > 0,
              'ALTER TABLE stock_document_line CHANGE qty quantity DECIMAL(18,3) NOT NULL COMMENT ''数量（> 0，精度不超过 SKU.quantity_scale）''',
              'SELECT ''skip: stock_document_line.qty already renamed'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_chk = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'stock_document_line'
                  AND CONSTRAINT_NAME = 'chk_item_stock_doc_line_quantity');
SET @ddl = IF(@has_chk = 0,
              'ALTER TABLE stock_document_line ADD CONSTRAINT chk_item_stock_doc_line_quantity CHECK (quantity > 0)',
              'SELECT ''skip: chk_item_stock_doc_line_quantity exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) material_settlement：qty → quantity
SET @has_qty = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'material_settlement'
                  AND COLUMN_NAME = 'qty');
SET @has_chk = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'material_settlement'
                  AND CONSTRAINT_NAME = 'chk_item_settlement_qty');
SET @ddl = IF(@has_chk > 0,
              'ALTER TABLE material_settlement DROP CHECK chk_item_settlement_qty',
              'SELECT ''skip: chk_item_settlement_qty not exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@has_qty > 0,
              'ALTER TABLE material_settlement CHANGE qty quantity DECIMAL(18,3) NOT NULL COMMENT ''本次结算量（> 0）''',
              'SELECT ''skip: material_settlement.qty already renamed'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_chk = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'material_settlement'
                  AND CONSTRAINT_NAME = 'chk_item_settlement_quantity');
SET @ddl = IF(@has_chk = 0,
              'ALTER TABLE material_settlement ADD CONSTRAINT chk_item_settlement_quantity CHECK (quantity > 0)',
              'SELECT ''skip: chk_item_settlement_quantity exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 校验：以下查询应均返回 0
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA='lab_management' AND TABLE_NAME IN ('stock_document_line','material_settlement')
--    AND COLUMN_NAME='qty';   -- 预期 0
