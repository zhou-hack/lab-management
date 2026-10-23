-- ============================================================================
-- 迁移脚本：学生预约（SELF）下线 + 老师审核环节移除（2026-10-22 需求定稿）
-- 背景：预约类型收敛为 老师预约(TEACHER)/学生代理代领(PROXY) 两类；
--       TEACHER 由老师本人发起、创建即 TEACHER_APPROVED（行批准量=申请量）；
--       PROXY 只能引用待终审的老师预约单（request_type=TEACHER、
--       status=TEACHER_APPROVED）；老师审核（teacher-approve/reject 接口与
--       item:request:teacher_approve 权限）整体移除，仅剩管理员终审一级。
-- 内容：
--   1) 清空历史 SELF 申领单及引用其作源单的 PROXY 代领单（含申领行；
--      含软删除行，否则新 CHECK 约束加不上）；出库单据行
--      stock_document_line.request_line_id 为信息性引用（无外键），
--      库存单据与台账历史保留不动。
--   2) 删除权限 167（item:request:teacher_approve）、菜单 215 及全部角色绑定。
--   3) CHECK 约束收紧：type 去 SELF、status 去 DRAFT/PENDING、source 仅 TEACHER 可无源单。
--   4) 列默认值与注释同步（request_type 默认 TEACHER、status 默认 TEACHER_APPROVED）。
-- 幂等：可重复执行（清数据/删绑定为自然幂等，DDL 用 information_schema 判定）。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_request_remove_self.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 清空历史 SELF 单及引用 SELF 源单的 PROXY 单
--    外键 RESTRICT：先删代领行（source_line_id 子行），再删源行，最后删主单
--    1a) 代领行（PROXY 单的行，其 source_line_id 指向 SELF 单的行）
DELETE FROM material_request_line
 WHERE source_line_id IS NOT NULL
   AND request_id IN (
       SELECT r.id FROM material_request r
        WHERE r.request_type = 'PROXY'
          AND r.source_request_id IN (
              SELECT s.id FROM material_request s WHERE s.request_type = 'SELF'
          )
   );

--    1b) 其余申领行（SELF 单的行等）
DELETE FROM material_request_line
 WHERE request_id IN (
     SELECT r.id FROM material_request r
      WHERE r.request_type = 'SELF'
         OR (r.request_type = 'PROXY'
             AND r.source_request_id IN (
                 SELECT s.id FROM material_request s WHERE s.request_type = 'SELF'
             ))
 );

--    1c) 主单：先删引用 SELF 源单的 PROXY 子单（外键 RESTRICT，子先父后），再删 SELF 单
--        （同表子查询须包一层派生表，规避 MySQL 1093）
DELETE FROM material_request
 WHERE request_type = 'PROXY'
   AND source_request_id IN (
       SELECT t.id FROM (
           SELECT id FROM material_request WHERE request_type = 'SELF'
       ) t
   );

DELETE FROM material_request
 WHERE request_type = 'SELF';

-- 2) 删除老师审核权限 167、菜单 215 及角色绑定（顺序：先绑定后主档）
DELETE FROM sys_role_menu       WHERE menu_id = 215;
DELETE FROM sys_menu            WHERE id = 215 AND menu_code = 'item:request:teacher_approve';
DELETE FROM sys_role_permission WHERE permission_id = 167;
DELETE FROM sys_permission      WHERE id = 167 AND permission_code = 'item:request:teacher_approve';

-- 3) CHECK 约束收紧（幂等：新定义已生效则跳过）
--    旧 type 含 SELF；新定义仅 ('PROXY','TEACHER')
SET @chk_type_new = (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS cc
    JOIN information_schema.TABLE_CONSTRAINTS tc
      ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = 'lab_management' AND tc.TABLE_NAME = 'material_request'
      AND tc.CONSTRAINT_NAME = 'chk_item_request_type' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%PROXY%' AND cc.CHECK_CLAUSE NOT LIKE '%SELF%');
SET @ddl = IF(@chk_type_new = 0,
    'ALTER TABLE material_request DROP CHECK chk_item_request_type, ADD CONSTRAINT chk_item_request_type CHECK (request_type IN (''PROXY'', ''TEACHER''))',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

--    旧 source 允许 SELF 无源单；新定义仅 TEACHER 可无源单
SET @chk_src_new = (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS cc
    JOIN information_schema.TABLE_CONSTRAINTS tc
      ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = 'lab_management' AND tc.TABLE_NAME = 'material_request'
      AND tc.CONSTRAINT_NAME = 'chk_item_request_source' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE NOT LIKE '%SELF%');
SET @ddl = IF(@chk_src_new = 0,
    'ALTER TABLE material_request DROP CHECK chk_item_request_source, ADD CONSTRAINT chk_item_request_source CHECK ((request_type = ''TEACHER'') OR (source_request_id IS NOT NULL))',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

--    旧 status 含 DRAFT/PENDING（两级审核遗留）；新定义仅六个终态流转值
SET @chk_status_new = (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS cc
    JOIN information_schema.TABLE_CONSTRAINTS tc
      ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = 'lab_management' AND tc.TABLE_NAME = 'material_request'
      AND tc.CONSTRAINT_NAME = 'chk_item_request_status' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE NOT LIKE '%DRAFT%' AND cc.CHECK_CLAUSE NOT LIKE '%PENDING%');
SET @ddl = IF(@chk_status_new = 0,
    'ALTER TABLE material_request DROP CHECK chk_item_request_status, ADD CONSTRAINT chk_item_request_status CHECK (status IN (''TEACHER_APPROVED'', ''APPROVED'', ''PARTIALLY_FULFILLED'', ''FULFILLED'', ''REJECTED'', ''CANCELLED''))',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 列默认值与注释同步（MODIFY 全量定义，重复执行安全）
ALTER TABLE material_request
    MODIFY COLUMN request_type VARCHAR(32) NOT NULL DEFAULT 'TEACHER'
        COMMENT '申领类型：TEACHER 老师预约 / PROXY 学生代理代领（2026-10-22 SELF 下线）',
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'TEACHER_APPROVED'
        COMMENT '状态：TEACHER_APPROVED/APPROVED/PARTIALLY_FULFILLED/FULFILLED/REJECTED/CANCELLED；TEACHER/PROXY 均创建即 TEACHER_APPROVED，仅管理员终审',
    MODIFY COLUMN student_no VARCHAR(32) NULL
        COMMENT '学生学号（PROXY=代理学生；TEACHER 为空）',
    MODIFY COLUMN student_name VARCHAR(64) NULL
        COMMENT '学生姓名（PROXY=代理学生；TEACHER 为空）',
    MODIFY COLUMN student_class_name VARCHAR(64) NULL
        COMMENT '学生班级（PROXY=代理学生填写；TEACHER 为空）';

-- 5) 校验（应分别返回 0 / 0 / 0 / 3 条约束）
-- SELECT COUNT(*) FROM material_request WHERE request_type = 'SELF';
-- SELECT COUNT(*) FROM sys_permission WHERE id = 167;
-- SELECT COUNT(*) FROM sys_menu WHERE id = 215;
-- SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS cc
--   JOIN information_schema.TABLE_CONSTRAINTS tc
--     ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
--  WHERE tc.TABLE_SCHEMA = 'lab_management' AND tc.TABLE_NAME = 'material_request'
--    AND tc.CONSTRAINT_NAME IN ('chk_item_request_type', 'chk_item_request_source', 'chk_item_request_status');
