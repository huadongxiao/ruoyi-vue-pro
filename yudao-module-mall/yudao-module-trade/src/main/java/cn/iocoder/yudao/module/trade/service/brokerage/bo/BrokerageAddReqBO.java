package cn.iocoder.yudao.module.trade.service.brokerage.bo;

import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 佣金 增加 Request BO
 *
 * @author owen
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrokerageAddReqBO {

    /**
     * 业务编号
     */
    @NotBlank(message = "业务编号不能为空")
    private String bizId;
    /**
     * 佣金基数
     */
    @NotNull(message = "佣金基数不能为空")
    private Integer basePrice;
    /**
     * 是否商品独立分销
     * <p>
     * 为 true 时使用 {@link #levels}，为 false 时使用全局配置
     */
    private Boolean subCommissionType;
    /**
     * 商品独立分销的层级规则（全量覆盖）。
     * 仅当 {@link #subCommissionType} 为 true 时生效；为空表示该商品未配置佣金，记为 0
     */
    private List<BrokerageLevelRule> levels;
    /**
     * 购买数量。固定佣金按单件配置，需乘以该值
     */
    @NotNull(message = "购买数量不能为空")
    private Integer count;
    /**
     * 来源用户编号
     */
    @NotNull(message = "来源用户编号不能为空")
    private Long sourceUserId;
    /**
     * 佣金记录标题
     */
    @NotEmpty(message = "佣金记录标题不能为空")
    private String title;

}
