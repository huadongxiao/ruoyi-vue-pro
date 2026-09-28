package cn.iocoder.yudao.module.trade.service.brokerage;

import cn.hutool.core.collection.ListUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageUserDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.dal.mysql.brokerage.BrokerageUserMapper;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * {@link BrokerageUserServiceImpl} 的下级层级查询单测（支持 N 级）
 *
 * 说明 1：原有的 {@link BrokerageUserServiceImplTest} 类被 {@code @Disabled} 标注（芋艿 TODO），
 * 因此新建本测试类，保证用例真实执行。
 *
 * 说明 2：此处使用真实的 {@link BrokerageUserMapper}（H2）插入绑定关系后查询，
 * 因为 {@code BaseDbUnitTest} 中的 Mapper 是真实 MyBatis 代理，不是 Mockito mock，无法用 when() 打桩。
 *
 * @author owen
 */
@Import(BrokerageUserServiceImpl.class)
public class BrokerageUserLevelQueryTest extends BaseDbUnitTest {

    @Resource
    private BrokerageUserServiceImpl brokerageUserService;

    @Resource
    private BrokerageUserMapper brokerageUserMapper;

    @MockBean
    private TradeConfigService tradeConfigService;
    @MockBean
    private MemberUserApi memberUserApi;

    @Test
    public void testGetBrokerageUserCountByLevel_threeLevels() {
        // mock 配置：3 级
        when(tradeConfigService.getTradeConfig()).thenReturn(buildConfig(3));
        // 准备链路：2 绑 1；3 绑 2；4 绑 3
        insertUser(2L, 1L);
        insertUser(3L, 2L);
        insertUser(4L, 3L);

        // 断言：逐层只返回目标层级
        assertEquals(1L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, 1));
        assertEquals(1L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, 2));
        assertEquals(1L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, 3));
        // 断言：level 为空时返回所有层级（1+2+3 级）
        assertEquals(3L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, null));
    }

    @Test
    public void testGetBrokerageUserCountByLevel_outOfRange() {
        // 只有 1 级，查第 2 级应抛错
        when(tradeConfigService.getTradeConfig()).thenReturn(buildConfig(1));
        assertThrows(ServiceException.class,
                () -> brokerageUserService.getBrokerageUserCountByBindUserId(1L, 2));
    }

    @Test
    public void testGetBrokerageUserCountByLevel_noConfig() {
        // 未配置层级规则时，返回空
        when(tradeConfigService.getTradeConfig()).thenReturn(TradeConfigDO.builder().build());
        assertEquals(0L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, null));
    }

    @Test
    public void testGetBrokerageUserCountByLevel_noConfigQuerySecondLevel() {
        // 回归保护：未配置层级数时，即使查询第 2 级也不能抛错
        // 否则 AppBrokerageUserController#getBrokerageUserSummary 会 500，会员端分销中心直接不可用
        when(tradeConfigService.getTradeConfig()).thenReturn(TradeConfigDO.builder().build());
        assertEquals(0L, brokerageUserService.getBrokerageUserCountByBindUserId(1L, 2));
    }

    @Test
    public void testGetBrokerageUserCountByLevel_oneLevelQuerySecondLevelThrows() {
        // 只配 1 级时，查第 2 级应抛错（由调用方按 levelCount 决定是否查询）
        when(tradeConfigService.getTradeConfig()).thenReturn(buildConfig(1));
        assertThrows(ServiceException.class,
                () -> brokerageUserService.getBrokerageUserCountByBindUserId(1L, 2));
    }

    private void insertUser(Long id, Long bindUserId) {
        BrokerageUserDO user = new BrokerageUserDO();
        user.setId(id).setBindUserId(bindUserId).setBrokerageEnabled(true)
                .setBrokeragePrice(0).setFrozenPrice(0);
        brokerageUserMapper.insert(user);
    }

    private static TradeConfigDO buildConfig(int levelCount) {
        BrokerageLevelRule[] rules = new BrokerageLevelRule[levelCount];
        for (int i = 0; i < levelCount; i++) {
            rules[i] = new BrokerageLevelRule(i + 1, new BigDecimal("10"), 0);
        }
        return TradeConfigDO.builder().brokerageEnabled(true)
                .brokerageLevels(ListUtil.of(rules)).build();
    }

}
