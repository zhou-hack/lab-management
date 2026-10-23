package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialRequestCreateDTO;
import com.labmanage.module.item.dto.MaterialRequestIssueDTO;
import com.labmanage.module.item.dto.MaterialRequestLineDTO;
import com.labmanage.module.item.dto.MaterialRequestQuery;
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
import com.labmanage.module.item.mapper.LabBookingRefMapper;
import com.labmanage.module.item.mapper.SysUserRefMapper;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.vo.ProxyLineVO;
import com.labmanage.module.item.vo.ProxySourceVO;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * 物料申领服务单元测试（纯 Mockito：TEACHER 老师预约创建、定额快照、
 * 管理员终审、实发出库单生成与同事务自动过账；PROXY 学生代理代领
 * 引用老师端已审核通过的老师预约单，含占用联动与剩余可代领量）
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

    @Mock
    private LabBookingRefMapper bookingRefMapper;

    @Mock
    private StockDocumentService stockDocumentService;

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

    /** 老师端已审核通过的老师预约单（PROXY 合法源单） */
    private MaterialRequest teacherWithStatus(long id, MaterialRequestStatus status) {
        MaterialRequest source = request(id, status);
        source.setRequestType(RequestType.TEACHER);
        return source;
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

    /** 老师预约创建 DTO（归属老师 9） */
    private MaterialRequestCreateDTO teacherDto(String labBookingId, String scheduleOccurrenceId,
                                                String offeringId, Integer participantCount,
                                                Integer periodCount, String purpose,
                                                List<MaterialRequestLineDTO> lines) {
        return new MaterialRequestCreateDTO(RequestType.TEACHER, null, null, "9", null,
                null, null, null, offeringId, labBookingId, scheduleOccurrenceId,
                participantCount, periodCount, purpose, lines);
    }

    /** 学生代理代领创建 DTO（PROXY，单行） */
    private MaterialRequestCreateDTO proxyDto(String sourceRequestId, String operatorId,
                                              String sourceLineId, String skuId, String requestedQty) {
        return new MaterialRequestCreateDTO(RequestType.PROXY, sourceRequestId, null, null,
                operatorId, "2023011234", "李同学", null, null, null, null, null, null,
                "代同学领取实验耗材",
                List.of(new MaterialRequestLineDTO(skuId, new BigDecimal(requestedQty), sourceLineId)));
    }

    private BusinessException assertBizError(int expectedCode, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedCode, ex.getCode());
        return ex;
    }

    // ------------------------------------------------------------- create（TEACHER 老师预约）

    @Test
    void createRejectsBookingAndOccurrenceTogether() {
        MaterialRequestCreateDTO dto = teacherDto("300", "400", null, 30, 4, "实验课",
                List.of(new MaterialRequestLineDTO("1", BigDecimal.TEN, null)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("不能同时填写"));
        verifyNoInteractions(requestMapper);
    }

    @Test
    void createRejectsDuplicateSkuLines() {
        MaterialRequestCreateDTO dto = teacherDto(null, null, null, 30, 4, "实验课", List.of(
                new MaterialRequestLineDTO("1", BigDecimal.TEN, null),
                new MaterialRequestLineDTO("1", BigDecimal.ONE, null)));

        BusinessException ex = assertBizError(40000, () -> service.create(dto));

        assertTrue(ex.getMessage().contains("物料重复"));
        // 行校验在主单落库之后（服务层事务回滚兜底）
        verify(requestMapper).insert(any(MaterialRequest.class));
        verify(requestLineMapper, never()).insertBatch(any());
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
        MaterialRequestCreateDTO dto = teacherDto(null, null, "66", 30, 4, "有机化学实验",
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
        // 老师本人发起即视为老师已批：批准量 = 申请量
        assertEquals(BigDecimal.TEN, saved.getApprovedQty());
        // 批量插入前须预赋雪花 ID
        assertNotNull(saved.getId());
    }

    @Test
    void teacherCreateRequiresOwnerTeacherId() {
        MaterialRequestCreateDTO dto = new MaterialRequestCreateDTO(RequestType.TEACHER, null,
                null, null, null, null, null, null, null, null, null, 30, 4, "实验课",
                List.of(new MaterialRequestLineDTO("1", BigDecimal.TEN, null)));

        assertBizError(40000, () -> service.create(dto));

        verifyNoInteractions(requestMapper);
    }

    @Test
    void teacherCreateSkipsTeacherReviewStage() {
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));
        doAnswer(inv -> {
            inv.getArgument(0, MaterialRequest.class).setId(666L);
            return 1;
        }).when(requestMapper).insert(any(MaterialRequest.class));

        MaterialRequestCreateDTO dto = new MaterialRequestCreateDTO(RequestType.TEACHER, null,
                null, "9", null, null, null, null, null, null, null, 30, 4, "实验课",
                List.of(new MaterialRequestLineDTO("1", BigDecimal.TEN, null)));

        String id = service.create(dto);

        assertEquals("666", id);
        ArgumentCaptor<MaterialRequest> reqCaptor = ArgumentCaptor.forClass(MaterialRequest.class);
        verify(requestMapper).insert(reqCaptor.capture());
        MaterialRequest saved = reqCaptor.getValue();
        // 老师本人第一责任人：创建即老师已审核（仅管理员终审）；经办人 = 老师本人
        assertEquals(9L, saved.getOwnerTeacherId());
        assertEquals(9L, saved.getOperatorId());
        assertEquals(RequestType.TEACHER, saved.getRequestType());
        assertEquals(MaterialRequestStatus.TEACHER_APPROVED, saved.getStatus());
        // 行批准量 = 申请量（本人发起即视为老师已批，终审可调整）
        ArgumentCaptor<List<MaterialRequestLine>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(requestLineMapper).insertBatch(linesCaptor.capture());
        assertEquals(BigDecimal.TEN, linesCaptor.getValue().get(0).getApprovedQty());
    }

    // ------------------------------------------------------------- create（PROXY 学生代理代领）

    @Test
    void proxyRejectsMissingSourceRequest() {
        BusinessException ex = assertBizError(40000,
                () -> service.create(proxyDto(null, "8", "11", "1", "5")));

        assertTrue(ex.getMessage().contains("不能新建空白申领"));
        verifyNoInteractions(requestMapper);
    }

    @Test
    void proxyRejectsSourceNotTeacherApproved() {
        // 源单类型正确但已过终审（APPROVED）也不可代领：只能引用待终审的老师预约单
        when(requestMapper.selectById(777L))
                .thenReturn(teacherWithStatus(777L, MaterialRequestStatus.APPROVED));

        BusinessException ex = assertBizError(40000,
                () -> service.create(proxyDto("777", "8", "11", "1", "5")));

        assertTrue(ex.getMessage().contains("不可代领"));
    }

    @Test
    void proxyRejectsSourceNotTeacherType() {
        MaterialRequest proxyOrder = request(777L, MaterialRequestStatus.TEACHER_APPROVED);
        proxyOrder.setRequestType(RequestType.PROXY);
        when(requestMapper.selectById(777L)).thenReturn(proxyOrder);

        BusinessException ex = assertBizError(40000,
                () -> service.create(proxyDto("777", "8", "11", "1", "5")));

        assertTrue(ex.getMessage().contains("不可代领"));
    }

    @Test
    void proxyAllowsMissingOperator() {
        when(requestMapper.selectById(777L))
                .thenReturn(teacherWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(777L))
                .thenReturn(List.of(requestLine(11L, 1L, "10", "10", "0")));
        when(requestLineMapper.sumOccupiedQtyBySourceLineIds(any())).thenReturn(List.of());
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));
        doAnswer(inv -> {
            inv.getArgument(0, MaterialRequest.class).setId(889L);
            return 1;
        }).when(requestMapper).insert(any(MaterialRequest.class));

        String id = service.create(proxyDto("777", null, "11", "1", "5"));

        assertEquals("889", id);
        ArgumentCaptor<MaterialRequest> reqCaptor = ArgumentCaptor.forClass(MaterialRequest.class);
        verify(requestMapper).insert(reqCaptor.capture());
        // 代领人 ID 输入已下线：operatorId 可空，学生学号/姓名留档为主
        assertNull(reqCaptor.getValue().getOperatorId());
        assertEquals("2023011234", reqCaptor.getValue().getStudentNo());
        assertEquals("李同学", reqCaptor.getValue().getStudentName());
    }

    @Test
    void proxyRejectsMissingStudentInfo() {
        when(requestMapper.selectById(777L))
                .thenReturn(teacherWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));

        MaterialRequestCreateDTO noStudentNo = new MaterialRequestCreateDTO(RequestType.PROXY,
                "777", null, null, "8", null, "李同学", null, null, null, null, null, null,
                "代同学领取实验耗材",
                List.of(new MaterialRequestLineDTO("1", BigDecimal.ONE, "11")));
        MaterialRequestCreateDTO noStudentName = new MaterialRequestCreateDTO(RequestType.PROXY,
                "777", null, null, "8", "2023011234", null, null, null, null, null, null, null,
                "代同学领取实验耗材",
                List.of(new MaterialRequestLineDTO("1", BigDecimal.ONE, "11")));

        BusinessException ex = assertBizError(40000, () -> service.create(noStudentNo));
        assertTrue(ex.getMessage().contains("学号与姓名"));
        assertBizError(40000, () -> service.create(noStudentName));
    }

    @Test
    void proxyRejectsMissingSourceLine() {
        when(requestMapper.selectById(777L))
                .thenReturn(teacherWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(777L))
                .thenReturn(List.of(requestLine(11L, 1L, "10", "10", "2")));

        BusinessException ex = assertBizError(40400,
                () -> service.create(proxyDto("777", "8", "99", "1", "5")));

        assertTrue(ex.getMessage().contains("源申领行不存在"));
    }

    @Test
    void proxyRejectsSkuMismatch() {
        when(requestMapper.selectById(777L))
                .thenReturn(teacherWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(777L))
                .thenReturn(List.of(requestLine(11L, 1L, "10", "10", "2")));

        BusinessException ex = assertBizError(40000,
                () -> service.create(proxyDto("777", "8", "11", "2", "5")));

        assertTrue(ex.getMessage().contains("物料不一致"));
    }

    @Test
    void proxyRejectsQtyOverRemaining() {
        when(requestMapper.selectById(777L))
                .thenReturn(teacherWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
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
                .thenReturn(teacherWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
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
        // 创建即老师已审核（老师作为第一责任人继承源单），直接交管理员终审；
        // 责任老师强制绑定源单归属老师，经办人 = 代理学生本人
        assertEquals(RequestType.PROXY, saved.getRequestType());
        assertEquals(777L, saved.getSourceRequestId());
        assertEquals(9L, saved.getOwnerTeacherId());
        assertEquals(8L, saved.getOperatorId());
        // 代理学生学号/姓名留档
        assertEquals("2023011234", saved.getStudentNo());
        assertEquals("李同学", saved.getStudentName());
        assertEquals(MaterialRequestStatus.TEACHER_APPROVED, saved.getStatus());

        ArgumentCaptor<List<MaterialRequestLine>> linesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(requestLineMapper).insertBatch(linesCaptor.capture());
        MaterialRequestLine savedLine = linesCaptor.getValue().get(0);
        // 代领行绑定源行并继承定额快照与签批/双签标志；批准量留空由管理员终审写入
        assertEquals(11L, savedLine.getSourceLineId());
        assertEquals(new BigDecimal("5"), savedLine.getRequestedQty());
        assertEquals(new BigDecimal("6"), savedLine.getQuotaQtySnapshot());
        assertEquals(1, savedLine.getRequiresExtraApproval());
        assertEquals(1, savedLine.getRequiresDualSign());
        assertNull(savedLine.getApprovedQty());
        assertEquals(BigDecimal.ZERO, savedLine.getFulfilledQty());
        assertNotNull(savedLine.getId());
    }

    // ---------------------------------------------------------- 代领源单与源行查询

    @Test
    void proxySourcesMapsTeacherApprovedRequests() {
        MaterialRequest source = teacherWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED);
        source.setOfferingId(66L);
        source.setLabBookingId(300L);
        source.setPurpose("有机化学实验");
        Page<MaterialRequest> page = new Page<>(1, 20);
        page.setRecords(List.of(source));
        page.setTotal(1L);
        when(requestMapper.selectPage(any(), any())).thenReturn(page);
        when(sysUserRefMapper.selectNamesByIds(any())).thenReturn(List.of(
                new SysUserRefMapper.UserRef(9L, "张老师")));
        when(bookingRefMapper.selectCourseByIds(any())).thenReturn(List.of(
                new LabBookingRefMapper.BookingCourse(300L, "电力电子技术", "电气 2301",
                        java.time.LocalDate.of(2026, 10, 20))));

        PageResult<ProxySourceVO> result = service.proxySources(new MaterialRequestQuery());

        assertEquals(1, result.records().size());
        ProxySourceVO vo = result.records().get(0);
        assertEquals("777", vo.id());
        assertEquals("9", vo.ownerTeacherId());
        assertEquals("张老师", vo.ownerTeacherName());
        assertEquals("66", vo.offeringId());
        // 已预约课程名称（前端代领源单下拉按课程名展示）
        assertEquals("电力电子技术", vo.courseName());
        assertEquals("有机化学实验", vo.purpose());
    }

    @Test
    void proxyLinesRejectsInvalidSource() {
        when(requestMapper.selectById(777L)).thenReturn(request(777L, MaterialRequestStatus.APPROVED));

        BusinessException ex = assertBizError(40000, () -> service.proxyLines("777"));

        assertTrue(ex.getMessage().contains("不可代领"));
    }

    @Test
    void proxyLinesReturnsAvailability() {
        when(requestMapper.selectById(777L))
                .thenReturn(teacherWithStatus(777L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(777L))
                .thenReturn(List.of(requestLine(11L, 1L, "10", "10", "2")));
        when(requestLineMapper.sumOccupiedQtyBySourceLineIds(any())).thenReturn(List.of(
                new MaterialRequestLineMapper.SourceLineOccupancy(11L, new BigDecimal("3"))));
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));

        List<ProxyLineVO> lines = service.proxyLines("777");

        assertEquals(1, lines.size());
        ProxyLineVO vo = lines.get(0);
        assertEquals("11", vo.lineId());
        assertEquals("SKU-ETHANOL-500ML", vo.skuCode());
        assertEquals(new BigDecimal("10"), vo.approvedQty());
        assertEquals(new BigDecimal("2"), vo.fulfilledQty());
        assertEquals(new BigDecimal("3"), vo.occupiedQty());
        // 剩余可代领量 = 批准 10 − 实发 2 − 已占用 3 = 5
        assertEquals(new BigDecimal("5"), vo.availableQty());
    }

    // ---------------------------------------------------------------- 管理员终审

    @Test
    void approveRejectsWhenNotPendingFinalReview() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.APPROVED));

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
    void approveFallsBackToRequestedForLineWithoutApprovedQty() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.TEACHER_APPROVED));
        when(requestLineMapper.selectByRequestId(555L))
                .thenReturn(List.of(requestLine(1L, 1L, "12", null, "0")));
        when(requestMapper.casApprove(555L)).thenReturn(1);

        assertDoesNotThrow(() -> service.approve("555", null));

        ArgumentCaptor<MaterialRequestLine> lineCaptor = ArgumentCaptor.forClass(MaterialRequestLine.class);
        verify(requestLineMapper).updateById(lineCaptor.capture());
        // PROXY 代领行批准量创建时留空：终审未指定时回退为申请量
        assertEquals(new BigDecimal("12"), lineCaptor.getValue().getApprovedQty());
        verify(requestMapper).casApprove(555L);
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void cancelRejectsWhenFulfilled() {
        // 部分实发不设派生态（状态仍为 APPROVED），取消按申领行实发量拦截
        when(requestMapper.selectById(555L))
                .thenReturn(request(555L, MaterialRequestStatus.APPROVED));
        when(requestLineMapper.selectByRequestId(555L))
                .thenReturn(List.of(requestLine(1L, 1L, "10", "10", "4")));

        BusinessException ex = assertBizError(40900, () -> service.cancel("555"));

        assertTrue(ex.getMessage().contains("实发量"));
        verify(requestMapper, never()).casCancel(any(Long.class));
    }

    // ------------------------------------------------------------------- issue

    @Test
    void issueRejectsWhenNotApproved() {
        when(requestMapper.selectById(555L)).thenReturn(request(555L, MaterialRequestStatus.TEACHER_APPROVED));

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

        // 单据处理面板下线（2026-10-23）：实发出库单同事务自动审批并过账
        verify(stockDocumentService).approve("901");
        verify(stockDocumentService).post("901");
    }
}
