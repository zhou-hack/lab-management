package com.labmanage.roles.service;

/**
 * 业务策略参数读取（business_policy 当前生效版本，进程内缓存）
 */
public interface PolicyService {

    /**
     * 读取策略整数参数；策略缺失或键缺失时返回默认值。
     *
     * @param policyCode 策略编码
     * @param configKey  config JSON 内的键
     * @param defaultValue 默认值
     */
    int getInt(String policyCode, String configKey, int defaultValue);

    /**
     * 刷新缓存（策略发布后调用）。
     */
    void refresh();
}
