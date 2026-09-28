package cn.iocoder.yudao.module.merchant.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商户主体类型枚举
 *
 * 企业商户：有营业执照，走企业商户进件（/v2/merchant/basicdata/ent）
 * 小微商户（个人商户）：无营业执照，走个人商户进件（/v2/merchant/basicdata/indv）
 */
@Getter
@AllArgsConstructor
public enum MerchantTypeEnum {

    ENTERPRISE(1, "企业商户"),
    INDIVIDUAL(2, "小微商户");

    private final Integer type;
    private final String name;

}
