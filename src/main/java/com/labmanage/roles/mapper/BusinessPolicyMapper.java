package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.entity.BusinessPolicy;
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
}
