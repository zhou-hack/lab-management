-- ============================================================================
-- 迁移脚本：stock_location 补保管人列 custodian_id（2026-10-19 需求定稿新增，
--           基线 lab_management_all.sql 4.3 节已含；本脚本供建库早于该定稿的旧库补齐）
--   语义：每库位一名保管人（→ sys_user.id），库存流水/库位台账展示用。
--   幂等：列已存在则跳过（information_schema 判存）；外键已存在则跳过。
--   执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_location_custodian.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 幂等补列 custodian_id
SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'stock_location'
                  AND COLUMN_NAME = 'custodian_id');
SET @ddl = IF(@has_col = 0,
              'ALTER TABLE stock_location ADD COLUMN custodian_id BIGINT NULL COMMENT ''保管人（→ sys_user.id；每库位一名，2026-10-19 需求定稿新增）'' AFTER location_detail',
              'SELECT ''skip: custodian_id exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 幂等补外键 fk_item_location_custodian（与基线定义一致）
SET @has_fk = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'stock_location'
                 AND CONSTRAINT_NAME = 'fk_item_location_custodian');
SET @ddl = IF(@has_fk = 0,
              'ALTER TABLE stock_location ADD CONSTRAINT fk_item_location_custodian FOREIGN KEY (custodian_id) REFERENCES sys_user (id)',
              'SELECT ''skip: fk_item_location_custodian exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
