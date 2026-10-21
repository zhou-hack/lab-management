package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.module.item.dto.StockLocationCreateDTO;
import com.labmanage.module.item.dto.StockLocationUpdateDTO;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.entity.StockDocumentLine;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.SysUserRefMapper;
import com.labmanage.module.item.vo.StockLocationVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库位服务单元测试：同实验室库位码唯一（含未挂实验室的 NULL 组）、存在性校验、启停、
 * 删除引用检查、保管人维护（写入与姓名解析）
 */
@ExtendWith(MockitoExtension.class)
class StockLocationServiceImplTest {

    @Mock
    private StockLocationMapper locationMapper;

    @Mock
    private StockBalanceMapper balanceMapper;

    @Mock
    private StockDocumentLineMapper documentLineMapper;

    @Mock
    private SysUserRefMapper sysUserRefMapper;

    @InjectMocks
    private StockLocationServiceImpl service;

    /**
     * 纯单测环境无 MyBatis 容器，显式初始化实体元数据（Lambda 列缓存）
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, StockLocation.class);
        TableInfoHelper.initTableInfo(assistant, StockBalance.class);
        TableInfoHelper.initTableInfo(assistant, StockDocumentLine.class);
    }

    private StockLocation location(long id) {
        StockLocation location = new StockLocation();
        location.setId(id);
        location.setLabId(7L);
        location.setLocationCode("A-01");
        location.setLocationName("一号实验室 A 区药品柜");
        location.setEnabled(1);
        return location;
    }

    @Test
    void createGeneralLocationWithoutLab() {
        when(locationMapper.selectCount(any())).thenReturn(0L);

        service.create(new StockLocationCreateDTO(null, " A-01 ", "通用药品柜", "一层", null));

        ArgumentCaptor<StockLocation> captor = ArgumentCaptor.forClass(StockLocation.class);
        verify(locationMapper).insert(captor.capture());
        StockLocation saved = captor.getValue();
        assertNull(saved.getLabId());
        assertNull(saved.getCustodianId());
        assertEquals("A-01", saved.getLocationCode());
        assertEquals("通用药品柜", saved.getLocationName());
        assertEquals(1, saved.getEnabled());
    }

    @Test
    void createWritesCustodian() {
        when(locationMapper.selectCount(any())).thenReturn(0L);

        service.create(new StockLocationCreateDTO("7", "A-01", "药品柜", null, "9"));

        ArgumentCaptor<StockLocation> captor = ArgumentCaptor.forClass(StockLocation.class);
        verify(locationMapper).insert(captor.capture());
        assertEquals(9L, captor.getValue().getCustodianId());
    }

    @Test
    void createRejectsDuplicateCodeInSameLab() {
        when(locationMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new StockLocationCreateDTO("7", "A-01", "药品柜", null, null)));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("库位码已存在"));
        verify(locationMapper, never()).insert(any(StockLocation.class));
    }

    @Test
    void createMapsDuplicateKeyToConflict() {
        when(locationMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_item_location_code"))
                .when(locationMapper).insert(any(StockLocation.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new StockLocationCreateDTO(null, "A-01", "通用药品柜", "一层", null)));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("库位码已存在"));
    }

    @Test
    void createRejectsInvalidLabId() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new StockLocationCreateDTO("abc", "A-01", "药品柜", null, null)));

        assertEquals(40000, ex.getCode());
        verify(locationMapper, never()).insert(any(StockLocation.class));
    }

    @Test
    void createRejectsInvalidCustodianId() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(new StockLocationCreateDTO(null, "A-01", "药品柜", null, "xyz")));

        assertEquals(40000, ex.getCode());
        verify(locationMapper, never()).insert(any(StockLocation.class));
    }

    @Test
    void updateRejectsMissingLocation() {
        when(locationMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(new StockLocationUpdateDTO("1", "7", "A-02", "药品柜", null, null)));

        assertEquals(40400, ex.getCode());
        assertTrue(ex.getMessage().contains("库位不存在"));
    }

    @Test
    void updateRejectsDuplicateCode() {
        when(locationMapper.selectById(1L)).thenReturn(location(1L));
        when(locationMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(new StockLocationUpdateDTO("1", "7", "A-02", "药品柜", null, null)));

        assertEquals(40900, ex.getCode());
        verify(locationMapper, never()).update(any(), any());
    }

    @Test
    void updateSuccess() {
        when(locationMapper.selectById(1L)).thenReturn(location(1L));
        when(locationMapper.selectCount(any())).thenReturn(0L);

        service.update(new StockLocationUpdateDTO("1", "7", " A-02 ", "药品柜", "一层", "9"));

        // 全量编辑语义：UpdateWrapper 显式 set 每个字段（传 null 即清除）
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Wrapper<StockLocation>> captor = ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(locationMapper).update(isNull(), captor.capture());
        String sqlSet = ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StockLocation>)
                captor.getValue()).getSqlSet();
        assertTrue(sqlSet.contains("lab_id"));
        assertTrue(sqlSet.contains("location_code"));
        assertTrue(sqlSet.contains("custodian_id"));
        var params = ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StockLocation>)
                captor.getValue()).getParamNameValuePairs();
        assertTrue(params.containsValue("A-02"));
        assertTrue(params.containsValue(9L));
    }

    @Test
    void updateMapsDuplicateKeyToConflict() {
        when(locationMapper.selectById(1L)).thenReturn(location(1L));
        when(locationMapper.selectCount(any())).thenReturn(0L);
        doThrow(new DuplicateKeyException("uk_item_location_code"))
                .when(locationMapper).update(any(), any());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(new StockLocationUpdateDTO("1", "7", "A-02", "药品柜", "一层", null)));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("库位码已存在"));
    }

    @Test
    void updateStatusSuccess() {
        when(locationMapper.selectById(5L)).thenReturn(location(5L));

        service.updateStatus("5", false);

        ArgumentCaptor<StockLocation> captor = ArgumentCaptor.forClass(StockLocation.class);
        verify(locationMapper).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals(0, captor.getValue().getEnabled());
    }

    @Test
    void getSuccess() {
        StockLocation stored = location(5L);
        stored.setCustodianId(9L);
        when(locationMapper.selectById(5L)).thenReturn(stored);
        when(sysUserRefMapper.selectNamesByIds(anyList()))
                .thenReturn(List.of(new SysUserRefMapper.UserRef(9L, "张老师")));

        StockLocationVO vo = service.get("5");

        assertEquals("5", vo.id());
        assertEquals("A-01", vo.locationCode());
        assertEquals("一号实验室 A 区药品柜", vo.locationName());
        assertEquals("9", vo.custodianId());
        assertEquals("张老师", vo.custodianName());
        assertEquals(Boolean.TRUE, vo.enabled());
    }

    @Test
    void getRejectsMissingLocation() {
        when(locationMapper.selectById(5L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.get("5"));

        assertEquals(40400, ex.getCode());
        assertTrue(ex.getMessage().contains("库位不存在"));
    }

    @Test
    void deleteRejectsMissingLocation() {
        when(locationMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40400, ex.getCode());
        verify(locationMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsBalanceReference() {
        when(locationMapper.selectById(1L)).thenReturn(location(1L));
        when(balanceMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("库存余额"));
        verify(locationMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteRejectsDocumentLineReference() {
        when(locationMapper.selectById(1L)).thenReturn(location(1L));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(documentLineMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete("1"));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("单据明细"));
        verify(locationMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteSuccess() {
        when(locationMapper.selectById(1L)).thenReturn(location(1L));
        when(balanceMapper.selectCount(any())).thenReturn(0L);
        when(documentLineMapper.selectCount(any())).thenReturn(0L);

        service.delete("1");

        verify(locationMapper).deleteById(1L);
    }
}
