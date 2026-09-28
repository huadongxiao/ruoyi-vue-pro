package cn.iocoder.yudao.module.trade.service.brokerage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.*;
import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.number.MoneyUtils;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.BrokerageLevelRule;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.product.api.spu.dto.ProductSpuRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.brokerage.vo.record.BrokerageRecordPageReqVO;
import cn.iocoder.yudao.module.trade.controller.app.brokerage.vo.record.AppBrokerageProductPriceRespVO;
import cn.iocoder.yudao.module.trade.controller.app.brokerage.vo.user.AppBrokerageUserRankByPriceRespVO;
import cn.iocoder.yudao.module.trade.controller.app.brokerage.vo.user.AppBrokerageUserRankPageReqVO;
import cn.iocoder.yudao.module.trade.convert.brokerage.BrokerageRecordConvert;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageRecordDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.brokerage.BrokerageUserDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.config.TradeConfigDO;
import cn.iocoder.yudao.module.trade.dal.mysql.brokerage.BrokerageRecordMapper;
import cn.iocoder.yudao.module.trade.enums.brokerage.BrokerageRecordBizTypeEnum;
import cn.iocoder.yudao.module.trade.enums.brokerage.BrokerageRecordStatusEnum;
import cn.iocoder.yudao.module.trade.service.brokerage.bo.BrokerageAddReqBO;
import cn.iocoder.yudao.module.trade.service.brokerage.bo.UserBrokerageSummaryRespBO;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.getMaxValue;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.getMinValue;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.BROKERAGE_WITHDRAW_USER_BALANCE_NOT_ENOUGH;

/**
 * 佣金记录 Service 实现类
 *
 * @author owen
 */
@Slf4j
@Service
@Validated
public class BrokerageRecordServiceImpl implements BrokerageRecordService {

    @Resource
    private BrokerageRecordMapper brokerageRecordMapper;
    @Resource
    private TradeConfigService tradeConfigService;
    @Resource
    private BrokerageUserService brokerageUserService;

    @Resource
    private ProductSpuApi productSpuApi;
    @Resource
    private ProductSkuApi productSkuApi;

    @Override
    public BrokerageRecordDO getBrokerageRecord(Long id) {
        return brokerageRecordMapper.selectById(id);
    }

    @Override
    public PageResult<BrokerageRecordDO> getBrokerageRecordPage(BrokerageRecordPageReqVO pageReqVO) {
        return brokerageRecordMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBrokerage(Long userId, Long brokerageUserId, BrokerageRecordBizTypeEnum bizType,
                             List<BrokerageAddReqBO> list) {
        TradeConfigDO memberConfig = tradeConfigService.getTradeConfig();
        // 0 未启用分销功能
        if (memberConfig == null || !BooleanUtil.isTrue(memberConfig.getBrokerageEnabled())) {
            log.error("[addBrokerage][增加佣金失败：brokerageEnabled 未配置，userId({}) brokerageUserId({}) bizType({}) list({})",
                    userId, brokerageUserId, bizType, list);
            return;
        }
        // 0.1 校验层级规则已配置
        List<BrokerageLevelRule> globalRules = tradeConfig.getBrokerageLevels();
        if (CollUtil.isEmpty(globalRules)) {
            log.error("[addBrokerage][增加佣金失败：brokerageLevels 未配置，userId({}) bizType({})", userId, bizType);
            return;
        }

        // 1.1 获得一级推广人
        BrokerageUserDO firstUser = brokerageUserService.getBrokerageUser(brokerageUserId);
        if (firstUser == null || !BooleanUtil.isTrue(firstUser.getBrokerageEnabled())) {
            return;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelBrokerage(BrokerageRecordBizTypeEnum bizType, String bizId) {
        List<BrokerageRecordDO> records = brokerageRecordMapper.selectListByBizTypeAndBizId(bizType.getType(), bizId);
        if (CollUtil.isEmpty(records)) {
            log.error("[cancelBrokerage][bizId({}) bizType({}) 更新为已失效失败：记录不存在]", bizId, bizType);
            return;
        }

        records.forEach(record -> {
            // 1. 更新佣金记录为已失效
            BrokerageRecordDO updateObj = new BrokerageRecordDO().setStatus(BrokerageRecordStatusEnum.CANCEL.getStatus());
            int updateRows = brokerageRecordMapper.updateByIdAndStatus(record.getId(), record.getStatus(), updateObj);
            if (updateRows == 0) {
                log.error("[cancelBrokerage][record({}) 更新为已失效失败]", record.getId());
                return;
            }

            // 2. 更新用户的佣金
            if (BrokerageRecordStatusEnum.WAIT_SETTLEMENT.getStatus().equals(record.getStatus())) {
                brokerageUserService.updateUserFrozenPrice(record.getUserId(), -record.getPrice());
            } else if (BrokerageRecordStatusEnum.SETTLEMENT.getStatus().equals(record.getStatus())) {
                brokerageUserService.updateUserPrice(record.getUserId(), -record.getPrice());
            }
        });
    }

    /**
     * 计算佣金 = 比例部分 + 固定部分
     *
     * @param basePrice  佣金基数，单位：分
     * @param percent    返佣比例，百分比数值。例如 10 表示 10%
     * @param fixedPrice 固定佣金，单位：分（单件）
     * @param count      购买数量
     * @return 佣金
     */
    int calculatePrice(Integer basePrice, BigDecimal percent, Integer fixedPrice, Integer count) {
        // 1. 比例部分：基数与比例都为正时才计算，否则记 0（沿用旧实现的守卫语义，避免 NPE）
        int percentPart = 0;
        if (basePrice != null && basePrice > 0 && percent != null && percent.compareTo(BigDecimal.ZERO) > 0) {
            percentPart = MoneyUtils.calculateRatePriceFloor(basePrice, percent.doubleValue());
        }
        // 2. 固定部分：单件固定佣金 × 购买数量
        int fixedPart = (fixedPrice != null ? fixedPrice : 0) * (count != null ? count : 1);
        // 3. 相加
        return percentPart + fixedPart;
    }

    /**
     * 增加某一级用户的佣金
     *
     * @param user                用户
     * @param list                佣金增加参数列表
     * @param brokerageFrozenDays 冻结天数
     * @param globalRules         全局层级规则
     * @param bizType             业务类型
     * @param sourceUserLevel     来源用户等级，从 1 开始
     */
    private void addBrokerage(BrokerageUserDO user, List<BrokerageAddReqBO> list, Integer brokerageFrozenDays,
                              List<BrokerageLevelRule> globalRules, BrokerageRecordBizTypeEnum bizType,
                              Integer sourceUserLevel) {
        // 1.1 处理冻结时间
        LocalDateTime unfreezeTime = null;
        if (brokerageFrozenDays != null && brokerageFrozenDays > 0) {
            unfreezeTime = LocalDateTime.now().plusDays(brokerageFrozenDays);
        }
        // 1.2 计算分佣
        int totalBrokerage = 0;
        List<BrokerageRecordDO> records = new ArrayList<>();
        for (BrokerageAddReqBO item : list) {
            // 规则来源以 subCommissionType 为准：
            // 商品独立分销用商品配置（为空表示该商品未配佣金，记 0，不能回落到全局）；否则用全局配置
            List<BrokerageLevelRule> rules = BooleanUtil.isTrue(item.getSubCommissionType())
                    ? item.getLevels() : globalRules;
            if (CollUtil.isEmpty(rules) || sourceUserLevel > rules.size()) {
                continue;
            }
            BrokerageLevelRule rule = rules.get(sourceUserLevel - 1);
            int brokeragePrice = calculatePrice(item.getBasePrice(), rule.getPercent(), rule.getFixedPrice(),
                    item.getCount());
            if (brokeragePrice <= 0) {
                continue;
            }
            totalBrokerage += brokeragePrice;
            // 创建记录实体
            records.add(BrokerageRecordConvert.INSTANCE.convert(user, bizType, item.getBizId(),
                    brokerageFrozenDays, brokeragePrice, unfreezeTime, item.getTitle(),
                    item.getSourceUserId(), sourceUserLevel));
        }
        if (CollUtil.isEmpty(records)) {
            return;
        }
        // 1.3 保存佣金记录
        brokerageRecordMapper.insertBatch(records);

        // 2. 更新用户佣金
        if (brokerageFrozenDays != null && brokerageFrozenDays > 0) { // 更新用户冻结佣金
            brokerageUserService.updateUserFrozenPrice(user.getId(), totalBrokerage);
        } else { // 更新用户可用佣金
            brokerageUserService.updateUserPrice(user.getId(), totalBrokerage);
        }
    }

    @Override
    public int unfreezeRecord() {
        // 1. 查询待结算的佣金记录
        List<BrokerageRecordDO> records = brokerageRecordMapper.selectListByStatusAndUnfreezeTimeLt(
                BrokerageRecordStatusEnum.WAIT_SETTLEMENT.getStatus(), LocalDateTime.now());
        if (CollUtil.isEmpty(records)) {
            return 0;
        }

        // 2. 遍历执行
        int count = 0;
        for (BrokerageRecordDO record : records) {
            try {
                boolean success = getSelf().unfreezeRecord(record);
                if (success) {
                    count++;
                }
            } catch (Exception e) {
                log.error("[unfreezeRecord][record({}) 更新为已结算失败]", record.getId(), e);
            }
        }
        return count;
    }

    /**
     * 解冻单条佣金记录
     *
     * @param record 佣金记录
     * @return 解冻是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean unfreezeRecord(BrokerageRecordDO record) {
        // 更新记录状态
        BrokerageRecordDO updateObj = new BrokerageRecordDO()
                .setStatus(BrokerageRecordStatusEnum.SETTLEMENT.getStatus())
                .setUnfreezeTime(LocalDateTime.now());
        int updateRows = brokerageRecordMapper.updateByIdAndStatus(record.getId(), record.getStatus(), updateObj);
        if (updateRows == 0) {
            log.error("[unfreezeRecord][record({}) 更新为已结算失败]", record.getId());
            return false;
        }

        // 更新用户冻结佣金
        brokerageUserService.updateFrozenPriceDecrAndPriceIncr(record.getUserId(), -record.getPrice());
        log.info("[unfreezeRecord][record({}) 更新为已结算成功]", record.getId());
        return true;
    }

    @Override
    public List<UserBrokerageSummaryRespBO> getUserBrokerageSummaryListByUserId(Collection<Long> userIds,
                                                                                Integer bizType, Integer status) {
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return brokerageRecordMapper.selectCountAndSumPriceByUserIdInAndBizTypeAndStatus(userIds, bizType, status);
    }

    @Override
    public Integer getSummaryPriceByUserId(Long userId, BrokerageRecordBizTypeEnum bizType, BrokerageRecordStatusEnum status,
                                           LocalDateTime beginTime, LocalDateTime endTime) {
        return brokerageRecordMapper.selectSummaryPriceByUserIdAndBizTypeAndCreateTimeBetween(userId,
                bizType.getType(), status.getStatus(), beginTime, endTime);
    }

    @Override
    public PageResult<AppBrokerageUserRankByPriceRespVO> getBrokerageUserChildSummaryPageByPrice(AppBrokerageUserRankPageReqVO pageReqVO) {
        IPage<AppBrokerageUserRankByPriceRespVO> pageResult = brokerageRecordMapper.selectSummaryPricePageGroupByUserId(
                MyBatisUtils.buildPage(pageReqVO),
                BrokerageRecordBizTypeEnum.ORDER.getType(), BrokerageRecordStatusEnum.SETTLEMENT.getStatus(),
                ArrayUtil.get(pageReqVO.getTimes(), 0), ArrayUtil.get(pageReqVO.getTimes(), 1));
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal());
    }

    @Override
    public Integer getUserRankByPrice(Long userId, LocalDateTime[] times) {
        // 用户的推广金额
        Integer price = brokerageRecordMapper.selectSummaryPriceByUserIdAndBizTypeAndCreateTimeBetween(userId,
                BrokerageRecordBizTypeEnum.ORDER.getType(), BrokerageRecordStatusEnum.SETTLEMENT.getStatus(),
                ArrayUtil.get(times, 0), ArrayUtil.get(times, 1));
        // 排在用户前面的人数
        Integer greaterCount = brokerageRecordMapper.selectCountByPriceGt(price,
                BrokerageRecordBizTypeEnum.ORDER.getType(), BrokerageRecordStatusEnum.SETTLEMENT.getStatus(),
                ArrayUtil.get(times, 0), ArrayUtil.get(times, 1));
        // 获得排名
        return ObjUtil.defaultIfNull(greaterCount, 0) + 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBrokerage(Long userId, BrokerageRecordBizTypeEnum bizType, String bizId, Integer brokeragePrice, String title) {
        // 1. 校验佣金余额
        BrokerageUserDO user = brokerageUserService.getBrokerageUser(userId);
        int balance = Optional.of(user)
                .map(BrokerageUserDO::getBrokeragePrice).orElse(0);
        if (balance + brokeragePrice < 0) {
            throw exception(BROKERAGE_WITHDRAW_USER_BALANCE_NOT_ENOUGH, MoneyUtils.fenToYuanStr(balance));
        }

        // 2. 更新佣金余额
        boolean success = brokerageUserService.updateUserPrice(userId, brokeragePrice);
        if (!success) {
            // 失败时，则抛出异常。只会出现扣减佣金时，余额不足的情况
            throw exception(BROKERAGE_WITHDRAW_USER_BALANCE_NOT_ENOUGH, MoneyUtils.fenToYuanStr(balance));
        }

        // 3. 新增记录
        BrokerageRecordDO record = BrokerageRecordConvert.INSTANCE.convert(user, bizType, bizId, 0, brokeragePrice,
                null, title, null, null);
        brokerageRecordMapper.insert(record);
    }

    @Override
    public AppBrokerageProductPriceRespVO calculateProductBrokeragePrice(Long userId, Long spuId) {
        // 1. 构建默认的返回值
        AppBrokerageProductPriceRespVO respVO = new AppBrokerageProductPriceRespVO().setEnabled(false)
                .setBrokerageMinPrice(0).setBrokerageMaxPrice(0);

        // 2.1 校验分销功能是否开启
        TradeConfigDO tradeConfig = tradeConfigService.getTradeConfig();
        if (tradeConfig == null || BooleanUtil.isFalse(tradeConfig.getBrokerageEnabled())) {
            return respVO;
        }
        // 2.2 校验用户是否有分销资格
        respVO.setEnabled(brokerageUserService.getUserBrokerageEnabled(userId));
        if (BooleanUtil.isFalse(respVO.getEnabled())) {
            return respVO;
        }
        // 2.3 校验商品是否存在
        ProductSpuRespDTO spu = productSpuApi.getSpu(spuId);
        if (spu == null) {
            return respVO;
        }

        // 3.1 获取商品 SKU 列表
        List<ProductSkuRespDTO> skuList = productSkuApi.getSkuListBySpuId(ListUtil.of(spuId));
        // 3.2 取一级规则（浏览者作为直属上级，拿到的是第 1 级佣金）
        BrokerageLevelRule firstRule = CollUtil.getFirst(tradeConfig.getBrokerageLevels());
        if (firstRule == null) {
            return respVO;
        }
        if (BooleanUtil.isTrue(spu.getSubCommissionType())) {
            // 3.3.1 商品独立分销：取 SKU 第 1 级的固定佣金
            Integer fixedMinPrice = getMinValue(skuList,
                    sku -> getLevelFixedPrice(sku.getBrokerageLevels(), 1));
            Integer fixedMaxPrice = getMaxValue(skuList,
                    sku -> getLevelFixedPrice(sku.getBrokerageLevels(), 1));
            respVO.setBrokerageMinPrice(calculatePrice(null, null, fixedMinPrice, 1))
                    .setBrokerageMaxPrice(calculatePrice(null, null, fixedMaxPrice, 1));
        } else {
            // 3.3.2 全局比例模式
            Integer spuMinPrice = getMinValue(skuList, ProductSkuRespDTO::getPrice);
            Integer spuMaxPrice = getMaxValue(skuList, ProductSkuRespDTO::getPrice);
            respVO.setBrokerageMinPrice(calculatePrice(spuMinPrice, firstRule.getPercent(), 0, 1))
                    .setBrokerageMaxPrice(calculatePrice(spuMaxPrice, firstRule.getPercent(), 0, 1));
        }
        return respVO;
    }

    /**
     * 取指定层级的固定佣金，缺失时按 0 处理
     */
    private static Integer getLevelFixedPrice(List<BrokerageLevelRule> rules, int level) {
        if (CollUtil.isEmpty(rules) || rules.size() < level) {
            return 0;
        }
        return ObjectUtil.defaultIfNull(rules.get(level - 1).getFixedPrice(), 0);
    }

    /**
     * 获得自身的代理对象，解决 AOP 生效问题
     *
     * @return 自己
     */
    private BrokerageRecordServiceImpl getSelf() {
        return SpringUtil.getBean(getClass());
    }

}
