package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.ScopeCreateDTO;
import com.labmanage.roles.dto.ScopePageQueryDTO;
import com.labmanage.roles.vo.AdminOptionVO;
import com.labmanage.roles.vo.ScopePageItemVO;

import java.util.List;

/**
 * 数据范围服务：管理员归口授权的维护与范围判定。
 * 判定语义：无范围记录不自动授予全校数据；WRITE 含 READ，冲突取最高级别；
 * 非管理员范围由本人身份、课程名单与有效助理授权计算，不走本表。
 */
public interface SysScopeService {

    PageResult<ScopePageItemVO> page(ScopePageQueryDTO query);

    /** 可授权管理员下拉（启用中的 ADMIN） */
    List<AdminOptionVO> adminOptions(String keyword);

    /** 新增授权行；同目标升级别通过增删行实现（唯一键含 access_mode） */
    void create(ScopeCreateDTO dto);

    /** 物理删除授权行（关系表一行唯一，审计由操作日志承担） */
    void remove(Long id);

    /**
     * 范围判定：用户是否持有指定范围。
     * requestedMode=WRITE 时仅 WRITE 行满足；READ 时 READ/WRITE 行均满足。
     * ALL 行匹配任意请求；其余行要求类型与目标同时相等。
     */
    boolean hasAccess(Long userId, String scopeType, Long targetId, String accessMode);

    /** 当前登录管理员须持有目标部门写范围（departmentId 为 NULL 时要求全校写范围），否则 40300 */
    void requireDepartmentWrite(Long departmentId);

    /** 当前登录管理员须持有全校写范围，否则 40300 */
    void requireAllWrite();
}
