package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.item.dto.ClaimPageQuery;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.entity.MaterialClaimAllocation;
import com.labmanage.module.item.entity.MaterialClaimLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.ClaimStatus;
import com.labmanage.module.item.enums.PickupMode;
import com.labmanage.module.item.mapper.MaterialClaimAllocationMapper;
import com.labmanage.module.item.mapper.MaterialClaimLineMapper;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.vo.ClaimAllocationVO;
import com.labmanage.module.item.vo.ClaimLineVO;
import com.labmanage.module.item.vo.ClaimSummaryVO;
import com.labmanage.module.item.vo.PickupDetailVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ClaimPickupService {

    private static final Map<String, SFunction<MaterialClaim, ?>> SORT_COLUMNS = Map.of(
            "createTime", MaterialClaim::getCreateTime,
            "bookingDate", MaterialClaim::getBookingDate,
            "bookingStartAt", MaterialClaim::getBookingStartAt,
            "bookingEndAt", MaterialClaim::getBookingEndAt,
            "status", MaterialClaim::getStatus,
            "revisionNo", MaterialClaim::getRevisionNo
    );

    private final MaterialClaimMapper claimMapper;
    private final MaterialClaimLineMapper lineMapper;
    private final MaterialClaimAllocationMapper allocationMapper;
    private final MaterialDocumentLineMapper documentLineMapper;
    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final SysUserMapper userMapper;
    private final ItemFlowService flowService;
    private final ItemTimeService timeService;

    public ClaimPickupService(MaterialClaimMapper claimMapper,
                              MaterialClaimLineMapper lineMapper,
                              MaterialClaimAllocationMapper allocationMapper,
                              MaterialDocumentLineMapper documentLineMapper,
                              MaterialMapper materialMapper,
                              StockLocationMapper locationMapper,
                              SysUserMapper userMapper,
                              ItemFlowService flowService,
                              ItemTimeService timeService) {
        this.claimMapper = claimMapper;
        this.lineMapper = lineMapper;
        this.allocationMapper = allocationMapper;
        this.documentLineMapper = documentLineMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.userMapper = userMapper;
        this.flowService = flowService;
        this.timeService = timeService;
    }

    public PageResult<ClaimSummaryVO> pageMine(ClaimPageQuery query) {
        Long studentId = SecurityUtils.currentUserId();
        if (query.bookingDateFrom() != null && query.bookingDateTo() != null
                && query.bookingDateFrom().isAfter(query.bookingDateTo())) {
            throw badRequest("预约开始日期不能晚于结束日期");
        }
        ClaimStatus status = parseStatus(query.status());
        LambdaQueryWrapper<MaterialClaim> wrapper = Wrappers.<MaterialClaim>lambdaQuery()
                .eq(MaterialClaim::getDelegateStudentId, studentId)
                .eq(MaterialClaim::getPickupMode, PickupMode.ASSISTANT)
                .eq(status != null, MaterialClaim::getStatus, status)
                .ge(query.bookingDateFrom() != null, MaterialClaim::getBookingDate, query.bookingDateFrom())
                .le(query.bookingDateTo() != null, MaterialClaim::getBookingDate, query.bookingDateTo());
        SFunction<MaterialClaim, ?> sortColumn = query.sortBy() == null ? MaterialClaim::getCreateTime
                : SORT_COLUMNS.getOrDefault(query.sortBy(), MaterialClaim::getCreateTime);
        wrapper.orderBy(true, "asc".equalsIgnoreCase(query.sortOrder()), sortColumn)
                .orderByDesc(MaterialClaim::getId);
        Page<MaterialClaim> page = claimMapper.selectPage(new Page<>(query.pageNum(), query.pageSize()), wrapper);
        List<MaterialClaim> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
        }
        Set<Long> documentIds = records.stream().map(MaterialClaim::getBizDocumentId).collect(Collectors.toSet());
        Map<Long, BizDocument> documents = flowService.listDocumentsByIds(documentIds).stream()
                .collect(Collectors.toMap(BizDocument::getId, Function.identity()));
        Set<Long> applicantIds = records.stream().map(MaterialClaim::getApplicantId).collect(Collectors.toSet());
        Map<Long, SysUser> applicants = userMapper.selectBatchIds(applicantIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<Long> claimIds = records.stream().map(MaterialClaim::getId).toList();
        Set<Long> issuedClaimIds = new HashSet<>(documentLineMapper.selectOutboundClaimIds(claimIds));
        Instant now = timeService.now();
        List<ClaimSummaryVO> result = records.stream()
                .map(claim -> toSummaryVO(claim, documents.get(claim.getBizDocumentId()),
                        applicants.get(claim.getApplicantId()), displayStatus(claim, issuedClaimIds.contains(claim.getId()), now)))
                .toList();
        return PageResult.of(result, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    public PickupDetailVO detailMine(Long id) {
        Long studentId = SecurityUtils.currentUserId();
        MaterialClaim claim = claimMapper.selectById(id);
        if (claim == null || claim.getPickupMode() != PickupMode.ASSISTANT
                || !studentId.equals(claim.getDelegateStudentId())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "代领申领单不存在");
        }
        BizDocument document = flowService.findDocumentById(claim.getBizDocumentId());
        if (document == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "代领申领单不存在");
        }
        List<MaterialClaimLine> lines = lineMapper.selectList(Wrappers.<MaterialClaimLine>lambdaQuery()
                .eq(MaterialClaimLine::getClaimId, id)
                .eq(MaterialClaimLine::getRevisionNo, claim.getRevisionNo())
                .orderByAsc(MaterialClaimLine::getMaterialId)
                .orderByAsc(MaterialClaimLine::getId));
        List<MaterialClaimAllocation> allocations = allocationMapper.selectList(
                Wrappers.<MaterialClaimAllocation>lambdaQuery()
                        .eq(MaterialClaimAllocation::getClaimId, id)
                        .eq(MaterialClaimAllocation::getRevisionNo, claim.getRevisionNo())
                        .orderByAsc(MaterialClaimAllocation::getLocationId)
                        .orderByAsc(MaterialClaimAllocation::getId));
        Set<Long> materialIds = lines.stream().map(MaterialClaimLine::getMaterialId).collect(Collectors.toSet());
        Map<Long, Material> materials = materialIds.isEmpty() ? Map.of() : materialMapper.selectBatchIdsIncludingDeleted(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        Set<Long> locationIds = allocations.stream().map(MaterialClaimAllocation::getLocationId).collect(Collectors.toSet());
        Map<Long, StockLocation> locations = locationIds.isEmpty() ? Map.of() : locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(StockLocation::getId, Function.identity()));
        Map<Long, MaterialClaimLine> linesById = lines.stream()
                .collect(Collectors.toMap(MaterialClaimLine::getId, Function.identity()));
        List<ClaimLineVO> lineVOs = lines.stream().map(line -> toLineVO(line, materials.get(line.getMaterialId()))).toList();
        List<ClaimAllocationVO> allocationVOs = allocations.stream().map(allocation -> {
            MaterialClaimLine line = linesById.get(allocation.getClaimLineId());
            StockLocation location = locations.get(allocation.getLocationId());
            return new ClaimAllocationVO(str(allocation.getId()), str(allocation.getClaimId()),
                    str(allocation.getClaimLineId()), allocation.getRevisionNo(),
                    line == null ? null : str(line.getMaterialId()), str(allocation.getLocationId()),
                    location == null ? null : location.getLocationCode(),
                    location == null ? null : location.getLocationName(), allocation.getPlannedQuantity());
        }).toList();
        SysUser applicant = userMapper.selectById(claim.getApplicantId());
        boolean issued = !documentLineMapper.selectOutboundClaimIds(List.of(id)).isEmpty();
        return new PickupDetailVO(str(id), document.getDocumentNo(), str(claim.getApplicantId()),
                applicant == null ? null : applicant.getRealName(), claim.getPurpose(), claim.getBookingDate(),
                claim.getBookingStartAt(), claim.getBookingEndAt(), claim.getReturnDeadline(),
                displayStatus(claim, issued, timeService.now()), str(claim.getDelegateStudentId()),
                claim.getDelegatedAt(), lineVOs, allocationVOs);
    }

    private ClaimSummaryVO toSummaryVO(MaterialClaim claim, BizDocument document, SysUser applicant,
                                       ClaimStatus displayStatus) {
        return new ClaimSummaryVO(str(claim.getId()), str(claim.getBizDocumentId()), str(claim.getId()),
                document == null ? null : document.getDocumentNo(), str(claim.getApplicantId()),
                applicant == null ? null : applicant.getRealName(), claim.getPurpose(), claim.getBookingDate(),
                claim.getBookingStartAt(), claim.getBookingEndAt(), claim.getReturnDeadline(), displayStatus,
                claim.getRevisionNo(), claim.getDelegatedAt(), claim.getCreateTime());
    }

    private ClaimLineVO toLineVO(MaterialClaimLine line, Material material) {
        return new ClaimLineVO(str(line.getId()), str(line.getClaimId()), line.getRevisionNo(),
                str(line.getMaterialId()), material == null ? null : material.getMaterialName(),
                material == null ? null : material.getSpecification(),
                material == null ? null : material.getMaterialType(),
                material == null ? null : material.getQuantityMode(),
                material == null ? null : material.getBaseUnit(), line.getRequestedQuantity(),
                line.getIssuedQuantity(), line.getRequestedQuantity().subtract(line.getIssuedQuantity()));
    }

    private ClaimStatus displayStatus(MaterialClaim claim, boolean hasIssued, Instant now) {
        boolean expired = !hasIssued && (claim.getStatus() == ClaimStatus.PENDING || claim.getStatus() == ClaimStatus.APPROVED)
                && timeService.isIssueWindowExpired(now, claim.getBookingDate());
        return expired ? ClaimStatus.EXPIRED : claim.getStatus();
    }

    private ClaimStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ClaimStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw badRequest("申领状态不合法");
        }
    }

    private String str(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }
}
