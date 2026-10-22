package com.labmanage.module.equipment.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.equipment.dto.AssetCreateRequest;
import com.labmanage.module.equipment.dto.AssetPageQuery;
import com.labmanage.module.equipment.dto.AssetTransferRequest;
import com.labmanage.module.equipment.dto.AssetUpdateRequest;
import com.labmanage.module.equipment.entity.EquipmentAsset;
import com.labmanage.module.equipment.entity.EquipmentLocationHistory;
import com.labmanage.module.equipment.entity.EquipmentModel;
import com.labmanage.module.equipment.enums.EquipmentStatus;
import com.labmanage.module.equipment.enums.ValueLevel;
import com.labmanage.module.equipment.mapper.EquipmentAssetMapper;
import com.labmanage.module.equipment.mapper.EquipmentLocationHistoryMapper;
import com.labmanage.module.equipment.mapper.EquipmentModelMapper;
import com.labmanage.module.equipment.service.AssetService;
import com.labmanage.module.equipment.vo.AssetImportResultVO;
import com.labmanage.module.equipment.vo.AssetVO;
import com.labmanage.module.equipment.vo.CustodianOptionVO;
import com.labmanage.roles.department.entity.SysDepartment;
import com.labmanage.roles.department.mapper.SysDepartmentMapper;
import com.labmanage.roles.user.entity.SysUser;
import com.labmanage.roles.user.mapper.SysUserMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final SysDepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;

    public AssetServiceImpl(EquipmentAssetMapper assetMapper,
                            EquipmentModelMapper modelMapper,
                            EquipmentLocationHistoryMapper locationHistoryMapper,
                            SysDepartmentMapper departmentMapper,
                            SysUserMapper userMapper) {
        this.assetMapper = assetMapper;
        this.modelMapper = modelMapper;
        this.locationHistoryMapper = locationHistoryMapper;
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<AssetVO> page(AssetPageQuery query) {
        LambdaQueryWrapper<EquipmentAsset> wrapper = buildWrapper(query, StpUtil.hasPermission(PERM_ASSET_UPDATE));
        Page<EquipmentAsset> page = assetMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(assemble(page.getRecords()), page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public void create(AssetCreateRequest request) {
        EquipmentModel model = modelMapper.selectById(request.modelId());
        if (model == null || model.getEnabled() != 1) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备型号不存在或已停用");
        }
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
        if (asset.getStatus() == EquipmentStatus.IN_USE) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备借出中，禁止删除；请先登记归还");
        }
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
        if (Objects.equals(request.toLocation(), asset.getLocationDetail())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "设备已在目标位置");
        }
        EquipmentLocationHistory history = new EquipmentLocationHistory();
        history.setAssetId(asset.getId());
        history.setFromLocation(asset.getLocationDetail());
        history.setToLocation(request.toLocation());
        history.setMovedAt(Instant.now());
        history.setOperatorId(StpUtil.getLoginIdAsLong());
        history.setReason(request.reason());
        locationHistoryMapper.insert(history);

        asset.setLocationDetail(request.toLocation());
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

    /* ---------------- Excel 导入导出 ---------------- */

    /** 导出表头（导入按表头列名解析，导出文件即导入模板） */
    private static final String[] EXPORT_HEADERS = {
            "资产编号", "序列号", "型号码", "设备名称", "品牌", "规格说明",
            "归口部门", "存放位置", "保管人", "购置日期", "购置价格(元)", "保修截止",
            "价值分级", "状态", "启用", "入账时间(UTC)"
    };

    /** 导入列宽（与 EXPORT_HEADERS 顺序一致） */
    private static final int[] EXPORT_WIDTHS = {
            16, 18, 20, 22, 14, 26, 18, 16, 12, 12, 14, 12, 10, 10, 8, 20
    };

    /** 导入状态文案/枚举名 → 枚举 */
    private static final Map<String, EquipmentStatus> STATUS_BY_TEXT = Map.of(
            "空闲", EquipmentStatus.IDLE, "IDLE", EquipmentStatus.IDLE,
            "维护中", EquipmentStatus.MAINTAINING, "MAINTAINING", EquipmentStatus.MAINTAINING,
            "已报废", EquipmentStatus.SCRAPPED, "SCRAPPED", EquipmentStatus.SCRAPPED
    );

    /** 导入价值分级文案/枚举名 → 枚举 */
    private static final Map<String, ValueLevel> VALUE_LEVEL_BY_TEXT = Map.of(
            "普通", ValueLevel.NORMAL, "NORMAL", ValueLevel.NORMAL,
            "贵重", ValueLevel.VALUABLE, "VALUABLE", ValueLevel.VALUABLE
    );

    /** 单次导入行数上限（xlsx 默认上传体积约束下已足够，防止误传整库） */
    private static final int IMPORT_MAX_ROWS = 1000;

    private static final DateTimeFormatter EXCEL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** UTC 带时区格式器（入账时间列输出 UTC 时间点文本） */
    private static final DateTimeFormatter EXCEL_DATETIME_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @Override
    public byte[] export(AssetPageQuery query) {
        LambdaQueryWrapper<EquipmentAsset> wrapper = buildWrapper(query, StpUtil.hasPermission(PERM_ASSET_UPDATE));
        List<EquipmentAsset> assets = assetMapper.selectList(wrapper);
        List<AssetVO> rows = assemble(assets);
        Map<String, Instant> createTimeById = assets.stream()
                .collect(Collectors.toMap(a -> a.getId().toString(), EquipmentAsset::getCreateTime));
        Map<Long, EquipmentModel> models = loadMap(
                rows.stream().map(AssetVO::modelId).filter(Objects::nonNull).map(Long::parseLong)
                        .collect(Collectors.toSet()),
                modelMapper::selectBatchIds,
                EquipmentModel::getId);
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("设备台账");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
            Row header = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                sheet.setColumnWidth(i, EXPORT_WIDTHS[i] * 256);
                Cell cell = header.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowIdx = 1;
            for (AssetVO row : rows) {
                EquipmentModel model = row.modelId() == null ? null : models.get(Long.parseLong(row.modelId()));
                Instant createTime = row.id() == null ? null : createTimeById.get(row.id());
                Cell[] cells = writeRow(sheet, rowIdx, row, createTime);
                writeModelContext(cells, 4, model);
                if (row.purchasePrice() != null) {
                    cells[10].setCellValue(row.purchasePrice().doubleValue());
                    cells[10].setCellStyle(moneyStyle);
                }
                rowIdx++;
            }
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "导出失败，请稍后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetImportResultVO importAssets(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "导入文件为空");
        }
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getRow(0) == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "Excel 无有效工作表或缺少表头，请先「导出」一份文件作为模板");
            }
            if (sheet.getLastRowNum() > IMPORT_MAX_ROWS) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "单次最多导入 " + IMPORT_MAX_ROWS + " 行，请分批导入");
            }
            Map<String, Integer> columns = headerIndex(sheet.getRow(0));
            List<String> missing = new ArrayList<>();
            for (String required : List.of("资产编号", "型号码", "归口部门")) {
                if (!columns.containsKey(required)) {
                    missing.add(required);
                }
            }
            if (!missing.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "表头缺少必需列：" + String.join("、", missing) + "，请以「导出」的文件为模板");
            }

            // 参照数据（仅启用记录，与页面新增口径一致）
            Map<String, EquipmentModel> models = modelMapper.selectList(new LambdaQueryWrapper<EquipmentModel>()
                            .eq(EquipmentModel::getEnabled, 1))
                    .stream().collect(Collectors.toMap(EquipmentModel::getModelCode, Function.identity()));
            Map<String, List<SysDepartment>> departments = departmentMapper.selectList(
                            new LambdaQueryWrapper<SysDepartment>().eq(SysDepartment::getEnabled, 1))
                    .stream().collect(Collectors.groupingBy(SysDepartment::getDepartmentName));
            Map<String, List<SysUser>> custodians = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getEnabled, 1))
                    .stream().collect(Collectors.groupingBy(SysUser::getRealName));

            List<AssetImportResultVO.RowError> errors = new ArrayList<>();
            List<EquipmentAsset> valid = new ArrayList<>();
            List<Integer> validRows = new ArrayList<>();
            Set<String> fileCodes = new HashSet<>();
            int total = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isBlankRow(row)) {
                    continue;
                }
                total++;
                int excelRow = r + 1;
                String code = text(row, columns, "资产编号");
                String modelCode = text(row, columns, "型号码");
                String deptName = text(row, columns, "归口部门");
                if (!StringUtils.hasText(code) || !StringUtils.hasText(modelCode) || !StringUtils.hasText(deptName)) {
                    errors.add(new AssetImportResultVO.RowError(excelRow, "资产编号、型号码、归口部门为必填"));
                    continue;
                }
                if (fileCodes.contains(code)) {
                    errors.add(new AssetImportResultVO.RowError(excelRow, "文件内资产编号重复：" + code));
                    continue;
                }
                EquipmentModel model = models.get(modelCode);
                if (model == null) {
                    errors.add(new AssetImportResultVO.RowError(excelRow, "型号码不存在或已停用：" + modelCode));
                    continue;
                }
                Long departmentId = uniqueId(departments, deptName, SysDepartment::getId);
                if (departmentId == null) {
                    errors.add(new AssetImportResultVO.RowError(excelRow, "归口部门不存在或名称不唯一：" + deptName));
                    continue;
                }
                String custodianName = text(row, columns, "保管人");
                Long custodianId = null;
                if (StringUtils.hasText(custodianName)) {
                    custodianId = uniqueId(custodians, custodianName, SysUser::getId);
                    if (custodianId == null) {
                        errors.add(new AssetImportResultVO.RowError(excelRow,
                                "保管人不存在或姓名不唯一：" + custodianName));
                        continue;
                    }
                }
                LocalDate purchaseDate;
                try {
                    purchaseDate = dateCell(row, columns, "购置日期");
                } catch (RuntimeException e) {
                    errors.add(new AssetImportResultVO.RowError(excelRow, e.getMessage()));
                    continue;
                }
                LocalDate warrantyUntil;
                try {
                    warrantyUntil = dateCell(row, columns, "保修截止");
                } catch (RuntimeException e) {
                    errors.add(new AssetImportResultVO.RowError(excelRow, e.getMessage()));
                    continue;
                }
                BigDecimal price;
                try {
                    price = moneyCell(row, columns, "购置价格(元)");
                } catch (RuntimeException e) {
                    errors.add(new AssetImportResultVO.RowError(excelRow, e.getMessage()));
                    continue;
                }
                String statusText = text(row, columns, "状态");
                EquipmentStatus status = StringUtils.hasText(statusText)
                        ? STATUS_BY_TEXT.get(statusText) : EquipmentStatus.IDLE;
                if (status == null) {
                    errors.add(new AssetImportResultVO.RowError(excelRow,
                            "状态应为 空闲/维护中/已报废：" + statusText));
                    continue;
                }
                String levelText = text(row, columns, "价值分级");
                ValueLevel valueLevel = null;
                if (StringUtils.hasText(levelText)) {
                    valueLevel = VALUE_LEVEL_BY_TEXT.get(levelText);
                    if (valueLevel == null) {
                        errors.add(new AssetImportResultVO.RowError(excelRow,
                                "价值分级应为 普通/贵重：" + levelText));
                        continue;
                    }
                }
                Integer enabled = parseEnabled(text(row, columns, "启用"));
                if (enabled == null) {
                    errors.add(new AssetImportResultVO.RowError(excelRow, "启用应为 启用/停用 或 1/0"));
                    continue;
                }

                EquipmentAsset asset = new EquipmentAsset();
                asset.setModelId(model.getId());
                asset.setAssetCode(code);
                asset.setSerialNo(text(row, columns, "序列号"));
                asset.setDepartmentId(departmentId);
                asset.setLocationDetail(text(row, columns, "存放位置"));
                asset.setCustodianId(custodianId);
                asset.setPurchaseDate(purchaseDate);
                asset.setPurchasePrice(price);
                asset.setWarrantyUntil(warrantyUntil);
                asset.setValueLevel(valueLevel);
                asset.setStatus(status);
                asset.setEnabled(enabled);
                valid.add(asset);
                validRows.add(excelRow);
                fileCodes.add(code);
            }

            // 与库内已有资产编号查重（分批 IN，避免超长 SQL）
            Set<String> existing = new HashSet<>();
            List<String> codes = valid.stream().map(EquipmentAsset::getAssetCode).toList();
            for (int i = 0; i < codes.size(); i += 500) {
                assetMapper.selectList(new LambdaQueryWrapper<EquipmentAsset>()
                                .in(EquipmentAsset::getAssetCode, codes.subList(i, Math.min(i + 500, codes.size()))))
                        .forEach(a -> existing.add(a.getAssetCode()));
            }
            int success = 0;
            for (int i = 0; i < valid.size(); i++) {
                EquipmentAsset asset = valid.get(i);
                if (existing.contains(asset.getAssetCode())) {
                    errors.add(new AssetImportResultVO.RowError(validRows.get(i),
                            "资产编号已存在：" + asset.getAssetCode()));
                } else {
                    assetMapper.insert(asset);
                    success++;
                }
            }
            return new AssetImportResultVO(
                    total,
                    success,
                    total - success,
                    errors);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "文件解析失败，请使用「导出」的 xlsx 文件作为模板");
        }
    }

    private LambdaQueryWrapper<EquipmentAsset> buildWrapper(AssetPageQuery query, boolean maintainer) {
        LambdaQueryWrapper<EquipmentAsset> wrapper = new LambdaQueryWrapper<>();
        if (maintainer) {
            if (query.getStatus() != null) {
                wrapper.eq(EquipmentAsset::getStatus, query.getStatus());
            }
        } else {
            // 无维护权限：强制收敛为「在架」目录视图
            wrapper.eq(EquipmentAsset::getStatus, EquipmentStatus.IDLE)
                    .eq(EquipmentAsset::getEnabled, 1);
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
        return wrapper;
    }

    private EquipmentAsset requireAsset(Long assetId) {
        EquipmentAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "设备不存在");
        }
        return asset;
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

    /* ---------------- Excel 读写辅助 ---------------- */

    /** 表头行：列名 → 列下标（重复列名取首个） */
    private Map<String, Integer> headerIndex(Row header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = header.getFirstCellNum(); i >= 0 && i < header.getLastCellNum(); i++) {
            String name = cellText(header.getCell(i));
            if (StringUtils.hasText(name)) {
                columns.putIfAbsent(name, i);
            }
        }
        return columns;
    }

    private boolean isBlankRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = row.getFirstCellNum(); i >= 0 && i < row.getLastCellNum(); i++) {
            if (StringUtils.hasText(cellText(row.getCell(i)))) {
                return false;
            }
        }
        return true;
    }

    private String text(Row row, Map<String, Integer> columns, String name) {
        Integer idx = columns.get(name);
        return idx == null ? null : cellText(row.getCell(idx));
    }

    /** 单元格取文本：数字去掉无意义小数尾（123.0 → 123），空单元格返回 null */
    private String cellText(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC ->
                        BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> null;
            };
            default -> null;
        };
    }

    /** 名称 → 唯一 ID；不存在或名称对应多条启用记录时返回 null（调用方报行级错误） */
    private <T> Long uniqueId(Map<String, List<T>> groups, String name, Function<T, Long> idGetter) {
        List<T> matches = groups.get(name);
        if (matches == null || matches.size() != 1) {
            return null;
        }
        return idGetter.apply(matches.get(0));
    }

    /** 日期列：Excel 日期单元格或 yyyy-MM-dd 文本；空白返回 null，非法格式抛出说明性异常 */
    private LocalDate dateCell(Row row, Map<String, Integer> columns, String name) {
        String raw = text(row, columns, name);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Integer idx = columns.get(name);
        Cell cell = row.getCell(idx);
        if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        try {
            return LocalDate.parse(raw, EXCEL_DATE);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(name + "格式应为 yyyy-MM-dd：" + raw);
        }
    }

    /** 金额列：Excel 数字或文本数值，两位小数；空白返回 null，负数/非法值抛说明性异常 */
    private BigDecimal moneyCell(Row row, Map<String, Integer> columns, String name) {
        String raw = text(row, columns, name);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + "不是有效数值：" + raw);
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + "不能为负数：" + raw);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 启用列：启用/1 → 1，停用/0 → 0，空白默认 1，其余非法 */
    private Integer parseEnabled(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 1;
        }
        return switch (raw) {
            case "启用", "1" -> 1;
            case "停用", "0" -> 0;
            default -> null;
        };
    }

    /** 写一条台账行（品牌/规格说明由 writeModelContext 补写） */
    private Cell[] writeRow(Sheet sheet, int rowIdx, AssetVO row, Instant createTime) {
        Row line = sheet.createRow(rowIdx);
        Cell[] cells = new Cell[EXPORT_HEADERS.length];
        for (int i = 0; i < EXPORT_HEADERS.length; i++) {
            cells[i] = line.createCell(i);
        }
        cells[0].setCellValue(nvl(row.assetCode()));
        cells[1].setCellValue(nvl(row.serialNo()));
        cells[2].setCellValue(nvl(row.modelCode()));
        cells[3].setCellValue(nvl(row.equipmentName()));
        cells[6].setCellValue(nvl(row.departmentName()));
        cells[7].setCellValue(nvl(row.locationDetail()));
        cells[8].setCellValue(nvl(row.custodianName()));
        if (row.purchaseDate() != null) {
            cells[9].setCellValue(row.purchaseDate().toString());
        }
        if (row.warrantyUntil() != null) {
            cells[11].setCellValue(row.warrantyUntil().toString());
        }
        cells[12].setCellValue(row.valueLevel() == null ? "待分类"
                : row.valueLevel() == ValueLevel.NORMAL ? "普通" : "贵重");
        cells[13].setCellValue(row.status() == EquipmentStatus.IDLE ? "空闲"
                : row.status() == EquipmentStatus.MAINTAINING ? "维护中" : "已报废");
        cells[14].setCellValue(row.enabled() != null && row.enabled() == 1 ? "启用" : "停用");
        if (createTime != null) {
            cells[15].setCellValue(EXCEL_DATETIME_UTC.format(createTime));
        }
        return cells;
    }

    private void writeModelContext(Cell[] cells, int brandIdx, EquipmentModel model) {
        cells[brandIdx].setCellValue(model == null ? "" : nvl(model.getBrand()));
        cells[brandIdx + 1].setCellValue(model == null ? "" : nvl(model.getSpecification()));
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
