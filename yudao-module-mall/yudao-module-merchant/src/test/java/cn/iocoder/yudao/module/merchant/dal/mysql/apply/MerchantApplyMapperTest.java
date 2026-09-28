package cn.iocoder.yudao.module.merchant.dal.mysql.apply;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.merchant.dal.dataobject.apply.MerchantApplyDO;
import cn.iocoder.yudao.module.merchant.enums.MerchantApplyStatusEnum;
import org.junit.jupiter.api.Test;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils.buildTime;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertPojoEquals;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MerchantApplyMapper} 鐨勫崟鍏冩祴璇曠被
 */
public class MerchantApplyMapperTest extends BaseDbUnitTest {

    @Resource
    private MerchantApplyMapper merchantApplyMapper;

    @Test
    public void testSelectByReqSeqId() {
        // 鍑嗗鍙傛暟
        MerchantApplyDO dbApply = randomPojo(MerchantApplyDO.class, o -> {
            o.setReqSeqId("M202609281200000001ABCD");
            o.setReqDate("20260928");
            o.setAuditStatus("Y");
        });
        merchantApplyMapper.insert(dbApply);

        // 璋冪敤锛屽苟鏂█
        assertPojoEquals(dbApply, merchantApplyMapper.selectByReqSeqId("M202609281200000001ABCD"));
    }

    @Test
    public void testSelectDoingByMerchantId() {
        // mock 鏁版嵁锛氫竴鏉¤繘浠朵腑 + 涓€鏉″凡閫氳繃
        MerchantApplyDO dbDoing = randomPojo(MerchantApplyDO.class, o -> {
            o.setMerchantId(1L);
            o.setReqDate("20260928");
            o.setAuditStatus("Y");
            o.setStatus(MerchantApplyStatusEnum.DOING.getStatus());
        });
        merchantApplyMapper.insert(dbDoing);
        merchantApplyMapper.insert(randomPojo(MerchantApplyDO.class, o -> {
            o.setMerchantId(1L);
            o.setReqDate("20260928");
            o.setAuditStatus("Y");
            o.setStatus(MerchantApplyStatusEnum.APPROVED.getStatus());
        }));

        // 璋冪敤锛屽苟鏂█锛氬彧鏌ュ埌杩涗欢涓殑閭ｆ潯
        assertPojoEquals(dbDoing, merchantApplyMapper.selectDoingByMerchantId(1L));
    }

    @Test
    public void testSelectDoingListBySubmitTimeLt() {
        // mock 数据：进件中+早于15分钟前(应查出)；进件中+刚提交(不应查出)；已通过+早提交(不应查出)
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        LocalDateTime before = now.minusMinutes(15);
        MerchantApplyDO dbMatched = randomPojo(MerchantApplyDO.class, o -> {
            o.setReqDate("20260928");
            o.setAuditStatus("Y");
            o.setStatus(MerchantApplyStatusEnum.DOING.getStatus());
            o.setSubmitTime(now.minusMinutes(30));
        });
        merchantApplyMapper.insert(dbMatched);
        merchantApplyMapper.insert(randomPojo(MerchantApplyDO.class, o -> {
            o.setReqDate("20260928");
            o.setAuditStatus("Y");
            o.setStatus(MerchantApplyStatusEnum.DOING.getStatus());
            o.setSubmitTime(now.minusMinutes(5));
        }));
        merchantApplyMapper.insert(randomPojo(MerchantApplyDO.class, o -> {
            o.setReqDate("20260928");
            o.setAuditStatus("Y");
            o.setStatus(MerchantApplyStatusEnum.APPROVED.getStatus());
            o.setSubmitTime(now.minusMinutes(30));
        }));

        // 璋冪敤锛屽苟鏂█
        List<MerchantApplyDO> list = merchantApplyMapper.selectDoingListBySubmitTimeLt(before);
        assertEquals(1, list.size());
        assertPojoEquals(dbMatched, list.get(0));
    }

    @Test
    public void testSelectApprovedList() {
        // mock 鏁版嵁锛氫竴鏉￠€氳繃 + 涓€鏉¤繘浠朵腑
        MerchantApplyDO dbApproved = randomPojo(MerchantApplyDO.class, o -> {
            o.setReqDate("20260928");
            o.setAuditStatus("Y");
            o.setStatus(MerchantApplyStatusEnum.APPROVED.getStatus());
            o.setSubmitTime(buildTime(2026, 9, 28));
        });
        merchantApplyMapper.insert(dbApproved);
        merchantApplyMapper.insert(randomPojo(MerchantApplyDO.class, o -> {
            o.setReqDate("20260928");
            o.setAuditStatus("Y");
            o.setStatus(MerchantApplyStatusEnum.DOING.getStatus());
            o.setSubmitTime(buildTime(2026, 9, 28));
        }));

        // 璋冪敤锛屽苟鏂█
        List<MerchantApplyDO> list = merchantApplyMapper.selectApprovedList();
        assertEquals(1, list.size());
        assertPojoEquals(dbApproved, list.get(0));
    }

}
