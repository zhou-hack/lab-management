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
import com.labmanage.module.space.service.support.SpaceImportFileParser;
import com.labmanage.module.space.service.support.SpaceImportFileParser.RawRow;
import com.labmanage.module.space.vo.SpaceBatchStatusVO;
import com.labmanage.module.space.vo.SpaceImportVO;
import com.labmanage.module.space.vo.SpaceDetailVO;
import com.labmanage.module.space.vo.SpaceListVO;
import com.labmanage.module.space.vo.SpaceOptionVO;
import com.labmanage.module.space.vo.SpaceStatsVO;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 导入：开放时段单元格中提取 HH:mm（兼容导出的 ~ 与 -、—、～、至 等分隔写法） */
    private static final Pattern IMPORT_TIME_TOKEN = Pattern.compile("(\\d{1,2}):(\\d{2})");
    /** 导入：容纳人数仅接受 1-5 位正整数（范围在代码内二次校验） */
    private static final Pattern IMPORT_DIGITS = Pattern.compile("\\d{1,5}");
    /** 导入：批量插入每批行数（大文件分批提交，控制单批内存与锁持有时间） */
    private static final int IMPORT_INSERT_BATCH = 500;
    /** 导入：容纳人数上限（与前端录入控件一致） */
    private static final int IMPORT_MAX_CAPACITY = 9999;

    private final BizSpaceMapper spaceMapper;
    private final LabBookingMapper bookingMapper;
    private final LabSlotMapper slotMapper;
    private final SpaceImportFileParser importFileParser;
    private final SqlSessionFactory sqlSessionFactory;

    public SpaceServiceImpl(BizSpaceMapper spaceMapper,
                            LabBookingMapper bookingMapper,
                            LabSlotMapper slotMapper,
                            SpaceImportFileParser importFileParser,
                            SqlSessionFactory sqlSessionFactory) {
        this.spaceMapper = spaceMapper;
        this.bookingMapper = bookingMapper;
        this.slotMapper = slotMapper;
        this.importFileParser = importFileParser;
        this.sqlSessionFactory = sqlSessionFactory;
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
    public List<SpaceOptionVO> options(SpaceType labType, Integer minCapacity, Boolean availableOnly, String keyword, int limit) {
        log.info("[场地选项] 开始：操作人={}, 类型={}, 最小容量={}, 仅可用={}, 关键字={}, 上限={}",
                SecurityUtils.currentUserId(), labType, minCapacity, availableOnly, keyword, limit);
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
        // Typeahead 关键字模糊查询（联想下拉）
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(BizSpace::getLabName, keyword.trim());
        }
        wrapper.orderByAsc(BizSpace::getLabName);
        wrapper.last("LIMIT " + limit);

        List<SpaceOptionVO> result = spaceMapper.selectList(wrapper).stream()
                .map(s -> new SpaceOptionVO(
                        String.valueOf(s.getId()), s.getLabName(), s.getLabType(),
                        s.getCapacity(), s.getStatus()))
                .toList();
        log.info("[场地选项] 完成：操作人={}, 类型={}, 最小容量={}, 仅可用={}, 关键字={}, 上限={}, 返回条数={}",
                SecurityUtils.currentUserId(), labType, minCapacity, availableOnly, keyword, limit, result.size());
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

    @Override
    public byte[] importTemplateCsv() {
        // 表头与导出完全一致，保证「导出 → 修改 → 导入」闭环
        String header = '\ufeff' + String.join(",", SpaceImportFileParser.HEADERS) + "\r\n";
        return header.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public SpaceImportVO importSpaces(MultipartFile file) {
        long begin = System.nanoTime();
        Long operatorId = SecurityUtils.currentUserId();
        String filename = file == null ? "(空)" : file.getOriginalFilename();
        log.info("[场地导入] 开始：操作人={}, 文件名={}", operatorId, filename);

        // 文件级校验（空文件/格式/表头/行数）在解析器内抛 40000
        List<RawRow> rows = importFileParser.parse(file);

        // 名称查重快照（含逻辑删除记录，删除后名称不可复用），一次性查库
        Set<String> dbNames = new HashSet<>(spaceMapper.selectAllNamesIncludingDeleted());
        Set<String> seenInFile = new HashSet<>();
        Map<String, Integer> firstRowByName = new HashMap<>();

        List<BizSpace> validSpaces = new ArrayList<>();
        List<SpaceImportVO.Failure> failures = new ArrayList<>();

        for (RawRow row : rows) {
            Map<String, String> c = row.cells();
            List<String> errors = new ArrayList<>();

            String name = trimToNull(c.get("场地名称"));
            if (name == null) {
                errors.add("场地名称不能为空");
            } else if (name.length() > 128) {
                errors.add("场地名称长度不能超过 128");
            }

            SpaceType type = parseImportType(c.get("类型"), errors);

            String location = trimToNull(c.get("位置"));
            if (location != null && location.length() > 128) {
                errors.add("位置长度不能超过 128");
            }

            Integer capacity = parseImportCapacity(c.get("容纳人数"), errors);

            String facilities = trimToNull(c.get("配套设备"));
            if (facilities != null && facilities.length() > 2000) {
                errors.add("配套设备清单长度不能超过 2000");
            }

            LocalTime[] window = parseImportWindow(c.get("开放时段"), errors);
            SpaceStatus status = parseImportStatus(c.get("状态"), errors);

            // 重复检测仅对本身合法的名称执行，避免叠加噪音错误
            if (name != null && name.length() <= 128) {
                if (dbNames.contains(name)) {
                    errors.add("场地名称已存在（含已删除记录，名称不可复用）");
                } else if (!seenInFile.add(name)) {
                    errors.add("与文件内第 " + firstRowByName.get(name) + " 行的场地名称重复");
                } else {
                    firstRowByName.put(name, row.rowNum());
                }
            }

            if (!errors.isEmpty()) {
                failures.add(new SpaceImportVO.Failure(row.rowNum(), name == null ? "" : name,
                        String.join("；", errors), new LinkedHashMap<>(c)));
                continue;
            }

            BizSpace space = new BizSpace();
            space.setLabName(name);
            space.setLabType(type);
            space.setLocationDetail(location);
            space.setCapacity(capacity);
            space.setFacilities(facilities);
            space.setOpenStart(window[0]);
            space.setOpenEnd(window[1]);
            space.setStatus(status);
            validSpaces.add(space);
        }

        // 合法行分批落库（每批独立提交，部分成功；仅写 lab 表，不触碰预约等下游业务）
        batchInsertSpaces(validSpaces);

        int total = rows.size();
        int successCount = validSpaces.size();
        int failedCount = failures.size();
        log.info("[场地导入] 完成：操作人={}, 文件={}, 数据行={}, 成功={}, 失败={}, 耗时={}ms",
                operatorId, filename, total, successCount, failedCount,
                (System.nanoTime() - begin) / 1_000_000);
        if (failedCount > 0) {
            log.warn("[场地导入] 存在失败行：操作人={}, 失败={} 条，首条=第{}行「{}」原因={}",
                    operatorId, failedCount,
                    failures.get(0).rowNum(), failures.get(0).labName(), failures.get(0).reason());
            // 逐条打印前 20 条失败明细，便于审计与排查（超出部分仅计数，避免超大文件刷日志）
            failures.stream().limit(20).forEach(f -> log.warn(
                    "[场地导入] 失败明细：操作人={}, 第{}行「{}」, 原因={}",
                    operatorId, f.rowNum(), f.labName(), f.reason()));
            if (failedCount > 20) {
                log.warn("[场地导入] 其余 {} 条失败明细见接口返回/失败明细文件", failedCount - 20);
            }
        }
        return new SpaceImportVO(total, successCount, failedCount, failures);
    }

    /** BATCH 执行器分批插入，按 {@link #IMPORT_INSERT_BATCH} 刷新并清缓存 */
    private void batchInsertSpaces(List<BizSpace> spaces) {
        if (spaces.isEmpty()) {
            return;
        }
        try (SqlSession batchSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            BizSpaceMapper batchMapper = batchSession.getMapper(BizSpaceMapper.class);
            int count = 0;
            for (BizSpace space : spaces) {
                batchMapper.insert(space);
                count++;
                if (count % IMPORT_INSERT_BATCH == 0) {
                    batchSession.flushStatements();
                    batchSession.clearCache();
                }
            }
            batchSession.commit();
        }
    }

    /** 导入类型解析：接受枚举 code 与中文名（教室/实训室），非法值收集错误 */
    private SpaceType parseImportType(String raw, List<String> errors) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            errors.add("场地类型不能为空");
            return null;
        }
        return switch (value) {
            case "CLASSROOM", "教室" -> SpaceType.CLASSROOM;
            case "TRAINING_ROOM", "实训室" -> SpaceType.TRAINING_ROOM;
            default -> {
                errors.add("场地类型只能是「教室」或「实训室」");
                yield null;
            }
        };
    }

    /** 导入人数解析：必填、纯整数、1..9999 */
    private Integer parseImportCapacity(String raw, List<String> errors) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            errors.add("容纳人数不能为空");
            return null;
        }
        if (!IMPORT_DIGITS.matcher(value).matches()) {
            errors.add("容纳人数必须为正整数");
            return null;
        }
        int capacity = Integer.parseInt(value);
        if (capacity < 1 || capacity > IMPORT_MAX_CAPACITY) {
            errors.add("容纳人数须在 1 到 " + IMPORT_MAX_CAPACITY + " 之间");
            return null;
        }
        return capacity;
    }

    /**
     * 导入开放时段解析：空值取默认 08:00~21:00；否则须恰好含两个 HH:mm，
     * 且开始早于结束。分隔符任意（导出格式为 08:00~21:00）。
     */
    private LocalTime[] parseImportWindow(String raw, List<String> errors) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return new LocalTime[]{DEFAULT_OPEN_START, DEFAULT_OPEN_END};
        }
        Matcher matcher = IMPORT_TIME_TOKEN.matcher(value);
        List<LocalTime> times = new ArrayList<>(2);
        while (matcher.find()) {
            int hour = Integer.parseInt(matcher.group(1));
            int minute = Integer.parseInt(matcher.group(2));
            if (hour > 23) {
                errors.add("开放时段小时不能超过 23");
                return null;
            }
            times.add(LocalTime.of(hour, minute));
        }
        if (times.size() != 2) {
            errors.add("开放时段格式须为「HH:mm~HH:mm」（如 08:00~21:00）");
            return null;
        }
        LocalTime start = times.get(0);
        LocalTime end = times.get(1);
        if (!start.isBefore(end)) {
            errors.add("开放开始时间必须早于开放结束时间");
            return null;
        }
        return new LocalTime[]{start, end};
    }

    /** 导入状态解析：空值默认可用；接受 code 与中文名，「停用」作为「关闭」的前端用语别名 */
    private SpaceStatus parseImportStatus(String raw, List<String> errors) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return SpaceStatus.AVAILABLE;
        }
        return switch (value) {
            case "AVAILABLE", "可用" -> SpaceStatus.AVAILABLE;
            case "MAINTAINING", "维护中" -> SpaceStatus.MAINTAINING;
            case "CLOSED", "关闭", "停用" -> SpaceStatus.CLOSED;
            default -> {
                errors.add("状态只能是「可用/维护中/停用」之一");
                yield null;
            }
        };
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
                s.getFacilities(),
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
