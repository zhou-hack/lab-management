-- ============================================================================
-- 迁移脚本：领用定额独立菜单下线（2026-10-22 需求定稿）
-- 背景：领用定额并入「物料预约」页面页签（前端 RequestPanel 内 el-tabs），
--       左侧菜单不再单列「领用定额」入口。
-- 内容：
--   1) 删角色-菜单绑定：菜单 181/189-192 的全部绑定（ADMIN 331/339-342；
--      OPS_ADMIN 为动态绑定，随菜单删除自动失效，无需单独处理）。
--   2) 删菜单：动作 189-192（子）→ 页面 181（父），先子后父规避自引用 1451。
--   3) 权限 127-130（item:quota:*）保留——后端接口与前端 v-permission 仍在使用。
-- 幂等：可重复执行（DELETE 按编号/编码双路清理，天然幂等）。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_quota_menu_merge.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 角色-菜单绑定清理（按菜单编号 + 菜单编码双路，兜底其他角色绑定）
DELETE FROM sys_role_menu WHERE menu_id IN (181, 189, 190, 191, 192);
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT t.id FROM (
        SELECT id FROM sys_menu
         WHERE menu_code IN ('ITEM_QUOTA', 'item:quota:list', 'item:quota:create',
                             'item:quota:update', 'item:quota:enable')
    ) t
);

-- 2) 菜单清理：先删动作子菜单，再删页面父菜单（自引用外键须子先父后）
DELETE FROM sys_menu
 WHERE menu_code IN ('item:quota:list', 'item:quota:create',
                     'item:quota:update', 'item:quota:enable')
   AND menu_type = 'ACTION';
DELETE FROM sys_menu
 WHERE menu_code = 'ITEM_QUOTA'
   AND menu_type = 'PAGE';

-- 3) 校验（应均返回 0）
-- SELECT COUNT(*) FROM sys_menu WHERE menu_code LIKE 'item:quota:%' OR menu_code = 'ITEM_QUOTA';
-- SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (181, 189, 190, 191, 192);
-- SELECT COUNT(*) FROM sys_permission WHERE permission_code LIKE 'item:quota:%';  -- 应为 4（保留）
