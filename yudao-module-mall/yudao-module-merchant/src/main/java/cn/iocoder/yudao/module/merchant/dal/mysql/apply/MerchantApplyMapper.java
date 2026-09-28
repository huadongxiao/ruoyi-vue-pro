package cn.iocoder.yudao.module.merchant.dal.mysql.apply;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.apply.MerchantApplyDO;
import cn.iocoder.yudao.module.merchant.enums.MerchantApplyStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MerchantApplyMapper extends BaseMapperX<MerchantApplyDO> {

    default MerchantApplyDO selectByReqSeqId(String reqSeqId) {
        return selectOne(MerchantApplyDO::getReqSeqId, reqSeqId);
    }

    /**
     * 查询商户「进件中」的申请单（用于禁止重复提交）
     */
    default MerchantApplyDO selectDoingByMerchantId(Long merchantId) {
        return selectOne(new LambdaQueryWrapperX<MerchantApplyDO>()
                .eq(MerchantApplyDO::getMerchantId, merchantId)
                .eq(MerchantApplyDO::getStatus, MerchantApplyStatusEnum.DOING.getStatus()));
    }

    default List<MerchantApplyDO> selectListByMerchantId(Long merchantId) {
        return selectList(new LambdaQueryWrapperX<MerchantApplyDO>()
                .eq(MerchantApplyDO::getMerchantId, merchantId)
                .orderByDesc(MerchantApplyDO::getId));
    }

    /**
     * 轮询兜底：进件中、且提交时间早于 beforeTime 的申请单
     */
    default List<MerchantApplyDO> selectDoingListBySubmitTimeLt(LocalDateTime beforeTime) {
        return selectList(new LambdaQueryWrapperX<MerchantApplyDO>()
                .eq(MerchantApplyDO::getStatus, MerchantApplyStatusEnum.DOING.getStatus())
                .lt(MerchantApplyDO::getSubmitTime, beforeTime));
    }

    /**
     * 开通重试：审核通过、但商户可能尚未生效的申请单
     */
    default List<MerchantApplyDO> selectApprovedList() {
        return selectList(new LambdaQueryWrapperX<MerchantApplyDO>()
                .eq(MerchantApplyDO::getStatus, MerchantApplyStatusEnum.APPROVED.getStatus()));
    }

}
