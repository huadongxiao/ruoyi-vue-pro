package cn.iocoder.yudao.module.trade.service.brokerage;

import cn.hutool.core.collection.ListUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.brokerage.vo.record.BrokerageRecordPageReqVO;
import cn.iocoder.yudao.module.trade.controller.app.brokerage.vo.record.AppBrokerageProductPriceRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageRecordDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageUserDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.dal.mysql.brokerage.BrokerageRecordMapper;
import cn.iocoder.yudao.module.trade.enums.brokerage.BrokerageRecordBizTypeEnum;
import cn.iocoder.yudao.module.trade.enums.brokerage.BrokerageRecordStatusEnum;
import cn.iocoder.yudao.module.trade.service.brokerage.bo.BrokerageAddReqBO;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.buildBetweenTime;
import static cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.buildTime;
import static cn.iocoder.yudao.framework.common.util.object.ObjectUtils.cloneIgnoreId;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link BrokerageRecordServiceImpl} 的单元测试类
 *
 * @author owen
 */
@Import(BrokerageRecordServiceImpl.class)
public class BrokerageRecordServiceImplTest extends BaseDbUnitTest {

    @Resource
    private BrokerageRecordServiceImpl brokerageRecordService;
    @Resource
    private BrokerageRecordMapper brokerageRecordMapper;

    @MockBean
    private TradeConfigService tradeConfigService;
    @MockBean
    private BrokerageUserService brokerageUserService;
    @MockBean
    private ProductSpuApi productSpuApi;
    @MockBean
    private ProductSkuApi productSkuApi;

    @Test
    public void testGetBrokerageRecordPage() {
        // mock 数据
        BrokerageRecordDO dbBrokerageRecord = randomPojo(BrokerageRecordDO.class, o -> { // 等会查询到
            o.setUserId(1L);
            o.setBizType(BrokerageRecordBizTypeEnum.ORDER.getType());
            o.setStatus(BrokerageRecordStatusEnum.SETTLEMENT.getStatus());
            o.setSourceUserLevel(1);
            o.setSourceUserId(100L);
            o.setCreateTime(buildTime(2023, 2, 10));
            o.setDeleted(false);
        });
        brokerageRecordMapper.insert(dbBrokerageRecord);
        // 测试 userId 不匹配
        brokerageRecordMapper.insert(cloneIgnoreId(dbBrokerageRecord, o -> o.setUserId(2L)));
        // 测试 bizType 不匹配
        brokerageRecordMapper.insert(cloneIgnoreId(dbBrokerageRecord,
                o -> o.setBizType(BrokerageRecordBizTypeEnum.WITHDRAW.getType())));
        // 测试 status 不匹配
        brokerageRecordMapper.insert(cloneIgnoreId(dbBrokerageRecord,
                o -> o.setStatus(BrokerageRecordStatusEnum.CANCEL.getStatus())));
        // 测试 sourceUserLevel 不匹配
        brokerageRecordMapper.insert(cloneIgnoreId(dbBrokerageRecord, o -> o.setSourceUserLevel(2)));
        // 测试 createTime 不匹配
        brokerageRecordMapper.insert(cloneIgnoreId(dbBrokerageRecord,
                o -> o.setCreateTime(buildTime(2023, 3, 1))));
        // 准备参数
        BrokerageRecordPageReqVO reqVO = new BrokerageRecordPageReqVO();
        reqVO.setUserId(1L);
        reqVO.setBizType(BrokerageRecordBizTypeEnum.ORDER.getType());
        reqVO.setStatus(BrokerageRecordStatusEnum.SETTLEMENT.getStatus());
        reqVO.setSourceUserLevel(1);
        reqVO.setCreateTime(buildBetweenTime(2023, 2, 1, 2023, 2, 28));

        // 调用
        PageResult<BrokerageRecordDO> pageResult = brokerageRecordService.getBrokerageRecordPage(reqVO);
        // 断言
        assertEquals(1, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertPojoEquals(dbBrokerageRecord, pageResult.getList().get(0));
    }

    // ==================== calculatePrice：相加语义 ====================

    @Test
    public void testCalculatePrice_additive() {
        // 基数 1000 分：比例 10% => 100 分；固定 88 分 × 2 件 => 176 分；合计 276
        int brokerage = brokerageRecordService.calculatePrice(1000, new BigDecimal("10"), 88, 2);
        assertEquals(276, brokerage);
    }

    @Test
    public void testCalculatePrice_percentOnly() {
        // 固定为 0 时，只算比例部分：1000 × 10% = 100
        int brokerage = brokerageRecordService.calculatePrice(1000, new BigDecimal("10"), 0, 1);
        assertEquals(100, brokerage);
    }

    @Test
    public void testCalculatePrice_fixedOnly() {
        // 比例为 0 时，只算固定部分：88 × 3 = 264
        int brokerage = brokerageRecordService.calculatePrice(1000, BigDecimal.ZERO, 88, 3);
        assertEquals(264, brokerage);
    }

    @Test
    public void testCalculatePrice_nullAndNonPositiveGuards() {
        // 基数 null / 0 / 负数 或 比例 null / 0 时，比例部分记 0，且不抛 NPE
        assertEquals(0, brokerageRecordService.calculatePrice(null, new BigDecimal("10"), null, 1));
        assertEquals(0, brokerageRecordService.calculatePrice(0, new BigDecimal("10"), null, 1));
        assertEquals(0, brokerageRecordService.calculatePrice(-100, new BigDecimal("10"), null, 1));
        assertEquals(0, brokerageRecordService.calculatePrice(1000, null, null, 1));
        assertEquals(0, brokerageRecordService.calculatePrice(1000, BigDecimal.ZERO, null, 1));
    }

    // ==================== addBrokerage：N 级分佣 ====================

    @Test
    public void testAddBrokerage_threeLevels() {
        // 1. mock 配置：3 级，一级 10%+100 分，二级 5%，三级 2%，不冻结
        when(tradeConfigService.getTradeConfig()).thenReturn(buildConfig(
                new BrokerageLevelRule(1, new BigDecimal("10"), 100),
                new BrokerageLevelRule(2, new BigDecimal("5"), 0),
                new BrokerageLevelRule(3, new BigDecimal("2"), 0)));
        // 2. mock 链路：买家 1000L -> 上级 200L -> 300L -> 400L，均有资格
        when(brokerageUserService.getBindBrokerageUser(1000L)).thenReturn(buildUser(200L, 300L, true));
        when(brokerageUserService.getBrokerageUser(300L)).thenReturn(buildUser(300L, 400L, true));
        when(brokerageUserService.getBrokerageUser(400L)).thenReturn(buildUser(400L, null, true));
        // 3. 订单项：基数 1000 分，数量 1
        BrokerageAddReqBO item = buildItem("1", 1000, 1).setSubCommissionType(false);

        // 4. 调用
        brokerageRecordService.addBrokerage(1000L, BrokerageRecordBizTypeEnum.ORDER, ListUtil.of(item));

        // 5. 断言：一级 1000*10%+100=200；二级 1000*5%=50；三级 1000*2%=20
        List<BrokerageRecordDO> records = selectRecords("1");
        assertEquals(3, records.size());
        assertEquals(200, findRecord(records, 200L).getPrice().intValue());
        assertEquals(1, findRecord(records, 200L).getSourceUserLevel().intValue());
        assertEquals(50, findRecord(records, 300L).getPrice().intValue());
        assertEquals(2, findRecord(records, 300L).getSourceUserLevel().intValue());
        assertEquals(20, findRecord(records, 400L).getPrice().intValue());
        assertEquals(3, findRecord(records, 400L).getSourceUserLevel().intValue());
    }

    @Test
    public void testAddBrokerage_skipDisabledWithoutCompressingLevel() {
        // 3 级配置；一级无资格被跳过，二级、三级仍分别用第 2、3 级规则（层号不压缩）
        when(tradeConfigService.getTradeConfig()).thenReturn(buildConfig(
                new BrokerageLevelRule(1, new BigDecimal("10"), 0),
                new BrokerageLevelRule(2, new BigDecimal("5"), 0),
                new BrokerageLevelRule(3, new BigDecimal("2"), 0)));
        when(brokerageUserService.getBindBrokerageUser(1000L)).thenReturn(buildUser(200L, 300L, false)); // 一级无资格
        when(brokerageUserService.getBrokerageUser(300L)).thenReturn(buildUser(300L, 400L, true));
        when(brokerageUserService.getBrokerageUser(400L)).thenReturn(buildUser(400L, null, true));
        BrokerageAddReqBO item = buildItem("2", 1000, 1).setSubCommissionType(false);

        brokerageRecordService.addBrokerage(1000L, BrokerageRecordBizTypeEnum.ORDER, ListUtil.of(item));

        List<BrokerageRecordDO> records = selectRecords("2");
        assertEquals(2, records.size());                       // 一级被跳过，不产生记录
        assertEquals(50, findRecord(records, 300L).getPrice().intValue()); // 二级仍是 5%
        assertEquals(2, findRecord(records, 300L).getSourceUserLevel().intValue());
        assertEquals(20, findRecord(records, 400L).getPrice().intValue()); // 三级仍是 2%
        assertEquals(3, findRecord(records, 400L).getSourceUserLevel().intValue());
    }

    @Test
    public void testAddBrokerage_productOverride() {
        // 全局 3 级，但商品全量覆盖为「一级 0% + 固定 500」，二/三级为 0
        when(tradeConfigService.getTradeConfig()).thenReturn(buildConfig(
                new BrokerageLevelRule(1, new BigDecimal("10"), 0),
                new BrokerageLevelRule(2, new BigDecimal("5"), 0),
                new BrokerageLevelRule(3, new BigDecimal("2"), 0)));
        when(brokerageUserService.getBindBrokerageUser(1000L)).thenReturn(buildUser(200L, null, true));
        BrokerageAddReqBO item = buildItem("3", 1000, 1).setSubCommissionType(true)
                .setLevels(ListUtil.of(
                        new BrokerageLevelRule(1, BigDecimal.ZERO, 500),
                        new BrokerageLevelRule(2, BigDecimal.ZERO, 0),
                        new BrokerageLevelRule(3, BigDecimal.ZERO, 0)));

        brokerageRecordService.addBrokerage(1000L, BrokerageRecordBizTypeEnum.ORDER, ListUtil.of(item));

        List<BrokerageRecordDO> records = selectRecords("3");
        assertEquals(1, records.size());
        assertEquals(500, records.get(0).getPrice().intValue()); // 用商品配置的固定 500，而不是全局的 10%
    }

    @Test
    public void testAddBrokerage_productSubCommissionWithoutLevelsPaysNothing() {
        // 回归点：商品开启独立分销但未配佣金 -> 旧行为是 0 佣金，禁止回落到全局
        when(tradeConfigService.getTradeConfig()).thenReturn(buildConfig(
                new BrokerageLevelRule(1, new BigDecimal("10"), 0)));
        when(brokerageUserService.getBindBrokerageUser(1000L)).thenReturn(buildUser(200L, null, true));
        BrokerageAddReqBO item = buildItem("4", 1000, 1).setSubCommissionType(true).setLevels(null);

        brokerageRecordService.addBrokerage(1000L, BrokerageRecordBizTypeEnum.ORDER, ListUtil.of(item));

        assertTrue(selectRecords("4").isEmpty());
    }

    @Test
    public void testAddBrokerage_fixedPriceMultipliedByCount() {
        // 单件固定 100 分，买 3 件 => 300 分
        when(tradeConfigService.getTradeConfig()).thenReturn(buildConfig(
                new BrokerageLevelRule(1, BigDecimal.ZERO, 100)));
        when(brokerageUserService.getBindBrokerageUser(1000L)).thenReturn(buildUser(200L, null, true));
        BrokerageAddReqBO item = buildItem("5", 500, 3).setSubCommissionType(false);

        brokerageRecordService.addBrokerage(1000L, BrokerageRecordBizTypeEnum.ORDER, ListUtil.of(item));

        List<BrokerageRecordDO> records = selectRecords("5");
        assertEquals(1, records.size());
        assertEquals(300, records.get(0).getPrice().intValue());
    }

    // ==================== calculateProductBrokeragePrice ====================

    @Test
    public void testCalculateProductBrokeragePrice_globalPercent() {
        // mock：分销功能已开启，一级比例 10%，当前用户有分销资格
        TradeConfigDO tradeConfig = new TradeConfigDO();
        tradeConfig.setBrokerageEnabled(true);
        tradeConfig.setBrokerageLevels(ListUtil.of(new BrokerageLevelRule(1, new BigDecimal("10"), 0)));
        when(tradeConfigService.getTradeConfig()).thenReturn(tradeConfig);
        when(brokerageUserService.getUserBrokerageEnabled(100L)).thenReturn(true);

        // mock：商品未开启独立分销（subCommissionType = false），SKU 售价 1000 分
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setSubCommissionType(false);
        when(productSpuApi.getSpu(1L)).thenReturn(spu);

        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setPrice(1000);
        when(productSkuApi.getSkuListBySpuId(ListUtil.of(1L))).thenReturn(ListUtil.of(sku));

        // 调用
        AppBrokerageProductPriceRespVO result = brokerageRecordService.calculateProductBrokeragePrice(100L, 1L);

        // 断言：按 10% 比例计算，1000 * 10% = 100 分（向下取整）
        assertTrue(result.getEnabled());
        assertEquals(100, result.getBrokerageMinPrice().intValue());
        assertEquals(100, result.getBrokerageMaxPrice().intValue());
    }

    @Test
    public void testCalculateProductBrokeragePrice_subCommissionZeroFixed() {
        // mock：分销功能已开启，一级比例 10%，当前用户有分销资格
        TradeConfigDO tradeConfig = new TradeConfigDO();
        tradeConfig.setBrokerageEnabled(true);
        tradeConfig.setBrokerageLevels(ListUtil.of(new BrokerageLevelRule(1, new BigDecimal("10"), 0)));
        when(tradeConfigService.getTradeConfig()).thenReturn(tradeConfig);
        when(brokerageUserService.getUserBrokerageEnabled(100L)).thenReturn(true);

        // mock：商品开启独立分销（subCommissionType = true），SKU 固定佣金为 0（商家主动设置）
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setSubCommissionType(true);
        when(productSpuApi.getSpu(2L)).thenReturn(spu);

        ProductSkuRespDTO sku = new ProductSkuRespDTO();
        sku.setPrice(1000);
        sku.setBrokerageLevels(ListUtil.of(new BrokerageLevelRule(1, BigDecimal.ZERO, 0)));
        when(productSkuApi.getSkuListBySpuId(ListUtil.of(2L))).thenReturn(ListUtil.of(sku));

        // 调用
        AppBrokerageProductPriceRespVO result = brokerageRecordService.calculateProductBrokeragePrice(100L, 2L);

        // 断言：独立分销固定佣金为 0，应返回 0，不得回退到全局比例
        assertTrue(result.getEnabled());
        assertEquals(0, result.getBrokerageMinPrice().intValue());
        assertEquals(0, result.getBrokerageMaxPrice().intValue());
    }

    @Test
    public void testCalculateProductBrokeragePrice_subCommissionNullFixed() {
        // mock：分销功能已开启，一级比例 10%，当前用户有分销资格
        TradeConfigDO tradeConfig = new TradeConfigDO();
        tradeConfig.setBrokerageEnabled(true);
        tradeConfig.setBrokerageLevels(ListUtil.of(new BrokerageLevelRule(1, new BigDecimal("10"), 0)));
        when(tradeConfigService.getTradeConfig()).thenReturn(tradeConfig);
        when(brokerageUserService.getUserBrokerageEnabled(100L)).thenReturn(true);

        // mock：商品开启独立分销，其中一个 SKU 层级规则未配置
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setSubCommissionType(true);
        when(productSpuApi.getSpu(3L)).thenReturn(spu);

        ProductSkuRespDTO nullBrokerageSku = new ProductSkuRespDTO();
        nullBrokerageSku.setPrice(1000);
        nullBrokerageSku.setBrokerageLevels(null);
        ProductSkuRespDTO fixedBrokerageSku = new ProductSkuRespDTO();
        fixedBrokerageSku.setPrice(2000);
        fixedBrokerageSku.setBrokerageLevels(ListUtil.of(new BrokerageLevelRule(1, BigDecimal.ZERO, 200)));
        when(productSkuApi.getSkuListBySpuId(ListUtil.of(3L)))
                .thenReturn(ListUtil.of(nullBrokerageSku, fixedBrokerageSku));

        // 调用
        AppBrokerageProductPriceRespVO result = brokerageRecordService.calculateProductBrokeragePrice(100L, 3L);

        // 断言：独立分销固定佣金为空时按 0 处理，避免比较最小/最大佣金时报错
        assertTrue(result.getEnabled());
        assertEquals(0, result.getBrokerageMinPrice().intValue());
        assertEquals(200, result.getBrokerageMaxPrice().intValue());
    }

    @Test
    public void testCalculateProductBrokeragePrice_subCommissionEmptySkuList() {
        // mock：分销功能已开启，一级比例 10%，当前用户有分销资格
        TradeConfigDO tradeConfig = new TradeConfigDO();
        tradeConfig.setBrokerageEnabled(true);
        tradeConfig.setBrokerageLevels(ListUtil.of(new BrokerageLevelRule(1, new BigDecimal("10"), 0)));
        when(tradeConfigService.getTradeConfig()).thenReturn(tradeConfig);
        when(brokerageUserService.getUserBrokerageEnabled(100L)).thenReturn(true);

        // mock：商品开启独立分销，但查询不到 SKU
        ProductSpuRespDTO spu = new ProductSpuRespDTO();
        spu.setSubCommissionType(true);
        when(productSpuApi.getSpu(4L)).thenReturn(spu);
        when(productSkuApi.getSkuListBySpuId(ListUtil.of(4L))).thenReturn(ListUtil.of());

        // 调用
        AppBrokerageProductPriceRespVO result = brokerageRecordService.calculateProductBrokeragePrice(100L, 4L);

        // 断言：独立分销没有固定佣金时，不回退到全局比例
        assertTrue(result.getEnabled());
        assertEquals(0, result.getBrokerageMinPrice().intValue());
        assertEquals(0, result.getBrokerageMaxPrice().intValue());
    }

    // ==================== 测试辅助 ====================

    private static TradeConfigDO buildConfig(BrokerageLevelRule... rules) {
        return TradeConfigDO.builder()
                .brokerageEnabled(true).brokerageFrozenDays(0)
                .brokerageLevels(ListUtil.of(rules))
                .build();
    }

    private static BrokerageUserDO buildUser(Long id, Long bindUserId, boolean enabled) {
        // brokeragePrice / frozenPrice 不能为空：BrokerageRecordConvert 会用 brokeragePrice 填充
        // trade_brokerage_record.total_price（该列 NOT NULL）
        return BrokerageUserDO.builder().id(id).bindUserId(bindUserId).brokerageEnabled(enabled)
                .brokeragePrice(0).frozenPrice(0).build();
    }

    private static BrokerageAddReqBO buildItem(String bizId, Integer basePrice, Integer count) {
        BrokerageAddReqBO item = new BrokerageAddReqBO();
        item.setBizId(bizId).setBasePrice(basePrice).setCount(count)
                .setSourceUserId(1000L).setTitle("测试订单项");
        return item;
    }

    private List<BrokerageRecordDO> selectRecords(String bizId) {
        return brokerageRecordMapper.selectListByBizTypeAndBizId(
                BrokerageRecordBizTypeEnum.ORDER.getType(), bizId);
    }

    private static BrokerageRecordDO findRecord(List<BrokerageRecordDO> records, Long userId) {
        return records.stream().filter(r -> r.getUserId().equals(userId)).findFirst().orElseThrow();
    }

}
