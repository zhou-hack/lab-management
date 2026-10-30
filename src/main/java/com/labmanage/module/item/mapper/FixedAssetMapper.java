package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.vo.AssetSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FixedAssetMapper extends BaseMapper<FixedAsset> {

    @Select("SELECT id FROM fixed_asset WHERE asset_code = #{assetCode} LIMIT 1")
    Long selectIdByAssetCodeIncludingDeleted(@Param("assetCode") String assetCode);

    @Select("SELECT * FROM fixed_asset WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    FixedAsset selectByIdForUpdate(@Param("id") Long id);

    @Select("""
            <script>
            SELECT * FROM fixed_asset
            WHERE material_id = #{materialId} AND deleted = 0
            ORDER BY id ASC
            <if test="forUpdate">FOR UPDATE</if>
            </script>
            """)
    List<FixedAsset> selectByMaterialId(@Param("materialId") Long materialId,
                                        @Param("forUpdate") boolean forUpdate);

    @Select("""
            SELECT CAST(m.id AS CHAR) AS materialId,
                   m.material_name AS materialName,
                   m.specification AS specification,
                   m.base_unit AS baseUnit,
                   CAST(COALESCE(SUM(CASE WHEN a.id IS NOT NULL AND a.status NOT IN ('LOST', 'SCRAPPED') THEN 1 ELSE 0 END), 0) AS DECIMAL(18,3)) AS ownedQuantity,
                   CAST(COALESCE(SUM(CASE WHEN a.id IS NOT NULL AND a.status = 'IDLE' THEN 1 ELSE 0 END), 0) AS DECIMAL(18,3)) AS availableQuantity
            FROM material m
            LEFT JOIN fixed_asset a ON a.material_id = m.id AND a.deleted = 0
            WHERE m.deleted = 0 AND m.material_type = 'FIXED_ASSET'
            GROUP BY m.id, m.material_name, m.specification, m.base_unit
            ORDER BY m.material_name ASC, m.id ASC
            """)
    List<AssetSummaryVO> selectAssetSummary();
}
