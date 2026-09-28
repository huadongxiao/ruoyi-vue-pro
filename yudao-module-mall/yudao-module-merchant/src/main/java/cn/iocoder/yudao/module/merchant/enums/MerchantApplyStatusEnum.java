package cn.iocoder.yudao.module.merchant.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商户进件申请单状态枚举
 *
 * 提交时先落 4 提交失败（留痕、可重试）；汇付受理后 0 进件中；审核结果 1 通过 / 2 拒绝 / 3 失败
 */
@Getter
@AllArgsConstructor
public enum MerchantApplyStatusEnum {

    DOING(0, "进件中"),
    APPROVED(1, "通过"),
    REJECTED(2, "拒绝"),
    FAILED(3, "失败"),
    SUBMIT_FAILED(4, "提交失败");

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 名称
     */
    private final String name;

}
