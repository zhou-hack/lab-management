package com.labmanage.module.item.service.impl;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.MaterialSettlementCreateDTO;
import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.SettlementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 物料结算服务单元测试（纯 Mockito：核销类型、结算量校验）
 */
@ExtendWith(MockitoExtension.class)
class MaterialSettlementServiceImplTest {

    @Mock
    private MaterialSettlementMapper settlementMapper;

    @Mock
    private StockDocumentLineMapper documentLineMapper;

    @Mock
    private StockDocumentMapper documentMapper;

    @InjectMocks
    private MaterialSettlementServiceImpl service;

    // ---------------------------------------------------------------- helpers

    private BusinessException assertBizError(int expectedCode, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedCode, ex.getCode());
        return ex;
    }

    private StockDocumentLine issueLine(long id, long documentId, String qty) {
        StockDocumentLine line = new StockDocumentLine();
        line.setId(id);
        line.setStockDocumentId(documentId);
        line.setSkuId(1L);
        line.setQuantity(new BigDecimal(qty));
        return line;
    }

    private StockDocument postedIssue(long id) {
        StockDocument document = new StockDocument();
        document.setId(id);
        document.setMovementType(MovementType.ISSUE);
        document.setStatus(StockDocumentStatus.POSTED);
        return document;
    }

    // ------------------------------------------------------------------ settle

    @Test
    void settleRejectsReturnedType() {
        BusinessException ex = assertBizError(40000, () -> service.settle(
                new MaterialSettlementCreateDTO("21", SettlementType.RETURNED, BigDecimal.ONE, null)));

        assertTrue(ex.getMessage().contains("归还单过账自动生成"));
        verifyNoInteractions(settlementMapper, documentLineMapper);
    }

    @Test
    void settleRejectsUnknownLine() {
        when(documentLineMapper.lockById(21L)).thenReturn(null);

        assertBizError(40400, () -> service.settle(
                new MaterialSettlementCreateDTO("21", SettlementType.CONSUMED, BigDecimal.ONE, null)));
    }

    @Test
    void settleRejectsNonPostedIssueDocument() {
        when(documentLineMapper.lockById(21L)).thenReturn(issueLine(21L, 66L, "10"));
        StockDocument draft = postedIssue(66L);
        draft.setStatus(StockDocumentStatus.DRAFT);
        when(documentMapper.selectById(66L)).thenReturn(draft);

        BusinessException ex = assertBizError(40000, () -> service.settle(
                new MaterialSettlementCreateDTO("21", SettlementType.CONSUMED, BigDecimal.ONE, null)));

        assertTrue(ex.getMessage().contains("不属于已过账的领用出库单"));
        verify(settlementMapper, never()).insert(any(MaterialSettlement.class));
    }

    @Test
    void settleRejectsQtyOverOriginalIssue() {
        when(documentLineMapper.lockById(21L)).thenReturn(issueLine(21L, 66L, "10"));
        when(documentMapper.selectById(66L)).thenReturn(postedIssue(66L));
        when(settlementMapper.sumSettledQty(21L)).thenReturn(new BigDecimal("8"));

        BusinessException ex = assertBizError(40900, () -> service.settle(
                new MaterialSettlementCreateDTO("21", SettlementType.CONSUMED, new BigDecimal("3"), null)));

        assertTrue(ex.getMessage().contains("超过原出库量"));
        verify(settlementMapper, never()).insert(any(MaterialSettlement.class));
    }

    @Test
    void settleInsertsRecord() {
        when(documentLineMapper.lockById(21L)).thenReturn(issueLine(21L, 66L, "10"));
        when(documentMapper.selectById(66L)).thenReturn(postedIssue(66L));
        when(settlementMapper.sumSettledQty(21L)).thenReturn(new BigDecimal("8"));
        doAnswer(inv -> {
            inv.getArgument(0, MaterialSettlement.class).setId(888L);
            return 1;
        }).when(settlementMapper).insert(any(MaterialSettlement.class));

        String id = service.settle(new MaterialSettlementCreateDTO(
                "21", SettlementType.CONSUMED, new BigDecimal("2"), "实验消耗完毕"));

        assertEquals("888", id);
        ArgumentCaptor<MaterialSettlement> captor = ArgumentCaptor.forClass(MaterialSettlement.class);
        verify(settlementMapper).insert(captor.capture());
        MaterialSettlement saved = captor.getValue();
        assertEquals(SettlementType.CONSUMED, saved.getSettlementType());
        assertEquals(21L, saved.getIssueLineId());
        assertEquals(new BigDecimal("2"), saved.getQuantity());
        assertEquals("实验消耗完毕", saved.getReason());
    }

    // ------------------------------------------------------------------- page

    @Test
    void pageMapsRowsWithMovementDetails() {
        when(settlementMapper.selectSettlementPage(any(), org.mockito.ArgumentMatchers.isNull()))
                .thenAnswer(inv -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination
                            .Page<MaterialSettlementMapper.SettlementRow> page =
                            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
                    page.setRecords(List.of(new MaterialSettlementMapper.SettlementRow(
                            888L, 21L, "CONSUMED", new BigDecimal("2"), null,
                            java.time.Instant.parse("2026-10-23T02:00:00.000Z"), null, 999L,
                            "实验消耗完毕", 777L, "PE-CAP-470UF50V", "电解电容 470μF/50V", "个",
                            "一号实验室 A 区元件柜",
                            java.time.Instant.parse("2026-10-23T01:00:00.000Z"), "电子电工实训")));
                    page.setTotal(1L);
                    return page;
                });

        var result = service.page(new com.labmanage.module.item.dto.MaterialSettlementQuery());

        assertEquals(1, result.records().size());
        var vo = result.records().get(0);
        assertEquals("888", vo.id());
        assertEquals("21", vo.issueLineId());
        assertEquals(SettlementType.CONSUMED, vo.settlementType());
        assertEquals(new BigDecimal("2"), vo.quantity());
        // 关联出库流水详情（issue_line_id → stock_movement.document_line_id）
        assertEquals("777", vo.movementId());
        assertEquals("PE-CAP-470UF50V", vo.skuCode());
        assertEquals("电解电容 470μF/50V", vo.materialName());
        assertEquals("一号实验室 A 区元件柜", vo.locationName());
        assertEquals("电子电工实训", vo.courseName());
    }

    // ---------------------------------------------------------- issueOptions

    @Test
    void issueOptionsMapsRowsWithRemainQty() {
        when(settlementMapper.selectIssueOptionPage(any(), org.mockito.ArgumentMatchers.eq("")))
                .thenAnswer(inv -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination
                            .Page<MaterialSettlementMapper.IssueOptionRow> page =
                            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 50);
                    page.setRecords(List.of(new MaterialSettlementMapper.IssueOptionRow(
                            21L, 1L, "PE-DIO-1N4007", "整流二极管 1N4007", "个",
                            new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("2"),
                            java.time.Instant.parse("2026-10-23T01:00:00.000Z"), 300L,
                            "电力电子技术", 777L, "一号实验室 A 区元件柜",
                            null, "张老师", "电力电子实验课耗材")));
                    page.setTotal(1L);
                    return page;
                });

        var result = service.issueOptions(new com.labmanage.module.item.dto.SettlementIssueOptionQuery());

        assertEquals(1, result.records().size());
        var vo = result.records().get(0);
        assertEquals("21", vo.issueLineId());
        assertEquals("1", vo.skuId());
        assertEquals("PE-DIO-1N4007", vo.skuCode());
        assertEquals("整流二极管 1N4007", vo.materialName());
        assertEquals(new BigDecimal("10"), vo.quantity());
        assertEquals(new BigDecimal("8"), vo.settledQty());
        assertEquals(new BigDecimal("2"), vo.remainQty());
        assertEquals("300", vo.materialRequestId());
        // 课程名称（出库单 → 物料预约 → 场地预约链路解析）
        assertEquals("电力电子技术", vo.courseName());
        // 关联流水与出库详情
        assertEquals("777", vo.movementId());
        assertEquals("一号实验室 A 区元件柜", vo.locationName());
        assertEquals("张老师", vo.operatorName());
        assertEquals("电力电子实验课耗材", vo.requestPurpose());
    }
}
