package com.labmanage.module.space.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.space.dto.LabCreateDTO;
import com.labmanage.module.space.dto.LabPageQueryDTO;
import com.labmanage.module.space.dto.LabUpdateDTO;
import com.labmanage.module.space.dto.OpenRuleCreateDTO;
import com.labmanage.module.space.vo.LabOpenRuleVO;
import com.labmanage.module.space.vo.LabPageItemVO;

import java.util.List;

/**
 * 实验室管理服务（含开放规则维护）
 */
public interface SpaceLabService {

    PageResult<LabPageItemVO> page(LabPageQueryDTO query);

    void create(LabCreateDTO dto);

    /** 编辑实验室（编码不可改；改管理部门需同时具备新旧部门写范围） */
    void update(Long id, LabUpdateDTO dto);

    /** 启用/停用实验室 */
    void updateEnabled(Long id, Integer enabled);

    /** 指定实验室的开放规则列表 */
    List<LabOpenRuleVO> listOpenRules(Long labId);

    /** 新增开放规则（同日时间窗，openTime < closeTime，validFrom <= validTo） */
    void createOpenRule(Long labId, OpenRuleCreateDTO dto);

    /** 删除开放规则（逻辑删除） */
    void deleteOpenRule(Long ruleId);
}
