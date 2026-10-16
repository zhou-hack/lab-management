package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.module.space.dto.LabPageQueryDTO;
import com.labmanage.module.space.entity.Lab;
import com.labmanage.module.space.vo.LabPageItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 实验室 Mapper
 */
@Mapper
public interface LabMapper extends BaseMapper<Lab> {

    /**
     * 实验室分页列表（join 楼栋/校区/部门名；ID 列 CAST 为字符）。
     */
    @Select("""
            <script>
            SELECT CAST(l.id AS CHAR) AS id,
                   CAST(l.building_id AS CHAR) AS buildingId,
                   b.building_name AS buildingName,
                   c.campus_name AS campusName,
                   CAST(l.department_id AS CHAR) AS departmentId,
                   d.department_name AS departmentName,
                   l.lab_code AS labCode,
                   l.lab_name AS labName,
                   l.lab_type AS labType,
                   l.floor_no AS floorNo,
                   l.room_no AS roomNo,
                   l.capacity AS capacity,
                   l.area_sqm AS areaSqm,
                   l.location_note AS locationNote,
                   l.enabled AS enabled,
                   l.create_time AS createTime
            FROM lab l
            JOIN building b ON b.id = l.building_id AND b.deleted = 0
            JOIN campus c ON c.id = b.campus_id AND c.deleted = 0
            JOIN sys_department d ON d.id = l.department_id AND d.deleted = 0
            WHERE l.deleted = 0
            <if test="query.keyword != null and query.keyword != ''">
                AND (l.lab_code LIKE CONCAT('%', #{query.keyword}, '%')
                     OR l.lab_name LIKE CONCAT('%', #{query.keyword}, '%')
                     OR l.room_no LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.campusId != null">
                AND b.campus_id = #{query.campusId}
            </if>
            <if test="query.buildingId != null">
                AND l.building_id = #{query.buildingId}
            </if>
            <if test="query.departmentId != null">
                AND l.department_id = #{query.departmentId}
            </if>
            <if test="query.labType != null and query.labType != ''">
                AND l.lab_type = #{query.labType}
            </if>
            <if test="query.enabled != null">
                AND l.enabled = #{query.enabled}
            </if>
            ORDER BY l.lab_code ASC
            </script>
            """)
    Page<LabPageItemVO> selectLabPage(Page<LabPageItemVO> page, @Param("query") LabPageQueryDTO query);
}
