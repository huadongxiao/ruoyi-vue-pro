package cn.iocoder.yudao.module.merchant.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.merchant.MerchantDO;

/**
 * 商户 Service 接口
 */
public interface MerchantService {

    /**
     * 初始化商户草稿（幂等）：不存在则创建，并赋予「待生效商家」角色
     *
     * @param userId 用户编号
     * @return 商户编号
     */
    Long initMerchant(Long userId);

    MerchantDO getMerchant(Long id);

    MerchantDO getMerchantByUserId(Long userId);

    PageResult<MerchantDO> getMerchantPage(MerchantPageReqVO pageReqVO);

    /**
     * 平台：停用 / 启用商户，并联动 admin 账号状态
     *
     * @param id     商户编号
     * @param status 目标状态：2 生效 / 4 停用
     */
    void updateMerchantStatus(Long id, Integer status);

    /**
     * 商户生效（汇付审核通过 + 业务开通成功后调用）：状态置 2 + 赋「商家」角色
     *
     * @param id      商户编号
     * @param huifuId 汇付商户号
     */
    void effectMerchant(Long id, String huifuId);

    /**
     * 校验商户存在
     *
     * @param id 商户编号
     * @return 商户
     */
    MerchantDO validateMerchantExists(Long id);

    /**
     * 获得当前登录用户绑定的商户
     *
     * @return 商户
     */
    MerchantDO getLoginMerchant();

}
