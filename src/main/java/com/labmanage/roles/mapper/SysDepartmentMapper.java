package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.entity.SysDepartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 组织部门 Mapper
 */
@Mapper
public interface SysDepartmentMapper extends BaseMapper<SysDepartment> {

    /**
     * 自动生成编码（DEPT_ + 4 位序号）的当前最大值。
     * 手写 SQL 不过滤逻辑删除行是有意为之：业务码删除后不复用，序号须单调递增。
     */
    @Select("SELECT MAX(department_code) FROM sys_department WHERE department_code REGEXP '^DEPT_[0-9]{4}$'")
    String selectMaxGeneratedCode();
}
