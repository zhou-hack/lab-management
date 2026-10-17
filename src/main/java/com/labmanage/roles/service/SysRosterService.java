package com.labmanage.roles.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.roles.dto.RosterBatchCreateDTO;
import com.labmanage.roles.dto.RosterCreateDTO;
import com.labmanage.roles.dto.RosterEnabledDTO;
import com.labmanage.roles.dto.RosterPageQueryDTO;
import com.labmanage.roles.dto.RosterUpdateDTO;
import com.labmanage.roles.vo.RosterImportResultVO;
import com.labmanage.roles.vo.RosterPageItemVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 预录名单管理服务
 */
public interface SysRosterService {

    /** 名单分页列表（关键字/身份类型/建档状态筛选） */
    PageResult<RosterPageItemVO> page(RosterPageQueryDTO query);

    /** 单条录入；学工号/手机号冲突抛 40900 */
    void create(RosterCreateDTO dto);

    /** 批量导入（同事务，任一条冲突整体回滚并报行号） */
    int createBatch(RosterBatchCreateDTO dto);

    /** 编辑名单（学工号、身份类型不可改）；已建档名单禁止编辑 */
    void update(Long id, RosterUpdateDTO dto);

    /** 启用/停用；已建档名单禁止停用 */
    void updateEnabled(Long id, RosterEnabledDTO dto);

    /** 导出名单 CSV（UTF-8 BOM，筛选条件与分页列表一致） */
    byte[] exportCsv(RosterPageQueryDTO query);

    /** 批量导入模板 CSV（表头 + 示例行，UTF-8 BOM） */
    byte[] importTemplate();

    /** 批量导入名单（CSV 部分成功：成功行已入库，失败行给出行级原因，最多记 50 条） */
    RosterImportResultVO importCsv(MultipartFile file);
}
