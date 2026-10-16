package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.LogPageQueryDTO;
import com.labmanage.roles.entity.SysOperationLog;
import com.labmanage.roles.vo.LogPageItemVO;

/**
 * 操作日志服务（采集写入 + 管理端查询）
 */
public interface SysLogService {

    /**
     * 追加一条日志（独立事务：业务事务回滚不影响审计记录）。
     * 实现内部吞掉异常，日志写入失败不影响业务。
     */
    void record(SysOperationLog logEntry);

    /** 日志分页列表（关键字/模块/结果/时间窗口筛选） */
    PageResult<LogPageItemVO> page(LogPageQueryDTO query);
}
