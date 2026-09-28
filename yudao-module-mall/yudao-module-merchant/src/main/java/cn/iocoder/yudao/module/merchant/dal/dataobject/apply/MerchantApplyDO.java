package cn.iocoder.yudao.module.merchant.dal.dataobject.apply;

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
 * 商户进件申请单 DO
 *
 * 每次进件一条，保留历史；状态枚举：{@link cn.iocoder.yudao.module.merchant.enums.MerchantApplyStatusEnum}
 */
@TableName("merchant_apply")
@KeySequence("merchant_apply_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantApplyDO extends BaseDO {

    /**
     * 申请单编号
     */
    @TableId
    private Long id;
    /**
     * 商户编号
     */
    private Long merchantId;
    /**
     * 汇付请求流水号（当日唯一）
     */
    private String reqSeqId;
    /**
     * 汇付请求日期（yyyyMMdd）
     */
    private String reqDate;
    /**
     * 汇付申请单号
     */
    private String applyNo;
    /**
     * 状态
     */
    private Integer status;
    /**
     * 汇付审核结果：Y 通过 / N 拒绝 / F 失败
     */
    private String auditStatus;
    /**
     * 汇付审核描述
     */
    private String auditDesc;
    /**
     * 汇付商户号
     */
    private String huifuId;
    /**
     * 银行卡序列号
     */
    private String tokenNo;
    /**
     * 提交时间
     */
    private LocalDateTime submitTime;
    /**
     * 审核时间
     */
    private LocalDateTime auditTime;
    /**
     * 回调/响应原始报文
     */
    private String rawNotifyJson;

}
