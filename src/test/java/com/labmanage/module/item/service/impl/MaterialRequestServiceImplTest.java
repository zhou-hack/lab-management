package com.labmanage.module.item.service.impl;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestLineDTO;
import com.labmanage.module.item.entity.MaterialQuota;
import com.labmanage.module.item.entity.MaterialRequest;
import com.labmanage.module.item.entity.MaterialRequestLine;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockDocument;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.MaterialRequestStatus;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.mapper.MaterialQuotaMapper;
import com.labmanage.module.item.mapper.MaterialRequestLineMapper;
import com.labmanage.module.item.mapper.MaterialRequestMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 物料申领服务单元测试（纯 Mockito：定额快照、审批校验、实发出库单生成）
 */
@ExtendWith(MockitoExtension.class)
class MaterialRequestServiceImplTest {

    @Mock
    private MaterialRequestMapper requestMapper;

    @Mock
    private MaterialRequestLineMapper requestLineMapper;

    @Mock
    private MaterialQuotaMapper quotaMapper;

    @Mock
    private MaterialSkuMapper skuMapper;

    @Mock
    private StockLocationMapper locationMapper;

    @Mock
    private StockDocumentMapper documentMapper;

    @Mock
    private StockDocumentLineMapper documentLineMapper;

    @InjectMocks
    private MaterialRequestServiceImpl service;

    // ---------------------------------------------------------------- helpers

    private MaterialSku sku(long id) {
        MaterialSku sku = new MaterialSku();
        sku.setId(id);
        sku.setSkuCode("SKU-ETHANOL-500ML");
        sku.setMaterialName("无水乙醇");
        sku.setBaseUnit("瓶");
        sku.setQuantityScale(3);
        sku.setEnabled(1);
        return sku;
    }

    private MaterialRequest request(long id, MaterialRequestStatus status) {
        MaterialRequest request = new MaterialRequest();
        request.setId(id);
        request.setDocumentId(id);
        request.setOwnerTeacherId(9L);
        request.setOperatorId(8L);
        request.setStatus(status);
        return request;
    }

    private MaterialRequestLine requestLine(long id, long skuId, String requested,
                                            String approved, String fulfilled) {
        MaterialRequestLine line = new MaterialRequestLine();
        line.setId(id);
        line.setRequestId(555L);
        line.setSkuId(skuId);
        line.setRequestedQty(new BigDecimal(requested));
        line.setApprovedQty(approved == null ? null : new BigDecimal(approved));
        line.setFulfilledQty(fulfilled == null ? BigDecimal.ZERO : new BigDecimal(fulfilled));
        line.setQuotaQtySnapshot(BigDecimal.ZERO);
        line.setRequiresExtraApproval(0);
        line.setRequiresDualSign(0);
        return line;
    }

    private BusinessException assertBizError(int expectedCode, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedCode, ex.getCode());
        return ex;
    }

    // ------------------------------------------------------------------ create

    @Test
    void createRejectsBookingAndOccurrenceTogether() {
        MaterialRequestCreateDTO dto = new MaterialRequestCreateDTO(
                null, null, null, null, null, "300", "400",
                30, 4, "实验课", List.of(new MaterialRequestLineDTO("1", BigDecimal.TEN)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("不能同时填写"));
        verifyNoInteractions(requestMapper);
    }

    @Test
    void createRejectsDuplicateSkuLines() {
        MaterialRequestCreateDTO dto = new MaterialRequestCreateDTO(
                null, null, null, null, null, null, null,
                30, 4, "实验课", List.of(
                new MaterialRequestLineDTO("1", BigDecimal.TEN),
                new MaterialRequestLineDTO("1", BigDecimal.ONE)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("物料重复"));
        verifyNoInteractions(requestMapper);
    }

    @Test
    void createComputesQuotaSnapshotAndFlags() {
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));
        MaterialQuota quota = new MaterialQuota();
        quota.setOfferingId(66L);
        quota.setSkuId(1L);
        quota.setQtyPerStudentPeriod(new BigDecimal("0.05"));
        quota.setEnabled(1);
        when(quotaMapper.selectByOfferingAndSkuIds(eq(66L), anyCollection())).thenReturn(List.of(quota));
        doAnswer(inv -> {
            inv.getArgument(0, MaterialRequest.class).setId(555L);
            return 1;
        }).when(requestMapper).insert(any(MaterialRequest.class));
        MaterialSku hazardous = sku(1L);
        hazardous.setIsHazardous(1);

        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(hazardous));
        MaterialRequestCreateDTO dto = new MaterialRequestCreateDTO(
                null, "9", null, null, "66", null, null,
                30, 4, "有机化学实验", List.of(new MaterialRequestLineDTO("1", BigDecimal.TEN)));

        String id = service.create(dto);

        assertEquals("555", id);
        ArgumentCaptor<List<MaterialRequestLine>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(requestLineMapper).insertBatch(linesCaptor.capture());
        MaterialRequestLine saved = linesCaptor.getValue().get(0);
        // 定额快照 = 30 人 × 4 课时 × 0.05 = 6.000；申请 10 超定额 → 需签批；危险物料 → 需双签
        assertEquals(new BigDecimal("6.000"), saved.getQuotaQtySnapshot());
        assertEquals(1, saved.getRequiresExtraApproval());
        assertEquals(1, saved.getRequiresDualSign());
        // 批量插入前须预赋雪花 ID
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getId());
    }

    // ----------------------------------------------------------------- approve

    @Test
    void approveRejectsWhenNotDraft() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.APPROVED));

        assertBizError(40900, () -> service.approve("555", null));

        verify(requestMapper, never()).casApprove(any(Long.class));
    }

    @Test
    void approveRejectsQtyOverRequest() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.DRAFT));
        when(requestLineMapper.selectByRequestId(555L))
                .thenReturn(List.of(requestLine(1L, 1L, "10", null, null)));

        MaterialRequestApproveDTO dto = new MaterialRequestApproveDTO(
                List.of(new MaterialRequestApproveDTO.ApproveLineDTO("1", new BigDecimal("11"))));

        BusinessException ex = assertBizError(40000, () -> service.approve("555", dto));

        assertTrue(ex.getMessage().contains("批准量不能超过申请量"));
    }

    @Test
    void approveDefaultsUnspecifiedLinesToRequested() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.DRAFT));
        when(requestLineMapper.selectByRequestId(555L)).thenReturn(List.of(
                requestLine(1L, 1L, "10", null, null),
                requestLine(2L, 2L, "5", null, null)));
        when(requestMapper.casApprove(555L)).thenReturn(1);

        MaterialRequestApproveDTO dto = new MaterialRequestApproveDTO(
                List.of(new MaterialRequestApproveDTO.ApproveLineDTO("1", new BigDecimal("8"))));

        assertDoesNotThrow(() -> service.approve("555", dto));

        ArgumentCaptor<MaterialRequestLine> lineCaptor = ArgumentCaptor.forClass(MaterialRequestLine.class);
        verify(requestLineMapper, org.mockito.Mockito.times(2)).updateById(lineCaptor.capture());
        List<MaterialRequestLine> updated = lineCaptor.getAllValues();
        assertEquals(new BigDecimal("8"), updated.get(0).getApprovedQty());
        assertEquals(new BigDecimal("5"), updated.get(1).getApprovedQty());
        verify(requestMapper).casApprove(555L);
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void cancelRejectsWhenFulfilled() {
        when(requestMapper.selectById(555L))
                .thenReturn(request(555L, MaterialRequestStatus.PARTIALLY_FULFILLED));

        BusinessException ex = assertBizError(40900, () -> service.cancel("555"));

        assertTrue(ex.getMessage().contains("实发量"));
        verify(requestMapper, never()).casCancel(any(Long.class));
    }

    // ------------------------------------------------------------------- issue

    @Test
    void issueRejectsWhenNotApproved() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.DRAFT));

        assertBizError(40900, () -> service.issue("555", new MaterialRequestIssueDTO(
                OffsetDateTime.parse("2026-10-14T09:00:00+08:00"), "100", null, null)));
    }

    @Test
    void issueRejectsQtyOverRemaining() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.APPROVED));
        when(requestLineMapper.selectByRequestId(555L))
                .thenReturn(List.of(requestLine(1L, 1L, "10", "10", "4")));

        MaterialRequestIssueDTO dto = new MaterialRequestIssueDTO(
                OffsetDateTime.parse("2026-10-14T09:00:00+08:00"), "100", null,
                List.of(new MaterialRequestIssueDTO.IssueLineDTO("1", new BigDecimal("7"), null)));

        BusinessException ex = assertBizError(40000, () -> service.issue("555", dto));

        assertTrue(ex.getMessage().contains("超过剩余批准量"));
    }

    @Test
    void issueGeneratesLinkedIssueDocument() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.APPROVED));
        when(requestLineMapper.selectByRequestId(555L))
                .thenReturn(List.of(requestLine(1L, 1L, "10", "10", "4")));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));
        StockLocation location = new StockLocation();
        location.setId(100L);
        location.setEnabled(1);
        when(locationMapper.selectByIds(anyCollection())).thenReturn(List.of(location));
        doAnswer(inv -> {
            inv.getArgument(0, StockDocument.class).setId(901L);
            return 1;
        }).when(documentMapper).insert(any(StockDocument.class));

        MaterialRequestIssueDTO dto = new MaterialRequestIssueDTO(
                OffsetDateTime.parse("2026-10-14T09:00:00+08:00"), "100", "实验课发放", null);

        String issueDocumentId = service.issue("555", dto);

        assertEquals("901", issueDocumentId);
        ArgumentCaptor<StockDocument> docCaptor = ArgumentCaptor.forClass(StockDocument.class);
        verify(documentMapper).insert(docCaptor.capture());
        StockDocument savedDoc = docCaptor.getValue();
        assertEquals(MovementType.ISSUE, savedDoc.getMovementType());
        assertEquals(StockDocumentStatus.DRAFT, savedDoc.getStatus());
        assertEquals(555L, savedDoc.getMaterialRequestId());
        assertEquals(9L, savedDoc.getOwnerTeacherId());

        ArgumentCaptor<List<StockDocumentLine>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(documentLineMapper).insertBatch(linesCaptor.capture());
        StockDocumentLine savedLine = linesCaptor.getValue().get(0);
        // 默认整单剩余量发放：批准 10 - 已实发 4 = 6
        assertEquals(1L, savedLine.getRequestLineId());
        assertEquals(100L, savedLine.getFromLocationId());
        assertEquals(new BigDecimal("6"), savedLine.getQuantity());
        org.junit.jupiter.api.Assertions.assertNotNull(savedLine.getId());
        assertEquals(1, savedLine.getLineNo());
    }
}
