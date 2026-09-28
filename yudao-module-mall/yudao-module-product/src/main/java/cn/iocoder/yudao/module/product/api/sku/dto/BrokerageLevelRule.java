package cn.iocoder.yudao.module.product.api.sku.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 分销层级规则
 *
 * 放在 product 的 api 包：product 不依赖 trade，而 trade 已依赖 product，
 * 因此该类型必须由 product 侧持有，trade 通过 {@link ProductSkuRespDTO} 读取。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrokerageLevelRule {

    /**
     * 层级，从 1 开始连续
     */
    private Integer level;
    /**
     * 返佣比例，百分比数值。例如 10 表示 10%，允许小数
     */
    private BigDecimal percent;
    /**
     * 固定佣金，单位：分。单件商品的金额，结算时乘以购买数量
     */
    private Integer fixedPrice;

}
