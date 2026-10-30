package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.MaterialSkuCreateDTO;
import com.labmanage.module.item.dto.MaterialSkuQuery;
import com.labmanage.module.item.dto.MaterialSkuUpdateDTO;
import com.labmanage.module.item.entity.MaterialCategory;
import com.labmanage.module.item.entity.MaterialQuota;
import com.labmanage.module.item.entity.MaterialRequestLine;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.mapper.MaterialCategoryMapper;
import com.labmanage.module.item.mapper.MaterialQuotaMapper;
import com.labmanage.module.item.mapper.MaterialRequestLineMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.service.MaterialSkuService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.MaterialSkuVO;
import com.labmanage.module.item.vo.SkuOptionVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物料 SKU 服务实现
 */
@Service
public class MaterialSkuServiceImpl implements MaterialSkuService {

    private final MaterialSkuMapper skuMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final StockBalanceMapper balanceMapper;
    private final StockDocumentLineMapper documentLineMapper;
    private final MaterialRequestLineMapper requestLineMapper;
    private final MaterialQuotaMapper quotaMapper;

    public MaterialSkuServiceImpl(MaterialSkuMapper skuMapper, MaterialCategoryMapper categoryMapper,
                                  StockBalanceMapper balanceMapper, StockDocumentLineMapper documentLineMapper,
                                  MaterialRequestLineMapper requestLineMapper, MaterialQuotaMapper quotaMapper) {
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
        this.balanceMapper = balanceMapper;
        this.documentLineMapper = documentLineMapper;
        this.requestLineMapper = requestLineMapper;
        this.quotaMapper = quotaMapper;
    }

    @Override
    public PageResult<MaterialSkuVO> page(MaterialSkuQuery query) {
        LambdaQueryWrapper<MaterialSku> wrapper = new LambdaQueryWrapper<>();
        if (query.getSkuCode() != null && !query.getSkuCode().isBlank()) {
            wrapper.like(MaterialSku::getSkuCode, query.getSkuCode().trim());
        }
        if (query.getMaterialName() != null && !query.getMaterialName().isBlank()) {
            wrapper.like(MaterialSku::getMaterialName, query.getMaterialName().trim());
        }
        if (query.getCategoryId() != null && !query.getCategoryId().isBlank()) {
            wrapper.eq(MaterialSku::getCategoryId, IdUtils.requireId(query.getCategoryId(), "分类 ID"));
        }
        if (query.getMaterialType() != null) {
            wrapper.eq(MaterialSku::getMaterialType, query.getMaterialType());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(MaterialSku::getEnabled, query.getEnabled() ? 1 : 0);
        }
        wrapper.orderByAsc(MaterialSku::getSkuCode);
        IPage<MaterialSku> page = skuMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<MaterialSkuVO> records = toVos(page.getRecords());
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public List<SkuOptionVO> listOptions(String keyword) {
        LambdaQueryWrapper<MaterialSku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MaterialSku::getEnabled, 1);
        // 能申领的都是库存中有的：任一库位可用量 > 0（2026-10-24 定稿）
        wrapper.exists("SELECT 1 FROM stock_balance b "
                + "WHERE b.sku_id = material_sku.id AND b.deleted = 0 AND b.stock_qty > 0");
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(MaterialSku::getSkuCode, kw)
                    .or().like(MaterialSku::getMaterialName, kw));
        }
        wrapper.orderByAsc(MaterialSku::getSkuCode);
        wrapper.last("LIMIT 20");
        List<MaterialSku> skus = skuMapper.selectList(wrapper);
        if (skus.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> stockMap = balanceMapper.sumStockBySkuIds(
                        skus.stream().map(MaterialSku::getId).toList())
                .stream()
                .collect(Collectors.toMap(StockBalanceMapper.SkuStockTotal::skuId,
                        StockBalanceMapper.SkuStockTotal::totalQty));
        return skus.stream()
                .map(s -> new SkuOptionVO(String.valueOf(s.getId()), s.getSkuCode(), s.getMaterialName(),
                        s.getBaseUnit(), stockMap.getOrDefault(s.getId(), BigDecimal.ZERO)))
                .toList();
    }

    @Override
    public MaterialSkuVO get(String id) {
        MaterialSku sku = requireSku(IdUtils.requireId(id, "物料 ID"));
        return MaterialSkuVO.from(sku, categoryNameOf(sku.getCategoryId()));
    }

    @Override
    public void create(MaterialSkuCreateDTO dto) {
        checkCodeUnique(dto.skuCode().trim(), null);
        Long categoryId = IdUtils.requireId(dto.categoryId(), "分类 ID");
        requireCategory(categoryId);
        MaterialSku entity = new MaterialSku();
        entity.setCategoryId(categoryId);
        entity.setSkuCode(dto.skuCode().trim());
        entity.setMaterialName(dto.materialName().trim());
        entity.setSpecification(dto.specification());
        entity.setBaseUnit(dto.baseUnit().trim());
        entity.setQuantityScale(dto.quantityScale());
        entity.setMaterialType(dto.materialType());
        entity.setIsHazardous(Boolean.TRUE.equals(dto.isHazardous()) ? 1 : 0);
        entity.setIsValuable(Boolean.TRUE.equals(dto.isValuable()) ? 1 : 0);
        entity.setEnabled(1);
        try {
            skuMapper.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "SKU 码已存在，不得复用");
        }
    }

    @Override
    public void update(MaterialSkuUpdateDTO dto) {
        Long id = IdUtils.requireId(dto.id(), "物料 ID");
        requireSku(id);
        checkCodeUnique(dto.skuCode().trim(), id);
        Long categoryId = IdUtils.requireId(dto.categoryId(), "分类 ID");
        requireCategory(categoryId);
        MaterialSku entity = new MaterialSku();
        entity.setId(id);
        entity.setCategoryId(categoryId);
        entity.setSkuCode(dto.skuCode().trim());
        entity.setMaterialName(dto.materialName().trim());
        entity.setSpecification(dto.specification());
        entity.setBaseUnit(dto.baseUnit().trim());
        entity.setQuantityScale(dto.quantityScale());
        entity.setMaterialType(dto.materialType());
        entity.setIsHazardous(Boolean.TRUE.equals(dto.isHazardous()) ? 1 : 0);
        entity.setIsValuable(Boolean.TRUE.equals(dto.isValuable()) ? 1 : 0);
        try {
            skuMapper.updateById(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "SKU 码已存在，不得复用");
        }
    }

    @Override
    public void updateStatus(String id, boolean enabled) {
        Long skuId = IdUtils.requireId(id, "物料 ID");
        requireSku(skuId);
        MaterialSku entity = new MaterialSku();
        entity.setId(skuId);
        entity.setEnabled(enabled ? 1 : 0);
        skuMapper.updateById(entity);
    }

    @Override
    public void delete(String id) {
        Long skuId = IdUtils.requireId(id, "物料 ID");
        requireSku(skuId);
        if (balanceMapper.selectCount(new LambdaQueryWrapper<StockBalance>().eq(StockBalance::getSkuId, skuId)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该物料存在库存余额，禁止删除");
        }
        if (documentLineMapper.selectCount(
                new LambdaQueryWrapper<StockDocumentLine>().eq(StockDocumentLine::getSkuId, skuId)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该物料已有单据明细，禁止删除");
        }
        if (requestLineMapper.selectCount(
                new LambdaQueryWrapper<MaterialRequestLine>().eq(MaterialRequestLine::getSkuId, skuId)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该物料已有申领记录，禁止删除");
        }
        if (quotaMapper.selectCount(new LambdaQueryWrapper<MaterialQuota>().eq(MaterialQuota::getSkuId, skuId)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该物料已配置领用定额，禁止删除");
        }
        skuMapper.deleteById(skuId);
    }

    private List<MaterialSkuVO> toVos(List<MaterialSku> skus) {
        if (skus.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = skus.stream().map(MaterialSku::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> categoryNames = categoryMapper.selectByIds(categoryIds).stream()
                .collect(Collectors.toMap(MaterialCategory::getId, MaterialCategory::getCategoryName));
        return skus.stream()
                .map(sku -> MaterialSkuVO.from(sku, categoryNames.get(sku.getCategoryId())))
                .toList();
    }

    private String categoryNameOf(Long categoryId) {
        MaterialCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getCategoryName();
    }

    private MaterialSku requireSku(Long id) {
        MaterialSku sku = skuMapper.selectById(id);
        if (sku == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料不存在");
        }
        return sku;
    }

    private void requireCategory(Long id) {
        MaterialCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料分类不存在");
        }
    }

    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<MaterialSku> wrapper = new LambdaQueryWrapper<MaterialSku>()
                .eq(MaterialSku::getSkuCode, code);
        if (excludeId != null) {
            wrapper.ne(MaterialSku::getId, excludeId);
        }
        if (skuMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "SKU 码已存在，不得复用");
        }
    }

}
