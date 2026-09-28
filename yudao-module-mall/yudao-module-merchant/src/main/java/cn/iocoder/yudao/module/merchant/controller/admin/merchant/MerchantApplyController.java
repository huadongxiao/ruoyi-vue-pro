package cn.iocoder.yudao.module.merchant.controller.admin.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantApplyRespVO;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantApplySubmitReqVO;
import cn.iocoder.yudao.module.merchant.service.apply.MerchantApplyService;
import cn.iocoder.yudao.module.merchant.service.merchant.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商户进件申请")
@RestController
@RequestMapping("/merchant/apply")
@Validated
public class MerchantApplyController {

    @Resource
    private MerchantApplyService merchantApplyService;

    @Resource
    private MerchantService merchantService;

    @PostMapping("/submit")
    @Operation(summary = "提交进件申请")
    @PreAuthorize("@ss.hasPermission('merchant:apply:submit')")
    public CommonResult<Long> submitApply(@Valid @RequestBody MerchantApplySubmitReqVO reqVO) {
        return success(merchantApplyService.submitApply(merchantService.getLoginMerchant().getId(), reqVO));
    }

    @GetMapping("/my-list")
    @Operation(summary = "获得我的进件申请列表")
    @PreAuthorize("@ss.hasPermission('merchant:apply:query')")
    public CommonResult<List<MerchantApplyRespVO>> getMyApplyList() {
        Long merchantId = merchantService.getLoginMerchant().getId();
        return success(BeanUtils.toBean(merchantApplyService.getApplyListByMerchantId(merchantId),
                MerchantApplyRespVO.class));
    }

}
