package cn.iocoder.yudao.module.merchant.service.apply;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantApplySubmitReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.apply.MerchantApplyDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.image.MerchantImageDO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.apply.MerchantApplyMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.image.MerchantImageMapper;
import cn.iocoder.yudao.module.merchant.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.merchant.enums.MerchantApplyStatusEnum;
import cn.iocoder.yudao.module.merchant.enums.MerchantImageBizTypeEnum;
import cn.iocoder.yudao.module.merchant.enums.MerchantStatusEnum;
import cn.iocoder.yudao.module.merchant.enums.MerchantTypeEnum;
import cn.iocoder.yudao.module.merchant.framework.huifu.HuifuClient;
import cn.iocoder.yudao.module.merchant.framework.huifu.HuifuProperties;
import cn.iocoder.yudao.module.merchant.service.merchant.MerchantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_REQUEST_ERROR;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_RESP_CODE_ERROR;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_APPLY_EXISTS_DOING;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_APPLY_PARAM_INVALID;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_DISABLE;

/**
 * 商户进件申请 Service 实现类
 *
 * 状态机（见 spec §6）：
 * 提交（先落 SUBMIT_FAILED 留痕 → 图片推汇付 → KYC 进件 → DOING）
 * → 回调 / 轮询收敛（幂等）→ Y：APPROVED + 业务开通 → 商户生效；N/F：REJECTED/FAILED + 商户拒绝
 */
@Slf4j
@Service
@Validated
public class MerchantApplyServiceImpl implements MerchantApplyService {

    private static final DateTimeFormatter REQ_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter REQ_SEQ_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 配置管理 key：进件审核结果回调地址（后台可改，实时生效） */
    private static final String CONFIG_KEY_KYC_CALLBACK_URL = "merchant.huifu.kyc-callback-url";
    /** 配置管理 key：业务开通结果回调地址（后台可改，实时生效） */
    private static final String CONFIG_KEY_BUSI_CALLBACK_URL = "merchant.huifu.busi-callback-url";

    @Resource
    private MerchantApplyMapper merchantApplyMapper;

    @Resource
    private MerchantImageMapper merchantImageMapper;

    @Resource
    private MerchantMapper merchantMapper;

    @Resource
    private MerchantService merchantService;

    @Resource
    private HuifuClient huifuClient;

    @Resource
    private HuifuProperties huifuProperties;

    @Resource
    private FileApi fileApi;

    @Resource
    private ConfigApi configApi;

    @Override
    public Long submitApply(Long merchantId, MerchantApplySubmitReqVO reqVO) {
        // 1. 商户校验
        MerchantDO merchant = merchantService.validateMerchantExists(merchantId);
        if (Objects.equals(merchant.getStatus(), MerchantStatusEnum.DISABLED.getStatus())) {
            throw exception(MERCHANT_DISABLE);
        }
        if (merchantApplyMapper.selectDoingByMerchantId(merchantId) != null) {
            throw exception(MERCHANT_APPLY_EXISTS_DOING);
        }
        // 1.1 按主体类型校验必填字段与图片
        boolean individual = Objects.equals(reqVO.getMerchantType(), MerchantTypeEnum.INDIVIDUAL.getType());
        validateApplyParams(reqVO, individual);
        // 2. 基础信息落库（主体类型 / 名称 / 联系人 / 结算卡脱敏）
        MerchantDO merchantUpdate = new MerchantDO();
        merchantUpdate.setId(merchantId);
        merchantUpdate.setMerchantType(reqVO.getMerchantType());
        merchantUpdate.setName(reqVO.getRegName());
        merchantUpdate.setShortName(reqVO.getShortName());
        merchantUpdate.setContactName(reqVO.getContactName());
        merchantUpdate.setContactMobile(reqVO.getContactMobileNo());
        merchantUpdate.setContactEmail(reqVO.getContactEmail());
        merchantUpdate.setSettleCardNoMasked(maskCardNo(reqVO.getCardNo()));
        merchantMapper.updateById(merchantUpdate);
        // 3. 先落「提交失败」申请单（spec §10：超时 / 异常留痕、可重试）
        String reqSeqId = buildReqSeqId();
        String reqDate = LocalDateTime.now().format(REQ_DATE_FORMATTER);
        MerchantApplyDO apply = MerchantApplyDO.builder()
                .merchantId(merchantId).reqSeqId(reqSeqId).reqDate(reqDate).applyNo("")
                .status(MerchantApplyStatusEnum.SUBMIT_FAILED.getStatus())
                .auditStatus("").auditDesc("").huifuId("").tokenNo("")
                .submitTime(LocalDateTime.now()).build();
        merchantApplyMapper.insert(apply);
        // 4. 图片逐张推汇付（失败直接抛出，申请单停留「提交失败」）
        Map<String, String> fileIds = uploadImages(merchant, reqVO, reqDate);
        // 5. 按主体类型进件：企业 → ent，小微（个人）→ indv
        Map<String, Object> data = individual
                ? buildIndvData(merchant, reqVO, fileIds, reqSeqId, reqDate)
                : buildKycData(merchant, reqVO, fileIds, reqSeqId, reqDate);
        Map<String, Object> resp = individual ? huifuClient.indvOpen(data) : huifuClient.entOpen(data);
        apply.setRawNotifyJson(JsonUtils.toJsonString(resp));
        // 受理成功码：企业 00000000；个人 90000000（审核中）
        String acceptCode = individual ? "90000000" : "00000000";
        if (!acceptCode.equals(resp.get("resp_code"))) {
            apply.setAuditDesc(String.valueOf(resp.get("resp_desc")));
            merchantApplyMapper.updateById(apply);
            throw exception(HUIFU_RESP_CODE_ERROR, resp.get("resp_code"), resp.get("resp_desc"));
        }
        // 6. 进件中
        apply.setStatus(MerchantApplyStatusEnum.DOING.getStatus());
        apply.setApplyNo((String) resp.get("apply_no"));
        apply.setHuifuId((String) resp.get("huifu_id"));
        merchantApplyMapper.updateById(apply);
        // 7. 商户状态 → 进件中，并回写 huifu_id
        MerchantDO statusUpdate = new MerchantDO();
        statusUpdate.setId(merchantId);
        statusUpdate.setStatus(MerchantStatusEnum.APPLYING.getStatus());
        statusUpdate.setHuifuId((String) resp.get("huifu_id"));
        statusUpdate.setSubmitTime(LocalDateTime.now());
        merchantMapper.updateById(statusUpdate);
        return apply.getId();
    }

    @Override
    public void handleAuditResult(String reqSeqId, String auditStatus, String auditDesc, String rawJson) {
        MerchantApplyDO apply = merchantApplyMapper.selectByReqSeqId(reqSeqId);
        if (apply == null) {
            log.warn("[handleAuditResult][reqSeqId({}) 无对应申请单，忽略]", reqSeqId);
            return;
        }
        // 幂等：非「进件中」直接返回（回调重复 / 轮询与回调并发）
        if (!Objects.equals(apply.getStatus(), MerchantApplyStatusEnum.DOING.getStatus())) {
            return;
        }
        if ("P".equals(auditStatus)) {
            return; // 仍审核中
        }
        apply.setAuditStatus(auditStatus);
        apply.setAuditDesc(auditDesc);
        apply.setRawNotifyJson(rawJson);
        apply.setAuditTime(LocalDateTime.now());
        if ("Y".equals(auditStatus)) {
            apply.setStatus(MerchantApplyStatusEnum.APPROVED.getStatus());
            merchantApplyMapper.updateById(apply);
            // 审核通过 → 业务开通（失败由定时任务重试）
            openBusiness(apply);
            return;
        }
        // N 拒绝 / F 失败 → 商户可修改资料后重新提交
        apply.setStatus("N".equals(auditStatus)
                ? MerchantApplyStatusEnum.REJECTED.getStatus() : MerchantApplyStatusEnum.FAILED.getStatus());
        merchantApplyMapper.updateById(apply);
        MerchantDO merchantUpdate = new MerchantDO();
        merchantUpdate.setId(apply.getMerchantId());
        merchantUpdate.setStatus(MerchantStatusEnum.REJECTED.getStatus());
        merchantMapper.updateById(merchantUpdate);
    }

    @Override
    public List<MerchantApplyDO> getApplyListByMerchantId(Long merchantId) {
        return merchantApplyMapper.selectListByMerchantId(merchantId);
    }

    @Override
    public void openBusiness(MerchantApplyDO apply) {
        MerchantDO merchant = merchantMapper.selectById(apply.getMerchantId());
        if (merchant == null || Objects.equals(merchant.getStatus(), MerchantStatusEnum.EFFECTIVE.getStatus())) {
            return; // 已生效，幂等
        }
        String huifuId = StrUtil.isNotBlank(merchant.getHuifuId()) ? merchant.getHuifuId() : apply.getHuifuId();
        if (StrUtil.isBlank(huifuId)) {
            log.warn("[openBusiness][申请单({}) 无 huifu_id，等待下次重试]", apply.getId());
            throw exception(HUIFU_REQUEST_ERROR, "缺少汇付商户号 huifu_id");
        }
        // 1. 组装业务开通参数
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("req_seq_id", buildReqSeqId());
        data.put("req_date", LocalDateTime.now().format(REQ_DATE_FORMATTER));
        data.put("huifu_id", huifuId);
        data.put("short_name", merchant.getShortName());
        if (StrUtil.isNotBlank(huifuProperties.getWxConfList())) {
            data.put("wx_conf_list", huifuProperties.getWxConfList());
        }
        if (StrUtil.isNotBlank(huifuProperties.getAliConfList())) {
            data.put("ali_conf_list", huifuProperties.getAliConfList());
        }
        if (StrUtil.isNotBlank(huifuProperties.getOnlineBusiType())) {
            data.put("online_busi_type", huifuProperties.getOnlineBusiType());
        }
        if (StrUtil.isNotBlank(huifuProperties.getOnlineMediaInfoList())) {
            data.put("online_media_info_list", huifuProperties.getOnlineMediaInfoList());
        }
        data.put("async_return_url", getBusiCallbackUrl());
        data.put("busi_async_return_url", getBusiCallbackUrl());
        data.put("online_refund", "Y");
        // 2. 调用汇付
        Map<String, Object> resp = huifuClient.busiOpen(data);
        if (!"00000000".equals(resp.get("resp_code"))) {
            throw exception(HUIFU_RESP_CODE_ERROR, resp.get("resp_code"), resp.get("resp_desc"));
        }
        // 3. 开通申请已受理 → 商户生效 + 赋「商家」角色
        merchantService.effectMerchant(merchant.getId(), huifuId);
    }

    @Override
    public void pollApplyStatus() {
        // 1. 进件中、提交超 15 分钟 → 调申请单状态查询兜底
        List<MerchantApplyDO> doingList = merchantApplyMapper.selectDoingListBySubmitTimeLt(
                LocalDateTime.now().minusMinutes(15));
        for (MerchantApplyDO apply : doingList) {
            try {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("req_seq_id", apply.getReqSeqId());
                data.put("req_date", apply.getReqDate());
                data.put("apply_no", apply.getApplyNo());
                data.put("huifu_id", apply.getHuifuId());
                Map<String, Object> resp = huifuClient.queryApplyStatus(data);
                String applyStatus = (String) resp.get("apply_status");
                if (StrUtil.isBlank(applyStatus)) {
                    continue;
                }
                handleAuditResult(apply.getReqSeqId(), applyStatus,
                        (String) resp.get("apply_reason"), JsonUtils.toJsonString(resp));
            } catch (Exception e) {
                log.error("[pollApplyStatus][申请单({}) 状态查询失败]", apply.getId(), e);
            }
        }
        // 2. 审核通过但未生效 → 业务开通重试
        for (MerchantApplyDO apply : merchantApplyMapper.selectApprovedList()) {
            MerchantDO merchant = merchantMapper.selectById(apply.getMerchantId());
            if (merchant == null || Objects.equals(merchant.getStatus(), MerchantStatusEnum.EFFECTIVE.getStatus())) {
                continue;
            }
            try {
                openBusiness(apply);
            } catch (Exception e) {
                log.error("[pollApplyStatus][申请单({}) 业务开通重试失败]", apply.getId(), e);
            }
        }
    }

    // ========== 私有方法 ==========

    /**
     * 图片逐张推汇付，返回「汇付 file_type → 汇付 file_id」
     */
    private Map<String, String> uploadImages(MerchantDO merchant, MerchantApplySubmitReqVO reqVO, String reqDate) {
        Map<MerchantImageBizTypeEnum, Long> images = new LinkedHashMap<>();
        images.put(MerchantImageBizTypeEnum.LEGAL_CERT_FRONT, reqVO.getLegalCertFrontId());
        images.put(MerchantImageBizTypeEnum.LEGAL_CERT_BACK, reqVO.getLegalCertBackId());
        images.put(MerchantImageBizTypeEnum.LICENSE, reqVO.getLicensePicId());
        images.put(MerchantImageBizTypeEnum.INDV_CERT_FRONT, reqVO.getIndvCertFrontId());
        images.put(MerchantImageBizTypeEnum.INDV_CERT_BACK, reqVO.getIndvCertBackId());
        images.put(MerchantImageBizTypeEnum.REG_ACCT, reqVO.getRegAcctId());
        images.put(MerchantImageBizTypeEnum.SETTLE_CARD_FRONT, reqVO.getSettleCardFrontId());
        images.put(MerchantImageBizTypeEnum.SETTLE_CERT_FRONT, reqVO.getSettleCertFrontId());
        images.put(MerchantImageBizTypeEnum.SETTLE_CERT_BACK, reqVO.getSettleCertBackId());
        images.put(MerchantImageBizTypeEnum.AUTH_ENTRUST, reqVO.getAuthEntrustId());
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<MerchantImageBizTypeEnum, Long> entry : images.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String bizType = entry.getKey().getCode();
            byte[] content;
            try {
                content = fileApi.getFileContent(entry.getValue());
            } catch (Exception e) {
                throw exception(HUIFU_REQUEST_ERROR, "读取图片(" + entry.getValue() + ")失败：" + e.getMessage());
            }
            if (content == null) {
                throw exception(MERCHANT_APPLY_PARAM_INVALID, "图片不存在：" + entry.getValue());
            }
            String huifuFileId = huifuClient.uploadPicture(content, bizType + ".jpg", bizType,
                    merchant.getHuifuId(), buildReqSeqId(), reqDate);
            merchantImageMapper.insert(MerchantImageDO.builder()
                    .merchantId(merchant.getId()).bizType(bizType)
                    .infraFileId(entry.getValue()).huifuFileId(huifuFileId).build());
            result.put(bizType, huifuFileId);
        }
        return result;
    }

    /**
     * 按主体类型校验进件必填项（VO 注解只覆盖两类通用的必填，差异项在此校验）
     */
    private void validateApplyParams(MerchantApplySubmitReqVO vo, boolean individual) {
        if (individual) {
            // 小微：负责人身份证 F40/F41 + 银行卡卡号面 F13 必填
            if (vo.getIndvCertFrontId() == null || vo.getIndvCertBackId() == null || vo.getSettleCardFrontId() == null) {
                throw exception(MERCHANT_APPLY_PARAM_INVALID, "小微商户必须上传负责人身份证正反面与银行卡卡号面");
            }
            return;
        }
        // 企业：执照与法人四要素 + 执照图片 + 小票名称 + 登录账号
        String[] requiredTexts = {"receiptName", "entType", "busiType", "licenseCode", "licenseValidityType",
                "licenseBeginDate", "foundDate", "regDistrictId", "regDetail", "legalName", "legalCertType", "loginName"};
        for (String field : requiredTexts) {
            if (StrUtil.isBlank(getField(vo, field))) {
                throw exception(MERCHANT_APPLY_PARAM_INVALID, "企业商户进件缺少必填项：" + field);
            }
        }
        if ("0".equals(vo.getLicenseValidityType()) && StrUtil.isBlank(vo.getLicenseEndDate())) {
            throw exception(MERCHANT_APPLY_PARAM_INVALID, "证照非长期有效时必须填写证照有效期截止");
        }
        if (vo.getLicensePicId() == null || vo.getLegalCertFrontId() == null || vo.getLegalCertBackId() == null) {
            throw exception(MERCHANT_APPLY_PARAM_INVALID, "企业商户必须上传营业执照与法人身份证正反面");
        }
    }

    private static String getField(MerchantApplySubmitReqVO vo, String field) {
        switch (field) {
            case "receiptName": return vo.getReceiptName();
            case "entType": return vo.getEntType();
            case "busiType": return vo.getBusiType();
            case "licenseCode": return vo.getLicenseCode();
            case "licenseValidityType": return vo.getLicenseValidityType();
            case "licenseBeginDate": return vo.getLicenseBeginDate();
            case "foundDate": return vo.getFoundDate();
            case "regDistrictId": return vo.getRegDistrictId();
            case "regDetail": return vo.getRegDetail();
            case "legalName": return vo.getLegalName();
            case "legalCertType": return vo.getLegalCertType();
            case "loginName": return vo.getLoginName();
            default: return null;
        }
    }

    /**
     * 组装企业商户进件（KYC）请求参数，字段见 docs/huifu/企业商户进件-KYC.md
     */
    private Map<String, Object> buildKycData(MerchantDO merchant, MerchantApplySubmitReqVO vo,
                                             Map<String, String> fileIds, String reqSeqId, String reqDate) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("req_seq_id", reqSeqId);
        data.put("req_date", reqDate);
        data.put("reg_name", vo.getRegName());
        data.put("short_name", vo.getShortName());
        data.put("receipt_name", vo.getReceiptName());
        data.put("ent_type", vo.getEntType());
        data.put("busi_type", vo.getBusiType());
        data.put("mcc", vo.getMcc());
        data.put("scene_type", vo.getSceneType());
        data.put("license_pic", fileIds.get(MerchantImageBizTypeEnum.LICENSE.getCode()));
        data.put("license_code", vo.getLicenseCode());
        data.put("license_validity_type", vo.getLicenseValidityType());
        data.put("license_begin_date", vo.getLicenseBeginDate());
        if ("0".equals(vo.getLicenseValidityType())) {
            data.put("license_end_date", vo.getLicenseEndDate());
        }
        data.put("found_date", vo.getFoundDate());
        data.put("reg_district_id", vo.getRegDistrictId());
        data.put("reg_detail", vo.getRegDetail());
        data.put("district_id", vo.getDistrictId());
        if (StrUtil.isNotBlank(vo.getDetailAddr())) {
            data.put("detail_addr", vo.getDetailAddr());
        }
        data.put("legal_name", vo.getLegalName());
        data.put("legal_cert_type", vo.getLegalCertType());
        data.put("legal_cert_no", vo.getLegalCertNo());
        data.put("legal_cert_validity_type", vo.getLegalCertValidityType());
        data.put("legal_cert_begin_date", vo.getLegalCertBeginDate());
        if ("0".equals(vo.getLegalCertValidityType())) {
            data.put("legal_cert_end_date", vo.getLegalCertEndDate());
        }
        data.put("legal_addr", vo.getLegalAddr());
        data.put("legal_cert_front_pic", fileIds.get(MerchantImageBizTypeEnum.LEGAL_CERT_FRONT.getCode()));
        data.put("legal_cert_back_pic", fileIds.get(MerchantImageBizTypeEnum.LEGAL_CERT_BACK.getCode()));
        if (StrUtil.isNotBlank(vo.getContactName())) {
            data.put("contact_name", vo.getContactName());
        }
        data.put("contact_mobile_no", vo.getContactMobileNo());
        data.put("contact_email", vo.getContactEmail());
        data.put("login_name", vo.getLoginName());
        // 结算卡信息（jsonObject 字符串）
        Map<String, Object> cardInfo = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(vo.getCardBranchCode())) {
            cardInfo.put("branch_code", vo.getCardBranchCode());
        }
        cardInfo.put("card_no", vo.getCardNo());
        cardInfo.put("card_name", vo.getCardName());
        cardInfo.put("card_type", vo.getCardType());
        data.put("card_info", JsonUtils.toJsonString(cardInfo));
        // 条件图
        putIfPresent(data, "reg_acct_pic", fileIds.get(MerchantImageBizTypeEnum.REG_ACCT.getCode()));
        putIfPresent(data, "settle_card_front_pic", fileIds.get(MerchantImageBizTypeEnum.SETTLE_CARD_FRONT.getCode()));
        putIfPresent(data, "settle_cert_front_pic", fileIds.get(MerchantImageBizTypeEnum.SETTLE_CERT_FRONT.getCode()));
        putIfPresent(data, "settle_cert_back_pic", fileIds.get(MerchantImageBizTypeEnum.SETTLE_CERT_BACK.getCode()));
        putIfPresent(data, "auth_entrust_pic", fileIds.get(MerchantImageBizTypeEnum.AUTH_ENTRUST.getCode()));
        // 平台侧字段
        data.put("ext_mer_id", String.valueOf(merchant.getId()));
        if (StrUtil.isNotBlank(getKycCallbackUrl())) {
            data.put("async_return_url", getKycCallbackUrl());
        }
        return data;
    }

    /**
     * 组装个人（小微）商户进件请求参数（/v2/merchant/basicdata/indv）
     *
     * 与企业进件的差异：无执照/公司类型/注册地/成立日期；reg_name=负责人姓名；
     * 身份证图片类型为 F40/F41；银行卡卡号面 F13 必填
     */
    private Map<String, Object> buildIndvData(MerchantDO merchant, MerchantApplySubmitReqVO vo,
                                              Map<String, String> fileIds, String reqSeqId, String reqDate) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("req_seq_id", reqSeqId);
        data.put("req_date", reqDate);
        // reg_name = 负责人姓名；short_name 选填（≥4 字符）
        data.put("reg_name", vo.getRegName());
        putIfPresent(data, "short_name", vo.getShortName());
        data.put("mcc", vo.getMcc());
        data.put("scene_type", vo.getSceneType());
        data.put("district_id", vo.getDistrictId());
        putIfPresent(data, "detail_addr", vo.getDetailAddr());
        // 负责人证件（仅支持身份证）
        data.put("legal_cert_no", vo.getLegalCertNo());
        data.put("legal_cert_validity_type", vo.getLegalCertValidityType());
        data.put("legal_cert_begin_date", vo.getLegalCertBeginDate());
        if ("0".equals(vo.getLegalCertValidityType())) {
            data.put("legal_cert_end_date", vo.getLegalCertEndDate());
        }
        data.put("legal_addr", vo.getLegalAddr());
        data.put("legal_cert_front_pic", fileIds.get(MerchantImageBizTypeEnum.INDV_CERT_FRONT.getCode()));
        data.put("legal_cert_back_pic", fileIds.get(MerchantImageBizTypeEnum.INDV_CERT_BACK.getCode()));
        // 联系人
        data.put("contact_mobile_no", vo.getContactMobileNo());
        data.put("contact_email", vo.getContactEmail());
        putIfPresent(data, "login_name", vo.getLoginName());
        // 结算卡信息（个人商户只能对私）
        Map<String, Object> cardInfo = new LinkedHashMap<>();
        putIfPresent(cardInfo, "branch_code", vo.getCardBranchCode());
        cardInfo.put("card_no", vo.getCardNo());
        cardInfo.put("card_name", vo.getCardName());
        cardInfo.put("card_type", vo.getCardType());
        cardInfo.put("cert_no", vo.getLegalCertNo()); // 持卡人证件号
        cardInfo.put("cert_type", "00"); // 身份证
        data.put("card_info", JsonUtils.toJsonString(cardInfo));
        // 银行卡卡号面 F13 必填
        data.put("settle_card_front_pic", fileIds.get(MerchantImageBizTypeEnum.SETTLE_CARD_FRONT.getCode()));
        // 平台侧字段
        data.put("ext_mer_id", String.valueOf(merchant.getId()));
        if (StrUtil.isNotBlank(getKycCallbackUrl())) {
            data.put("async_return_url", getKycCallbackUrl());
        }
        return data;
    }

    /**
     * 进件回调地址：配置管理（infra_config）优先，yaml 兜底
     */
    private String getKycCallbackUrl() {
        return resolveConfig(CONFIG_KEY_KYC_CALLBACK_URL, huifuProperties.getKycCallbackUrl());
    }

    /**
     * 业务开通回调地址：配置管理（infra_config）优先，yaml 兜底
     */
    private String getBusiCallbackUrl() {
        return resolveConfig(CONFIG_KEY_BUSI_CALLBACK_URL, huifuProperties.getBusiCallbackUrl());
    }

    private String resolveConfig(String key, String defaultValue) {
        String value = configApi.getConfigValueByKey(key);
        return StrUtil.isNotBlank(value) ? value : defaultValue;
    }

    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            data.put(key, value);
        }
    }

    /**
     * 生成汇付请求流水号（当日唯一，≤ 32 字符）
     */
    private static String buildReqSeqId() {
        return "M" + LocalDateTime.now().format(REQ_SEQ_FORMATTER)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /**
     * 结算卡号脱敏：保留前 6 后 4
     */
    private static String maskCardNo(String cardNo) {
        if (StrUtil.isBlank(cardNo) || cardNo.length() < 10) {
            return StrUtil.isBlank(cardNo) ? "" : "****";
        }
        return cardNo.substring(0, 6) + "****" + cardNo.substring(cardNo.length() - 4);
    }

}
