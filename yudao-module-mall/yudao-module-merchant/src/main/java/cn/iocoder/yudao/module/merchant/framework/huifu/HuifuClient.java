package cn.iocoder.yudao.module.merchant.framework.huifu;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_CALLBACK_VERIFY_ERROR;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_REQUEST_ERROR;
import static cn.iocoder.yudao.module.merchant.enums.ErrorCodeConstants.HUIFU_RESP_CODE_ERROR;

/**
 * 汇付（斗拱）客户端
 *
 * 覆盖进件闭环用到的 4 个接口：图片上传、企业商户进件（KYC）、申请单状态查询、商户业务开通。
 * 接口文档见 docs/huifu/。
 */
@Slf4j
@Component
public class HuifuClient {

    @Resource
    private HuifuProperties properties;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
    }

    /**
     * 图片上传（multipart/form-data，无加签、无验签）
     *
     * @param content  文件内容
     * @param fileName 文件名
     * @param fileType 汇付图片类型，如 F07
     * @param huifuId  汇付商户号，未开户可为空
     * @param reqSeqId 请求流水号
     * @param reqDate  请求日期 yyyyMMdd
     * @return 汇付 file_id
     */
    public String uploadPicture(byte[] content, String fileName, String fileType,
                                String huifuId, String reqSeqId, String reqDate) {
        // 1. 业务参数（JSON 字符串）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("req_seq_id", reqSeqId);
        data.put("req_date", reqDate);
        data.put("file_type", fileType);
        if (StrUtil.isNotBlank(huifuId)) {
            data.put("huifu_id", huifuId);
        }
        // 2. 表单
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("sys_id", properties.getSysId());
        form.add("product_id", properties.getProductId());
        form.add("data", JsonUtils.toJsonString(data));
        form.add("file", new ByteArrayResource(content) {

            @Override
            public String getFilename() {
                return fileName;
            }

        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        // 3. 请求
        ResponseEntity<String> response = restTemplate.postForEntity(
                properties.getGatewayUrl() + "/v2/supplementary/picture",
                new HttpEntity<>(form, headers), String.class);
        // 4. 解析（图片上传不验签）
        Map<String, Object> respData = parseResponse(response.getBody(), false);
        assertRespCode(respData);
        return (String) respData.get("file_id");
    }

    /**
     * 企业商户进件（KYC），受理成功 resp_code=00000000
     */
    public Map<String, Object> entOpen(Map<String, Object> data) {
        return post("/v2/merchant/basicdata/ent", data);
    }

    /**
     * 个人（小微）商户进件，受理成功 resp_code=90000000（审核中）
     */
    public Map<String, Object> indvOpen(Map<String, Object> data) {
        return post("/v2/merchant/basicdata/indv", data);
    }

    /**
     * 申请单状态查询
     */
    public Map<String, Object> queryApplyStatus(Map<String, Object> data) {
        return post("/v2/merchant/basicdata/status/query", data);
    }

    /**
     * 商户业务开通
     */
    public Map<String, Object> busiOpen(Map<String, Object> data) {
        return post("/v2/merchant/busi/open", data);
    }

    /**
     * 组装并加签请求体（包级可见，供单测）
     */
    Map<String, Object> buildBody(Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sys_id", properties.getSysId());
        body.put("product_id", properties.getProductId());
        body.put("data", new TreeMap<>(data));
        body.put("sign", HuifuSigner.sign(data, properties.getPrivateKey()));
        return body;
    }

    private Map<String, Object> post(String path, Map<String, Object> data) {
        ResponseEntity<String> response = restTemplate.postForEntity(
                properties.getGatewayUrl() + path, buildBody(data), String.class);
        return parseResponse(response.getBody(), true);
    }

    /**
     * 解析响应 data；verifySign = true 时用汇付公钥验签（同步返参：排序后验签）
     */
    Map<String, Object> parseResponse(String raw, boolean verifySign) {
        Map<String, Object> body = JsonUtils.parseMap(raw);
        if (body == null) {
            throw exception(HUIFU_REQUEST_ERROR, "响应报文为空");
        }
        Object dataObj = body.get("data");
        Map<String, Object> data = dataObj instanceof String
                ? JsonUtils.parseMap((String) dataObj)
                : JsonUtils.convertObject(dataObj, new TypeReference<Map<String, Object>>() {});
        if (data == null) {
            throw exception(HUIFU_REQUEST_ERROR, "响应 data 为空");
        }
        if (verifySign && !HuifuSigner.verify(data, properties.getHuifuPublicKey(), (String) body.get("sign"))) {
            throw exception(HUIFU_CALLBACK_VERIFY_ERROR);
        }
        return data;
    }

    /**
     * 校验业务返回码
     */
    void assertRespCode(Map<String, Object> data) {
        String respCode = (String) data.get("resp_code");
        if (!"00000000".equals(respCode)) {
            throw exception(HUIFU_RESP_CODE_ERROR, respCode, data.get("resp_desc"));
        }
    }

}
