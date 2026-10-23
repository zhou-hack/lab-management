-- ============================================================================
-- 迁移脚本：material_request 增加学生信息列（2026-10-22 需求定稿）
-- 背景：预约类型扩展为 老师预约(TEACHER)/学生预约(SELF)/学生代理代领(PROXY)；
--       学生预约与学生代理代领必须填写学生学号/姓名/班级（表单手填落库，
--       登录模块接入后可由会话自动带出）。
-- 幂等：可重复执行（先检查列存在再添加，information_schema 判定）。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_request_student_info.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 加列（幂等：列不存在才加）
SET @has_no = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'lab_management' AND TABLE_NAME = 'material_request' AND COLUMN_NAME = 'student_no');
SET @ddl = IF(@has_no = 0,
    'ALTER TABLE material_request ADD COLUMN student_no VARCHAR(32) NULL COMMENT ''学生学号（SELF=预约学生；PROXY=代理学生；TEACHER 为空；2026-10-22 新增）'' AFTER grant_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_name = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'lab_management' AND TABLE_NAME = 'material_request' AND COLUMN_NAME = 'student_name');
SET @ddl = IF(@has_name = 0,
    'ALTER TABLE material_request ADD COLUMN student_name VARCHAR(64) NULL COMMENT ''学生姓名（SELF=预约学生；PROXY=代理学生；2026-10-22 新增）'' AFTER student_no',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_class = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'lab_management' AND TABLE_NAME = 'material_request' AND COLUMN_NAME = 'student_class_name');
SET @ddl = IF(@has_class = 0,
    'ALTER TABLE material_request ADD COLUMN student_class_name VARCHAR(64) NULL COMMENT ''学生班级（SELF/PROXY 填写；2026-10-22 新增）'' AFTER student_name',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 预约类型 CHECK 约束纳入 TEACHER（幂等：新定义已生效则跳过）
--    旧定义 chk_item_request_type 仅 ('SELF','PROXY')、chk_item_request_source 仅 SELF 可无源单，
--    TEACHER 类型（无源单）会被旧约束拒绝（HTTP 50000）。
SET @chk_type_new = (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS cc
    JOIN information_schema.TABLE_CONSTRAINTS tc
      ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = 'lab_management' AND tc.TABLE_NAME = 'material_request'
      AND tc.CONSTRAINT_NAME = 'chk_item_request_type' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%TEACHER%');
SET @ddl = IF(@chk_type_new = 0,
    'ALTER TABLE material_request DROP CHECK chk_item_request_type, ADD CONSTRAINT chk_item_request_type CHECK (request_type IN (''SELF'', ''PROXY'', ''TEACHER''))',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @chk_src_new = (SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS cc
    JOIN information_schema.TABLE_CONSTRAINTS tc
      ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
    WHERE tc.TABLE_SCHEMA = 'lab_management' AND tc.TABLE_NAME = 'material_request'
      AND tc.CONSTRAINT_NAME = 'chk_item_request_source' AND tc.CONSTRAINT_TYPE = 'CHECK'
      AND cc.CHECK_CLAUSE LIKE '%TEACHER%');
SET @ddl = IF(@chk_src_new = 0,
    'ALTER TABLE material_request DROP CHECK chk_item_request_source, ADD CONSTRAINT chk_item_request_source CHECK ((request_type IN (''SELF'', ''TEACHER'')) OR (source_request_id IS NOT NULL))',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 校验：应返回 3
-- SELECT COUNT(*) FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA='lab_management' AND TABLE_NAME='material_request'
--    AND COLUMN_NAME IN ('student_no','student_name','student_class_name');
