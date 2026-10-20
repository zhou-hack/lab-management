package com.labmanage.module.item.service.impl;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.StocktakeCountDTO;
import com.labmanage.module.item.dto.StocktakeCreateDTO;
import com.labmanage.module.item.dto.StocktakeExportQuery;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.Stocktake;
import com.labmanage.module.item.entity.StocktakeLine;
import com.labmanage.module.item.enums.AdjustDirection;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.enums.StocktakeStatus;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.StocktakeLineMapper;
import com.labmanage.module.item.mapper.StocktakeMapper;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.vo.StocktakeVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 盘点服务单元测试（纯 Mockito：快照建单、活跃唯一、版本校验、调账生成与审批）
 */
@ExtendWith(MockitoExtension.class)
class StocktakeServiceImplTest {

    @Mock
    private StocktakeMapper stocktakeMapper;

    @Mock
    private StocktakeLineMapper stocktakeLineMapper;

    @Mock
    private StockBalanceMapper balanceMapper;

    @Mock
    private StockLocationMapper locationMapper;

    @Mock
    private MaterialSkuMapper skuMapper;

    @Mock
    private StockDocumentMapper documentMapper;

    @Mock
    private StockDocumentLineMapper documentLineMapper;

    @Mock
    private StockDocumentService documentService;

    @InjectMocks
    private StocktakeServiceImpl service;

    // ---------------------------------------------------------------- helpers

    private StockLocation location(long id) {
        StockLocation location = new StockLocation();
        location.setId(id);
        location.setLocationName("A 区药品柜");
        location.setEnabled(1);
        return location;
    }

    private Stocktake stocktake(long id, StocktakeStatus status) {
        Stocktake stocktake = new Stocktake();
        stocktake.setId(id);
        stocktake.setDocumentId(id);
        stocktake.setLocationId(100L);
        stocktake.setCountDate(LocalDate.parse("2026-10-14"));
        stocktake.setStatus(status);
        return stocktake;
    }

    private StocktakeLine stocktakeLine(long id, long skuId, String bookQty, String countedQty,
                                        Integer versionSnapshot, String reason) {
        StocktakeLine line = new StocktakeLine();
        line.setId(id);
        line.setStocktakeId(555L);
        line.setSkuId(skuId);
        line.setBookQtySnapshot(new BigDecimal(bookQty));
        line.setCountedQty(countedQty == null ? null : new BigDecimal(countedQty));
        line.setBalanceVersionSnapshot(versionSnapshot);
        line.setDifferenceReason(reason);
        return line;
    }

    private StockBalance balance(long skuId, String qty, int version) {
        StockBalance balance = new StockBalance();
        balance.setId(skuId);
        balance.setLocationId(100L);
        balance.setSkuId(skuId);
        balance.setStockQty(new BigDecimal(qty));
        balance.setMovingAveragePrice(BigDecimal.ZERO);
        balance.setVersion(version);
        return balance;
    }

    private BusinessException assertBizError(int expectedCode, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedCode, ex.getCode());
        return ex;
    }

    // ------------------------------------------------------------------ create

    // ------------------------------------------------------------- listForExport

    @Test
    void listForExportMapsVosWithLocationNames() {
        Stocktake counting = stocktake(601L, StocktakeStatus.COUNTING);
        Stocktake cancelled = stocktake(602L, StocktakeStatus.CANCELLED);
        when(stocktakeMapper.selectList(any())).thenReturn(List.of(counting, cancelled));
        StockLocation named = location(100L);
        named.setLocationName("一号实验室 A 区药品柜");
        when(locationMapper.selectByIds(anyCollection())).thenReturn(List.of(named));

        StocktakeExportQuery query = new StocktakeExportQuery();
        query.setLocationId("100");
        query.setStatus(StocktakeStatus.COUNTING);

        List<StocktakeVO> vos = service.listForExport(query);

        assertEquals(2, vos.size());
        assertEquals("601", vos.get(0).id());
        assertEquals("一号实验室 A 区药品柜", vos.get(0).locationName());
        assertEquals(StocktakeStatus.COUNTING, vos.get(0).status());
        assertEquals("602", vos.get(1).id());
        assertEquals(StocktakeStatus.CANCELLED, vos.get(1).status());
    }


    @Test
    void createSnapshotsAllBalances() {
        when(locationMapper.selectById(100L)).thenReturn(location(100L));
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance(1L, "20", 3), balance(2L, "5.5", 7)));
        doAnswer(inv -> {
            inv.getArgument(0, Stocktake.class).setId(555L);
            return 1;
        }).when(stocktakeMapper).insert(any(Stocktake.class));

        String id = service.create(new StocktakeCreateDTO("100", null, LocalDate.parse("2026-10-14")));

        assertEquals("555", id);
        ArgumentCaptor<Stocktake> docCaptor = ArgumentCaptor.forClass(Stocktake.class);
        verify(stocktakeMapper).insert(docCaptor.capture());
        assertEquals(StocktakeStatus.DRAFT, docCaptor.getValue().getStatus());

        ArgumentCaptor<List<StocktakeLine>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(stocktakeLineMapper).insertBatch(linesCaptor.capture());
        List<StocktakeLine> savedLines = linesCaptor.getValue();
        assertEquals(2, savedLines.size());
        assertEquals(new BigDecimal("20"), savedLines.get(0).getBookQtySnapshot());
        assertEquals(3, savedLines.get(0).getBalanceVersionSnapshot());
        assertEquals(new BigDecimal("5.5"), savedLines.get(1).getBookQtySnapshot());
        assertEquals(7, savedLines.get(1).getBalanceVersionSnapshot());
        // 批量插入前须预赋雪花 ID
        assertNotNull(savedLines.get(0).getId());
        assertNotNull(savedLines.get(1).getId());
    }

    @Test
    void createRejectsDisabledLocation() {
        StockLocation disabled = location(100L);
        disabled.setEnabled(0);
        when(locationMapper.selectById(100L)).thenReturn(disabled);

        BusinessException ex = assertBizError(40000,
                () -> service.create(new StocktakeCreateDTO("100", null, LocalDate.parse("2026-10-14"))));

        assertTrue(ex.getMessage().contains("库位已停用"));
    }

    // ------------------------------------------------------------------- start

    @Test
    void startRejectsWhenActiveStocktakeExists() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.DRAFT));
        when(stocktakeMapper.lockActiveByLocationId(100L)).thenReturn(stocktake(777L, StocktakeStatus.COUNTING));

        BusinessException ex = assertBizError(40900, () -> service.start("555"));

        assertTrue(ex.getMessage().contains("已有进行中的盘点单"));
        verify(stocktakeMapper, org.mockito.Mockito.never()).casStart(any(Long.class), any(Instant.class));
    }

    @Test
    void startRejectsConcurrentDuplicateAsConflict() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.DRAFT));
        when(stocktakeMapper.lockActiveByLocationId(100L)).thenReturn(null);
        when(stocktakeMapper.casStart(eq(555L), any(Instant.class)))
                .thenThrow(new DuplicateKeyException("uk_item_stocktake_active"));

        BusinessException ex = assertBizError(40900, () -> service.start("555"));

        assertTrue(ex.getMessage().contains("并发冲突"));
    }

    @Test
    void startSucceedsFromDraft() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.DRAFT));
        when(stocktakeMapper.lockActiveByLocationId(100L)).thenReturn(null);
        when(stocktakeMapper.casStart(eq(555L), any(Instant.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.start("555"));

        verify(stocktakeMapper).casStart(eq(555L), any(Instant.class));
    }

    // ------------------------------------------------------------------- count

    @Test
    void countRejectsWhenNotCounting() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.DRAFT));

        assertBizError(40900, () -> service.count("555",
                new StocktakeCountDTO(List.of(new StocktakeCountDTO.CountLineDTO("1", BigDecimal.ONE, null)))));
    }

    @Test
    void countRejectsForeignLine() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.COUNTING));
        when(stocktakeLineMapper.selectByStocktakeId(555L)).thenReturn(List.of());

        assertBizError(40400, () -> service.count("555",
                new StocktakeCountDTO(List.of(new StocktakeCountDTO.CountLineDTO("999", BigDecimal.ONE, "x")))));
    }

    // ----------------------------------------------------------- submitReview

    @Test
    void submitRejectsMissingCountedQty() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.COUNTING));
        when(stocktakeLineMapper.selectByStocktakeId(555L))
                .thenReturn(List.of(stocktakeLine(1L, 1L, "20", null, 3, null)));

        BusinessException ex = assertBizError(40000, () -> service.submitReview("555"));

        assertTrue(ex.getMessage().contains("未录入实盘量"));
    }

    @Test
    void submitRejectsDifferenceWithoutReason() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.COUNTING));
        when(stocktakeLineMapper.selectByStocktakeId(555L))
                .thenReturn(List.of(stocktakeLine(1L, 1L, "20", "18", 3, null)));

        BusinessException ex = assertBizError(40000, () -> service.submitReview("555"));

        assertTrue(ex.getMessage().contains("差异原因"));
    }

    @Test
    void submitRejectsWhenBalanceVersionChanged() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.COUNTING));
        when(stocktakeLineMapper.selectByStocktakeId(555L))
                .thenReturn(List.of(stocktakeLine(1L, 1L, "20", "20", 3, null)));
        // 建单后版本已从 3 递增到 4（盘点期间发生过库存变动）
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance(1L, "20", 4)));

        BusinessException ex = assertBizError(40900, () -> service.submitReview("555"));

        assertTrue(ex.getMessage().contains("库存已发生变动"));
    }

    @Test
    void submitGeneratesAdjustDocumentForDifferences() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.COUNTING));
        when(stocktakeLineMapper.selectByStocktakeId(555L)).thenReturn(List.of(
                stocktakeLine(1L, 1L, "20", "18", 3, "一瓶破损未登记"),
                stocktakeLine(2L, 2L, "5", "6", 7, "多出一瓶")));
        when(balanceMapper.selectList(any())).thenReturn(List.of(
                balance(1L, "20", 3), balance(2L, "5", 7)));
        doAnswer(inv -> {
            inv.getArgument(0, StockDocument.class).setId(901L);
            return 1;
        }).when(documentMapper).insert(any(StockDocument.class));
        when(stocktakeMapper.casSubmitReview(555L)).thenReturn(1);

        String adjustId = service.submitReview("555");

        assertEquals("901", adjustId);
        ArgumentCaptor<StockDocument> docCaptor = ArgumentCaptor.forClass(StockDocument.class);
        verify(documentMapper).insert(docCaptor.capture());
        assertEquals(MovementType.ADJUST, docCaptor.getValue().getMovementType());
        assertEquals(StockDocumentStatus.DRAFT, docCaptor.getValue().getStatus());

        ArgumentCaptor<List<StockDocumentLine>> adjustCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(documentLineMapper).insertBatch(adjustCaptor.capture());
        List<StockDocumentLine> adjustLines = adjustCaptor.getValue();
        assertEquals(2, adjustLines.size());
        assertEquals(AdjustDirection.LOSS.name(), adjustLines.get(0).getAdjustDirection());
        assertEquals(100L, adjustLines.get(0).getFromLocationId());
        assertEquals(new BigDecimal("2.000"), adjustLines.get(0).getQuantity());
        assertEquals(AdjustDirection.SURPLUS.name(), adjustLines.get(1).getAdjustDirection());
        assertEquals(100L, adjustLines.get(1).getToLocationId());
        assertEquals(new BigDecimal("1.000"), adjustLines.get(1).getQuantity());

        verify(stocktakeMapper).updateAdjustDocumentId(555L, 901L);
        verify(stocktakeMapper).casSubmitReview(555L);
    }

    @Test
    void submitNoDifferenceReturnsNull() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.COUNTING));
        when(stocktakeLineMapper.selectByStocktakeId(555L))
                .thenReturn(List.of(stocktakeLine(1L, 1L, "20", "20", 3, null)));
        when(balanceMapper.selectList(any())).thenReturn(List.of(balance(1L, "20", 3)));
        when(stocktakeMapper.casSubmitReview(555L)).thenReturn(1);

        String adjustId = service.submitReview("555");

        assertNull(adjustId);
        verifyNoInteractions(documentMapper, documentLineMapper);
    }

    // ----------------------------------------------- complete / approveAdjust

    @Test
    void completeRejectsWhenAdjustPending() {
        Stocktake pending = stocktake(555L, StocktakeStatus.PENDING_REVIEW);
        pending.setAdjustDocumentId(901L);
        when(stocktakeMapper.selectById(555L)).thenReturn(pending);

        BusinessException ex = assertBizError(40000, () -> service.complete("555"));

        assertTrue(ex.getMessage().contains("存在差异调账单"));
    }

    @Test
    void approveAdjustRejectsWhenNoAdjustDocument() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.PENDING_REVIEW));

        assertBizError(40000, () -> service.approveAdjust("555"));
    }

    @Test
    void approveAdjustApprovesAndPostsAdjustDocument() {
        Stocktake pending = stocktake(555L, StocktakeStatus.PENDING_REVIEW);
        pending.setAdjustDocumentId(901L);
        when(stocktakeMapper.selectById(555L)).thenReturn(pending);

        assertDoesNotThrow(() -> service.approveAdjust("555"));

        verify(documentService).approve("901");
        verify(documentService).post("901");
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void cancelRejectsAfterStart() {
        when(stocktakeMapper.selectById(555L)).thenReturn(stocktake(555L, StocktakeStatus.COUNTING));
        when(stocktakeMapper.casCancel(555L)).thenReturn(0);

        assertBizError(40900, () -> service.cancel("555"));
    }

    @Test
    void getFillsSkuAndLocationNames() {
        Stocktake counting = stocktake(555L, StocktakeStatus.COUNTING);
        when(stocktakeMapper.selectById(555L)).thenReturn(counting);
        when(locationMapper.selectById(100L)).thenReturn(location(100L));
        when(stocktakeLineMapper.selectByStocktakeId(555L))
                .thenReturn(List.of(stocktakeLine(1L, 1L, "20", "18", 3, "一瓶破损")));
        com.labmanage.module.item.entity.MaterialSku sku = new com.labmanage.module.item.entity.MaterialSku();
        sku.setId(1L);
        sku.setSkuCode("SKU-ETHANOL-500ML");
        sku.setMaterialName("无水乙醇 500mL");
        sku.setBaseUnit("瓶");
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku));

        var detail = service.get("555");

        assertEquals("A 区药品柜", detail.locationName());
        assertEquals("SKU-ETHANOL-500ML", detail.lines().get(0).skuCode());
        assertEquals(new BigDecimal("-2"), detail.lines().get(0).differenceQty());
    }
}
