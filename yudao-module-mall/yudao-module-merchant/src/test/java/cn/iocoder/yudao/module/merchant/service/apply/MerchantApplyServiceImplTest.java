package cn.iocoder.yudao.module.merchant.service.apply;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantApplySubmitReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.apply.MerchantApplyDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.apply.MerchantApplyMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.merchant.enums.MerchantApplyStatusEnum;
import cn.iocoder.yudao.module.merchant.enums.MerchantStatusEnum;
import cn.iocoder.yudao.module.merchant.framework.huifu.HuifuClient;
import cn.iocoder.yudao.module.merchant.framework.huifu.HuifuProperties;
import cn.iocoder.yudao.module.merchant.service.merchant.MerchantService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_RESP_CODE_ERROR;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_APPLY_EXISTS_DOING;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_APPLY_PARAM_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MerchantApplyServiceImpl} 的单元测试类
 *
 * 汇付、文件、商户 Service 均以 Mock 替代，聚焦「提交 → 审核收敛 → 业务开通」状态机。
 */
@Import({MerchantApplyServiceImpl.class, MerchantApplyServiceImplTest.TestConfig.class})
public class MerchantApplyServiceImplTest extends BaseDbUnitTest {

    @TestConfiguration
    public static class TestConfig {

        @Bean
        public HuifuProperties huifuProperties() {
            HuifuProperties properties = new HuifuProperties();
            properties.setKycCallbackUrl("http://localhost/admin-api/merchant/huifu/kyc-callback");
            properties.setBusiCallbackUrl("http://localhost/admin-api/merchant/huifu/busi-callback");
            properties.setWxConfList("[{\"pay_scene\":\"1\",\"fee_rate\":\"0.38\"}]");
            return properties;
        }

    }

    @Resource
    private MerchantApplyServiceImpl applyService;

    @Resource
    private MerchantApplyMapper merchantApplyMapper;

    @Resource
    private MerchantMapper merchantMapper;

    @MockBean
    private HuifuClient huifuClient;

    @MockBean
    private FileApi fileApi;

    @MockBean
    private MerchantService merchantService;

    @MockBean
    private ConfigApi configApi;

    @Test
    public void testSubmitApply_blockedByDoing() {
        // mock 数据：商户 + 一条进件中的申请单
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.DRAFT.getStatus(), "");
        insertApply(merchant.getId(), MerchantApplyStatusEnum.DOING);
        Mockito.when(merchantService.validateMerchantExists(merchant.getId())).thenReturn(merchant);

        // 准备参数
        MerchantApplySubmitReqVO reqVO = randomSubmitReqVO();

        // 调用，并断言异常
        assertServiceException(() -> applyService.submitApply(merchant.getId(), reqVO), MERCHANT_APPLY_EXISTS_DOING);
    }

    @Test
    public void testSubmitApply_success() throws Exception {
        // mock 数据
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.DRAFT.getStatus(), "");
        Mockito.when(merchantService.validateMerchantExists(merchant.getId())).thenReturn(merchant);
        mockUploadSuccess();
        mockEntOpenSuccess();

        // 准备参数
        MerchantApplySubmitReqVO reqVO = randomSubmitReqVO();

        // 调用
        Long applyId = applyService.submitApply(merchant.getId(), reqVO);

        // 断言：申请单进件中 + 商户进件中
        MerchantApplyDO apply = merchantApplyMapper.selectById(applyId);
        assertEquals(MerchantApplyStatusEnum.DOING.getStatus(), apply.getStatus());
        assertEquals("2022011100377000", apply.getApplyNo());
        assertEquals("6666000100000001", apply.getHuifuId());
        MerchantDO dbMerchant = merchantMapper.selectById(merchant.getId());
        assertEquals(MerchantStatusEnum.APPLYING.getStatus(), dbMerchant.getStatus());
        assertEquals("6666000100000001", dbMerchant.getHuifuId());
    }

    @Test
    public void testSubmitApply_huifuBusinessError() throws Exception {
        // mock 数据
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.DRAFT.getStatus(), "");
        Mockito.when(merchantService.validateMerchantExists(merchant.getId())).thenReturn(merchant);
        mockUploadSuccess();
        Map<String, Object> resp = new HashMap<>();
        resp.put("resp_code", "10020001");
        resp.put("resp_desc", "参数错误");
        Mockito.when(huifuClient.entOpen(Mockito.any())).thenReturn(resp);

        // 准备参数
        MerchantApplySubmitReqVO reqVO = randomSubmitReqVO();

        // 调用，并断言异常
        assertServiceException(() -> applyService.submitApply(merchant.getId(), reqVO),
                HUIFU_RESP_CODE_ERROR, "10020001", "参数错误");
        // 断言：申请单停留「提交失败」
        MerchantApplyDO apply = merchantApplyMapper.selectByReqSeqId(
                merchantApplyMapper.selectListByMerchantId(merchant.getId()).get(0).getReqSeqId());
        assertEquals(MerchantApplyStatusEnum.SUBMIT_FAILED.getStatus(), apply.getStatus());
    }

    @Test
    public void testHandleAuditResult_pass_openBusiness() {
        // mock 数据
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.APPLYING.getStatus(), "6666000100000001");
        MerchantApplyDO apply = insertApply(merchant.getId(), MerchantApplyStatusEnum.DOING);
        mockBusiOpenSuccess();

        // 调用
        applyService.handleAuditResult(apply.getReqSeqId(), "Y", "审核通过", "{}");

        // 断言：申请单通过 + 商户生效
        assertEquals(MerchantApplyStatusEnum.APPROVED.getStatus(),
                merchantApplyMapper.selectById(apply.getId()).getStatus());
        Mockito.verify(merchantService).effectMerchant(merchant.getId(), "6666000100000001");
    }

    @Test
    public void testHandleAuditResult_reject() {
        // mock 数据
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.APPLYING.getStatus(), "6666000100000001");
        MerchantApplyDO apply = insertApply(merchant.getId(), MerchantApplyStatusEnum.DOING);

        // 调用
        applyService.handleAuditResult(apply.getReqSeqId(), "N", "证照不清晰", "{}");

        // 断言：申请单拒绝 + 商户拒绝 + 不开通业务
        MerchantApplyDO dbApply = merchantApplyMapper.selectById(apply.getId());
        assertEquals(MerchantApplyStatusEnum.REJECTED.getStatus(), dbApply.getStatus());
        assertEquals("证照不清晰", dbApply.getAuditDesc());
        assertEquals(MerchantStatusEnum.REJECTED.getStatus(),
                merchantMapper.selectById(merchant.getId()).getStatus());
        Mockito.verify(merchantService, Mockito.never()).effectMerchant(Mockito.any(), Mockito.any());
    }

    @Test
    public void testHandleAuditResult_idempotent() {
        // mock 数据：申请单已通过（模拟回调重复到达）
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.EFFECTIVE.getStatus(), "6666000100000001");
        MerchantApplyDO apply = insertApply(merchant.getId(), MerchantApplyStatusEnum.APPROVED);

        // 调用
        applyService.handleAuditResult(apply.getReqSeqId(), "Y", "审核通过", "{}");

        // 断言：不再触发业务开通
        Mockito.verify(huifuClient, Mockito.never()).busiOpen(Mockito.any());
        Mockito.verify(merchantService, Mockito.never()).effectMerchant(Mockito.any(), Mockito.any());
    }

    @Test
    public void testOpenBusiness_failure() {
        // mock 数据
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.APPLYING.getStatus(), "6666000100000001");
        MerchantApplyDO apply = insertApply(merchant.getId(), MerchantApplyStatusEnum.APPROVED);
        Map<String, Object> resp = new HashMap<>();
        resp.put("resp_code", "10020002");
        resp.put("resp_desc", "开通失败");
        Mockito.when(huifuClient.busiOpen(Mockito.any())).thenReturn(resp);

        // 调用，并断言异常
        assertServiceException(() -> applyService.openBusiness(apply), HUIFU_RESP_CODE_ERROR, "10020002", "开通失败");
        // 断言：商户未生效
        Mockito.verify(merchantService, Mockito.never()).effectMerchant(Mockito.any(), Mockito.any());
    }

    @Test
    public void testSubmitApply_callbackUrlFromConfigPriority() throws Exception {
        // mock 数据：配置管理中已配置回调地址（优先于 yaml）
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.DRAFT.getStatus(), "");
        Mockito.when(merchantService.validateMerchantExists(merchant.getId())).thenReturn(merchant);
        Mockito.when(configApi.getConfigValueByKey("merchant.huifu.kyc-callback-url"))
                .thenReturn("http://config.example.com/kyc");
        mockUploadSuccess();
        mockEntOpenSuccess();

        // 调用
        applyService.submitApply(merchant.getId(), randomSubmitReqVO());

        // 断言：entOpen 报文使用配置管理的回调地址，而非 yaml 兜底值
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(huifuClient).entOpen(captor.capture());
        assertEquals("http://config.example.com/kyc", captor.getValue().get("async_return_url"));
    }

    @Test
    public void testSubmitApply_callbackUrlFallbackToYaml() throws Exception {
        // mock 数据：配置管理未配置（返回 null）→ 回退 yaml 值
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.DRAFT.getStatus(), "");
        Mockito.when(merchantService.validateMerchantExists(merchant.getId())).thenReturn(merchant);
        Mockito.when(configApi.getConfigValueByKey(Mockito.anyString())).thenReturn(null);
        mockUploadSuccess();
        mockEntOpenSuccess();

        // 调用
        applyService.submitApply(merchant.getId(), randomSubmitReqVO());

        // 断言：entOpen 报文使用 yaml 的兜底值
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(huifuClient).entOpen(captor.capture());
        assertEquals("http://localhost/admin-api/merchant/huifu/kyc-callback",
                captor.getValue().get("async_return_url"));
    }

    @Test
    public void testSubmitApply_individual_success() throws Exception {
        // mock 数据：小微商户
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.DRAFT.getStatus(), "");
        Mockito.when(merchantService.validateMerchantExists(merchant.getId())).thenReturn(merchant);
        mockUploadSuccess();
        // 个人进件受理成功码 90000000（审核中）
        Map<String, Object> resp = new HashMap<>();
        resp.put("resp_code", "90000000");
        resp.put("resp_desc", "审核中");
        resp.put("apply_no", "20260928110001");
        resp.put("huifu_id", "6666000100000002");
        Mockito.when(huifuClient.indvOpen(Mockito.any())).thenReturn(resp);

        // 准备参数：小微类型 + F40/F41/F13 图片
        MerchantApplySubmitReqVO reqVO = randomSubmitReqVO();
        reqVO.setMerchantType(2);
        reqVO.setIndvCertFrontId(9L);
        reqVO.setIndvCertBackId(10L);
        reqVO.setSettleCardFrontId(11L);

        // 调用
        Long applyId = applyService.submitApply(merchant.getId(), reqVO);

        // 断言：走 indv 接口、申请单进件中、商户类型已落库
        Mockito.verify(huifuClient).indvOpen(Mockito.any());
        Mockito.verify(huifuClient, Mockito.never()).entOpen(Mockito.any());
        assertEquals(MerchantApplyStatusEnum.DOING.getStatus(),
                merchantApplyMapper.selectById(applyId).getStatus());
        assertEquals(2, merchantMapper.selectById(merchant.getId()).getMerchantType());
    }

    @Test
    public void testSubmitApply_individual_missingImages() {
        // mock 数据
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.DRAFT.getStatus(), "");
        Mockito.when(merchantService.validateMerchantExists(merchant.getId())).thenReturn(merchant);

        // 准备参数：小微但缺 F40/F41/F13
        MerchantApplySubmitReqVO reqVO = randomSubmitReqVO();
        reqVO.setMerchantType(2);
        reqVO.setIndvCertFrontId(null);
        reqVO.setIndvCertBackId(null);
        reqVO.setSettleCardFrontId(null);

        // 调用，并断言异常
        assertServiceException(() -> applyService.submitApply(merchant.getId(), reqVO),
                MERCHANT_APPLY_PARAM_INVALID, "小微商户必须上传负责人身份证正反面与银行卡卡号面");
    }

    @Test
    public void testSubmitApply_enterprise_missingLicenseFields() {
        // mock 数据
        MerchantDO merchant = insertMerchant(MerchantStatusEnum.DRAFT.getStatus(), "");
        Mockito.when(merchantService.validateMerchantExists(merchant.getId())).thenReturn(merchant);

        // 准备参数：企业但清空执照字段
        MerchantApplySubmitReqVO reqVO = randomSubmitReqVO();
        reqVO.setMerchantType(1);
        reqVO.setLicenseCode("");

        // 调用，并断言异常
        assertServiceException(() -> applyService.submitApply(merchant.getId(), reqVO),
                MERCHANT_APPLY_PARAM_INVALID, "企业商户进件缺少必填项：licenseCode");
    }

    // ========== 私有方法 ==========

    private MerchantDO insertMerchant(Integer status, String huifuId) {
        MerchantDO merchant = randomPojo(MerchantDO.class, o -> {
            o.setStatus(status);
            o.setHuifuId(huifuId);
            o.setShortName("芋道科技");
        });
        merchantMapper.insert(merchant);
        return merchant;
    }

    private MerchantApplyDO insertApply(Long merchantId, MerchantApplyStatusEnum status) {
        MerchantApplyDO apply = randomPojo(MerchantApplyDO.class, o -> {
            o.setMerchantId(merchantId);
            o.setReqSeqId("M20260928000001");
            o.setReqDate("20260928");
            o.setStatus(status.getStatus());
            o.setApplyNo("2022011100377000");
            o.setHuifuId("6666000100000001");
            o.setAuditStatus("");
            o.setAuditDesc("");
            o.setTokenNo("");
            o.setSubmitTime(cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.buildTime(2026, 9, 28));
        });
        merchantApplyMapper.insert(apply);
        return apply;
    }

    private MerchantApplySubmitReqVO randomSubmitReqVO() {
        return randomPojo(MerchantApplySubmitReqVO.class, o -> {
            o.setLicenseValidityType("1");
            o.setLegalCertValidityType("1");
            o.setLicensePicId(1L);
            o.setLegalCertFrontId(2L);
            o.setLegalCertBackId(3L);
        });
    }

    private void mockUploadSuccess() throws Exception {
        Mockito.when(fileApi.getFileContent(Mockito.anyLong())).thenReturn(new byte[]{1, 2, 3});
        Mockito.when(huifuClient.uploadPicture(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any())).thenReturn("FILE-ID-1");
    }

    private void mockEntOpenSuccess() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("resp_code", "00000000");
        resp.put("apply_no", "2022011100377000");
        resp.put("huifu_id", "6666000100000001");
        Mockito.when(huifuClient.entOpen(Mockito.any())).thenReturn(resp);
    }

    private void mockBusiOpenSuccess() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("resp_code", "00000000");
        resp.put("apply_no", "202209120038432");
        Mockito.when(huifuClient.busiOpen(Mockito.any())).thenReturn(resp);
    }

}
