-- ============================================================================
-- 迁移脚本：lab_booking_material 物料预约预登记（2026-10-20 需求定稿新增，
--           基线 lab_management_all.sql 6.8 节已含；本脚本供建库早于该定稿的旧库补齐）
--   语义：预约单（lab_booking）+SKU 唯一的物料预登记，预登记不是出库、
--         不自动减库存；仅 PENDING/APPROVED 预约单可登记；唯一键不含 deleted，
--         删后重加由服务端复活软删行（deleted 置回 0 并更新数量）。
--   改号：权限原 168-171，2026-10-21 与 dev/database(9f757af) 整合时
--         equipment 报修占 168-169 撞号，改号 172-175（168-171 对 item 弃用）。
--         曾执行过旧版 168-171 种子的库须先跑本脚本清理旧号，
--         再执行 equipment_repair_renumber.sql（其 DELETE 168/169 不区分模块）。
--   幂等：表已存在则跳过（CREATE IF NOT EXISTS）；种子先 DELETE 后 INSERT
--         （先子后父防外键 1451；权限删除按 permission_code 守卫，
--         不误伤 equipment:repair 168-169），重复执行结果一致。
--   执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_booking_material.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 幂等建表 lab_booking_material
CREATE TABLE IF NOT EXISTS lab_booking_material (
    id             BIGINT        NOT NULL                COMMENT '主键（雪花 ID）',
    booking_id     BIGINT        NOT NULL                COMMENT '场地预约单（→ lab_booking.id）',
    sku_id         BIGINT        NOT NULL                COMMENT '物料（→ material_sku.id）',
    requested_qty  DECIMAL(18,3) NOT NULL                COMMENT '预登数量（> 0；生成申领单时作为默认申请量）',
    note           VARCHAR(512)  NULL                    COMMENT '备注（用途/要求说明）',
    create_time    DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time    DATETIME(3)   NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted        TINYINT       NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除（唯一键不含 deleted，删后重加由服务端复活本行）',
    create_user_id BIGINT        NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id BIGINT        NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_booking_material (booking_id, sku_id),
    KEY idx_lab_booking_material_sku (sku_id),
    CONSTRAINT fk_lab_booking_material_booking FOREIGN KEY (booking_id) REFERENCES lab_booking (id),
    CONSTRAINT fk_lab_booking_material_sku FOREIGN KEY (sku_id) REFERENCES material_sku (id),
    CONSTRAINT chk_lab_booking_material_qty CHECK (requested_qty > 0),
    CONSTRAINT chk_lab_booking_material_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物料预约预登记（预约单+SKU 唯一，预登记不减库存）';

-- 2) 种子幂等重放：先删后插（先子后父防外键 1451：
--    role_menu → role_permission → menu 子 → menu 父 → permission；
--    sys_menu 有自引用外键 fk_sys_menu_parent，删除须先子菜单后父菜单；
--    OPS_ADMIN(5) 动态绑定过旧号的须先解除指向绑定，否则权限/菜单删除触发 1451；
--    权限删除按 permission_code 守卫：兼容清理旧号 168-171，不误伤 equipment:repair 168-169）
DELETE FROM sys_role_menu WHERE id IN (370, 371, 372, 373, 374);
DELETE FROM sys_role_menu WHERE role_id = 5 AND menu_id IN (216, 217, 218, 219, 220);
DELETE FROM sys_role_permission WHERE id IN (267, 268, 269, 270);
DELETE op FROM sys_role_permission op
  JOIN sys_permission p ON p.id = op.permission_id
 WHERE op.role_id = 5 AND p.permission_code LIKE 'item:booking_material%';
DELETE FROM sys_menu WHERE id IN (217, 218, 219, 220);
DELETE FROM sys_menu WHERE id IN (216);
DELETE FROM sys_permission WHERE permission_code LIKE 'item:booking_material%';

-- 3) 权限：物料预约预登记（管理侧维护入口；老师侧预约流程嵌入待场地预约模块接入后补绑定）
INSERT INTO sys_permission (id, permission_code, permission_name, module_code, enabled) VALUES
(172, 'item:booking_material:list',   '物料预约查询', 'item', 1),
(173, 'item:booking_material:create', '物料预约登记', 'item', 1),
(174, 'item:booking_material:update', '物料预约修改', 'item', 1),
(175, 'item:booking_material:delete', '物料预约删除', 'item', 1);

-- 4) 菜单：页面并入物料工作台（停用保留，仿 180-182 模式），ACTION 挂页面下
INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, permission_id, menu_type, sort_order, enabled) VALUES
(216, 151, 'ITEM_BOOKING_MATERIAL',         '物料预约',     '/admin/item/booking_material', NULL, NULL, 'PAGE',   11, 0),
(217, 216, 'item:booking_material:list',   '物料预约查询', NULL,                          NULL, 172, 'ACTION', 1, 1),
(218, 216, 'item:booking_material:create', '物料预约登记', NULL,                          NULL, 173, 'ACTION', 2, 1),
(219, 216, 'item:booking_material:update', '物料预约修改', NULL,                          NULL, 174, 'ACTION', 3, 1),
(220, 216, 'item:booking_material:delete', '物料预约删除', NULL,                          NULL, 175, 'ACTION', 4, 1);

-- 5) 角色绑定：仅 ADMIN（老师侧预约嵌入待后续接入，届时另行补 TEACHER 绑定）
INSERT INTO sys_role_permission (id, role_id, permission_id, enabled) VALUES
(267, 1, 172, 1), (268, 1, 173, 1), (269, 1, 174, 1), (270, 1, 175, 1);

INSERT INTO sys_role_menu (id, role_id, menu_id, enabled) VALUES
(370, 1, 216, 1), (371, 1, 217, 1), (372, 1, 218, 1), (373, 1, 219, 1), (374, 1, 220, 1);

-- 5.1) 为 OPS_ADMIN(5) 补绑本次新增权限/菜单（动态全量绑定不覆盖后续迁移新增；
--      id 取当前最大值顺延，唯一索引 + NOT EXISTS 双保险幂等）
SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_permission);
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY p.id), 5, p.id, 1
FROM sys_permission p
WHERE p.permission_code LIKE 'item:booking_material%'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = 5 AND rp.permission_id = p.id);

SET @bid = (SELECT COALESCE(MAX(id), 5000) FROM sys_role_menu);
INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, enabled)
SELECT @bid + ROW_NUMBER() OVER (ORDER BY m.id), 5, m.id, 1
FROM sys_menu m
WHERE m.id IN (216, 217, 218, 219, 220)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm
                  WHERE rm.role_id = 5 AND rm.menu_id = m.id);

-- 6) 落地断言（全部应为 OK；任何 MISS 表示前序步骤未生效，须排查后重跑）
SELECT IF(COUNT(*) = 1, 'OK: table lab_booking_material', 'MISS: table lab_booking_material') AS check_table
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lab_booking_material';

SELECT IF(COUNT(*) = 4, 'OK: permission 172-175', 'MISS: permission 172-175') AS check_permission
FROM sys_permission WHERE id IN (172, 173, 174, 175);

SELECT IF(COUNT(*) = 5, 'OK: menu 216-220', 'MISS: menu 216-220') AS check_menu
FROM sys_menu WHERE id IN (216, 217, 218, 219, 220);

SELECT IF(COUNT(*) = 4, 'OK: role_permission 267-270', 'MISS: role_permission 267-270') AS check_role_permission
FROM sys_role_permission WHERE id IN (267, 268, 269, 270);

SELECT IF(COUNT(*) = 5, 'OK: role_menu 370-374', 'MISS: role_menu 370-374') AS check_role_menu
FROM sys_role_menu WHERE id IN (370, 371, 372, 373, 374);

SELECT IF(COUNT(*) = 0, 'OK: no stale booking_material on 168-171', 'WARN: stale booking_material rows on 168-171') AS check_stale
FROM sys_permission WHERE id IN (168, 169, 170, 171) AND permission_code LIKE 'item:booking_material%';
