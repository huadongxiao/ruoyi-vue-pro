package cn.iocoder.yudao.module.merchant.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商户状态枚举
 *
 * 0 草稿 → 1 进件中 → 2 生效 / 3 拒绝；2 可被平台改为 4 停用
 */
@Getter
@AllArgsConstructor
public enum MerchantStatusEnum {

    DRAFT(0, "草稿"),
    APPLYING(1, "进件中"),
    EFFECTIVE(2, "生效"),
    REJECTED(3, "拒绝"),
    DISABLED(4, "停用");

    /**
     * 状态
     */
    private final Integer status;
    /**
     * 名称
     */
    private final String name;

}
