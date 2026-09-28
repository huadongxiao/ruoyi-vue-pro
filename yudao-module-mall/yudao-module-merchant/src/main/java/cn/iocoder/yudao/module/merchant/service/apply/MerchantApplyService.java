package cn.iocoder.yudao.module.merchant.service.apply;

import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantApplySubmitReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.apply.MerchantApplyDO;

import java.util.List;

/**
 * 商户进件申请 Service 接口
 */
public interface MerchantApplyService {

    /**
     * 提交进件：图片逐张推汇付 → 企业商户进件（KYC）
     *
     * @param merchantId 商户编号
     * @param reqVO      进件表单
     * @return 申请单编号
     */
    Long submitApply(Long merchantId, MerchantApplySubmitReqVO reqVO);

    /**
     * 处理审核结果（回调 + 轮询共用，按申请单状态幂等）
     *
     * @param reqSeqId    汇付请求流水号
     * @param auditStatus 审核结果：P 审核中 / Y 通过 / N 拒绝 / F 失败
     * @param auditDesc   审核描述
     * @param rawJson     原始报文
     */
    void handleAuditResult(String reqSeqId, String auditStatus, String auditDesc, String rawJson);

    /**
     * 商户业务开通（审核通过后调用；商户已生效则幂等返回）
     *
     * @param apply 申请单
     */
    void openBusiness(MerchantApplyDO apply);

    /**
     * 获得商户的进件申请单列表
     *
     * @param merchantId 商户编号
     * @return 申请单列表
     */
    List<MerchantApplyDO> getApplyListByMerchantId(Long merchantId);

    /**
     * 轮询兜底 + 业务开通重试（定时任务调用）
     */
    void pollApplyStatus();

}
