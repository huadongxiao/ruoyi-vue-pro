package cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商户分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantPageReqVO extends PageParam {

    @Schema(description = "商户名称", example = "芋道")
    private String name;

    @Schema(description = "状态：0草稿 1进件中 2生效 3拒绝 4停用", example = "2")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;

}
