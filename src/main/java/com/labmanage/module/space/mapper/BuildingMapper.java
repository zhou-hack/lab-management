package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.module.space.dto.BuildingPageQueryDTO;
import com.labmanage.module.space.entity.Building;
import com.labmanage.module.space.vo.BuildingOptionVO;
import com.labmanage.module.space.vo.BuildingPageItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 楼栋 Mapper
 */
@Mapper
public interface BuildingMapper extends BaseMapper<Building> {

    /**
     * 楼栋分页列表（join 校区名，附带实验室数；ID 列 CAST 为字符）。
     */
    @Select("""
            <script>
            SELECT CAST(b.id AS CHAR) AS id,
                   CAST(b.campus_id AS CHAR) AS campusId,
                   c.campus_name AS campusName,
                   b.building_code AS buildingCode,
                   b.building_name AS buildingName,
                   b.enabled AS enabled,
                   (SELECT COUNT(*) FROM lab l WHERE l.building_id = b.id AND l.deleted = 0) AS labCount,
                   b.create_time AS createTime
            FROM building b
            JOIN campus c ON c.id = b.campus_id AND c.deleted = 0
            WHERE b.deleted = 0
            <if test="query.keyword != null and query.keyword != ''">
                AND (b.building_code LIKE CONCAT('%', #{query.keyword}, '%')
                     OR b.building_name LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.campusId != null">
                AND b.campus_id = #{query.campusId}
            </if>
            <if test="query.enabled != null">
                AND b.enabled = #{query.enabled}
            </if>
            ORDER BY b.building_code ASC
            </script>
            """)
    Page<BuildingPageItemVO> selectBuildingPage(Page<BuildingPageItemVO> page, @Param("query") BuildingPageQueryDTO query);

    /** 启用楼栋下拉选项（可按校区过滤） */
    @Select("""
            <script>
            SELECT CAST(b.id AS CHAR) AS id,
                   CAST(b.campus_id AS CHAR) AS campusId,
                   b.building_name AS buildingName
            FROM building b
            JOIN campus c ON c.id = b.campus_id AND c.deleted = 0 AND c.enabled = 1
            WHERE b.deleted = 0 AND b.enabled = 1
            <if test="campusId != null">
                AND b.campus_id = #{campusId}
            </if>
            ORDER BY b.building_code ASC
            </script>
            """)
    List<BuildingOptionVO> selectEnabledOptions(@Param("campusId") Long campusId);
}
