-- ============================================================================
-- 移除借用功能 · 已建库增量清理脚本（dev / test 双库各执行一次）
-- ----------------------------------------------------------------------------
-- 背景：2026-10-14 产品定稿为纯管理员设备管理，无教师、无设备借用申请。
--   种子脚本（固定设备.sql / 计算机网络实训室种子数据.sql）已同步移除借用
--   权限与菜单；本脚本供已执行过旧版种子的库做幂等增量清理。
-- 处理内容（表结构按决策保留，仅清数据与绑定）：
--   1) 借用菜单 165–169、173 及其角色绑定；
--   2) 借用权限 155–161 及其角色绑定；
--   3) 借用链数据：equipment_usage / equipment_booking /
--      approval_task / document_event / biz_document（清空，表保留）。
-- 执行前提：
--   * 已执行旧版种子的 lab_management / lab_management_test；
--   * 会话时区 +00:00（SET time_zone = '+00:00'）。
-- 幂等性：全部为 DELETE，可重复执行。
-- ============================================================================

-- 1. 角色菜单绑定（先删绑定再删菜单，避免外键 RESTRICT）
DELETE FROM sys_role_menu WHERE menu_id IN (165, 166, 167, 168, 169, 173);

-- 2. 借用菜单（165 借用管理 / 166 前台设备服务 / 167 发起借用 / 168 我的借用 /
--    169 借用审批 / 173 前台首页）；sys_menu.parent_id 自引用外键，子先父后
DELETE FROM sys_menu WHERE id IN (167, 168, 169, 173);
DELETE FROM sys_menu WHERE id IN (165, 166);

-- 3. 角色权限绑定（按权限 ID 定位，覆盖种子 529–535 及任何手工绑定）
DELETE FROM sys_role_permission WHERE permission_id BETWEEN 155 AND 161;

-- 4. 借用权限（155–161，equipment:booking:*）
DELETE FROM sys_permission WHERE id BETWEEN 155 AND 161;

-- 5. 借用链数据（子表在前，父表在后；表结构保留）
DELETE FROM equipment_usage;
DELETE FROM equipment_booking;
DELETE FROM approval_task;
DELETE FROM document_event;
DELETE FROM biz_document;
