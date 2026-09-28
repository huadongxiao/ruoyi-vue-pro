package cn.iocoder.yudao.module.merchant.framework.huifu;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_CALLBACK_VERIFY_ERROR;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_RESP_CODE_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HuifuClient} 的单元测试类
 *
 * 仅覆盖「请求体组装 + 响应解析 + 返回码校验」等纯逻辑，不发起真实 HTTP 请求。
 */
public class HuifuClientTest {

    private static KeyPair keyPair;

    private HuifuClient huifuClient;

    private HuifuProperties properties;

    @BeforeAll
    public static void beforeAll() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @BeforeEach
    public void setUp() {
        properties = new HuifuProperties();
        properties.setSysId("sys_test");
        properties.setProductId("PROD_TEST");
        properties.setPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        // 单测中「汇付公钥」使用平台公钥，便于模拟汇付侧返回
        properties.setHuifuPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        huifuClient = new HuifuClient();
        ReflectionTestUtils.setField(huifuClient, "properties", properties);
    }

    @Test
    public void testBuildBody_signed() {
        // 准备参数
        Map<String, Object> data = new HashMap<>();
        data.put("req_seq_id", "M1");
        data.put("req_date", "20260928");

        // 调用
        Map<String, Object> body = huifuClient.buildBody(data);

        // 断言
        assertEquals("sys_test", body.get("sys_id"));
        assertEquals("PROD_TEST", body.get("product_id"));
        assertNotNull(body.get("sign"));
        assertTrue(HuifuSigner.verify(data, properties.getHuifuPublicKey(), (String) body.get("sign")));
    }

    @Test
    public void testParseResponse_verifySuccess() {
        // 准备参数：模拟汇付同步返回（data 为 JSON 对象 + sign）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resp_code", "00000000");
        data.put("huifu_id", "6666000000000001");
        data.put("apply_no", "2022011100377000");
        String dataJson = JsonUtils.toJsonString(data);
        String sign = HuifuSigner.signText(HuifuSigner.sortJson(data), properties.getPrivateKey());
        String raw = "{\"data\":" + dataJson + ",\"sign\":\"" + sign + "\"}";

        // 调用
        Map<String, Object> result = huifuClient.parseResponse(raw, true);

        // 断言
        assertEquals("6666000000000001", result.get("huifu_id"));
        assertEquals("2022011100377000", result.get("apply_no"));
    }

    @Test
    public void testParseResponse_dataAsJsonString() {
        // 准备参数：data 传 JSON 字符串的兼容场景（不验签）
        String raw = "{\"data\":\"{\\\"resp_code\\\":\\\"00000000\\\",\\\"file_id\\\":\\\"abc\\\"}\",\"sign\":\"\"}";

        // 调用
        Map<String, Object> result = huifuClient.parseResponse(raw, false);

        // 断言
        assertEquals("abc", result.get("file_id"));
    }

    @Test
    public void testParseResponse_verifyFail() {
        // 准备参数：签名非法
        String raw = "{\"data\":{\"resp_code\":\"00000000\"},\"sign\":\"invalid\"}";

        // 调用，并断言异常
        assertServiceException(() -> huifuClient.parseResponse(raw, true), HUIFU_CALLBACK_VERIFY_ERROR);
    }

    @Test
    public void testAssertRespCode_error() {
        // 准备参数：业务返回码非成功
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resp_code", "10020001");
        data.put("resp_desc", "参数错误");

        // 调用，并断言异常
        assertServiceException(() -> huifuClient.assertRespCode(data), HUIFU_RESP_CODE_ERROR, "10020001", "参数错误");
    }

    @Test
    public void testAssertRespCode_success() {
        // 准备参数
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resp_code", "00000000");
        data.put("file_id", "abc");

        // 调用，无异常即通过
        huifuClient.assertRespCode(data);
        assertEquals("abc", data.get("file_id"));
    }

}
