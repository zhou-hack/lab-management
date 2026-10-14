package com.labmanage.module.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.space.dto.SpaceCreateDTO;
import com.labmanage.module.space.dto.SpacePageQuery;
import com.labmanage.module.space.dto.SpaceUpdateDTO;
import com.labmanage.module.space.entity.BizSpace;
import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.enums.SpaceType;
import com.labmanage.module.space.mapper.BizSpaceMapper;
import com.labmanage.module.space.service.SpaceService;
import com.labmanage.module.space.vo.SpaceDetailVO;
import com.labmanage.module.space.vo.SpaceListVO;
import com.labmanage.module.space.vo.SpaceOptionVO;
import com.labmanage.module.space.vo.SpaceStatsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 场地业务实现（biz_space 单表）。
 * <p>
 * 授权说明：接口访问由 @SaCheckPermission 按权限标识控制（管理员全量、
 * 师生只读 space:space:list/read）。sys_user_data_scope 的 DEPARTMENT/CAMPUS/BUILDING
 * 行级范围在单表模式下无组织锚点列，一期不做行级过滤；LAB 级范围仍以
 * biz_space.id 为目标，删除前做引用检查。
 */
@Service
public class SpaceServiceImpl implements SpaceService {

    private static final Logger log = LoggerFactory.getLogger(SpaceServiceImpl.class);

    /** 排序字段白名单（防注入）：入参字段 → DB 列 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "spaceName", "space_name",
            "capacity", "capacity",
            "createTime", "create_time",
            "updateTime", "update_time"
    );

    /** 开放时段缺省值与 HH:mm 输入/输出格式（上海本地时间，无时区换算） */
    private static final LocalTime DEFAULT_OPEN_START = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_OPEN_END = LocalTime.of(21, 0);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final BizSpaceMapper spaceMapper;

    public SpaceServiceImpl(BizSpaceMapper spaceMapper) {
        this.spaceMapper = spaceMapper;
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public PageResult<SpaceListVO> page(SpacePageQuery query) {
        long begin = System.nanoTime();
        LambdaQueryWrapper<BizSpace> wrapper = new LambdaQueryWrapper<>();
        if (hasText(query.getSpaceName())) {
            wrapper.like(BizSpace::getSpaceName, query.getSpaceName().trim());
        }
        if (hasText(query.getLocation())) {
            wrapper.like(BizSpace::getLocation, query.getLocation().trim());
        }
        if (query.getSpaceType() != null) {
            wrapper.eq(BizSpace::getSpaceType, query.getSpaceType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(BizSpace::getStatus, query.getStatus());
        }
        if (query.getMinCapacity() != null) {
            wrapper.ge(BizSpace::getCapacity, query.getMinCapacity());
        }
        if (query.getMaxCapacity() != null) {
            wrapper.le(BizSpace::getCapacity, query.getMaxCapacity());
        }
        applySort(wrapper, query);

        Page<BizSpace> page = spaceMapper.selectPage(PageUtils.toPage(query), wrapper);
        List<SpaceListVO> records = page.getRecords().stream().map(this::toListVO).toList();
        log.info("[场地分页] 完成：操作人={}, 页码={}, 页大小={}, 条件=[{}], 命中总数={}, 返回行数={}, 耗时={}ms",
                SecurityUtils.currentUserId(), query.getPageNum(), query.getPageSize(),
                buildFilterSummary(query), page.getTotal(), records.size(),
                (System.nanoTime() - begin) / 1_000_000);
        return PageResult.of(records, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Override
    public SpaceDetailVO detail(Long id) {
        long begin = System.nanoTime();
        BizSpace space = requireSpace(id);
        log.info("[场地详情] 完成：操作人={}, 场地ID={}, 名称={}, 状态={}, 耗时={}ms",
                SecurityUtils.currentUserId(), id, space.getSpaceName(), space.getStatus(),
                (System.nanoTime() - begin) / 1_000_000);
        return toDetailVO(space);
    }

    @Override
    public List<SpaceOptionVO> options(SpaceType spaceType, Integer minCapacity, Boolean availableOnly) {
        LambdaQueryWrapper<BizSpace> wrapper = new LambdaQueryWrapper<>();
        if (spaceType != null) {
            wrapper.eq(BizSpace::getSpaceType, spaceType);
        }
        if (minCapacity != null) {
            wrapper.ge(BizSpace::getCapacity, minCapacity);
        }
        if (Boolean.TRUE.equals(availableOnly)) {
            wrapper.eq(BizSpace::getStatus, SpaceStatus.AVAILABLE);
        }
        wrapper.orderByAsc(BizSpace::getSpaceName);

        List<SpaceOptionVO> result = spaceMapper.selectList(wrapper).stream()
                .map(s -> new SpaceOptionVO(
                        String.valueOf(s.getId()), s.getSpaceName(), s.getSpaceType(),
                        s.getCapacity(), s.getStatus()))
                .toList();
        log.info("[场地选项] 完成：操作人={}, 类型={}, 最小容量={}, 仅可用={}, 返回条数={}",
                SecurityUtils.currentUserId(), spaceType, minCapacity, availableOnly, result.size());
        return result;
    }

    @Override
    public SpaceStatsVO stats() {
        long available = 0;
        long maintaining = 0;
        long closed = 0;
        for (Map<String, Object> row : spaceMapper.countGroupByStatus()) {
            long cnt = ((Number) row.get("cnt")).longValue();
            String status = String.valueOf(row.get("status"));
            switch (status) {
                case "AVAILABLE" -> available = cnt;
                case "MAINTAINING" -> maintaining = cnt;
                case "CLOSED" -> closed = cnt;
                default -> log.warn("[场地统计] 未知状态不参与分档：status={}, cnt={}", status, cnt);
            }
        }
        log.info("[场地统计] 完成：操作人={}, 总数={}, 可用={}, 维护中={}, 停用={}",
                SecurityUtils.currentUserId(), available + maintaining + closed, available, maintaining, closed);
        return new SpaceStatsVO(available + maintaining + closed, available, maintaining, closed);
    }

    // ------------------------------------------------------------------ 写入

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaceDetailVO create(SpaceCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[场地新增] 开始：操作人={}, 入参={}", operatorId, dto);

        String name = dto.spaceName().trim();
        requireNameUnused(name, null);

        LocalTime openStart = hasText(dto.openStart()) ? LocalTime.parse(dto.openStart(), HH_MM) : DEFAULT_OPEN_START;
        LocalTime openEnd = hasText(dto.openEnd()) ? LocalTime.parse(dto.openEnd(), HH_MM) : DEFAULT_OPEN_END;
        validateOpenWindow(openStart, openEnd);

        BizSpace space = new BizSpace();
        space.setSpaceName(name);
        space.setSpaceType(dto.spaceType());
        space.setLocation(trimToNull(dto.location()));
        space.setCapacity(dto.capacity());
        space.setFacilities(trimToNull(dto.facilities()));
        space.setOpenStart(openStart);
        space.setOpenEnd(openEnd);
        space.setStatus(dto.status() == null ? SpaceStatus.AVAILABLE : dto.status());

        spaceMapper.insert(space);
        log.info("[场地新增] 完成：操作人={}, 场地ID={}, 名称={}, 类型={}, 状态={}, 开放时段={}~{}",
                operatorId, space.getId(), name, space.getSpaceType(), space.getStatus(), openStart, openEnd);
        return toDetailVO(requireSpace(space.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaceDetailVO update(Long id, SpaceUpdateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[场地编辑] 开始：操作人={}, 场地ID={}, 入参={}", operatorId, id, dto);

        BizSpace space = requireSpace(id);
        String name = dto.spaceName().trim();
        requireNameUnused(name, id);

        // 空值语义：开放时段与状态为空表示保留原值；location/facilities 为空即清空
        LocalTime openStart = hasText(dto.openStart()) ? LocalTime.parse(dto.openStart(), HH_MM) : space.getOpenStart();
        LocalTime openEnd = hasText(dto.openEnd()) ? LocalTime.parse(dto.openEnd(), HH_MM) : space.getOpenEnd();
        validateOpenWindow(openStart, openEnd);
        SpaceStatus status = dto.status() == null ? space.getStatus() : dto.status();

        int rows = spaceMapper.update(null, new LambdaUpdateWrapper<BizSpace>()
                .eq(BizSpace::getId, id)
                .set(BizSpace::getSpaceName, name)
                .set(BizSpace::getSpaceType, dto.spaceType())
                .set(BizSpace::getLocation, trimToNull(dto.location()))
                .set(BizSpace::getCapacity, dto.capacity())
                .set(BizSpace::getFacilities, trimToNull(dto.facilities()))
                .set(BizSpace::getOpenStart, openStart)
                .set(BizSpace::getOpenEnd, openEnd)
                .set(BizSpace::getStatus, status)
                .set(BizSpace::getUpdateUserId, operatorId));
        if (rows == 0) {
            log.warn("[场地编辑] 更新未命中任何行（场地可能已被删除）：操作人={}, 场地ID={}", operatorId, id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        log.info("[场地编辑] 完成：操作人={}, 场地ID={}, 名称={}, 状态={}, 开放时段={}~{}",
                operatorId, id, name, status, openStart, openEnd);
        return toDetailVO(requireSpace(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[场地启用] 开始：操作人={}, 场地ID={}", operatorId, id);

        BizSpace space = requireSpace(id);
        int rows = spaceMapper.update(null, new LambdaUpdateWrapper<BizSpace>()
                .eq(BizSpace::getId, id)
                .set(BizSpace::getStatus, SpaceStatus.AVAILABLE)
                .set(BizSpace::getUpdateUserId, operatorId));
        if (rows == 0) {
            log.warn("[场地启用] 更新未命中任何行（场地可能已被删除）：操作人={}, 场地ID={}", operatorId, id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        log.info("[场地启用] 完成：操作人={}, 场地ID={}, 名称={}, 原状态={} → 新状态=AVAILABLE",
                operatorId, id, space.getSpaceName(), space.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[场地停用] 开始：操作人={}, 场地ID={}", operatorId, id);

        BizSpace space = requireSpace(id);
        // 停用不自动取消已有生效预约（由预约模块按策略处理）；停用后禁止新建占用
        int rows = spaceMapper.update(null, new LambdaUpdateWrapper<BizSpace>()
                .eq(BizSpace::getId, id)
                .set(BizSpace::getStatus, SpaceStatus.CLOSED)
                .set(BizSpace::getUpdateUserId, operatorId));
        if (rows == 0) {
            log.warn("[场地停用] 更新未命中任何行（场地可能已被删除）：操作人={}, 场地ID={}", operatorId, id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        log.info("[场地停用] 完成：操作人={}, 场地ID={}, 名称={}, 原状态={} → 新状态=CLOSED",
                operatorId, id, space.getSpaceName(), space.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[场地删除] 开始：操作人={}, 场地ID={}", operatorId, id);

        BizSpace space = requireSpace(id);

        // 删除前引用检查：LAB 级数据范围授权；下游模块（预约/资产/物料）落地后在此追加
        List<String> conflicts = new ArrayList<>();
        long scopeCount = spaceMapper.countLabScopeBySpaceId(id);
        if (scopeCount > 0) {
            conflicts.add("存在 " + scopeCount + " 条 LAB 级数据范围授权，请先解除");
        }
        // TODO 预约模块：lab_booking 未结束预约、lab_slot 未释放占用
        // TODO 资产模块：equipment_asset / stock_location 启用记录
        if (!conflicts.isEmpty()) {
            log.warn("[场地删除] 存在未结业务引用，拒绝删除：操作人={}, 场地ID={}, 名称={}, 冲突项=[{}]",
                    operatorId, id, space.getSpaceName(), String.join("；", conflicts));
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "场地存在未结业务引用，无法删除：" + String.join("；", conflicts));
        }

        int rows = spaceMapper.delete(new LambdaQueryWrapper<BizSpace>().eq(BizSpace::getId, id));
        if (rows == 0) {
            log.warn("[场地删除] 删除未命中任何行（场地可能已被删除）：操作人={}, 场地ID={}", operatorId, id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        log.info("[场地删除] 完成：操作人={}, 场地ID={}, 名称={}, 原状态={}（逻辑删除，名称不可复用）",
                operatorId, id, space.getSpaceName(), space.getStatus());
    }

    // ------------------------------------------------------------- 组装/辅助

    private SpaceListVO toListVO(BizSpace s) {
        return new SpaceListVO(
                String.valueOf(s.getId()),
                s.getSpaceName(),
                s.getSpaceType(),
                s.getSpaceType() == null ? null : s.getSpaceType().getDisplayName(),
                s.getLocation(),
                s.getCapacity(),
                s.getStatus(),
                s.getStatus() == null ? null : s.getStatus().getDisplayName(),
                formatTime(s.getOpenStart()),
                formatTime(s.getOpenEnd()),
                s.getUpdateTime()
        );
    }

    private SpaceDetailVO toDetailVO(BizSpace s) {
        return new SpaceDetailVO(
                String.valueOf(s.getId()),
                s.getSpaceName(),
                s.getSpaceType(),
                s.getSpaceType() == null ? null : s.getSpaceType().getDisplayName(),
                s.getLocation(),
                s.getCapacity(),
                s.getFacilities(),
                formatTime(s.getOpenStart()),
                formatTime(s.getOpenEnd()),
                s.getStatus(),
                s.getStatus() == null ? null : s.getStatus().getDisplayName(),
                s.getCreateTime(),
                s.getUpdateTime()
        );
    }

    private String formatTime(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }

    private BizSpace requireSpace(Long id) {
        BizSpace space = spaceMapper.selectById(id);
        if (space == null) {
            log.warn("[场地查询] 场地不存在或已删除：场地ID={}", id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return space;
    }

    /** 分页查询条件摘要（仅拼非空条件，用于日志排查） */
    private String buildFilterSummary(SpacePageQuery query) {
        StringBuilder sb = new StringBuilder();
        if (hasText(query.getSpaceName())) {
            sb.append("名称~").append(query.getSpaceName().trim()).append(", ");
        }
        if (hasText(query.getLocation())) {
            sb.append("位置~").append(query.getLocation().trim()).append(", ");
        }
        if (query.getSpaceType() != null) {
            sb.append("类型=").append(query.getSpaceType()).append(", ");
        }
        if (query.getStatus() != null) {
            sb.append("状态=").append(query.getStatus()).append(", ");
        }
        if (query.getMinCapacity() != null) {
            sb.append("容量>=").append(query.getMinCapacity()).append(", ");
        }
        if (query.getMaxCapacity() != null) {
            sb.append("容量<=").append(query.getMaxCapacity()).append(", ");
        }
        if (sb.isEmpty()) {
            return "无条件";
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }

    /**
     * 名称占用校验（含逻辑删除记录，删除后不复用）。
     *
     * @param excludeId 编辑时排除自身，创建时传 null
     */
    private void requireNameUnused(String name, Long excludeId) {
        Long ownerId = spaceMapper.selectIdByNameIncludingDeleted(name);
        if (ownerId != null && !ownerId.equals(excludeId)) {
            log.warn("[场地名称校验] 名称已被占用：名称={}, 占用行ID={}, 排除ID={}", name, ownerId, excludeId);
            throw new BusinessException("场地名称已存在（含已删除记录，名称不可复用）：" + name);
        }
    }

    private void validateOpenWindow(LocalTime start, LocalTime end) {
        if (!start.isBefore(end)) {
            log.warn("[场地时段校验] 开放时段非法（开始须早于结束）：openStart={}, openEnd={}", start, end);
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "开放开始时间必须早于开放结束时间");
        }
    }

    private void applySort(LambdaQueryWrapper<BizSpace> wrapper, SpacePageQuery query) {
        String sortBy = query.getSortBy();
        String column = sortBy == null ? null : SORT_COLUMNS.get(sortBy);
        if (column == null) {
            wrapper.orderByDesc(BizSpace::getCreateTime);
            return;
        }
        boolean asc = "asc".equalsIgnoreCase(query.getSortOrder());
        wrapper.last("ORDER BY " + column + (asc ? " ASC" : " DESC")
                + (column.equals("create_time") ? "" : ", create_time DESC"));
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String trimToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
