package com.labmanage.module.space.service;

import com.labmanage.module.space.dto.MaintenanceWindowCreateDTO;
import com.labmanage.module.space.vo.MaintenanceWindowVO;

import java.util.List;

/**
 * 场地维修时段服务。
 * <p>
 * 规则（2026-10-24 定稿）：
 * a) 设置维修时段须 startAt 晚于当前时间且 startAt < endAt；
 * b) 与生效中维修时段重叠 → 40900；与已批准预约占用（lab_slot 未释放）重叠 → 40900 拒绝，
 *    须先取消重叠预约；
 * c) 取消置 cancelled_at（幂等，已取消/不存在 → 40400）。
 */
public interface SpaceMaintenanceService {

    /** 场地生效中的维修时段（含进行中与未来，按开始时间升序） */
    List<MaintenanceWindowVO> listActive(Long spaceId);

    /** 新建维修时段（场地存在即可设；重叠校验见类注释） */
    MaintenanceWindowVO create(Long spaceId, MaintenanceWindowCreateDTO dto);

    /** 取消维修时段（幂等提示；不存在或已取消 → 40400） */
    void cancel(Long spaceId, Long windowId);
}
