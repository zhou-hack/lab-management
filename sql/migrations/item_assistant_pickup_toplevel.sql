-- ============================================================================
-- 学生代理代领入口修复（2026-10-24）
-- ----------------------------------------------------------------------------
-- 背景：菜单 205「物料代领」挂在目录 151「物料管理」下，但 151 仅绑管理员角色，
--      持有效助理授权的学生（ASSISTANT 动态角色 4）菜单树中 205 成孤儿不可见；
--      且原 ItemAssistantPickup 复用管理端申领面板，学生视角无可用数据。
-- 变更：205 提为顶层菜单（与设备用户端 216-219 同风格），路径去 /admin 前缀。
-- 幂等：UPDATE 幂等可重放。
-- ============================================================================
SET time_zone = '+00:00';
SET NAMES utf8mb4;

UPDATE sys_menu
SET parent_id = NULL, route_path = '/item/assistant_pickup', sort_order = 27
WHERE id = 205;

-- 断言验证（预期 OK）
SELECT IF(parent_id IS NULL AND route_path = '/item/assistant_pickup',
        'OK  菜单 205 顶层化', 'FAIL 菜单 205') AS result
FROM sys_menu WHERE id = 205 AND deleted = 0;
