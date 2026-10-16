package com.labmanage.module.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.booking.mapper.LabBookingMapper;
import com.labmanage.module.booking.mapper.LabSlotMapper;
import com.labmanage.module.space.dto.SpaceBatchStatusDTO;
import com.labmanage.module.space.dto.SpaceCreateDTO;
import com.labmanage.module.space.dto.SpacePageQuery;
import com.labmanage.module.space.dto.SpaceUpdateDTO;
import com.labmanage.module.space.entity.BizSpace;
import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.enums.SpaceType;
import com.labmanage.module.space.mapper.BizSpaceMapper;
import com.labmanage.module.space.service.SpaceService;
import com.labmanage.module.space.vo.SpaceBatchStatusVO;
import com.labmanage.module.space.vo.SpaceDetailVO;
import com.labmanage.module.space.vo.SpaceListVO;
import com.labmanage.module.space.vo.SpaceOptionVO;
import com.labmanage.module.space.vo.SpaceStatsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 场地业务实现（lab 单表）。
 * <p>
 * 授权说明：接口访问由 @SaCheckPermission 按权限标识控制（管理员全量、
 * 师生只读 space:space:list/read）。sys_user_data_scope 的 DEPARTMENT/CAMPUS/BUILDING
 * 行级范围在单表模式下无组织锚点列，一期不做行级过滤；LAB 级范围仍以
 * lab.id 为目标，删除前做引用检查。
 */
@Service
public class SpaceServiceImpl implements SpaceService {

    private static final Logger log = LoggerFactory.getLogger(SpaceServiceImpl.class);

    /** 排序字段白名单（防注入）：入参字段 → DB 列 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "labName", "lab_name",
            "capacity", "capacity",
            "createTime", "create_time",
            "updateTime", "update_time"
    );

    /** 开放时段缺省值与 HH:mm 输入/输出格式（上海本地时间，无时区换算） */
    private static final LocalTime DEFAULT_OPEN_START = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_OPEN_END = LocalTime.of(21, 0);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** 导出与展示用业务时区（AGENTS：页面展示按上海本地日历） */
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm").withZone(SHANGHAI);

    private final BizSpaceMapper spaceMapper;
    private final LabBookingMapper bookingMapper;
    private final LabSlotMapper slotMapper;

    public SpaceServiceImpl(BizSpaceMapper spaceMapper,
                            LabBookingMapper bookingMapper,
                            LabSlotMapper slotMapper) {
        this.spaceMapper = spaceMapper;
        this.bookingMapper = bookingMapper;
        this.slotMapper = slotMapper;
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public PageResult<SpaceListVO> page(SpacePageQuery query) {
        long begin = System.nanoTime();
        LambdaQueryWrapper<BizSpace> wrapper = buildWrapper(query);
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
                SecurityUtils.currentUserId(), id, space.getLabName(), space.getStatus(),
                (System.nanoTime() - begin) / 1_000_000);
        return toDetailVO(space);
    }

    @Override
    public List<SpaceOptionVO> options(SpaceType labType, Integer minCapacity, Boolean availableOnly) {
        LambdaQueryWrapper<BizSpace> wrapper = new LambdaQueryWrapper<>();
        if (labType != null) {
            wrapper.eq(BizSpace::getLabType, labType);
        }
        if (minCapacity != null) {
            wrapper.ge(BizSpace::getCapacity, minCapacity);
        }
        if (Boolean.TRUE.equals(availableOnly)) {
            wrapper.eq(BizSpace::getStatus, SpaceStatus.AVAILABLE);
        }
        wrapper.orderByAsc(BizSpace::getLabName);

        List<SpaceOptionVO> result = spaceMapper.selectList(wrapper).stream()
                .map(s -> new SpaceOptionVO(
                        String.valueOf(s.getId()), s.getLabName(), s.getLabType(),
                        s.getCapacity(), s.getStatus()))
                .toList();
        log.info("[场地选项] 完成：操作人={}, 类型={}, 最小容量={}, 仅可用={}, 返回条数={}",
                SecurityUtils.currentUserId(), labType, minCapacity, availableOnly, result.size());
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
        List<SpaceStatsVO.TypeCountVO> typeDistribution = new ArrayList<>();
        for (Map<String, Object> row : spaceMapper.countGroupByType()) {
            String type = String.valueOf(row.get("lab_type"));
            SpaceType labType = SpaceType.valueOf(type);
            typeDistribution.add(new SpaceStatsVO.TypeCountVO(
                    type, labType.getDisplayName(), ((Number) row.get("cnt")).longValue()));
        }
        log.info("[场地统计] 完成：操作人={}, 总数={}, 可用={}, 维护中={}, 停用={}, 类型分布={}",
                SecurityUtils.currentUserId(), available + maintaining + closed, available, maintaining, closed,
                typeDistribution);
        return new SpaceStatsVO(available + maintaining + closed, available, maintaining, closed, typeDistribution);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaceBatchStatusVO batchStatus(SpaceBatchStatusDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        SpaceStatus target = dto.target();
        if (target != SpaceStatus.AVAILABLE && target != SpaceStatus.CLOSED) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "批量操作仅支持启用（AVAILABLE）或停用（CLOSED）");
        }
        log.info("[场地批量{}] 开始：操作人={}, 目标状态={}, 场地ID={}",
                target == SpaceStatus.AVAILABLE ? "启用" : "停用", operatorId, target, dto.ids());

        List<String> missing = new ArrayList<>();
        int updated = 0;
        for (Long id : dto.ids()) {
            BizSpace space = spaceMapper.selectById(id);
            if (space == null) {
                missing.add(String.valueOf(id));
                log.warn("[场地批量] 场地不存在或已删除，跳过：操作人={}, 场地ID={}", operatorId, id);
                continue;
            }
            if (space.getStatus() == target) {
                continue;
            }
            int rows = spaceMapper.update(null, new LambdaUpdateWrapper<BizSpace>()
                    .eq(BizSpace::getId, id)
                    .set(BizSpace::getStatus, target)
                    .set(BizSpace::getUpdateUserId, operatorId));
            if (rows > 0) {
                updated++;
                log.info("[场地批量{}] 单场地完成：操作人={}, 场地ID={}, 名称={}, 原状态={} → 新状态={}",
                        target == SpaceStatus.AVAILABLE ? "启用" : "停用",
                        operatorId, id, space.getLabName(), space.getStatus(), target);
            }
        }
        log.info("[场地批量{}] 完成：操作人={}, 目标状态={}, 提交数={}, 成功数={}, 未命中={}",
                target == SpaceStatus.AVAILABLE ? "启用" : "停用", operatorId, target,
                dto.ids().size(), updated, missing);
        return new SpaceBatchStatusVO(updated, missing);
    }

    @Override
    public byte[] exportCsv(SpacePageQuery query) {
        long begin = System.nanoTime();
        // 与分页同条件导出；上限保护防全表扫描。
        // 注意 last() 多次调用以最后一次为准：显式排序占用 last 段时，
        // 必须把 ORDER BY 与 LIMIT 合并为同一片段，否则排序会被 LIMIT 覆盖。
        LambdaQueryWrapper<BizSpace> wrapper = buildWrapper(query);
        String orderBy = explicitOrderBy(query);
        wrapper.last(orderBy == null ? "LIMIT 10000" : orderBy + " LIMIT 10000");
        List<BizSpace> spaces = spaceMapper.selectList(wrapper);

        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff'); // UTF-8 BOM：Excel 识别中文表头
        sb.append("场地名称,类型,位置,容纳人数,开放时段,状态,配套设备,创建时间(上海),更新时间(上海)\r\n");
        for (BizSpace s : spaces) {
            sb.append(csv(s.getLabName())).append(',')
                    .append(csv(s.getLabType() == null ? null : s.getLabType().getDisplayName())).append(',')
                    .append(csv(s.getLocationDetail())).append(',')
                    .append(s.getCapacity()).append(',')
                    .append(formatTime(s.getOpenStart())).append('~').append(formatTime(s.getOpenEnd())).append(',')
                    .append(csv(s.getStatus() == null ? null : s.getStatus().getDisplayName())).append(',')
                    .append(csv(s.getFacilities())).append(',')
                    .append(csv(formatDateTime(s.getCreateTime()))).append(',')
                    .append(csv(formatDateTime(s.getUpdateTime()))).append("\r\n");
        }
        log.info("[场地导出] 完成：操作人={}, 条件=[{}], 导出行数={}, 耗时={}ms",
                SecurityUtils.currentUserId(), buildFilterSummary(query), spaces.size(),
                (System.nanoTime() - begin) / 1_000_000);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ 写入

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaceDetailVO create(SpaceCreateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[场地新增] 开始：操作人={}, 入参={}", operatorId, dto);

        String name = dto.labName().trim();
        requireNameUnused(name, null);

        LocalTime openStart = hasText(dto.openStart()) ? LocalTime.parse(dto.openStart(), HH_MM) : DEFAULT_OPEN_START;
        LocalTime openEnd = hasText(dto.openEnd()) ? LocalTime.parse(dto.openEnd(), HH_MM) : DEFAULT_OPEN_END;
        validateOpenWindow(openStart, openEnd);

        BizSpace space = new BizSpace();
        space.setLabName(name);
        space.setLabType(dto.labType());
        space.setLocationDetail(trimToNull(dto.locationDetail()));
        space.setCapacity(dto.capacity());
        space.setFacilities(trimToNull(dto.facilities()));
        space.setOpenStart(openStart);
        space.setOpenEnd(openEnd);
        space.setStatus(dto.status() == null ? SpaceStatus.AVAILABLE : dto.status());

        spaceMapper.insert(space);
        log.info("[场地新增] 完成：操作人={}, 场地ID={}, 名称={}, 类型={}, 状态={}, 开放时段={}~{}",
                operatorId, space.getId(), name, space.getLabType(), space.getStatus(), openStart, openEnd);
        return toDetailVO(requireSpace(space.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpaceDetailVO update(Long id, SpaceUpdateDTO dto) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[场地编辑] 开始：操作人={}, 场地ID={}, 入参={}", operatorId, id, dto);

        BizSpace space = requireSpace(id);
        String name = dto.labName().trim();
        requireNameUnused(name, id);

        // 空值语义：开放时段与状态为空表示保留原值；location/facilities 为空即清空
        LocalTime openStart = hasText(dto.openStart()) ? LocalTime.parse(dto.openStart(), HH_MM) : space.getOpenStart();
        LocalTime openEnd = hasText(dto.openEnd()) ? LocalTime.parse(dto.openEnd(), HH_MM) : space.getOpenEnd();
        validateOpenWindow(openStart, openEnd);
        SpaceStatus status = dto.status() == null ? space.getStatus() : dto.status();

        int rows = spaceMapper.update(null, new LambdaUpdateWrapper<BizSpace>()
                .eq(BizSpace::getId, id)
                .set(BizSpace::getLabName, name)
                .set(BizSpace::getLabType, dto.labType())
                .set(BizSpace::getLocationDetail, trimToNull(dto.locationDetail()))
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
                operatorId, id, space.getLabName(), space.getStatus());
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
                operatorId, id, space.getLabName(), space.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long operatorId = SecurityUtils.currentUserId();
        log.info("[场地删除] 开始：操作人={}, 场地ID={}", operatorId, id);

        BizSpace space = requireSpace(id);

        // 删除前引用检查：LAB 级数据范围授权、未结预约与未释放占用；资产模块落地后追加
        List<String> conflicts = new ArrayList<>();
        long scopeCount = spaceMapper.countLabScopeBySpaceId(id);
        if (scopeCount > 0) {
            conflicts.add("存在 " + scopeCount + " 条 LAB 级数据范围授权，请先解除");
        }
        long activeBookings = bookingMapper.countActiveByLabId(id);
        if (activeBookings > 0) {
            conflicts.add("存在 " + activeBookings + " 条未结预约（待审批/已批准），请先处理");
        }
        long unreleasedSlots = slotMapper.countUnreleasedByLabId(id);
        if (unreleasedSlots > 0) {
            conflicts.add("存在 " + unreleasedSlots + " 条未释放场地占用，请先取消对应预约");
        }
        // TODO 资产模块：equipment_asset / stock_location 启用记录
        if (!conflicts.isEmpty()) {
            log.warn("[场地删除] 存在未结业务引用，拒绝删除：操作人={}, 场地ID={}, 名称={}, 冲突项=[{}]",
                    operatorId, id, space.getLabName(), String.join("；", conflicts));
            throw new BusinessException(ResultCode.CONFLICT.getCode(),
                    "场地存在未结业务引用，无法删除：" + String.join("；", conflicts));
        }

        int rows = spaceMapper.delete(new LambdaQueryWrapper<BizSpace>().eq(BizSpace::getId, id));
        if (rows == 0) {
            log.warn("[场地删除] 删除未命中任何行（场地可能已被删除）：操作人={}, 场地ID={}", operatorId, id);
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        log.info("[场地删除] 完成：操作人={}, 场地ID={}, 名称={}, 原状态={}（逻辑删除，名称不可复用）",
                operatorId, id, space.getLabName(), space.getStatus());
    }

    // ------------------------------------------------------------- 组装/辅助

    /** 分页/导出共用的查询条件组装 */
    private LambdaQueryWrapper<BizSpace> buildWrapper(SpacePageQuery query) {
        LambdaQueryWrapper<BizSpace> wrapper = new LambdaQueryWrapper<>();
        if (hasText(query.getLabName())) {
            wrapper.like(BizSpace::getLabName, query.getLabName().trim());
        }
        if (hasText(query.getLocationDetail())) {
            wrapper.like(BizSpace::getLocationDetail, query.getLocationDetail().trim());
        }
        if (query.getLabType() != null) {
            wrapper.eq(BizSpace::getLabType, query.getLabType());
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
        return wrapper;
    }

    /** CSV 字段转义：含逗号/引号/换行时整体加引号，内部引号翻倍 */
    private String csv(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private String formatDateTime(Instant time) {
        return time == null ? "" : EXPORT_TIME.format(time);
    }

    private SpaceListVO toListVO(BizSpace s) {
        return new SpaceListVO(
                String.valueOf(s.getId()),
                s.getLabName(),
                s.getLabType(),
                s.getLabType() == null ? null : s.getLabType().getDisplayName(),
                s.getLocationDetail(),
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
                s.getLabName(),
                s.getLabType(),
                s.getLabType() == null ? null : s.getLabType().getDisplayName(),
                s.getLocationDetail(),
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
        if (hasText(query.getLabName())) {
            sb.append("名称~").append(query.getLabName().trim()).append(", ");
        }
        if (hasText(query.getLocationDetail())) {
            sb.append("位置~").append(query.getLocationDetail().trim()).append(", ");
        }
        if (query.getLabType() != null) {
            sb.append("类型=").append(query.getLabType()).append(", ");
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
        String orderBy = explicitOrderBy(query);
        if (orderBy == null) {
            wrapper.orderByDesc(BizSpace::getCreateTime);
            return;
        }
        wrapper.last(orderBy);
    }

    /**
     * 显式排序子句（sortBy 命中白名单时返回完整 ORDER BY 片段，否则返回 null 走默认排序）。
     * 列名来自白名单映射，不存在注入风险；抽出共用以避免与导出 LIMIT 片段拼接时重复逻辑。
     */
    private String explicitOrderBy(SpacePageQuery query) {
        String sortBy = query.getSortBy();
        String column = sortBy == null ? null : SORT_COLUMNS.get(sortBy);
        if (column == null) {
            return null;
        }
        boolean asc = "asc".equalsIgnoreCase(query.getSortOrder());
        return "ORDER BY " + column + (asc ? " ASC" : " DESC")
                + (column.equals("create_time") ? "" : ", create_time DESC");
    }

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String trimToNull(String s) {
        return hasText(s) ? s.trim() : null;
    }
}
