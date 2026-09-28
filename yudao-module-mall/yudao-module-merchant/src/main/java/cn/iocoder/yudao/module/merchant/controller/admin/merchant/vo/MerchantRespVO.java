package cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 商户 Response VO
 */
@Schema(description = "管理后台 - 商户 Response VO")
@Data
public class MerchantRespVO {

    @Schema(description = "商户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "绑定的 admin 用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "商户名称", example = "李四的小铺")
    private String name;

    @Schema(description = "商户简称", example = "小铺")
    private String shortName;

    @Schema(description = "店铺 LOGO", example = "https://example.com/logo.png")
    private String logo;

    @Schema(description = "备注", example = "备注信息")
    private String remark;

    @Schema(description = "状态：0草稿 1进件中 2生效 3拒绝 4停用", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "主体类型：1企业商户 2小微商户", example = "1")
    private Integer merchantType;

    @Schema(description = "汇付商户号", example = "6666000000000001")
    private String huifuId;

    @Schema(description = "外部商户号（= 本表 id）", example = "1")
    private String extMerId;

    @Schema(description = "联系人姓名", example = "李四")
    private String contactName;

    @Schema(description = "联系人手机号", example = "13800000000")
    private String contactMobile;

    @Schema(description = "联系人邮箱", example = "lisi@example.com")
    private String contactEmail;

    @Schema(description = "结算卡号（脱敏）", example = "6222**********1234")
    private String settleCardNoMasked;

    @Schema(description = "最近进件提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "商户生效时间")
    private LocalDateTime effectTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
