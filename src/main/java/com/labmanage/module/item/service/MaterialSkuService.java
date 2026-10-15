package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialSkuCreateDTO;
import com.labmanage.module.item.dto.MaterialSkuQuery;
import com.labmanage.module.item.dto.MaterialSkuUpdateDTO;
import com.labmanage.module.item.vo.MaterialSkuVO;

/**
 * 物料 SKU 服务
 */
public interface MaterialSkuService {

    /** 分页查询 */
    PageResult<MaterialSkuVO> page(MaterialSkuQuery query);

    /** 详情 */
    MaterialSkuVO get(String id);

    /** 新增物料 */
    void create(MaterialSkuCreateDTO dto);

    /** 修改物料 */
    void update(MaterialSkuUpdateDTO dto);

    /** 启用/停用 */
    void updateStatus(String id, boolean enabled);

    /** 删除（逻辑删除；被余额/单据明细/申领明细/定额/盘点明细引用时拒绝） */
    void delete(String id);
}
