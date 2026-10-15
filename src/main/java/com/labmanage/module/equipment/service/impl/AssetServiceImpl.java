package com.labmanage.module.equipment.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.equipment.dto.AssetClassifyRequest;
import com.labmanage.module.equipment.dto.AssetCreateRequest;
import com.labmanage.module.equipment.dto.AssetPageQuery;
import com.labmanage.module.equipment.dto.AssetTransferRequest;
import com.labmanage.module.equipment.dto.AssetUpdateRequest;
import com.labmanage.module.equipment.entity.EquipmentAsset;
import com.labmanage.module.equipment.entity.EquipmentLocationHistory;
import com.labmanage.module.equipment.entity.EquipmentModel;
import com.labmanage.module.equipment.enums.EquipmentStatus;
import com.labmanage.module.equipment.mapper.EquipmentAssetMapper;
import com.labmanage.module.equipment.mapper.EquipmentLocationHistoryMapper;
import com.labmanage.module.equipment.mapper.EquipmentModelMapper;
import com.labmanage.module.equipment.service.AssetService;
import com.labmanage.module.equipment.vo.AssetVO;
import com.labmanage.module.equipment.vo.CustodianOptionVO;
import com.labmanage.module.space.entity.BizSpace;
import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.mapper.BizSpaceMapper;
import com.labmanage.roles.entity.SysDepartment;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysDepartmentMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备台账服务实现
 */
@Service
public class AssetServiceImpl implements AssetService {

    /** 台账维护权限（区分管理员维护视图与目录视图） */
    private static final String PERM_ASSET_UPDATE = "equipment:asset:update";

    private final EquipmentAssetMapper assetMapper;
    private final EquipmentModelMapper modelMapper;
    private final EquipmentLocationHistoryMapper locationHistoryMapper;
    private final BizSpaceMapper labMapper;
    private final SysDepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;

    public AssetServiceImpl(EquipmentAssetMapper assetMapper,
                            EquipmentModelMapper modelMapper,
                            EquipmentLocationHistoryMapper locationHistoryMapper,
                            BizSpaceMapper labMapper,
                            SysDepartmentMapper departmentMapper,
                            SysUserMapper userMapper) {
        this.assetMapper = assetMapper;
        this.modelMapper = modelMapper;
        this.locationHistoryMapper = locationHistoryMapper;
        this.labMapper = labMapper;
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<AssetVO> page(AssetPageQuery query) {
        LambdaQueryWrapper<EquipmentAsset> wrapper = new LambdaQueryWrapper<>();
        boolean maintainer = StpUtil.hasPermission(PERM_ASSET_UPDATE);
        if (maintainer) {
            if (query.getStatus() != null) {
                wrapper.eq(EquipmentAsset::getStatus, query.getStatus());
            }
        } else {
            // 无维护权限：强制收敛为「在架」目录视图
            wrapper.eq(EquipmentAsset::getStatus, EquipmentStatus.IDLE)
                    .eq(EquipmentAsset::getEnabled, 1);
        }
        if (query.getLabId() != null) {
            wrapper.eq(EquipmentAsset::getLabId, query.getLabId());
        }
        if (query.getDepartmentId() != null) {
            wrapper.eq(EquipmentAsset::getDepartmentId, query.getDepartmentId());
        }
        if (query.getModelId() != null) {
            wrapper.eq(EquipmentAsset::getModelId, query.getModelId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword();
            List<Long> modelIds = modelMapper.selectList(new LambdaQueryWrapper<EquipmentModel>()
                            .like(EquipmentModel::getEquipmentName, keyword))
                    .stream().map(EquipmentModel::getId).toList();
            wrapper.and(w -> {
                w.like(EquipmentAsset::getAssetCode, keyword).or().like(EquipmentAsset::getSerialNo, keyword);
                if (!modelIds.isEmpty()) {
                    w.or().in(EquipmentAsset::getModelId, modelIds);
                }
            });
        }
        wrapper.orderByAsc(EquipmentAsset::getId);
        Page<EquipmentAsset> page = assetMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(assemble(page.getRecords()), page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public void create(AssetCreateRequest request) {
        EquipmentModel model = modelMapper.selectById(request.modelId());
        if (model == null || model.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备型号不存在或已停用");
        }
        requireLab(request.labId());
        requireDepartment(request.departmentId());
        Long codeExists = assetMapper.selectCount(new LambdaQueryWrapper<EquipmentAsset>()
                .eq(EquipmentAsset::getAssetCode, request.assetCode()));
        if (codeExists > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "资产编号已存在");
        }
        EquipmentAsset asset = new EquipmentAsset();
        asset.setModelId(request.modelId());
        asset.setAssetCode(request.assetCode());
        asset.setSerialNo(request.serialNo());
        asset.setLabId(request.labId());
        asset.setDepartmentId(request.departmentId());
        asset.setLocationDetail(request.locationDetail());
        asset.setCustodianId(request.custodianId());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setPurchasePrice(request.purchasePrice());
        asset.setWarrantyUntil(request.warrantyUntil());
        asset.setStatus(EquipmentStatus.IDLE);
        asset.setEnabled(1);
        assetMapper.insert(asset);
    }

    @Override
    public void update(String id, AssetUpdateRequest request) {
        EquipmentAsset asset = requireAsset(Ids.parse(id));
        if (request.custodianId() != null) {
            SysUser custodian = userMapper.selectById(request.custodianId());
            if (custodian == null || custodian.getEnabled() != 1) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "保管人不存在或已停用");
            }
        }
        asset.setLocationDetail(request.locationDetail());
        asset.setCustodianId(request.custodianId());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setPurchasePrice(request.purchasePrice());
        asset.setWarrantyUntil(request.warrantyUntil());
        asset.setEnabled(request.enabled());
        assetMapper.updateById(asset);
    }

    @Override
    public void delete(String id) {
        EquipmentAsset asset = requireAsset(Ids.parse(id));
        assetMapper.deleteById(asset.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(String id, AssetTransferRequest request) {
        EquipmentAsset asset = assetMapper.selectByIdForUpdate(Ids.parse(id));
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "设备不存在");
        }
        if (asset.getStatus() != EquipmentStatus.IDLE) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "仅空闲设备允许迁移");
        }
        BizSpace toLab = requireLab(request.toLabId());
        if (Objects.equals(toLab.getId(), asset.getLabId())
                && Objects.equals(request.toLocation(), asset.getLocationDetail())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备已在目标位置");
        }
        EquipmentLocationHistory history = new EquipmentLocationHistory();
        history.setAssetId(asset.getId());
        history.setFromLabId(asset.getLabId());
        history.setToLabId(request.toLabId());
        history.setFromLocation(asset.getLocationDetail());
        history.setToLocation(request.toLocation());
        history.setMovedAt(Instant.now());
        history.setOperatorId(StpUtil.getLoginIdAsLong());
        history.setReason(request.reason());
        locationHistoryMapper.insert(history);

        asset.setLabId(request.toLabId());
        asset.setLocationDetail(request.toLocation());
        assetMapper.updateById(asset);
    }

    @Override
    public void classify(String id, AssetClassifyRequest request) {
        EquipmentAsset asset = requireAsset(Ids.parse(id));
        asset.setValueLevel(request.valueLevel());
        asset.setClassificationReason(request.classificationReason());
        asset.setClassifiedBy(StpUtil.getLoginIdAsLong());
        asset.setClassifiedAt(Instant.now());
        assetMapper.updateById(asset);
    }

    @Override
    public List<CustodianOptionVO> custodianOptions() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getEnabled, 1)
                        .orderByAsc(SysUser::getId))
                .stream()
                .map(user -> new CustodianOptionVO(String.valueOf(user.getId()), user.getRealName()))
                .toList();
    }

    private EquipmentAsset requireAsset(Long assetId) {
        EquipmentAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "设备不存在");
        }
        return asset;
    }

    private BizSpace requireLab(Long labId) {
        BizSpace lab = labMapper.selectById(labId);
        if (lab == null || lab.getStatus() == SpaceStatus.CLOSED) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "实验室不存在或已关闭");
        }
        return lab;
    }

    private SysDepartment requireDepartment(Long departmentId) {
        SysDepartment department = departmentMapper.selectById(departmentId);
        if (department == null || department.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "归口部门不存在或已停用");
        }
        return department;
    }

    private List<AssetVO> assemble(List<EquipmentAsset> assets) {
        if (assets.isEmpty()) {
            return List.of();
        }
        Map<Long, EquipmentModel> models = loadMap(
                assets.stream().map(EquipmentAsset::getModelId).collect(Collectors.toSet()),
                modelMapper::selectBatchIds,
                EquipmentModel::getId);
        Map<Long, BizSpace> labs = loadMap(
                assets.stream().map(EquipmentAsset::getLabId).filter(Objects::nonNull).collect(Collectors.toSet()),
                labMapper::selectBatchIds,
                BizSpace::getId);
        Map<Long, SysDepartment> departments = loadMap(
                assets.stream().map(EquipmentAsset::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet()),
                departmentMapper::selectBatchIds,
                SysDepartment::getId);
        Map<Long, SysUser> custodians = loadMap(
                assets.stream().map(EquipmentAsset::getCustodianId).filter(Objects::nonNull).collect(Collectors.toSet()),
                userMapper::selectBatchIds,
                SysUser::getId);
        return assets.stream()
                .map(asset -> {
                    EquipmentModel model = models.get(asset.getModelId());
                    BizSpace lab = labs.get(asset.getLabId());
                    SysDepartment department = departments.get(asset.getDepartmentId());
                    SysUser custodian = custodians.get(asset.getCustodianId());
                    return new AssetVO(
                            asset.getId().toString(),
                            asset.getAssetCode(),
                            asset.getSerialNo(),
                            asset.getModelId().toString(),
                            model != null ? model.getModelCode() : null,
                            model != null ? model.getEquipmentName() : null,
                            model != null ? model.getCategoryId().toString() : null,
                            asset.getLabId() == null ? null : asset.getLabId().toString(),
                            lab != null ? lab.getLabName() : null,
                            asset.getDepartmentId() == null ? null : asset.getDepartmentId().toString(),
                            department != null ? department.getDepartmentName() : null,
                            asset.getLocationDetail(),
                            asset.getCustodianId() == null ? null : asset.getCustodianId().toString(),
                            custodian != null ? custodian.getRealName() : null,
                            asset.getPurchaseDate(),
                            asset.getPurchasePrice(),
                            asset.getWarrantyUntil(),
                            asset.getValueLevel(),
                            asset.getStatus(),
                            asset.getEnabled()
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
