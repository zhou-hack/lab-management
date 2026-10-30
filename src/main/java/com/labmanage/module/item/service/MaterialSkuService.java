package com.labmanage.module.item.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.item.dto.MaterialSkuCreateDTO;
import com.labmanage.module.item.dto.MaterialSkuQuery;
import com.labmanage.module.item.dto.MaterialSkuUpdateDTO;
import com.labmanage.module.item.vo.MaterialSkuVO;
import com.labmanage.module.item.vo.SkuOptionVO;

import java.util.List;

/**
 * 物料 SKU 服务
 */
public interface MaterialSkuService {

    /** 分页查询 */
    PageResult<MaterialSkuVO> page(MaterialSkuQuery query);

    /** 物料选项（申领表单选择器用；关键字同时匹配 SKU 码前缀与名称模糊，仅启用，最多 20 条） */
    List<SkuOptionVO> listOptions(String keyword);

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
