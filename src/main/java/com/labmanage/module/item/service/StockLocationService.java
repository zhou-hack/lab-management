package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.StockLocationCreateDTO;
import com.labmanage.module.item.dto.StockLocationQuery;
import com.labmanage.module.item.dto.StockLocationUpdateDTO;
import com.labmanage.module.item.vo.StockLocationVO;
import com.labmanage.module.item.vo.SysUserOptionVO;

import java.util.List;

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

    /** 删除（逻辑删除；被库存余额/单据明细引用时拒绝） */
    void delete(String id);

    /** 保管人候选搜索（跨模块只读引用 sys_user，按姓名/账号/学工号前缀匹配） */
    List<SysUserOptionVO> custodianOptions(String keyword);
}
