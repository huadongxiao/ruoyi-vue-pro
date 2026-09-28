package cn.iocoder.yudao.module.merchant.framework.huifu;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 汇付（斗拱）配置项
 *
 * 对应 application.yaml 的 yudao.merchant.huifu
 */
@ConfigurationProperties(prefix = "yudao.merchant.huifu")
@Data
public class HuifuProperties {

    /**
     * 渠道商 / 平台 huifu_id（公共参数 sys_id）
     */
    private String sysId;
    /**
     * 汇付产品号（公共参数 product_id）
     */
    private String productId;
    /**
     * 平台私钥（Base64，PKCS8）
     */
    private String privateKey;
    /**
     * 汇付公钥（Base64，X509）
     */
    private String huifuPublicKey;
    /**
     * 网关地址
     */
    private String gatewayUrl = "https://api.huifu.com";
    /**
     * 进件审核结果回调地址（KYC async_return_url）
     */
    private String kycCallbackUrl;
    /**
     * 业务开通结果回调地址（busi_async_return_url）
     */
    private String busiCallbackUrl;
    /**
     * 微信配置对象 JSON 串（wx_conf_list，含费率）
     */
    private String wxConfList;
    /**
     * 支付宝配置对象 JSON 串（ali_conf_list，含费率）
     */
    private String aliConfList;
    /**
     * 线上业务类型编码（scene_type 含线上时必填）
     */
    private String onlineBusiType;
    /**
     * 运营媒介材料 JSON 串（online_media_info_list，scene_type 含线上时必填）
     */
    private String onlineMediaInfoList;

}
