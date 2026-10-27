package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.core.BasePageQuery;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.space.entity.BizSpace;
import com.labmanage.module.space.mapper.BizSpaceMapper;
import com.labmanage.roles.dto.GrantCreateDTO;
import com.labmanage.roles.entity.AssistantGrant;
import com.labmanage.roles.entity.AssistantGrantScope;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.enums.UserType;
import com.labmanage.roles.mapper.AssistantGrantMapper;
import com.labmanage.roles.mapper.AssistantGrantScopeMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.service.AssistantGrantService;
import com.labmanage.roles.vo.GrantVO;
import com.labmanage.roles.vo.StudentOptionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 助理授权服务实现。
 */
@Service
public class AssistantGrantServiceImpl implements AssistantGrantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantGrantServiceImpl.class);

    /** 有效期上限：一学年（366 天，覆盖闰年） */
    private static final long MAX_GRANT_DAYS = 366;

    /** 学生搜索返回上限 */
    private static final int STUDENT_SEARCH_LIMIT = 20;

    private final AssistantGrantMapper grantMapper;

    private final AssistantGrantScopeMapper scopeMapper;

    private final SysUserMapper userMapper;

    private final BizSpaceMapper spaceMapper;

    public AssistantGrantServiceImpl(AssistantGrantMapper grantMapper, AssistantGrantScopeMapper scopeMapper,
                                     SysUserMapper userMapper, BizSpaceMapper spaceMapper) {
        this.grantMapper = grantMapper;
        this.scopeMapper = scopeMapper;
        this.userMapper = userMapper;
        this.spaceMapper = spaceMapper;
    }

    @Override
    public PageResult<GrantVO> pageMine(BasePageQuery query) {
        long begin = System.nanoTime();
        Long teacherId = SecurityUtils.currentUserId();
        LambdaQueryWrapper<AssistantGrant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssistantGrant::getTeacherId, teacherId).orderByDesc(AssistantGrant::getId);
        Page<AssistantGrant> page = grantMapper.selectPage(PageUtils.toPage(query), wrapper);

        List<AssistantGrant> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), query.getPageNum(), query.getPageSize());
        }

        // 联查学生、范围、场地名称
        List<Long> studentIds = records.stream().map(AssistantGrant::getStudentId).distinct().toList();
        Map<Long, SysUser> studentMap = userMapper.selectByIds(studentIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<Long> grantIds = records.stream().map(AssistantGrant::getId).toList();
        List<AssistantGrantScope> scopes = scopeMapper.selectList(new LambdaQueryWrapper<AssistantGrantScope>()
                .in(AssistantGrantScope::getGrantId, grantIds));
        List<Long> labIds = scopes.stream().map(AssistantGrantScope::getLabId).filter(id -> id != null)
                .distinct().toList();
        Map<Long, BizSpace> labMap = labIds.isEmpty() ? Map.of()
                : spaceMapper.selectByIds(labIds).stream()
                        .collect(Collectors.toMap(BizSpace::getId, Function.identity()));
        Map<Long, List<AssistantGrantScope>> scopeMap = scopes.stream()
                .collect(Collectors.groupingBy(AssistantGrantScope::getGrantId));

        Instant now = Instant.now();
        List<GrantVO> rows = records.stream()
                .map(grant -> toVO(grant, studentMap, scopeMap, labMap, now))
                .toList();
        log.info("[助理授权] 列表完成：操作人={}, 页码={}, 条数={}, 总数={}, 耗时={}ms",
                teacherId, query.getPageNum(), rows.size(), page.getTotal(), (System.nanoTime() - begin) / 1_000_000);
        return PageResult.of(rows, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public List<StudentOptionVO> searchStudents(String keyword) {
        long begin = System.nanoTime();
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUserType, UserType.STUDENT.name())
                .eq(SysUser::getEnabled, 1);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysUser::getRealName, kw).or().like(SysUser::getPersonNo, kw));
        }
        wrapper.orderByAsc(SysUser::getPersonNo).last("LIMIT " + STUDENT_SEARCH_LIMIT);
        List<StudentOptionVO> result = userMapper.selectList(wrapper).stream()
                .map(u -> new StudentOptionVO(String.valueOf(u.getId()), u.getRealName(), u.getPersonNo()))
                .toList();
        log.info("[助理授权] 学生搜索完成：操作人={}, 关键字={}, 条数={}, 耗时={}ms",
                SecurityUtils.currentUserId(), keyword, result.size(), (System.nanoTime() - begin) / 1_000_000);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(GrantCreateDTO dto) {
        long begin = System.nanoTime();
        Long teacherId = SecurityUtils.currentUserId();

        // 学生校验：存在、启用、STUDENT 身份（名单派生，客户端不得指定角色）
        SysUser student = userMapper.selectById(dto.studentId());
        if (student == null || !Integer.valueOf(1).equals(student.getEnabled())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "被授权学生不存在或已停用");
        }
        if (!UserType.STUDENT.name().equals(student.getUserType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅可授权学生身份的用户为助理");
        }
        if (student.getId().equals(teacherId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "不能授权本人为助理");
        }

        // 有效期校验：endAt 晚于 startAt、未过期、最长一学年
        Instant startAt = dto.startAt().truncatedTo(ChronoUnit.MILLIS);
        Instant endAt = dto.endAt().truncatedTo(ChronoUnit.MILLIS);
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "失效时间须晚于生效时间");
        }
        if (!endAt.isAfter(Instant.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "失效时间须晚于当前时间");
        }
        if (Duration.between(startAt, endAt).toDays() > MAX_GRANT_DAYS) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "授权有效期最长一学年");
        }

        // 防重：同一老师对同一学生仅允许一条生效中授权（调整请先撤销再新建）。
        // B5：此处先查后插存在并发竞态，数据库层由生成列唯一键
        // uk_assistant_grant_active_pair 兜底（sql/migrations/assistant_grant_active_unique.sql），
        // 并发双写时第二笔落库报重复键，由全局异常处理映射为 40900
        Long activeCount = grantMapper.selectCount(new LambdaQueryWrapper<AssistantGrant>()
                .eq(AssistantGrant::getTeacherId, teacherId)
                .eq(AssistantGrant::getStudentId, student.getId())
                .eq(AssistantGrant::getStatus, "ACTIVE"));
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该学生已有生效中的授权，请先撤销再重新添加");
        }

        // 范围校验：场地须存在（一期仅 LAB 范围）
        List<Long> labIds = dto.scopes().stream().map(GrantCreateDTO.GrantScopeDTO::labId).distinct().toList();
        List<BizSpace> labs = spaceMapper.selectByIds(labIds);
        if (labs.size() != labIds.size()) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "授权范围包含不存在的场地");
        }

        AssistantGrant grant = new AssistantGrant();
        grant.setTeacherId(teacherId);
        grant.setStudentId(student.getId());
        grant.setStartAt(startAt);
        grant.setEndAt(endAt);
        grant.setStatus("ACTIVE");
        grant.setReason(dto.reason() == null || dto.reason().isBlank() ? null : dto.reason().trim());
        grantMapper.insert(grant);

        for (GrantCreateDTO.GrantScopeDTO scope : dto.scopes()) {
            AssistantGrantScope entity = new AssistantGrantScope();
            entity.setGrantId(grant.getId());
            entity.setScopeType(scope.scopeType());
            entity.setLabId(scope.labId());
            entity.setActionCode(scope.actionCode());
            scopeMapper.insert(entity);
        }
        log.info("[助理授权] 创建完成：操作人={}, 授权ID={}, 学生={}({}), 范围数={}, 有效期={}~{}, 耗时={}ms",
                teacherId, grant.getId(), student.getRealName(), student.getId(), dto.scopes().size(),
                startAt, endAt, (System.nanoTime() - begin) / 1_000_000);
        return grant.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        long begin = System.nanoTime();
        Long teacherId = SecurityUtils.currentUserId();
        AssistantGrant grant = grantMapper.selectById(id);
        if (grant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "授权记录不存在");
        }
        if (!grant.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅可撤销本人发起的授权");
        }
        if (!"ACTIVE".equals(grant.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT.getCode(), "该授权已撤销或已到期，无需重复操作");
        }
        grant.setStatus("REVOKED");
        grant.setRevokedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        grant.setRevokedBy(teacherId);
        grantMapper.updateById(grant);
        log.info("[助理授权] 撤销完成：操作人={}, 授权ID={}, 学生ID={}, 耗时={}ms",
                teacherId, id, grant.getStudentId(), (System.nanoTime() - begin) / 1_000_000);
    }

    /** 组装视图：状态按 endAt 派生（ACTIVE 且已过期展示 EXPIRED） */
    private GrantVO toVO(AssistantGrant grant, Map<Long, SysUser> studentMap,
                         Map<Long, List<AssistantGrantScope>> scopeMap, Map<Long, BizSpace> labMap, Instant now) {
        SysUser student = studentMap.get(grant.getStudentId());
        String status = "ACTIVE".equals(grant.getStatus()) && !grant.getEndAt().isAfter(now)
                ? "EXPIRED" : grant.getStatus();
        List<GrantVO.GrantScopeVO> scopes = scopeMap.getOrDefault(grant.getId(), List.of()).stream()
                .map(scope -> {
                    BizSpace lab = scope.getLabId() == null ? null : labMap.get(scope.getLabId());
                    return new GrantVO.GrantScopeVO(
                            String.valueOf(scope.getId()),
                            scope.getScopeType(),
                            scope.getLabId() == null ? null : String.valueOf(scope.getLabId()),
                            lab == null ? null : lab.getLabName(),
                            scope.getActionCode());
                })
                .toList();
        return new GrantVO(
                String.valueOf(grant.getId()),
                String.valueOf(grant.getStudentId()),
                student == null ? null : student.getRealName(),
                student == null ? null : student.getPersonNo(),
                grant.getStartAt(),
                grant.getEndAt(),
                status,
                grant.getReason(),
                scopes,
                grant.getCreateTime());
    }
}
