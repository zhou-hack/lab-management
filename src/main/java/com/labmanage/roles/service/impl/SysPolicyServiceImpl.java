package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.PolicyPageQueryDTO;
import com.labmanage.roles.dto.PolicyPublishDTO;
import com.labmanage.roles.entity.BusinessPolicy;
import com.labmanage.roles.mapper.BusinessPolicyMapper;
import com.labmanage.roles.service.PolicyService;
import com.labmanage.roles.service.SysPolicyService;
import com.labmanage.roles.vo.PolicyPageItemVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 业务策略管理服务实现
 */
@Service
public class SysPolicyServiceImpl implements SysPolicyService {

    private final BusinessPolicyMapper policyMapper;

    private final PolicyService policyService;

    private final JsonMapper jsonMapper;

    public SysPolicyServiceImpl(BusinessPolicyMapper policyMapper, PolicyService policyService,
                                JsonMapper jsonMapper) {
        this.policyMapper = policyMapper;
        this.policyService = policyService;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public PageResult<PolicyPageItemVO> page(PolicyPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<PolicyPageItemVO> page = policyMapper.selectPolicyPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publish(PolicyPublishDTO dto) {
        Integer maxVersion = policyMapper.selectMaxVersion(dto.policyCode());
        if (maxVersion == null) {
            throw new BusinessException(40000, "策略编码未登记，不能发布新版本");
        }
        if (dto.effectiveTo() != null && !dto.effectiveTo().isAfter(dto.effectiveFrom())) {
            throw new BusinessException(40000, "生效结束必须晚于生效开始");
        }
        validateConfig(dto.config());
        // 发布即替代：同事务停用同码全部旧启用版本，保证任一时刻同码仅一个生效版本
        // （预置版本 effective_to 为 NULL 长期有效，严格区间不重叠会使新版本永远无法发布）
        BusinessPolicy disableOld = new BusinessPolicy();
        disableOld.setEnabled(0);
        policyMapper.update(disableOld, Wrappers.<BusinessPolicy>lambdaUpdate()
                .eq(BusinessPolicy::getPolicyCode, dto.policyCode())
                .eq(BusinessPolicy::getEnabled, 1));
        int newVersion = maxVersion + 1;
        BusinessPolicy policy = new BusinessPolicy();
        policy.setPolicyCode(dto.policyCode());
        policy.setPolicyVersion(newVersion);
        policy.setPolicyName(dto.policyName());
        policy.setEffectiveFrom(dto.effectiveFrom());
        policy.setEffectiveTo(dto.effectiveTo());
        policy.setConfig(dto.config());
        policy.setEnabled(1);
        try {
            policyMapper.insert(policy);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(40900, "版本号冲突，请重试");
        }
        policyService.refresh();
        return newVersion;
    }

    /** config 必须为合法 JSON 对象（策略参数为固定结构 JSON） */
    private void validateConfig(String config) {
        JsonNode node;
        try {
            node = jsonMapper.readTree(config);
        } catch (Exception e) {
            throw new BusinessException(40000, "策略参数不是合法 JSON");
        }
        if (node == null || !node.isObject()) {
            throw new BusinessException(40000, "策略参数必须为 JSON 对象");
        }
    }
}
