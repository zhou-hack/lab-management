package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.labmanage.module.item.entity.MaterialSettlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 物料结算 Mapper
 */
@Mapper
public interface MaterialSettlementMapper extends BaseMapper<MaterialSettlement> {

    /**
     * 结算记录行（核销列表：关联出库流水补物料/库位/出库时间/课程等详情）
     */
    record SettlementRow(Long id, Long issueLineId, String settlementType, BigDecimal quantity,
                         Long operatorId, Instant settledAt, Long returnLineId, Long documentId,
                         String reason, Long movementId, String skuCode, String materialName,
                         String baseUnit, String locationName, Instant occurredAt, String courseName) {
    }

    /**
     * 结算记录分页（经 issue_line_id 关联出库流水 stock_movement，含物料/库位/课程）
     */
    @Select("""
            <script>
            SELECT ms.id, ms.issue_line_id, ms.settlement_type, ms.quantity, ms.operator_id,
                   ms.settled_at, ms.return_line_id, ms.document_id, ms.reason,
                   sm.id AS movement_id, s.sku_code, s.material_name, s.base_unit,
                   loc.location_name, sm.occurred_at, lb.course_name
              FROM material_settlement ms
              LEFT JOIN stock_movement sm ON sm.document_line_id = ms.issue_line_id AND sm.deleted = 0
              LEFT JOIN material_sku s ON s.id = sm.sku_id AND s.deleted = 0
              LEFT JOIN stock_location loc ON loc.id = sm.location_id AND loc.deleted = 0
              LEFT JOIN stock_document_line dl ON dl.id = ms.issue_line_id AND dl.deleted = 0
              LEFT JOIN stock_document d ON d.id = dl.stock_document_id AND d.deleted = 0
              LEFT JOIN material_request mr ON mr.id = d.material_request_id AND mr.deleted = 0
              LEFT JOIN lab_booking lb ON lb.id = mr.lab_booking_id AND lb.deleted = 0
             WHERE ms.deleted = 0
             <if test="issueLineId != null">
               AND ms.issue_line_id = #{issueLineId}
             </if>
             ORDER BY ms.id DESC
            </script>
            """)
    IPage<SettlementRow> selectSettlementPage(IPage<SettlementRow> page,
                                              @Param("issueLineId") Long issueLineId);

    /**
     * 可核销出库行选项行（核销登记下拉：已过账领用出库单的行，含已结算/剩余可核销量与课程名称）
     */
    record IssueOptionRow(Long issueLineId, Long skuId, String skuCode, String materialName,
                          String baseUnit, BigDecimal quantity, BigDecimal settledQty,
                          BigDecimal remainQty, Instant occurredAt, Long materialRequestId,
                          String courseName, Long movementId, String locationName,
                          String regOperatorName, String operatorName, String requestPurpose) {
    }

    /**
     * 可核销出库行分页（keyword 按物料编码/名称/课程名称过滤；与库存流水的出库一一对应）
     */
    @Select("""
            <script>
            SELECT l.id AS issue_line_id, l.sku_id, s.sku_code, s.material_name, s.base_unit,
                   l.quantity AS quantity, COALESCE(st.settled_qty, 0) AS settled_qty,
                   (l.quantity - COALESCE(st.settled_qty, 0)) AS remain_qty,
                   d.occurred_at AS occurred_at, d.material_request_id, lb.course_name,
                   sm.id AS movement_id, loc.location_name, sm.operator_name AS reg_operator_name,
                   u.real_name AS operator_name, mr.purpose AS request_purpose
              FROM stock_document_line l
              JOIN stock_document d ON d.id = l.stock_document_id AND d.deleted = 0
              JOIN material_sku s ON s.id = l.sku_id AND s.deleted = 0
              LEFT JOIN stock_movement sm ON sm.document_line_id = l.id AND sm.deleted = 0
              LEFT JOIN stock_location loc ON loc.id = sm.location_id AND loc.deleted = 0
              LEFT JOIN sys_user u ON u.id = d.operator_id AND u.deleted = 0
              LEFT JOIN material_request mr ON mr.id = d.material_request_id AND mr.deleted = 0
              LEFT JOIN lab_booking lb ON lb.id = mr.lab_booking_id AND lb.deleted = 0
              LEFT JOIN (SELECT issue_line_id, SUM(quantity) AS settled_qty
                           FROM material_settlement WHERE deleted = 0 GROUP BY issue_line_id) st
                     ON st.issue_line_id = l.id
             WHERE l.deleted = 0 AND d.movement_type = 'ISSUE' AND d.status = 'POSTED'
             <if test="keyword != null and keyword != ''">
               AND (s.sku_code LIKE CONCAT('%', #{keyword}, '%')
                    OR s.material_name LIKE CONCAT('%', #{keyword}, '%')
                    OR lb.course_name LIKE CONCAT('%', #{keyword}, '%'))
             </if>
             ORDER BY d.occurred_at DESC, l.id DESC
            </script>
            """)
    IPage<IssueOptionRow> selectIssueOptionPage(IPage<IssueOptionRow> page,
                                                @Param("keyword") String keyword);

    /**
     * 原出库行累计结算量（归还/核销前校验不超过原出库量）
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM material_settlement "
            + "WHERE issue_line_id = #{issueLineId} AND deleted = 0")
    BigDecimal sumSettledQty(@Param("issueLineId") Long issueLineId);

    /**
     * 已有结算记录的原出库行 ID 集合（冲销依赖检查批量加载：已归还/核销的出库行禁止冲销）
     */
    @Select("""
            <script>
            SELECT DISTINCT issue_line_id FROM material_settlement WHERE deleted = 0
              AND issue_line_id IN
            <foreach collection='issueLineIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>
            </script>
            """)
    List<Long> selectSettledIssueLineIds(@Param("issueLineIds") Collection<Long> issueLineIds);
}
