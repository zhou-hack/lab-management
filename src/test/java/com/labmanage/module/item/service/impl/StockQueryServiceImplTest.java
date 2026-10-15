package com.labmanage.module.item.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.StockBalanceQuery;
import com.labmanage.module.item.dto.StockBalanceWarningUpdateDTO;
import com.labmanage.module.item.dto.StockWarningQuery;
import com.labmanage.module.item.entity.MaterialSku;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.mapper.MaterialSkuMapper;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import com.labmanage.module.item.mapper.StockDocumentLineMapper;
import com.labmanage.module.item.mapper.StockLocationMapper;
import com.labmanage.module.item.mapper.StockMovementMapper;
import com.labmanage.module.item.vo.StockBalanceVO;
import com.labmanage.module.item.vo.StockWarningVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 库存查询服务单元测试：预警级别判定、建议补货量、实验室无库位短路
 */
@ExtendWith(MockitoExtension.class)
class StockQueryServiceImplTest {

    @Mock
    private StockBalanceMapper balanceMapper;

    @Mock
    private StockMovementMapper movementMapper;

    @Mock
    private MaterialSkuMapper skuMapper;

    @Mock
    private StockLocationMapper locationMapper;

    @Mock
    private StockDocumentLineMapper lineMapper;

    @InjectMocks
    private StockQueryServiceImpl service;

    /**
     * 纯单测环境无 MyBatis 容器，显式初始化实体元数据（Lambda 列缓存）
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, StockBalance.class);
        TableInfoHelper.initTableInfo(assistant, com.labmanage.module.item.entity.StockLocation.class);
    }

    private StockBalance balance(long id, long locationId, long skuId, String qty, String safe, String target) {
        StockBalance balance = new StockBalance();
        balance.setId(id);
        balance.setLocationId(locationId);
        balance.setSkuId(skuId);
        balance.setStockQty(new BigDecimal(qty));
        balance.setSafeQty(new BigDecimal(safe));
        balance.setTargetQty(new BigDecimal(target));
        balance.setMovingAveragePrice(BigDecimal.ZERO);
        balance.setVersion(1);
        balance.setUpdateTime(Instant.parse("2026-10-13T00:00:00.000Z"));
        return balance;
    }

    @Test
    void pageBalanceComputesWarningLevelAndSuggestedQty() {
        StockBalanceQuery query = new StockBalanceQuery();
        Page<StockBalance> page = new Page<>(1, 20);
        page.setRecords(List.of(
                balance(1, 10, 1, "15", "10", "20"),
                balance(2, 10, 2, "8", "10", "20"),
                balance(3, 10, 3, "0", "10", "20")));
        page.setTotal(3);
        doReturn(page).when(balanceMapper).selectPage(any(), any());

        PageResult<StockBalanceVO> result = service.pageBalance(query);

        assertEquals(3L, result.total());
        List<StockBalanceVO> records = result.records();
        // 高于安全库存 → 正常
        assertEquals("NORMAL", records.get(0).warningLevel());
        assertEquals(0, records.get(0).suggestedQty().compareTo(new BigDecimal("5")));
        // 低于安全库存 → 预警
        assertEquals("WARNING", records.get(1).warningLevel());
        assertEquals(0, records.get(1).suggestedQty().compareTo(new BigDecimal("12")));
        // 零库存 → 缺货
        assertEquals("OUT_OF_STOCK", records.get(2).warningLevel());
        assertEquals(0, records.get(2).suggestedQty().compareTo(new BigDecimal("20")));
    }

    @Test
    void pageBalanceSuggestedQtyNeverNegative() {
        StockBalanceQuery query = new StockBalanceQuery();
        Page<StockBalance> page = new Page<>(1, 20);
        // 库存 25 已超目标 20 → 建议补货量取 0
        page.setRecords(List.of(balance(1, 10, 1, "25", "10", "20")));
        page.setTotal(1);
        doReturn(page).when(balanceMapper).selectPage(any(), any());

        PageResult<StockBalanceVO> result = service.pageBalance(query);

        assertEquals(0, result.records().get(0).suggestedQty().compareTo(BigDecimal.ZERO));
    }

    @Test
    void pageWarningMarksShortageSeparately() {
        StockWarningQuery query = new StockWarningQuery();
        Page<StockBalance> page = new Page<>(1, 20);
        page.setRecords(List.of(
                balance(1, 10, 1, "5", "10", "20"),
                balance(2, 10, 2, "0", "10", "20")));
        page.setTotal(2);
        doReturn(page).when(balanceMapper).selectPage(any(), any());

        PageResult<StockWarningVO> result = service.pageWarning(query);

        List<StockWarningVO> records = result.records();
        assertEquals(2L, result.total());
        assertEquals("WARNING", records.get(0).level());
        assertEquals(0, records.get(0).suggestedQty().compareTo(new BigDecimal("15")));
        assertEquals("OUT_OF_STOCK", records.get(1).level());
        assertEquals(0, records.get(1).suggestedQty().compareTo(new BigDecimal("20")));
    }

    @Test
    void pageBalanceShortCircuitsWhenLabHasNoLocations() {
        StockBalanceQuery query = new StockBalanceQuery();
        query.setLabId("7");
        // selectList 默认返回空集合 → 实验室无库位
        doReturn(List.of()).when(locationMapper).selectList(any());

        PageResult<StockBalanceVO> result = service.pageBalance(query);

        assertTrue(result.records().isEmpty());
        assertEquals(0L, result.total());
        verify(balanceMapper, never()).selectPage(any(), any());
    }

    // ==================== 预警配置（updateWarningConfig） ====================

    private MaterialSku sku(long id, int quantityScale) {
        MaterialSku sku = new MaterialSku();
        sku.setId(id);
        sku.setQuantityScale(quantityScale);
        return sku;
    }

    @Test
    void updateWarningConfigSuccess() {
        StockBalance existing = balance(500L, 10, 1, "15", "10", "20");
        doReturn(existing).when(balanceMapper).selectById(500L);
        doReturn(sku(1, 3)).when(skuMapper).selectById(1L);
        when(balanceMapper.updateWarningConfig(500L, 1,
                new BigDecimal("12.5"), new BigDecimal("30"))).thenReturn(1);

        service.updateWarningConfig("500",
                new StockBalanceWarningUpdateDTO(new BigDecimal("12.5"), new BigDecimal("30")));

        verify(balanceMapper).updateWarningConfig(500L, 1,
                new BigDecimal("12.5"), new BigDecimal("30"));
    }

    @Test
    void updateWarningConfigRejectsMissingBalance() {
        doReturn(null).when(balanceMapper).selectById(999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateWarningConfig("999",
                        new StockBalanceWarningUpdateDTO(BigDecimal.TEN, BigDecimal.TEN)));

        assertEquals(40400, ex.getCode());
        assertTrue(ex.getMessage().contains("库存余额不存在"));
        verify(balanceMapper, never()).updateWarningConfig(any(), any(), any(), any());
    }

    @Test
    void updateWarningConfigRejectsTargetBelowSafe() {
        doReturn(balance(500L, 10, 1, "15", "10", "20")).when(balanceMapper).selectById(500L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateWarningConfig("500",
                        new StockBalanceWarningUpdateDTO(new BigDecimal("30"), new BigDecimal("20"))));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("补货目标库存不能低于安全库存"));
        verify(balanceMapper, never()).updateWarningConfig(any(), any(), any(), any());
    }

    @Test
    void updateWarningConfigRejectsVersionConflict() {
        StockBalance existing = balance(500L, 10, 1, "15", "10", "20");
        doReturn(existing).when(balanceMapper).selectById(500L);
        doReturn(sku(1, 3)).when(skuMapper).selectById(1L);
        // 并发修改：version 条件更新 0 行
        when(balanceMapper.updateWarningConfig(any(), any(), any(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateWarningConfig("500",
                        new StockBalanceWarningUpdateDTO(new BigDecimal("12"), new BigDecimal("30"))));

        assertEquals(40900, ex.getCode());
        assertTrue(ex.getMessage().contains("已被他人修改"));
    }

    @Test
    void updateWarningConfigRejectsScaleOverflow() {
        StockBalance existing = balance(500L, 10, 1, "15", "10", "20");
        doReturn(existing).when(balanceMapper).selectById(500L);
        // 整数单位物料（quantity_scale=0）不允许小数
        doReturn(sku(1, 0)).when(skuMapper).selectById(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateWarningConfig("500",
                        new StockBalanceWarningUpdateDTO(new BigDecimal("12.5"), new BigDecimal("30"))));

        assertEquals(40000, ex.getCode());
        assertTrue(ex.getMessage().contains("精度"));
        verify(balanceMapper, never()).updateWarningConfig(any(), any(), any(), any());
    }

    @Test
    void updateWarningConfigRejectsInvalidId() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateWarningConfig("abc",
                        new StockBalanceWarningUpdateDTO(BigDecimal.TEN, BigDecimal.TEN)));

        assertEquals(40000, ex.getCode());
        verify(balanceMapper, never()).selectById(any());
    }
}
