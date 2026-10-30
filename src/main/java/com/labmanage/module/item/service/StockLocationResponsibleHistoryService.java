package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.module.item.dto.StockLocationResponsibleHistoryPageQuery;
import com.labmanage.module.item.entity.StockLocation;
import com.labmanage.module.item.entity.StockLocationResponsibleHistory;
import com.labmanage.module.item.mapper.StockLocationResponsibleHistoryMapper;
import com.labmanage.module.item.vo.StockLocationAdminVO;
import com.labmanage.module.item.vo.StockLocationResponsibleHistoryVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
public class StockLocationResponsibleHistoryService {

    private static final TypeReference<List<StockLocationAdminVO>> ADMIN_LIST_TYPE = new TypeReference<>() { };

    private final StockLocationResponsibleHistoryMapper historyMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public StockLocationResponsibleHistoryService(StockLocationResponsibleHistoryMapper historyMapper,
                                                  SysUserMapper userMapper,
                                                  ObjectMapper objectMapper) {
        this.historyMapper = historyMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public void recordChange(StockLocation location,
                             List<StockLocationAdminVO> beforeAdmins,
                             List<StockLocationAdminVO> afterAdmins,
                             Long operatorId,
                             Instant occurredAt) {
        SysUser operator = userMapper.selectById(operatorId);
        StockLocationResponsibleHistory history = new StockLocationResponsibleHistory();
        history.setLocationId(location.getId());
        history.setLocationCode(location.getLocationCode());
        history.setLocationName(location.getLocationName());
        history.setBeforeSnapshot(writeSnapshot(beforeAdmins));
        history.setAfterSnapshot(writeSnapshot(afterAdmins));
        history.setOperatorId(operatorId);
        history.setOperatorName(operator == null ? null : operator.getRealName());
        history.setOccurredAt(occurredAt);
        historyMapper.insert(history);
    }

    public PageResult<StockLocationResponsibleHistoryVO> page(StockLocationResponsibleHistoryPageQuery query) {
        Page<StockLocationResponsibleHistory> page = historyMapper.selectPage(PageUtils.toPage(query),
                Wrappers.<StockLocationResponsibleHistory>lambdaQuery()
                        .eq(query.getLocationId() != null, StockLocationResponsibleHistory::getLocationId,
                                query.getLocationId())
                        .orderByDesc(StockLocationResponsibleHistory::getOccurredAt)
                        .orderByDesc(StockLocationResponsibleHistory::getId));
        List<StockLocationResponsibleHistoryVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    private StockLocationResponsibleHistoryVO toVO(StockLocationResponsibleHistory history) {
        return new StockLocationResponsibleHistoryVO(String.valueOf(history.getId()),
                String.valueOf(history.getLocationId()), history.getLocationCode(), history.getLocationName(),
                readSnapshot(history.getBeforeSnapshot()), readSnapshot(history.getAfterSnapshot()),
                String.valueOf(history.getOperatorId()), history.getOperatorName(), history.getOccurredAt());
    }

    private String writeSnapshot(List<StockLocationAdminVO> admins) {
        try {
            return objectMapper.writeValueAsString(admins);
        } catch (JacksonException e) {
            throw new IllegalStateException("库位负责人快照序列化失败", e);
        }
    }

    private List<StockLocationAdminVO> readSnapshot(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, ADMIN_LIST_TYPE);
        } catch (JacksonException e) {
            throw new IllegalStateException("库位负责人历史快照格式不正确", e);
        }
    }
}
