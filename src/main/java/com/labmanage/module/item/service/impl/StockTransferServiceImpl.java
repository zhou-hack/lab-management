package com.labmanage.module.item.service.impl;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.item.dto.StockBalanceExportQuery;
import com.labmanage.module.item.dto.StockDocumentCreateDTO;
import com.labmanage.module.item.dto.StockDocumentLineCreateDTO;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.service.StockTransferService;
import com.labmanage.module.item.transfer.CsvRowReader;
import com.labmanage.module.item.transfer.CsvRowWriter;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.ImportColumns;
import com.labmanage.module.item.transfer.RowReader;
import com.labmanage.module.item.transfer.RowWriter;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.transfer.XlsxRowReader;
import com.labmanage.module.item.transfer.XlsxRowWriter;
import com.labmanage.module.item.vo.ImportResultVO;
import com.labmanage.module.item.vo.StockBalanceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labmanage.module.item.entity.StockBalance;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存余额批量导入/导出服务实现
 * <p>
 * 库存数量受「余额可从流水重建」审计约束，期初导入不直接改写余额：
 * 逐行校验（库位/SKU 存在且启用、组合当前无余额行、数量与单价精度合法）后，
 * 有效行组装为一张 OPENING 期初入库草稿单，走既有审批过账流程入账。
 */
@Service
public class StockTransferServiceImpl implements StockTransferService {

    /** 单次导入数据行上限 */
    private static final int MAX_IMPORT_ROWS = 5000;

    /** 单次导出行数上限 */
    private static final int MAX_EXPORT_ROWS = 10000;

    private static final DateTimeFormatter STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"));

    /** 导出展示用上海时区（业务时区约定，显式 ZoneId 不依赖系统默认） */
    private static final DateTimeFormatter SHANGHAI_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final StockQueryService stockQueryService;
    private final StockDocumentService stockDocumentService;
    private final StockBalanceMapper balanceMapper;
    private final StockLocationMapper locationMapper;
    private final MaterialSkuMapper skuMapper;

    public StockTransferServiceImpl(StockQueryService stockQueryService,
                                     StockDocumentService stockDocumentService,
                                     StockBalanceMapper balanceMapper,
                                     StockLocationMapper locationMapper,
                                     MaterialSkuMapper skuMapper) {
        this.stockQueryService = stockQueryService;
        this.stockDocumentService = stockDocumentService;
        this.balanceMapper = balanceMapper;
        this.locationMapper = locationMapper;
        this.skuMapper = skuMapper;
    }

    // ==================== 期初余额导入 ====================

    @Override
    public ImportResultVO importOpeningBalances(MultipartFile file) {
        List<List<String>> rows = readRows(file);
        ImportColumns.checkHeader(rows.isEmpty() ? null : rows.get(0), ImportColumns.BALANCE_OPENING_HEADER);
        int total = 0;
        List<ImportResultVO.Failure> failures = new ArrayList<>();
        Set<String> seenPairs = new HashSet<>();
        List<StockDocumentLineCreateDTO> validLines = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            total++;
            int rowNo = i + 1;
            try {
                String locationCode = requireText(cell(row, 0, "库位编码"), 64, "库位编码");
                String skuCode = requireText(cell(row, 1, "SKU码"), 64, "SKU 码");
                BigDecimal quantity = requireQuantity(cell(row, 2, "数量"), "数量");
                BigDecimal unitPrice = optionalPrice(cell(row, 3, "单价"));

                StockLocation location = selectLocationByCode(locationCode);
                if (location == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "库位编码不存在：" + locationCode);
                }
                if (location.getEnabled() == null || location.getEnabled() != 1) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "库位已停用：" + locationCode);
                }
                MaterialSku sku = selectSkuByCode(skuCode);
                if (sku == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "SKU 码不存在：" + skuCode);
                }
                if (sku.getEnabled() == null || sku.getEnabled() != 1) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "物料已停用：" + skuCode);
                }
                if (quantity.stripTrailingZeros().scale() > sku.getQuantityScale()) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                            "数量精度超过物料限制（最多 " + sku.getQuantityScale() + " 位小数）");
                }
                Long hasBalance = balanceMapper.selectCount(new LambdaQueryWrapper<StockBalance>()
                        .eq(StockBalance::getLocationId, location.getId())
                        .eq(StockBalance::getSkuId, sku.getId()));
                if (hasBalance != null && hasBalance > 0) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(),
                            "该库位已存在此物料的余额记录，请通过单据调整库存：" + locationCode + " / " + skuCode);
                }
                // 校验全部通过后再登记组合：失败行不占用去重集合
                if (!seenPairs.add(locationCode + "\n" + skuCode)) {
                    throw new BusinessException(ResultCode.CONFLICT.getCode(),
                            "文件内库位+SKU 组合重复：" + locationCode + " / " + skuCode);
                }
                validLines.add(new StockDocumentLineCreateDTO(
                        String.valueOf(sku.getId()), null, String.valueOf(location.getId()), quantity, unitPrice));
            } catch (BusinessException e) {
                failures.add(new ImportResultVO.Failure(rowNo, e.getMessage()));
            }
        }
        String documentId = null;
        if (!validLines.isEmpty()) {
            StockDocumentCreateDTO dto = new StockDocumentCreateDTO(
                    MovementType.OPENING, OffsetDateTime.now(ZoneOffset.UTC), null, null,
                    "期初批量导入：" + validLines.size() + " 行", validLines);
            documentId = stockDocumentService.create(dto);
        }
        return new ImportResultVO(total, validLines.size(), failures.size(), failures, documentId);
    }

    // ==================== 余额导出 ====================

    @Override
    public TransferFile exportBalances(StockBalanceExportQuery query) {
        FileFormat format = formatOf(query.getFormat());
        List<StockBalanceVO> balances = stockQueryService.listBalanceForExport(query);
        if (balances.size() > MAX_EXPORT_ROWS) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "导出行数超过上限（" + MAX_EXPORT_ROWS + "），请缩小筛选范围");
        }
        Set<Long> locationIds = balances.stream()
                .map(vo -> Long.valueOf(vo.locationId())).collect(Collectors.toSet());
        Map<Long, StockLocation> locationMap = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectByIds(locationIds).stream()
                        .collect(Collectors.toMap(StockLocation::getId, Function.identity()));

        List<String[]> lines = new ArrayList<>(balances.size() + 1);
        lines.add(headerRow("库位编码", "SKU码", "物料名称", "库存量", "安全库存", "补货目标", "移动均价", "预警状态"));
        for (StockBalanceVO vo : balances) {
            StockLocation location = locationMap.get(Long.valueOf(vo.locationId()));
            lines.add(new String[]{
                    location == null ? "" : nullToEmpty(location.getLocationCode()),
                    nullToEmpty(vo.skuCode()),
                    nullToEmpty(vo.skuName()),
                    qtyText(vo.stockQty()),
                    qtyText(vo.safeQty()),
                    qtyText(vo.targetQty()),
                    priceText(vo.movingAveragePrice()),
                    warningText(vo.warningLevel())
            });
        }
        String fileName = "库存余额导出_" + STAMP_FORMAT.format(Instant.now()) + "." + format.extension();
        return new TransferFile(fileName, format.contentType(), writeLines(format, lines));
    }

    @Override
    public TransferFile openingImportTemplate(FileFormat format) {
        List<String[]> lines = List.<String[]>of(ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]));
        return new TransferFile("期初余额导入模板." + format.extension(), format.contentType(),
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

    private StockLocation selectLocationByCode(String locationCode) {
        return locationMapper.selectOne(new LambdaQueryWrapper<StockLocation>()
                .eq(StockLocation::getLocationCode, locationCode));
    }

    private MaterialSku selectSkuByCode(String skuCode) {
        return skuMapper.selectOne(new LambdaQueryWrapper<MaterialSku>()
                .eq(MaterialSku::getSkuCode, skuCode));
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

    private BigDecimal requireQuantity(String raw, String fieldName) {
        BigDecimal value = parseDecimal(raw, fieldName);
        if (value.signum() <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), fieldName + "必须大于 0");
        }
        checkDigits(value, 15, 3, fieldName);
        return value;
    }

    private BigDecimal optionalPrice(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        BigDecimal price = parseDecimal(value, "单价");
        if (price.signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "单价不能为负");
        }
        checkDigits(price, 15, 4, "单价");
        return price;
    }

    private BigDecimal parseDecimal(String raw, String fieldName) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), fieldName + "不能为空");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    fieldName + "必须为数字，识别到「" + value + "」");
        }
    }

    /** 整数位 / 小数位上限校验（对齐 StockDocumentLineCreateDTO 的 @Digits 约束） */
    private void checkDigits(BigDecimal value, int maxInteger, int maxFraction, String fieldName) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > maxFraction) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    fieldName + "小数位不能超过 " + maxFraction + " 位");
        }
        int integerDigits = stripped.precision() - Math.max(stripped.scale(), 0);
        if (integerDigits > maxInteger) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    fieldName + "整数位不能超过 " + maxInteger + " 位");
        }
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

    private String cellOrEmpty(List<String> row, int index) {
        return index < row.size() && row.get(index) != null ? row.get(index) : "";
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

    private String[] headerRow(String... columns) {
        return columns;
    }

    private String warningText(String level) {
        if ("OUT_OF_STOCK".equals(level)) {
            return "缺货";
        }
        if ("WARNING".equals(level)) {
            return "预警";
        }
        return "正常";
    }

    /** 时间点导出为上海本地时间（业务时区，显式 ZoneId） */
    private String instantText(Instant value) {
        return value == null ? "" : SHANGHAI_FORMAT.format(value);
    }

    private String qtyText(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String priceText(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
