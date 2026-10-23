-- ============================================================================
-- item 模块：物料代领菜单 205 改绑 ASSISTANT 角色（幂等，可重复执行）
-- 版本：2026-10-22；背景：原绑 STUDENT 导致未授权学生也看到「物料代领」入口，
-- 操作时才被 assistant_grant 校验拒绝。改绑 ASSISTANT 后，后端装配菜单时
-- 动态 ASSISTANT 仅在有效授权期间并入（AuthServiceImpl.assembleRoleIds），
-- 未授权学生不显示该入口，有授权的学生代理可见；接口侧授权校验保持不变。
-- 全新建库无需执行（lab_management_all.sql 正本已为新口径）。
-- ============================================================================

-- 1) 菜单 205 由 STUDENT(3) 改绑 ASSISTANT(4)（绑定行 id=361；兼容旧库无该行的情况）
UPDATE sys_role_menu SET role_id = 4 WHERE id = 361 AND role_id = 3 AND menu_id = 205;
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES (361, 4, 205, 1);

-- 2) 清理可能的 STUDENT 侧重复绑定（历史库直接 INSERT 过 (role 3, menu 205) 的行）
DELETE FROM sys_role_menu WHERE role_id = 3 AND menu_id = 205;

-- 3) 校验（预期：物料代领仅绑 ASSISTANT=4 与 OPS_ADMIN=5，无 STUDENT 行）
-- SELECT rm.role_id, r.role_code, m.menu_name
-- FROM sys_role_menu rm
-- JOIN sys_menu m ON m.id = rm.menu_id
-- JOIN sys_role r ON r.id = rm.role_id
-- WHERE rm.menu_id = 205;
