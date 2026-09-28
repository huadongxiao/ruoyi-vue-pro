package cn.iocoder.yudao.module.merchant.dal.mysql.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.merchant.MerchantDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantMapper extends BaseMapperX<MerchantDO> {

    default MerchantDO selectByUserId(Long userId) {
        return selectOne(MerchantDO::getUserId, userId);
    }

    default MerchantDO selectByHuifuId(String huifuId) {
        return selectOne(MerchantDO::getHuifuId, huifuId);
    }

    default PageResult<MerchantDO> selectPage(MerchantPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MerchantDO>()
                .likeIfPresent(MerchantDO::getName, reqVO.getName())
                .eqIfPresent(MerchantDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MerchantDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(MerchantDO::getId));
    }

}
