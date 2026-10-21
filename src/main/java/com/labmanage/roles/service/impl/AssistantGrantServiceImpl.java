package com.labmanage.roles.service.impl;

import com.labmanage.common.utils.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.GrantCreateDTO;
import com.labmanage.roles.dto.GrantPageQueryDTO;
import com.labmanage.roles.entity.AssistantGrant;
import com.labmanage.roles.entity.AssistantGrantScope;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.enums.UserType;
import com.labmanage.roles.mapper.AssistantGrantMapper;
import com.labmanage.roles.mapper.AssistantGrantScopeMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.service.AssistantGrantService;
import com.labmanage.roles.service.AuthService;
import com.labmanage.roles.vo.GrantPageItemVO;
import com.labmanage.roles.vo.StudentOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 助理授权服务实现。
 * 创建/撤销后踢学生会话使权限缓存失效（设计 4.6 节要求）；ASSISTANT 角色按有效授权窗口动态装配。
 * 授权权力落在范围表：MATERIAL_ISSUE 物料领取 → (MATERIAL, ISSUE_RETURN) 行；
 * OFFERING/LAB 课程/实验室级领还为预留，随教学模块落地开放。
 */
@Service
public class AssistantGrantServiceImpl implements AssistantGrantService {

    /** 最长授权时长：一学年（366 天，含闰年） */
    private static final long MAX_GRANT_DAYS = 366;

    /** 授权权力：物料领取 */
    private static final String CAP_MATERIAL_ISSUE = "MATERIAL_ISSUE";

    private final AssistantGrantMapper grantMapper;

    private final AssistantGrantScopeMapper scopeMapper;

    private final SysUserMapper userMapper;

    private final AuthService authService;

    public AssistantGrantServiceImpl(AssistantGrantMapper grantMapper, AssistantGrantScopeMapper scopeMapper,
                                     SysUserMapper userMapper, AuthService authService) {
        this.grantMapper = grantMapper;
        this.scopeMapper = scopeMapper;
        this.userMapper = userMapper;
        this.authService = authService;
    }

    @Override
    public PageResult<GrantPageItemVO> pageMine(GrantPageQueryDTO query) {
        return pageByTeacher(SecurityUtils.currentUserId(), query);
    }

    @Override
    public PageResult<GrantPageItemVO> pageAudit(GrantPageQueryDTO query) {
        return pageByTeacher(null, query);
    }

    @Override
    @Transactional
    public void create(GrantCreateDTO dto) {
        Long teacherId = SecurityUtils.currentUserId();
        Instant now = Instant.now();
        if (!dto.endAt().isAfter(dto.startAt())) {
            throw new BusinessException(40000, "授权结束时间必须晚于开始时间");
        }
        if (!dto.endAt().isAfter(now)) {
            throw new BusinessException(40000, "授权结束时间必须晚于当前时间");
        }
        if (Duration.between(dto.startAt(), dto.endAt()).compareTo(Duration.ofDays(MAX_GRANT_DAYS)) > 0) {
            throw new BusinessException(40000, "单笔授权最长一学年（366 天）");
        }
        SysUser student = userMapper.selectById(dto.studentId());
        if (student == null || !UserType.STUDENT.name().equals(student.getUserType())
                || !Integer.valueOf(1).equals(student.getEnabled())) {
            throw new BusinessException(40000, "被授权人不是有效学生账号");
        }
        AssistantGrant grant = new AssistantGrant();
        grant.setTeacherId(teacherId);
        grant.setStudentId(dto.studentId());
        grant.setStartAt(dto.startAt());
        grant.setEndAt(dto.endAt());
        grant.setStatus("ACTIVE");
        grant.setReason(dto.reason());
        grantMapper.insert(grant);
        // 授权权力落范围行（去重保持插入序）；未识别类型在 DTO 注解已拦截，此处防御
        for (String capability : new LinkedHashSet<>(dto.capabilities())) {
            if (!CAP_MATERIAL_ISSUE.equals(capability)) {
                throw new BusinessException(40000, "授权权力类型无效");
            }
            AssistantGrantScope scope = new AssistantGrantScope();
            scope.setGrantId(grant.getId());
            scope.setScopeType("MATERIAL");
            scope.setActionCode("ISSUE_RETURN");
            scopeMapper.insert(scope);
        }
        // 踢掉学生现有会话，重新登录时按有效授权装配 ASSISTANT 角色
        authService.evictSession(dto.studentId());
    }

    @Override
    public void revoke(Long id) {
        AssistantGrant grant = grantMapper.selectById(id);
        if (grant == null) {
            throw new BusinessException(40400, "授权记录不存在");
        }
        Long loginId = SecurityUtils.currentUserId();
        if (!grant.getTeacherId().equals(loginId)) {
            throw new BusinessException(40300, "只能撤销本人发起的授权");
        }
        if (!"ACTIVE".equals(grant.getStatus())) {
            throw new BusinessException(40900, "该授权已撤销或已结束");
        }
        grant.setStatus("REVOKED");
        grant.setRevokedAt(Instant.now());
        grant.setRevokedBy(loginId);
        grantMapper.updateById(grant);
        // 权限缓存失效：立即踢掉学生会话，助理身份随下次登录重新计算
        authService.evictSession(grant.getStudentId());
    }

    @Override
    public List<StudentOptionVO> studentOptions(String keyword) {
        return grantMapper.selectStudentOptions(keyword == null ? null : keyword.trim());
    }

    private PageResult<GrantPageItemVO> pageByTeacher(Long teacherId, GrantPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<GrantPageItemVO> page = grantMapper.selectGrantPage(PageUtils.toPage(query), teacherId, query);
        PageResult<GrantPageItemVO> result = PageUtils.toPageResult(page);
        return fillCapabilities(result);
    }

    /**
     * 批量回填授权权力：按本页授权 ID 一次 IN 查询范围行，聚合为能力码列表。
     */
    private PageResult<GrantPageItemVO> fillCapabilities(PageResult<GrantPageItemVO> result) {
        List<GrantPageItemVO> records = result.records();
        if (records.isEmpty()) {
            return result;
        }
        List<Long> grantIds = records.stream().map(vo -> Long.valueOf(vo.id())).toList();
        List<AssistantGrantScope> scopes = scopeMapper.selectList(
                new LambdaQueryWrapper<AssistantGrantScope>().in(AssistantGrantScope::getGrantId, grantIds));
        Map<Long, List<String>> capsByGrant = new LinkedHashMap<>();
        for (AssistantGrantScope scope : scopes) {
            capsByGrant.computeIfAbsent(scope.getGrantId(), k -> new ArrayList<>()).add(toCapability(scope));
        }
        List<GrantPageItemVO> filled = records.stream()
                .map(vo -> new GrantPageItemVO(vo.id(), vo.teacherId(), vo.teacherName(), vo.studentId(),
                        vo.studentName(), vo.studentNo(), vo.startAt(), vo.endAt(), vo.status(), vo.revokedAt(),
                        vo.revokedByName(), vo.reason(), vo.createTime(),
                        capsByGrant.getOrDefault(Long.valueOf(vo.id()), List.of())))
                .toList();
        return new PageResult<>(filled, result.total(), result.pageNum(), result.pageSize());
    }

    /** 范围行 → 授权权力码（MATERIAL 领还一体映射为 MATERIAL_ISSUE） */
    private static String toCapability(AssistantGrantScope scope) {
        if ("MATERIAL".equals(scope.getScopeType())) {
            return CAP_MATERIAL_ISSUE;
        }
        return scope.getScopeType() + "_" + scope.getActionCode();
    }
}
