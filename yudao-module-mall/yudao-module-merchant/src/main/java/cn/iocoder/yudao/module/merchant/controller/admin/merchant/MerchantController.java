package cn.iocoder.yudao.module.merchant.controller.admin.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantRespVO;
import cn.iocoder.yudao.module.merchant.service.merchant.MerchantService;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 商户")
@RestController
@RequestMapping("/merchant/merchant")
@Validated
public class MerchantController {

    @Resource
    private MerchantService merchantService;

    @PostMapping("/init")
    @Operation(summary = "初始化我的商户（注册后调用，幂等）")
    public CommonResult<Long> initMerchant() {
        return success(merchantService.initMerchant(SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/my")
    @Operation(summary = "获得我的商户")
    public CommonResult<MerchantRespVO> getMyMerchant() {
        return success(BeanUtils.toBean(merchantService.getLoginMerchant(), MerchantRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得商户分页")
    @PreAuthorize("@ss.hasPermission('merchant:merchant:query')")
    public CommonResult<PageResult<MerchantRespVO>> getMerchantPage(@Valid MerchantPageReqVO pageReqVO) {
        return success(BeanUtils.toBean(merchantService.getMerchantPage(pageReqVO), MerchantRespVO.class));
    }

    @PutMapping("/update-status")
    @Operation(summary = "停用/启用商户")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @Parameter(name = "status", description = "状态：2生效 4停用", required = true, example = "2")
    @PreAuthorize("@ss.hasPermission('merchant:merchant:update')")
    public CommonResult<Boolean> updateMerchantStatus(@RequestParam("id") Long id,
                                                      @RequestParam("status") Integer status) {
        merchantService.updateMerchantStatus(id, status);
        return success(true);
    }

}
