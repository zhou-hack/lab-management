package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialQuotaQuery;
import com.labmanage.module.item.dto.MaterialQuotaUpdateDTO;
import com.labmanage.module.item.dto.MaterialQuotaUpsertDTO;
import com.labmanage.module.item.vo.MaterialQuotaVO;

/**
 * 领用定额服务（开课实例 + SKU 唯一；停用后新申请不再取默认定额）
 */
public interface MaterialQuotaService {

    /** 批量登记（已存在则更新量，不存在则新增并启用） */
    void upsert(MaterialQuotaUpsertDTO dto);

    /** 修改每人每课时定额 */
    void update(String id, MaterialQuotaUpdateDTO dto);

    /** 启用 */
    void enable(String id);

    /** 停用 */
    void disable(String id);

    /** 分页查询 */
    PageResult<MaterialQuotaVO> page(MaterialQuotaQuery query);
}
