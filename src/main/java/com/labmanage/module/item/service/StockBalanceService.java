package com.labmanage.module.item.service;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.item.dto.StockBalancePositionUpdateDTO;
import com.labmanage.module.item.entity.StockBalance;
import com.labmanage.module.item.mapper.StockBalanceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockBalanceService {

    private final StockBalanceMapper balanceMapper;
    private final ItemLocationAccessService locationAccessService;
    private final ItemTimeService timeService;

    public StockBalanceService(StockBalanceMapper balanceMapper,
                               ItemLocationAccessService locationAccessService,
                               ItemTimeService timeService) {
        this.balanceMapper = balanceMapper;
        this.locationAccessService = locationAccessService;
        this.timeService = timeService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePosition(Long id, StockBalancePositionUpdateDTO dto) {
        StockBalance balance = balanceMapper.selectById(id);
        if (balance == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "耗材库存记录不存在");
        }
        Long operatorId = SecurityUtils.currentUserId();
        locationAccessService.requireLocationAdmin(operatorId, balance.getLocationId());
        String positionText = dto.positionText() == null || dto.positionText().isBlank()
                ? null : dto.positionText().trim();
        try {
            int rows = balanceMapper.updatePosition(id, dto.version(), positionText,
                    timeService.now(), operatorId);
            if (rows != 1) {
                throw new BusinessException(ResultCode.CONFLICT.getCode(), "库存记录已变化，请刷新后重试");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该耗材在目标库内位置已有库存记录");
        }
    }
}
