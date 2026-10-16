-- ============================================================================
-- 迁移：移除空间管理功能（2026-10-16）
-- ============================================================================
-- 背景：产品定稿删除空间管理模块（校区/楼栋/实验室三级维护），应用层已同步：
--   * 后端 module/space 整包删除，/api/v1/space/** 接口下线；
--   * 前端 SpaceManage 页面、侧栏菜单入口、首页统计与快捷入口移除；
--   * 设备台账解除实验室依赖（新增/迁移不再校验实验室，接口不再返回 labId/labName）。
-- 本脚本可复现线上库（lab_management）变更，语句均可重复执行：
--   1. 菜单与角色绑定逻辑删除（「基础数据」目录 121 仅含空间管理，一并移除）；
--   2. space:* 权限（101-112）与角色权限绑定（501-512）逻辑删除；
--   3. equipment_asset.lab_id、equipment_location_history.to_lab_id 放宽为可空。
-- 数据保留说明：campus / building / lab 三表及既有数据保留，不做物理删除；
--   equipment_asset.lab_id 等历史列保留但应用不再读写，后续如需物理清理另行迁移。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 菜单：基础数据目录 + 空间管理页 + 三个操作按钮（逻辑删除）
UPDATE sys_menu SET deleted = 1 WHERE id IN (121, 122, 123, 124, 125);

-- 2. 菜单-角色绑定（逻辑删除）
UPDATE sys_role_menu SET deleted = 1 WHERE id IN (601, 602, 603, 604, 605);

-- 3. space:* 权限（逻辑删除）
UPDATE sys_permission SET deleted = 1 WHERE id BETWEEN 101 AND 112;

-- 4. 权限-角色绑定（逻辑删除）
UPDATE sys_role_permission SET deleted = 1 WHERE id BETWEEN 501 AND 512;

-- 5. 实验室历史列放宽可空（NOT NULL → NULL；历史数据保留，应用不再写入）
ALTER TABLE equipment_asset
    MODIFY lab_id BIGINT NULL COMMENT '保管实验室（历史列：空间模块已移除，不再读写）';
ALTER TABLE equipment_location_history
    MODIFY to_lab_id BIGINT NULL COMMENT '新实验室（历史列：空间模块已移除，不再读写）';
