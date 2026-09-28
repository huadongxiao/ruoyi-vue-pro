package cn.iocoder.yudao.module.merchant.dal.mysql.image;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.merchant.dal.dataobject.image.MerchantImageDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MerchantImageMapper extends BaseMapperX<MerchantImageDO> {

    default List<MerchantImageDO> selectListByMerchantId(Long merchantId) {
        return selectList(MerchantImageDO::getMerchantId, merchantId);
    }

}
