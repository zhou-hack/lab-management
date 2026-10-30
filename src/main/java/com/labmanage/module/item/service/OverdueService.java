package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.item.dto.OverduePageQuery;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.entity.MaterialDocument;
import com.labmanage.module.item.entity.MaterialDocumentLine;
import com.labmanage.module.item.entity.MaterialSettlement;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper.OutstandingMaterial;
import com.labmanage.module.item.mapper.MaterialDocumentMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.MaterialSettlementMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.vo.OverdueItemVO;
import com.labmanage.module.item.vo.OverdueItemVO.MaterialSummary;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OverdueService {

    private static final String UNRETURNED = "UNRETURNED";
    private static final String RETURNED = "RETURNED";
    private static final Map<String, String> UNRETURNED_SORT_COLUMNS = Map.of(
            "returnDeadline", "claim.return_deadline",
            "postedAt", "issue_doc.posted_at",
            "sourcePostedAt", "issue_doc.posted_at",
            "documentNo", "issue_doc.document_no",
            "locationId", "issue_doc.location_id",
            "createTime", "issue_doc.create_time"
    );
    private static final Map<String, String> RETURNED_SORT_COLUMNS = Map.of(
            "returnDeadline", "claim.return_deadline",
            "postedAt", "return_doc.posted_at",
            "sourcePostedAt", "issue_doc.posted_at",
            "documentNo", "return_doc.document_no",
            "sourceDocumentNo", "issue_doc.document_no",
            "locationId", "issue_doc.location_id",
            "createTime", "return_doc.create_time"
    );

    private final MaterialDocumentMapper documentMapper;
    private final MaterialDocumentLineMapper documentLineMapper;
    private final MaterialSettlementMapper settlementMapper;
    private final MaterialClaimMapper claimMapper;
    private final MaterialMapper materialMapper;
    private final StockLocationMapper locationMapper;
    private final SysUserMapper userMapper;
    private final ItemFlowService flowService;
    private final ItemTimeService timeService;

    public OverdueService(MaterialDocumentMapper documentMapper,
                          MaterialDocumentLineMapper documentLineMapper,
                          MaterialSettlementMapper settlementMapper,
                          MaterialClaimMapper claimMapper,
                          MaterialMapper materialMapper,
                          StockLocationMapper locationMapper,
                          SysUserMapper userMapper,
                          ItemFlowService flowService,
                          ItemTimeService timeService) {
        this.documentMapper = documentMapper;
        this.documentLineMapper = documentLineMapper;
        this.settlementMapper = settlementMapper;
        this.claimMapper = claimMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.userMapper = userMapper;
        this.flowService = flowService;
        this.timeService = timeService;
    }

    public PageResult<OverdueItemVO> page(OverduePageQuery query) {
        String type = normalizeType(query.getType());
        validateDateRange(query.getStartDate(), query.getEndDate());
        Long currentUserId = SecurityUtils.currentUserId();
        Long applicantId = SecurityUtils.hasPermission("item:claim:review")
                ? query.getApplicantId() : currentUserId;
        Instant startAt = query.getStartDate() == null ? null : dayStart(query.getStartDate());
        Instant endAt = query.getEndDate() == null ? null : dayStart(query.getEndDate().plusDays(1));
        long offset = (long) (query.getPageNum() - 1) * query.getPageSize();
        String orderBy = orderBy(query, type);

        long total;
        List<MaterialDocument> documents;
        List<OverdueItemVO> records;
        if (UNRETURNED.equals(type)) {
            Instant now = timeService.now();
            total = documentMapper.countUnreturnedOverdues(query, now, applicantId, startAt, endAt);
            documents = total == 0 ? List.of() : documentMapper.selectUnreturnedOverdues(
                    query, now, applicantId, startAt, endAt, orderBy, offset, query.getPageSize());
            records = documents.isEmpty() ? List.of() : unreturnedVOs(documents, now);
        } else {
            total = documentMapper.countReturnedOverdues(query, applicantId, startAt, endAt);
            documents = total == 0 ? List.of() : documentMapper.selectReturnedOverdues(
                    query, applicantId, startAt, endAt, orderBy, offset, query.getPageSize());
            records = documents.isEmpty() ? List.of() : returnedVOs(documents);
        }
        return PageResult.of(records, total, query.getPageNum(), query.getPageSize());
    }

    private List<OverdueItemVO> unreturnedVOs(List<MaterialDocument> documents, Instant now) {
        Map<Long, MaterialClaim> claims = loadClaims(documents);
        Map<Long, BizDocument> claimDocuments = loadClaimDocuments(claims.values());
        Map<Long, StockLocation> locations = loadLocations(
                documents.stream().map(MaterialDocument::getLocationId).collect(Collectors.toSet()));
        Map<Long, SysUser> users = loadApplicantUsers(claims.values());
        List<Long> sourceIds = documents.stream().map(MaterialDocument::getId).toList();
        Map<Long, List<OutstandingMaterial>> outstandingBySource = documentLineMapper
                .selectOutstandingMaterials(sourceIds).stream()
                .collect(Collectors.groupingBy(OutstandingMaterial::sourceDocumentId));
        Set<Long> materialIds = outstandingBySource.values().stream().flatMap(Collection::stream)
                .map(OutstandingMaterial::materialId).collect(Collectors.toSet());
        Map<Long, Material> materials = loadMaterials(materialIds);

        List<OverdueItemVO> result = new ArrayList<>();
        for (MaterialDocument source : documents) {
            MaterialClaim claim = claims.get(source.getBusinessChainId());
            StockLocation location = locations.get(source.getLocationId());
            SysUser applicant = users.get(claim.getApplicantId());
            BizDocument claimDocument = claimDocuments.get(claim.getBizDocumentId());
            List<OutstandingMaterial> sourceOutstanding = outstandingBySource.getOrDefault(source.getId(), List.of());
            List<MaterialSummary> summaries = sourceOutstanding.stream()
                    .map(row -> materialSummary(row.materialId(), materials.get(row.materialId()),
                            row.outstandingQuantity(), null, null))
                    .toList();
            BigDecimal outstandingTotal = sourceOutstanding.stream()
                    .map(OutstandingMaterial::outstandingQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new OverdueItemVO(UNRETURNED,
                    str(claim.getId()), str(claim.getBizDocumentId()),
                    claimDocument == null ? null : claimDocument.getDocumentNo(),
                    str(source.getId()), str(source.getBizDocumentId()), source.getDocumentNo(),
                    null, null, null, summaries, outstandingTotal,
                    str(claim.getApplicantId()), applicant == null ? null : applicant.getRealName(),
                    str(location.getId()), location.getLocationCode(), location.getLocationName(),
                    source.getPostedAt(), null, claim.getReturnDeadline(),
                    overdueSeconds(claim.getReturnDeadline(), now), null));
        }
        return result;
    }

    private List<OverdueItemVO> returnedVOs(List<MaterialDocument> returnDocuments) {
        List<Long> returnIds = returnDocuments.stream().map(MaterialDocument::getId).toList();
        Set<Long> sourceIds = returnDocuments.stream().map(MaterialDocument::getSourceMaterialDocumentId)
                .collect(Collectors.toSet());
        Map<Long, MaterialDocument> sources = documentMapper.selectBatchIds(sourceIds).stream()
                .collect(Collectors.toMap(MaterialDocument::getId, Function.identity()));
        Map<Long, MaterialClaim> claims = loadClaims(returnDocuments);
        Map<Long, BizDocument> claimDocuments = loadClaimDocuments(claims.values());
        Map<Long, StockLocation> locations = loadLocations(sources.values().stream()
                .map(MaterialDocument::getLocationId).collect(Collectors.toSet()));
        Map<Long, SysUser> users = loadApplicantUsers(claims.values());

        List<MaterialDocumentLine> returnLines = documentLineMapper.selectList(
                Wrappers.<MaterialDocumentLine>lambdaQuery()
                        .in(MaterialDocumentLine::getMaterialDocumentId, returnIds)
                        .orderByAsc(MaterialDocumentLine::getMaterialDocumentId)
                        .orderByAsc(MaterialDocumentLine::getMaterialId));
        List<MaterialSettlement> settlements = settlementMapper.selectList(
                Wrappers.<MaterialSettlement>lambdaQuery()
                        .in(MaterialSettlement::getMaterialDocumentId, returnIds)
                        .orderByAsc(MaterialSettlement::getMaterialDocumentId)
                        .orderByAsc(MaterialSettlement::getMaterialId));
        Map<Long, Map<Long, BigDecimal>> returnedQuantities = new HashMap<>();
        Map<Long, Map<Long, BigDecimal>> settledQuantities = new HashMap<>();
        Set<Long> materialIds = new TreeSet<>();
        for (MaterialDocumentLine line : returnLines) {
            mergeQuantity(returnedQuantities, line.getMaterialDocumentId(), line.getMaterialId(), line.getQuantity());
            materialIds.add(line.getMaterialId());
        }
        for (MaterialSettlement settlement : settlements) {
            mergeQuantity(settledQuantities, settlement.getMaterialDocumentId(),
                    settlement.getMaterialId(), settlement.getQuantity());
            materialIds.add(settlement.getMaterialId());
        }
        Map<Long, Material> materials = loadMaterials(materialIds);

        List<OverdueItemVO> result = new ArrayList<>();
        for (MaterialDocument returnDocument : returnDocuments) {
            MaterialDocument source = sources.get(returnDocument.getSourceMaterialDocumentId());
            MaterialClaim claim = claims.get(returnDocument.getBusinessChainId());
            StockLocation location = locations.get(source.getLocationId());
            SysUser applicant = users.get(claim.getApplicantId());
            BizDocument claimDocument = claimDocuments.get(claim.getBizDocumentId());
            List<MaterialSummary> summaries = returnedMaterialSummaries(returnDocument.getId(),
                    returnedQuantities, settledQuantities, materials);
            result.add(new OverdueItemVO(RETURNED,
                    str(claim.getId()), str(claim.getBizDocumentId()),
                    claimDocument == null ? null : claimDocument.getDocumentNo(),
                    str(source.getId()), str(source.getBizDocumentId()), source.getDocumentNo(),
                    str(returnDocument.getId()), str(returnDocument.getBizDocumentId()), returnDocument.getDocumentNo(),
                    summaries, null,
                    str(claim.getApplicantId()), applicant == null ? null : applicant.getRealName(),
                    str(location.getId()), location.getLocationCode(), location.getLocationName(),
                    source.getPostedAt(), returnDocument.getPostedAt(), claim.getReturnDeadline(),
                    overdueSeconds(claim.getReturnDeadline(), returnDocument.getPostedAt()),
                    returnDocument.getOverdueReason()));
        }
        return result;
    }

    private List<MaterialSummary> returnedMaterialSummaries(
            Long returnDocumentId,
            Map<Long, Map<Long, BigDecimal>> returnedQuantities,
            Map<Long, Map<Long, BigDecimal>> settledQuantities,
            Map<Long, Material> materials) {
        Map<Long, BigDecimal> returned = returnedQuantities.getOrDefault(returnDocumentId, Map.of());
        Map<Long, BigDecimal> settled = settledQuantities.getOrDefault(returnDocumentId, Map.of());
        Set<Long> materialIds = new TreeSet<>(returned.keySet());
        materialIds.addAll(settled.keySet());
        return materialIds.stream()
                .map(materialId -> materialSummary(materialId, materials.get(materialId), null,
                        returned.getOrDefault(materialId, BigDecimal.ZERO),
                        settled.getOrDefault(materialId, BigDecimal.ZERO)))
                .toList();
    }

    private MaterialSummary materialSummary(Long materialId, Material material,
                                             BigDecimal outstanding, BigDecimal returned, BigDecimal settled) {
        return new MaterialSummary(str(materialId), material.getMaterialName(), material.getSpecification(),
                material.getMaterialType(), material.getBaseUnit(), outstanding, returned, settled);
    }

    private void mergeQuantity(Map<Long, Map<Long, BigDecimal>> target,
                               Long documentId, Long materialId, BigDecimal quantity) {
        target.computeIfAbsent(documentId, ignored -> new HashMap<>())
                .merge(materialId, quantity, BigDecimal::add);
    }

    private Map<Long, MaterialClaim> loadClaims(List<MaterialDocument> documents) {
        Set<Long> claimIds = documents.stream().map(MaterialDocument::getBusinessChainId)
                .collect(Collectors.toSet());
        return claimMapper.selectBatchIds(claimIds).stream()
                .collect(Collectors.toMap(MaterialClaim::getId, Function.identity()));
    }

    private Map<Long, BizDocument> loadClaimDocuments(Collection<MaterialClaim> claims) {
        Set<Long> documentIds = claims.stream().map(MaterialClaim::getBizDocumentId).collect(Collectors.toSet());
        return flowService.listDocumentsByIds(documentIds).stream()
                .collect(Collectors.toMap(BizDocument::getId, Function.identity()));
    }

    private Map<Long, StockLocation> loadLocations(Set<Long> locationIds) {
        return locationIds.isEmpty() ? Map.of() : locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(StockLocation::getId, Function.identity()));
    }

    private Map<Long, SysUser> loadApplicantUsers(Collection<MaterialClaim> claims) {
        Set<Long> userIds = claims.stream().map(MaterialClaim::getApplicantId).collect(Collectors.toSet());
        return userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private Map<Long, Material> loadMaterials(Set<Long> materialIds) {
        return materialIds.isEmpty() ? Map.of() : materialMapper.selectBatchIdsIncludingDeleted(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
    }

    private String orderBy(OverduePageQuery query, String type) {
        boolean unreturned = UNRETURNED.equals(type);
        String defaultOrder = unreturned
                ? "claim.return_deadline ASC, issue_doc.id ASC"
                : "return_doc.posted_at DESC, return_doc.id DESC";
        if (query.getSortBy() == null) {
            return defaultOrder;
        }
        String column = (unreturned ? UNRETURNED_SORT_COLUMNS : RETURNED_SORT_COLUMNS).get(query.getSortBy());
        if (column == null) {
            return defaultOrder;
        }
        String direction = "asc".equalsIgnoreCase(query.getSortOrder()) ? "ASC" : "DESC";
        return column + " " + direction + (unreturned ? ", issue_doc.id DESC" : ", return_doc.id DESC");
    }

    private String normalizeType(String type) {
        String value = type == null || type.isBlank() ? UNRETURNED : type;
        if (!UNRETURNED.equals(value) && !RETURNED.equals(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "逾期类型仅支持UNRETURNED/RETURNED");
        }
        return value;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "开始日期不能晚于结束日期");
        }
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(ItemTimeService.SHANGHAI).toInstant();
    }

    private long overdueSeconds(Instant deadline, Instant actualTime) {
        return Math.max(0, Duration.between(deadline, actualTime).getSeconds());
    }

    private String str(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
