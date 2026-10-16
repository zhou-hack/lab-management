package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.LogPageQueryDTO;
import com.labmanage.roles.entity.SysOperationLog;
import com.labmanage.roles.mapper.SysOperationLogMapper;
import com.labmanage.roles.service.SysLogService;
import com.labmanage.roles.vo.LogPageItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作日志服务实现
 */
@Service
public class SysLogServiceImpl implements SysLogService {

    private static final Logger log = LoggerFactory.getLogger(SysLogServiceImpl.class);

    private final SysOperationLogMapper operationLogMapper;

    public SysLogServiceImpl(SysOperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(SysOperationLog logEntry) {
        try {
            operationLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 审计写入失败不阻断业务，仅告警
            log.warn("操作日志写入失败：{} {}", logEntry.getActionCode(), e.getMessage());
        }
    }

    @Override
    public PageResult<LogPageItemVO> page(LogPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<LogPageItemVO> page = operationLogMapper.selectLogPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }
}
