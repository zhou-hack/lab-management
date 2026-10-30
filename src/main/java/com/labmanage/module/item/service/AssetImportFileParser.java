package com.labmanage.module.item.service;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.vo.AssetImportPreviewVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AssetImportFileParser {

    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 500;
    private static final int MAX_ASSETS = 5000;
    private static final int MAX_HEADER_SCAN_ROWS = 30;
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("sequence", List.of("序号", "编号", "项号", "顺序号", "no", "number", "seq", "index")),
            Map.entry("name", List.of("设备名称", "物料名称", "资产名称", "产品名称", "品名", "名称", "设备", "物料",
                    "equipmentname", "devicename", "assetname", "materialname", "itemname", "productname", "name")),
            Map.entry("unit", List.of("单位", "计量单位", "unit", "uom", "unitofmeasure")),
            Map.entry("quantity", List.of("数量", "设备数量", "资产数量", "可用数量", "固定数量",
                    "quantity", "qty", "count", "amount", "totalquantity", "availablequantity")),
            Map.entry("brand", List.of("品牌", "厂家", "制造商", "厂商", "brand", "manufacturer", "factory")),
            Map.entry("model", List.of("型号", "规格型号", "型号规格", "规格", "model", "spec", "specification", "specmodel")),
            Map.entry("category", List.of("类别", "分类", "物料类别", "物料分类", "物料类型", "资产类别", "资产类型",
                    "category", "type", "materialcategory", "materialtype", "assetcategory", "assettype")),
            Map.entry("remark", List.of("备注", "说明", "注释", "remark", "remarks", "note", "notes", "comment", "comments"))
    );
    private static final Map<String, MaterialType> CATEGORY_TYPES = Map.ofEntries(
            Map.entry("耗材", MaterialType.CONSUMABLE), Map.entry("消耗品", MaterialType.CONSUMABLE),
            Map.entry("消耗材料", MaterialType.CONSUMABLE), Map.entry("材料", MaterialType.CONSUMABLE),
            Map.entry("低值易耗品", MaterialType.CONSUMABLE), Map.entry("低值易耗", MaterialType.CONSUMABLE),
            Map.entry("consumable", MaterialType.CONSUMABLE), Map.entry("consumables", MaterialType.CONSUMABLE),
            Map.entry("设备", MaterialType.FIXED_ASSET), Map.entry("固定资产", MaterialType.FIXED_ASSET),
            Map.entry("设备资产", MaterialType.FIXED_ASSET), Map.entry("仪器", MaterialType.FIXED_ASSET),
            Map.entry("仪器设备", MaterialType.FIXED_ASSET), Map.entry("资产", MaterialType.FIXED_ASSET),
            Map.entry("asset", MaterialType.FIXED_ASSET), Map.entry("fixedasset", MaterialType.FIXED_ASSET),
            Map.entry("equipment", MaterialType.FIXED_ASSET), Map.entry("device", MaterialType.FIXED_ASSET)
    );
    private static final List<String> CONSUMABLE_NAME_KEYWORDS = List.of(
            "电阻", "电容", "电感", "二极管", "三极管", "芯片", "集成电路", "导线", "电线", "电缆",
            "网线", "水晶头", "焊锡", "焊丝", "胶带", "绝缘胶布", "螺丝", "螺母", "垫片", "扎带",
            "电池", "试剂", "耗材", "元器件", "连接器", "接插件", "跳线", "熔丝", "保险丝", "灯珠", "排针",
            "排母", "杜邦线", "热缩管", "棉签", "擦拭纸", "无尘布");
    private static final List<String> FIXED_ASSET_NAME_KEYWORDS = List.of(
            "交换机", "计算机", "电脑", "服务器", "打印机", "投影仪", "投影机", "显示器", "路由器",
            "实训台", "实验台", "操作台", "实验箱", "实训箱", "示波器", "信号发生器", "万用表", "稳压电源",
            "直流电源", "仪器", "设备", "装置", "工作站", "摄像机", "摄像头", "扫描仪", "复印机",
            "一体机", "空调", "机柜", "储物柜", "音柱", "仿真器", "开发板", "测试仪", "分析仪",
            "device", "equipment");
    private static final Set<String> GENERIC_FIXED_ASSET_NAMES = Set.of("仪器", "设备", "装置", "device", "equipment");
    private static final Set<String> CONSUMABLE_UNITS = Set.of(
            "个", "只", "根", "卷", "米", "瓶", "盒", "包", "支", "片", "粒", "颗");
    private static final Set<String> FIXED_ASSET_UNITS = Set.of("台", "套", "架", "部", "辆", "台套");

    public record AssetRow(int rowNum, String materialName, String baseUnit, int quantity,
                           String brand, String model, String remark, MaterialType materialType) {
        public String specification() {
            String left = brand == null ? "" : brand.trim();
            String right = model == null ? "" : model.trim();
            if (left.isEmpty()) return right.isEmpty() ? null : right;
            return right.isEmpty() ? left : left + " / " + right;
        }
    }

    public record ParseResult(List<AssetRow> rows, List<AssetImportPreviewVO.Issue> issues, String sheetName) {
    }

    private record HeaderMapping(Row row, Map<String, Integer> columns, int score) {
    }

    private record Classification(MaterialType materialType, String error) {
    }

    public ParseResult parse(MultipartFile file) {
        return parse(file, 1);
    }

    public ParseResult parse(MultipartFile file, int targetSection) {
        if (targetSection < 0 || targetSection > 3) {
            throw badRequest("导入章节仅支持全部、第一部分、第二部分或第三部分");
        }
        if (file == null || file.isEmpty()) {
            throw badRequest("请选择要导入的Excel文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw badRequest("请上传.xls或.xlsx格式的Excel文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("导入文件不能超过10MB");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            ParseResult best = null;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) continue;
                ParseResult candidate = parseSheet(sheet, formatter, evaluator, targetSection);
                if (candidate == null) continue;
                if (best == null || candidate.rows().size() > best.rows().size()
                        || (candidate.rows().size() == best.rows().size()
                        && candidate.issues().size() > best.issues().size())) {
                    best = candidate;
                }
            }
            if (best == null || (best.rows().isEmpty() && best.issues().isEmpty())) {
                throw badRequest("未识别到设备明细，请确保表格包含设备名称和数量列");
            }
            return best;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw badRequest("Excel文件解析失败，请确认文件未损坏且未加密");
        }
    }

    private ParseResult parseSheet(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator,
                                   int targetSection) {
        HeaderMapping header = findHeader(sheet, formatter);
        if (header == null) return null;
        Map<String, Integer> columns = header.columns();
        List<AssetRow> rows = new ArrayList<>();
        List<AssetImportPreviewVO.Issue> issues = new ArrayList<>();
        Integer currentSection = null;
        int dataRows = 0;
        int totalAssets = 0;
        for (int r = header.row().getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            String sequence = text(row, columns.get("sequence"), formatter, evaluator);
            Integer section = sectionNumber(sequence);
            if (section != null) {
                currentSection = section;
                continue;
            }
            if (targetSection > 0 && currentSection != null && currentSection != targetSection) continue;
            if (isBlankRow(row, formatter, evaluator)) continue;
            String name = text(row, columns.get("name"), formatter, evaluator);
            String quantityText = text(row, columns.get("quantity"), formatter, evaluator);
            if (isRepeatedHeader(row, columns, formatter, evaluator) || isSummaryName(name)) continue;
            if (quantityText.isBlank() && !isNumericSequence(sequence)) continue;
            if (++dataRows > MAX_DATA_ROWS) {
                throw badRequest("单次最多导入" + MAX_DATA_ROWS + "条设备明细");
            }
            if (name.isBlank()) {
                issues.add(new AssetImportPreviewVO.Issue(r + 1, "设备名称不能为空"));
                continue;
            }
            int quantity;
            try {
                quantity = new BigDecimal(quantityText).intValueExact();
                if (quantity <= 0) throw new NumberFormatException();
            } catch (Exception e) {
                issues.add(new AssetImportPreviewVO.Issue(r + 1, "数量必须是大于0的整数"));
                continue;
            }
            String unit = text(row, columns.get("unit"), formatter, evaluator);
            Classification classification = classify(name, unit,
                    text(row, columns.get("category"), formatter, evaluator));
            if (classification.error() != null) {
                issues.add(new AssetImportPreviewVO.Issue(r + 1, classification.error()));
                continue;
            }
            if (classification.materialType() == MaterialType.FIXED_ASSET) {
                totalAssets += quantity;
                if (totalAssets > MAX_ASSETS) {
                    throw badRequest("单次最多生成" + MAX_ASSETS + "台固定资产");
                }
            }
            rows.add(new AssetRow(r + 1, name, unit.isBlank() ? "台" : unit, quantity,
                    text(row, columns.get("brand"), formatter, evaluator),
                    text(row, columns.get("model"), formatter, evaluator),
                    text(row, columns.get("remark"), formatter, evaluator), classification.materialType()));
        }
        return new ParseResult(rows, issues, sheet.getSheetName());
    }

    private Classification classify(String name, String originalUnit, String category) {
        if (!category.isBlank()) {
            MaterialType explicit = CATEGORY_TYPES.get(normalizeHeader(category));
            return explicit == null
                    ? new Classification(null, "类别只能填写耗材或设备")
                    : new Classification(explicit, null);
        }
        String normalizedName = normalizeHeader(name);
        boolean consumableName = CONSUMABLE_NAME_KEYWORDS.stream().anyMatch(normalizedName::contains);
        boolean fixedAssetName = FIXED_ASSET_NAME_KEYWORDS.stream().anyMatch(normalizedName::contains);
        if (consumableName && fixedAssetName) {
            boolean specificFixedAssetSuffix = FIXED_ASSET_NAME_KEYWORDS.stream()
                    .filter(keyword -> !GENERIC_FIXED_ASSET_NAMES.contains(keyword))
                    .anyMatch(normalizedName::endsWith);
            return specificFixedAssetSuffix
                    ? new Classification(MaterialType.FIXED_ASSET, null)
                    : new Classification(null, "名称同时包含耗材和设备特征，请增加类别列并填写耗材或设备");
        }
        MaterialType byName = consumableName == fixedAssetName ? null
                : consumableName ? MaterialType.CONSUMABLE : MaterialType.FIXED_ASSET;
        if (byName != null) return new Classification(byName, null);
        String unit = normalizeHeader(originalUnit);
        MaterialType byUnit = CONSUMABLE_UNITS.contains(unit) ? MaterialType.CONSUMABLE
                : FIXED_ASSET_UNITS.contains(unit) ? MaterialType.FIXED_ASSET : null;
        return byUnit == null
                ? new Classification(null, "无法判断属于耗材还是设备，请增加类别列并填写耗材或设备")
                : new Classification(byUnit, null);
    }

    private HeaderMapping findHeader(Sheet sheet, DataFormatter formatter) {
        HeaderMapping best = null;
        int end = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + MAX_HEADER_SCAN_ROWS - 1);
        for (int r = sheet.getFirstRowNum(); r <= end; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Map<String, Integer> columns = detectColumns(row, formatter);
            if (!columns.containsKey("name") || !columns.containsKey("quantity")) continue;
            int score = columns.size();
            if (best == null || score > best.score()) {
                best = new HeaderMapping(row, columns, score);
            }
        }
        return best;
    }

    private Map<String, Integer> detectColumns(Row row, DataFormatter formatter) {
        Map<String, Integer> columns = new HashMap<>();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell == null) continue;
            String header = normalizeHeader(formatter.formatCellValue(cell));
            if (header.isEmpty()) continue;
            for (Map.Entry<String, List<String>> field : ALIASES.entrySet()) {
                if (!columns.containsKey(field.getKey()) && field.getValue().contains(header)) {
                    columns.put(field.getKey(), c);
                }
            }
            if (!columns.containsKey("quantity") && header.contains("数量") && !header.contains("借出")) {
                columns.put("quantity", c);
            }
            if (!columns.containsKey("name") && header.endsWith("名称")
                    && (header.contains("设备") || header.contains("物料") || header.contains("资产"))) {
                columns.put("name", c);
            }
        }
        return columns;
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replaceAll("[\\s_\\-—（）()【】\\[\\]/\\\\:：]", "").toLowerCase();
    }

    private boolean isRepeatedHeader(Row row, Map<String, Integer> columns,
                                     DataFormatter formatter, FormulaEvaluator evaluator) {
        String name = normalizeHeader(text(row, columns.get("name"), formatter, evaluator));
        String quantity = normalizeHeader(text(row, columns.get("quantity"), formatter, evaluator));
        return ALIASES.get("name").contains(name) && ALIASES.get("quantity").contains(quantity);
    }

    private boolean isSummaryName(String name) {
        String normalized = normalizeHeader(name);
        return normalized.startsWith("合计") || normalized.startsWith("总计")
                || normalized.startsWith("小计") || normalized.startsWith("汇总")
                || normalized.equals("total") || normalized.equals("subtotal") || normalized.equals("grandtotal");
    }

    private boolean isBlankRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) return true;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !formatter.formatCellValue(cell, evaluator).trim().isEmpty()) return false;
        }
        return true;
    }

    private String text(Row row, Integer column, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null || column == null) return "";
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
    }

    private boolean isSection(String value, String number) {
        String normalized = value == null ? "" : value.replace(" ", "");
        return normalized.startsWith("（" + number + "）") || normalized.startsWith("(" + number + ")");
    }

    private Integer sectionNumber(String value) {
        List<String> numbers = List.of("一", "二", "三", "四", "五", "六", "七", "八", "九", "十");
        for (int i = 0; i < numbers.size(); i++) {
            if (isSection(value, numbers.get(i))) return i + 1;
        }
        return null;
    }

    private boolean isNumericSequence(String value) {
        return value != null && value.matches("\\d+(?:\\.0+)?");
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }
}
