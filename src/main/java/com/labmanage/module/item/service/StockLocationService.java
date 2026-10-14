package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.StockLocationCreateDTO;
import com.labmanage.module.item.dto.StockLocationQuery;
import com.labmanage.module.item.dto.StockLocationUpdateDTO;
import com.labmanage.module.item.vo.StockLocationVO;

/**
 * 库位服务
 */
public interface StockLocationService {

    /** 分页查询 */
    PageResult<StockLocationVO> page(StockLocationQuery query);

    /** 详情 */
    StockLocationVO get(String id);

    /** 新增库位 */
    void create(StockLocationCreateDTO dto);

    /** 修改库位 */
    void update(StockLocationUpdateDTO dto);

    /** 启用/停用 */
    void updateStatus(String id, boolean enabled);

    /** 删除（逻辑删除；被库存余额/单据明细/进行中盘点引用时拒绝） */
    void delete(String id);
}
