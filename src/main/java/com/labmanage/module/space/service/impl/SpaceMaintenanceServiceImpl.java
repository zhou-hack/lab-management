package com.labmanage.module.space.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.booking.entity.LabSlot;
import com.labmanage.module.booking.mapper.LabSlotMapper;
import com.labmanage.module.space.dto.MaintenanceWindowCreateDTO;
import com.labmanage.module.space.entity.BizSpace;
import com.labmanage.module.space.entity.LabMaintenanceWindow;
import com.labmanage.module.space.mapper.BizSpaceMapper;
import com.labmanage.module.space.mapper.LabMaintenanceWindowMapper;
import com.labmanage.module.space.service.SpaceMaintenanceService;
import com.labmanage.module.space.vo.MaintenanceWindowVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 场地维修时段业务实现（规则见 SpaceMaintenanceService 类注释）。
 */
@Service
public class SpaceMaintenanceServiceImpl implements SpaceMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(SpaceMaintenanceServiceImpl.class);

    private static final DateTimeFormatter SHANGHAI_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

    private final BizSpaceMapper spaceMapper;
    private final LabMaintenanceWindowMapper maintenanceMapper;
    private final LabSlotMapper slotMapper;

    public SpaceMaintenanceServiceImpl(BizSpaceMapper spaceMapper,
                                       LabMaintenanceWindowMapper maintenanceMapper,
                                       LabSlotMapper slotMapper) {
        this.spaceMapper = spaceMapper;
        this.maintenanceMapper = maintenanceMapper;
        this.slotMapper = slotMapper;
    }

    @Override
    public List<MaintenanceWindowVO> listActive(Long spaceId) {
        requireSpace(spaceId);
        return maintenanceMapper.listActiveByLab(spaceId, Instant.now()).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaintenanceWindowVO create(Long spaceId, MaintenanceWindowCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        BizSpace space = requireSpace(spaceId);

        Instant now = Instant.now();
        if (!dto.startAt().isBefore(dto.endAt())) {
            throw new BusinessException(40000, "维修开始时间必须早于结束时间");
        }
        if (!dto.startAt().isAfter(now)) {
            throw new BusinessException(40000, "维修开始时间必须晚于当前时间");
        }
        if (maintenanceMapper.countOverlap(spaceId, dto.startAt(), dto.endAt()) > 0) {
            log.warn("[维修时段设置] 与生效中维修时段重叠：操作人={}, 场地ID={}, 时段=[{} ~ {}]",
                    operatorId, spaceId, dto.startAt(), dto.endAt());
            throw new BusinessException(40900, "与已有维修时段重叠，请调整起止时间");
        }
        // 与已批准预约占用重叠（lab_slot 未释放）→ 拒绝，提示先处理预约（2026-10-24 定稿）
        List<LabSlot> conflicts = slotMapper.listOccupiedInRange(spaceId, dto.startAt(), dto.endAt());
        if (!conflicts.isEmpty()) {
            log.warn("[维修时段设置] 撞已批准预约：操作人={}, 场地ID={}, 时段=[{} ~ {}], 冲突占用数={}",
                    operatorId, spaceId, dto.startAt(), dto.endAt(), conflicts.size());
            throw new BusinessException(40900,
                    "该时段与 " + conflicts.size() + " 条已批准预约重叠（最早开始 "
                            + SHANGHAI_FMT.format(conflicts.get(0).getStartAt())
                            + "），请先取消重叠预约再设置维修时段");
        }

        LabMaintenanceWindow window = new LabMaintenanceWindow();
        window.setLabId(spaceId);
        window.setStartAt(dto.startAt());
        window.setEndAt(dto.endAt());
        window.setReason(dto.reason() == null || dto.reason().isBlank() ? null : dto.reason().trim());
        maintenanceMapper.insert(window);
        log.info("[维修时段设置] 完成：操作人={}, 场地={}, 时段=[{} ~ {}], 维修ID={}",
                operatorId, space.getLabName(), dto.startAt(), dto.endAt(), window.getId());
        return toVO(window);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long spaceId, Long windowId) {
        Long operatorId = SecurityUtils.currentUserId();
        requireSpace(spaceId);
        LabMaintenanceWindow window = maintenanceMapper.selectOne(Wrappers.<LabMaintenanceWindow>lambdaQuery()
                .eq(LabMaintenanceWindow::getId, windowId)
                .eq(LabMaintenanceWindow::getLabId, spaceId));
        if (window == null || window.getCancelledAt() != null) {
            throw new BusinessException(40400, "维修时段不存在或已取消");
        }
        window.setCancelledAt(Instant.now());
        maintenanceMapper.updateById(window);
        log.info("[维修时段取消] 完成：操作人={}, 场地ID={}, 维修ID={}", operatorId, spaceId, windowId);
    }

    private BizSpace requireSpace(Long spaceId) {
        BizSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw new BusinessException(40400, "场地不存在");
        }
        return space;
    }

    private MaintenanceWindowVO toVO(LabMaintenanceWindow w) {
        return new MaintenanceWindowVO(String.valueOf(w.getId()), String.valueOf(w.getLabId()),
                w.getStartAt(), w.getEndAt(), w.getReason());
    }
}
