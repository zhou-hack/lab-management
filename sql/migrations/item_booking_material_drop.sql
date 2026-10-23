-- ============================================================================
-- 迁移脚本：物料预约预登记功能全量下线（2026-10-22 需求定稿）
-- 适用：曾执行过含物料预约（lab_booking_material 表、权限 172-175、
--       菜单 216-220、绑定 267-270/370-374，或旧号 168-171）旧版基线
--       或 item_booking_material.sql 增量的库。
-- 幂等：可重复执行；DELETE 先按编号再按权限码/菜单码兜底清理，
--       DROP TABLE IF EXISTS。
-- 说明：预约单候选搜索接口随申领单保留（迁移至 material-requests/booking-options，
--       权限改挂 item:request:create），仅删除预登记业务本身；
--       预登记历史数据随表删除、不可恢复，执行前请先整库备份。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_booking_material_drop.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 回收菜单绑定（370-374=ADMIN 固定绑定 216-220；OPS_ADMIN 为执行时刻动态绑定，
--    绑定行 menu_id 指向 216-220 但 ID 不固定，须按 menu_id 兜底清理）
DELETE FROM sys_role_menu WHERE menu_id IN (216, 217, 218, 219, 220);
DELETE FROM sys_role_menu WHERE menu_id IN
    (SELECT id FROM sys_menu WHERE menu_code = 'ITEM_BOOKING_MATERIAL'
                                 OR menu_code LIKE 'item:booking_material:%');

-- 2) 回收权限绑定（267-270=ADMIN 固定绑定 172-175；OPS_ADMIN 同理按 permission_id 兜底）
DELETE FROM sys_role_permission WHERE permission_id IN (172, 173, 174, 175);
DELETE FROM sys_role_permission WHERE permission_id IN
    (SELECT id FROM sys_permission WHERE permission_code LIKE 'item:booking_material%');

-- 3) 删除物料预约菜单（216=页面；217-220=动作；旧号库可能为 168-171 段，按菜单码兜底）
--    先删子菜单后删父菜单：sys_menu 自引用外键 fk_sys_menu_parent(parent_id→id)，
--    单条批量 DELETE 在父行先处理时会报 1451，拆两条保证顺序。
DELETE FROM sys_menu WHERE id IN (217, 218, 219, 220);
DELETE FROM sys_menu WHERE id = 216;
DELETE FROM sys_menu WHERE menu_code LIKE 'item:booking_material:%';
DELETE FROM sys_menu WHERE menu_code = 'ITEM_BOOKING_MATERIAL';

-- 4) 删除物料预约权限（172-175；旧号库按权限码兜底）
DELETE FROM sys_permission WHERE id IN (172, 173, 174, 175);
DELETE FROM sys_permission WHERE permission_code LIKE 'item:booking_material%';

-- 5) DROP 预登记表（无子表依赖）
DROP TABLE IF EXISTS lab_booking_material;

-- 6) 校验：以下查询应均返回 0
-- SELECT COUNT(*) FROM sys_permission WHERE permission_code LIKE 'item:booking_material%'; -- 预期 0
-- SELECT COUNT(*) FROM sys_menu WHERE menu_code = 'ITEM_BOOKING_MATERIAL'
--    OR menu_code LIKE 'item:booking_material:%';                                        -- 预期 0
-- SELECT COUNT(*) FROM information_schema.TABLES
--  WHERE TABLE_SCHEMA='lab_management' AND TABLE_NAME='lab_booking_material';            -- 预期 0
