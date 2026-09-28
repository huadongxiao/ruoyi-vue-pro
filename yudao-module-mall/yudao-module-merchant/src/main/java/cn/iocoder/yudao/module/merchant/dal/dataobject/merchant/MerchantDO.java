package cn.iocoder.yudao.module.merchant.dal.dataobject.merchant;

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

import java.time.LocalDateTime;

/**
 * 商户 DO
 *
 * 状态枚举：{@link cn.iocoder.yudao.module.merchant.enums.MerchantStatusEnum}
 */
@TableName("merchant")
@KeySequence("merchant_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantDO extends BaseDO {

    /**
     * 商户编号
     */
    @TableId
    private Long id;
    /**
     * 绑定的 admin 用户编号
     */
    private Long userId;
    /**
     * 商户名称
     */
    private String name;
    /**
     * 商户简称
     */
    private String shortName;
    /**
     * 店铺 LOGO
     */
    private String logo;
    /**
     * 备注
     */
    private String remark;
    /**
     * 状态
     */
    private Integer status;
    /**
     * 商户主体类型：1企业商户 2小微商户（个人）
     *
     * 枚举：{@link cn.iocoder.yudao.module.merchant.enums.MerchantTypeEnum}
     */
    private Integer merchantType;
    /**
     * 汇付商户号
     */
    private String huifuId;
    /**
     * 外部商户号（= 本表 id，进件时写入汇付 ext_mer_id）
     */
    private String extMerId;
    /**
     * 联系人姓名
     */
    private String contactName;
    /**
     * 联系人手机号
     */
    private String contactMobile;
    /**
     * 联系人邮箱
     */
    private String contactEmail;
    /**
     * 结算卡号（脱敏）
     */
    private String settleCardNoMasked;
    /**
     * 最近进件提交时间
     */
    private LocalDateTime submitTime;
    /**
     * 商户生效时间
     */
    private LocalDateTime effectTime;

}
