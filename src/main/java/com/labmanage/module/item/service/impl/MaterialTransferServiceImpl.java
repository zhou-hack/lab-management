package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.item.dto.MaterialCategoryCreateDTO;
import com.labmanage.module.item.dto.MaterialCategoryExportQuery;
import com.labmanage.module.item.dto.MaterialSkuCreateDTO;
import com.labmanage.module.item.dto.MaterialSkuExportQuery;
import com.labmanage.module.item.entity.MaterialCategory;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.mapper.MaterialCategoryMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.service.MaterialCategoryService;
import com.labmanage.module.item.service.MaterialSkuService;
import com.labmanage.module.item.service.MaterialTransferService;
import com.labmanage.module.item.transfer.BooleanParser;
import com.labmanage.module.item.transfer.CsvRowReader;
import com.labmanage.module.item.transfer.CsvRowWriter;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.ImportColumns;
import com.labmanage.module.item.transfer.MaterialTypeParser;
import com.labmanage.module.item.transfer.RowReader;
import com.labmanage.module.item.transfer.RowWriter;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.transfer.XlsxRowReader;
import com.labmanage.module.item.transfer.XlsxRowWriter;
import com.labmanage.module.item.util.IdUtils;
import com.labmanage.module.item.vo.ImportResultVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物料批量导入/导出服务实现
 * <p>
 * 导入逐行复用既有 {@link MaterialSkuService#create} / {@link MaterialCategoryService#create}，
 * 失败原因与手工新增一致；编码冲突/非法行跳过并记入失败明细，逐行独立提交、不整批回滚。
 * 导出在内存生成完整文件字节后再返回，异常发生在 HTTP 响应提交之前。
 */
@Service
public class MaterialTransferServiceImpl implements MaterialTransferService {

    /** 单次导入数据行上限 */
    private static final int MAX_IMPORT_ROWS = 5000;

    /** 单次导出行数上限 */
    private static final int MAX_EXPORT_ROWS = 10000;

    private static final DateTimeFormatter STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));

    private static final DateTimeFormatter EXPORT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final MaterialSkuService skuService;
    private final MaterialCategoryService categoryService;
    private final MaterialSkuMapper skuMapper;
    private final MaterialCategoryMapper categoryMapper;

    public MaterialTransferServiceImpl(MaterialSkuService skuService,
                                       MaterialCategoryService categoryService,
                                       MaterialSkuMapper skuMapper,
                                       MaterialCategoryMapper categoryMapper) {
        this.skuService = skuService;
        this.categoryService = categoryService;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
    }

    // ==================== 导入 ====================

    @Override
    public ImportResultVO importSkus(MultipartFile file) {
        List<List<String>> rows = readRows(file);
        ImportColumns.checkHeader(rows.isEmpty() ? null : rows.get(0), ImportColumns.SKU_HEADER);
        int total = 0;
        int success = 0;
        List<ImportResultVO.Failure> failures = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            total++;
            int rowNo = i + 1;
            try {
                MaterialCategory category = resolveCategory(cell(row, 0, "分类编码"), 64);
                String skuCode = requireText(cell(row, 1, "SKU码"), 64, "SKU 码");
                if (!seenCodes.add(skuCode)) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(), "文件内 SKU 码重复：" + skuCode);
                }
                String materialName = requireText(cell(row, 2, "物料名称"), 128, "物料名称");
                String specification = optionalText(cell(row, 3, "规格型号"), 500, "规格型号");
                String baseUnit = requireText(cell(row, 4, "计量单位"), 16, "计量单位");
                int quantityScale = requireScale(cell(row, 5, "数量精度"), "数量精度");
                MaterialType materialType = MaterialTypeParser.parse(cell(row, 6, "物料类型"));
                boolean hazardous = BooleanParser.parse(cell(row, 7, "危险品"), "危险品");
                boolean valuable = BooleanParser.parse(cell(row, 8, "高价值"), "高价值");
                skuService.create(new MaterialSkuCreateDTO(
                        String.valueOf(category.getId()), skuCode, materialName, specification,
                        baseUnit, quantityScale, materialType, hazardous, valuable));
                success++;
            } catch (BusinessException e) {
                failures.add(new ImportResultVO.Failure(rowNo, e.getMessage()));
            }
        }
        return new ImportResultVO(total, success, failures.size(), failures);
    }

    @Override
    public ImportResultVO importCategories(MultipartFile file) {
        List<List<String>> rows = readRows(file);
        ImportColumns.checkHeader(rows.isEmpty() ? null : rows.get(0), ImportColumns.CATEGORY_HEADER);
        int total = 0;
        int success = 0;
        List<ImportResultVO.Failure> failures = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            total++;
            int rowNo = i + 1;
            try {
                String categoryCode = requireText(cell(row, 0, "分类编码"), 64, "分类编码");
                if (!seenCodes.add(categoryCode)) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(), "文件内分类编码重复：" + categoryCode);
                }
                String categoryName = requireText(cell(row, 1, "分类名称"), 128, "分类名称");
                String parentCode = optionalText(cell(row, 2, "父分类编码"), 64, "父分类编码");
                String parentId = null;
                if (parentCode != null) {
                    MaterialCategory parent = selectCategoryByCode(parentCode.trim());
                    if (parent == null) {
                        throw new BusinessException(ResultCode.NOT_FOUND.getCode(),
                                "父分类编码不存在：" + parentCode.trim());
                    }
                    parentId = String.valueOf(parent.getId());
                }
                categoryService.create(new MaterialCategoryCreateDTO(categoryCode, categoryName, parentId));
                success++;
            } catch (BusinessException e) {
                failures.add(new ImportResultVO.Failure(rowNo, e.getMessage()));
            }
        }
        return new ImportResultVO(total, success, failures.size(), failures);
    }

    // ==================== 导出 ====================

    @Override
    public TransferFile exportSkus(MaterialSkuExportQuery query) {
        FileFormat format = formatOf(query.getFormat());
        LambdaQueryWrapper<MaterialSku> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().trim();
            wrapper.and(q -> q.likeRight(MaterialSku::getSkuCode, keyword)
                    .or()
                    .like(MaterialSku::getMaterialName, keyword)
                    .or()
                    .like(MaterialSku::getSpecification, keyword));
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
        if (skuMapper.selectCount(wrapper) > MAX_EXPORT_ROWS) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "导出行数超过上限（" + MAX_EXPORT_ROWS + "），请缩小筛选范围");
        }
        List<MaterialSku> skus = skuMapper.selectList(wrapper);
        Map<Long, MaterialCategory> categories = skus.isEmpty() ? Map.of()
                : categoryMapper.selectByIds(skus.stream().map(MaterialSku::getCategoryId).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(MaterialCategory::getId, Function.identity()));

        List<String[]> lines = new ArrayList<>(skus.size() + 1);
        lines.add(ImportColumns.SKU_HEADER.toArray(new String[0]));
        for (MaterialSku sku : skus) {
            MaterialCategory category = categories.get(sku.getCategoryId());
            lines.add(new String[]{
                    category == null ? "" : category.getCategoryCode(),
                    sku.getSkuCode(),
                    sku.getMaterialName(),
                    nullToEmpty(sku.getSpecification()),
                    sku.getBaseUnit(),
                    String.valueOf(sku.getQuantityScale()),
                    sku.getMaterialType() == MaterialType.CONSUMABLE ? "耗材" : "可回收",
                    flag(sku.getIsHazardous()),
                    flag(sku.getIsValuable()),
                    category == null ? "" : category.getCategoryName(),
                    enabledText(sku.getEnabled()),
                    timeText(sku.getCreateTime()),
                    timeText(sku.getUpdateTime())
            });
        }
        String fileName = "物料档案导出_" + STAMP_FORMAT.format(Instant.now()) + "." + format.extension();
        return new TransferFile(fileName, format.contentType(), writeLines(format, lines));
    }

    @Override
    public TransferFile exportCategories(MaterialCategoryExportQuery query) {
        FileFormat format = formatOf(query.getFormat());
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
        if (categoryMapper.selectCount(wrapper) > MAX_EXPORT_ROWS) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "导出行数超过上限（" + MAX_EXPORT_ROWS + "），请缩小筛选范围");
        }
        List<MaterialCategory> list = categoryMapper.selectList(wrapper);
        Map<Long, MaterialCategory> byId = list.stream()
                .collect(Collectors.toMap(MaterialCategory::getId, Function.identity()));

        List<String[]> lines = new ArrayList<>(list.size() + 1);
        lines.add(ImportColumns.CATEGORY_HEADER.toArray(new String[0]));
        for (MaterialCategory category : list) {
            MaterialCategory parent = category.getParentId() == null ? null : byId.get(category.getParentId());
            lines.add(new String[]{
                    category.getCategoryCode(),
                    category.getCategoryName(),
                    category.getParentId() == null
                            ? ""
                            : (parent == null ? "" : parent.getCategoryCode()),
                    enabledText(category.getEnabled()),
                    timeText(category.getCreateTime()),
                    timeText(category.getUpdateTime())
            });
        }
        String fileName = "物料分类导出_" + STAMP_FORMAT.format(Instant.now()) + "." + format.extension();
        return new TransferFile(fileName, format.contentType(), writeLines(format, lines));
    }

    // ==================== 模板 ====================

    @Override
    public TransferFile skuImportTemplate(FileFormat format) {
        List<String[]> lines = List.<String[]>of(ImportColumns.SKU_HEADER.toArray(new String[0]));
        return new TransferFile("物料导入模板." + format.extension(), format.contentType(),
                writeLines(format, lines));
    }

    @Override
    public TransferFile categoryImportTemplate(FileFormat format) {
        List<String[]> lines = List.<String[]>of(ImportColumns.CATEGORY_HEADER.toArray(new String[0]));
        return new TransferFile("物料分类导入模板." + format.extension(), format.contentType(),
                writeLines(format, lines));
    }

    // ==================== 私有工具 ====================

    private List<List<String>> readRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "导入文件不能为空");
        }
        FileFormat format = FileFormat.fromFileName(file.getOriginalFilename());
        RowReader reader = format == FileFormat.XLSX ? new XlsxRowReader() : new CsvRowReader();
        try {
            return reader.read(file.getInputStream(), MAX_IMPORT_ROWS);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "读取导入文件失败");
        }
    }

    private MaterialCategory resolveCategory(String raw, int maxLength) {
        String code = requireText(raw, maxLength, "分类编码");
        MaterialCategory category = selectCategoryByCode(code);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "分类编码不存在：" + code);
        }
        return category;
    }

    private MaterialCategory selectCategoryByCode(String categoryCode) {
        return categoryMapper.selectOne(new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getCategoryCode, categoryCode));
    }

    private String requireText(String raw, int maxLength, String fieldName) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), fieldName + "不能为空");
        }
        if (value.length() > maxLength) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    fieldName + "不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private String optionalText(String raw, int maxLength, String fieldName) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    fieldName + "不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private int requireScale(String raw, String fieldName) {
        String value = raw == null ? "" : raw.trim();
        int scale;
        try {
            scale = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    fieldName + "必须为 0-3 的整数，识别到「" + value + "」");
        }
        if (scale < 0 || scale > 3) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    fieldName + "必须为 0-3 的整数，识别到「" + value + "」");
        }
        return scale;
    }

    private boolean isBlankRow(List<String> row) {
        return row == null || row.stream().allMatch(c -> c == null || c.isBlank());
    }

    private String cell(List<String> row, int index, String name) {
        if (index >= row.size()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), name + "不能为空");
        }
        return row.get(index);
    }

    private FileFormat formatOf(String format) {
        if ("xlsx".equalsIgnoreCase(format)) {
            return FileFormat.XLSX;
        }
        if ("csv".equalsIgnoreCase(format)) {
            return FileFormat.CSV;
        }
        throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "导出格式只能为 xlsx 或 csv");
    }

    private byte[] writeLines(FileFormat format, List<String[]> lines) {
        try (RowWriter writer = format == FileFormat.XLSX ? new XlsxRowWriter() : new CsvRowWriter()) {
            for (String[] line : lines) {
                writer.writeRow(line);
            }
            return writer.toBytes();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "生成导出文件失败");
        }
    }

    private String flag(Integer value) {
        return value != null && value == 1 ? "是" : "否";
    }

    private String enabledText(Integer enabled) {
        return enabled != null && enabled == 1 ? "启用" : "停用";
    }

    private String timeText(Instant time) {
        return time == null ? "" : EXPORT_TIME_FORMAT.format(time);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
