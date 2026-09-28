package cn.iocoder.yudao.module.merchant.job;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.merchant.service.apply.MerchantApplyService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 汇付进件轮询 Job：
 * 1. 进件中且提交超 15 分钟的申请单，主动查询汇付申请单状态兜底（回调可能丢失）
 * 2. 审核通过但商户未生效的申请单，重试业务开通
 *
 * 对应 infra_job 记录：id=12904，handler_name=huifuApplyPollJob，每 5 分钟执行一次
 */
@Component
public class HuifuApplyPollJob implements JobHandler {

    @Resource
    private MerchantApplyService merchantApplyService;

    @Override
    @TenantJob
    public String execute(String param) {
        merchantApplyService.pollApplyStatus();
        return "汇付进件轮询完成";
    }

}
