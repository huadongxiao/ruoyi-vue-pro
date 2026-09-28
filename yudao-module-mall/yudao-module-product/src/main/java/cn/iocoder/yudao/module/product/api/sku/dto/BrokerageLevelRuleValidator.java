package cn.iocoder.yudao.module.product.api.sku.dto;

import cn.hutool.core.collection.CollUtil;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分销层级规则校验工具
 *
 * @author owen
 */
public class BrokerageLevelRuleValidator {

    /**
     * 分销层级上限
     */
    public static final int MAX_LEVEL = 10;

    /**
     * 校验层级规则是否合法
     * <p>
     * 要求：层级从 1 连续递增、比例在 0-100、固定佣金不小于 0、数量不超过 {@link #MAX_LEVEL}
     *
     * @param rules 层级规则
     * @return 是否合法
     */
    public static boolean isValid(List<BrokerageLevelRule> rules) {
        if (CollUtil.isEmpty(rules) || rules.size() > MAX_LEVEL) {
            return false;
        }
        for (int i = 0; i < rules.size(); i++) {
            BrokerageLevelRule rule = rules.get(i);
            // 层级必须从 1 连续递增
            if (rule == null || rule.getLevel() == null || rule.getLevel() != i + 1) {
                return false;
            }
            // 比例必须在 0-100
            BigDecimal percent = rule.getPercent();
            if (percent == null || percent.compareTo(BigDecimal.ZERO) < 0
                    || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                return false;
            }
            // 固定佣金不能为负
            if (rule.getFixedPrice() != null && rule.getFixedPrice() < 0) {
                return false;
            }
        }
        return true;
    }

}
