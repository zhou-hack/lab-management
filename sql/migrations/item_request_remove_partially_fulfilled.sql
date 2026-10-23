-- ============================================================================
-- 迁移脚本：「部分实发」派生状态下线（2026-10-22 需求定稿）
-- 背景：物料预约单状态收敛为五值——TEACHER_APPROVED/APPROVED/FULFILLED/
--       REJECTED/CANCELLED。PARTIALLY_FULFILLED 为出库进度的派生态，
--       与 APPROVED 行为完全一致（可继续实发），信息冗余：
--       实发进度由申领行 fulfilled_qty/approved_qty 表达，全部行发完
--       才推进 FULFILLED；部分实发期间单据保持 APPROVED。
-- 内容：
--   1) 历史数据归位：PARTIALLY_FULFILLED → APPROVED（含软删除行，
--      否则新 CHECK 约束加不上）。
--   2) CHECK 约束 chk_item_request_status 收紧为五值。
--   3) status 列注释同步（五值）。
-- 幂等：可重复执行（UPDATE 为自然幂等，DDL 用 information_schema 判定）。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_request_remove_partially_fulfilled.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 历史数据归位：部分实发 → 已批准（新模型下两者行为等价，可继续实发）
UPDATE material_request SET status = 'APPROVED' WHERE status = 'PARTIALLY_FULFILLED';

-- 2) CHECK 约束收紧（幂等：新定义已生效则跳过）
--    旧 status 含 PARTIALLY_FULFILLED；新定义仅五个流转值
SET @chk_status_new = (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS cc
    JOIN information_schema.TABLE_CONSTRAINTS tc
      ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = 'lab_management' AND tc.TABLE_NAME = 'material_request'
      AND tc.CONSTRAINT_NAME = 'chk_item_request_status' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE NOT LIKE '%PARTIALLY_FULFILLED%');
SET @ddl = IF(@chk_status_new = 0,
    'ALTER TABLE material_request DROP CHECK chk_item_request_status, ADD CONSTRAINT chk_item_request_status CHECK (status IN (''TEACHER_APPROVED'', ''APPROVED'', ''FULFILLED'', ''REJECTED'', ''CANCELLED''))',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) status 列注释同步（MODIFY 全量定义，重复执行安全）
ALTER TABLE material_request
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'TEACHER_APPROVED'
        COMMENT '状态：TEACHER_APPROVED/APPROVED/FULFILLED/REJECTED/CANCELLED；TEACHER/PROXY 均创建即 TEACHER_APPROVED，仅管理员终审（「部分实发」派生态已砍除，实发进度看申领行 fulfilled_qty）';

-- 4) 校验（应返回 0 / 1 条约束）
-- SELECT COUNT(*) FROM material_request WHERE status = 'PARTIALLY_FULFILLED';
-- SELECT cc.CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS cc
--   JOIN information_schema.TABLE_CONSTRAINTS tc
--     ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
--  WHERE tc.TABLE_SCHEMA = 'lab_management' AND tc.TABLE_NAME = 'material_request'
--    AND tc.CONSTRAINT_NAME = 'chk_item_request_status';
