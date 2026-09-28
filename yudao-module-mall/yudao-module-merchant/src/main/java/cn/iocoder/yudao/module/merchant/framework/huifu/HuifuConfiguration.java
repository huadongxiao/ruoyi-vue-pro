package cn.iocoder.yudao.module.merchant.framework.huifu;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 汇付（斗拱）组件的 Configuration
 */
@Configuration
@EnableConfigurationProperties(HuifuProperties.class)
public class HuifuConfiguration {
}
