package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.space.entity.LabOpenRule;
import com.labmanage.module.space.vo.LabOpenRuleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实验室开放规则 Mapper
 */
@Mapper
public interface LabOpenRuleMapper extends BaseMapper<LabOpenRule> {

    /** 指定实验室的开放规则列表（按星期与开放时间排序） */
    @Select("""
            SELECT CAST(id AS CHAR) AS id,
                   CAST(lab_id AS CHAR) AS labId,
                   weekday,
                   open_time AS openTime,
                   close_time AS closeTime,
                   valid_from AS validFrom,
                   valid_to AS validTo,
                   enabled
            FROM lab_open_rule
            WHERE deleted = 0 AND lab_id = #{labId}
            ORDER BY weekday ASC, open_time ASC
            """)
    List<LabOpenRuleVO> selectByLabId(@Param("labId") Long labId);
}
