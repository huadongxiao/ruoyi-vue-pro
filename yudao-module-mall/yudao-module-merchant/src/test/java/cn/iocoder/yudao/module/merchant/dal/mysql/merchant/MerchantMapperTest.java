package cn.iocoder.yudao.module.merchant.dal.mysql.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.merchant.controller.admin.merchant.vo.MerchantPageReqVO;
import cn.iocoder.yudao.module.merchant.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.merchant.enums.MerchantStatusEnum;
import org.junit.jupiter.api.Test;

import javax.annotation.Resource;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.buildTime;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.cloneIgnoreId;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MerchantMapper} 的单元测试类
 */
public class MerchantMapperTest extends BaseDbUnitTest {

    @Resource
    private MerchantMapper merchantMapper;

    @Test
    public void testSelectByUserId() {
        // 准备参数
        MerchantDO dbMerchant = randomPojo(MerchantDO.class, o -> o.setStatus(MerchantStatusEnum.DRAFT.getStatus()));
        merchantMapper.insert(dbMerchant);

        // 调用，并断言
        assertPojoEquals(dbMerchant, merchantMapper.selectByUserId(dbMerchant.getUserId()));
    }

    @Test
    public void testSelectByHuifuId() {
        // 准备参数
        MerchantDO dbMerchant = randomPojo(MerchantDO.class, o -> o.setHuifuId("6666000100000001"));
        merchantMapper.insert(dbMerchant);

        // 调用，并断言
        assertPojoEquals(dbMerchant, merchantMapper.selectByHuifuId("6666000100000001"));
    }

    @Test
    public void testSelectPage() {
        // mock 数据
        MerchantDO dbMerchant = randomPojo(MerchantDO.class, o -> {
            o.setName("芋道商户");
            o.setStatus(MerchantStatusEnum.EFFECTIVE.getStatus());
            o.setCreateTime(buildTime(2026, 9, 1));
        });
        merchantMapper.insert(dbMerchant); // 等会查询到
        // 测试 name 不匹配
        merchantMapper.insert(cloneIgnoreId(dbMerchant, o -> o.setName("源码")));
        // 测试 status 不匹配
        merchantMapper.insert(cloneIgnoreId(dbMerchant, o -> o.setStatus(MerchantStatusEnum.DRAFT.getStatus())));
        // 测试 createTime 不匹配
        merchantMapper.insert(cloneIgnoreId(dbMerchant, o -> o.setCreateTime(buildTime(2026, 10, 1))));

        // 准备参数
        MerchantPageReqVO reqVO = new MerchantPageReqVO();
        reqVO.setName("芋道");
        reqVO.setStatus(MerchantStatusEnum.EFFECTIVE.getStatus());
        reqVO.setCreateTime(new LocalDateTime[]{buildTime(2026, 8, 1), buildTime(2026, 9, 28)});

        // 调用
        PageResult<MerchantDO> pageResult = merchantMapper.selectPage(reqVO);
        // 断言
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(dbMerchant, pageResult.getList().get(0));
    }

}
