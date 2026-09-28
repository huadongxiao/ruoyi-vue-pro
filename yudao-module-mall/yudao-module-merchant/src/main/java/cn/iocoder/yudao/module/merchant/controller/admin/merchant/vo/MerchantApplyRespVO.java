package cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台 - 商户进件申请 Response VO
 */
@Schema(description = "管理后台 - 商户进件申请 Response VO")
@Data
public class MerchantApplyRespVO {

    @Schema(description = "申请单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "商户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long merchantId;

    @Schema(description = "汇付请求流水号", example = "M202609281200000001ABCD")
    private String reqSeqId;

    @Schema(description = "汇付请求日期", example = "20260928")
    private String reqDate;

    @Schema(description = "汇付申请单号", example = "APL123456789")
    private String applyNo;

    @Schema(description = "状态：0进件中 1通过 2拒绝 3失败 4提交失败", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "汇付审核结果：Y 通过 / N 拒绝 / F 失败", example = "Y")
    private String auditStatus;

    @Schema(description = "汇付审核描述", example = "审核通过")
    private String auditDesc;

    @Schema(description = "汇付商户号", example = "6666000000000001")
    private String huifuId;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "审核时间")
    private LocalDateTime auditTime;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
