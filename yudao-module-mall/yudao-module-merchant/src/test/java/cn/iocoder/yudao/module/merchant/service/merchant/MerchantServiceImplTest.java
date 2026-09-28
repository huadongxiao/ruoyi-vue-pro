package cn.iocoder.yudao.module.merchant.service.merchant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.merchant.constant.MerchantConstants;
import cn.iocoder.yudao.module.merchant.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.merchant.dal.mysql.merchant.MerchantMapper;
import cn.iocoder.yudao.module.merchant.enums.MerchantStatusEnum;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import javax.annotation.Resource;
import java.util.Collections;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_NOT_EXISTS;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.MERCHANT_STATUS_ILLEGAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link MerchantServiceImpl} 的单元测试类
 */
@Import(MerchantServiceImpl.class)
public class MerchantServiceImplTest extends BaseDbUnitTest {

    @Resource
    private MerchantServiceImpl merchantService;

    @Resource
    private MerchantMapper merchantMapper;

    @MockBean
    private PermissionApi permissionApi;

    @MockBean
    private AdminUserApi adminUserApi;

    @Test
    public void testInitMerchant_success() {
        // 准备参数
        Long userId = randomLongId();

        // 调用
        Long merchantId = merchantService.initMerchant(userId);

        // 断言：草稿已创建，ext_mer_id 等于 id
        assertNotNull(merchantId);
        MerchantDO merchant = merchantMapper.selectById(merchantId);
        assertEquals(MerchantStatusEnum.DRAFT.getStatus(), merchant.getStatus());
        assertEquals(userId, merchant.getUserId());
        assertEquals(String.valueOf(merchantId), merchant.getExtMerId());
        // 断言：赋予「待生效商家」角色
        Mockito.verify(permissionApi).assignUserRole(userId,
                Collections.singleton(MerchantConstants.ROLE_ID_MERCHANT_PENDING));
    }

    @Test
    public void testInitMerchant_exists() {
        // mock 数据
        MerchantDO dbMerchant = randomPojo(MerchantDO.class, o -> {
            o.setStatus(MerchantStatusEnum.DRAFT.getStatus());
            o.setExtMerId(null);
        });
        merchantMapper.insert(dbMerchant);

        // 调用
        Long merchantId = merchantService.initMerchant(dbMerchant.getUserId());

        // 断言：幂等，返回已存在的商户
        assertEquals(dbMerchant.getId(), merchantId);
        Mockito.verify(permissionApi, Mockito.never()).assignUserRole(Mockito.any(), Mockito.any());
    }

    @Test
    public void testUpdateMerchantStatus_disable() {
        // mock 数据
        MerchantDO dbMerchant = randomPojo(MerchantDO.class, o -> o.setStatus(MerchantStatusEnum.EFFECTIVE.getStatus()));
        merchantMapper.insert(dbMerchant);

        // 调用
        merchantService.updateMerchantStatus(dbMerchant.getId(), MerchantStatusEnum.DISABLED.getStatus());

        // 断言：商户停用 + 账号禁用
        assertEquals(MerchantStatusEnum.DISABLED.getStatus(),
                merchantMapper.selectById(dbMerchant.getId()).getStatus());
        Mockito.verify(adminUserApi).updateUserStatus(dbMerchant.getUserId(),
                CommonStatusEnum.DISABLE.getStatus());
    }

    @Test
    public void testUpdateMerchantStatus_illegal() {
        // mock 数据
        MerchantDO dbMerchant = randomPojo(MerchantDO.class, o -> o.setStatus(MerchantStatusEnum.DRAFT.getStatus()));
        merchantMapper.insert(dbMerchant);

        // 调用，并断言异常
        assertServiceException(() -> merchantService.updateMerchantStatus(dbMerchant.getId(),
                MerchantStatusEnum.APPLYING.getStatus()), MERCHANT_STATUS_ILLEGAL,
                MerchantStatusEnum.APPLYING.getStatus());
    }

    @Test
    public void testEffectMerchant_success() {
        // mock 数据
        MerchantDO dbMerchant = randomPojo(MerchantDO.class, o -> {
            o.setStatus(MerchantStatusEnum.APPLYING.getStatus());
            o.setHuifuId("");
        });
        merchantMapper.insert(dbMerchant);

        // 调用
        merchantService.effectMerchant(dbMerchant.getId(), "6666000100000001");

        // 断言：商户生效
        MerchantDO merchant = merchantMapper.selectById(dbMerchant.getId());
        assertEquals(MerchantStatusEnum.EFFECTIVE.getStatus(), merchant.getStatus());
        assertEquals("6666000100000001", merchant.getHuifuId());
        assertNotNull(merchant.getEffectTime());
        // 断言：赋予「商家」角色
        Mockito.verify(permissionApi).assignUserRole(dbMerchant.getUserId(),
                Collections.singleton(MerchantConstants.ROLE_ID_MERCHANT));
    }

    @Test
    public void testValidateMerchantExists_notExists() {
        assertServiceException(() -> merchantService.validateMerchantExists(randomLongId()), MERCHANT_NOT_EXISTS);
    }

}
