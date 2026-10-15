package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.MaterialCategoryCreateDTO;
import com.labmanage.module.item.dto.MaterialCategoryQuery;
import com.labmanage.module.item.dto.MaterialCategoryUpdateDTO;
import com.labmanage.module.item.entity.MaterialCategory;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.mapper.MaterialCategoryMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.service.MaterialCategoryService;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.MaterialCategoryVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 物料分类服务实现
 */
@Service
public class MaterialCategoryServiceImpl implements MaterialCategoryService {

    private static final int MAX_TREE_DEPTH_GUARD = 20;

    private final MaterialCategoryMapper categoryMapper;
    private final MaterialSkuMapper skuMapper;

    public MaterialCategoryServiceImpl(MaterialCategoryMapper categoryMapper, MaterialSkuMapper skuMapper) {
        this.categoryMapper = categoryMapper;
        this.skuMapper = skuMapper;
    }

    @Override
    public PageResult<MaterialCategoryVO> page(MaterialCategoryQuery query) {
        LambdaQueryWrapper<MaterialCategory> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(q -> q.likeRight(MaterialCategory::getCategoryCode, keyword)
                    .or()
                    .like(MaterialCategory::getCategoryName, keyword));
        }
        if (query.getParentId() != null && !query.getParentId().isBlank()) {
            wrapper.eq(MaterialCategory::getParentId, IdUtils.requireId(query.getParentId(), "父分类 ID"));
        }
        if (query.getEnabled() != null) {
            wrapper.eq(MaterialCategory::getEnabled, query.getEnabled() ? 1 : 0);
        }
        wrapper.orderByAsc(MaterialCategory::getCategoryCode);
        IPage<MaterialCategory> page = categoryMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<MaterialCategoryVO> records = page.getRecords().stream().map(MaterialCategoryVO::from).toList();
        return PageResult.of(records, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public List<MaterialCategoryVO> listEnabled() {
        LambdaQueryWrapper<MaterialCategory> wrapper = new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getEnabled, 1)
                .orderByAsc(MaterialCategory::getCategoryCode);
        return categoryMapper.selectList(wrapper).stream().map(MaterialCategoryVO::from).toList();
    }

    @Override
    public void create(MaterialCategoryCreateDTO dto) {
        checkCodeUnique(dto.categoryCode(), null);
        Long parentId = IdUtils.optionalId(dto.parentId(), "父分类 ID");
        if (parentId != null) {
            requireCategory(parentId);
        }
        MaterialCategory entity = new MaterialCategory();
        entity.setParentId(parentId);
        entity.setCategoryCode(dto.categoryCode().trim());
        entity.setCategoryName(dto.categoryName().trim());
        entity.setEnabled(1);
        try {
            categoryMapper.insert(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "分类编码已存在，不得复用");
        }
    }

    @Override
    public void update(MaterialCategoryUpdateDTO dto) {
        Long id = IdUtils.requireId(dto.id(), "分类 ID");
        requireCategory(id);
        checkCodeUnique(dto.categoryCode().trim(), id);
        Long parentId = IdUtils.optionalId(dto.parentId(), "父分类 ID");
        if (parentId != null) {
            requireCategory(parentId);
            checkNoCycle(id, parentId);
        }
        MaterialCategory entity = new MaterialCategory();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setCategoryCode(dto.categoryCode().trim());
        entity.setCategoryName(dto.categoryName().trim());
        try {
            categoryMapper.updateById(entity);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "分类编码已存在，不得复用");
        }
    }

    @Override
    public void updateStatus(String id, boolean enabled) {
        Long categoryId = IdUtils.requireId(id, "分类 ID");
        requireCategory(categoryId);
        MaterialCategory entity = new MaterialCategory();
        entity.setId(categoryId);
        entity.setEnabled(enabled ? 1 : 0);
        categoryMapper.updateById(entity);
    }

    @Override
    public MaterialCategoryVO get(String id) {
        MaterialCategory category = requireCategory(IdUtils.requireId(id, "分类 ID"));
        return MaterialCategoryVO.from(category);
    }

    @Override
    public void delete(String id) {
        Long categoryId = IdUtils.requireId(id, "分类 ID");
        requireCategory(categoryId);
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<MaterialCategory>().eq(MaterialCategory::getParentId, categoryId));
        if (childCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该分类下存在子分类，禁止删除");
        }
        Long skuCount = skuMapper.selectCount(
                new LambdaQueryWrapper<MaterialSku>().eq(MaterialSku::getCategoryId, categoryId));
        if (skuCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该分类下存在物料，禁止删除");
        }
        categoryMapper.deleteById(categoryId);
    }

    private MaterialCategory requireCategory(Long id) {
        MaterialCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "物料分类不存在");
        }
        return category;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<MaterialCategory> wrapper = new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getCategoryCode, code);
        if (excludeId != null) {
            wrapper.ne(MaterialCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "分类编码已存在，不得复用");
        }
    }

    /**
     * 修改父分类时防止树形循环
     */
    private void checkNoCycle(Long id, Long parentId) {
        if (id.equals(parentId)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "父分类不能是自身");
        }
        Long cursor = parentId;
        int depth = 0;
        while (cursor != null && depth < MAX_TREE_DEPTH_GUARD) {
            if (id.equals(cursor)) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "父分类设置形成循环");
            }
            MaterialCategory parent = categoryMapper.selectById(cursor);
            cursor = parent == null ? null : parent.getParentId();
            depth++;
        }
    }
}
