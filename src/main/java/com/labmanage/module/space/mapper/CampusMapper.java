package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.space.entity.Campus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 校区 Mapper
 */
@Mapper
public interface CampusMapper extends BaseMapper<Campus> {

    /**
     * 校区下未删除楼栋数（删除校验）
     */
    @Select("SELECT COUNT(*) FROM building WHERE campus_id = #{campusId} AND deleted = 0")
    long countBuildings(@Param("campusId") Long campusId);

    /**
     * 引用该校区的有效数据范围记录数（删除校验）
     */
    @Select("SELECT COUNT(*) FROM sys_user_data_scope WHERE campus_id = #{campusId} AND deleted = 0")
    long countDataScopes(@Param("campusId") Long campusId);
}
