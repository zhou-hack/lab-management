-- ============================================================================
-- 迁移：设备型号增加「数量」字段（2026-10-16）
-- ============================================================================
-- 背景：产品定稿在设备型号上直接登记数量（配合计量单位阅读，如 8 台）：
--   * equipment_model 增加 quantity INT 列（规格说明之后、计量单位之前，与页面列序一致）；
--   * 新增/编辑型号接口与页面表单同步支持录入；
--   * 既有数据按台账回填（与当前未删除资产数一致，保证存量型号数值合理）。
-- 可复现线上库（lab_management）变更；ALTER 首次执行后重复执行会报列已存在，可忽略；
-- 回填 UPDATE 可重复执行（重复执行结果不变）。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 型号表增加数量列（登记设备台数）
ALTER TABLE equipment_model
    ADD COLUMN quantity INT NOT NULL DEFAULT 0 COMMENT '数量（登记设备台数）' AFTER specification;

-- 2. 存量型号按台账回填数量（未删除资产数）
UPDATE equipment_model m
SET m.quantity = (
    SELECT COUNT(*)
    FROM equipment_asset a
    WHERE a.model_id = m.id
      AND a.deleted = 0
);
