package com.labmanage.roles.service;

import com.labmanage.roles.dto.DepartmentCreateDTO;
import com.labmanage.roles.dto.DepartmentEnabledDTO;
import com.labmanage.roles.dto.DepartmentUpdateDTO;
import com.labmanage.roles.vo.DepartmentTreeVO;

import java.util.List;

/**
 * 组织部门管理服务
 */
public interface SysDepartmentService {

    /** 部门树（全部未删除节点，含停用；按编码排序组装） */
    List<DepartmentTreeVO> tree();

    /** 新增部门；父部门须存在且启用；编码留空自动生成 DEPT_ 序号，手填编码冲突抛 40900 */
    void create(DepartmentCreateDTO dto);

    /** 编辑部门（编码不可改）；父部门禁止指向自身或后代（循环校验） */
    void update(Long id, DepartmentUpdateDTO dto);

    /** 启用/停用；存在启用子部门或被用户/名单引用时禁止停用 */
    void updateEnabled(Long id, DepartmentEnabledDTO dto);
}
