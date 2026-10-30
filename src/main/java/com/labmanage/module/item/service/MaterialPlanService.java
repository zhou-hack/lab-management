package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.item.dto.MaterialPlanLineDTO;
import com.labmanage.module.item.dto.MaterialPlanMaterialQuery;
import com.labmanage.module.item.dto.MaterialPlanUpsertDTO;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.entity.MaterialBookingPlan;
import com.labmanage.module.item.entity.MaterialBookingPlanLine;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.mapper.MaterialBookingPlanLineMapper;
import com.labmanage.module.item.mapper.MaterialBookingPlanMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.vo.MaterialPlanLineVO;
import com.labmanage.module.item.vo.MaterialPlanMaterialVO;
import com.labmanage.module.item.vo.MaterialPlanVO;
import com.labmanage.module.item.validation.MaterialQuantityValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MaterialPlanService {

    private final MaterialBookingPlanMapper planMapper;
    private final MaterialBookingPlanLineMapper lineMapper;
    private final MaterialMapper materialMapper;
    private final ItemTimeService timeService;
    private final MaterialQuantityValidator quantityValidator;

    public MaterialPlanService(MaterialBookingPlanMapper planMapper,
                               MaterialBookingPlanLineMapper lineMapper,
                               MaterialMapper materialMapper,
                               ItemTimeService timeService,
                               MaterialQuantityValidator quantityValidator) {
        this.planMapper = planMapper;
        this.lineMapper = lineMapper;
        this.materialMapper = materialMapper;
        this.timeService = timeService;
        this.quantityValidator = quantityValidator;
    }

    public List<MaterialPlanVO> list(Integer enabled) {
        validateEnabledFilter(enabled);
        Long ownerId = SecurityUtils.currentUserId();
        List<MaterialBookingPlan> plans = planMapper.selectList(Wrappers.<MaterialBookingPlan>lambdaQuery()
                .eq(MaterialBookingPlan::getOwnerTeacherId, ownerId)
                .eq(enabled != null, MaterialBookingPlan::getEnabled, enabled)
                .orderByDesc(MaterialBookingPlan::getCreateTime)
                .orderByDesc(MaterialBookingPlan::getId));
        return toVOs(plans);
    }

    public PageResult<MaterialPlanMaterialVO> materials(MaterialPlanMaterialQuery query) {
        String materialName = trimToNull(query.getMaterialName());
        Page<Material> page = materialMapper.selectPage(PageUtils.toPage(query), Wrappers.<Material>lambdaQuery()
                .eq(Material::getEnabled, 1)
                .eq(query.getMaterialType() != null, Material::getMaterialType, query.getMaterialType())
                .like(materialName != null, Material::getMaterialName, materialName)
                .orderByAsc(Material::getMaterialName)
                .orderByAsc(Material::getId));
        List<Long> materialIds = page.getRecords().stream().map(Material::getId).toList();
        Map<Long, BigDecimal> ownedQuantities = materialIds.isEmpty() ? Map.of()
                : materialMapper.selectOwnedQuantities(materialIds).stream()
                .collect(Collectors.toMap(MaterialMapper.OwnedQuantityVO::materialId,
                        MaterialMapper.OwnedQuantityVO::ownedQuantity));
        List<MaterialPlanMaterialVO> records = page.getRecords().stream()
                .map(material -> new MaterialPlanMaterialVO(str(material.getId()), material.getMaterialName(),
                        material.getSpecification(), material.getMaterialType().name(), material.getQuantityMode(),
                        material.getBaseUnit(), ownedQuantities.getOrDefault(material.getId(), BigDecimal.ZERO)))
                .toList();
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    public MaterialPlanVO detail(Long id) {
        return toVOs(List.of(requireOwnedPlan(id))).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialPlanVO create(MaterialPlanUpsertDTO dto) {
        validate(dto);
        List<ResolvedPlanLine> lines = resolveLines(dto.lines());
        Long ownerId = SecurityUtils.currentUserId();
        MaterialBookingPlan plan = new MaterialBookingPlan();
        plan.setOwnerTeacherId(ownerId);
        applyFields(plan, dto);
        planMapper.insert(plan);
        try {
            for (ResolvedPlanLine line : lines) {
                insertLine(plan.getId(), line);
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "同一方案同一物料只能出现一次");
        }
        return detail(plan.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialPlanVO update(Long id, MaterialPlanUpsertDTO dto) {
        validate(dto);
        Long operatorId = SecurityUtils.currentUserId();
        MaterialBookingPlan plan = planMapper.selectByIdForUpdate(id);
        requireOwner(plan, operatorId);
        List<ResolvedPlanLine> desired = resolveLines(dto.lines());
        applyFields(plan, dto);
        plan.setUpdateTime(timeService.now());
        plan.setUpdateUserId(operatorId);
        if (planMapper.updateById(plan) != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "方案已变化，请刷新后重试");
        }

        Instant now = timeService.now();
        Map<Long, MaterialBookingPlanLine> existing = lineMapper.selectAllByPlanIdIncludingDeleted(id).stream()
                .collect(Collectors.toMap(MaterialBookingPlanLine::getMaterialId, Function.identity()));
        Map<Long, ResolvedPlanLine> desiredMap = desired.stream()
                .collect(Collectors.toMap(ResolvedPlanLine::materialId, Function.identity()));
        for (MaterialBookingPlanLine existingLine : existing.values()) {
            ResolvedPlanLine desiredLine = desiredMap.get(existingLine.getMaterialId());
            if (desiredLine != null) {
                if (lineMapper.restoreOrUpdate(existingLine.getId(), desiredLine.defaultQuantity(), now, operatorId) != 1) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(), "方案物料行已变化，请刷新后重试");
                }
            } else if (Integer.valueOf(0).equals(existingLine.getDeleted())
                    && lineMapper.logicalDelete(existingLine.getId(), now, operatorId) != 1) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "方案物料行已变化，请刷新后重试");
            }
        }
        try {
            for (ResolvedPlanLine desiredLine : desired) {
                if (!existing.containsKey(desiredLine.materialId())) {
                    insertLine(id, desiredLine);
                }
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "同一方案同一物料只能出现一次");
        }
        return detail(id);
    }

    private List<ResolvedPlanLine> resolveLines(List<MaterialPlanLineDTO> input) {
        Set<Long> uniqueIds = new HashSet<>();
        List<Long> materialIds = new ArrayList<>();
        for (MaterialPlanLineDTO line : input) {
            Long materialId = parseId(line.materialId());
            if (!uniqueIds.add(materialId)) {
                throw badRequest("同一方案同一物料只能出现一次：" + materialId);
            }
            materialIds.add(materialId);
        }
        Map<Long, Material> materials = materialMapper.selectBatchIds(materialIds).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity()));
        List<ResolvedPlanLine> result = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            MaterialPlanLineDTO line = input.get(i);
            Long materialId = materialIds.get(i);
            Material material = materials.get(materialId);
            if (material == null || !Integer.valueOf(1).equals(material.getEnabled())) {
                throw badRequest("物料不存在或已停用：" + materialId);
            }
            if (material.getMaterialType() == MaterialType.FIXED_ASSET && !isInteger(line.defaultQuantity())) {
                throw badRequest("固定资产默认数量必须为整数：" + materialId);
            }
            result.add(new ResolvedPlanLine(materialId, line.defaultQuantity()));
        }
        return result;
    }

    private List<MaterialPlanVO> toVOs(List<MaterialBookingPlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        List<Long> planIds = plans.stream().map(MaterialBookingPlan::getId).toList();
        List<MaterialBookingPlanLine> lines = lineMapper.selectList(Wrappers.<MaterialBookingPlanLine>lambdaQuery()
                .in(MaterialBookingPlanLine::getPlanId, planIds)
                .orderByAsc(MaterialBookingPlanLine::getPlanId)
                .orderByAsc(MaterialBookingPlanLine::getId));
        Set<Long> materialIds = lines.stream().map(MaterialBookingPlanLine::getMaterialId).collect(Collectors.toSet());
        Map<Long, Material> materials = materialIds.isEmpty() ? Map.of() : materialMapper.selectBatchIdsIncludingDeleted(materialIds)
                .stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        Map<Long, List<MaterialBookingPlanLine>> linesByPlan = lines.stream().collect(Collectors.groupingBy(
                MaterialBookingPlanLine::getPlanId, LinkedHashMap::new, Collectors.toList()));
        return plans.stream()
                .map(plan -> new MaterialPlanVO(str(plan.getId()), str(plan.getOwnerTeacherId()), plan.getPlanName(),
                        plan.getEnabled(), plan.getRemark(),
                        linesByPlan.getOrDefault(plan.getId(), List.of()).stream()
                                .map(line -> toLineVO(line, materials.get(line.getMaterialId()))).toList(),
                        plan.getCreateTime(), plan.getUpdateTime()))
                .toList();
    }

    private MaterialPlanLineVO toLineVO(MaterialBookingPlanLine line, Material material) {
        return new MaterialPlanLineVO(str(line.getId()), str(line.getMaterialId()),
                material == null ? null : material.getMaterialName(),
                material == null ? null : material.getSpecification(),
                material == null ? null : material.getMaterialType(),
                material == null ? null : material.getBaseUnit(), line.getDefaultQuantity());
    }

    private MaterialBookingPlan requireOwnedPlan(Long id) {
        MaterialBookingPlan plan = planMapper.selectById(id);
        requireOwner(plan, SecurityUtils.currentUserId());
        return plan;
    }

    private void requireOwner(MaterialBookingPlan plan, Long userId) {
        if (plan == null || !plan.getOwnerTeacherId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "预设方案不存在");
        }
    }

    private void insertLine(Long planId, ResolvedPlanLine line) {
        MaterialBookingPlanLine entity = new MaterialBookingPlanLine();
        entity.setPlanId(planId);
        entity.setMaterialId(line.materialId());
        entity.setDefaultQuantity(line.defaultQuantity());
        lineMapper.insert(entity);
    }

    private void applyFields(MaterialBookingPlan plan, MaterialPlanUpsertDTO dto) {
        plan.setPlanName(dto.planName().trim());
        plan.setEnabled(dto.enabled());
        plan.setRemark(trimToNull(dto.remark()));
    }

    private void validate(MaterialPlanUpsertDTO dto) {
        if (dto == null || dto.planName() == null || dto.planName().isBlank()
                || dto.planName().trim().length() > 128 || dto.enabled() == null
                || (dto.enabled() != 0 && dto.enabled() != 1) || dto.lines() == null || dto.lines().isEmpty()
                || (dto.remark() != null && dto.remark().length() > 512)) {
            throw badRequest("方案名称、启用状态、备注或物料行不合法");
        }
        for (MaterialPlanLineDTO line : dto.lines()) {
            if (line == null || line.materialId() == null || line.materialId().isBlank()) {
                throw badRequest("方案物料行不能为空");
            }
            quantityValidator.requirePositiveInteger(line.defaultQuantity(), "默认数量");
        }
    }

    private void validateEnabledFilter(Integer enabled) {
        if (enabled != null && enabled != 0 && enabled != 1) {
            throw badRequest("启用状态仅支持0/1");
        }
    }

    private boolean isInteger(BigDecimal quantity) {
        return quantity.stripTrailingZeros().scale() <= 0;
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw badRequest("物料ID不合法");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String str(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }

    private record ResolvedPlanLine(Long materialId, BigDecimal defaultQuantity) {
    }
}
