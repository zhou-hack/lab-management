package com.labmanage.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.ResultCode;
import com.labmanage.common.utils.Ids;
import com.labmanage.common.utils.SecurityUtils;
import com.labmanage.module.item.dto.AssistantGrantCreateDTO;
import com.labmanage.module.item.dto.AssistantGrantPageQuery;
import com.labmanage.module.item.entity.MaterialAssistantGrant;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.enums.PickupMode;
import com.labmanage.module.item.mapper.MaterialAssistantGrantMapper;
import com.labmanage.module.item.vo.AssistantGrantVO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.vo.StudentOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssistantGrantService {

    private static final Duration MAX_GRANT_DURATION = Duration.ofDays(366);

    private final MaterialAssistantGrantMapper grantMapper;
    private final SysUserMapper userMapper;
    private final ItemTimeService timeService;

    public AssistantGrantService(MaterialAssistantGrantMapper grantMapper,
                                 SysUserMapper userMapper,
                                 ItemTimeService timeService) {
        this.grantMapper = grantMapper;
        this.userMapper = userMapper;
        this.timeService = timeService;
    }

    public PageResult<AssistantGrantVO> pageMine(AssistantGrantPageQuery query) {
        Long teacherId = SecurityUtils.currentUserId();
        String status = normalizeStatus(query.status());
        Instant now = timeService.now();
        LambdaQueryWrapper<MaterialAssistantGrant> wrapper = Wrappers.<MaterialAssistantGrant>lambdaQuery()
                .eq(MaterialAssistantGrant::getTeacherId, teacherId)
                .orderByDesc(MaterialAssistantGrant::getCreateTime)
                .orderByDesc(MaterialAssistantGrant::getId);
        if ("REVOKED".equals(status)) {
            wrapper.isNotNull(MaterialAssistantGrant::getRevokedAt);
        } else if (status != null) {
            wrapper.isNull(MaterialAssistantGrant::getRevokedAt);
            if ("ACTIVE".equals(status)) {
                wrapper.gt(MaterialAssistantGrant::getEndAt, now);
            } else {
                wrapper.le(MaterialAssistantGrant::getEndAt, now);
            }
        }
        Page<MaterialAssistantGrant> page = grantMapper.selectPage(
                new Page<>(query.pageNum(), query.pageSize()), wrapper);
        List<MaterialAssistantGrant> records = page.getRecords();
        Set<Long> userIds = new HashSet<>();
        records.forEach(grant -> {
            userIds.add(grant.getTeacherId());
            userIds.add(grant.getStudentId());
        });
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<AssistantGrantVO> result = records.stream()
                .map(grant -> toVO(grant, users, now))
                .toList();
        return PageResult.of(result, page.getTotal(), (int) page.getCurrent(), (int) page.getSize());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public AssistantGrantVO create(AssistantGrantCreateDTO dto) {
        Long teacherId = SecurityUtils.currentUserId();
        if (userMapper.selectByIdForUpdate(teacherId) == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), "当前教师账号不存在");
        }
        Long studentId = Ids.parse(dto.studentId());
        SysUser student = userMapper.selectById(studentId);
        requireEnabledStudent(student);
        Instant startAt = dto.startAt().toInstant();
        Instant endAt = dto.endAt().toInstant();
        Instant now = timeService.now();
        if (!startAt.isBefore(endAt)) {
            throw badRequest("授权结束时间必须晚于开始时间");
        }
        if (!endAt.isAfter(now)) {
            throw badRequest("授权结束时间必须晚于当前时间");
        }
        if (Duration.between(startAt, endAt).compareTo(MAX_GRANT_DURATION) > 0) {
            throw badRequest("授权时长不能超过366天");
        }
        Long duplicates = grantMapper.selectCount(Wrappers.<MaterialAssistantGrant>lambdaQuery()
                .eq(MaterialAssistantGrant::getTeacherId, teacherId)
                .eq(MaterialAssistantGrant::getStudentId, studentId)
                .isNull(MaterialAssistantGrant::getRevokedAt)
                .gt(MaterialAssistantGrant::getEndAt, now)
                .lt(MaterialAssistantGrant::getStartAt, endAt)
                .gt(MaterialAssistantGrant::getEndAt, startAt));
        if (duplicates > 0) {
            throw conflict("同一教师与学生已存在时间重叠的有效授权");
        }
        MaterialAssistantGrant grant = new MaterialAssistantGrant();
        grant.setTeacherId(teacherId);
        grant.setStudentId(studentId);
        grant.setStartAt(startAt);
        grant.setEndAt(endAt);
        grant.setReason(trimToNull(dto.reason()));
        grantMapper.insert(grant);
        MaterialAssistantGrant saved = grantMapper.selectById(grant.getId());
        SysUser teacher = userMapper.selectById(teacherId);
        return toVO(saved, Map.of(teacherId, teacher, studentId, student), timeService.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        Long teacherId = SecurityUtils.currentUserId();
        MaterialAssistantGrant grant = grantMapper.selectByIdForUpdate(id);
        if (grant == null || !teacherId.equals(grant.getTeacherId())) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "助理授权不存在");
        }
        if (grant.getRevokedAt() != null) {
            throw conflict("助理授权已撤销");
        }
        Instant now = timeService.now();
        int updated = grantMapper.update(null, Wrappers.<MaterialAssistantGrant>lambdaUpdate()
                .eq(MaterialAssistantGrant::getId, id)
                .eq(MaterialAssistantGrant::getTeacherId, teacherId)
                .isNull(MaterialAssistantGrant::getRevokedAt)
                .set(MaterialAssistantGrant::getRevokedAt, now)
                .set(MaterialAssistantGrant::getRevokedBy, teacherId)
                .set(MaterialAssistantGrant::getUpdateTime, now)
                .set(MaterialAssistantGrant::getUpdateUserId, teacherId));
        if (updated != 1) {
            throw conflict("助理授权状态已变化，请刷新后重试");
        }
    }

    public List<StudentOptionVO> studentOptions(String keyword) {
        return userMapper.selectStudentOptions(trimToNull(keyword));
    }

    public MaterialAssistantGrant findEffectiveGrantForUpdate(Long teacherId, Long studentId, Instant now) {
        return grantMapper.selectOne(Wrappers.<MaterialAssistantGrant>lambdaQuery()
                .eq(MaterialAssistantGrant::getTeacherId, teacherId)
                .eq(MaterialAssistantGrant::getStudentId, studentId)
                .isNull(MaterialAssistantGrant::getRevokedAt)
                .le(MaterialAssistantGrant::getStartAt, now)
                .gt(MaterialAssistantGrant::getEndAt, now)
                .orderByDesc(MaterialAssistantGrant::getId)
                .last("LIMIT 1 FOR UPDATE"));
    }

    public PickupResolution resolvePickup(MaterialClaim claim) {
        return resolvePickup(claim, false);
    }

    public PickupResolution lockAndResolvePickup(MaterialClaim claim) {
        return resolvePickup(claim, true);
    }

    private PickupResolution resolvePickup(MaterialClaim claim, boolean lockGrant) {
        PickupMode mode = claim.getPickupMode() == null ? PickupMode.SELF : claim.getPickupMode();
        if (mode == PickupMode.SELF) {
            SysUser applicant = userMapper.selectById(claim.getApplicantId());
            return new PickupResolution(mode, claim.getApplicantId(),
                    applicant == null ? null : applicant.getRealName(),
                    applicant != null, applicant == null ? "领取人不存在" : null);
        }

        Long studentId = claim.getDelegateStudentId();
        SysUser student = studentId == null ? null : userMapper.selectById(studentId);
        String studentName = student == null ? null : student.getRealName();
        if (studentId == null || claim.getDelegateGrantId() == null) {
            return new PickupResolution(mode, studentId, studentName, false, "代领委派信息不完整");
        }
        MaterialAssistantGrant grant = lockGrant
                ? grantMapper.selectByIdForUpdate(claim.getDelegateGrantId())
                : grantMapper.selectById(claim.getDelegateGrantId());
        if (grant == null) {
            return new PickupResolution(mode, studentId, studentName, false, "代领授权不存在");
        }
        if (!claim.getApplicantId().equals(grant.getTeacherId()) || !studentId.equals(grant.getStudentId())) {
            return new PickupResolution(mode, studentId, studentName, false, "代领授权与申领教师或学生不匹配");
        }
        if (grant.getRevokedAt() != null) {
            return new PickupResolution(mode, studentId, studentName, false, "代领授权已撤销，禁止实发");
        }
        Instant now = timeService.now();
        if (now.isBefore(grant.getStartAt())) {
            return new PickupResolution(mode, studentId, studentName, false, "代领授权尚未生效，禁止实发");
        }
        if (!now.isBefore(grant.getEndAt())) {
            return new PickupResolution(mode, studentId, studentName, false, "代领授权已过期，禁止实发");
        }
        if (student == null || !"STUDENT".equals(student.getUserType())) {
            return new PickupResolution(mode, studentId, studentName, false, "代领人不是学生账号，禁止实发");
        }
        if (!Integer.valueOf(1).equals(student.getEnabled())) {
            return new PickupResolution(mode, studentId, studentName, false, "代领学生账号已停用，禁止实发");
        }
        return new PickupResolution(mode, studentId, studentName, true, null);
    }

    private AssistantGrantVO toVO(MaterialAssistantGrant grant, Map<Long, SysUser> users, Instant now) {
        SysUser teacher = users.get(grant.getTeacherId());
        SysUser student = users.get(grant.getStudentId());
        String status = grant.getRevokedAt() != null ? "REVOKED"
                : !now.isBefore(grant.getEndAt()) ? "EXPIRED" : "ACTIVE";
        return new AssistantGrantVO(str(grant.getId()), str(grant.getTeacherId()),
                teacher == null ? null : teacher.getRealName(), str(grant.getStudentId()),
                student == null ? null : student.getRealName(),
                student == null ? null : student.getAccountName(),
                student == null ? null : student.getPersonNo(), grant.getStartAt(), grant.getEndAt(), status,
                grant.getRevokedAt(), str(grant.getRevokedBy()), grant.getReason(), grant.getCreateTime());
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(status) && !"EXPIRED".equals(status) && !"REVOKED".equals(status)) {
            throw badRequest("授权状态仅支持ACTIVE/EXPIRED/REVOKED");
        }
        return status;
    }

    private void requireEnabledStudent(SysUser student) {
        if (student == null || !"STUDENT".equals(student.getUserType())) {
            throw badRequest("被授权人必须是学生账号");
        }
        if (!Integer.valueOf(1).equals(student.getEnabled())) {
            throw badRequest("被授权学生账号必须处于启用状态");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String str(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.BAD_REQUEST.getCode(), message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ResultCode.CONFLICT.getCode(), message);
    }

    public record PickupResolution(
            PickupMode pickupMode,
            Long pickupUserId,
            String pickupUserName,
            boolean valid,
            String blockedReason
    ) {
    }
}
