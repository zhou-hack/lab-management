package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.StockBalanceExportQuery;
import com.labmanage.module.item.dto.StockDocumentCreateDTO;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.enums.MovementType;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.service.StockDocumentService;
import com.labmanage.module.item.service.StockQueryService;
import com.labmanage.module.item.transfer.CsvRowReader;
import com.labmanage.module.item.transfer.CsvRowWriter;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.ImportColumns;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.transfer.XlsxRowReader;
import com.labmanage.module.item.transfer.XlsxRowWriter;
import com.labmanage.module.item.vo.ImportResultVO;
import com.labmanage.module.item.vo.StockBalanceVO;
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
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存余额批量导入/导出服务单元测试：
 * 期初导入逐行校验与组单、余额导出内容
 */
@ExtendWith(MockitoExtension.class)
class StockTransferServiceImplTest {

    @Mock
    private StockQueryService stockQueryService;

    @Mock
    private StockDocumentService stockDocumentService;

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
                new String[]{"LOC-A", "SKU-ETHANOL", "10.5"},
                new String[]{"LOC-A", "SKU-BEAKER", "3"}));
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
        assertEquals("102", dto.lines().get(1).skuId());
        assertEquals(new BigDecimal("3"), dto.lines().get(1).quantity());
        assertTrue(dto.reason().contains("期初批量导入"));
    }

    @Test
    void importOpeningRecordsFailuresAndContinues() throws IOException {
        MockMultipartFile file = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-XXX", "SKU-ETHANOL", "10"},
                new String[]{"LOC-A", "SKU-XXX", "10"},
                new String[]{"LOC-A", "SKU-ETHANOL", "0"},
                new String[]{"LOC-A", "SKU-ETHANOL", "10"}));
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
                new String[]{"LOC-A", "SKU-ETHANOL", "10"},
                new String[]{"LOC-A", "SKU-ETHANOL", "5"}));
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
                new String[]{"LOC-A", "SKU-ETHANOL", "10"}));
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
                new String[]{"LOC-A", "SKU-ETHANOL", "10"}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 0));

        ImportResultVO locResult = service.importOpeningBalances(locFile);
        assertEquals(1, locResult.failures().size());
        assertTrue(locResult.failures().get(0).reason().contains("库位已停用"));

        MockMultipartFile skuFile = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "10"}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 1));
        when(skuMapper.selectOne(any())).thenReturn(sku(101L, "SKU-ETHANOL", 2, 0));

        ImportResultVO skuResult = service.importOpeningBalances(skuFile);
        assertEquals(1, skuResult.failures().size());
        assertTrue(skuResult.failures().get(0).reason().contains("物料已停用"));
    }

    @Test
    void importOpeningRejectsQuantityPrecisionOverflow() throws IOException {
        MockMultipartFile file = file("opening.csv", csvBytes(
                ImportColumns.BALANCE_OPENING_HEADER.toArray(new String[0]),
                new String[]{"LOC-A", "SKU-ETHANOL", "10.555"}));
        when(locationMapper.selectOne(any())).thenReturn(location(10L, "LOC-A", 1));
        when(skuMapper.selectOne(any())).thenReturn(sku(101L, "SKU-ETHANOL", 2));

        ImportResultVO result = service.importOpeningBalances(file);

        assertEquals(1, result.total());
        assertEquals(0, result.successCount());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("数量精度超过物料限制"));
        verify(stockDocumentService, never()).create(any());
    }

    @Test
    void importOpeningRejectsMismatchedHeader() throws IOException {
        MockMultipartFile file = file("opening.csv", csvBytes(
                new String[]{"错误表头", "SKU码", "数量"},
                new String[]{"LOC-A", "SKU-ETHANOL", "10"}));

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
                new String[]{"LOC-A", "SKU-ETHANOL", "10"}));
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
                "8.5", "10", "20", "WARNING");
        StockBalanceVO outOfStock = balanceVO("2", "99", "二号柜", "102", "SKU-BEAKER", "烧杯",
                "0", "5", "10", "OUT_OF_STOCK");
        when(stockQueryService.listBalanceForExport(any())).thenReturn(List.of(warning, outOfStock));
        when(locationMapper.selectByIds(any())).thenReturn(List.of(location(10L, "LOC-A", 1)));
        StockBalanceExportQuery query = new StockBalanceExportQuery();
        query.setFormat("csv");

        TransferFile file = service.exportBalances(query);

        assertEquals("text/csv", file.contentType());
        assertTrue(file.fileName().startsWith("库存余额导出_"));
        assertTrue(file.fileName().endsWith(".csv"));
        List<List<String>> rows = new CsvRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(List.of("库位编码", "SKU码", "物料名称", "库存量", "安全库存", "补货目标", "预警状态"),
                rows.get(0));
        assertEquals(List.of("LOC-A", "SKU-ETHANOL", "无水乙醇", "8.5", "10", "20", "预警"), rows.get(1));
        assertEquals(List.of("", "SKU-BEAKER", "烧杯", "0", "5", "10", "缺货"), rows.get(2));
    }

    @Test
    void exportBalancesSupportsXlsx() throws IOException {
        StockBalanceVO vo = balanceVO("1", "10", "一号柜", "101", "SKU-ETHANOL", "无水乙醇",
                "8.5", "10", "20", "NORMAL");
        when(stockQueryService.listBalanceForExport(any())).thenReturn(List.of(vo));
        when(locationMapper.selectByIds(any())).thenReturn(List.of(location(10L, "LOC-A", 1)));
        StockBalanceExportQuery query = new StockBalanceExportQuery();
        query.setFormat("xlsx");

        TransferFile file = service.exportBalances(query);

        assertTrue(file.fileName().endsWith(".xlsx"));
        List<List<String>> rows = new XlsxRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(2, rows.size());
        assertEquals("正常", rows.get(1).get(6));
    }

    @Test
    void exportBalancesRejectsWhenExceedsLimit() {
        StockBalanceVO vo = balanceVO("1", "10", "一号柜", "101", "SKU-ETHANOL", "无水乙醇",
                "1", "1", "1", "NORMAL");
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
                                     String targetQty, String warningLevel) {
        return new StockBalanceVO(id, locationId, locationName, skuId, skuCode, skuName, "瓶",
                new BigDecimal(stockQty), new BigDecimal(safeQty), new BigDecimal(targetQty),
                null, warningLevel, 1,
                Instant.parse("2026-10-17T01:00:00Z"));
    }
}
