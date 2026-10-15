package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.StockDocumentLine;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 库存单据行 Mapper
 */
@Mapper
public interface StockDocumentLineMapper extends BaseMapper<StockDocumentLine> {

    /**
     * 按单据查询明细行（行号升序）
     */
    @Select("SELECT * FROM stock_document_line WHERE stock_document_id = #{documentId} AND deleted = 0 ORDER BY line_no")
    List<StockDocumentLine> selectByDocumentId(@Param("documentId") Long documentId);

    /**
     * 行锁读取单据行（归还过账时锁原出库行，串行化同一原行的并发归还校验）
     */
    @Select("SELECT * FROM stock_document_line WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    StockDocumentLine lockById(@Param("id") Long id);

    /**
     * 批量插入单据行（多行 VALUES 单语句；ID 须由调用方 IdWorker.getId() 预赋，
     * 审计字段 create_time/update_time/deleted 走列默认值）
     */
    @Insert("""
            <script>
            INSERT INTO stock_document_line
                (id, stock_document_id, line_no, sku_id, from_location_id, to_location_id, quantity,
                 unit_price_snapshot, amount_snapshot, request_line_id, original_issue_line_id, adjust_direction)
            VALUES
            <foreach collection='lines' item='l' separator=','>
                (#{l.id}, #{l.stockDocumentId}, #{l.lineNo}, #{l.skuId}, #{l.fromLocationId}, #{l.toLocationId},
                 #{l.quantity}, #{l.unitPriceSnapshot}, #{l.amountSnapshot}, #{l.requestLineId},
                 #{l.originalIssueLineId}, #{l.adjustDirection})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("lines") List<StockDocumentLine> lines);
}
