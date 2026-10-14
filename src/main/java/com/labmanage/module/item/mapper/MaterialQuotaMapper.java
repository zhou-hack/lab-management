package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 领用定额 Mapper
 */
@Mapper
public interface MaterialQuotaMapper extends BaseMapper<MaterialQuota> {

    /**
     * 按开课实例 + SKU 集合一次查询定额（定额快照 / upsert 存量检查批量加载）
     */
    @Select("""
            <script>
            SELECT * FROM material_quota WHERE offering_id = #{offeringId} AND deleted = 0
              AND sku_id IN
            <foreach collection='skuIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    List<MaterialQuota> selectByOfferingAndSkuIds(@Param("offeringId") Long offeringId,
                                                  @Param("skuIds") Collection<Long> skuIds);
}
