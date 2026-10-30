package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.item.entity.StockLocationAdmin;
import com.labmanage.module.item.mapper.StockLocationAdminMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ItemLocationAccessService {

    private final StockLocationAdminMapper adminMapper;

    public ItemLocationAccessService(StockLocationAdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    public boolean isLocationAdmin(Long userId, Long locationId) {
        if (userId == null || locationId == null) {
            return false;
        }
        return adminMapper.selectCount(Wrappers.<StockLocationAdmin>lambdaQuery()
                .eq(StockLocationAdmin::getLocationId, locationId)
                .eq(StockLocationAdmin::getUserId, userId)) > 0;
    }

    public boolean isLocationAdmin(Long locationId) {
        return isLocationAdmin(SecurityUtils.currentUserId(), locationId);
    }

    public List<Long> userLocationIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return adminMapper.selectList(Wrappers.<StockLocationAdmin>lambdaQuery()
                        .eq(StockLocationAdmin::getUserId, userId)
                        .orderByAsc(StockLocationAdmin::getLocationId))
                .stream()
                .map(StockLocationAdmin::getLocationId)
                .distinct()
                .toList();
    }

    public List<Long> currentUserLocationIds() {
        return userLocationIds(SecurityUtils.currentUserId());
    }

    public void requireLocationAdmin(Long locationId) {
        requireLocationAdmin(SecurityUtils.currentUserId(), locationId);
    }

    public void requireLocationAdmin(Long userId, Long locationId) {
        if (!isLocationAdmin(userId, locationId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "当前用户不是该库位管理员");
        }
    }
}
