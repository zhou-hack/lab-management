package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.RestrictionCreateDTO;
import com.labmanage.roles.dto.RestrictionPageQueryDTO;
import com.labmanage.roles.vo.RestrictionPageItemVO;
import com.labmanage.roles.vo.UserOptionVO;

import java.time.Instant;
import java.util.List;

/**
 * 预约限制服务：手工限制的维护与生效判定。
 * 自动逾期冻结（source_document_id 非空）由单据模块落地后接入；
 * 预约下单拦截使用 hasActiveRestriction。
 */
public interface SysRestrictionService {

    PageResult<RestrictionPageItemVO> page(RestrictionPageQueryDTO query);

    /** 被限制用户下拉（启用中全部类型） */
    List<UserOptionVO> userOptions(String keyword);

    /** 手工新增限制（source_document_id 恒为 NULL，允许多条，时段服务端校验） */
    void create(RestrictionCreateDTO dto);

    /** 撤销限制（写 revoked_at/revoked_by；已撤销重复操作返回 40900） */
    void revoke(Long id);

    /** 指定时间点是否存在生效中的限制（ALL 覆盖 EQUIPMENT/LAB；预约模块下单拦截用） */
    boolean hasActiveRestriction(Long userId, String restrictionType, Instant at);
}
