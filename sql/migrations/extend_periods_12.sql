-- ============================================================================
-- booking 模块：节次扩展为一天 12 节 + 场地开放时段延长到 22:00（幂等，可重复执行）
-- 版本：2026-10-24；背景：学校作息为 12 节课，第 9-12 节为晚间（18:30 起），
-- 第 12 节 21:00-21:45 超出原开放结束 21:00；开放结束统一延长到 22:00（留 15 分钟
-- 清场缓冲），新建/导入场地的缺省值同步为 08:00~22:00。
-- 全新建库无需执行（lab_management_all.sql 正本已为新口径）。
-- ============================================================================

-- 1) 节次补 9-12（已存在则跳过）
INSERT IGNORE INTO class_period (id, period_no, period_name, start_clock, end_clock) VALUES
(9, 9, '第9节', '18:30:00', '19:15:00'),
(10, 10, '第10节', '19:20:00', '20:05:00'),
(11, 11, '第11节', '20:10:00', '20:55:00'),
(12, 12, '第12节', '21:00:00', '21:45:00');

-- 2) 全部场地开放结束延长到 22:00（仅收敛原默认 21:00 的行，自定义窗口不动）
UPDATE lab SET open_end = '22:00:00' WHERE open_end = '21:00:00' AND deleted = 0;

-- 3) 校验（预期：节次 12 行；lab 全部 open_end = 22:00）
-- SELECT period_no, period_name, start_clock, end_clock FROM class_period WHERE deleted = 0 ORDER BY period_no;
-- SELECT id, lab_name, open_start, open_end FROM lab WHERE deleted = 0;
