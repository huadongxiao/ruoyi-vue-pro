package cn.iocoder.yudao.module.merchant.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Merchant 错误码枚举类
 *
 * merchant 系统，使用 1-012-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 商户 1-012-001-000 ==========
    ErrorCode MERCHANT_NOT_EXISTS = new ErrorCode(1_012_001_000, "商户不存在");
    ErrorCode MERCHANT_USER_NOT_BIND = new ErrorCode(1_012_001_001, "当前账号未绑定商户");
    ErrorCode MERCHANT_DISABLE = new ErrorCode(1_012_001_002, "商户已停用");
    ErrorCode MERCHANT_NAME_EXISTS = new ErrorCode(1_012_001_003, "商户名称({})已存在");
    ErrorCode MERCHANT_STATUS_ILLEGAL = new ErrorCode(1_012_001_004, "商户状态({})不允许该操作");

    // ========== 商户进件申请 1-012-002-000 ==========
    ErrorCode MERCHANT_APPLY_NOT_EXISTS = new ErrorCode(1_012_002_000, "进件申请单不存在");
    ErrorCode MERCHANT_APPLY_EXISTS_DOING = new ErrorCode(1_012_002_001, "存在进件中的申请单，请等待汇付审核结果");
    ErrorCode MERCHANT_APPLY_ILLEGAL_STATUS = new ErrorCode(1_012_002_002, "申请单状态({})不允许该操作");
    ErrorCode MERCHANT_APPLY_PARAM_INVALID = new ErrorCode(1_012_002_003, "进件参数不合法：{}");

    // ========== 汇付交互 1-012-003-000 ==========
    ErrorCode HUIFU_SIGN_ERROR = new ErrorCode(1_012_003_000, "汇付加签/验签失败：{}");
    ErrorCode HUIFU_REQUEST_ERROR = new ErrorCode(1_012_003_001, "汇付请求异常：{}");
    ErrorCode HUIFU_RESP_CODE_ERROR = new ErrorCode(1_012_003_002, "汇付业务返回码({})：{}");
    ErrorCode HUIFU_CALLBACK_VERIFY_ERROR = new ErrorCode(1_012_003_003, "汇付回调验签失败");

}
