package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.MaterialRequestApproveDTO;
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
import com.labmanage.module.item.mapper.LabBookingRefMapper;
import com.labmanage.module.item.mapper.MaterialQuotaMapper;
import com.labmanage.module.item.mapper.MaterialRequestLineMapper;
import com.labmanage.module.item.mapper.MaterialRequestMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockDocumentMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.SysUserRefMapper;
import com.labmanage.module.item.service.MaterialRequestService;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.BookingOptionVO;
import com.labmanage.module.item.vo.MaterialRequestDetailVO;
import com.labmanage.module.item.vo.MaterialRequestLineVO;
import com.labmanage.module.item.vo.MaterialRequestVO;
import com.labmanage.module.item.vo.ProxyLineVO;
import com.labmanage.module.item.vo.ProxySourceVO;
import com.labmanage.module.item.vo.TeacherOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物料申领服务实现
 * <p>
 * 申领批准不扣库存；实发时生成关联申领行的 ISSUE 草稿单，过账阶段由库存引擎
 * 行锁申领行 + 条件更新 fulfilled_qty（防超批准量）并推进申领状态。
 * <p>
 * TEACHER 老师预约（单级管理员终审）+ PROXY 学生代理代领（2026-10-23 恢复）：
 * 老师本人发起并作为第一责任人，创建即 TEACHER_APPROVED（行批准量=申请量，终审可调整），
 * 直接交由管理员终审；学生代理代领只能引用老师端已审核通过的老师预约单（源行剩余可代领量控制），
 * 老师作为第一责任人继承源单、免老师级审核，创建即 TEACHER_APPROVED 直接交管理员终审。
 */
@Service
public class MaterialRequestServiceImpl implements MaterialRequestService {

    private final MaterialRequestMapper requestMapper;
    private final MaterialRequestLineMapper requestLineMapper;
    private final MaterialQuotaMapper quotaMapper;
    private final MaterialSkuMapper skuMapper;
    private final StockLocationMapper locationMapper;
    private final StockDocumentMapper documentMapper;
    private final StockDocumentLineMapper documentLineMapper;
    private final SysUserRefMapper sysUserRefMapper;
    private final LabBookingRefMapper bookingRefMapper;
    // 单据处理面板下线（2026-10-23）：实发出库单生成后同事务自动审批过账
    private final StockDocumentService stockDocumentService;

    public MaterialRequestServiceImpl(MaterialRequestMapper requestMapper,
                                      MaterialRequestLineMapper requestLineMapper,
                                      MaterialQuotaMapper quotaMapper,
                                      MaterialSkuMapper skuMapper,
                                      StockLocationMapper locationMapper,
                                      StockDocumentMapper documentMapper,
                                      StockDocumentLineMapper documentLineMapper,
                                      SysUserRefMapper sysUserRefMapper,
                                      LabBookingRefMapper bookingRefMapper,
                                      StockDocumentService stockDocumentService) {
        this.requestMapper = requestMapper;
        this.requestLineMapper = requestLineMapper;
        this.quotaMapper = quotaMapper;
        this.skuMapper = skuMapper;
        this.locationMapper = locationMapper;
        this.documentMapper = documentMapper;
        this.documentLineMapper = documentLineMapper;
        this.sysUserRefMapper = sysUserRefMapper;
        this.bookingRefMapper = bookingRefMapper;
        this.stockDocumentService = stockDocumentService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(MaterialRequestCreateDTO dto) {
        return dto.requestType() == RequestType.PROXY
                ? createProxy(dto)
                : createTeacher(dto);
    }

    /** 老师预约：老师本人发起并作为第一责任人，创建即 TEACHER_APPROVED，仅由管理员终审 */
    private String createTeacher(MaterialRequestCreateDTO dto) {
        Long ownerTeacherId = IdUtils.requireId(dto.ownerTeacherId(), "归属老师 ID");
        Long labBookingId = IdUtils.optionalId(dto.labBookingId(), "场地预约 ID");
        Long scheduleOccurrenceId = IdUtils.optionalId(dto.scheduleOccurrenceId(), "课次 ID");
        if (labBookingId != null && scheduleOccurrenceId != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "场地预约与课次不能同时填写，只能取其一");
        }
        Long offeringId = IdUtils.optionalId(dto.offeringId(), "开课实例 ID");

        MaterialRequest request = new MaterialRequest();
        request.setDocumentId(IdWorker.getId());
        request.setLabId(IdUtils.optionalId(dto.labId(), "实验室 ID"));
        request.setOwnerTeacherId(ownerTeacherId);
        // 经办人 = 老师本人（登录模块接入后由会话写入）
        request.setOperatorId(ownerTeacherId);
        request.setGrantId(IdUtils.optionalId(dto.grantId(), "助理授权 ID"));
        request.setOfferingId(offeringId);
        request.setLabBookingId(labBookingId);
        request.setScheduleOccurrenceId(scheduleOccurrenceId);
        request.setParticipantCount(dto.participantCount());
        request.setPeriodCount(dto.periodCount());
        request.setPurpose(dto.purpose());
        request.setRequestType(RequestType.TEACHER);
        // 老师本人即第一责任人，创建即视为老师已审，仅由管理员终审
        request.setStatus(MaterialRequestStatus.TEACHER_APPROVED);
        requestMapper.insert(request);

        List<MaterialRequestLine> lines = buildDirectLines(request, dto.lines(), offeringId,
                dto.participantCount(), dto.periodCount());
        if (!lines.isEmpty()) {
            requestLineMapper.insertBatch(lines);
        }
        return String.valueOf(request.getId());
    }

    /**
     * 学生代理代领：不能新建空白申领，必须引用老师端已审核通过的源申领单
     * （request_type=TEACHER、status=TEACHER_APPROVED），按源行剩余可代领量领取；
     * 创建即 TEACHER_APPROVED（老师作为第一责任人继承源单，免老师级审核），
     * 责任老师强制绑定源单归属老师，代领人 = operator_id，直接交由管理员终审。
     */
    private String createProxy(MaterialRequestCreateDTO dto) {
        if (dto.sourceRequestId() == null || dto.sourceRequestId().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "学生代理代领必须指定老师端已审核通过的源申领单，不能新建空白申领");
        }
        MaterialRequest source = requireRequest(dto.sourceRequestId());
        if (source.getRequestType() != RequestType.TEACHER
                || source.getStatus() != MaterialRequestStatus.TEACHER_APPROVED) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "源申领单不是老师端已审核通过的老师预约单，不可代领");
        }
        Long proxyOperatorId = IdUtils.optionalId(dto.operatorId(), "代领人 ID");
        if (dto.studentNo() == null || dto.studentNo().isBlank()
                || dto.studentName() == null || dto.studentName().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "学生代理代领必须填写代理学生学号与姓名");
        }
        Long sourceRequestId = source.getId();

        Map<Long, MaterialRequestLine> sourceLineMap = requestLineMapper
                .selectByRequestId(sourceRequestId).stream()
                .collect(Collectors.toMap(MaterialRequestLine::getId, line -> line));
        // 源行剩余可代领量 = 批准量 − 实发量 − 已被有效代领单占用申请量
        Map<Long, BigDecimal> occupiedMap = occupiedQtyOf(sourceLineMap.keySet());

        // 申领单 + SKU 唯一：同一单内物料去重
        Set<Long> skuIds = new HashSet<>();
        for (MaterialRequestLineDTO line : dto.lines()) {
            Long skuId = IdUtils.requireId(line.skuId(), "物料 ID");
            if (!skuIds.add(skuId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "申领行物料重复：" + line.skuId());
            }
        }
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));

        MaterialRequest request = new MaterialRequest();
        request.setDocumentId(IdWorker.getId());
        request.setLabId(source.getLabId());
        // 责任老师 = 源单归属老师（第一责任人继承源单，入参忽略）
        request.setOwnerTeacherId(source.getOwnerTeacherId());
        request.setOperatorId(proxyOperatorId);
        // 代理学生学号/姓名留档（登录模块接入后可由会话自动带出）
        request.setStudentNo(dto.studentNo().trim());
        request.setStudentName(dto.studentName().trim());
        request.setGrantId(IdUtils.optionalId(dto.grantId(), "助理授权 ID"));
        request.setOfferingId(source.getOfferingId());
        request.setLabBookingId(source.getLabBookingId());
        request.setScheduleOccurrenceId(source.getScheduleOccurrenceId());
        request.setParticipantCount(source.getParticipantCount());
        request.setPeriodCount(source.getPeriodCount());
        request.setPurpose(dto.purpose());
        request.setRequestType(RequestType.PROXY);
        request.setSourceRequestId(sourceRequestId);
        request.setStatus(MaterialRequestStatus.TEACHER_APPROVED);
        requestMapper.insert(request);

        List<MaterialRequestLine> lines = new ArrayList<>(dto.lines().size());
        for (MaterialRequestLineDTO lineDto : dto.lines()) {
            Long sourceLineId = IdUtils.requireId(lineDto.sourceLineId(), "源申领行 ID");
            MaterialRequestLine sourceLine = sourceLineMap.get(sourceLineId);
            if (sourceLine == null) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                        "源申领行不存在或不属于源申领单：" + lineDto.sourceLineId());
            }
            Long skuId = IdUtils.requireId(lineDto.skuId(), "物料 ID");
            if (!sourceLine.getSkuId().equals(skuId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "代领物料与源申领行物料不一致：" + lineDto.skuId());
            }
            MaterialSku sku = requireEnabledSku(skuMap, skuId, lineDto.skuId());
            validateQuantityScale(lineDto.requestedQty(), sku);

            BigDecimal available = sourceLine.getApprovedQty().subtract(sourceLine.getFulfilledQty())
                    .subtract(occupiedMap.getOrDefault(sourceLineId, BigDecimal.ZERO))
                    .max(BigDecimal.ZERO);
            if (lineDto.requestedQty().compareTo(available) > 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "代领量超过源申领行剩余可代领量：剩余 "
                                + available.stripTrailingZeros().toPlainString()
                                + "，本次 " + lineDto.requestedQty().stripTrailingZeros().toPlainString());
            }

            MaterialRequestLine line = new MaterialRequestLine();
            line.setId(IdWorker.getId());
            line.setRequestId(request.getId());
            line.setSkuId(skuId);
            line.setRequestedQty(lineDto.requestedQty());
            // 定额快照与签批/双签标志继承源行（源单已经老师端审核）
            line.setQuotaQtySnapshot(sourceLine.getQuotaQtySnapshot());
            line.setRequiresExtraApproval(sourceLine.getRequiresExtraApproval());
            line.setRequiresDualSign(sourceLine.getRequiresDualSign());
            line.setSourceLineId(sourceLineId);
            // 批准量由管理员终审写入；占用口径按申请量保守计算
            line.setFulfilledQty(BigDecimal.ZERO);
            lines.add(line);
        }
        if (!lines.isEmpty()) {
            requestLineMapper.insertBatch(lines);
        }
        return String.valueOf(request.getId());
    }

    /** 老师预约行构建：SKU 去重 + 启用校验 + 定额快照 + 签批/双签标志 + 批准量=申请量（本人发起即视为老师已批） */
    private List<MaterialRequestLine> buildDirectLines(MaterialRequest request,
                                                       List<MaterialRequestLineDTO> lineDtos,
                                                       Long offeringId,
                                                       Integer participantCount,
                                                       Integer periodCount) {
        // 预约单 + SKU 唯一：同一单内物料去重
        Set<Long> skuIds = new HashSet<>();
        for (MaterialRequestLineDTO line : lineDtos) {
            Long skuId = IdUtils.requireId(line.skuId(), "物料 ID");
            if (!skuIds.add(skuId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "预约行物料重复：" + line.skuId());
            }
        }
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        // 启用定额一次加载（批量查询替代逐行 selectByOfferingAndSku）
        Map<Long, MaterialQuota> quotaMap = offeringId == null ? Map.of()
                : quotaMapper.selectByOfferingAndSkuIds(offeringId, skuIds).stream()
                        .collect(Collectors.toMap(MaterialQuota::getSkuId, quota -> quota));

        BigDecimal participant = participantCount == null
                ? BigDecimal.ZERO : BigDecimal.valueOf(participantCount);
        BigDecimal period = periodCount == null
                ? BigDecimal.ZERO : BigDecimal.valueOf(periodCount);

        List<MaterialRequestLine> lines = new ArrayList<>(lineDtos.size());
        for (MaterialRequestLineDTO lineDto : lineDtos) {
            Long skuId = IdUtils.requireId(lineDto.skuId(), "物料 ID");
            MaterialSku sku = requireEnabledSku(skuMap, skuId, lineDto.skuId());
            validateQuantityScale(lineDto.requestedQty(), sku);

            // 定额快照：开课实例有启用定额时按 参与人数 × 课时 × 每人每课时定额；缺定额记 0
            BigDecimal quotaSnapshot = BigDecimal.ZERO;
            if (offeringId != null) {
                MaterialQuota quota = quotaMap.get(skuId);
                if (quota != null && quota.getEnabled() != null && quota.getEnabled() == 1) {
                    quotaSnapshot = participant.multiply(period)
                            .multiply(quota.getQtyPerStudentPeriod())
                            .setScale(3, RoundingMode.HALF_UP);
                }
            }

            MaterialRequestLine line = new MaterialRequestLine();
            line.setId(IdWorker.getId());
            line.setRequestId(request.getId());
            line.setSkuId(skuId);
            line.setRequestedQty(lineDto.requestedQty());
            line.setQuotaQtySnapshot(quotaSnapshot);
            // 超定额（含缺定额按 0 记）须老师签批确认
            line.setRequiresExtraApproval(
                    lineDto.requestedQty().compareTo(quotaSnapshot) > 0 ? 1 : 0);
            // 危险/高价值物料要求双人签字（标志按 SKU 主档预填，签字依赖后续接入）
            line.setRequiresDualSign(isOne(sku.getIsHazardous()) || isOne(sku.getIsValuable()) ? 1 : 0);
            // 老师本人发起即视为老师已批：批准量=申请量（管理员终审可调整）
            line.setApprovedQty(lineDto.requestedQty());
            line.setFulfilledQty(BigDecimal.ZERO);
            lines.add(line);
        }
        return lines;
    }

    @Override
    public PageResult<MaterialRequestVO> page(MaterialRequestQuery query) {
        LambdaQueryWrapper<MaterialRequest> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(MaterialRequest::getStatus, query.getStatus());
        }
        if (query.getRequestType() != null) {
            wrapper.eq(MaterialRequest::getRequestType, query.getRequestType());
        }
        if (query.getLabId() != null && !query.getLabId().isBlank()) {
            wrapper.eq(MaterialRequest::getLabId, IdUtils.requireId(query.getLabId(), "实验室 ID"));
        }
        if (query.getOwnerTeacherId() != null && !query.getOwnerTeacherId().isBlank()) {
            wrapper.eq(MaterialRequest::getOwnerTeacherId,
                    IdUtils.requireId(query.getOwnerTeacherId(), "归属老师 ID"));
        }
        if (query.getOfferingId() != null && !query.getOfferingId().isBlank()) {
            wrapper.eq(MaterialRequest::getOfferingId,
                    IdUtils.requireId(query.getOfferingId(), "开课实例 ID"));
        }
        if (query.getCourseName() != null && !query.getCourseName().isBlank()) {
            // 课程名称筛选：先按课程/班级名称查场地预约单，再按 lab_booking_id 过滤申领单
            List<Long> bookingIds = bookingRefMapper.searchIdsByCourseKeyword(query.getCourseName().trim());
            if (bookingIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(MaterialRequest::getLabBookingId, bookingIds);
        }
        wrapper.orderByDesc(MaterialRequest::getId);
        IPage<MaterialRequest> page = requestMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> names = resolveNames(page.getRecords());
        Map<Long, LabBookingRefMapper.BookingCourse> courseMap = bookingCourseMapOf(page.getRecords());
        List<MaterialRequestVO> records = page.getRecords().stream()
                .map(e -> MaterialRequestVO.from(e,
                        names.get(e.getOwnerTeacherId()),
                        names.get(e.getOperatorId()),
                        courseMap.get(e.getLabBookingId())))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public MaterialRequestDetailVO get(String id) {
        MaterialRequest request = requireRequest(id);
        List<MaterialRequestLine> lines = requestLineMapper.selectByRequestId(request.getId());
        Set<Long> skuIds = lines.stream().map(MaterialRequestLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIds).stream()
                        .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        List<MaterialRequestLineVO> lineVos = lines.stream()
                .map(line -> {
                    MaterialSku sku = skuMap.get(line.getSkuId());
                    return MaterialRequestLineVO.from(line,
                            sku == null ? null : sku.getSkuCode(),
                            sku == null ? null : sku.getMaterialName(),
                            sku == null ? null : sku.getBaseUnit());
                })
                .toList();
        Map<Long, String> names = resolveNames(List.of(request));
        Map<Long, LabBookingRefMapper.BookingCourse> courseMap = bookingCourseMapOf(List.of(request));
        return MaterialRequestDetailVO.from(request, lineVos,
                names.get(request.getOwnerTeacherId()),
                names.get(request.getOperatorId()),
                courseMap.get(request.getLabBookingId()));
    }

    /**
     * 批量解析申领单关联场地预约的课程信息（列表/详情展示课程名称用；未关联或已删除的预约返回空 Map 项）
     */
    private Map<Long, LabBookingRefMapper.BookingCourse> bookingCourseMapOf(List<MaterialRequest> requests) {
        Set<Long> bookingIds = requests.stream()
                .map(MaterialRequest::getLabBookingId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (bookingIds.isEmpty()) {
            // 可变空 Map：调用方会以 null 键 get（申领单未关联预约），Map.of() 会抛 NPE
            return new HashMap<>();
        }
        return bookingRefMapper.selectCourseByIds(new ArrayList<>(bookingIds)).stream()
                .collect(java.util.stream.Collectors.toMap(LabBookingRefMapper.BookingCourse::id, c -> c));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String id, MaterialRequestApproveDTO dto) {
        MaterialRequest request = requireRequest(id);
        if (request.getStatus() != MaterialRequestStatus.TEACHER_APPROVED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单不处于待终审状态，无法管理员终审");
        }
        writeApprovedQuantities(request, dto, false);
        if (requestMapper.casApprove(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "申领单状态已变更，终审失败");
        }
    }

    @Override
    public void reject(String id) {
        MaterialRequest request = requireRequest(id);
        if (requestMapper.casReject(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单不存在或不处于待终审状态，无法管理员终审拒绝");
        }
    }

    @Override
    public List<BookingOptionVO> bookingOptions(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        return bookingRefMapper.searchOptionsByKeyword(trimmed).stream()
                .map(BookingOptionVO::from)
                .toList();
    }

    @Override
    public List<TeacherOptionVO> teacherOptions(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        return sysUserRefMapper.searchTeacherOptions(trimmed).stream()
                .map(TeacherOptionVO::from)
                .toList();
    }

    @Override
    public PageResult<ProxySourceVO> proxySources(MaterialRequestQuery query) {
        LambdaQueryWrapper<MaterialRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaterialRequest::getRequestType, RequestType.TEACHER);
        wrapper.eq(MaterialRequest::getStatus, MaterialRequestStatus.TEACHER_APPROVED);
        if (query.getOwnerTeacherId() != null && !query.getOwnerTeacherId().isBlank()) {
            wrapper.eq(MaterialRequest::getOwnerTeacherId,
                    IdUtils.requireId(query.getOwnerTeacherId(), "归属老师 ID"));
        }
        if (query.getLabId() != null && !query.getLabId().isBlank()) {
            wrapper.eq(MaterialRequest::getLabId, IdUtils.requireId(query.getLabId(), "实验室 ID"));
        }
        wrapper.orderByDesc(MaterialRequest::getId);
        IPage<MaterialRequest> page = requestMapper.selectPage(PageUtils.toPage(query), wrapper);
        Map<Long, String> names = resolveNames(page.getRecords());
        Map<Long, LabBookingRefMapper.BookingCourse> courseMap = bookingCourseMapOf(page.getRecords());
        List<ProxySourceVO> records = page.getRecords().stream()
                .map(e -> ProxySourceVO.from(e, names.get(e.getOwnerTeacherId()),
                        courseMap.get(e.getLabBookingId())))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public List<ProxyLineVO> proxyLines(String sourceRequestId) {
        MaterialRequest source = requireRequest(sourceRequestId);
        if (source.getRequestType() != RequestType.TEACHER
                || source.getStatus() != MaterialRequestStatus.TEACHER_APPROVED) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "源申领单不是老师端已审核通过的老师预约单，不可代领");
        }
        List<MaterialRequestLine> lines = requestLineMapper.selectByRequestId(source.getId());
        Map<Long, BigDecimal> occupiedMap = occupiedQtyOf(lines.stream()
                .map(MaterialRequestLine::getId).collect(Collectors.toSet()));
        Set<Long> skuIds = lines.stream().map(MaterialRequestLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIds).stream()
                        .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        return lines.stream()
                .map(line -> {
                    MaterialSku sku = skuMap.get(line.getSkuId());
                    return ProxyLineVO.from(line,
                            sku == null ? null : sku.getSkuCode(),
                            sku == null ? null : sku.getMaterialName(),
                            sku == null ? null : sku.getBaseUnit(),
                            occupiedMap.get(line.getId()));
                })
                .toList();
    }

    @Override
    public void cancel(String id) {
        MaterialRequest request = requireRequest(id);
        // 部分实发不设派生态（状态仍为 APPROVED），取消前按申领行实发量校验
        boolean anyFulfilled = requestLineMapper.selectByRequestId(request.getId()).stream()
                .anyMatch(line -> line.getFulfilledQty() != null && line.getFulfilledQty().signum() > 0);
        if (anyFulfilled) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单已有实发量，不能取消，请走冲销流程");
        }
        if (requestMapper.casCancel(request.getId()) == 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单当前状态不允许取消");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String issue(String id, MaterialRequestIssueDTO dto) {
        MaterialRequest request = requireRequest(id);
        if (!request.issuable()) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "申领单未批准，不能实发出库");
        }
        List<MaterialRequestLine> lines = requestLineMapper.selectByRequestId(request.getId());
        Map<Long, MaterialRequestLine> lineMap = lines.stream()
                .collect(Collectors.toMap(MaterialRequestLine::getId, line -> line));
        Long defaultLocationId = IdUtils.requireId(dto.fromLocationId(), "出库位 ID");

        // 组装本次实发行：指定行用指定量与库位，未指定行按剩余批准量 + 默认库位
        List<StockDocumentLine> issueLines = new ArrayList<>();
        Map<Long, Long> locationIds = new HashMap<>();
        if (dto.lines() == null || dto.lines().isEmpty()) {
            for (MaterialRequestLine line : lines) {
                BigDecimal remaining = remainingOf(line);
                if (remaining.signum() <= 0) {
                    continue;
                }
                locationIds.put(line.getId(), defaultLocationId);
                issueLines.add(buildIssueLine(line, remaining, defaultLocationId));
            }
        } else {
            for (MaterialRequestIssueDTO.IssueLineDTO lineDto : dto.lines()) {
                Long lineId = IdUtils.requireId(lineDto.lineId(), "申领行 ID");
                MaterialRequestLine line = lineMap.get(lineId);
                if (line == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                            "申领行不存在：" + lineDto.lineId());
                }
                Long from = lineDto.fromLocationId() == null || lineDto.fromLocationId().isBlank()
                        ? defaultLocationId
                        : IdUtils.requireId(lineDto.fromLocationId(), "出库位 ID");
                if (lineDto.quantity().compareTo(remainingOf(line)) > 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                            "实发量超过剩余批准量：批准 "
                                    + line.getApprovedQty().stripTrailingZeros().toPlainString()
                                    + "，已实发 " + line.getFulfilledQty().stripTrailingZeros().toPlainString()
                                    + "，本次 " + lineDto.quantity().stripTrailingZeros().toPlainString());
                }
                locationIds.put(line.getId(), from);
                issueLines.add(buildIssueLine(line, lineDto.quantity(), from));
            }
        }
        if (issueLines.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "没有可实发的申领行（批准量已发完或未指定有效行）");
        }

        // 校验物料与库位
        Set<Long> skuIds = issueLines.stream().map(StockDocumentLine::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        Set<Long> locIds = new HashSet<>(locationIds.values());
        Map<Long, StockLocation> locationMap = locIds.isEmpty() ? Map.of()
                : locationMapper.selectByIds(locIds).stream()
                        .collect(Collectors.toMap(StockLocation::getId, location -> location));
        for (StockDocumentLine issueLine : issueLines) {
            MaterialSku sku = requireEnabledSku(skuMap, issueLine.getSkuId(),
                    String.valueOf(issueLine.getSkuId()));
            validateQuantityScale(issueLine.getQuantity(), sku);
            requireEnabledLocation(locationMap, issueLine.getFromLocationId());
        }

        // 生成关联申领单的 ISSUE 出库单，同一事务内自动审批并过账
        // （2026-10-23 单据处理面板下线：过账阶段行锁申领行、回写 fulfilled_qty、
        //  推进申领状态并扣减库存，实发即入账；任一步失败整笔回滚）
        Instant occurredAt = dto.occurredAt().toInstant().truncatedTo(ChronoUnit.MILLIS);
        StockDocument document = new StockDocument();
        document.setDocumentId(IdWorker.getId());
        document.setMovementType(MovementType.ISSUE);
        document.setStatus(StockDocumentStatus.DRAFT);
        document.setOperatorId(request.getOperatorId());
        document.setOwnerTeacherId(request.getOwnerTeacherId());
        document.setGrantId(request.getGrantId());
        document.setMaterialRequestId(request.getId());
        document.setLabBookingId(request.getLabBookingId());
        document.setOccurredAt(occurredAt);
        document.setReason(dto.reason());
        documentMapper.insert(document);

        int lineNo = 1;
        for (StockDocumentLine issueLine : issueLines) {
            issueLine.setId(IdWorker.getId());
            issueLine.setStockDocumentId(document.getId());
            issueLine.setLineNo(lineNo++);
        }
        documentLineMapper.insertBatch(issueLines);
        // 同一事务内自动审批并过账：CAS 状态迁移 + 行锁 + 余额/流水/申领回写复用既有过账路径
        stockDocumentService.approve(String.valueOf(document.getId()));
        stockDocumentService.post(String.valueOf(document.getId()));
        return String.valueOf(document.getId());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * 逐行写入批准量。
     *
     * @param defaultToRequested 未指定行批准量的缺省策略：true=按申请量批准；
     *                           false=管理员终审保持既有批准量，
     *                           行尚未写入批准量时回退按申请量
     */
    private void writeApprovedQuantities(MaterialRequest request, MaterialRequestApproveDTO dto,
            boolean defaultToRequested) {
        List<MaterialRequestLine> lines = requestLineMapper.selectByRequestId(request.getId());
        Map<Long, MaterialRequestLine> lineMap = lines.stream()
                .collect(Collectors.toMap(MaterialRequestLine::getId, line -> line));
        Map<Long, BigDecimal> approvedMap = new HashMap<>();
        if (dto != null && dto.lines() != null && !dto.lines().isEmpty()) {
            for (MaterialRequestApproveDTO.ApproveLineDTO lineDto : dto.lines()) {
                Long lineId = IdUtils.requireId(lineDto.lineId(), "申领行 ID");
                MaterialRequestLine line = lineMap.get(lineId);
                if (line == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                            "申领行不存在：" + lineDto.lineId());
                }
                if (lineDto.approvedQty().compareTo(line.getRequestedQty()) > 0) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                            "批准量不能超过申请量：申请 "
                                    + line.getRequestedQty().stripTrailingZeros().toPlainString()
                                    + "，批准 " + lineDto.approvedQty().stripTrailingZeros().toPlainString());
                }
                approvedMap.put(lineId, lineDto.approvedQty());
            }
        }
        for (MaterialRequestLine line : lines) {
            if (approvedMap.containsKey(line.getId())) {
                continue;
            }
            if (defaultToRequested || line.getApprovedQty() == null) {
                // 缺省按申请量（老师预约行创建时已写入批准量）
                approvedMap.put(line.getId(), line.getRequestedQty());
            } else {
                // 管理员终审缺省保持既有批准量（老师预约行的批准量）
                approvedMap.put(line.getId(), line.getApprovedQty());
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : approvedMap.entrySet()) {
            MaterialRequestLine line = lineMap.get(entry.getKey());
            line.setApprovedQty(entry.getValue());
            requestLineMapper.updateById(line);
        }
    }

    /** 源申领行已被有效代领单（未取消、未拒绝的 PROXY 单）占用的申请量，键为源行 ID */
    private Map<Long, BigDecimal> occupiedQtyOf(Set<Long> sourceLineIds) {
        if (sourceLineIds.isEmpty()) {
            return Map.of();
        }
        return requestLineMapper.sumOccupiedQtyBySourceLineIds(List.copyOf(sourceLineIds)).stream()
                .collect(Collectors.toMap(
                        MaterialRequestLineMapper.SourceLineOccupancy::sourceLineId,
                        MaterialRequestLineMapper.SourceLineOccupancy::occupiedQty));
    }

    /** 批量解析责任老师/经办人姓名（跨模块只读引用 sys_user；缺失用户返回空，前端回退显示 ID） */
    private Map<Long, String> resolveNames(Collection<MaterialRequest> requests) {
        Set<Long> userIds = new HashSet<>();
        for (MaterialRequest request : requests) {
            if (request.getOwnerTeacherId() != null) {
                userIds.add(request.getOwnerTeacherId());
            }
            if (request.getOperatorId() != null) {
                userIds.add(request.getOperatorId());
            }
        }
        if (userIds.isEmpty()) {
            // 可变空 Map：调用方会以 null 键 get（记录无老师/经办人），Map.of() 会抛 NPE
            return new HashMap<>();
        }
        return sysUserRefMapper.selectNamesByIds(List.copyOf(userIds)).stream()
                .filter(ref -> ref.realName() != null)
                .collect(Collectors.toMap(SysUserRefMapper.UserRef::userId,
                        SysUserRefMapper.UserRef::realName, (a, b) -> a));
    }

    private MaterialRequest requireRequest(String id) {
        Long requestPk = IdUtils.requireId(id, "申领单 ID");
        MaterialRequest request = requestMapper.selectById(requestPk);
        if (request == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申领单不存在");
        }
        return request;
    }

    private BigDecimal remainingOf(MaterialRequestLine line) {
        if (line.getApprovedQty() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal fulfilled = line.getFulfilledQty() == null
                ? BigDecimal.ZERO : line.getFulfilledQty();
        return line.getApprovedQty().subtract(fulfilled);
    }

    private StockDocumentLine buildIssueLine(MaterialRequestLine line, BigDecimal quantity, Long fromLocationId) {
        StockDocumentLine issueLine = new StockDocumentLine();
        issueLine.setSkuId(line.getSkuId());
        issueLine.setFromLocationId(fromLocationId);
        issueLine.setQuantity(quantity);
        issueLine.setRequestLineId(line.getId());
        return issueLine;
    }

    private MaterialSku requireEnabledSku(Map<Long, MaterialSku> skuMap, Long skuId, String raw) {
        MaterialSku sku = skuMap.get(skuId);
        if (sku == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料不存在：" + raw);
        }
        if (sku.getEnabled() == null || sku.getEnabled() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "物料已停用：" + raw);
        }
        return sku;
    }

    private void requireEnabledLocation(Map<Long, StockLocation> locationMap, Long locationId) {
        StockLocation location = locationMap.get(locationId);
        if (location == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位不存在：" + locationId);
        }
        if (location.getEnabled() == null || location.getEnabled() != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "库位已停用：" + locationId);
        }
    }

    private void validateQuantityScale(BigDecimal quantity, MaterialSku sku) {
        int scale = sku.getQuantityScale() == null ? 3 : sku.getQuantityScale();
        if (quantity.stripTrailingZeros().scale() > scale) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "数量精度超过物料数量精度（最多 " + scale + " 位小数）：" + sku.getSkuCode());
        }
    }

    private boolean isOne(Integer flag) {
        return flag != null && flag == 1;
    }
}
