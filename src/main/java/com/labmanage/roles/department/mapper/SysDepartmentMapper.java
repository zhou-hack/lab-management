package com.labmanage.roles.department.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.roles.department.entity.SysDepartment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 组织 Mapper（只读支撑）
 */
@Mapper
public interface SysDepartmentMapper extends BaseMapper<SysDepartment> {
}
