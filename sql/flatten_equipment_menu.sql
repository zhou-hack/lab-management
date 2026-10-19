-- ============================================================================
-- 迁移：设备管理菜单扁平化（2026-10-19）
-- ============================================================================
-- 产品定稿（与种子脚本 lab_management_all.sql 3.8 节一致）：设备管理不设子菜单，
-- 取消「管理模块目录 → 设备管理/设备台账两级」结构，顶级单页菜单「设备管理」
-- 路由 /admin/equipment，型号面板与设备台账面板均在该页内。
-- 本脚本适配既有线上库（菜单编号与种子不同，如 dev 库 161/163/164/170）：
--   * 逻辑删除目录 M_EQUIPMENT_ADMIN 与台账页 C_EQUIPMENT_ASSET 及其角色绑定；
--   * 型号页上提为顶级：挂管理端根 M_ADMIN_ROOT，路由改 /admin/equipment，
--     menu_code 对齐种子定稿 C_EQUIPMENT_ADMIN；
--   * 「设备登记」ACTION 挂到设备管理页下（保留按钮权限登记）。
-- 按菜单编码定位，可重复执行：二次运行时变量未命中（已删/已改码）自动空跑。
-- ============================================================================
SET NAMES utf8mb4;

SET @dir_id   := (SELECT id FROM sys_menu WHERE menu_code = 'M_EQUIPMENT_ADMIN' AND deleted = 0 LIMIT 1);
SET @asset_id := (SELECT id FROM sys_menu WHERE menu_code = 'C_EQUIPMENT_ASSET'  AND deleted = 0 LIMIT 1);
SET @page_id  := (SELECT id FROM sys_menu WHERE menu_code = 'C_EQUIPMENT_MODEL'  AND deleted = 0 LIMIT 1);
SET @root_id  := (SELECT id FROM sys_menu WHERE menu_code = 'M_ADMIN_ROOT'       AND deleted = 0 LIMIT 1);

-- 1. 逻辑删除目录与台账页及其角色绑定
UPDATE sys_menu      SET deleted = 1 WHERE id IN (@dir_id, @asset_id);
UPDATE sys_role_menu SET deleted = 1 WHERE menu_id IN (@dir_id, @asset_id);

-- 2. 设备管理页上提为顶级单页（component_key 必须为本地组件白名单键 EquipmentManage，
--    与种子 3.8 节及前端 router/dynamic.js 一致，误填路由路径会导致菜单被白名单跳过）
UPDATE sys_menu
SET parent_id     = @root_id,
    menu_code     = 'C_EQUIPMENT_ADMIN',
    route_path    = '/admin/equipment',
    component_key = 'EquipmentManage',
    sort_order    = 20
WHERE id = @page_id;

-- 3. 「设备登记」ACTION 改挂设备管理页
UPDATE sys_menu SET parent_id = @page_id
WHERE menu_code = 'F_EQUIPMENT_ASSET_CREATE' AND deleted = 0;

-- 4. 清理早期「分类管理」独立页残留（分类目录已并入型号面板抽屉；随目录删除成为孤儿）
UPDATE sys_menu SET deleted = 1
WHERE menu_code = 'C_EQUIPMENT_CATEGORY' AND deleted = 0;
