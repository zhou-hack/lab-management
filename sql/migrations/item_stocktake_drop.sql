-- ============================================================================
-- 迁移脚本：盘点功能全量下线（2026-10-20，对应 2026-10-19 需求定稿）
-- 适用：曾执行过含盘点（stocktake 两表、权限 133-137/166、菜单 183/195-199/214、
--       绑定 233-237/262/333/345-349/364）旧版基线或增量脚本的库。
-- 幂等：可重复执行；DELETE 按编号范围清理，DROP TABLE IF EXISTS，
--       adjust_direction 列按 information_schema 判存后再 DROP。
-- 说明：库存差异后续改走 ADJUST 调账单（movement_type 保留 ADJUST 枚举）；
--       盘点历史数据（含行上已录入的实盘值与 adjust_direction 方向标记）随表删除、
--       不可恢复，执行前请先整库备份；stock_movement 中 ADJUST 类型历史流水保留不动。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_stocktake_drop.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 回收盘点菜单绑定（333=盘点页 183；345-349=动作 195-199；364=快照导出动作 214）
DELETE FROM sys_role_menu WHERE id IN (333, 345, 346, 347, 348, 349, 364);

-- 2) 回收盘点权限绑定（233-237=权限 133-137；262=权限 166）
DELETE FROM sys_role_permission WHERE id IN (233, 234, 235, 236, 237, 262);

-- 3) 删除盘点菜单（183=盘点页；195-199=盘点动作；214=快照导出动作）
--    先删子菜单后删父菜单：sys_menu 自引用外键 fk_sys_menu_parent(parent_id→id)，
--    单条批量 DELETE 在父行先处理时会报 1451，拆两条保证顺序。
DELETE FROM sys_menu WHERE id IN (195, 196, 197, 198, 199, 214);
DELETE FROM sys_menu WHERE id = 183;

-- 4) 删除盘点权限（133-137=盘点五项；166=盘点快照导出）
DELETE FROM sys_permission WHERE id IN (133, 134, 135, 136, 137, 166);

-- 5) DROP 盘点两表（先子表 stocktake_line 后父表 stocktake）
DROP TABLE IF EXISTS stocktake_line;
DROP TABLE IF EXISTS stocktake;

-- 6) 删除 stock_document_line.adjust_direction（盘点调账方向标记，盘点阶段专用；
--    曾执行过 2026-10-17 版 item_stock_transfer_permission.sql 或含该列旧基线的库才需要）
SET @has_col = (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'stock_document_line'
                  AND COLUMN_NAME = 'adjust_direction');
SET @ddl = IF(@has_col > 0,
              'ALTER TABLE stock_document_line DROP COLUMN adjust_direction',
              'SELECT ''skip: adjust_direction not exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 7) 校验：以下查询应均返回 0
-- SELECT COUNT(*) FROM sys_permission WHERE id IN (133,134,135,136,137,166);          -- 预期 0
-- SELECT COUNT(*) FROM sys_menu WHERE id IN (183,195,196,197,198,199,214);            -- 预期 0
-- SELECT COUNT(*) FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA='lab_management' AND TABLE_NAME IN ('stocktake','stocktake_line'); -- 预期 0
