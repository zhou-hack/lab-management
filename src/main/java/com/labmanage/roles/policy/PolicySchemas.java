package com.labmanage.roles.policy;

import com.labmanage.roles.vo.PolicySchemaFieldVO;

import java.util.List;
import java.util.Map;

/**
 * 业务策略参数元数据注册表：按策略编码登记参数字段（key/中文名/类型/范围/单位）。
 * 发布新版本时按此做字段级校验，前端按此渲染结构化表单；未登记的编码不允许发布。
 */
public final class PolicySchemas {

    private PolicySchemas() {
    }

    private static final String INT = "INT";

    /** 会话有效期取值范围（秒）：5 分钟 ~ 24 小时 */
    private static final int SESSION_MIN = 300;
    private static final int SESSION_MAX = 86400;

    private static final Map<String, List<PolicySchemaFieldVO>> REGISTRY = Map.of(
            "LOGIN_MAX_FAIL", List.of(
                    new PolicySchemaFieldVO("maxFailCount", "连续失败锁定阈值", INT, 1, 10, "次")),
            "LOGIN_LOCK_MINUTES", List.of(
                    new PolicySchemaFieldVO("lockMinutes", "登录锁定时长", INT, 1, 1440, "分钟")),
            "SMS_CODE_TTL_SECONDS", List.of(
                    new PolicySchemaFieldVO("ttlSeconds", "验证码有效期", INT, 60, 3600, "秒")),
            "SMS_RESEND_INTERVAL_SECONDS", List.of(
                    new PolicySchemaFieldVO("intervalSeconds", "验证码重发间隔", INT, 30, 600, "秒")),
            "SMS_DAILY_LIMIT", List.of(
                    new PolicySchemaFieldVO("dailyLimit", "验证码日发送上限", INT, 1, 100, "条")),
            "SMS_CODE_MAX_FAIL", List.of(
                    new PolicySchemaFieldVO("maxFailCount", "验证码错误作废次数", INT, 1, 10, "次")),
            "SESSION_TIMEOUT_STUDENT_TEACHER", List.of(
                    new PolicySchemaFieldVO("timeoutSeconds", "学生/老师会话有效期", INT, SESSION_MIN, SESSION_MAX, "秒")),
            "SESSION_TIMEOUT_ADMIN", List.of(
                    new PolicySchemaFieldVO("timeoutSeconds", "管理员会话有效期", INT, SESSION_MIN, SESSION_MAX, "秒"))
    );

    /** 全部编码的参数字段元数据（前端结构化表单用） */
    public static Map<String, List<PolicySchemaFieldVO>> all() {
        return REGISTRY;
    }

    /** 指定编码的参数字段元数据；未登记返回 null */
    public static List<PolicySchemaFieldVO> fieldsOf(String policyCode) {
        return REGISTRY.get(policyCode);
    }
}
