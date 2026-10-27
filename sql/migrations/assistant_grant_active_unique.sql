-- ============================================================================
-- 实验室设备管理系统 · 助理授权「生效中唯一」兜底约束（B5）
-- ----------------------------------------------------------------------------
-- 背景：AssistantGrantServiceImpl.create 的防重为「先查后插」，高并发下同一老师
--       对同一学生可写入两条 ACTIVE 授权。本脚本在数据库层增加兜底唯一键：
--       生成列 active_pair_key 仅在 status='ACTIVE' 时取 teacher_id-student_id，
--       其余状态为 NULL（MySQL 唯一索引允许多个 NULL，不影响历史数据）。
-- 执行前提：assistant_grant 表已存在（assistant_grant.sql / lab_management_all.sql）。
-- 影响：并发双写时第二笔 INSERT 报重复键（1062），后端全局异常处理映射为 40900。
-- ============================================================================

ALTER TABLE assistant_grant
    ADD COLUMN active_pair_key VARCHAR(64) GENERATED ALWAYS AS (
        IF(status = 'ACTIVE' AND deleted = 0, CONCAT(teacher_id, '-', student_id), NULL)
    ) STORED COMMENT '生效中授权归一键（B5 防重兜底，勿写）' AFTER status,
    ADD UNIQUE KEY uk_assistant_grant_active_pair (active_pair_key);
