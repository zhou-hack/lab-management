package com.labmanage.module.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.equipment.dto.CategoryCreateRequest;
import com.labmanage.module.equipment.dto.CategoryUpdateRequest;
import com.labmanage.module.equipment.entity.EquipmentCategory;
import com.labmanage.module.equipment.mapper.EquipmentCategoryMapper;
import com.labmanage.module.equipment.service.CategoryService;
import com.labmanage.module.equipment.vo.CategoryTreeVO;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 设备分类服务实现（多级树）
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final EquipmentCategoryMapper categoryMapper;

    public CategoryServiceImpl(EquipmentCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<CategoryTreeVO> tree() {
        List<EquipmentCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<EquipmentCategory>()
                .orderByAsc(EquipmentCategory::getCategoryCode));
        Map<Long, List<EquipmentCategory>> childrenMap = all.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(EquipmentCategory::getParentId, LinkedHashMap::new, Collectors.toList()));
        return all.stream()
                .filter(category -> category.getParentId() == null)
                .sorted(Comparator.comparing(EquipmentCategory::getCategoryCode))
                .map(category -> toTreeNode(category, childrenMap))
                .toList();
    }

    @Override
    public void create(CategoryCreateRequest request) {
        if (request.parentId() != null) {
            requireCategory(request.parentId());
        }
        Long codeExists = categoryMapper.selectCount(new LambdaQueryWrapper<EquipmentCategory>()
                .eq(EquipmentCategory::getCategoryCode, request.categoryCode()));
        if (codeExists > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "分类编码已存在");
        }
        EquipmentCategory category = new EquipmentCategory();
        category.setParentId(request.parentId());
        category.setCategoryCode(request.categoryCode());
        category.setCategoryName(request.categoryName());
        category.setEnabled(1);
        categoryMapper.insert(category);
    }

    @Override
    public void update(String id, CategoryUpdateRequest request) {
        EquipmentCategory category = requireCategory(Ids.parse(id));
        if (request.parentId() != null) {
            checkNoCycle(category.getId(), request.parentId());
        }
        category.setParentId(request.parentId());
        category.setCategoryName(request.categoryName());
        category.setEnabled(request.enabled());
        categoryMapper.updateById(category);
    }

    @Override
    public void delete(String id) {
        EquipmentCategory category = requireCategory(Ids.parse(id));
        if (categoryMapper.countChildren(category.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "分类下存在子分类，无法删除");
        }
        if (categoryMapper.countModels(category.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "分类下存在设备型号，无法删除");
        }
        categoryMapper.deleteById(category.getId());
    }

    /**
     * 层级循环校验：新父节点不能是自身，也不能位于自身子树内
     * （沿新父节点向上回溯，途经自身即形成循环）
     */
    private void checkNoCycle(Long categoryId, Long newParentId) {
        if (Objects.equals(categoryId, newParentId)) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "父分类不能是分类自身");
        }
        Long cursor = newParentId;
        while (cursor != null) {
            if (Objects.equals(cursor, categoryId)) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "分类层级形成循环");
            }
            cursor = requireCategory(cursor).getParentId();
        }
    }

    private EquipmentCategory requireCategory(Long categoryId) {
        EquipmentCategory category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "设备分类不存在");
        }
        return category;
    }

    /**
     * 递归装配树节点（子节点按分类码排序）
     */
    private CategoryTreeVO toTreeNode(EquipmentCategory category, Map<Long, List<EquipmentCategory>> childrenMap) {
        List<EquipmentCategory> children = childrenMap.getOrDefault(category.getId(), List.of());
        List<CategoryTreeVO> childNodes = children.stream()
                .sorted(Comparator.comparing(EquipmentCategory::getCategoryCode))
                .map(child -> toTreeNode(child, childrenMap))
                .toList();
        return new CategoryTreeVO(
                category.getId().toString(),
                category.getParentId() == null ? null : category.getParentId().toString(),
                category.getCategoryCode(),
                category.getCategoryName(),
                category.getEnabled(),
                childNodes
        );
    }
}
