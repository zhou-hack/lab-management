package com.labmanage.module.item.service.impl;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.StockReversalCreateDTO;
import com.labmanage.module.item.dto.StockReturnCreateDTO;
import com.labmanage.module.item.dto.StockReturnLineDTO;
import com.labmanage.module.item.dto.StockTransferCreateDTO;
import com.labmanage.module.item.dto.StockTransferLineDTO;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.mapper.MaterialRequestLineMapper;
import com.labmanage.module.item.mapper.MaterialRequestMapper;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.StockMovementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 库存单据服务第二阶段单元测试（调拨/归还/报废/冲销登记，纯 Mockito）
 */
@ExtendWith(MockitoExtension.class)
class StockDocumentServiceImplPhase2Test {

    private static final Instant OCCURRED = Instant.parse("2026-10-14T01:00:00.000Z");

    @Mock
    private StockDocumentMapper documentMapper;

    @Mock
    private StockDocumentLineMapper lineMapper;

    @Mock
    private StockBalanceMapper balanceMapper;

    @Mock
    private MaterialSkuMapper skuMapper;

    @Mock
    private StockLocationMapper locationMapper;

    @Mock
    private StockMovementMapper movementMapper;

    @Mock
    private MaterialRequestMapper requestMapper;

    @Mock
    private MaterialRequestLineMapper requestLineMapper;

    @Mock
    private MaterialSettlementMapper settlementMapper;

    @Mock
    private com.labmanage.module.item.mapper.SysUserRefMapper sysUserRefMapper;

    @InjectMocks
    private StockDocumentServiceImpl service;

    // ---------------------------------------------------------------- helpers

    private MaterialSku sku(long id, int scale) {
        MaterialSku sku = new MaterialSku();
        sku.setId(id);
        sku.setMaterialName("无水乙醇");
        sku.setBaseUnit("瓶");
        sku.setQuantityScale(scale);
        sku.setEnabled(1);
        return sku;
    }

    private StockLocation location(long id) {
        StockLocation location = new StockLocation();
        location.setId(id);
        location.setLocationName("A 区药品柜");
        location.setEnabled(1);
        return location;
    }

    private StockBalance balance(long locationId, long skuId, String qty) {
        StockBalance balance = new StockBalance();
        balance.setId(locationId * 1000 + skuId);
        balance.setLocationId(locationId);
        balance.setSkuId(skuId);
        balance.setStockQty(new BigDecimal(qty));
        balance.setSafeQty(BigDecimal.ZERO);
        balance.setTargetQty(BigDecimal.ZERO);
        balance.setVersion(0);
        return balance;
    }

    private StockDocumentLine line(int lineNo, long skuId, Long from, Long to, String qty) {
        StockDocumentLine line = new StockDocumentLine();
        line.setId((long) lineNo);
        line.setLineNo(lineNo);
        line.setSkuId(skuId);
        line.setFromLocationId(from);
        line.setToLocationId(to);
        line.setQuantity(new BigDecimal(qty));
        return line;
    }

    private StockDocument document(MovementType type, long id) {
        StockDocument document = new StockDocument();
        document.setId(id);
        document.setDocumentId(id);
        document.setMovementType(type);
        document.setStatus(StockDocumentStatus.APPROVED);
        document.setOccurredAt(OCCURRED);
        return document;
    }

    private BusinessException assertBizError(int expectedCode, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedCode, ex.getCode());
        return ex;
    }

    private void stubInsertWithId(long id) {
        doAnswer(inv -> {
            inv.getArgument(0, StockDocument.class).setId(id);
            return 1;
        }).when(documentMapper).insert(any(StockDocument.class));
    }

    // ------------------------------------------------------------- 调拨登记

    @Test
    void createTransferRejectsSameLocation() {
        stubInsertWithId(901L);
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        when(locationMapper.selectByIds(anyCollection())).thenReturn(List.of(location(100L)));
        StockTransferCreateDTO dto = new StockTransferCreateDTO(
                OffsetDateTime.parse("2026-10-14T09:00:00+08:00"), null, "搬迁调拨",
                List.of(new StockTransferLineDTO("1", "100", "100", new BigDecimal("5"))));

        BusinessException ex = assertBizError(40000, () -> service.createTransfer(dto));

        assertTrue(ex.getMessage().contains("调拨双边库位不能相同"));
        verify(lineMapper, never()).insertBatch(anyList());
    }

    @Test
    void createTransferRejectsDisabledToLocation() {
        stubInsertWithId(901L);
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        StockLocation disabled = location(200L);
        disabled.setEnabled(0);
        when(locationMapper.selectByIds(anyCollection())).thenReturn(List.of(location(100L), disabled));
        StockTransferCreateDTO dto = new StockTransferCreateDTO(
                OffsetDateTime.parse("2026-10-14T09:00:00+08:00"), null, "搬迁调拨",
                List.of(new StockTransferLineDTO("1", "100", "200", new BigDecimal("5"))));

        BusinessException ex = assertBizError(40000, () -> service.createTransfer(dto));

        assertTrue(ex.getMessage().contains("调入库位不存在或已停用"));
        verify(lineMapper, never()).insertBatch(anyList());
    }

    // --------------------------------------------------------- 调拨过账

    @Test
    void postTransferShortageRejected() {
        when(documentMapper.casPost(eq(901L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(901L)).thenReturn(document(MovementType.TRANSFER, 901L));
        when(lineMapper.selectByDocumentId(901L)).thenReturn(List.of(line(1, 1L, 100L, 200L, "5")));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        // 调出库位余额不足；调入库位无行则初始化 0 量行
        when(balanceMapper.lockByLocationAndSku(100L, 1L)).thenReturn(balance(100L, 1L, "2"));
        when(balanceMapper.lockByLocationAndSku(200L, 1L)).thenReturn(null);
        doAnswer(inv -> 1).when(balanceMapper).insert(any(StockBalance.class));

        BusinessException ex = assertBizError(40900, () -> service.post("901"));

        assertTrue(ex.getMessage().contains("调出库位库存不足"));
        verifyNoInteractions(movementMapper);
    }

    // ------------------------------------------------------------- 归还登记

    @Test
    void createReturnRejectsNonPostedIssueLine() {
        stubInsertWithId(902L);
        // 行 ID 必须与归还行引用的原出库行 ID 一致（批量加载按实体 ID 建映射）
        StockDocumentLine issueLine = line(21, 1L, 100L, null, "5");
        issueLine.setStockDocumentId(66L);
        when(lineMapper.selectByIds(anyCollection())).thenReturn(List.of(issueLine));
        StockDocument issueDraft = document(MovementType.ISSUE, 66L);
        issueDraft.setStatus(StockDocumentStatus.DRAFT);
        when(documentMapper.selectByIds(anyCollection())).thenReturn(List.of(issueDraft));
        StockReturnCreateDTO dto = new StockReturnCreateDTO(
                OffsetDateTime.parse("2026-10-14T10:00:00+08:00"), null, "课后归还",
                List.of(new StockReturnLineDTO("21", null, new BigDecimal("2"))));

        BusinessException ex = assertBizError(40000, () -> service.createReturn(dto));

        assertTrue(ex.getMessage().contains("不属于已过账的领用出库单"));
        verify(lineMapper, never()).insertBatch(anyList());
    }

    // ------------------------------------------------------------- 冲销登记

    @Test
    void createReversalRejectsUnpostedOriginal() {
        StockDocument original = document(MovementType.PURCHASE_IN, 66L);
        original.setStatus(StockDocumentStatus.DRAFT);
        when(documentMapper.selectById(66L)).thenReturn(original);

        BusinessException ex = assertBizError(40900,
                () -> service.createReversal(new StockReversalCreateDTO("66", "错账冲销")));

        assertTrue(ex.getMessage().contains("仅允许冲销已过账单据"));
    }

    @Test
    void createReversalRejectsNonReversableType() {
        StockDocument original = document(MovementType.TRANSFER, 66L);
        original.setStatus(StockDocumentStatus.POSTED);
        when(documentMapper.selectById(66L)).thenReturn(original);

        BusinessException ex = assertBizError(40000,
                () -> service.createReversal(new StockReversalCreateDTO("66", "错账冲销")));

        assertTrue(ex.getMessage().contains("不允许冲销"));
    }

    @Test
    void createReversalCopiesIssueLinesWithDirectionFlipped() {
        StockDocument original = document(MovementType.ISSUE, 66L);
        original.setStatus(StockDocumentStatus.POSTED);
        when(documentMapper.selectById(66L)).thenReturn(original);
        when(documentMapper.selectCount(any())).thenReturn(0L);
        StockDocumentLine issueLine = line(1, 1L, 100L, null, "5");
        issueLine.setStockDocumentId(66L);
        when(lineMapper.selectByDocumentId(66L)).thenReturn(List.of(issueLine));
        when(settlementMapper.selectSettledIssueLineIds(anyCollection())).thenReturn(List.of());
        stubInsertWithId(903L);

        String result = service.createReversal(new StockReversalCreateDTO("66", "错账冲销"));

        assertEquals("903", result);
        ArgumentCaptor<StockDocument> docCaptor = ArgumentCaptor.forClass(StockDocument.class);
        verify(documentMapper).insert(docCaptor.capture());
        assertEquals(MovementType.REVERSAL, docCaptor.getValue().getMovementType());
        assertEquals(66L, docCaptor.getValue().getReversalOfId());

        ArgumentCaptor<List<StockDocumentLine>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(lineMapper).insertBatch(linesCaptor.capture());
        StockDocumentLine reversalLine = linesCaptor.getValue().get(0);
        // 原出库行 → 冲销入库行：库位对调到原出库位
        assertEquals(100L, reversalLine.getToLocationId());
        assertEquals(new BigDecimal("5"), reversalLine.getQuantity());
        // 批量插入前须预赋雪花 ID
        assertDoesNotThrow(() -> java.util.Objects.requireNonNull(reversalLine.getId()));
    }
}
