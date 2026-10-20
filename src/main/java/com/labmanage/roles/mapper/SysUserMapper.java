package com.labmanage.roles.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.roles.dto.UserPageQueryDTO;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.vo.AdminOptionVO;
import com.labmanage.roles.vo.UserOptionVO;
import com.labmanage.roles.vo.UserPageItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 用户分页列表（join 部门名；锁定态由 locked_until 与 UTC 当前时间比较得出；
     * ID 列 CAST 为字符，避免 JavaScript 精度损失）。
     */
    @Select("""
            <script>
            SELECT CAST(u.id AS CHAR) AS id,
                   u.user_type AS userType,
                   u.real_name AS realName,
                   u.account_name AS accountName,
                   u.phone AS phone,
                   u.person_no AS personNo,
                   CAST(u.department_id AS CHAR) AS departmentId,
                   d.department_name AS departmentName,
                   u.enabled AS enabled,
                   (u.locked_until IS NOT NULL AND u.locked_until > UTC_TIMESTAMP(3)) AS locked,
                   EXISTS(SELECT 1 FROM sys_user_role ur
                          WHERE ur.user_id = u.id AND ur.role_id = 1 AND ur.deleted = 0 AND ur.enabled = 1) AS adminBound,
                   u.create_time AS createTime
            FROM sys_user u
            LEFT JOIN sys_department d ON d.id = u.department_id AND d.deleted = 0
            WHERE u.deleted = 0
            <if test="query.keyword != null and query.keyword != ''">
                AND (u.real_name LIKE CONCAT('%', #{query.keyword}, '%')
                     OR u.person_no LIKE CONCAT('%', #{query.keyword}, '%')
                     OR u.phone LIKE CONCAT('%', #{query.keyword}, '%')
                     OR u.account_name LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
            <if test="query.userType != null and query.userType != ''">
                AND u.user_type = #{query.userType}
            </if>
            <if test="query.enabled != null">
                AND u.enabled = #{query.enabled}
            </if>
            ORDER BY u.create_time DESC, u.id DESC
            </script>
            """)
    Page<UserPageItemVO> selectUserPage(Page<UserPageItemVO> page, @Param("query") UserPageQueryDTO query);

    /** 可授权管理员下拉（启用中的 ADMIN 账号，关键字模糊，上限 50） */
    @Select("""
            <script>
            SELECT CAST(u.id AS CHAR) AS userId,
                   u.real_name AS realName,
                   u.account_name AS accountName
            FROM sys_user u
            WHERE u.deleted = 0 AND u.enabled = 1 AND u.user_type = 'ADMIN'
            <if test="keyword != null and keyword != ''">
                AND (u.real_name LIKE CONCAT('%', #{keyword}, '%')
                     OR u.account_name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY u.id ASC
            LIMIT 50
            </script>
            """)
    List<AdminOptionVO> selectAdminOptions(@Param("keyword") String keyword);

    /** 用户下拉（启用中全部类型，关键字模糊姓名/账号/学工号，上限 50；预约限制等场景用） */
    @Select("""
            <script>
            SELECT CAST(u.id AS CHAR) AS userId,
                   u.real_name AS realName,
                   u.account_name AS accountName,
                   u.person_no AS personNo,
                   u.user_type AS userType
            FROM sys_user u
            WHERE u.deleted = 0 AND u.enabled = 1
            <if test="keyword != null and keyword != ''">
                AND (u.real_name LIKE CONCAT('%', #{keyword}, '%')
                     OR u.account_name LIKE CONCAT('%', #{keyword}, '%')
                     OR u.person_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY u.id ASC
            LIMIT 50
            </script>
            """)
    List<UserOptionVO> selectUserOptions(@Param("keyword") String keyword);
}
