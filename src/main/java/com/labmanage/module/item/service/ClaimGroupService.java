package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.entity.MaterialClaimLine;
import com.labmanage.module.item.enums.ClaimKind;
import com.labmanage.module.item.enums.ClaimStatus;
import com.labmanage.module.item.mapper.MaterialClaimLineMapper;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialDocumentLineMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 父子申领分组、锁顺序、业务链范围与父单状态投影。 */
@Service
public class ClaimGroupService {

    private final MaterialClaimMapper claimMapper;
    private final MaterialClaimLineMapper lineMapper;
    private final MaterialDocumentLineMapper documentLineMapper;
    private final ItemTimeService timeService;

    public ClaimGroupService(MaterialClaimMapper claimMapper,
                             MaterialClaimLineMapper lineMapper,
                             MaterialDocumentLineMapper documentLineMapper,
                             ItemTimeService timeService) {
        this.claimMapper = claimMapper;
        this.lineMapper = lineMapper;
        this.documentLineMapper = documentLineMapper;
        this.timeService = timeService;
    }

    public boolean isRoot(MaterialClaim claim) {
        return claim != null && claim.getClaimKind() == ClaimKind.ROOT;
    }

    public boolean isChild(MaterialClaim claim) {
        return claim != null && claim.getClaimKind() == ClaimKind.CHILD;
    }

    public MaterialClaim requireClaim(Long id) {
        MaterialClaim claim = claimMapper.selectById(id);
        if (claim == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申领单不存在");
        }
        return claim;
    }

    /** 统一按 ROOT → 当前 CHILD（按 ID 排序）加锁，避免整组操作与子单操作死锁。 */
    public MaterialClaim requireClaimForUpdate(Long id) {
        MaterialClaim snapshot = requireClaim(id);
        if (isChild(snapshot)) {
            MaterialClaim root = lockClaim(snapshot.getParentClaimId());
            lockCurrentChildren(root);
            return lockClaim(id);
        }
        MaterialClaim locked = lockClaim(id);
        if (isRoot(locked)) {
            lockCurrentChildren(locked);
        }
        return locked;
    }

    public List<MaterialClaim> allChildren(MaterialClaim root) {
        if (!isRoot(root)) {
            return List.of();
        }
        return claimMapper.selectList(Wrappers.<MaterialClaim>lambdaQuery()
                .eq(MaterialClaim::getClaimKind, ClaimKind.CHILD)
                .eq(MaterialClaim::getParentClaimId, root.getId())
                .orderByAsc(MaterialClaim::getLocationId)
                .orderByAsc(MaterialClaim::getId));
    }

    public List<MaterialClaim> currentChildren(MaterialClaim root) {
        if (!isRoot(root)) {
            return List.of();
        }
        return claimMapper.selectList(Wrappers.<MaterialClaim>lambdaQuery()
                .eq(MaterialClaim::getClaimKind, ClaimKind.CHILD)
                .eq(MaterialClaim::getParentClaimId, root.getId())
                .eq(MaterialClaim::getRevisionNo, root.getRevisionNo())
                .orderByAsc(MaterialClaim::getLocationId)
                .orderByAsc(MaterialClaim::getId));
    }

    public List<Long> currentChildIds(MaterialClaim root) {
        return currentChildren(root).stream().map(MaterialClaim::getId).toList();
    }

    /** 库存单据查询范围：ROOT 聚合全部 CHILD，其他类型只查询自身。 */
    public List<Long> documentClaimIds(MaterialClaim claim) {
        List<Long> ids = new ArrayList<>();
        ids.add(claim.getId());
        if (isRoot(claim)) {
            ids.addAll(allChildren(claim).stream().map(MaterialClaim::getId).toList());
        }
        return ids;
    }

    public boolean hasOutbound(MaterialClaim claim) {
        return !documentLineMapper.selectOutboundClaimIds(documentClaimIds(claim)).isEmpty();
    }

    public boolean hasOutbound(Long claimId) {
        return hasOutbound(requireClaim(claimId));
    }

    /** 子单状态变化后同步当前版本父单状态；调用方入口已通过 requireClaimForUpdate 固定锁顺序。 */
    public void syncParentStatus(MaterialClaim child, Long operatorId) {
        if (!isChild(child)) {
            return;
        }
        MaterialClaim root = requireClaim(child.getParentClaimId());
        List<MaterialClaim> children = currentChildren(root);
        if (children.isEmpty()) {
            return;
        }
        ClaimStatus nextStatus = aggregateStatus(children);
        Instant now = timeService.now();
        claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, root.getId())
                .eq(MaterialClaim::getRevisionNo, root.getRevisionNo())
                .set(MaterialClaim::getStatus, nextStatus)
                .set(MaterialClaim::getUpdateTime, now)
                .set(MaterialClaim::getUpdateUserId, operatorId));
        root.setStatus(nextStatus);
        root.setUpdateTime(now);
        root.setUpdateUserId(operatorId);
    }

    public void syncParentStatus(Long childId, Long operatorId) {
        syncParentStatus(requireClaim(childId), operatorId);
    }

    /** ROOT 行只读累计实发投影，实际出库仍只更新 CHILD 行。 */
    public void applyIssuedProjection(MaterialClaim root, List<MaterialClaimLine> rootLines) {
        if (!isRoot(root) || rootLines.isEmpty()) {
            return;
        }
        List<Long> childIds = currentChildIds(root);
        if (childIds.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> issuedByMaterial = lineMapper.selectList(Wrappers.<MaterialClaimLine>lambdaQuery()
                        .in(MaterialClaimLine::getClaimId, childIds)
                        .in(MaterialClaimLine::getRevisionNo, root.getRevisionNo()))
                .stream()
                .collect(Collectors.groupingBy(MaterialClaimLine::getMaterialId,
                        Collectors.reducing(BigDecimal.ZERO, MaterialClaimLine::getIssuedQuantity, BigDecimal::add)));
        rootLines.forEach(line -> line.setIssuedQuantity(
                issuedByMaterial.getOrDefault(line.getMaterialId(), BigDecimal.ZERO)));
    }

    private ClaimStatus aggregateStatus(List<MaterialClaim> children) {
        boolean anyPartial = children.stream().anyMatch(child -> child.getStatus() == ClaimStatus.PARTIALLY_ISSUED);
        boolean anyClosed = children.stream().anyMatch(child -> child.getStatus() == ClaimStatus.CLOSED);
        boolean anyOpen = children.stream().anyMatch(child -> child.getStatus() == ClaimStatus.PENDING
                || child.getStatus() == ClaimStatus.APPROVED || child.getStatus() == ClaimStatus.PARTIALLY_ISSUED);
        if (anyPartial || (anyClosed && anyOpen)) {
            return ClaimStatus.PARTIALLY_ISSUED;
        }
        if (anyClosed && !anyOpen) {
            return ClaimStatus.CLOSED;
        }
        if (children.stream().anyMatch(child -> child.getStatus() == ClaimStatus.PENDING)) {
            return ClaimStatus.PENDING;
        }
        if (children.stream().anyMatch(child -> child.getStatus() == ClaimStatus.APPROVED)) {
            return ClaimStatus.APPROVED;
        }
        if (children.stream().anyMatch(child -> child.getStatus() == ClaimStatus.CANCELLED)) {
            return ClaimStatus.CANCELLED;
        }
        if (children.stream().anyMatch(child -> child.getStatus() == ClaimStatus.EXPIRED)) {
            return ClaimStatus.EXPIRED;
        }
        return ClaimStatus.REJECTED;
    }

    private MaterialClaim lockClaim(Long id) {
        MaterialClaim claim = claimMapper.selectByIdForUpdate(id);
        if (claim == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "申领单不存在");
        }
        return claim;
    }

    private void lockCurrentChildren(MaterialClaim root) {
        currentChildIds(root).stream().sorted().forEach(this::lockClaim);
    }
}
