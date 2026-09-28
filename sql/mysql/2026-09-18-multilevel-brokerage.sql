-- ========================================================================
-- 多级分销改造：新增层级规则 JSON 列，并迁移存量二级数据
--
-- 兼容性：MySQL 5.7+（依赖 JSON_ARRAY / JSON_OBJECT）。其他数据库需改写方言。
-- 说明：旧列本期保留不删除，作为回滚余量；确认稳定后的下个版本再执行文件末尾注释掉的 DROP。
-- ========================================================================

-- 1. 全局配置：新增 brokerage_levels
ALTER TABLE trade_config
    ADD COLUMN brokerage_levels json NULL COMMENT '分销层级规则，数组长度即层级数';

-- 迁移存量二级配置：percent 沿用旧列，fixedPrice 记为 0（旧逻辑全局只走比例）
UPDATE trade_config
SET brokerage_levels = JSON_ARRAY(
        JSON_OBJECT('level', 1, 'percent', COALESCE(brokerage_first_percent, 0), 'fixedPrice', 0),
        JSON_OBJECT('level', 2, 'percent', COALESCE(brokerage_second_percent, 0), 'fixedPrice', 0)
    );

-- 2. 商品 SKU：新增 brokerage_levels
ALTER TABLE product_sku
    ADD COLUMN brokerage_levels json NULL COMMENT '分销层级规则，商品独立分销时全量覆盖全局';

-- 迁移存量独立分销佣金：percent 记为 0（旧逻辑商品只走固定佣金），fixedPrice 沿用旧列
UPDATE product_sku
SET brokerage_levels = JSON_ARRAY(
        JSON_OBJECT('level', 1, 'percent', 0, 'fixedPrice', COALESCE(first_brokerage_price, 0)),
        JSON_OBJECT('level', 2, 'percent', 0, 'fixedPrice', COALESCE(second_brokerage_price, 0))
    )
WHERE first_brokerage_price IS NOT NULL
   OR second_brokerage_price IS NOT NULL;

-- 3. 迁移正确性校验（人工执行核对）
-- 全局：brokerage_levels 中的 percent 应与旧列一致
--   SELECT id, brokerage_first_percent, brokerage_second_percent, brokerage_levels FROM trade_config;
-- 商品：迁移行数应等于旧列非空的行数（两个结果应相等）
--   SELECT (SELECT COUNT(*) FROM product_sku WHERE brokerage_levels IS NOT NULL) AS migrated,
--          (SELECT COUNT(*) FROM product_sku
--           WHERE first_brokerage_price IS NOT NULL OR second_brokerage_price IS NOT NULL) AS expected;

-- 4. 回滚余量：本期不删除旧列，下个版本再执行
-- ALTER TABLE trade_config DROP COLUMN brokerage_first_percent;
-- ALTER TABLE trade_config DROP COLUMN brokerage_second_percent;
-- ALTER TABLE product_sku DROP COLUMN first_brokerage_price;
-- ALTER TABLE product_sku DROP COLUMN second_brokerage_price;
