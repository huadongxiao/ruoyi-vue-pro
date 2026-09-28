package cn.iocoder.yudao.module.merchant.framework.huifu;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_SIGN_ERROR;

/**
 * 汇付（斗拱）V2 接口加签 / 验签
 *
 * 规则见 docs/huifu/接口加签验签说明.md：
 * 1. 仅对 body.data 第一层按 key ASCII 字典序排序后序列化；嵌套对象以 JSON 字符串传递，不再排序；
 * 2. 算法 SHA256WithRSA + Base64：请求用平台私钥加签，响应 / 回调用汇付公钥验签；
 * 3. 同步返参需排序（{@link #verify}），异步回调原文不排序（{@link #verifyRaw}）。
 */
@Slf4j
public class HuifuSigner {

    /**
     * 对 data 第一层按 key 字典序排序后序列化
     */
    public static String sortJson(Map<String, Object> data) {
        return JsonUtils.toJsonString(new TreeMap<>(data));
    }

    /**
     * 加签：data 第一层排序后，用平台私钥签名
     */
    public static String sign(Map<String, Object> data, String privateKeyBase64) {
        return signText(sortJson(data), privateKeyBase64);
    }

    /**
     * 加签：对给定的字符串（不排序）签名
     */
    public static String signText(String text, String privateKeyBase64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(privateKeyBase64);
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(bytes));
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initSign(privateKey);
            signature.update(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            log.error("[signText][加签异常]", e);
            throw exception(HUIFU_SIGN_ERROR, e.getMessage());
        }
    }

    /**
     * 验签：同步返参（data 第一层排序后验签）
     */
    public static boolean verify(Map<String, Object> data, String publicKeyBase64, String sign) {
        return verifyText(sortJson(data), publicKeyBase64, sign);
    }

    /**
     * 验签：异步回调（data 原文不排序）
     */
    public static boolean verifyRaw(String dataJson, String publicKeyBase64, String sign) {
        return verifyText(dataJson, publicKeyBase64, sign);
    }

    private static boolean verifyText(String text, String publicKeyBase64, String sign) {
        if (StrUtil.isBlank(sign)) {
            return false;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(publicKeyBase64);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initVerify(publicKey);
            signature.update(text.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.error("[verifyText][验签异常]", e);
            return false;
        }
    }

    private HuifuSigner() {
    }

}
