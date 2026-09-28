package cn.iocoder.yudao.module.merchant.controller.admin.huifu;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.merchant.framework.huifu.HuifuProperties;
import cn.iocoder.yudao.module.merchant.framework.huifu.HuifuSigner;
import cn.iocoder.yudao.module.merchant.service.apply.MerchantApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_CALLBACK_VERIFY_ERROR;

/**
 * 汇付回调 Controller
 *
 * 汇付异步回调无登录态、无租户上下文：方法标注 {@link TenantIgnore}，按全局唯一 req_seq_id 定位申请单。
 * 验签规则见 docs/huifu/接口加签验签说明.md（异步返参原文不排序）。
 */
@Tag(name = "管理后台 - 汇付回调")
@RestController
@RequestMapping("/merchant/huifu")
@Validated
@Slf4j
public class HuifuCallbackController {

    @Resource
    private HuifuProperties huifuProperties;

    @Resource
    private MerchantApplyService merchantApplyService;

    /**
     * 进件审核结果回调（KYC async_return_url）
     */
    @PostMapping("/kyc-callback")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "接收汇付进件审核结果回调")
    public String kycCallback(@RequestBody String rawBody) {
        log.info("[kycCallback][收到回调] raw={}", rawBody);
        Map<String, Object> body = JsonUtils.parseMap(rawBody);
        if (body == null) {
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        // 1. 原文提取 data（字符串或 JSON 对象），不重排字段
        String dataJson = extractDataJson(body, rawBody);
        // 2. 验签
        if (!HuifuSigner.verifyRaw(dataJson, huifuProperties.getHuifuPublicKey(), (String) body.get("sign"))) {
            log.error("[kycCallback][验签失败] raw={}", rawBody);
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        // 3. 处理审核结果（幂等）
        Map<String, Object> data = JsonUtils.parseMap(dataJson);
        if (data == null) {
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        merchantApplyService.handleAuditResult((String) data.get("req_seq_id"),
                (String) data.get("audit_status"), (String) data.get("audit_desc"), dataJson);
        return "success";
    }

    /**
     * 业务开通结果回调（busi_async_return_url）
     *
     * P1 仅验签应答：审核通过后的开通结果以同步受理为准（见 MerchantApplyService#openBusiness）
     */
    @PostMapping("/busi-callback")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "接收汇付业务开通结果回调")
    public String busiCallback(@RequestBody String rawBody) {
        log.info("[busiCallback][收到回调] raw={}", rawBody);
        Map<String, Object> body = JsonUtils.parseMap(rawBody);
        if (body == null) {
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        String dataJson = extractDataJson(body, rawBody);
        if (!HuifuSigner.verifyRaw(dataJson, huifuProperties.getHuifuPublicKey(), (String) body.get("sign"))) {
            log.error("[busiCallback][验签失败] raw={}", rawBody);
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        // 汇付要求应答 RECV_ORD_ID_{ord_id}（见 docs/huifu/商户业务开通.md）
        Map<String, Object> data = JsonUtils.parseMap(dataJson);
        String ordId = data != null ? (String) data.get("ord_id") : null;
        return StrUtil.isNotBlank(ordId) ? "RECV_ORD_ID_" + ordId : "success";
    }

    /**
     * 提取回调 data 的 JSON 文本：字符串直接使用，对象按原文重新序列化（不重排字段）
     */
    private static String extractDataJson(Map<String, Object> body, String rawBody) {
        Object dataObj = body.get("data");
        if (dataObj instanceof String) {
            return (String) dataObj;
        }
        if (dataObj == null) {
            return "{}";
        }
        return JsonUtils.parseTree(rawBody).get("data").toString();
    }

}
