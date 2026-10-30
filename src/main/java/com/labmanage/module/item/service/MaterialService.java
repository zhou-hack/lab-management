package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.item.dto.ItemPageQuery;
import com.labmanage.module.item.dto.MaterialPageQuery;
import com.labmanage.module.item.dto.MaterialUpsertDTO;
import com.labmanage.module.item.entity.Material;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.enums.TrackMode;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.vo.MaterialOptionVO;
import com.labmanage.module.item.vo.MaterialVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class MaterialService {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "materialName", "material_name",
            "skuCode", "sku_code",
            "createTime", "create_time",
            "updateTime", "update_time"
    );

    private final MaterialMapper materialMapper;
    private final ItemTimeService timeService;

    public MaterialService(MaterialMapper materialMapper, ItemTimeService timeService) {
        this.materialMapper = materialMapper;
        this.timeService = timeService;
    }

    public PageResult<MaterialVO> page(MaterialPageQuery query) {
        LambdaQueryWrapper<Material> wrapper = Wrappers.<Material>lambdaQuery()
                .like(hasText(query.getMaterialName()), Material::getMaterialName, trim(query.getMaterialName()))
                .eq(query.getMaterialType() != null, Material::getMaterialType, query.getMaterialType())
                .eq(query.getEnabled() != null, Material::getEnabled, query.getEnabled());
        applySort(wrapper, query);
        Page<Material> page = materialMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }

    public MaterialVO detail(Long id) {
        return toVO(requireMaterial(id));
    }

    public List<MaterialOptionVO> options(MaterialType materialType, String keyword) {
        return materialMapper.selectList(Wrappers.<Material>lambdaQuery()
                        .eq(Material::getEnabled, 1)
                        .eq(materialType != null, Material::getMaterialType, materialType)
                        .and(hasText(keyword), w -> w.like(Material::getMaterialName, trim(keyword))
                                .or().like(Material::getSkuCode, trim(keyword))
                                .or().like(Material::getSpecification, trim(keyword)))
                        .orderByAsc(Material::getMaterialName)
                        .orderByAsc(Material::getId))
                .stream()
                .map(m -> new MaterialOptionVO(String.valueOf(m.getId()), m.getMaterialType(), m.getTrackMode(),
                        m.getMaterialType() == MaterialType.CONSUMABLE ? null : m.getSkuCode(),
                        m.getMaterialName(), m.getSpecification(), m.getBaseUnit(), m.getQuantityMode()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialVO create(MaterialUpsertDTO dto) {
        validate(dto);
        String skuCode = dto.materialType() == MaterialType.CONSUMABLE ? null : trimToNull(dto.skuCode());
        requireSkuUnused(skuCode, null);
        Material material = new Material();
        applyFields(material, dto, skuCode);
        try {
            materialMapper.insert(material);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "SKU编码已存在：" + skuCode);
        }
        return detail(material.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialVO update(Long id, MaterialUpsertDTO dto) {
        validate(dto);
        Material material = requireMaterial(id);
        if (material.getMaterialType() != dto.materialType() || material.getTrackMode() != dto.trackMode()) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "物料类型与跟踪方式建档后不可修改");
        }
        String skuCode = dto.materialType() == MaterialType.CONSUMABLE ? null : trimToNull(dto.skuCode());
        requireSkuUnused(skuCode, id);
        applyFields(material, dto, skuCode);
        material.setUpdateTime(timeService.now());
        material.setUpdateUserId(SecurityUtils.currentUserId());
        try {
            int rows = materialMapper.updateById(material);
            if (rows != 1) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "物料档案已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "SKU编码已存在：" + skuCode);
        }
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Material material = materialMapper.selectByIdForUpdate(id);
        if (material == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料档案不存在");
        }
        boolean hasOwnedStock = materialMapper.selectOwnedQuantities(List.of(id)).stream()
                .anyMatch(quantity -> quantity.ownedQuantity().signum() > 0);
        if (hasOwnedStock || materialMapper.hasInTransitStock(id) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "物料仍有保有库存或在途数量，只能停用，不能删除");
        }
        int rows = materialMapper.logicalDelete(id, timeService.now(), SecurityUtils.currentUserId());
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "物料档案已变化，请刷新后重试");
        }
    }

    private void validate(MaterialUpsertDTO dto) {
        if (dto.materialType() == null || dto.trackMode() == null || dto.quantityMode() == null
                || dto.materialName() == null || dto.materialName().isBlank()
                || dto.baseUnit() == null || dto.baseUnit().isBlank()
                || dto.enabled() == null || (dto.enabled() != 0 && dto.enabled() != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "物料档案必填字段或启用状态不合法");
        }
        boolean validCombination = (dto.materialType() == MaterialType.CONSUMABLE
                && dto.trackMode() == TrackMode.QUANTITY)
                || (dto.materialType() == MaterialType.FIXED_ASSET && dto.trackMode() == TrackMode.SERIAL);
        if (!validCombination) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "耗材必须使用CONSUMABLE+QUANTITY，固定资产必须使用FIXED_ASSET+SERIAL");
        }
    }

    private void applyFields(Material material, MaterialUpsertDTO dto, String skuCode) {
        material.setMaterialType(dto.materialType());
        material.setTrackMode(dto.trackMode());
        material.setSkuCode(skuCode);
        material.setMaterialName(dto.materialName().trim());
        material.setSpecification(trimToNull(dto.specification()));
        material.setBaseUnit(dto.baseUnit().trim());
        material.setQuantityMode(dto.quantityMode());
        material.setEnabled(dto.enabled());
    }

    private void requireSkuUnused(String skuCode, Long excludeId) {
        if (skuCode == null) {
            return;
        }
        Long ownerId = materialMapper.selectIdBySkuIncludingDeleted(skuCode);
        if (ownerId != null && !ownerId.equals(excludeId)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "SKU编码已存在：" + skuCode);
        }
    }

    private Material requireMaterial(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料档案不存在");
        }
        return material;
    }

    private MaterialVO toVO(Material material) {
        return new MaterialVO(String.valueOf(material.getId()), material.getMaterialType(), material.getTrackMode(),
                material.getMaterialType() == MaterialType.CONSUMABLE ? null : material.getSkuCode(),
                material.getMaterialName(), material.getSpecification(), material.getBaseUnit(),
                material.getQuantityMode(), material.getEnabled(), material.getCreateTime(), material.getUpdateTime());
    }

    private void applySort(LambdaQueryWrapper<Material> wrapper, ItemPageQuery query) {
        String column = query.getSortBy() == null ? null : SORT_COLUMNS.get(query.getSortBy());
        if (column == null) {
            wrapper.orderByDesc(Material::getCreateTime);
            return;
        }
        wrapper.last("ORDER BY " + column + ("asc".equalsIgnoreCase(query.getSortOrder()) ? " ASC" : " DESC")
                + (column.equals("create_time") ? "" : ", create_time DESC"));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String trimToNull(String value) { return hasText(value) ? value.trim() : null; }
}
