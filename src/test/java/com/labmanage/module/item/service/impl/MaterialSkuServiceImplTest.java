package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.MaterialSkuCreateDTO;
import com.labmanage.module.item.dto.MaterialSkuUpdateDTO;
import com.labmanage.module.item.entity.MaterialCategory;
import com.labmanage.module.item.entity.MaterialQuota;
import com.labmanage.module.item.entity.MaterialRequestLine;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StocktakeLine;
import com.labmanage.module.item.enums.MaterialType;
import com.labmanage.module.item.mapper.MaterialCategoryMapper;
import com.labmanage.module.item.mapper.MaterialQuotaMapper;
import com.labmanage.module.item.mapper.MaterialRequestLineMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StocktakeLineMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物料 SKU 服务单元测试：SKU 码唯一、分类校验、启停、删除引用检查
 */
@ExtendWith(MockitoExtension.class)
class MaterialSkuServiceImplTest {

    @Mock
    private MaterialSkuMapper skuMapper;

    @Mock
    private MaterialCategoryMapper categoryMapper;

    @Mock
    private StockBalanceMapper balanceMapper;

    @Mock
    private StockDocumentLineMapper documentLineMapper;

    @Mock
    private MaterialRequestLineMapper requestLineMapper;

    @Mock
    private MaterialQuotaMapper quotaMapper;

    @Mock
    private StocktakeLineMapper stocktakeLineMapper;

    @InjectMocks
    private MaterialSkuServiceImpl service;

    /**
     * 纯单测环境无 MyBatis 容器，显式初始化实体元数据（Lambda 列缓存）
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaterialSku.class);
        TableInfoHelper.initTableInfo(assistant, StockBalance.class);
        TableInfoHelper.initTableInfo(assistant, StockDocumentLine.class);
        TableInfoHelper.initTableInfo(assistant, MaterialRequestLine.class);
        TableInfoHelper.initTableInfo(assistant, MaterialQuota.class);
        TableInfoHelper.initTableInfo(assistant, StocktakeLine.class);
    }

    private MaterialSkuCreateDTO createDto(String categoryId, String skuCode) {
        return new MaterialSkuCreateDTO(categoryId, skuCode, "无水乙醇 500mL",
                "AR，500mL/瓶", " 瓶 ", 2, MaterialType.CONSUMABLE, true, false);
    }

    private MaterialSkuUpdateDTO updateDto(String id, String categoryId, String skuCode) {
        return new MaterialSkuUpdateDTO(id, categoryId, skuCode, "无水乙醇 500mL",
                "AR，500mL/瓶", "瓶", 2, MaterialType.CONSUMABLE, false, false);
    }

    @Test
    void createRejectsDuplicateSkuCode() {
        when(skuMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(createDto("5", "SKU-ETHANOL-500ML")));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("SKU 码已存在"));
        verify(skuMapper, never()).insert(any(MaterialSku.class));
    }

    @Test
    void createMapsDuplicateKeyToConflict() {
        when(skuMapper.selectCount(any())).thenReturn(0L);
        MaterialCategory category = new MaterialCategory();
        category.setId(5L);
        category.setCategoryCode("CAT-CHEM");
        category.setCategoryName("化学试剂");
        category.setEnabled(1);
        when(categoryMapper.selectById(5L)).thenReturn(category);
        doThrow(new DuplicateKeyException("uk_item_sku_code"))
                .when(skuMapper).insert(any(MaterialSku.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(createDto("5", "SKU-ETHANOL-500ML")));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("SKU 码已存在"));
    }

    @Test
    void createRejectsMissingCategory() {
        when(skuMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectById(9L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(createDto("9", "SKU-ETHANOL-500ML")));

        assertEquals(40400, ex.getCode());
        assertTrue(ex.getMessage().contains("物料分类不存在"));
        verify(skuMapper, never()).insert(any(MaterialSku.class));
    }

    @Test
    void createSuccess() {
        when(skuMapper.selectCount(any())).thenReturn(0L);
        MaterialCategory category = new MaterialCategory();
        category.setId(5L);
        category.setCategoryCode("CAT-CHEM");
        category.setCategoryName("化学试剂");
        category.setEnabled(1);
        when(categoryMapper.selectById(5L)).thenReturn(category);

        service.create(createDto("5", " SKU-ETHANOL-500ML "));

        ArgumentCaptor<MaterialSku> captor = ArgumentCaptor.forClass(MaterialSku.class);
        verify(skuMapper).insert(captor.capture());
        MaterialSku saved = captor.getValue();
        assertEquals(5L, saved.getCategoryId());
        assertEquals("SKU-ETHANOL-500ML", saved.getSkuCode());
        assertEquals("瓶", saved.getBaseUnit());
        assertEquals(2, saved.getQuantityScale());
        assertEquals(MaterialType.CONSUMABLE, saved.getMaterialType());
        assertEquals(1, saved.getIsHazardous());
        assertEquals(0, saved.getIsValuable());
        assertEquals(1, saved.getEnabled());
    }

    @Test
    void updateRejectsMissingSku() {
        when(skuMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(updateDto("1", "5", "SKU-ETHANOL-500ML")));

        assertEquals(40400, ex.getCode());
        assertTrue(ex.getMessage().contains("物料不存在"));
        verify(skuMapper, never()).updateById(any(MaterialSku.class));
    }

    @Test
    void updateSuccess() {
        MaterialSku existing = new MaterialSku();
        existing.setId(1L);
        existing.setEnabled(1);
        when(skuMapper.selectById(1L)).thenReturn(existing);
        when(skuMapper.selectCount(any())).thenReturn(0L);
        MaterialCategory category = new MaterialCategory();
        category.setId(5L);
        category.setCategoryCode("CAT-CHEM");
        category.setCategoryName("化学试剂");
        category.setEnabled(1);
        when(categoryMapper.selectById(5L)).thenReturn(category);

        service.update(updateDto("1", "5", "SKU-ETHANOL-1L"));

        ArgumentCaptor<MaterialSku> captor = ArgumentCaptor.forClass(MaterialSku.class);
        verify(skuMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals("SKU-ETHANOL-1L", captor.getValue().getSkuCode());
    }

    @Test
    void updateMapsDuplicateKeyToConflict() {
        when(skuMapper.selectById(1L)).thenReturn(existingSku(1L));
        when(skuMapper.selectCount(any())).thenReturn(0L);
        MaterialCategory category = new MaterialCategory();
        category.setId(5L);
        category.setCategoryCode("CAT-CHEM");
        category.setCategoryName("化学试剂");
        category.setEnabled(1);
        when(categoryMapper.selectById(5L)).thenReturn(category);
        doThrow(new DuplicateKeyException("uk_item_sku_code"))
                .when(skuMapper).updateById(any(MaterialSku.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(updateDto("1", "5", "SKU-ETHANOL-1L")));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("SKU 码已存在"));
    }

    private MaterialSku existingSku(long id) {
        MaterialSku sku = new MaterialSku();
        sku.setId(id);
        sku.setEnabled(1);
        return sku;
    }

    @Test
    void deleteRejectsMissingSku() {
        when(skuMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40400, ex.getCode());
        assertTrue(ex.getMessage().contains("物料不存在"));
    }

    @Test
    void deleteRejectsBalanceReference() {
        when(skuMapper.selectById(1L)).thenReturn(existingSku(1L));
        when(balanceMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("库存余额"));
        verify(skuMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsDocumentLineReference() {
        when(skuMapper.selectById(1L)).thenReturn(existingSku(1L));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(documentLineMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("单据明细"));
        verify(skuMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsRequestLineReference() {
        when(skuMapper.selectById(1L)).thenReturn(existingSku(1L));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(documentLineMapper.selectCount(any())).thenReturn(0L);
        when(requestLineMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("申领记录"));
        verify(skuMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsQuotaReference() {
        when(skuMapper.selectById(1L)).thenReturn(existingSku(1L));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(documentLineMapper.selectCount(any())).thenReturn(0L);
        when(requestLineMapper.selectCount(any())).thenReturn(0L);
        when(quotaMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("领用定额"));
        verify(skuMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsStocktakeLineReference() {
        when(skuMapper.selectById(1L)).thenReturn(existingSku(1L));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(documentLineMapper.selectCount(any())).thenReturn(0L);
        when(requestLineMapper.selectCount(any())).thenReturn(0L);
        when(quotaMapper.selectCount(any())).thenReturn(0L);
        when(stocktakeLineMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("盘点记录"));
        verify(skuMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteSuccess() {
        when(skuMapper.selectById(1L)).thenReturn(existingSku(1L));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(documentLineMapper.selectCount(any())).thenReturn(0L);
        when(requestLineMapper.selectCount(any())).thenReturn(0L);
        when(quotaMapper.selectCount(any())).thenReturn(0L);
        when(stocktakeLineMapper.selectCount(any())).thenReturn(0L);

        service.delete("1");

        verify(skuMapper).deleteById(1L);
    }
}
