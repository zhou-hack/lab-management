package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.MaterialQuotaQuery;
import com.labmanage.module.item.dto.MaterialQuotaUpdateDTO;
import com.labmanage.module.item.dto.MaterialQuotaUpsertDTO;
import com.labmanage.module.item.entity.MaterialCategory;
import com.labmanage.module.item.entity.MaterialQuota;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.mapper.MaterialCategoryMapper;
import com.labmanage.module.item.mapper.MaterialQuotaMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.service.MaterialQuotaService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.MaterialQuotaVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 领用定额服务实现
 */
@Service
public class MaterialQuotaServiceImpl implements MaterialQuotaService {

    private final MaterialQuotaMapper quotaMapper;
    private final MaterialSkuMapper skuMapper;
    private final MaterialCategoryMapper categoryMapper;

    public MaterialQuotaServiceImpl(MaterialQuotaMapper quotaMapper, MaterialSkuMapper skuMapper,
                                    MaterialCategoryMapper categoryMapper) {
        this.quotaMapper = quotaMapper;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
    }

    /**
     * 全局定额固定分组标识（2026-10-22 定稿：定额不再按开课实例分组，offering_id 保留列固定 0）
     */
    private static final Long GLOBAL_OFFERING_ID = 0L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(MaterialQuotaUpsertDTO dto) {
        Set<Long> skuIds = new HashSet<>();
        for (MaterialQuotaUpsertDTO.QuotaLineDTO line : dto.lines()) {
            Long skuId = IdUtils.requireId(line.skuId(), "物料 ID");
            if (!skuIds.add(skuId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "定额行物料重复：" + line.skuId());
            }
        }
        Map<Long, MaterialSku> skuMap = skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        // 存量定额一次加载（批量查询替代逐行查询；全局定额下每物料至多一条）
        Map<Long, MaterialQuota> existingMap = quotaMapper
                .selectByOfferingAndSkuIds(GLOBAL_OFFERING_ID, skuIds).stream()
                .collect(Collectors.toMap(MaterialQuota::getSkuId, quota -> quota));
        for (MaterialQuotaUpsertDTO.QuotaLineDTO line : dto.lines()) {
            Long skuId = IdUtils.requireId(line.skuId(), "物料 ID");
            MaterialSku sku = skuMap.get(skuId);
            if (sku == null) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                        "物料不存在：" + line.skuId());
            }
            MaterialQuota existing = existingMap.get(skuId);
            if (existing != null) {
                existing.setQtyPerStudentPeriod(line.qtyPerStudentPeriod());
                quotaMapper.updateById(existing);
            } else {
                MaterialQuota quota = new MaterialQuota();
                quota.setOfferingId(GLOBAL_OFFERING_ID);
                quota.setSkuId(skuId);
                quota.setQtyPerStudentPeriod(line.qtyPerStudentPeriod());
                quota.setEnabled(1);
                try {
                    quotaMapper.insert(quota);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(),
                            "该物料定额已存在，请勿重复登记");
                }
            }
        }
    }

    @Override
    public void update(String id, MaterialQuotaUpdateDTO dto) {
        MaterialQuota quota = requireQuota(id);
        quota.setQtyPerStudentPeriod(dto.qtyPerStudentPeriod());
        quotaMapper.updateById(quota);
    }

    @Override
    public void enable(String id) {
        MaterialQuota quota = requireQuota(id);
        quota.setEnabled(1);
        quotaMapper.updateById(quota);
    }

    @Override
    public void disable(String id) {
        MaterialQuota quota = requireQuota(id);
        quota.setEnabled(0);
        quotaMapper.updateById(quota);
    }

    @Override
    public PageResult<MaterialQuotaVO> page(MaterialQuotaQuery query) {
        LambdaQueryWrapper<MaterialQuota> wrapper = new LambdaQueryWrapper<>();
        if (query.getOfferingId() != null && !query.getOfferingId().isBlank()) {
            wrapper.eq(MaterialQuota::getOfferingId,
                    IdUtils.requireId(query.getOfferingId(), "开课实例 ID"));
        }
        if (query.getSkuId() != null && !query.getSkuId().isBlank()) {
            wrapper.eq(MaterialQuota::getSkuId, IdUtils.requireId(query.getSkuId(), "物料 ID"));
        }
        if (query.getCategoryId() != null && !query.getCategoryId().isBlank()) {
            List<Long> categorySkuIds = skuMapper.selectList(new LambdaQueryWrapper<MaterialSku>()
                            .eq(MaterialSku::getCategoryId, IdUtils.requireId(query.getCategoryId(), "分类 ID")))
                    .stream().map(MaterialSku::getId).toList();
            if (categorySkuIds.isEmpty()) {
                return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
            }
            wrapper.in(MaterialQuota::getSkuId, categorySkuIds);
        }
        wrapper.orderByDesc(MaterialQuota::getId);
        IPage<MaterialQuota> page = quotaMapper.selectPage(PageUtils.toPage(query), wrapper);

        List<MaterialQuota> records = page.getRecords();
        Set<Long> skuIds = records.stream().map(MaterialQuota::getSkuId).collect(Collectors.toSet());
        Map<Long, MaterialSku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIds).stream()
                        .collect(Collectors.toMap(MaterialSku::getId, sku -> sku));
        // 批量解析 SKU 所属分类（列表展示分类名称用）
        Set<Long> categoryIds = skuMap.values().stream()
                .map(MaterialSku::getCategoryId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, MaterialCategory> categoryMap = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectByIds(categoryIds).stream()
                        .collect(Collectors.toMap(MaterialCategory::getId, category -> category));
        List<MaterialQuotaVO> vos = records.stream()
                .map(quota -> {
                    MaterialSku sku = skuMap.get(quota.getSkuId());
                    MaterialCategory category = sku == null ? null
                            : categoryMap.get(sku.getCategoryId());
                    return MaterialQuotaVO.from(quota,
                            sku == null ? null : sku.getSkuCode(),
                            sku == null ? null : sku.getMaterialName(),
                            sku == null ? null : sku.getSpecification(),
                            sku == null ? null : sku.getBaseUnit(),
                            sku == null || sku.getCategoryId() == null
                                    ? null : String.valueOf(sku.getCategoryId()),
                            category == null ? null : category.getCategoryName());
                })
                .toList();
        return PageResult.of(vos, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    private MaterialQuota requireQuota(String id) {
        MaterialQuota quota = quotaMapper.selectById(IdUtils.requireId(id, "定额 ID"));
        if (quota == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "领用定额不存在");
        }
        return quota;
    }
}
