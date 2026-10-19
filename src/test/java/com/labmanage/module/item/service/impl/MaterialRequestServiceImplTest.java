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
import com.labmanage.module.item.enums.RequestType;
import com.labmanage.module.item.enums.StockDocumentStatus;
import com.labmanage.module.item.mapper.MaterialQuotaMapper;
import com.labmanage.module.item.mapper.MaterialRequestLineMapper;
import com.labmanage.module.item.mapper.MaterialRequestMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.SysUserRefMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 物料申领服务单元测试（纯 Mockito：定额快照、两级审核、代理代领校验、实发出库单生成）
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

    @Mock
    private SysUserRefMapper sysUserRefMapper;

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

    /** 本人申领创建 DTO（SELF） */
    private MaterialRequestCreateDTO selfDto(String labBookingId, String scheduleOccurrenceId,
                                             String offeringId, Integer participantCount,
                                             Integer periodCount, String purpose,
                                             List<MaterialRequestLineDTO> lines) {
        return new MaterialRequestCreateDTO(null, "9", null, null, offeringId,
                labBookingId, scheduleOccurrenceId, participantCount, periodCount, purpose,
                "SELF", null, lines);
    }

    /** 学生代理代领创建 DTO（PROXY，单行） */
    private MaterialRequestCreateDTO proxyDto(String sourceRequestId, String operatorId,
                                              String sourceLineId, String skuId, String requestedQty) {
        return new MaterialRequestCreateDTO(null, null, operatorId, null, null, null, null,
                null, null, "代同学领取实验耗材", "PROXY", sourceRequestId,
                List.of(new MaterialRequestLineDTO(skuId, new BigDecimal(requestedQty), sourceLineId)));
    }

    private BusinessException assertBizError(int expectedCode, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedCode, ex.getCode());
        return ex;
    }

    // ------------------------------------------------------------------ create（SELF）

    @Test
    void createRejectsBookingAndOccurrenceTogether() {
        MaterialRequestCreateDTO dto = selfDto("300", "400", null, 30, 4, "实验课",
                List.of(new MaterialRequestLineDTO("1", BigDecimal.TEN, null)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("不能同时填写"));
        verifyNoInteractions(requestMapper);
    }

    @Test
    void createRejectsDuplicateSkuLines() {
        MaterialRequestCreateDTO dto = selfDto(null, null, null, 30, 4, "实验课", List.of(
                new MaterialRequestLineDTO("1", BigDecimal.TEN, null),
                new MaterialRequestLineDTO("1", BigDecimal.ONE, null)));

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
        MaterialRequestCreateDTO dto = selfDto(null, null, "66", 30, 4, "有机化学实验",
                List.of(new MaterialRequestLineDTO("1", BigDecimal.TEN, null)));

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
        assertNotNull(saved.getId());
    }

    // ------------------------------------------------------------- create（PROXY 代领）

    @Test
    void proxyRejectsMissingSourceRequest() {
        BusinessException ex = assertBizError(40000,
                () -> service.create(proxyDto(null, "8", "11", "1", "5")));

        assertTrue(ex.getMessage().contains("不能新建空白申领"));
        verifyNoInteractions(requestMapper);
    }

    @Test
    void proxyRejectsSourceNotTeacherApproved() {
        when(requestMapper.selectById(777L))
                .thenReturn(selfWithStatus(777L, MaterialRequestStatus.APPROVED));

        BusinessException ex = assertBizError(40000,
                () -> service.create(proxyDto("777", "8", "11", "1", "5")));

        assertTrue(ex.getMessage().contains("不可代领"));
    }

    private MaterialRequest selfWithStatus(long id, MaterialRequestStatus status) {
        MaterialRequest source = request(id, status);
        source.setRequestType(RequestType.SELF);
        return source;
    }

    @Test
    void proxyRejectsSourceNotSelfType() {
        MaterialRequest proxyOrder = request(777L, MaterialRequestStatus.TEACHER_APPROVED);
        proxyOrder.setRequestType(RequestType.PROXY);
        when(requestMapper.selectById(777L)).thenReturn(proxyOrder);

        BusinessException ex = assertBizError(40000,
                () -> service.create(proxyDto("777", "8", "11", "1", "5")));

        assertTrue(ex.getMessage().contains("不可代领"));
    }

    @Test
    void proxyRejectsMissingOperator() {
        when(requestMapper.selectById(777L))
                .thenReturn(selfWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));

        assertBizError(40000, () -> service.create(proxyDto("777", null, "11", "1", "5")));
    }

    @Test
    void proxyRejectsMissingSourceLine() {
        when(requestMapper.selectById(777L))
                .thenReturn(selfWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(777L))
                .thenReturn(List.of(requestLine(11L, 1L, "10", "10", "2")));

        BusinessException ex = assertBizError(40400,
                () -> service.create(proxyDto("777", "8", "99", "1", "5")));

        assertTrue(ex.getMessage().contains("源申领行不存在"));
    }

    @Test
    void proxyRejectsSkuMismatch() {
        when(requestMapper.selectById(777L))
                .thenReturn(selfWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(777L))
                .thenReturn(List.of(requestLine(11L, 1L, "10", "10", "2")));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(2L)));

        BusinessException ex = assertBizError(40000,
                () -> service.create(proxyDto("777", "8", "11", "2", "5")));

        assertTrue(ex.getMessage().contains("物料不一致"));
    }

    @Test
    void proxyRejectsQtyOverRemaining() {
        when(requestMapper.selectById(777L))
                .thenReturn(selfWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
        // 源行：批准 10，实发 2；已被其他有效代领单占用 3 → 剩余可代领 5
        when(requestLineMapper.selectByRequestId(777L))
                .thenReturn(List.of(requestLine(11L, 1L, "10", "10", "2")));
        when(requestLineMapper.sumOccupiedQtyBySourceLineIds(any())).thenReturn(List.of(
                new MaterialRequestLineMapper.SourceLineOccupancy(11L, new BigDecimal("3"))));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));

        BusinessException ex = assertBizError(40000,
                () -> service.create(proxyDto("777", "8", "11", "1", "6")));

        assertTrue(ex.getMessage().contains("剩余可代领量"));
    }

    @Test
    void proxyCreatesBoundOrderInheritingSourceLine() {
        when(requestMapper.selectById(777L))
                .thenReturn(selfWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
        MaterialRequestLine sourceLine = requestLine(11L, 1L, "10", "10", "2");
        sourceLine.setQuotaQtySnapshot(new BigDecimal("6"));
        sourceLine.setRequiresExtraApproval(1);
        sourceLine.setRequiresDualSign(1);
        when(requestLineMapper.selectByRequestId(777L)).thenReturn(List.of(sourceLine));
        when(requestLineMapper.sumOccupiedQtyBySourceLineIds(any())).thenReturn(List.of(
                new MaterialRequestLineMapper.SourceLineOccupancy(11L, new BigDecimal("3"))));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));
        doAnswer(inv -> {
            inv.getArgument(0, MaterialRequest.class).setId(888L);
            return 1;
        }).when(requestMapper).insert(any(MaterialRequest.class));

        String id = service.create(proxyDto("777", "8", "11", "1", "5"));

        assertEquals("888", id);
        ArgumentCaptor<MaterialRequest> reqCaptor = ArgumentCaptor.forClass(MaterialRequest.class);
        verify(requestMapper).insert(reqCaptor.capture());
        MaterialRequest saved = reqCaptor.getValue();
        // 创建即老师已审核（源单已过老师审核），直接交管理员终审；责任老师绑定源单归属老师
        assertEquals(RequestType.PROXY, saved.getRequestType());
        assertEquals(777L, saved.getSourceRequestId());
        assertEquals(9L, saved.getOwnerTeacherId());
        assertEquals(8L, saved.getOperatorId());
        assertEquals(MaterialRequestStatus.TEACHER_APPROVED, saved.getStatus());

        ArgumentCaptor<List<MaterialRequestLine>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(requestLineMapper).insertBatch(linesCaptor.capture());
        MaterialRequestLine savedLine = linesCaptor.getValue().get(0);
        // 代领行绑定源行并继承定额快照与签批/双签标志
        assertEquals(11L, savedLine.getSourceLineId());
        assertEquals(new BigDecimal("6"), savedLine.getQuotaQtySnapshot());
        assertEquals(1, savedLine.getRequiresExtraApproval());
        assertEquals(1, savedLine.getRequiresDualSign());
        assertNotNull(savedLine.getId());
    }

    // ------------------------------------------------------------------ 两级审核

    @Test
    void teacherApproveRejectsWhenNotDraft() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.TEACHER_APPROVED));

        assertBizError(40900, () -> service.teacherApprove("555", null));

        verify(requestMapper, never()).casTeacherApprove(any(Long.class));
    }

    @Test
    void teacherApproveRejectsQtyOverRequest() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.DRAFT));
        when(requestLineMapper.selectByRequestId(555L))
                .thenReturn(List.of(requestLine(1L, 1L, "10", null, null)));

        MaterialRequestApproveDTO dto = new MaterialRequestApproveDTO(
                List.of(new MaterialRequestApproveDTO.ApproveLineDTO("1", new BigDecimal("11"))));

        BusinessException ex = assertBizError(40000, () -> service.teacherApprove("555", dto));

        assertTrue(ex.getMessage().contains("批准量不能超过申请量"));
    }

    @Test
    void teacherApproveDefaultsUnspecifiedLinesToRequested() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.DRAFT));
        when(requestLineMapper.selectByRequestId(555L)).thenReturn(List.of(
                requestLine(1L, 1L, "10", null, null),
                requestLine(2L, 2L, "5", null, null)));
        when(requestMapper.casTeacherApprove(555L)).thenReturn(1);

        MaterialRequestApproveDTO dto = new MaterialRequestApproveDTO(
                List.of(new MaterialRequestApproveDTO.ApproveLineDTO("1", new BigDecimal("8"))));

        assertDoesNotThrow(() -> service.teacherApprove("555", dto));

        ArgumentCaptor<MaterialRequestLine> lineCaptor = ArgumentCaptor.forClass(MaterialRequestLine.class);
        verify(requestLineMapper, times(2)).updateById(lineCaptor.capture());
        List<MaterialRequestLine> updated = lineCaptor.getAllValues();
        assertEquals(new BigDecimal("8"), updated.get(0).getApprovedQty());
        assertEquals(new BigDecimal("5"), updated.get(1).getApprovedQty());
        verify(requestMapper).casTeacherApprove(555L);
    }

    @Test
    void teacherRejectRejectsWhenNotDraft() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestMapper.casTeacherReject(555L)).thenReturn(0);

        assertBizError(40900, () -> service.teacherReject("555"));
    }

    @Test
    void approveRejectsDraftWithoutTeacherReview() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.DRAFT));

        assertBizError(40900, () -> service.approve("555", null));

        verify(requestMapper, never()).casApprove(any(Long.class));
    }

    @Test
    void approveFinalizesTeacherApprovedRequest() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(555L))
                .thenReturn(List.of(requestLine(1L, 1L, "10", "10", "0")));
        when(requestMapper.casApprove(555L)).thenReturn(1);

        assertDoesNotThrow(() -> service.approve("555", null));

        ArgumentCaptor<MaterialRequestLine> lineCaptor = ArgumentCaptor.forClass(MaterialRequestLine.class);
        verify(requestLineMapper).updateById(lineCaptor.capture());
        assertEquals(new BigDecimal("10"), lineCaptor.getValue().getApprovedQty());
        verify(requestMapper).casApprove(555L);
    }

    @Test
    void approveKeepsTeacherApprovedQtyWhenLinesAbsent() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(555L))
                .thenReturn(List.of(requestLine(1L, 1L, "12", "10", "0")));
        when(requestMapper.casApprove(555L)).thenReturn(1);

        assertDoesNotThrow(() -> service.approve("555", null));

        ArgumentCaptor<MaterialRequestLine> lineCaptor = ArgumentCaptor.forClass(MaterialRequestLine.class);
        verify(requestLineMapper).updateById(lineCaptor.capture());
        assertEquals(new BigDecimal("10"), lineCaptor.getValue().getApprovedQty());
        verify(requestMapper).casApprove(555L);
    }

    @Test
    void approveFallsBackToRequestedForProxyLineWithoutApprovedQty() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(555L))
                .thenReturn(List.of(requestLine(1L, 1L, "12", null, "0")));
        when(requestMapper.casApprove(555L)).thenReturn(1);

        assertDoesNotThrow(() -> service.approve("555", null));

        ArgumentCaptor<MaterialRequestLine> lineCaptor = ArgumentCaptor.forClass(MaterialRequestLine.class);
        verify(requestLineMapper).updateById(lineCaptor.capture());
        assertEquals(new BigDecimal("12"), lineCaptor.getValue().getApprovedQty());
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
        assertNotNull(savedLine.getId());
        assertEquals(1, savedLine.getLineNo());
    }
}
