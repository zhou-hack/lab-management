package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialQuotaQuery;
import com.labmanage.module.item.dto.MaterialQuotaUpdateDTO;
import com.labmanage.module.item.dto.MaterialQuotaUpsertDTO;
import com.labmanage.module.item.entity.MaterialCategory;
import com.labmanage.module.item.entity.MaterialQuota;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.mapper.MaterialCategoryMapper;
import com.labmanage.module.item.mapper.MaterialQuotaMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.vo.MaterialQuotaVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 领用定额服务单元测试（纯 Mockito：upsert 存量批量加载、启停、分页 VO 组装）
 */
@ExtendWith(MockitoExtension.class)
class MaterialQuotaServiceImplTest {

    @Mock
    private MaterialQuotaMapper quotaMapper;

    @Mock
    private MaterialSkuMapper skuMapper;

    @Mock
    private MaterialCategoryMapper categoryMapper;

    @InjectMocks
    private MaterialQuotaServiceImpl service;

    /**
     * 纯单测环境无 MyBatis 容器，显式初始化实体元数据（分页 Lambda 列缓存）
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaterialQuota.class);
    }

    // ---------------------------------------------------------------- helpers

    private MaterialSku sku(long id) {
        MaterialSku sku = new MaterialSku();
        sku.setId(id);
        sku.setSkuCode("SKU-ETHANOL-500ML");
        sku.setMaterialName("无水乙醇 500mL");
        sku.setSpecification("AR 级，500mL/瓶");
        sku.setBaseUnit("瓶");
        sku.setCategoryId(7L);
        sku.setEnabled(1);
        return sku;
    }

    private MaterialCategory category(long id, String name) {
        MaterialCategory category = new MaterialCategory();
        category.setId(id);
        category.setCategoryName(name);
        return category;
    }

    private MaterialQuota quota(long id, long offeringId, long skuId, String qty, int enabled) {
        MaterialQuota quota = new MaterialQuota();
        quota.setId(id);
        quota.setOfferingId(offeringId);
        quota.setSkuId(skuId);
        quota.setQtyPerStudentPeriod(new BigDecimal(qty));
        quota.setEnabled(enabled);
        return quota;
    }

    private BusinessException assertBizError(int expectedCode, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedCode, ex.getCode());
        return ex;
    }

    // ----------------------------------------------------------------- upsert

    @Test
    void upsertInsertsNewQuotaWithEnabledFlag() {
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));
        when(quotaMapper.selectByOfferingAndSkuIds(eq(0L), anyCollection()))
                .thenReturn(List.of());

        service.upsert(new MaterialQuotaUpsertDTO(
                List.of(new MaterialQuotaUpsertDTO.QuotaLineDTO("1", new BigDecimal("0.05")))));

        ArgumentCaptor<MaterialQuota> captor = ArgumentCaptor.forClass(MaterialQuota.class);
        verify(quotaMapper).insert(captor.capture());
        MaterialQuota saved = captor.getValue();
        assertEquals(0L, saved.getOfferingId());
        assertEquals(1L, saved.getSkuId());
        assertEquals(0, saved.getQtyPerStudentPeriod().compareTo(new BigDecimal("0.05")));
        assertEquals(1, saved.getEnabled());
    }

    @Test
    void upsertUpdatesExistingQuotaWithoutInsert() {
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));
        MaterialQuota existing = quota(5L, 66L, 1L, "0.02", 0);
        when(quotaMapper.selectByOfferingAndSkuIds(eq(0L), anyCollection()))
                .thenReturn(List.of(existing));

        service.upsert(new MaterialQuotaUpsertDTO(
                List.of(new MaterialQuotaUpsertDTO.QuotaLineDTO("1", new BigDecimal("0.05")))));

        // 已有定额走更新，保留原启用标志，不新增
        ArgumentCaptor<MaterialQuota> captor = ArgumentCaptor.forClass(MaterialQuota.class);
        verify(quotaMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getQtyPerStudentPeriod()
                .compareTo(new BigDecimal("0.05")));
        assertEquals(0, captor.getValue().getEnabled());
        verify(quotaMapper, never()).insert(any(MaterialQuota.class));
    }

    @Test
    void upsertMixedInsertAndUpdateInOneCall() {
        when(skuMapper.selectByIds(anyCollection()))
                .thenReturn(List.of(sku(1L), sku(2L)));
        MaterialQuota existing = quota(5L, 66L, 1L, "0.02", 1);
        when(quotaMapper.selectByOfferingAndSkuIds(eq(0L), anyCollection()))
                .thenReturn(List.of(existing));

        service.upsert(new MaterialQuotaUpsertDTO(List.of(
                new MaterialQuotaUpsertDTO.QuotaLineDTO("1", new BigDecimal("0.03")),
                new MaterialQuotaUpsertDTO.QuotaLineDTO("2", new BigDecimal("0.04")))));

        // 存量定额一次加载：既有行更新、新行插入，各一次
        verify(quotaMapper).updateById(existing);
        ArgumentCaptor<MaterialQuota> insertCaptor = ArgumentCaptor.forClass(MaterialQuota.class);
        verify(quotaMapper).insert(insertCaptor.capture());
        assertEquals(2L, insertCaptor.getValue().getSkuId());
        assertEquals(0, existing.getQtyPerStudentPeriod().compareTo(new BigDecimal("0.03")));
    }

    @Test
    void upsertRejectsDuplicateSkuLines() {
        assertBizError(40000, () -> service.upsert(new MaterialQuotaUpsertDTO(List.of(
                new MaterialQuotaUpsertDTO.QuotaLineDTO("1", BigDecimal.ONE),
                new MaterialQuotaUpsertDTO.QuotaLineDTO("1", BigDecimal.TEN)))));

        verifyNoInteractions(quotaMapper);
    }

    @Test
    void upsertRejectsMissingSku() {
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of());

        BusinessException ex = assertBizError(40400, () -> service.upsert(
                new MaterialQuotaUpsertDTO(
                        List.of(new MaterialQuotaUpsertDTO.QuotaLineDTO("9", BigDecimal.ONE)))));

        assertTrue(ex.getMessage().contains("物料不存在"));
        verify(quotaMapper, never()).insert(any(MaterialQuota.class));
    }

    @Test
    void upsertDuplicateKeyMapsToConflict() {
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));
        when(quotaMapper.selectByOfferingAndSkuIds(eq(0L), anyCollection()))
                .thenReturn(List.of());
        when(quotaMapper.insert(any(MaterialQuota.class)))
                .thenThrow(new DuplicateKeyException("uk_item_quota"));

        BusinessException ex = assertBizError(40900, () -> service.upsert(
                new MaterialQuotaUpsertDTO(
                        List.of(new MaterialQuotaUpsertDTO.QuotaLineDTO("1", BigDecimal.ONE)))));

        assertTrue(ex.getMessage().contains("请勿重复登记"));
    }

    // ---------------------------------------------------------------- update

    @Test
    void updateRejectsMissingQuota() {
        when(quotaMapper.selectById(9L)).thenReturn(null);

        assertBizError(40400, () -> service.update("9",
                new MaterialQuotaUpdateDTO(new BigDecimal("0.5"))));

        verify(quotaMapper, never()).updateById(any(MaterialQuota.class));
    }

    @Test
    void updateSavesNewQty() {
        MaterialQuota existing = quota(5L, 66L, 1L, "0.02", 1);
        when(quotaMapper.selectById(5L)).thenReturn(existing);

        service.update("5", new MaterialQuotaUpdateDTO(new BigDecimal("0.5")));

        ArgumentCaptor<MaterialQuota> captor = ArgumentCaptor.forClass(MaterialQuota.class);
        verify(quotaMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getQtyPerStudentPeriod()
                .compareTo(new BigDecimal("0.5")));
    }

    // ---------------------------------------------------------- enable/disable

    @Test
    void enableRejectsMissingQuota() {
        when(quotaMapper.selectById(9L)).thenReturn(null);

        assertBizError(40400, () -> service.enable("9"));
    }

    @Test
    void enableAndDisableToggleFlag() {
        MaterialQuota existing = quota(5L, 66L, 1L, "0.02", 0);
        when(quotaMapper.selectById(5L)).thenReturn(existing);

        service.enable("5");
        assertEquals(1, existing.getEnabled());

        service.disable("5");
        assertEquals(0, existing.getEnabled());
        verify(quotaMapper, org.mockito.Mockito.times(2)).updateById(existing);
    }

    // ------------------------------------------------------------------- page

    @Test
    void pageMapsVoWithSkuInfo() {
        MaterialQuotaQuery query = new MaterialQuotaQuery();
        Page<MaterialQuota> page = new Page<>(1, 20);
        page.setRecords(List.of(quota(5L, 66L, 1L, "0.05", 1)));
        page.setTotal(1);
        doReturn(page).when(quotaMapper).selectPage(any(IPage.class), any());
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of(sku(1L)));
        when(categoryMapper.selectByIds(anyCollection())).thenReturn(List.of(category(7L, "化学试剂")));

        PageResult<MaterialQuotaVO> result = service.page(query);

        assertEquals(1L, result.total());
        MaterialQuotaVO vo = result.records().get(0);
        assertEquals("5", vo.id());
        assertEquals("66", vo.offeringId());
        assertEquals("SKU-ETHANOL-500ML", vo.skuCode());
        assertEquals("无水乙醇 500mL", vo.skuName());
        assertEquals("AR 级，500mL/瓶", vo.specification());
        assertEquals("瓶", vo.baseUnit());
        assertEquals("7", vo.categoryId());
        assertEquals("化学试剂", vo.categoryName());
        assertEquals(1, vo.enabled());
    }

    @Test
    void pageToleratesMissingSkuOnVo() {
        // SKU 已被删除（快照引用）时 VO 物料字段为 null，不抛错
        MaterialQuotaQuery query = new MaterialQuotaQuery();
        Page<MaterialQuota> page = new Page<>(1, 20);
        page.setRecords(List.of(quota(5L, 66L, 9L, "0.05", 1)));
        page.setTotal(1);
        doReturn(page).when(quotaMapper).selectPage(any(IPage.class), any());
        when(skuMapper.selectByIds(anyCollection())).thenReturn(List.of());

        PageResult<MaterialQuotaVO> result = service.page(query);

        MaterialQuotaVO vo = result.records().get(0);
        assertEquals("5", vo.id());
        assertNull(vo.skuCode());
        assertNull(vo.skuName());
        assertNull(vo.baseUnit());
    }

    @Test
    void pageEmptyRecordsSkipsSkuLoad() {
        MaterialQuotaQuery query = new MaterialQuotaQuery();
        Page<MaterialQuota> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        doReturn(page).when(quotaMapper).selectPage(any(IPage.class), any());

        PageResult<MaterialQuotaVO> result = service.page(query);

        assertEquals(0L, result.total());
        verifyNoInteractions(skuMapper);
    }

    @Test
    void pageRejectsInvalidOfferingId() {
        MaterialQuotaQuery query = new MaterialQuotaQuery();
        query.setOfferingId("abc");

        assertBizError(40000, () -> service.page(query));

        verify(quotaMapper, never()).selectPage(any(IPage.class), any());
    }
}
