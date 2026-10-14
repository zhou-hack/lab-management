package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.entity.AssistantGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/**
 * 助理授权 Mapper
 */
@Mapper
public interface AssistantGrantMapper extends BaseMapper<AssistantGrant> {

    /**
     * 是否存在有效授权：ACTIVE 且 start_at <= now < end_at（半开区间）。
     */
    @Select("""
            SELECT COUNT(1) FROM assistant_grant
            WHERE deleted = 0 AND status = 'ACTIVE' AND student_id = #{studentId}
              AND start_at <= #{now} AND end_at > #{now}
            """)
    int countValidGrant(@Param("studentId") Long studentId, @Param("now") Instant now);
}
