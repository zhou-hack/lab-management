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
                 request_line_id, original_issue_line_id)
            VALUES
            <foreach collection='lines' item='l' separator=','>
                (#{l.id}, #{l.stockDocumentId}, #{l.lineNo}, #{l.skuId}, #{l.fromLocationId}, #{l.toLocationId},
                 #{l.quantity}, #{l.requestLineId}, #{l.originalIssueLineId})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("lines") List<StockDocumentLine> lines);

    /**
     * 申领单已过账的领用出库行（归还入口的数据源）
     */
    @Select("""
            SELECT l.* FROM stock_document_line l
            JOIN stock_document d ON d.id = l.stock_document_id AND d.deleted = 0
            WHERE d.material_request_id = #{requestId}
              AND d.movement_type = 'ISSUE' AND d.status = 'POSTED' AND l.deleted = 0
            ORDER BY l.id
            """)
    List<StockDocumentLine> selectPostedIssueLinesByRequestId(@Param("requestId") Long requestId);

    /**
     * 已过账归还量按原出库行汇总（累计归还量口径，用于可归还量 = 出库量 − 已归还量）
     */
    @Select("""
            <script>
            SELECT l.original_issue_line_id AS issueLineId, COALESCE(SUM(l.quantity), 0) AS returnedQty
            FROM stock_document_line l
            JOIN stock_document d ON d.id = l.stock_document_id AND d.deleted = 0
            WHERE d.movement_type = 'RETURN_IN' AND d.status = 'POSTED' AND l.deleted = 0
              AND l.original_issue_line_id IN
            <foreach collection='issueLineIds' item='i' open='(' separator=',' close=')'>
                #{i}
            </foreach>
            GROUP BY l.original_issue_line_id
            </script>
            """)
    List<IssueLineReturned> sumPostedReturnedByIssueLineIds(@Param("issueLineIds") List<Long> issueLineIds);

    /**
     * 原出库行累计已归还量
     */
    record IssueLineReturned(Long issueLineId, java.math.BigDecimal returnedQty) {
    }
}
