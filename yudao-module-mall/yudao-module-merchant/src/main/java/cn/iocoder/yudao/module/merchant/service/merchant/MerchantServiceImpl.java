package cn.iocoder.yudao.module.merchant.service.merchant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.merchant.constant.MerchantConstants;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.merchant.enums.MerchantStatusEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_NOT_EXISTS;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_STATUS_ILLEGAL;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_USER_NOT_BIND;

/**
 * 商户 Service 实现类
 */
@Service
@Validated
public class MerchantServiceImpl implements MerchantService {

    @Resource
    private MerchantMapper merchantMapper;

    @Resource
    private PermissionApi permissionApi;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public Long initMerchant(Long userId) {
        MerchantDO exist = merchantMapper.selectByUserId(userId);
        if (exist != null) {
            return exist.getId();
        }
        // 1. 建草稿
        MerchantDO merchant = MerchantDO.builder()
                .userId(userId).name("").shortName("").logo("").remark("")
                .status(MerchantStatusEnum.DRAFT.getStatus())
                .huifuId("").contactName("").contactMobile("").contactEmail("")
                .settleCardNoMasked("").build();
        merchantMapper.insert(merchant);
        // 2. ext_mer_id 恒等于 merchant.id，回写
        MerchantDO extUpdate = new MerchantDO();
        extUpdate.setId(merchant.getId());
        extUpdate.setExtMerId(String.valueOf(merchant.getId()));
        merchantMapper.updateById(extUpdate);
        // 3. 赋「待生效商家」角色
        permissionApi.assignUserRole(userId, Collections.singleton(MerchantConstants.ROLE_ID_MERCHANT_PENDING));
        return merchant.getId();
    }

    @Override
    public MerchantDO getMerchant(Long id) {
        return merchantMapper.selectById(id);
    }

    @Override
    public MerchantDO getMerchantByUserId(Long userId) {
        return merchantMapper.selectByUserId(userId);
    }

    @Override
    public PageResult<MerchantDO> getMerchantPage(MerchantPageReqVO pageReqVO) {
        return merchantMapper.selectPage(pageReqVO);
    }

    @Override
    public void updateMerchantStatus(Long id, Integer status) {
        MerchantDO merchant = validateMerchantExists(id);
        // 仅允许「停用 / 生效」两个目标状态
        if (!Objects.equals(status, MerchantStatusEnum.DISABLED.getStatus())
                && !Objects.equals(status, MerchantStatusEnum.EFFECTIVE.getStatus())) {
            throw exception(MERCHANT_STATUS_ILLEGAL, status);
        }
        if (Objects.equals(merchant.getStatus(), status)) {
            return; // 幂等
        }
        // 1. 更新商户状态
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        merchantMapper.updateById(updateObj);
        // 2. 联动账号状态：停用=禁用账号，启用=开启账号
        adminUserApi.updateUserStatus(merchant.getUserId(),
                Objects.equals(status, MerchantStatusEnum.DISABLED.getStatus())
                        ? CommonStatusEnum.DISABLE.getStatus() : CommonStatusEnum.ENABLE.getStatus());
    }

    @Override
    public void effectMerchant(Long id, String huifuId) {
        MerchantDO merchant = validateMerchantExists(id);
        // 1. 商户生效
        MerchantDO updateObj = new MerchantDO();
        updateObj.setId(id);
        updateObj.setStatus(MerchantStatusEnum.EFFECTIVE.getStatus());
        updateObj.setHuifuId(huifuId);
        updateObj.setEffectTime(LocalDateTime.now());
        merchantMapper.updateById(updateObj);
        // 2. 角色：待生效商家 → 商家
        permissionApi.assignUserRole(merchant.getUserId(), Collections.singleton(MerchantConstants.ROLE_ID_MERCHANT));
    }

    @Override
    public MerchantDO validateMerchantExists(Long id) {
        MerchantDO merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        return merchant;
    }

    @Override
    public MerchantDO getLoginMerchant() {
        MerchantDO merchant = merchantMapper.selectByUserId(SecurityFrameworkUtils.getLoginUserId());
        if (merchant == null) {
            throw exception(MERCHANT_USER_NOT_BIND);
        }
        return merchant;
    }

}
