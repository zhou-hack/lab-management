package com.labmanage.roles.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.PageResult;
import com.labmanage.common.utils.PageUtils;
import com.labmanage.roles.dto.RosterBatchCreateDTO;
import com.labmanage.roles.dto.RosterCreateDTO;
import com.labmanage.roles.dto.RosterEnabledDTO;
import com.labmanage.roles.dto.RosterPageQueryDTO;
import com.labmanage.roles.dto.RosterUpdateDTO;
import com.labmanage.roles.entity.SysDepartment;
import com.labmanage.roles.entity.SysPersonRoster;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysDepartmentMapper;
import com.labmanage.roles.mapper.SysPersonRosterMapper;
import com.labmanage.roles.mapper.SysUserMapper;
import com.labmanage.roles.service.SysRosterService;
import com.labmanage.roles.service.SysScopeService;
import com.labmanage.roles.vo.RosterExportRowVO;
import com.labmanage.roles.vo.RosterImportResultVO;
import com.labmanage.roles.vo.RosterPageItemVO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预录名单管理服务实现
 */
@Service
public class SysRosterServiceImpl implements SysRosterService {

    /** 导入 CSV 表头（首行必须完全一致） */
    private static final String IMPORT_HEADER = "学工号,身份类型,姓名,手机号,部门编码";

    private static final String EXPORT_HEADER = "学工号,身份类型,姓名,手机号,部门编码,部门名称,状态,创建时间";

    /** Excel 兼容的 UTF-8 BOM */
    private static final String CSV_BOM = "\uFEFF";

    private static final int IMPORT_MAX_BYTES = 2 * 1024 * 1024;

    private static final int IMPORT_MAX_ROWS = 1000;

    private static final int IMPORT_MAX_ERRORS = 50;

    private static final DateTimeFormatter SHANGHAI_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final SysPersonRosterMapper rosterMapper;

    private final SysDepartmentMapper departmentMapper;

    private final SysUserMapper userMapper;

    private final SysScopeService scopeService;

    private final Validator validator;

    public SysRosterServiceImpl(SysPersonRosterMapper rosterMapper, SysDepartmentMapper departmentMapper,
                                SysUserMapper userMapper, SysScopeService scopeService, Validator validator) {
        this.rosterMapper = rosterMapper;
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.scopeService = scopeService;
        this.validator = validator;
    }

    @Override
    public PageResult<RosterPageItemVO> page(RosterPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        Page<RosterPageItemVO> page = rosterMapper.selectRosterPage(PageUtils.toPage(query), query);
        return PageUtils.toPageResult(page);
    }

    @Override
    public void create(RosterCreateDTO dto) {
        requireEnabledDepartment(dto.departmentId());
        scopeService.requireDepartmentWrite(dto.departmentId());
        SysPersonRoster roster = new SysPersonRoster();
        roster.setPersonNo(dto.personNo());
        roster.setPersonType(dto.personType());
        roster.setRealName(dto.realName());
        roster.setPhone(dto.phone());
        roster.setDepartmentId(dto.departmentId());
        roster.setEnabled(1);
        try {
            rosterMapper.insert(roster);
        } catch (DuplicateKeyException e) {
            throw duplicateConflict(e, null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createBatch(RosterBatchCreateDTO dto) {
        requireEnabledDepartment(dto.departmentId());
        scopeService.requireDepartmentWrite(dto.departmentId());
        int row = 0;
        for (RosterBatchCreateDTO.Item item : dto.items()) {
            row++;
            SysPersonRoster roster = new SysPersonRoster();
            roster.setPersonNo(item.personNo());
            roster.setPersonType(dto.personType());
            roster.setRealName(item.realName());
            roster.setPhone(item.phone());
            roster.setDepartmentId(dto.departmentId());
            roster.setEnabled(1);
            try {
                rosterMapper.insert(roster);
            } catch (DuplicateKeyException e) {
                throw duplicateConflict(e, row);
            }
        }
        return dto.items().size();
    }

    @Override
    public void update(Long id, RosterUpdateDTO dto) {
        SysPersonRoster roster = requireRoster(id);
        scopeService.requireDepartmentWrite(roster.getDepartmentId());
        requireNotMatched(roster, "编辑");
        requireEnabledDepartment(dto.departmentId());
        // 迁入部门也须在操作者写范围内
        scopeService.requireDepartmentWrite(dto.departmentId());
        roster.setRealName(dto.realName());
        roster.setPhone(dto.phone());
        roster.setDepartmentId(dto.departmentId());
        try {
            rosterMapper.updateById(roster);
        } catch (DuplicateKeyException e) {
            throw duplicateConflict(e, null);
        }
    }

    @Override
    public void updateEnabled(Long id, RosterEnabledDTO dto) {
        SysPersonRoster roster = requireRoster(id);
        scopeService.requireDepartmentWrite(roster.getDepartmentId());
        if (Integer.valueOf(0).equals(dto.enabled())) {
            requireNotMatched(roster, "停用");
        }
        roster.setEnabled(dto.enabled());
        rosterMapper.updateById(roster);
    }

    @Override
    public byte[] exportCsv(RosterPageQueryDTO query) {
        if (query.getKeyword() != null) {
            query.setKeyword(query.getKeyword().trim());
        }
        List<RosterExportRowVO> rows = rosterMapper.selectRosterExport(query);
        StringBuilder csv = new StringBuilder(CSV_BOM).append(EXPORT_HEADER).append("\r\n");
        for (RosterExportRowVO row : rows) {
            csv.append(csvCell(row.personNo())).append(',')
                    .append(csvCell(personTypeName(row.personType()))).append(',')
                    .append(csvCell(row.realName())).append(',')
                    .append(csvCell(row.phone())).append(',')
                    .append(csvCell(row.departmentCode())).append(',')
                    .append(csvCell(row.departmentName())).append(',')
                    .append(Integer.valueOf(1).equals(row.enabled()) ? "启用" : "停用").append(',')
                    .append(row.createTime() == null ? "" : SHANGHAI_TIME.format(row.createTime()))
                    .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] importTemplate() {
        String csv = CSV_BOM + IMPORT_HEADER + "\r\n"
                + "20240001,STUDENT,张三,13800000001,ASSET_OFFICE\r\n"
                + "T2024001,TEACHER,李四,13900000002,ASSET_OFFICE\r\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public RosterImportResultVO importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40000, "请选择要导入的 CSV 文件");
        }
        if (file.getSize() > IMPORT_MAX_BYTES) {
            throw new BusinessException(40000, "文件大小不能超过 2MB");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取导入文件失败", e);
        }
        // 非 UTF-8（如 GBK）解码会产生 U+FFFD 替换字符，乱码入库前整体拒绝
        if (content.indexOf('\uFFFD') >= 0) {
            throw new BusinessException(40000, "文件编码无效，请使用 UTF-8 编码的 CSV");
        }
        if (content.startsWith(CSV_BOM)) {
            content = content.substring(1);
        }
        String[] lines = content.split("\r\n|\r|\n", -1);
        if (lines.length == 0 || !IMPORT_HEADER.equals(lines[0].trim())) {
            throw new BusinessException(40000, "表头不正确，请使用下载的导入模板");
        }
        int total = 0;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i] != null && !lines[i].trim().isEmpty()) {
                total++;
            }
        }
        // 部分成功语义下成功行即时入库，行数超限必须在任何写入前整体拒绝
        if (total > IMPORT_MAX_ROWS) {
            throw new BusinessException(40000, "单次最多导入 " + IMPORT_MAX_ROWS + " 行数据");
        }
        int success = 0;
        List<RosterImportResultVO.RowError> errors = new ArrayList<>();
        Map<String, SysDepartment> departmentCache = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            int rowIndex = i + 1;
            String[] columns = line.split(",", -1);
            String personNo = columns.length > 0 ? columns[0].trim() : "";
            try {
                importRow(columns, departmentCache);
                success++;
            } catch (BusinessException e) {
                if (errors.size() < IMPORT_MAX_ERRORS) {
                    errors.add(new RosterImportResultVO.RowError(rowIndex, personNo, e.getMessage()));
                }
            }
        }
        return new RosterImportResultVO(total, success, total - success, errors);
    }

    /** 逐行导入：复用单条录入的部门校验、数据范围与冲突处理，失败抛 BusinessException 由调用方收集 */
    private void importRow(String[] columns, Map<String, SysDepartment> departmentCache) {
        if (columns.length != 5) {
            throw new BusinessException(40000, "列数不正确（需 5 列：学工号,身份类型,姓名,手机号,部门编码）");
        }
        String departmentCode = columns[4].trim();
        SysDepartment department = departmentCache.computeIfAbsent(departmentCode, this::findDepartmentByCode);
        if (department == null) {
            throw new BusinessException(40000, "部门编码不存在：" + departmentCode);
        }
        RosterCreateDTO dto = new RosterCreateDTO(
                columns[0].trim(), columns[1].trim(), columns[2].trim(), columns[3].trim(), department.getId());
        Set<ConstraintViolation<RosterCreateDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new BusinessException(40000, violations.iterator().next().getMessage());
        }
        create(dto);
    }

    private SysDepartment findDepartmentByCode(String departmentCode) {
        return departmentMapper.selectOne(Wrappers.<SysDepartment>lambdaQuery()
                .eq(SysDepartment::getDepartmentCode, departmentCode));
    }

    private String personTypeName(String personType) {
        return switch (personType) {
            case "STUDENT" -> "学生";
            case "TEACHER" -> "老师";
            default -> personType;
        };
    }

    private String csvCell(String value) {
        if (value == null) {
            return "";
        }
        // 防 Excel 公式注入：以 = + - @ 开头的值前缀单引号，Excel 按文本展示且不显示该引号
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private SysPersonRoster requireRoster(Long id) {
        SysPersonRoster roster = rosterMapper.selectById(id);
        if (roster == null) {
            throw new BusinessException(40400, "名单记录不存在");
        }
        return roster;
    }

    /** 已建档名单受保护：编辑/停用会破坏已建账号的溯源关系 */
    private void requireNotMatched(SysPersonRoster roster, String action) {
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRosterId, roster.getId()));
        if (count != null && count > 0) {
            throw new BusinessException(40900, "该名单已完成首登建档，不可" + action);
        }
    }

    private void requireEnabledDepartment(Long departmentId) {
        SysDepartment department = departmentMapper.selectById(departmentId);
        if (department == null || !Integer.valueOf(1).equals(department.getEnabled())) {
            throw new BusinessException(40000, "所属部门不存在或已停用");
        }
    }

    private BusinessException duplicateConflict(DuplicateKeyException e, Integer row) {
        String prefix = row == null ? "" : "第 " + row + " 行：";
        String message = String.valueOf(e.getMessage());
        if (message.contains("uk_sys_person_roster_no")) {
            return new BusinessException(40900, prefix + "学工号已存在");
        }
        if (message.contains("uk_sys_person_roster_phone")) {
            return new BusinessException(40900, prefix + "手机号已存在");
        }
        return new BusinessException(40900, prefix + "名单记录已存在");
    }
}
