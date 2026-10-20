-- ============================================================================
-- 实验室设备管理系统 · 助理授权建表脚本
-- 依据 docs/数据库设计.md（评审稿 v1.0）4.6 节；归属 roles 模块（RBAC 实施补充）。
-- ----------------------------------------------------------------------------
-- 设计要点：
--   1. ASSISTANT 附加角色不持久化到 sys_user_role，由有效 assistant_grant 动态计算；
--      有效 = status=ACTIVE 且 start_at <= now < end_at（半开区间），EXPIRED 由定时任务回填；
--   2. 仅老师授权学生，最长一学年由服务端校验；同一学生可被多位老师授权，
--      一笔代办必须明确选用一条授权；
--   3. 范围表表达授权权力：MATERIAL 类型表示「物料领取」（领还一体，无目标，
--      action_code 固定 ISSUE_RETURN）；OFFERING/LAB 为课程/实验室级领还（恰一目标），
--      随教学模块落地后开放；生成列归一后 (grant_id, scope_type, scope_target_id, action_code) 唯一；
--   4. offering_id / lab_id 为跨模块引用，只建列与索引；offering_id 外键由
--      教学模块脚本补充，lab_id 预留（空间功能已下线）不建外键。
-- 执行前提：sys_rbac.sql 已执行（依赖 sys_user）；时区约定同 sys_rbac.sql。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. assistant_grant 助理授权表
-- ----------------------------------------------------------------------------
CREATE TABLE assistant_grant (
    id            BIGINT      NOT NULL                COMMENT '主键（雪花 ID）',
    teacher_id    BIGINT      NOT NULL                COMMENT '授权老师（→ sys_user.id）',
    student_id    BIGINT      NOT NULL                COMMENT '被授权学生（→ sys_user.id）',
    start_at      DATETIME(3) NOT NULL                COMMENT '授权开始（UTC，半开区间含）',
    end_at        DATETIME(3) NOT NULL                COMMENT '授权结束（UTC，半开区间不含）',
    status        VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/REVOKED/EXPIRED',
    revoked_at    DATETIME(3) NULL                    COMMENT '撤销时间（UTC）',
    revoked_by    BIGINT      NULL                    COMMENT '撤销人（→ sys_user.id）',
    reason        TEXT        NULL                    COMMENT '授权说明',
    create_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT     NULL                    COMMENT '创建人（→ sys_user.id，审计字段不建外键）',
    update_user_id BIGINT     NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    KEY idx_assistant_grant_student (student_id, status, end_at),
    KEY idx_assistant_grant_teacher (teacher_id, status),
    CONSTRAINT fk_assistant_grant_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user (id),
    CONSTRAINT fk_assistant_grant_student FOREIGN KEY (student_id) REFERENCES sys_user (id),
    CONSTRAINT fk_assistant_grant_revoker FOREIGN KEY (revoked_by) REFERENCES sys_user (id),
    CONSTRAINT chk_assistant_grant_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT chk_assistant_grant_time CHECK (end_at > start_at),
    CONSTRAINT chk_assistant_grant_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='助理授权表（ASSISTANT 动态计算依据）';

-- ----------------------------------------------------------------------------
-- 2. assistant_grant_scope 助理授权范围表
-- ----------------------------------------------------------------------------
CREATE TABLE assistant_grant_scope (
    id            BIGINT      NOT NULL                COMMENT '主键（雪花 ID）',
    grant_id      BIGINT      NOT NULL                COMMENT '授权（→ assistant_grant.id）',
    scope_type    VARCHAR(32) NOT NULL                COMMENT '范围类型：MATERIAL(物料领取，无目标)/OFFERING/LAB(预留)',
    offering_id   BIGINT      NULL                    COMMENT '目标开课（预留，→ course_offering.id，跨模块，外键由教学模块补充）',
    lab_id        BIGINT      NULL                    COMMENT '目标实验室（预留，空间功能已下线，不建外键）',
    action_code   VARCHAR(64) NOT NULL                COMMENT '动作：ISSUE_RETURN(MATERIAL 固定)/ISSUE/RETURN(预留)',
    scope_target_id BIGINT GENERATED ALWAYS AS (COALESCE(offering_id, lab_id, 0)) STORED COMMENT '范围目标归一键（生成列，勿写）',
    create_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) COMMENT '创建时间（UTC）',
    update_time   DATETIME(3) NOT NULL DEFAULT (UTC_TIMESTAMP(3)) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（UTC）',
    deleted       TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0正常 1删除',
    create_user_id BIGINT     NULL                    COMMENT '创建人（→ sys_user.id）',
    update_user_id BIGINT     NULL                    COMMENT '最后操作人（→ sys_user.id）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_assistant_grant_scope (grant_id, scope_type, scope_target_id, action_code),
    KEY idx_assistant_grant_scope_offering (offering_id),
    KEY idx_assistant_grant_scope_lab (lab_id),
    CONSTRAINT fk_assistant_grant_scope_grant FOREIGN KEY (grant_id) REFERENCES assistant_grant (id),
    CONSTRAINT chk_assistant_grant_scope_type CHECK (scope_type IN ('OFFERING', 'LAB', 'MATERIAL')),
    CONSTRAINT chk_assistant_grant_scope_target CHECK (
        (scope_type = 'MATERIAL' AND offering_id IS NULL AND lab_id IS NULL)
        OR (scope_type IN ('OFFERING', 'LAB') AND (offering_id IS NULL) <> (lab_id IS NULL))
    ),
    CONSTRAINT chk_assistant_grant_scope_action CHECK (action_code IN ('ISSUE', 'RETURN', 'ISSUE_RETURN')),
    CONSTRAINT chk_assistant_grant_scope_pair CHECK ((scope_type = 'MATERIAL') = (action_code = 'ISSUE_RETURN')),
    CONSTRAINT chk_assistant_grant_scope_deleted CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='助理授权范围表（恰一目标 + 动作）';
