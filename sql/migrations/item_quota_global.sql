-- ============================================================================
-- 迁移脚本：领用定额转全局定额（2026-10-22 需求定稿）
-- 背景：定额不再按开课实例分组——批量登记弹窗去除「开课实例 ID」输入，
--       每物料全局唯一一条定额；offering_id 保留列固定 0。
-- 内容：
--   1) 物理去重：同一 sku_id 多行（挂多个旧实例 / 软删占位行）时保留 id 最大一行。
--      注：唯一约束 uk_item_quota(offering_id, sku_id) 不含 deleted，
--      软删行也占约束位，去重必须物理删除。
--   2) offering_id 全部归一为 0（全局分组）。
--   3) 列默认值与注释更新为全局定额语义。
-- 幂等：可重复执行（去重与归一在第二遍均为空操作；MODIFY 注释幂等）。
-- 执行：mysql --default-character-set=utf8mb4 -uroot -p < sql/migrations/item_quota_global.sql
-- ============================================================================
USE lab_management;

SET NAMES utf8mb4;

-- 1) 同物料去重（含软删占位行），保留最新一条
DELETE q FROM material_quota q
JOIN (
    SELECT sku_id, MAX(id) AS keep_id
    FROM material_quota
    GROUP BY sku_id
    HAVING COUNT(*) > 1
) d ON q.sku_id = d.sku_id AND q.id < d.keep_id;

-- 2) 归一到全局分组 0
UPDATE material_quota SET offering_id = 0 WHERE offering_id <> 0;

-- 3) 列注释与默认值更新（全局定额语义）
ALTER TABLE material_quota
    MODIFY COLUMN offering_id BIGINT NOT NULL DEFAULT 0
    COMMENT '全局定额保留字段，固定 0（2026-10-22 定稿：定额不再按开课实例分组）';

-- 校验（应返回 0 行：无重复物料、无非 0 分组）
-- SELECT sku_id FROM material_quota GROUP BY sku_id HAVING COUNT(*) > 1;
-- SELECT COUNT(*) FROM material_quota WHERE offering_id <> 0;
