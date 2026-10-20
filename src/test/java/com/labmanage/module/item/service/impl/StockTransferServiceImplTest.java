package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.StockBalanceExportQuery;
import com.labmanage.module.item.dto.StockDocumentCreateDTO;
import com.labmanage.module.item.dto.StocktakeCountDTO;
import com.labmanage.module.item.dto.StocktakeExportQuery;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.enums.StocktakeStatus;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.service.StocktakeService;
import com.labmanage.module.item.transfer.CsvRowReader;
import com.labmanage.module.item.transfer.CsvRowWriter;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.ImportColumns;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.transfer.XlsxRowReader;
import com.labmanage.module.item.transfer.XlsxRowWriter;
import com.labmanage.module.item.vo.ImportResultVO;
import com.labmanage.module.item.vo.StockBalanceVO;
import com.labmanage.module.item.vo.StocktakeDetailVO;
import com.labmanage.module.item.vo.StocktakeLineVO;
import com.labmanage.module.item.vo.StocktakeVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存余额与盘点批量导入/导出服务单元测试：
 * 期初导入逐行校验与组单、余额导出内容、盘点快照导出、实盘回导定位与状态机
 */
@ExtendWith(MockitoExtension.class)
class StockTransferServiceImplTest {

    @Mock
    private StockQueryService stockQueryService;

    @Mock
    private StockDocumentService stockDocumentService;

    @Mock
    private StocktakeService stocktakeService;

    @Mock
    private StockBalanceMapper balanceMapper;

    @Mock
    private StockLocationMapper locationMapper;

    @Mock
    private MaterialSkuMapper skuMapper;

    @InjectMocks
    private StockTransferServiceImpl service;

    /**
     * 纯单测环境无 MyBatis 容器，显式初始化实体元数据（Lambda 列缓存）
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, StockLocation.class);
        TableInfoHelper.initTableInfo(assistant, MaterialSku.class);
        TableInfoHelper.initTableInfo(assistant, StockBalance.class);
    }

    // ==================== 期初余额导入 ====================

    @Test
    void importOpeningCreatesDraftDocument() throws IOException {
        MockMultipartFile file = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "10.5", "25.5"},
                new String[]{"LOC-A", "SKU-BEAKER", "3", ""}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 1));
        when(skuMapper.selectOne(any())).thenReturn(sku(101L, "SKU-ETHANOL", 2), sku(102L, "SKU-BEAKER", 0));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(stockDocumentService.create(any())).thenReturn("9001");

        ImportResultVO result = service.importOpeningBalances(file);

        assertEquals(2, result.total());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
        assertEquals("9001", result.documentId());

        ArgumentCaptor<StockDocumentCreateDTO> captor = ArgumentCaptor.forClass(StockDocumentCreateDTO.class);
        verify(stockDocumentService).create(captor.capture());
        StockDocumentCreateDTO dto = captor.getValue();
        assertEquals(MovementType.OPENING, dto.movementType());
        assertEquals(2, dto.lines().size());
        assertEquals("101", dto.lines().get(0).skuId());
        assertNull(dto.lines().get(0).fromLocationId());
        assertEquals("10", dto.lines().get(0).toLocationId());
        assertEquals(new BigDecimal("10.5"), dto.lines().get(0).quantity());
        assertEquals(new BigDecimal("25.5"), dto.lines().get(0).unitPrice());
        assertEquals("102", dto.lines().get(1).skuId());
        assertNull(dto.lines().get(1).unitPrice());
        assertTrue(dto.reason().contains("期初批量导入"));
    }

    @Test
    void importOpeningRecordsFailuresAndContinues() throws IOException {
        MockMultipartFile file = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-XXX", "SKU-ETHANOL", "10", ""},
                new String[]{"LOC-A", "SKU-XXX", "10", ""},
                new String[]{"LOC-A", "SKU-ETHANOL", "0", ""},
                new String[]{"LOC-A", "SKU-ETHANOL", "10", ""}));
        StockLocation loc = location(10L, "LOC-A", 1);
        MaterialSku skuA = sku(101L, "SKU-ETHANOL", 2);
        when(locationMapper.selectOne(any())).thenReturn(null, loc, loc);
        when(skuMapper.selectOne(any())).thenReturn(null, skuA);
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(stockDocumentService.create(any())).thenReturn("9001");

        ImportResultVO result = service.importOpeningBalances(file);

        assertEquals(4, result.total());
        assertEquals(1, result.successCount());
        assertEquals(3, result.failures().size());
        assertEquals(2, result.failures().get(0).rowNo());
        assertTrue(result.failures().get(0).reason().contains("库位编码不存在"));
        assertEquals(3, result.failures().get(1).rowNo());
        assertTrue(result.failures().get(1).reason().contains("SKU 码不存在"));
        assertEquals(4, result.failures().get(2).rowNo());
        assertTrue(result.failures().get(2).reason().contains("大于 0"));
        assertEquals("9001", result.documentId());
    }

    @Test
    void importOpeningRejectsDuplicatePairWithinFile() throws IOException {
        MockMultipartFile file = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "10", ""},
                new String[]{"LOC-A", "SKU-ETHANOL", "5", ""}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 1));
        when(skuMapper.selectOne(any())).thenReturn(sku(101L, "SKU-ETHANOL", 2));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(stockDocumentService.create(any())).thenReturn("9001");

        ImportResultVO result = service.importOpeningBalances(file);

        assertEquals(2, result.total());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("文件内库位+SKU 组合重复"));
    }

    @Test
    void importOpeningRejectsExistingBalance() throws IOException {
        MockMultipartFile file = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "10", ""}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 1));
        when(skuMapper.selectOne(any())).thenReturn(sku(101L, "SKU-ETHANOL", 2));
        when(balanceMapper.selectCount(any())).thenReturn(1L);

        ImportResultVO result = service.importOpeningBalances(file);

        assertEquals(1, result.total());
        assertEquals(0, result.successCount());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("已存在此物料的余额记录"));
        verify(stockDocumentService, never()).create(any());
        assertNull(result.documentId());
    }

    @Test
    void importOpeningRejectsDisabledLocationAndSku() throws IOException {
        MockMultipartFile locFile = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "10", ""}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 0));

        ImportResultVO locResult = service.importOpeningBalances(locFile);
        assertEquals(1, locResult.failures().size());
        assertTrue(locResult.failures().get(0).reason().contains("库位已停用"));

        MockMultipartFile skuFile = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "10", ""}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 1));
        when(skuMapper.selectOne(any())).thenReturn(sku(101L, "SKU-ETHANOL", 2, 0));

        ImportResultVO skuResult = service.importOpeningBalances(skuFile);
        assertEquals(1, skuResult.failures().size());
        assertTrue(skuResult.failures().get(0).reason().contains("物料已停用"));
    }

    @Test
    void importOpeningRejectsPrecisionAndDigitsOverflow() throws IOException {
        MockMultipartFile file = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "10.555", ""},
                new String[]{"LOC-A", "SKU-ETHANOL", "5", "25.12345"}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 1));
        when(skuMapper.selectOne(any())).thenReturn(sku(101L, "SKU-ETHANOL", 2));

        ImportResultVO result = service.importOpeningBalances(file);

        assertEquals(2, result.total());
        assertEquals(0, result.successCount());
        assertEquals(2, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("数量精度超过物料限制"));
        assertTrue(result.failures().get(1).reason().contains("单价小数位不能超过"));
        verify(stockDocumentService, never()).create(any());
    }

    @Test
    void importOpeningRejectsMismatchedHeader() throws IOException {
        MockMultipartFile file = file("opening.csv", csvBytes(
                new String[]{"错误表头", "SKU码", "数量", "单价"},
                new String[]{"LOC-A", "SKU-ETHANOL", "10", ""}));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importOpeningBalances(file));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("表头不匹配"));
    }

    @Test
    void importOpeningRejectsEmptyFileAndUnsupportedExtension() {
        BusinessException empty = assertThrows(BusinessException.class,
                () -> service.importOpeningBalances(file("opening.csv", new byte[0])));
        assertTrue(empty.getMessage().contains("导入文件不能为空"));

        BusinessException unsupported = assertThrows(BusinessException.class,
                () -> service.importOpeningBalances(file("opening.txt", "任意内容".getBytes())));
        assertTrue(unsupported.getMessage().contains("仅支持 .xlsx 或 .csv"));
    }

    @Test
    void importOpeningSupportsXlsx() throws IOException {
        MockMultipartFile file = file("opening.xlsx", xlsxBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "10", "25.5"}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 1));
        when(skuMapper.selectOne(any())).thenReturn(sku(101L, "SKU-ETHANOL", 2));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(stockDocumentService.create(any())).thenReturn("9001");

        ImportResultVO result = service.importOpeningBalances(file);

        assertEquals(1, result.total());
        assertEquals(1, result.successCount());
    }

    // ==================== 余额导出 ====================

    @Test
    void exportBalancesWritesCsvWithHeaderAndRows() throws IOException {
        StockBalanceVO warning = balanceVO("1", "10", "一号柜", "101", "SKU-ETHANOL", "无水乙醇",
                "8.5", "10", "20", "25.5", "WARNING");
        StockBalanceVO outOfStock = balanceVO("2", "99", "二号柜", "102", "SKU-BEAKER", "烧杯",
                "0", "5", "10", "3", "OUT_OF_STOCK");
        when(stockQueryService.listBalanceForExport(any())).thenReturn(List.of(warning, outOfStock));
        when(locationMapper.selectByIds(any())).thenReturn(List.of(location(10L, "LOC-A", 1)));
        StockBalanceExportQuery query = new StockBalanceExportQuery();
        query.setFormat("csv");

        TransferFile file = service.exportBalances(query);

        assertEquals("text/csv", file.contentType());
        assertTrue(file.fileName().startsWith("库存余额导出_"));
        assertTrue(file.fileName().endsWith(".csv"));
        List<List<String>> rows = new CsvRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(List.of("库位编码", "SKU码", "物料名称", "库存量", "安全库存", "补货目标", "移动均价", "预警状态"),
                rows.get(0));
        assertEquals(List.of("LOC-A", "SKU-ETHANOL", "无水乙醇", "8.5", "10", "20", "25.5", "预警"), rows.get(1));
        assertEquals(List.of("", "SKU-BEAKER", "烧杯", "0", "5", "10", "3", "缺货"), rows.get(2));
    }

    @Test
    void exportBalancesSupportsXlsx() throws IOException {
        StockBalanceVO vo = balanceVO("1", "10", "一号柜", "101", "SKU-ETHANOL", "无水乙醇",
                "8.5", "10", "20", "25.5", "NORMAL");
        when(stockQueryService.listBalanceForExport(any())).thenReturn(List.of(vo));
        when(locationMapper.selectByIds(any())).thenReturn(List.of(location(10L, "LOC-A", 1)));
        StockBalanceExportQuery query = new StockBalanceExportQuery();
        query.setFormat("xlsx");

        TransferFile file = service.exportBalances(query);

        assertTrue(file.fileName().endsWith(".xlsx"));
        List<List<String>> rows = new XlsxRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(2, rows.size());
        assertEquals("正常", rows.get(1).get(7));
    }

    @Test
    void exportBalancesRejectsWhenExceedsLimit() {
        StockBalanceVO vo = balanceVO("1", "10", "一号柜", "101", "SKU-ETHANOL", "无水乙醇",
                "1", "1", "1", "1", "NORMAL");
        when(stockQueryService.listBalanceForExport(any())).thenReturn(Collections.nCopies(10001, vo));
        StockBalanceExportQuery query = new StockBalanceExportQuery();
        query.setFormat("csv");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exportBalances(query));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("导出行数超过上限"));
    }

    @Test
    void exportBalancesRejectsInvalidFormat() {
        StockBalanceExportQuery query = new StockBalanceExportQuery();
        query.setFormat("pdf");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exportBalances(query));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("导出格式只能为 xlsx 或 csv"));
    }

    @Test
    void openingImportTemplateContainsOnlyHeader() throws IOException {
        TransferFile xlsx = service.openingImportTemplate(FileFormat.XLSX);
        assertTrue(xlsx.fileName().startsWith("期初余额导入模板"));
        List<List<String>> xlsxRows = new XlsxRowReader().read(new ByteArrayInputStream(xlsx.content()), 10);
        assertEquals(1, xlsxRows.size());
        assertEquals(ImportColumns.BALANCE_OPENING_HEADER, xlsxRows.get(0).subList(0, ImportColumns.BALANCE_OPENING_HEADER.size()));

        TransferFile csv = service.openingImportTemplate(FileFormat.CSV);
        List<List<String>> csvRows = new CsvRowReader().read(new ByteArrayInputStream(csv.content()), 10);
        assertEquals(1, csvRows.size());
        assertEquals(ImportColumns.BALANCE_OPENING_HEADER, csvRows.get(0).subList(0, ImportColumns.BALANCE_OPENING_HEADER.size()));
    }

    // ==================== 盘点快照导出 ====================

    @Test
    void exportStocktakeSnapshotWritesCsvRows() throws IOException {
        StocktakeLineVO recorded = line("501", "101", "SKU-ETHANOL", "无水乙醇", "20", "18", "一瓶破损未登记报废");
        StocktakeLineVO pending = line("502", "102", "SKU-BEAKER", "烧杯 100mL", "5", null, null);
        when(stocktakeService.get("8001")).thenReturn(
                detail("8001", "10", StocktakeStatus.COUNTING, List.of(recorded, pending)));
        when(locationMapper.selectById(10L)).thenReturn(location(10L, "LOC-A", 1));

        TransferFile file = service.exportStocktakeSnapshot("8001", FileFormat.CSV);

        assertTrue(file.fileName().startsWith("盘点快照_8001_"));
        List<List<String>> rows = new CsvRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(ImportColumns.STOCKTAKE_SNAPSHOT_HEADER, rows.get(0).subList(0, ImportColumns.STOCKTAKE_SNAPSHOT_HEADER.size()));
        assertEquals(List.of("LOC-A", "SKU-ETHANOL", "无水乙醇", "20", "18", "一瓶破损未登记报废"), rows.get(1));
        assertEquals(List.of("LOC-A", "SKU-BEAKER", "烧杯 100mL", "5", "", ""), rows.get(2));
    }

    @Test
    void exportStocktakeSnapshotSupportsXlsx() throws IOException {
        StocktakeLineVO recorded = line("501", "101", "SKU-ETHANOL", "无水乙醇", "20", "18", null);
        when(stocktakeService.get("8001")).thenReturn(
                detail("8001", "10", StocktakeStatus.PENDING_REVIEW, List.of(recorded)));
        when(locationMapper.selectById(10L)).thenReturn(location(10L, "LOC-A", 1));

        TransferFile file = service.exportStocktakeSnapshot("8001", FileFormat.XLSX);

        List<List<String>> rows = new XlsxRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(2, rows.size());
        assertEquals("18", rows.get(1).get(4));
    }

    // ==================== 盘点单列表导出 ====================

    @Test
    void exportStocktakesWritesCsvWithHeaderAndRows() throws IOException {
        StocktakeVO counting = stocktakeVO("9001", "10", "一号实验室 A 区药品柜", LocalDate.parse("2026-10-17"),
                StocktakeStatus.COUNTING, Instant.parse("2026-10-17T01:30:00Z"), null,
                null, Instant.parse("2026-10-17T01:28:00Z"));
        StocktakeVO completed = stocktakeVO("9002", "99", "二号实验室 B 区耗材柜", LocalDate.parse("2026-10-16"),
                StocktakeStatus.COMPLETED, Instant.parse("2026-10-16T02:00:00Z"),
                Instant.parse("2026-10-16T06:00:00Z"), "7001", Instant.parse("2026-10-16T01:58:00Z"));
        when(stocktakeService.listForExport(any())).thenReturn(List.of(counting, completed));
        when(locationMapper.selectByIds(any())).thenReturn(List.of(
                location(10L, "LOC-A", 1), location(99L, "LOC-B", 1)));
        StocktakeExportQuery query = new StocktakeExportQuery();
        query.setFormat("csv");

        TransferFile file = service.exportStocktakes(query);

        assertTrue(file.fileName().startsWith("盘点单导出_"));
        assertTrue(file.fileName().endsWith(".csv"));
        List<List<String>> rows = new CsvRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(List.of("盘点单ID", "库位编码", "库位名称", "盘点日期", "状态",
                "开始时间", "完成时间", "调账单ID", "登记时间"), rows.get(0));
        assertEquals(List.of("9001", "LOC-A", "一号实验室 A 区药品柜", "2026-10-17", "盘点中",
                "2026-10-17 09:30:00", "", "", "2026-10-17 09:28:00"), rows.get(1));
        assertEquals(List.of("9002", "LOC-B", "二号实验室 B 区耗材柜", "2026-10-16", "已完成",
                "2026-10-16 10:00:00", "2026-10-16 14:00:00", "7001", "2026-10-16 09:58:00"), rows.get(2));
    }

    @Test
    void exportStocktakesSupportsXlsx() throws IOException {
        StocktakeVO cancelled = stocktakeVO("9003", "10", "一号柜", LocalDate.parse("2026-10-15"),
                StocktakeStatus.CANCELLED, null, null, null, Instant.parse("2026-10-15T01:00:00Z"));
        when(stocktakeService.listForExport(any())).thenReturn(List.of(cancelled));
        when(locationMapper.selectByIds(any())).thenReturn(List.of(location(10L, "LOC-A", 1)));
        StocktakeExportQuery query = new StocktakeExportQuery();
        query.setFormat("xlsx");

        TransferFile file = service.exportStocktakes(query);

        assertTrue(file.fileName().endsWith(".xlsx"));
        List<List<String>> rows = new XlsxRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(2, rows.size());
        assertEquals("已取消", rows.get(1).get(4));
        assertEquals("2026-10-15 09:00:00", rows.get(1).get(8));
    }

    @Test
    void exportStocktakesRejectsWhenExceedsLimit() {
        StocktakeVO vo = stocktakeVO("9004", "10", "一号柜", LocalDate.parse("2026-10-17"),
                StocktakeStatus.DRAFT, null, null, null, Instant.parse("2026-10-17T01:00:00Z"));
        when(stocktakeService.listForExport(any())).thenReturn(Collections.nCopies(10001, vo));
        StocktakeExportQuery query = new StocktakeExportQuery();
        query.setFormat("csv");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exportStocktakes(query));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("导出行数超过上限"));
    }

    @Test
    void exportStocktakesRejectsInvalidFormat() {
        StocktakeExportQuery query = new StocktakeExportQuery();
        query.setFormat("pdf");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exportStocktakes(query));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("导出格式只能为 xlsx 或 csv"));
    }

    // ==================== 盘点实盘回导 ====================

    @Test
    void importStocktakeCountCallsCountWithValidLines() throws IOException {
        StocktakeLineVO lineA = line("501", "101", "SKU-ETHANOL", "无水乙醇", "20", null, null);
        StocktakeLineVO lineB = line("502", "102", "SKU-BEAKER", "烧杯", "5", null, null);
        when(stocktakeService.get("8001")).thenReturn(
                detail("8001", "10", StocktakeStatus.COUNTING, List.of(lineA, lineB)));
        when(skuMapper.selectByIds(any())).thenReturn(List.of(sku(101L, "SKU-ETHANOL", 2), sku(102L, "SKU-BEAKER", 0)));
        MockMultipartFile file = file("count.csv", csvBytes(
                ImportColumns.STOCKTAKE_SNAPSHOT_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "无水乙醇", "20", "18", " 一瓶破损未登记报废 "},
                new String[]{"LOC-A", "SKU-BEAKER", "烧杯", "5", "", ""},
                new String[]{"LOC-A", "SKU-BEAKER", "烧杯", "5", "6", ""}));

        ImportResultVO result = service.importStocktakeCount("8001", file);

        assertEquals(2, result.total());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());

        ArgumentCaptor<StocktakeCountDTO> captor = ArgumentCaptor.forClass(StocktakeCountDTO.class);
        verify(stocktakeService).count(eq("8001"), captor.capture());
        List<StocktakeCountDTO.CountLineDTO> lines = captor.getValue().lines();
        assertEquals(2, lines.size());
        assertEquals("501", lines.get(0).lineId());
        assertEquals(new BigDecimal("18"), lines.get(0).countedQty());
        assertEquals("一瓶破损未登记报废", lines.get(0).differenceReason());
        assertEquals("502", lines.get(1).lineId());
        assertEquals(new BigDecimal("6"), lines.get(1).countedQty());
        assertNull(lines.get(1).differenceReason());
    }

    @Test
    void importStocktakeCountRejectsWrongStatus() throws IOException {
        when(stocktakeService.get("8001")).thenReturn(
                detail("8001", "10", StocktakeStatus.PENDING_REVIEW, List.of()));
        MockMultipartFile file = file("count.csv", csvBytes(
                ImportColumns.STOCKTAKE_SNAPSHOT_HEADER.toArray(new String[0])));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.importStocktakeCount("8001", file));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("不处于盘点中状态"));
        verify(stocktakeService, never()).count(any(), any());
    }

    @Test
    void importStocktakeCountRecordsFailures() throws IOException {
        StocktakeLineVO lineA = line("501", "101", "SKU-ETHANOL", "无水乙醇", "20", null, null);
        when(stocktakeService.get("8001")).thenReturn(
                detail("8001", "10", StocktakeStatus.COUNTING, List.of(lineA)));
        when(skuMapper.selectByIds(any())).thenReturn(List.of(sku(101L, "SKU-ETHANOL", 2)));
        // 负数不经 csvBytes 生成：写出侧公式注入防护会给 "-1" 加 "'" 前缀，掩盖负数校验分支
        String csv = String.join("\r\n",
                String.join(",", ImportColumns.STOCKTAKE_SNAPSHOT_HEADER),
                "LOC-A,SKU-XXX,未知物料,1,2,",
                "LOC-A,SKU-ETHANOL,无水乙醇,20,-1,",
                "LOC-A,SKU-ETHANOL,无水乙醇,20,1.555,",
                "LOC-A,SKU-ETHANOL,无水乙醇,20,abc,",
                "LOC-A,SKU-ETHANOL,无水乙醇,20,5,",
                "LOC-A,SKU-ETHANOL,无水乙醇,20,8,") + "\r\n";
        MockMultipartFile file = file("count.csv", csv.getBytes(StandardCharsets.UTF_8));

        ImportResultVO result = service.importStocktakeCount("8001", file);

        assertEquals(6, result.total());
        assertEquals(1, result.successCount());
        assertEquals(5, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("SKU 码不在该盘点单中"));
        assertTrue(result.failures().get(1).reason().contains("实盘数不能为负"));
        assertTrue(result.failures().get(2).reason().contains("实盘数精度超过物料限制"));
        assertTrue(result.failures().get(3).reason().contains("必须为数字"));
        assertTrue(result.failures().get(4).reason().contains("文件内 SKU 码重复"));

        ArgumentCaptor<StocktakeCountDTO> captor = ArgumentCaptor.forClass(StocktakeCountDTO.class);
        verify(stocktakeService).count(eq("8001"), captor.capture());
        assertEquals(1, captor.getValue().lines().size());
        assertEquals("501", captor.getValue().lines().get(0).lineId());
    }

    @Test
    void importStocktakeCountRequiresCountedWhenReasonPresent() throws IOException {
        StocktakeLineVO lineA = line("501", "101", "SKU-ETHANOL", "无水乙醇", "20", null, null);
        when(stocktakeService.get("8001")).thenReturn(
                detail("8001", "10", StocktakeStatus.COUNTING, List.of(lineA)));
        when(skuMapper.selectByIds(any())).thenReturn(List.of(sku(101L, "SKU-ETHANOL", 2)));
        MockMultipartFile file = file("count.csv", csvBytes(
                ImportColumns.STOCKTAKE_SNAPSHOT_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "无水乙醇", "20", "", "有差异未填实盘"}));

        ImportResultVO result = service.importStocktakeCount("8001", file);

        assertEquals(1, result.total());
        assertEquals(0, result.successCount());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("实盘数不能为空"));
        verify(stocktakeService, never()).count(any(), any());
    }

    @Test
    void importStocktakeCountRejectsMismatchedHeaderAndEmptyFile() throws IOException {
        when(stocktakeService.get("8001")).thenReturn(
                detail("8001", "10", StocktakeStatus.COUNTING, List.of()));
        MockMultipartFile badHeader = file("count.csv", csvBytes(
                new String[]{"库位编码", "SKU码", "物料名称", "账面数", "实盘数", "错误列"},
                new String[]{"LOC-A", "SKU-A", "物料", "1", "2", "x"}));

        BusinessException header = assertThrows(BusinessException.class,
                () -> service.importStocktakeCount("8001", badHeader));
        assertTrue(header.getMessage().contains("表头不匹配"));

        BusinessException empty = assertThrows(BusinessException.class,
                () -> service.importStocktakeCount("8001", file("count.csv", new byte[0])));
        assertTrue(empty.getMessage().contains("导入文件不能为空"));
    }

    // ==================== 工具 ====================

    private byte[] csvBytes(String[]... lines) throws IOException {
        CsvRowWriter writer = new CsvRowWriter();
        for (String[] line : lines) {
            writer.writeRow(line);
        }
        byte[] bytes = writer.toBytes();
        writer.close();
        return bytes;
    }

    private byte[] xlsxBytes(String[]... lines) throws IOException {
        XlsxRowWriter writer = new XlsxRowWriter();
        for (String[] line : lines) {
            writer.writeRow(line);
        }
        byte[] bytes = writer.toBytes();
        writer.close();
        return bytes;
    }

    private MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, null, content);
    }

    private StockLocation location(long id, String code, int enabled) {
        StockLocation location = new StockLocation();
        location.setId(id);
        location.setLocationCode(code);
        location.setLocationName("库位-" + code);
        location.setEnabled(enabled);
        return location;
    }

    private MaterialSku sku(long id, String code, int scale) {
        return sku(id, code, scale, 1);
    }

    private MaterialSku sku(long id, String code, int scale, int enabled) {
        MaterialSku sku = new MaterialSku();
        sku.setId(id);
        sku.setSkuCode(code);
        sku.setMaterialName("物料-" + code);
        sku.setQuantityScale(scale);
        sku.setEnabled(enabled);
        return sku;
    }

    private StockBalanceVO balanceVO(String id, String locationId, String locationName, String skuId,
                                     String skuCode, String skuName, String stockQty, String safeQty,
                                     String targetQty, String price, String warningLevel) {
        return new StockBalanceVO(id, locationId, locationName, skuId, skuCode, skuName, "瓶",
                new BigDecimal(stockQty), new BigDecimal(safeQty), new BigDecimal(targetQty),
                new BigDecimal(price), null, warningLevel, 1,
                Instant.parse("2026-10-17T01:00:00Z"));
    }

    private StocktakeLineVO line(String id, String skuId, String skuCode, String skuName,
                                 String bookQty, String countedQty, String reason) {
        BigDecimal counted = countedQty == null ? null : new BigDecimal(countedQty);
        BigDecimal book = new BigDecimal(bookQty);
        return new StocktakeLineVO(id, skuId, skuCode, skuName, "瓶", book, counted,
                counted == null ? null : counted.subtract(book), reason, null, null);
    }

    private StocktakeVO stocktakeVO(String id, String locationId, String locationName, LocalDate countDate,
                                    StocktakeStatus status, Instant startedAt, Instant finishedAt,
                                    String adjustDocumentId, Instant createTime) {
        return new StocktakeVO(id, id, locationId, locationName, null, countDate, status,
                startedAt, finishedAt, adjustDocumentId, createTime);
    }

    private StocktakeDetailVO detail(String id, String locationId, StocktakeStatus status,
                                     List<StocktakeLineVO> lines) {
        return new StocktakeDetailVO(id, "7001", locationId, "一号实验室 A 区药品柜", null,
                LocalDate.parse("2026-10-14"), status,
                Instant.parse("2026-10-14T01:00:00Z"), null, null,
                Instant.parse("2026-10-14T00:58:00Z"), lines);
    }
}
