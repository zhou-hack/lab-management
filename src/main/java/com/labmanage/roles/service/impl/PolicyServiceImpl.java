package com.labmanage.roles.service.impl;

import com.labmanage.roles.entity.BusinessPolicy;
import com.labmanage.roles.mapper.BusinessPolicyMapper;
import com.labmanage.roles.service.PolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务策略参数读取实现：同码取最高版本，进程内缓存。
 */
@Service
public class PolicyServiceImpl implements PolicyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyServiceImpl.class);

    private final BusinessPolicyMapper policyMapper;

    private final JsonMapper jsonMapper;

    private volatile Map<String, JsonNode> cache;

    public PolicyServiceImpl(BusinessPolicyMapper policyMapper, JsonMapper jsonMapper) {
        this.policyMapper = policyMapper;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public int getInt(String policyCode, String configKey, int defaultValue) {
        JsonNode config = cache().get(policyCode);
        if (config == null) {
            return defaultValue;
        }
        JsonNode value = config.get(configKey);
        return value == null ? defaultValue : value.asInt(defaultValue);
    }

    @Override
    public synchronized void refresh() {
        cache = load();
    }

    private Map<String, JsonNode> cache() {
        Map<String, JsonNode> current = cache;
        if (current == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = load();
                }
                current = cache;
            }
        }
        return current;
    }

    private Map<String, JsonNode> load() {
        List<BusinessPolicy> policies = policyMapper.selectEffectivePolicies(Instant.now());
        Map<String, BusinessPolicy> latestByCode = new HashMap<>();
        for (BusinessPolicy policy : policies) {
            latestByCode.merge(policy.getPolicyCode(), policy,
                    (a, b) -> b.getPolicyVersion() > a.getPolicyVersion() ? b : a);
        }
        Map<String, JsonNode> result = new HashMap<>();
        latestByCode.forEach((code, policy) -> {
            try {
                result.put(code, jsonMapper.readTree(policy.getConfig()));
            } catch (Exception e) {
                log.warn("策略 {} 配置 JSON 解析失败，已忽略：{}", code, e.getMessage());
            }
        });
        return result;
    }
}
