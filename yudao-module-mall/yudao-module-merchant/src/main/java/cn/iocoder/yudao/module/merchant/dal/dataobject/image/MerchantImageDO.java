package cn.iocoder.yudao.module.merchant.dal.dataobject.image;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 商户资质图片映射 DO
 *
 * 记录「平台 infra 文件 → 汇付 file_id」的映射，图片类型枚举：{@link cn.iocoder.yudao.module.merchant.enums.MerchantImageBizTypeEnum}
 */
@TableName("merchant_image")
@KeySequence("merchant_image_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantImageDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 商户编号
     */
    private Long merchantId;
    /**
     * 图片类型（汇付 file_type）
     */
    private String bizType;
    /**
     * 平台 infra 文件编号
     */
    private Long infraFileId;
    /**
     * 汇付图片文件 ID
     */
    private String huifuFileId;

}
