package com.labmanage.module.space.service;

import com.labmanage.common.result.PageResult;
import com.labmanage.module.space.dto.SpaceBatchStatusDTO;
import com.labmanage.module.space.dto.SpaceCreateDTO;
import com.labmanage.module.space.dto.SpacePageQuery;
import com.labmanage.module.space.dto.SpaceUpdateDTO;
import com.labmanage.module.space.enums.SpaceType;
import com.labmanage.module.space.vo.SpaceBatchStatusVO;
import com.labmanage.module.space.vo.SpaceDetailVO;
import com.labmanage.module.space.vo.SpaceListVO;
import com.labmanage.module.space.vo.SpaceOptionVO;
import com.labmanage.module.space.vo.SpaceStatsVO;

import java.util.List;

/**
 * 场地服务。
 */
public interface SpaceService {

    /**
     * 分页查询场地。
     * 支持名称/位置模糊、类型、状态、容量区间过滤与排序白名单。
     */
    PageResult<SpaceListVO> page(SpacePageQuery query);

    /**
     * 场地详情。
     */
    SpaceDetailVO detail(Long id);

    /**
     * 场地统计（列表页 KPI 卡片：总块数、各状态块数与类型分布）。
     */
    SpaceStatsVO stats();

    /**
     * 场地批量启停（target 仅 AVAILABLE/CLOSED）。
     */
    SpaceBatchStatusVO batchStatus(SpaceBatchStatusDTO dto);

    /**
     * 场地 CSV 导出（与分页同条件过滤，UTF-8 BOM，时间按上海时区展示）。
     */
    byte[] exportCsv(SpacePageQuery query);

    /**
     * 场地下拉选项（供预约等下游模块联调）。
     *
     * @param spaceType     类型过滤（可空）
     * @param minCapacity   最小容纳人数（可空）
     * @param availableOnly true 时仅返回状态为 AVAILABLE 的场地
     */
    List<SpaceOptionVO> options(SpaceType spaceType, Integer minCapacity, Boolean availableOnly);

    /**
     * 新建场地（状态缺省 AVAILABLE，开放时段缺省 08:00-21:00）。
     */
    SpaceDetailVO create(SpaceCreateDTO dto);

    /**
     * 编辑场地（openStart/openEnd/status 为空表示保留原值）。
     */
    SpaceDetailVO update(Long id, SpaceUpdateDTO dto);

    /**
     * 启用场地（状态置为 AVAILABLE）。
     */
    void enable(Long id);

    /**
     * 停用场地（状态置为 CLOSED，停用后禁止新建预约，已有预约由预约模块按策略处理）。
     */
    void disable(Long id);

    /**
     * 删除场地（逻辑删除，名称不复用；删除前做业务引用检查）。
     */
    void delete(Long id);
}
