-- ============================================================================
-- 迁移：设备模块菜单更名（2026-10-16）
-- ============================================================================
-- 背景：产品定稿调整导航命名，菜单结构、路由、权限不变，仅改 menu_name：
--   * 目录 M_EQUIPMENT_ADMIN「设备管理」→「管理模块」；
--   * 页面 C_EQUIPMENT_MODEL「型号管理」→「设备管理」。
-- 本脚本可复现线上库（lab_management）变更，按 menu_code 定位、可重复执行；
--   dev 库与种子脚本菜单 ID 不同（161/163 vs 200/202），menu_code 两边一致。
-- 前端配套：侧栏目录图标按关键字兜底，需同步登记「管理模块」关键字。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 目录：设备管理 → 管理模块
UPDATE sys_menu
SET menu_name = '管理模块'
WHERE menu_code = 'M_EQUIPMENT_ADMIN'
  AND menu_type = 'DIRECTORY'
  AND deleted = 0;

-- 2. 页面：型号管理 → 设备管理
UPDATE sys_menu
SET menu_name = '设备管理'
WHERE menu_code = 'C_EQUIPMENT_MODEL'
  AND menu_type = 'PAGE'
  AND deleted = 0;
