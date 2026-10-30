package com.labmanage.module.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.equipment.dto.ModelCreateRequest;
import com.labmanage.module.equipment.dto.ModelPageQuery;
import com.labmanage.module.equipment.dto.ModelUpdateRequest;
import com.labmanage.module.equipment.entity.EquipmentCategory;
import com.labmanage.module.equipment.entity.EquipmentModel;
import com.labmanage.module.equipment.mapper.EquipmentCategoryMapper;
import com.labmanage.module.equipment.mapper.EquipmentModelMapper;
import com.labmanage.module.equipment.service.ModelService;
import com.labmanage.module.equipment.vo.ModelVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备型号服务实现
 */
@Service
public class ModelServiceImpl implements ModelService {

    private final EquipmentModelMapper modelMapper;
    private final EquipmentCategoryMapper categoryMapper;

    public ModelServiceImpl(EquipmentModelMapper modelMapper, EquipmentCategoryMapper categoryMapper) {
        this.modelMapper = modelMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public PageResult<ModelVO> page(ModelPageQuery query) {
        LambdaQueryWrapper<EquipmentModel> wrapper = new LambdaQueryWrapper<>();
        if (query.getCategoryId() != null) {
            wrapper.eq(EquipmentModel::getCategoryId, query.getCategoryId());
        }
        if (StringUtils.hasText(query.getModelCode())) {
            wrapper.like(EquipmentModel::getModelCode, query.getModelCode().trim());
        }
        if (StringUtils.hasText(query.getEquipmentName())) {
            wrapper.like(EquipmentModel::getEquipmentName, query.getEquipmentName().trim());
        }
        wrapper.orderByAsc(EquipmentModel::getId);
        Page<EquipmentModel> page = modelMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(assemble(page.getRecords()), page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public void create(ModelCreateRequest request) {
        EquipmentCategory category = categoryMapper.selectById(request.categoryId());
        if (category == null || category.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "所属分类不存在或已停用");
        }
        Long codeExists = modelMapper.selectCount(new LambdaQueryWrapper<EquipmentModel>()
                .eq(EquipmentModel::getModelCode, request.modelCode()));
        if (codeExists > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "型号码已存在");
        }
        EquipmentModel model = new EquipmentModel();
        model.setCategoryId(request.categoryId());
        model.setModelCode(request.modelCode());
        model.setEquipmentName(request.equipmentName());
        model.setBrand(request.brand());
        model.setSpecification(request.specification());
        model.setQuantity(request.quantity());
        model.setUnitName(request.unitName());
        model.setReferencePrice(request.referencePrice());
        model.setExpectedLifeMonths(request.expectedLifeMonths());
        model.setRequiresDataErasure(Boolean.TRUE.equals(request.requiresDataErasure()) ? 1 : 0);
        model.setEnabled(1);
        modelMapper.insert(model);
    }

    @Override
    public void update(String id, ModelUpdateRequest request) {
        EquipmentModel model = requireModel(Ids.parse(id));
        model.setEquipmentName(request.equipmentName());
        model.setBrand(request.brand());
        model.setSpecification(request.specification());
        model.setQuantity(request.quantity());
        model.setUnitName(request.unitName());
        model.setReferencePrice(request.referencePrice());
        model.setExpectedLifeMonths(request.expectedLifeMonths());
        model.setRequiresDataErasure(Boolean.TRUE.equals(request.requiresDataErasure()) ? 1 : 0);
        model.setEnabled(request.enabled());
        modelMapper.updateById(model);
    }

    @Override
    public void delete(String id) {
        EquipmentModel model = requireModel(Ids.parse(id));
        if (modelMapper.countAssets(model.getId()) > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "型号下存在设备台账，无法删除");
        }
        modelMapper.deleteById(model.getId());
    }

    private EquipmentModel requireModel(Long modelId) {
        EquipmentModel model = modelMapper.selectById(modelId);
        if (model == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "设备型号不存在");
        }
        return model;
    }

    private List<ModelVO> assemble(List<EquipmentModel> models) {
        if (models.isEmpty()) {
            return List.of();
        }
        Map<Long, EquipmentCategory> categories = loadMap(
                models.stream().map(EquipmentModel::getCategoryId).collect(Collectors.toSet()),
                categoryMapper::selectBatchIds,
                EquipmentCategory::getId);
        return models.stream()
                .map(model -> {
                    EquipmentCategory category = categories.get(model.getCategoryId());
                    return new ModelVO(
                            model.getId().toString(),
                            model.getCategoryId().toString(),
                            category != null ? category.getCategoryName() : null,
                            model.getModelCode(),
                            model.getEquipmentName(),
                            model.getBrand(),
                            model.getSpecification(),
                            model.getQuantity() != null ? model.getQuantity() : 0,
                            model.getUnitName(),
                            model.getReferencePrice(),
                            model.getExpectedLifeMonths(),
                            model.getRequiresDataErasure() != null && model.getRequiresDataErasure() == 1,
                            model.getEnabled()
                    );
                })
                .toList();
    }

    private <T> Map<Long, T> loadMap(Collection<Long> ids,
                                     Function<Collection<Long>, List<T>> loader,
                                     Function<T, Long> keyGetter) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return loader.apply(ids).stream().collect(Collectors.toMap(keyGetter, Function.identity()));
    }
}
