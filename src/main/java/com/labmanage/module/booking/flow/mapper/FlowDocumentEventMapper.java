package com.labmanage.module.booking.flow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.booking.flow.entity.DocumentEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 单据事件 Mapper（追加型历史，不提供更新/删除；
 * 命名加 Flow 前缀避免与 module/document 同名 Mapper 的 bean 冲突）
 */
@Mapper
public interface FlowDocumentEventMapper extends BaseMapper<DocumentEvent> {

    /** 单据全量事件，按发生时间升序（详情时间线）。 */
    @Select("SELECT * FROM document_event WHERE document_id = #{documentId} AND deleted = 0 "
            + "ORDER BY occurred_at ASC, id ASC")
    List<DocumentEvent> listByDocument(@Param("documentId") Long documentId);

    /**
     * 批量查询预约的签到/清点事件（列表行展示开门、结束时间）。
     * document_no 约定为 "LAB"+lab_booking.id，由服务端剥离前缀还原预约 ID。
     */
    @Select("""
            <script>
            SELECT d.document_no AS documentNo, e.event_type AS eventType, e.occurred_at AS occurredAt
            FROM document_event e
            JOIN biz_document d ON d.id = e.document_id AND d.deleted = 0
            WHERE e.deleted = 0 AND e.event_type IN ('CHECKIN', 'CHECKOUT')
            AND d.document_type = 'LAB_BOOKING'
            AND d.document_no IN
            <foreach collection="documentNos" item="no" open="(" separator="," close=")">
                #{no}
            </foreach>
            </script>
            """)
    List<Map<String, Object>> selectCheckEvents(@Param("documentNos") Collection<String> documentNos);
}
