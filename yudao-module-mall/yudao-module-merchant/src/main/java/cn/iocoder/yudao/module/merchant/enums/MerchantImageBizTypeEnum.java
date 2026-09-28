package cn.iocoder.yudao.module.merchant.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商户资质图片类型枚举
 *
 * 取值 = 汇付图片上传接口的 file_type，见 docs/huifu/企业商户进件-KYC.md
 */
@Getter
@AllArgsConstructor
public enum MerchantImageBizTypeEnum {

    LEGAL_CERT_FRONT("F02", "法人身份证人像面"),
    LEGAL_CERT_BACK("F03", "法人身份证国徽面"),
    LICENSE("F07", "营业执照"),
    REG_ACCT("F08", "开户许可证"),
    SETTLE_CARD_FRONT("F13", "结算银行卡卡号面"),
    AUTH_ENTRUST("F15", "授权委托书"),
    SETTLE_CERT_FRONT("F55", "持卡人身份证人像面"),
    SETTLE_CERT_BACK("F56", "持卡人身份证国徽面"),
    /** 个人商户（小微）专用：负责人身份证人像面 */
    INDV_CERT_FRONT("F40", "负责人身份证人像面"),
    /** 个人商户（小微）专用：负责人身份证国徽面 */
    INDV_CERT_BACK("F41", "负责人身份证国徽面");

    /**
     * 汇付图片类型
     */
    private final String code;
    /**
     * 名称
     */
    private final String name;

}
