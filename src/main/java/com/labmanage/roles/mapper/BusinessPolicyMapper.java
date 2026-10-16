package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.roles.dto.PolicyPageQueryDTO;
import com.labmanage.roles.entity.BusinessPolicy;
import com.labmanage.roles.vo.PolicyPageItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * 业务策略参数 Mapper
 */
@Mapper
public interface BusinessPolicyMapper extends BaseMapper<BusinessPolicy> {

    /**
     * 查询当前时间命中生效区间的全部启用策略（service 层按 code 取最高版本）。
     */
    @Select("""
            SELECT * FROM business_policy
            WHERE deleted = 0 AND enabled = 1
              AND effective_from <= #{now}
              AND (effective_to IS NULL OR effective_to > #{now})
            """)
    List<BusinessPolicy> selectEffectivePolicies(@Param("now") Instant now);

    /**
     * 策略分页列表（按编码分组升序、组内版本倒序）。
     */
    @Select("""
            <script>
            SELECT CAST(p.id AS CHAR) AS id,
                   p.policy_code AS policyCode,
                   p.policy_version AS policyVersion,
                   p.policy_name AS policyName,
                   p.effective_from AS effectiveFrom,
                   p.effective_to AS effectiveTo,
                   p.config AS config,
                   p.enabled AS enabled,
                   p.create_time AS createTime
            FROM business_policy p
            WHERE p.deleted = 0
            <if test="query.keyword != null and query.keyword != ''">
                AND (p.policy_code LIKE CONCAT('%', #{query.keyword}, '%')
                     OR p.policy_name LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.policyCode != null and query.policyCode != ''">
                AND p.policy_code = #{query.policyCode}
            </if>
            ORDER BY p.policy_code ASC, p.policy_version DESC, p.id DESC
            </script>
            """)
    Page<PolicyPageItemVO> selectPolicyPage(Page<PolicyPageItemVO> page, @Param("query") PolicyPageQueryDTO query);

    /**
     * 同码当前最高版本号；无记录返回 NULL。
     */
    @Select("""
            SELECT MAX(policy_version) FROM business_policy
            WHERE deleted = 0 AND policy_code = #{policyCode}
            """)
    Integer selectMaxVersion(@Param("policyCode") String policyCode);
}
