package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.labmanage.module.item.transfer.CsvRowReader;
import com.labmanage.module.item.transfer.CsvRowWriter;
import com.labmanage.module.item.transfer.FileFormat;
import com.labmanage.module.item.transfer.ImportColumns;
import com.labmanage.module.item.transfer.TransferFile;
import com.labmanage.module.item.transfer.XlsxRowReader;
import com.labmanage.module.item.transfer.XlsxRowWriter;
import com.labmanage.module.item.vo.ImportResultVO;
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
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物料批量导入/导出服务单元测试：逐行校验、冲突跳过记明细、导出文件内容与模板
 */
@ExtendWith(MockitoExtension.class)
class MaterialTransferServiceImplTest {

    @Mock
    private MaterialSkuService skuService;

    @Mock
    private MaterialCategoryService categoryService;

    @Mock
    private MaterialSkuMapper skuMapper;

    @Mock
    private MaterialCategoryMapper categoryMapper;

    @InjectMocks
    private MaterialTransferServiceImpl service;

    /**
     * 纯单测环境无 MyBatis 容器，显式初始化实体元数据（Lambda 列缓存）
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaterialSku.class);
        TableInfoHelper.initTableInfo(assistant, MaterialCategory.class);
    }

    // ==================== 导入：物料 ====================

    @Test
    void importSkusCreatesValidRows() throws IOException {
        MockMultipartFile file = file("import.csv", csvBytes(
                ImportColumns.SKU_HEADER.toArray(new String[0]),
                new String[]{"CAT-CHEM", " SKU-ETHANOL ", "无水乙醇 500mL", "AR，500mL/瓶", "瓶", "2", "耗材", "是", "否"},
                new String[]{"CAT-CHEM", "SKU-BEAKER", "烧杯 100mL", "", "个", "0", "可回收", "否", "否"}));
        when(categoryMapper.selectOne(any())).thenReturn(category(5L, "CAT-CHEM"));

        ImportResultVO result = service.importSkus(file);

        assertEquals(2, result.total());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());

        ArgumentCaptor<MaterialSkuCreateDTO> captor = ArgumentCaptor.forClass(MaterialSkuCreateDTO.class);
        verify(skuService, org.mockito.Mockito.times(2)).create(captor.capture());
        List<MaterialSkuCreateDTO> dtos = captor.getAllValues();
        assertEquals("5", dtos.get(0).categoryId());
        assertEquals("SKU-ETHANOL", dtos.get(0).skuCode());
        assertEquals("无水乙醇 500mL", dtos.get(0).materialName());
        assertEquals("AR，500mL/瓶", dtos.get(0).specification());
        assertEquals(2, dtos.get(0).quantityScale());
        assertEquals(MaterialType.CONSUMABLE, dtos.get(0).materialType());
        assertEquals(true, dtos.get(0).isHazardous());
        assertEquals(false, dtos.get(0).isValuable());
        assertEquals("SKU-BEAKER", dtos.get(1).skuCode());
        assertEquals(null, dtos.get(1).specification());
        assertEquals(MaterialType.RETURNABLE, dtos.get(1).materialType());
        assertEquals(false, dtos.get(1).isHazardous());
    }

    @Test
    void importSkusRecordsFailuresAndContinues() throws IOException {
        MockMultipartFile file = file("import.csv", csvBytes(
                ImportColumns.SKU_HEADER.toArray(new String[0]),
                new String[]{"CAT-XXX", "SKU-A", "物料A", "", "瓶", "0", "耗材", "否", "否"},
                new String[]{"CAT-CHEM", "SKU-B", "物料B", "", "瓶", "1", "耗材", "否", "否"},
                new String[]{"CAT-CHEM", "", "物料C", "", "瓶", "0", "耗材", "否", "否"}));
        when(categoryMapper.selectOne(any())).thenReturn(null, category(5L, "CAT-CHEM"), category(5L, "CAT-CHEM"));

        ImportResultVO result = service.importSkus(file);

        assertEquals(3, result.total());
        assertEquals(1, result.successCount());
        assertEquals(2, result.failures().size());
        assertEquals(2, result.failures().get(0).rowNo());
        assertTrue(result.failures().get(0).reason().contains("分类编码不存在"));
        assertEquals(4, result.failures().get(1).rowNo());
        assertTrue(result.failures().get(1).reason().contains("SKU 码不能为空"));
    }

    @Test
    void importSkusRejectsDuplicateCodeWithinFile() throws IOException {
        MockMultipartFile file = file("import.csv", csvBytes(
                ImportColumns.SKU_HEADER.toArray(new String[0]),
                new String[]{"CAT-CHEM", "SKU-DUP", "物料一", "", "瓶", "0", "耗材", "否", "否"},
                new String[]{"CAT-CHEM", "SKU-DUP", "物料二", "", "瓶", "0", "耗材", "否", "否"}));
        when(categoryMapper.selectOne(any())).thenReturn(category(5L, "CAT-CHEM"));

        ImportResultVO result = service.importSkus(file);

        assertEquals(2, result.total());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("文件内 SKU 码重复"));
    }

    @Test
    void importSkusPropagatesServiceConflict() throws IOException {
        MockMultipartFile file = file("import.csv", csvBytes(
                ImportColumns.SKU_HEADER.toArray(new String[0]),
                new String[]{"CAT-CHEM", "SKU-A", "物料A", "", "瓶", "0", "耗材", "否", "否"},
                new String[]{"CAT-CHEM", "SKU-B", "物料B", "", "瓶", "0", "耗材", "否", "否"}));
        when(categoryMapper.selectOne(any())).thenReturn(category(5L, "CAT-CHEM"));
        doAnswer(invocation -> {
            MaterialSkuCreateDTO dto = invocation.getArgument(0);
            if ("SKU-B".equals(dto.skuCode())) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "SKU 码已存在，不得复用");
            }
            return null;
        }).when(skuService).create(any(MaterialSkuCreateDTO.class));

        ImportResultVO result = service.importSkus(file);

        assertEquals(2, result.total());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failures().size());
        assertEquals("SKU 码已存在，不得复用", result.failures().get(0).reason());
    }

    @Test
    void importSkusSkipsInvalidScaleAndBlankRows() throws IOException {
        MockMultipartFile file = file("import.csv", csvBytes(
                ImportColumns.SKU_HEADER.toArray(new String[0]),
                new String[]{"CAT-CHEM", "SKU-A", "物料A", "", "瓶", "9", "耗材", "否", "否"},
                new String[]{"", "", "", "", "", "", "", "", ""},
                new String[]{"CAT-CHEM", "SKU-B", "物料B", "", "瓶", "0", "耗材", "否", "否"}));
        when(categoryMapper.selectOne(any())).thenReturn(category(5L, "CAT-CHEM"));

        ImportResultVO result = service.importSkus(file);

        assertEquals(2, result.total());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("数量精度"));
    }

    @Test
    void importSkusSupportsXlsx() throws IOException {
        MockMultipartFile file = file("import.xlsx", xlsxBytes(
                ImportColumns.SKU_HEADER.toArray(new String[0]),
                new String[]{"CAT-CHEM", "SKU-X", "物料X", "", "瓶", "0", "耗材", "否", "否"}));
        when(categoryMapper.selectOne(any())).thenReturn(category(5L, "CAT-CHEM"));

        ImportResultVO result = service.importSkus(file);

        assertEquals(1, result.total());
        assertEquals(1, result.successCount());
    }

    @Test
    void importSkusRejectsMismatchedHeader() throws IOException {
        MockMultipartFile file = file("import.csv", csvBytes(
                new String[]{"错误表头", "SKU码", "物料名称", "规格型号", "计量单位", "数量精度", "物料类型", "危险品", "高价值"},
                new String[]{"CAT-CHEM", "SKU-A", "物料A", "", "瓶", "0", "耗材", "否", "否"}));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importSkus(file));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("表头不匹配"));
    }

    @Test
    void importSkusRejectsEmptyFile() {
        MockMultipartFile file = file("import.csv", new byte[0]);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importSkus(file));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("导入文件不能为空"));
    }

    @Test
    void importSkusRejectsUnsupportedExtension() throws IOException {
        MockMultipartFile file = file("import.txt", "任意内容".getBytes());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.importSkus(file));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("仅支持 .xlsx 或 .csv"));
    }

    // ==================== 导入：分类 ====================

    @Test
    void importCategoriesCreatesHierarchy() throws IOException {
        MockMultipartFile file = file("import.csv", csvBytes(
                ImportColumns.CATEGORY_HEADER.toArray(new String[0]),
                new String[]{"CAT-CHEM", "化学试剂", ""},
                new String[]{"CAT-ACID", "酸类", "CAT-CHEM"}));
        when(categoryMapper.selectOne(any())).thenReturn(category(5L, "CAT-CHEM"));

        ImportResultVO result = service.importCategories(file);

        assertEquals(2, result.total());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());

        ArgumentCaptor<MaterialCategoryCreateDTO> captor = ArgumentCaptor.forClass(MaterialCategoryCreateDTO.class);
        verify(categoryService, org.mockito.Mockito.times(2)).create(captor.capture());
        List<MaterialCategoryCreateDTO> dtos = captor.getAllValues();
        assertEquals("CAT-CHEM", dtos.get(0).categoryCode());
        assertEquals(null, dtos.get(0).parentId());
        assertEquals("CAT-ACID", dtos.get(1).categoryCode());
        assertEquals("5", dtos.get(1).parentId());
    }

    @Test
    void importCategoriesSkipsMissingParent() throws IOException {
        MockMultipartFile file = file("import.csv", csvBytes(
                ImportColumns.CATEGORY_HEADER.toArray(new String[0]),
                new String[]{"CAT-CHEM", "化学试剂", ""},
                new String[]{"CAT-X", "酸类", "CAT-MISS"}));
        when(categoryMapper.selectOne(any())).thenReturn(null);

        ImportResultVO result = service.importCategories(file);

        assertEquals(2, result.total());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("父分类编码不存在"));
    }

    @Test
    void importCategoriesRejectsDuplicateCode() throws IOException {
        MockMultipartFile file = file("import.csv", csvBytes(
                ImportColumns.CATEGORY_HEADER.toArray(new String[0]),
                new String[]{"CAT-DUP", "分类一", ""},
                new String[]{"CAT-DUP", "分类二", ""}));

        ImportResultVO result = service.importCategories(file);

        assertEquals(1, result.successCount());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).reason().contains("文件内分类编码重复"));
    }

    // ==================== 导出 ====================

    @Test
    void exportSkusWritesCsvWithHeaderAndRows() throws IOException {
        MaterialSku sku = new MaterialSku();
        sku.setCategoryId(5L);
        sku.setSkuCode("SKU-ETHANOL");
        sku.setMaterialName("无水乙醇 500mL");
        sku.setSpecification("AR，500mL/瓶");
        sku.setBaseUnit("瓶");
        sku.setQuantityScale(2);
        sku.setMaterialType(MaterialType.CONSUMABLE);
        sku.setIsHazardous(1);
        sku.setIsValuable(0);
        sku.setEnabled(1);
        sku.setCreateTime(Instant.parse("2026-10-17T01:00:00Z"));
        when(skuMapper.selectCount(any())).thenReturn(1L);
        when(skuMapper.selectList(any())).thenReturn(List.of(sku));
        when(categoryMapper.selectByIds(any())).thenReturn(List.of(category(5L, "CAT-CHEM")));
        MaterialSkuExportQuery query = new MaterialSkuExportQuery();
        query.setFormat("csv");

        TransferFile file = service.exportSkus(query);

        assertEquals("text/csv", file.contentType());
        assertTrue(file.fileName().startsWith("物料档案导出_"));
        assertTrue(file.fileName().endsWith(".csv"));
        List<List<String>> rows = new CsvRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(ImportColumns.SKU_HEADER, rows.get(0).subList(0, ImportColumns.SKU_HEADER.size()));
        List<String> data = rows.get(1);
        assertEquals("CAT-CHEM", data.get(0));
        assertEquals("SKU-ETHANOL", data.get(1));
        assertEquals("是", data.get(7));
        assertEquals("否", data.get(8));
        assertEquals("启用", data.get(10));
        assertEquals("2026-10-17 09:00:00", data.get(11));
    }

    @Test
    void exportSkusSupportsXlsx() throws IOException {
        MaterialSku sku = new MaterialSku();
        sku.setCategoryId(5L);
        sku.setSkuCode("SKU-ETHANOL");
        sku.setMaterialName("无水乙醇");
        sku.setBaseUnit("瓶");
        sku.setQuantityScale(0);
        sku.setMaterialType(MaterialType.CONSUMABLE);
        sku.setIsHazardous(0);
        sku.setIsValuable(0);
        sku.setEnabled(1);
        when(skuMapper.selectCount(any())).thenReturn(1L);
        when(skuMapper.selectList(any())).thenReturn(List.of(sku));
        when(categoryMapper.selectByIds(any())).thenReturn(List.of(category(5L, "CAT-CHEM")));
        MaterialSkuExportQuery query = new MaterialSkuExportQuery();
        query.setFormat("xlsx");

        TransferFile file = service.exportSkus(query);

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file.contentType());
        assertTrue(file.fileName().endsWith(".xlsx"));
        List<List<String>> rows = new XlsxRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(2, rows.size());
        assertEquals("SKU-ETHANOL", rows.get(1).get(1));
    }

    @Test
    void exportSkusRejectsWhenExceedsLimit() {
        when(skuMapper.selectCount(any())).thenReturn(10001L);
        MaterialSkuExportQuery query = new MaterialSkuExportQuery();
        query.setFormat("csv");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.exportSkus(query));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("导出行数超过上限"));
    }

    @Test
    void exportCategoriesWritesCsvWithHierarchy() throws IOException {
        MaterialCategory parent = category(5L, "CAT-CHEM");
        MaterialCategory child = category(6L, "CAT-ACID");
        child.setParentId(5L);
        when(categoryMapper.selectCount(any())).thenReturn(2L);
        when(categoryMapper.selectList(any())).thenReturn(List.of(parent, child));
        MaterialCategoryExportQuery query = new MaterialCategoryExportQuery();
        query.setFormat("csv");

        TransferFile file = service.exportCategories(query);

        assertEquals("text/csv", file.contentType());
        assertTrue(file.fileName().startsWith("物料分类导出_"));
        List<List<String>> rows = new CsvRowReader().read(new ByteArrayInputStream(file.content()), 10);
        assertEquals(ImportColumns.CATEGORY_HEADER, rows.get(0).subList(0, ImportColumns.CATEGORY_HEADER.size()));
        assertEquals("CAT-CHEM", rows.get(1).get(0));
        assertEquals("", rows.get(1).get(2));
        assertEquals("CAT-ACID", rows.get(2).get(0));
        assertEquals("CAT-CHEM", rows.get(2).get(2));
    }

    // ==================== 模板 ====================

    @Test
    void skuImportTemplateContainsOnlyHeader() throws IOException {
        TransferFile xlsx = service.skuImportTemplate(FileFormat.XLSX);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx.contentType());
        assertTrue(xlsx.fileName().startsWith("物料导入模板"));
        List<List<String>> xlsxRows = new XlsxRowReader().read(new ByteArrayInputStream(xlsx.content()), 10);
        assertEquals(1, xlsxRows.size());
        assertEquals(ImportColumns.SKU_HEADER, xlsxRows.get(0).subList(0, ImportColumns.SKU_HEADER.size()));

        TransferFile csv = service.skuImportTemplate(FileFormat.CSV);
        List<List<String>> csvRows = new CsvRowReader().read(new ByteArrayInputStream(csv.content()), 10);
        assertEquals(1, csvRows.size());
        assertEquals(ImportColumns.SKU_HEADER, csvRows.get(0).subList(0, ImportColumns.SKU_HEADER.size()));
    }

    @Test
    void categoryImportTemplateContainsOnlyHeader() throws IOException {
        TransferFile xlsx = service.categoryImportTemplate(FileFormat.XLSX);
        List<List<String>> xlsxRows = new XlsxRowReader().read(new ByteArrayInputStream(xlsx.content()), 10);
        assertEquals(1, xlsxRows.size());
        assertEquals(ImportColumns.CATEGORY_HEADER, xlsxRows.get(0).subList(0, ImportColumns.CATEGORY_HEADER.size()));

        TransferFile csv = service.categoryImportTemplate(FileFormat.CSV);
        List<List<String>> csvRows = new CsvRowReader().read(new ByteArrayInputStream(csv.content()), 10);
        assertEquals(1, csvRows.size());
        assertEquals(ImportColumns.CATEGORY_HEADER, csvRows.get(0).subList(0, ImportColumns.CATEGORY_HEADER.size()));
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

    private MaterialCategory category(long id, String code) {
        MaterialCategory category = new MaterialCategory();
        category.setId(id);
        category.setCategoryCode(code);
        category.setCategoryName("分类-" + code);
        category.setEnabled(1);
        return category;
    }
}
