package cn.iocoder.yudao.module.merchant.framework.huifu;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HuifuSigner} 的单元测试类
 *
 * 使用运行时生成的 RSA 密钥对，验证「排序序列化 / 加签 / 验签」三项规则。
 */
public class HuifuSignerTest {

    private static KeyPair keyPair;

    @BeforeAll
    public static void beforeAll() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    private static String privateKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    }

    private static String publicKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @Test
    public void testSortJson_asciiOrder() {
        Map<String, Object> data = new HashMap<>();
        data.put("b", "2");
        data.put("a", "1");
        assertEquals("{\"a\":\"1\",\"b\":\"2\"}", HuifuSigner.sortJson(data));
    }

    @Test
    public void testSortJson_nestedStringNotSorted() {
        Map<String, Object> data = new HashMap<>();
        data.put("z", "1");
        data.put("nested", "{\"z\":1,\"a\":2}");
        assertEquals("{\"nested\":\"{\\\"z\\\":1,\\\"a\\\":2}\",\"z\":\"1\"}", HuifuSigner.sortJson(data));
    }

    @Test
    public void testSignAndVerifySync_roundTrip() {
        // 准备参数
        Map<String, Object> data = new HashMap<>();
        data.put("req_seq_id", "M20260928000001-1024");
        data.put("req_date", "20260928");

        // 调用
        String sign = HuifuSigner.sign(data, privateKey());

        // 断言：验签成功
        assertTrue(HuifuSigner.verify(data, publicKey(), sign));
        // 断言：篡改后验签失败
        data.put("req_date", "20260929");
        assertFalse(HuifuSigner.verify(data, publicKey(), sign));
    }

    @Test
    public void testVerifyRaw_callbackNotSorted() {
        // 准备参数：原文顺序与字典序不同
        String raw = "{\"req_seq_id\":\"M1\",\"audit_status\":\"Y\"}";
        String sign = HuifuSigner.signText(raw, privateKey());

        // 断言
        assertTrue(HuifuSigner.verifyRaw(raw, publicKey(), sign));
        assertFalse(HuifuSigner.verifyRaw("{\"req_seq_id\":\"M2\",\"audit_status\":\"Y\"}", publicKey(), sign));
    }

    @Test
    public void testVerify_blankSign() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", "1");
        assertFalse(HuifuSigner.verify(data, publicKey(), ""));
    }

}
