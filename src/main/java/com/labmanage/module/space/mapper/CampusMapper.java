package com.labmanage.module.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.module.space.dto.CampusPageQueryDTO;
import com.labmanage.module.space.entity.Campus;
import com.labmanage.module.space.vo.CampusOptionVO;
import com.labmanage.module.space.vo.CampusPageItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 校区 Mapper
 */
@Mapper
public interface CampusMapper extends BaseMapper<Campus> {

    /**
     * 校区分页列表（附带启用楼栋数；ID 列 CAST 为字符）。
     */
    @Select("""
            <script>
            SELECT CAST(c.id AS CHAR) AS id,
                   c.campus_code AS campusCode,
                   c.campus_name AS campusName,
                   c.address AS address,
                   c.enabled AS enabled,
                   (SELECT COUNT(*) FROM building b WHERE b.campus_id = c.id AND b.deleted = 0) AS buildingCount,
                   c.create_time AS createTime
            FROM campus c
            WHERE c.deleted = 0
            <if test="query.keyword != null and query.keyword != ''">
                AND (c.campus_code LIKE CONCAT('%', #{query.keyword}, '%')
                     OR c.campus_name LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.enabled != null">
                AND c.enabled = #{query.enabled}
            </if>
            ORDER BY c.campus_code ASC
            </script>
            """)
    Page<CampusPageItemVO> selectCampusPage(Page<CampusPageItemVO> page, @Param("query") CampusPageQueryDTO query);

    /** 启用校区下拉选项 */
    @Select("""
            SELECT CAST(id AS CHAR) AS id, campus_name AS campusName
            FROM campus
            WHERE deleted = 0 AND enabled = 1
            ORDER BY campus_code ASC
            """)
    List<CampusOptionVO> selectEnabledOptions();
}
