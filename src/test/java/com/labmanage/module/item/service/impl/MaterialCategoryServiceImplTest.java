package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.MaterialCategoryCreateDTO;
import com.labmanage.module.item.dto.MaterialCategoryUpdateDTO;
import com.labmanage.module.item.entity.MaterialCategory;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.mapper.MaterialCategoryMapper;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.vo.MaterialCategoryVO;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 物料分类服务单元测试：编码唯一、父分类存在性、树形循环检测、删除引用检查
 */
@ExtendWith(MockitoExtension.class)
class MaterialCategoryServiceImplTest {

    @Mock
    private MaterialCategoryMapper categoryMapper;

    @Mock
    private MaterialSkuMapper skuMapper;

    @InjectMocks
    private MaterialCategoryServiceImpl service;

    /**
     * 纯单测环境无 MyBatis 容器，显式初始化实体元数据（Lambda 列缓存）
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaterialCategory.class);
        TableInfoHelper.initTableInfo(assistant, MaterialSku.class);
    }

    private MaterialCategory category(long id, Long parentId) {
        MaterialCategory category = new MaterialCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setCategoryCode("CAT-" + id);
        category.setCategoryName("分类" + id);
        category.setEnabled(1);
        return category;
    }

    @Test
    void createRejectsDuplicateCode() {
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new MaterialCategoryCreateDTO("CAT-CHEM", "化学试剂", null)));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("分类编码已存在"));
        verify(categoryMapper, never()).insert(any(MaterialCategory.class));
    }

    @Test
    void createMapsDuplicateKeyToConflict() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_item_category_code"))
                .when(categoryMapper).insert(any(MaterialCategory.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new MaterialCategoryCreateDTO("CAT-CHEM", "化学试剂", null)));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("分类编码已存在"));
    }

    @Test
    void createRootCategorySuccess() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        service.create(new MaterialCategoryCreateDTO(" CAT-CHEM ", "化学试剂", null));

        ArgumentCaptor<MaterialCategory> captor = ArgumentCaptor.forClass(MaterialCategory.class);
        verify(categoryMapper).insert(captor.capture());
        MaterialCategory saved = captor.getValue();
        assertEquals("CAT-CHEM", saved.getCategoryCode());
        assertEquals("化学试剂", saved.getCategoryName());
        assertNull(saved.getParentId());
        assertEquals(1, saved.getEnabled());
    }

    @Test
    void createRejectsMissingParent() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectById(9L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new MaterialCategoryCreateDTO("CAT-CHEM", "化学试剂", "9")));

        assertEquals(40400, ex.getCode());
        assertTrue(ex.getMessage().contains("物料分类不存在"));
        verify(categoryMapper, never()).insert(any(MaterialCategory.class));
    }

    @Test
    void updateRejectsSelfAsParent() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, null));
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(new MaterialCategoryUpdateDTO("1", "CAT-CHEM", "化学试剂", "1")));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("父分类不能是自身"));
        verify(categoryMapper, never()).updateById(any(MaterialCategory.class));
    }

    @Test
    void updateRejectsParentCycle() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, null));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, 3L));
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, 1L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(new MaterialCategoryUpdateDTO("1", "CAT-CHEM", "化学试剂", "2")));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("父分类设置形成循环"));
        verify(categoryMapper, never()).updateById(any(MaterialCategory.class));
    }

    @Test
    void updateSuccess() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, null));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, null));

        service.update(new MaterialCategoryUpdateDTO("1", "CAT-CHEM-2", "化学试剂", "2"));

        ArgumentCaptor<MaterialCategory> captor = ArgumentCaptor.forClass(MaterialCategory.class);
        verify(categoryMapper).updateById(captor.capture());
        MaterialCategory updated = captor.getValue();
        assertEquals(1L, updated.getId());
        assertEquals(2L, updated.getParentId());
        assertEquals("CAT-CHEM-2", updated.getCategoryCode());
    }

    @Test
    void updateMapsDuplicateKeyToConflict() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, null));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_item_category_code"))
                .when(categoryMapper).updateById(any(MaterialCategory.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(new MaterialCategoryUpdateDTO("1", "CAT-CHEM-2", "化学试剂", null)));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("分类编码已存在"));
    }

    @Test
    void updateStatusRejectsMissingCategory() {
        when(categoryMapper.selectById(5L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateStatus("5", false));

        assertEquals(40400, ex.getCode());
        verify(categoryMapper, never()).updateById(any(MaterialCategory.class));
    }

    @Test
    void updateStatusSuccess() {
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, null));

        service.updateStatus("5", false);

        ArgumentCaptor<MaterialCategory> captor = ArgumentCaptor.forClass(MaterialCategory.class);
        verify(categoryMapper).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals(0, captor.getValue().getEnabled());
    }

    @Test
    void getSuccess() {
        when(categoryMapper.selectById(5L)).thenReturn(category(5L, null));

        MaterialCategoryVO vo = service.get("5");

        assertEquals("5", vo.id());
        assertEquals("CAT-5", vo.categoryCode());
        assertEquals("分类5", vo.categoryName());
        assertEquals(Boolean.TRUE, vo.enabled());
    }

    @Test
    void getRejectsMissingCategory() {
        when(categoryMapper.selectById(5L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.get("5"));

        assertEquals(40400, ex.getCode());
        assertTrue(ex.getMessage().contains("物料分类不存在"));
    }

    @Test
    void deleteRejectsMissingCategory() {
        when(categoryMapper.selectById(5L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("5"));

        assertEquals(40400, ex.getCode());
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsChildCategory() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, null));
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("存在子分类"));
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsSkuReference() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, null));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(skuMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("存在物料"));
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteSuccess() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, null));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(skuMapper.selectCount(any())).thenReturn(0L);

        service.delete("1");

        verify(categoryMapper).deleteById(1L);
    }
}
