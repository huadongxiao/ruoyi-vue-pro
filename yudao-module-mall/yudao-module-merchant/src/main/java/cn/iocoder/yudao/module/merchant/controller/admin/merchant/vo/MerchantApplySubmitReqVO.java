package cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * 商户进件提交 Request VO
 *
 * 企业商户字段对应 docs/huifu/企业商户进件-KYC.md（/v2/merchant/basicdata/ent）
 * 小微（个人）商户字段对应汇付个人商户进件（/v2/merchant/basicdata/indv）
 *
 * merchantType=1 企业：企业信息 + 法人信息 + 联系人 + 结算卡 + 营业执照(F07)/法人身份证(F02/F03)
 * merchantType=2 小微：负责人信息 + 经营信息 + 联系人 + 结算卡 + 负责人身份证(F40/F41) + 银行卡卡号面(F13)
 */
@Schema(description = "管理后台 - 商户进件提交 Request VO")
@Data
public class MerchantApplySubmitReqVO {

    // ========== 主体类型 ==========

    @Schema(description = "商户主体类型：1企业商户 2小微商户（个人）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "商户类型不能为空")
    private Integer merchantType;

    // ========== 企业信息（merchantType=1 必填；小微商户仅用 regName/shortName/mcc/sceneType/经营地） ==========

    @Schema(description = "商户名称（企业=执照名称；小微=负责人姓名）", requiredMode = Schema.RequiredMode.REQUIRED, example = "上海芋道科技有限公司")
    @NotEmpty(message = "商户名称不能为空")
    private String regName;

    @Schema(description = "商户简称（最少 4 个字符）", example = "芋道科技")
    private String shortName;

    @Schema(description = "小票名称（企业进件必填）", example = "芋道科技")
    private String receiptName;

    @Schema(description = "公司类型：1政府机构 2国营企业 3私营企业 4外资企业 5个体工商户 6其它组织 7事业单位 9业主委员会（企业必填）", example = "3")
    private String entType;

    @Schema(description = "经营类型：1实体 2虚拟（企业必填）", example = "2")
    private String busiType;

    @Schema(description = "所属行业（汇付 MCC 编码）", requiredMode = Schema.RequiredMode.REQUIRED, example = "5411")
    @NotEmpty(message = "所属行业不能为空")
    private String mcc;

    @Schema(description = "场景类型：ONLINE线上 OFFLINE线下 ALL线上线下", requiredMode = Schema.RequiredMode.REQUIRED, example = "ONLINE")
    @NotEmpty(message = "场景类型不能为空")
    private String sceneType;

    @Schema(description = "营业执照编号（企业必填）", example = "91310000MA1FL1234X")
    private String licenseCode;

    @Schema(description = "证照有效期类型：0非长期有效 1长期有效（企业必填）", example = "0")
    private String licenseValidityType;

    @Schema(description = "证照有效期开始 yyyyMMdd（企业必填）", example = "20200101")
    private String licenseBeginDate;

    @Schema(description = "证照有效期截止 yyyyMMdd（非长期有效时必填）", example = "20300101")
    private String licenseEndDate;

    @Schema(description = "成立时间 yyyyMMdd（企业必填）", example = "20200101")
    private String foundDate;

    @Schema(description = "注册区（地区码，企业必填）", example = "310104")
    private String regDistrictId;

    @Schema(description = "注册详细地址（企业必填）", example = "上海市徐汇区XX路1号")
    private String regDetail;

    @Schema(description = "经营区（地区码）", requiredMode = Schema.RequiredMode.REQUIRED, example = "310104")
    @NotEmpty(message = "经营区不能为空")
    private String districtId;

    @Schema(description = "经营详细地址（含线下场景必填）", example = "上海市徐汇区XX路1号")
    private String detailAddr;

    // ========== 负责人/法人信息 ==========

    @Schema(description = "法人姓名（企业必填；小微商户无需传，取 regName）", example = "张三")
    private String legalName;

    @Schema(description = "法人证件类型：00身份证（企业必填；小微只支持身份证）", example = "00")
    private String legalCertType;

    @Schema(description = "法人/负责人证件号码", requiredMode = Schema.RequiredMode.REQUIRED, example = "310112199001011234")
    @NotEmpty(message = "证件号码不能为空")
    private String legalCertNo;

    @Schema(description = "法人/负责人证件有效期类型：0非长期有效 1长期有效", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotEmpty(message = "证件有效期类型不能为空")
    private String legalCertValidityType;

    @Schema(description = "法人/负责人证件有效期开始 yyyyMMdd", requiredMode = Schema.RequiredMode.REQUIRED, example = "20150101")
    @NotEmpty(message = "证件有效期开始不能为空")
    private String legalCertBeginDate;

    @Schema(description = "法人/负责人证件有效期截止 yyyyMMdd（非长期有效时必填）", example = "20350101")
    private String legalCertEndDate;

    @Schema(description = "法人/负责人证件地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "上海市徐汇区XX路1号")
    @NotEmpty(message = "证件地址不能为空")
    private String legalAddr;

    // ========== 联系人信息 ==========

    @Schema(description = "联系人姓名", example = "李四")
    private String contactName;

    @Schema(description = "联系人手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
    @NotEmpty(message = "联系人手机号不能为空")
    private String contactMobileNo;

    @Schema(description = "联系人邮箱", requiredMode = Schema.RequiredMode.REQUIRED, example = "test@example.com")
    @NotEmpty(message = "联系人邮箱不能为空")
    private String contactEmail;

    @Schema(description = "汇付商户平台登录账号（全局唯一，英文/数字/下划线；企业必填，小微选填）", example = "yudao_merchant_1")
    private String loginName;

    // ========== 结算卡信息（card_info） ==========

    @Schema(description = "开户支行号", example = "305290002096")
    private String cardBranchCode;

    @Schema(description = "银行卡号", requiredMode = Schema.RequiredMode.REQUIRED, example = "6222021001000000001")
    @NotEmpty(message = "银行卡号不能为空")
    private String cardNo;

    @Schema(description = "银行卡户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "上海芋道科技有限公司")
    @NotEmpty(message = "银行卡户名不能为空")
    private String cardName;

    @Schema(description = "银行卡类型：0对私 1对公", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotEmpty(message = "银行卡类型不能为空")
    private String cardType;

    // ========== 资质图片（平台 infra 文件编号） ==========

    @Schema(description = "营业执照图片（F07，企业必填）")
    private Long licensePicId;

    @Schema(description = "法人身份证人像面（F02，企业必填）")
    private Long legalCertFrontId;

    @Schema(description = "法人身份证国徽面（F03，企业必填）")
    private Long legalCertBackId;

    @Schema(description = "负责人身份证人像面（F40，小微必填）")
    private Long indvCertFrontId;

    @Schema(description = "负责人身份证国徽面（F41，小微必填）")
    private Long indvCertBackId;

    @Schema(description = "开户许可证（F08，企业对公结算必填）")
    private Long regAcctId;

    @Schema(description = "结算银行卡卡号面（F13，对私必填；小微必填）")
    private Long settleCardFrontId;

    @Schema(description = "持卡人身份证人像面（F55，对私结算选传）")
    private Long settleCertFrontId;

    @Schema(description = "持卡人身份证国徽面（F56，对私结算选传）")
    private Long settleCertBackId;

    @Schema(description = "授权委托书（F15，对私非法人结算选传）")
    private Long authEntrustId;

}
