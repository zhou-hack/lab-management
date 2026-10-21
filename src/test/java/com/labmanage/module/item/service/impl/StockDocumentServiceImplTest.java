package com.labmanage.module.item.service.impl;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.StockDocumentCreateDTO;
import com.labmanage.module.item.dto.StockDocumentLineCreateDTO;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.StockMovement;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.StockMovementMapper;
import com.labmanage.module.item.enums.MoveDirection;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 库存单据服务单元测试（纯 Mockito，不依赖数据库）
 * <p>
 * 重点覆盖过账引擎：CAS 状态迁移、移动加权均价计算、余额行初始化、缺货整笔失败、
 * 出库成本回填、流水幂等键、多行加锁顺序。
 */
@ExtendWith(MockitoExtension.class)
class StockDocumentServiceImplTest {

    private static final Instant OCCURRED = Instant.parse("2026-10-13T01:00:00.000Z");

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
    private com.labmanage.module.item.mapper.MaterialRequestMapper requestMapper;

    @Mock
    private com.labmanage.module.item.mapper.MaterialRequestLineMapper requestLineMapper;

    @Mock
    private com.labmanage.module.item.mapper.MaterialSettlementMapper settlementMapper;

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

    private StockBalance balance(long id, long locationId, long skuId, String qty, String avg) {
        StockBalance balance = new StockBalance();
        balance.setId(id);
        balance.setLocationId(locationId);
        balance.setSkuId(skuId);
        balance.setStockQty(new BigDecimal(qty));
        balance.setSafeQty(BigDecimal.ZERO);
        balance.setTargetQty(BigDecimal.ZERO);
        balance.setMovingAveragePrice(new BigDecimal(avg));
        balance.setVersion(0);
        return balance;
    }

    private StockDocumentLine line(long id, int lineNo, long skuId, Long from, Long to, String qty, String price) {
        StockDocumentLine line = new StockDocumentLine();
        line.setId(id);
        line.setLineNo(lineNo);
        line.setSkuId(skuId);
        line.setFromLocationId(from);
        line.setToLocationId(to);
        line.setQuantity(new BigDecimal(qty));
        line.setUnitPriceSnapshot(price == null ? null : new BigDecimal(price));
        return line;
    }

    private StockDocument document(MovementType type, long documentId) {
        StockDocument document = new StockDocument();
        document.setId(1L);
        document.setDocumentId(documentId);
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

    // ---------------------------------------------------------- approve/cancel

    @Test
    void approveRejectedWhenNotDraft() {
        when(documentMapper.casApprove(1L)).thenReturn(0);

        assertBizError(40900, () -> service.approve("1"));

        verify(documentMapper).casApprove(1L);
        verifyNoInteractions(lineMapper);
    }

    @Test
    void approveSucceedsFromDraft() {
        when(documentMapper.casApprove(1L)).thenReturn(1);

        assertDoesNotThrow(() -> service.approve("1"));

        verify(documentMapper).casApprove(1L);
    }

    @Test
    void cancelRejectedWhenAlreadyPosted() {
        when(documentMapper.casCancel(1L)).thenReturn(0);

        assertBizError(40900, () -> service.cancel("1"));

        verify(documentMapper).casCancel(1L);
    }

    // ------------------------------------------------------------------ create

    @Test
    void createPurchaseInSuccess() {
        StockDocumentCreateDTO dto = new StockDocumentCreateDTO(
                MovementType.PURCHASE_IN,
                OffsetDateTime.parse("2026-10-13T09:00:00.123456+08:00"),
                null, "国药集团", "10 月补货",
                List.of(new StockDocumentLineCreateDTO("1", null, "100",
                        new BigDecimal("10"), new BigDecimal("25.5"))));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        when(locationMapper.selectByIds(anyCollection())).thenReturn(List.of(location(100L)));
        doAnswer(inv -> {
            inv.getArgument(0, StockDocument.class).setId(777L);
            return 1;
        }).when(documentMapper).insert(any(StockDocument.class));

        String result = service.create(dto);

        assertEquals("777", result);
        ArgumentCaptor<StockDocument> docCaptor = ArgumentCaptor.forClass(StockDocument.class);
        verify(documentMapper).insert(docCaptor.capture());
        StockDocument saved = docCaptor.getValue();
        assertEquals(StockDocumentStatus.DRAFT, saved.getStatus());
        assertEquals(MovementType.PURCHASE_IN, saved.getMovementType());
        assertEquals("国药集团", saved.getVendorName());
        // 带偏移量入参归一化为 UTC 且截断到毫秒
        assertEquals(Instant.parse("2026-10-13T01:00:00.123Z"), saved.getOccurredAt());

        ArgumentCaptor<List<StockDocumentLine>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(lineMapper).insertBatch(linesCaptor.capture());
        StockDocumentLine savedLine = linesCaptor.getValue().get(0);
        assertEquals(1, savedLine.getLineNo());
        assertEquals(1L, savedLine.getSkuId());
        assertEquals(100L, savedLine.getToLocationId());
        assertEquals(new BigDecimal("10"), savedLine.getQuantity());
        assertEquals(new BigDecimal("25.5"), savedLine.getUnitPriceSnapshot());
        assertEquals(new BigDecimal("255.00"), savedLine.getAmountSnapshot());
        // 批量插入前须预赋雪花 ID
        org.junit.jupiter.api.Assertions.assertNotNull(savedLine.getId());
    }

    @Test
    void createRejectUnsupportedType() {
        StockDocumentCreateDTO dto = new StockDocumentCreateDTO(
                MovementType.TRANSFER, OffsetDateTime.parse("2026-10-13T09:00:00+08:00"),
                null, null, null,
                List.of(new StockDocumentLineCreateDTO("1", "100", "200", BigDecimal.ONE, null)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("本阶段仅支持"));
        verifyNoInteractions(documentMapper, lineMapper);
    }

    @Test
    void createInboundRequiresToLocation() {
        StockDocumentCreateDTO dto = new StockDocumentCreateDTO(
                MovementType.OPENING, OffsetDateTime.parse("2026-10-13T09:00:00+08:00"),
                null, null, null,
                List.of(new StockDocumentLineCreateDTO("1", null, null, BigDecimal.ONE, null)));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("必须填写入库位"));
        // 单据头先落库、明细行校验失败由事务回滚，此处校验明细行未写入
        verify(lineMapper, never()).insertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void createInboundForbidsFromLocation() {
        StockDocumentCreateDTO dto = new StockDocumentCreateDTO(
                MovementType.PURCHASE_IN, OffsetDateTime.parse("2026-10-13T09:00:00+08:00"),
                null, null, null,
                List.of(new StockDocumentLineCreateDTO("1", "99", "100", BigDecimal.ONE, null)));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("不能填写出库位"));
        // 明细行批量化后断言 insertBatch 未被调用
        verify(lineMapper, never()).insertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void createIssueRequiresFromLocation() {
        StockDocumentCreateDTO dto = new StockDocumentCreateDTO(
                MovementType.ISSUE, OffsetDateTime.parse("2026-10-13T09:00:00+08:00"),
                null, null, null,
                List.of(new StockDocumentLineCreateDTO("1", null, null, BigDecimal.ONE, null)));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("必须填写出库位"));
    }

    @Test
    void createIssueRejectsUnitPrice() {
        StockDocumentCreateDTO dto = new StockDocumentCreateDTO(
                MovementType.ISSUE, OffsetDateTime.parse("2026-10-13T09:00:00+08:00"),
                null, null, "教学领用",
                List.of(new StockDocumentLineCreateDTO("1", "100", null, BigDecimal.ONE, new BigDecimal("5.0"))));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        when(locationMapper.selectByIds(anyCollection())).thenReturn(List.of(location(100L)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("出库单据不接受单价"));
    }

    @Test
    void createRejectsDisabledSku() {
        MaterialSku disabled = sku(1L, 3);
        disabled.setEnabled(0);
        StockDocumentCreateDTO dto = new StockDocumentCreateDTO(
                MovementType.OPENING, OffsetDateTime.parse("2026-10-13T09:00:00+08:00"),
                null, null, null,
                List.of(new StockDocumentLineCreateDTO("1", null, "100", BigDecimal.ONE, null)));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(disabled));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("物料不存在或已停用"));
    }

    @Test
    void createRejectsQuantityScaleExceeded() {
        StockDocumentCreateDTO dto = new StockDocumentCreateDTO(
                MovementType.OPENING, OffsetDateTime.parse("2026-10-13T09:00:00+08:00"),
                null, null, null,
                List.of(new StockDocumentLineCreateDTO("1", null, "100", new BigDecimal("0.5"), null)));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 0)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("数量精度超过物料限制"));
    }

    // -------------------------------------------------------------------- post

    @Test
    void postRejectedWhenCasFails() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(0);

        BusinessException ex = assertBizError(40900, () -> service.post("1"));

        assertTrue(ex.getMessage().contains("单据不存在或不是已批准状态"));
        verify(documentMapper, never()).selectById(any());
        verifyNoInteractions(lineMapper, balanceMapper, movementMapper);
    }

    @Test
    void postRejectedWhenNoLines() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.OPENING, 9001L));
        when(lineMapper.selectByDocumentId(1L)).thenReturn(List.of());

        assertBizError(40000, () -> service.post("1"));
    }

    @Test
    void postRejectedWhenSkuMissing() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.OPENING, 9001L));
        when(lineMapper.selectByDocumentId(1L))
                .thenReturn(List.of(line(11, 1, 1L, null, 100L, "10", "25.5")));
        // selectByIds 默认返回空集合 → 物料缺失

        BusinessException ex = assertBizError(40400, () -> service.post("1"));

        assertTrue(ex.getMessage().contains("物料不存在，无法过账"));
        verify(balanceMapper, never()).lockByLocationAndSku(any(), any());
        verify(movementMapper, never()).insert(any(StockMovement.class));
    }

    @Test
    void postRejectedWhenQuantityScaleExceeded() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.OPENING, 9001L));
        when(lineMapper.selectByDocumentId(1L))
                .thenReturn(List.of(line(11, 1, 1L, null, 100L, "0.500", "25.5")));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 0)));

        BusinessException ex = assertBizError(40000, () -> service.post("1"));

        assertTrue(ex.getMessage().contains("数量精度超过物料限制"));
        verify(balanceMapper, never()).lockByLocationAndSku(any(), any());
    }

    @Test
    void postInboundInitializesNewBalanceAndWritesMovement() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.OPENING, 9001L));
        when(lineMapper.selectByDocumentId(1L))
                .thenReturn(List.of(line(11, 1, 1L, null, 100L, "10", "25.5")));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        // lockByLocationAndSku 默认返回 null → 走插入新余额分支
        doAnswer(inv -> {
            inv.getArgument(0, StockBalance.class).setId(500L);
            return 1;
        }).when(balanceMapper).insert(any(StockBalance.class));
        when(balanceMapper.updatePostBalance(any(), any(), any(), any())).thenReturn(1);

        service.post("1");

        ArgumentCaptor<StockBalance> insertCaptor = ArgumentCaptor.forClass(StockBalance.class);
        verify(balanceMapper).insert(insertCaptor.capture());
        StockBalance created = insertCaptor.getValue();
        assertEquals(100L, created.getLocationId());
        assertEquals(1L, created.getSkuId());
        assertEquals(0, created.getStockQty().compareTo(BigDecimal.ZERO));
        assertEquals(0, created.getMovingAveragePrice().compareTo(BigDecimal.ZERO));
        assertEquals(0, created.getVersion());

        verify(balanceMapper).updatePostBalance(500L, 0, new BigDecimal("10"), new BigDecimal("25.5000"));

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementMapper).insert(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertEquals(11L, movement.getDocumentLineId());
        assertEquals(100L, movement.getLocationId());
        assertEquals(1L, movement.getSkuId());
        assertEquals(MoveDirection.IN, movement.getDirection());
        assertEquals(0, movement.getQtyDelta().compareTo(new BigDecimal("10")));
        assertEquals(0, movement.getBeforeQty().compareTo(BigDecimal.ZERO));
        assertEquals(0, movement.getAfterQty().compareTo(new BigDecimal("10")));
        assertEquals(new BigDecimal("255.00"), movement.getValueDelta());
        assertEquals(0, movement.getBeforeAvgPrice().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("25.5000"), movement.getAfterAvgPrice());
        assertEquals(OCCURRED, movement.getOccurredAt());
        assertEquals("STK:9001:1:IN", movement.getMovementKey());
    }

    @Test
    void postInboundComputesMovingAveragePrice() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.PURCHASE_IN, 9001L));
        when(lineMapper.selectByDocumentId(1L))
                .thenReturn(List.of(line(11, 1, 1L, null, 100L, "10", "30.0000")));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        // 原库存 10 × 均价 20，入 10 × 单价 30 → 新均价 25
        when(balanceMapper.lockByLocationAndSku(100L, 1L)).thenReturn(balance(501L, 100L, 1L, "10", "20.0000"));
        when(balanceMapper.updatePostBalance(any(), any(), any(), any())).thenReturn(1);

        service.post("1");

        verify(balanceMapper).updatePostBalance(501L, 0, new BigDecimal("20"), new BigDecimal("25.0000"));

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementMapper).insert(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertEquals(0, movement.getBeforeQty().compareTo(new BigDecimal("10")));
        assertEquals(0, movement.getAfterQty().compareTo(new BigDecimal("20")));
        assertEquals(new BigDecimal("300.00"), movement.getValueDelta());
        assertEquals(new BigDecimal("20.0000"), movement.getBeforeAvgPrice());
        assertEquals(new BigDecimal("25.0000"), movement.getAfterAvgPrice());
    }

    @Test
    void postOutboundRejectedWhenInsufficientStock() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.ISSUE, 9001L));
        when(lineMapper.selectByDocumentId(1L))
                .thenReturn(List.of(line(11, 1, 1L, 100L, null, "10", null)));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        when(balanceMapper.lockByLocationAndSku(100L, 1L)).thenReturn(balance(501L, 100L, 1L, "5", "10.0000"));

        BusinessException ex = assertBizError(40900, () -> service.post("1"));

        assertTrue(ex.getMessage().contains("库存不足"));
        verify(balanceMapper, never()).updatePostBalance(any(), any(), any(), any());
        verify(movementMapper, never()).insert(any(StockMovement.class));
    }

    @Test
    void postOutboundBackfillsCostSnapshot() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.ISSUE, 9001L));
        when(lineMapper.selectByDocumentId(1L))
                .thenReturn(List.of(line(11, 1, 1L, 100L, null, "4", null)));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        when(balanceMapper.lockByLocationAndSku(100L, 1L)).thenReturn(balance(501L, 100L, 1L, "10", "25.0000"));
        when(balanceMapper.updatePostBalance(any(), any(), any(), any())).thenReturn(1);

        service.post("1");

        // 出库成本按过账时移动加权均价回填到单据行
        ArgumentCaptor<StockDocumentLine> lineCaptor = ArgumentCaptor.forClass(StockDocumentLine.class);
        verify(lineMapper).updateById(lineCaptor.capture());
        StockDocumentLine backfilled = lineCaptor.getValue();
        assertEquals(new BigDecimal("25.0000"), backfilled.getUnitPriceSnapshot());
        assertEquals(new BigDecimal("100.00"), backfilled.getAmountSnapshot());

        verify(balanceMapper).updatePostBalance(501L, 0, new BigDecimal("6"), new BigDecimal("25.0000"));

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementMapper).insert(movementCaptor.capture());
        StockMovement movement = movementCaptor.getValue();
        assertEquals(MoveDirection.OUT, movement.getDirection());
        assertEquals(0, movement.getQtyDelta().compareTo(new BigDecimal("-4")));
        assertEquals(0, movement.getBeforeQty().compareTo(new BigDecimal("10")));
        assertEquals(0, movement.getAfterQty().compareTo(new BigDecimal("6")));
        assertEquals(new BigDecimal("-100.00"), movement.getValueDelta());
        assertEquals(new BigDecimal("25.0000"), movement.getBeforeAvgPrice());
        assertEquals(new BigDecimal("25.0000"), movement.getAfterAvgPrice());
        assertEquals("STK:9001:1:OUT", movement.getMovementKey());
    }

    @Test
    void postRejectedWhenMovementKeyDuplicated() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.OPENING, 9001L));
        when(lineMapper.selectByDocumentId(1L))
                .thenReturn(List.of(line(11, 1, 1L, null, 100L, "10", "25.5")));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        when(balanceMapper.lockByLocationAndSku(100L, 1L)).thenReturn(balance(501L, 100L, 1L, "0", "0"));
        when(balanceMapper.updatePostBalance(any(), any(), any(), any())).thenReturn(1);
        when(movementMapper.insert(any(StockMovement.class)))
                .thenThrow(new DuplicateKeyException("uk_stock_movement_key"));

        BusinessException ex = assertBizError(40900, () -> service.post("1"));

        assertTrue(ex.getMessage().contains("流水已存在，禁止重复过账"));
    }

    @Test
    void postLocksBalancesInLocationThenSkuOrder() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.OPENING, 9001L));
        // 行号顺序与 (库位, SKU) 排序顺序刻意不一致，验证按库位加锁
        when(lineMapper.selectByDocumentId(1L)).thenReturn(List.of(
                line(11, 1, 2L, null, 200L, "3", "2.5"),
                line(12, 2, 1L, null, 100L, "2", "1.5")));
        when(skuMapper.selectByIds(anyCollection()))
                .thenReturn(List.of(sku(1L, 3), sku(2L, 3)));
        when(balanceMapper.lockByLocationAndSku(100L, 1L)).thenReturn(balance(601L, 100L, 1L, "5", "1.0000"));
        when(balanceMapper.lockByLocationAndSku(200L, 2L)).thenReturn(balance(602L, 200L, 2L, "0", "0"));
        when(balanceMapper.updatePostBalance(any(), any(), any(), any())).thenReturn(1);

        service.post("1");

        InOrder lockOrder = inOrder(balanceMapper);
        lockOrder.verify(balanceMapper).lockByLocationAndSku(100L, 1L);
        lockOrder.verify(balanceMapper).lockByLocationAndSku(200L, 2L);

        // 流水也按排序后的行顺序写入（先 lineNo=2）
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementMapper, org.mockito.Mockito.times(2)).insert(movementCaptor.capture());
        List<StockMovement> movements = movementCaptor.getAllValues();
        assertEquals("STK:9001:2:IN", movements.get(0).getMovementKey());
        assertEquals("STK:9001:1:IN", movements.get(1).getMovementKey());
    }

    @Test
    void postRejectedWhenDocumentMissingAfterCas() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertBizError(50000, () -> service.post("1"));

        assertTrue(ex.getMessage().contains("单据数据异常"));
        verify(lineMapper, never()).selectByDocumentId(any());
    }

    @Test
    void postRejectedWhenBalanceVersionConflicts() {
        when(documentMapper.casPost(eq(1L), any(Instant.class))).thenReturn(1);
        when(documentMapper.selectById(1L)).thenReturn(document(MovementType.OPENING, 9001L));
        when(lineMapper.selectByDocumentId(1L))
                .thenReturn(List.of(line(11, 1, 1L, null, 100L, "10", "25.5")));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L, 3)));
        when(balanceMapper.lockByLocationAndSku(100L, 1L)).thenReturn(balance(501L, 100L, 1L, "0", "0"));
        // version 条件更新 0 行 → 防御性整笔回滚，不落流水
        when(balanceMapper.updatePostBalance(any(), any(), any(), any())).thenReturn(0);

        BusinessException ex = assertBizError(50000, () -> service.post("1"));

        assertTrue(ex.getMessage().contains("库存余额更新异常"));
        verify(movementMapper, never()).insert(any(StockMovement.class));
    }
}
