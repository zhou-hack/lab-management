package com.labmanage.module.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 器材借用链集成测试（test profile，连接 lab_management_test）
 * <p>
 * Spring Boot 4 已移除 TestRestTemplate，改用 RestClient 直连 RANDOM_PORT 真实服务；
 * MySQL 3306 不可达或凭据无效时整类跳过并在结果中说明。
 * 覆盖：学生两级全流程、老师一级流程、时段冲突、限制表拦截、
 * 驳回意见必填、撤销状态约束、借出窗口校验、归还状态重算（RESERVED/IDLE）、
 * DAMAGED 转维护、无 token 401、无权限 403、Instant UTC 往返与枚举 name 落库。
 * 种子已收权（仅 ADMIN），学生/老师角色权限由 setup 临时注入以验证业务链路。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EquipmentBookingFlowTest {

    private static final String BOOKINGS = "/api/v1/equipment/bookings";

    /** 管理员（RBAC 种子内置） */
    private static final long ADMIN_ID = 1L;
    private static final long STUDENT_ID = 910000000000000101L;
    private static final long TEACHER_ID = 910000000000000102L;
    private static final long STUDENT_ROLE_BINDING_ID = 910000000000000121L;
    private static final long TEACHER_ROLE_BINDING_ID = 910000000000000122L;
    private static final long RESTRICTION_ID = 910000000000000123L;

    /**
     * 测试临时授权（收权决策 2026-10-14：种子仅 ADMIN，TEACHER/STUDENT 无绑定）。
     * setup 为角色 2/3 注入流程所需权限以验证业务链路，cleanup 物理删除；
     * admin_approve(159) 有意不授，供 403 用例验证权限拒绝。
     */
    private static final long TEST_GRANT_STUDENT_ID = 910000000000000301L;
    private static final long TEST_GRANT_TEACHER_ID = 910000000000000311L;
    private static final long[] STUDENT_GRANT_PERMS = {149, 155, 156, 157, 160, 161};
    private static final long[] TEACHER_GRANT_PERMS = {149, 155, 156, 157, 158, 160, 161};
    private static final long CAMPUS_ID = 910000000000000110L;
    private static final long BUILDING_ID = 910000000000000111L;
    private static final long LAB_ID = 910000000000000112L;
    private static final long CATEGORY_ID = 910000000000000113L;
    private static final long MODEL_ID = 910000000000000114L;

    // 每个用例独占一台设备，避免用例间时段/状态串扰
    private static final long ASSET_FLOW = 910000000000000201L;
    private static final long ASSET_TEACHER = 910000000000000202L;
    private static final long ASSET_CONFLICT = 910000000000000203L;
    private static final long ASSET_RESTRICTED = 910000000000000204L;
    private static final long ASSET_CANCEL = 910000000000000205L;
    private static final long ASSET_WINDOW = 910000000000000206L;
    private static final long ASSET_RECALC = 910000000000000207L;
    private static final long ASSET_DAMAGED = 910000000000000208L;
    private static final long[] ASSET_IDS = {
            ASSET_FLOW, ASSET_TEACHER, ASSET_CONFLICT, ASSET_RESTRICTED,
            ASSET_CANCEL, ASSET_WINDOW, ASSET_RECALC, ASSET_DAMAGED};

    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private RestClient restClient;

    private String studentToken;
    private String teacherToken;
    private String adminToken;

    @BeforeAll
    void prepare() throws Exception {
        checkDatabaseAvailable();
        seed();
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
        studentToken = login(STUDENT_ID);
        teacherToken = login(TEACHER_ID);
        adminToken = login(ADMIN_ID);
    }

    @AfterAll
    void cleanup() {
        cleanupTestRows();
    }

    // ------------------------------------------------------------------
    // 鉴权
    // ------------------------------------------------------------------

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        HttpResp resp = get(null, BOOKINGS);
        assertError(resp, 401, 40100);
    }

    @Test
    void shouldRejectRequestWithoutPermission() throws Exception {
        // 学生无 admin_approve：Sa-Token 注解拦截 → 403
        HttpResp resp = post(studentToken, BOOKINGS + "/1/admin_approve",
                Map.of("approved", true));
        assertError(resp, 403, 40300);
        // 学生请求全量范围：服务端强制拒绝
        resp = get(studentToken, BOOKINGS + "?scope=ALL");
        assertError(resp, 403, 40300);
        // 老师无 admin_approve
        resp = post(teacherToken, BOOKINGS + "/1/admin_approve", Map.of("approved", true));
        assertError(resp, 403, 40300);
    }

    // ------------------------------------------------------------------
    // 借用链
    // ------------------------------------------------------------------

    @Test
    void studentTwoLevelFullFlow() throws Exception {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(5, ChronoUnit.MINUTES);
        Instant end = start.plus(4, ChronoUnit.HOURS);
        Map<String, Object> body = bookingBody(ASSET_FLOW, start, end);
        body.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        body.put("idempotencyKey", "IT-FLOW-1");

        HttpResp resp = post(studentToken, BOOKINGS, body);
        assertOk(resp);
        JsonNode data = json(resp).path("data");
        assertEquals("PENDING_TEACHER", data.path("status").asText());
        assertTrue(data.path("documentNo").asText().startsWith("EQ"), "单据编号应为 EQ 前缀");
        // Instant UTC 往返：入参 Z 偏移 → DATETIME(3) UTC → 出参三位毫秒
        assertEquals(UTC_MILLIS.format(start), data.path("startAt").asText());
        String bookingId = data.path("id").asText();

        // 幂等：同键重放返回原单
        resp = post(studentToken, BOOKINGS, body);
        assertOk(resp);
        assertEquals(bookingId, json(resp).path("data").path("id").asText());

        // 落库：UTC 墙钟 + 枚举按 name 存取
        long id = Long.parseLong(bookingId);
        assertEquals(LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                jdbcTemplate.queryForObject("SELECT start_at FROM equipment_booking WHERE id = ?",
                        LocalDateTime.class, id));
        assertEquals("NORMAL",
                jdbcTemplate.queryForObject("SELECT value_level_snapshot FROM equipment_booking WHERE id = ?",
                        String.class, id));

        // 老师批 → PENDING_ADMIN；重复处理 → 409
        assertOk(post(teacherToken, BOOKINGS + "/" + bookingId + "/teacher_approve",
                Map.of("approved", true, "comment", "同意借用")));
        assertError(post(teacherToken, BOOKINGS + "/" + bookingId + "/teacher_approve",
                Map.of("approved", true)), 409, 40900);
        assertEquals("PENDING_ADMIN", detailStatus(studentToken, bookingId));

        // 管理员批 → APPROVED，资产 IDLE → RESERVED
        assertOk(post(adminToken, BOOKINGS + "/" + bookingId + "/admin_approve", Map.of("approved", true)));
        assertEquals("APPROVED", detailStatus(studentToken, bookingId));
        assertEquals("RESERVED", assetStatus(ASSET_FLOW));

        // 借出 → CHECKED_OUT，资产 IN_USE，流水落账
        assertOk(post(adminToken, BOOKINGS + "/" + bookingId + "/checkout", Map.of("note", "柜台发放")));
        assertEquals("CHECKED_OUT", detailStatus(studentToken, bookingId));
        assertEquals("IN_USE", assetStatus(ASSET_FLOW));
        assertEquals("CHECKED_OUT", usageField(id, "status"));
        assertError(post(adminToken, BOOKINGS + "/" + bookingId + "/checkout", Map.of()), 409, 40900);

        // 归还 → RETURNED，无其他生效单，资产回到 IDLE
        assertOk(post(adminToken, BOOKINGS + "/" + bookingId + "/return", Map.of("condition", "NORMAL")));
        assertEquals("RETURNED", detailStatus(studentToken, bookingId));
        assertEquals("IDLE", assetStatus(ASSET_FLOW));
        assertEquals("RETURNED", usageField(id, "status"));
    }

    @Test
    void teacherBookingGoesOneLevel() throws Exception {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        HttpResp resp = post(teacherToken, BOOKINGS, bookingBody(ASSET_TEACHER, start, end));
        assertOk(resp);
        JsonNode data = json(resp).path("data");
        assertEquals("PENDING_ADMIN", data.path("status").asText());
        assertTrue(data.path("approvalTeacherId").isNull(), "老师单不应指定审批老师");
        String bookingId = data.path("id").asText();

        assertOk(post(adminToken, BOOKINGS + "/" + bookingId + "/admin_approve", Map.of("approved", true)));
        assertEquals("APPROVED", detailStatus(teacherToken, bookingId));
        assertEquals("RESERVED", assetStatus(ASSET_TEACHER));
    }

    @Test
    void shouldRejectConflictingSecondBooking() throws Exception {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        // 第一单完整批准
        Map<String, Object> body = bookingBody(ASSET_CONFLICT, start, end);
        body.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        HttpResp resp = post(studentToken, BOOKINGS, body);
        assertOk(resp);
        String bookingId = json(resp).path("data").path("id").asText();
        approveToEnd(bookingId);

        // 同区间第二单 → 409
        resp = post(studentToken, BOOKINGS, body);
        assertError(resp, 409, 40900);
    }

    @Test
    void shouldRejectSubmissionUnderRestriction() throws Exception {
        jdbcTemplate.update("""
                        INSERT INTO user_booking_restriction (id, user_id, restriction_type, reason, start_at, end_at)
                        VALUES (?, ?, 'EQUIPMENT', '集成测试限制', ?, ?)
                        """,
                RESTRICTION_ID, STUDENT_ID,
                LocalDateTime.ofInstant(Instant.now().minus(1, ChronoUnit.HOURS), ZoneOffset.UTC),
                LocalDateTime.ofInstant(Instant.now().plus(12, ChronoUnit.HOURS), ZoneOffset.UTC));

        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        HttpResp resp = post(studentToken, BOOKINGS, bookingBody(ASSET_RESTRICTED, start, end));
        assertError(resp, 409, 40900);

        // 撤销限制后可正常提交
        jdbcTemplate.update("DELETE FROM user_booking_restriction WHERE id = ?", RESTRICTION_ID);
        Map<String, Object> body = bookingBody(ASSET_RESTRICTED, start, end);
        body.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        resp = post(studentToken, BOOKINGS, body);
        assertOk(resp);
        assertEquals("PENDING_TEACHER", json(resp).path("data").path("status").asText());
    }

    @Test
    void rejectRequiresComment() throws Exception {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(5, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Map<String, Object> body = bookingBody(ASSET_TEACHER, start, end);
        body.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        HttpResp resp = post(studentToken, BOOKINGS, body);
        assertOk(resp);
        String bookingId = json(resp).path("data").path("id").asText();

        // 驳回未填意见 → 400
        resp = post(teacherToken, BOOKINGS + "/" + bookingId + "/teacher_approve",
                Map.of("approved", false, "comment", ""));
        assertError(resp, 400, 40000);

        // 填意见驳回 → REJECTED，后续任务链式取消
        assertOk(post(teacherToken, BOOKINGS + "/" + bookingId + "/teacher_approve",
                Map.of("approved", false, "comment", "设备规格不符")));
        assertEquals("REJECTED", detailStatus(studentToken, bookingId));
        Long documentId = jdbcTemplate.queryForObject(
                "SELECT document_id FROM equipment_booking WHERE id = ?", Long.class, Long.parseLong(bookingId));
        assertEquals("REJECTED", jdbcTemplate.queryForObject(
                "SELECT status FROM approval_task WHERE document_id = ? AND stage_code = 'TEACHER_REVIEW'",
                String.class, documentId));
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT status FROM approval_task WHERE document_id = ? AND stage_code = 'ADMIN_REVIEW'",
                String.class, documentId));
    }

    @Test
    void cancelOnlyAllowedForApplicantInPending() throws Exception {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Map<String, Object> body = bookingBody(ASSET_CANCEL, start, end);
        body.put("approvalTeacherId", String.valueOf(TEACHER_ID));

        // 他人撤销 → 403
        HttpResp resp = post(studentToken, BOOKINGS, body);
        assertOk(resp);
        String firstId = json(resp).path("data").path("id").asText();
        resp = post(teacherToken, BOOKINGS + "/" + firstId + "/cancel", Map.of("reason", "老师代撤"));
        assertError(resp, 403, 40300);

        // 本人撤销 → CANCELLED，任务链式取消
        assertOk(post(studentToken, BOOKINGS + "/" + firstId + "/cancel", Map.of("reason", "不需要了")));
        assertEquals("CANCELLED", detailStatus(studentToken, firstId));

        // 已批准单不可撤销 → 409
        Instant secondStart = start.plus(3, ChronoUnit.HOURS);
        body = bookingBody(ASSET_CANCEL, secondStart, secondStart.plus(2, ChronoUnit.HOURS));
        body.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        resp = post(studentToken, BOOKINGS, body);
        assertOk(resp);
        String secondId = json(resp).path("data").path("id").asText();
        approveToEnd(secondId);
        resp = post(studentToken, BOOKINGS + "/" + secondId + "/cancel", Map.of("reason", "误提交"));
        assertError(resp, 409, 40900);
    }

    @Test
    void checkoutValidatesTimeWindow() throws Exception {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Map<String, Object> body = bookingBody(ASSET_WINDOW, start, end);
        body.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        HttpResp resp = post(studentToken, BOOKINGS, body);
        assertOk(resp);
        String bookingId = json(resp).path("data").path("id").asText();
        approveToEnd(bookingId);

        // 借用尚未开始 → 409
        resp = post(adminToken, BOOKINGS + "/" + bookingId + "/checkout", Map.of());
        assertError(resp, 409, 40900);
        assertEquals("APPROVED", detailStatus(studentToken, bookingId));
    }

    @Test
    void returnRecomputesAssetStatusWithRemainingBooking() throws Exception {
        Instant farStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.HOURS);
        Instant farEnd = farStart.plus(2, ChronoUnit.HOURS);
        Instant nearStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(5, ChronoUnit.MINUTES);
        Instant nearEnd = nearStart.plus(1, ChronoUnit.HOURS);

        // 先批准不重叠的远期单，再走近期单借还
        Map<String, Object> farBody = bookingBody(ASSET_RECALC, farStart, farEnd);
        farBody.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        HttpResp resp = post(studentToken, BOOKINGS, farBody);
        assertOk(resp);
        String farId = json(resp).path("data").path("id").asText();
        approveToEnd(farId);
        assertEquals("RESERVED", assetStatus(ASSET_RECALC));

        Map<String, Object> nearBody = bookingBody(ASSET_RECALC, nearStart, nearEnd);
        nearBody.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        resp = post(studentToken, BOOKINGS, nearBody);
        assertOk(resp);
        String nearId = json(resp).path("data").path("id").asText();
        approveToEnd(nearId);
        assertOk(post(adminToken, BOOKINGS + "/" + nearId + "/checkout", Map.of()));
        assertOk(post(adminToken, BOOKINGS + "/" + nearId + "/return", Map.of("condition", "NORMAL")));

        // 仍有生效 APPROVED 单 → 资产回到 RESERVED 而非 IDLE
        assertEquals("RETURNED", detailStatus(studentToken, nearId));
        assertEquals("RESERVED", assetStatus(ASSET_RECALC));
    }

    @Test
    void damagedReturnMovesAssetToMaintaining() throws Exception {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(5, ChronoUnit.MINUTES);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Map<String, Object> body = bookingBody(ASSET_DAMAGED, start, end);
        body.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        HttpResp resp = post(studentToken, BOOKINGS, body);
        assertOk(resp);
        String bookingId = json(resp).path("data").path("id").asText();
        approveToEnd(bookingId);
        assertOk(post(adminToken, BOOKINGS + "/" + bookingId + "/checkout", Map.of()));

        // 损坏归还未填说明 → 400
        resp = post(adminToken, BOOKINGS + "/" + bookingId + "/return", Map.of("condition", "DAMAGED"));
        assertError(resp, 400, 40000);

        assertOk(post(adminToken, BOOKINGS + "/" + bookingId + "/return",
                Map.of("condition", "DAMAGED", "note", "外壳开裂，屏幕无显示")));
        assertEquals("RETURNED", detailStatus(studentToken, bookingId));
        assertEquals("MAINTAINING", assetStatus(ASSET_DAMAGED));
        assertEquals("DAMAGED", usageField(Long.parseLong(bookingId), "return_condition"));
    }

    // ------------------------------------------------------------------
    // 基础设施
    // ------------------------------------------------------------------

    /**
     * 统一响应载体：状态码 + 响应体文本
     */
    private record HttpResp(int status, String body) {
    }

    /**
     * MySQL 3306 不可达或凭据无效时跳过整类测试
     */
    private void checkDatabaseAvailable() {
        String url = "jdbc:mysql://localhost:3306/lab_management_test"
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true";
        String user = envOrDefault("DB_USERNAME", "root");
        String password = envOrDefault("DB_PASSWORD", "root");
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            Assumptions.assumeTrue(conn.isValid(5), "MySQL 测试库连接不可用，跳过集成测试");
        } catch (Exception e) {
            Assumptions.abort("MySQL 3306 不可达或凭据无效，跳过集成测试：" + e.getMessage());
        }
    }

    private String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void seed() {
        cleanupTestRows();
        jdbcTemplate.update("""
                        INSERT INTO sys_user (id, user_type, real_name, phone, person_no, department_id, enabled)
                        VALUES (?, 'STUDENT', '集成测试学生', '13900000001', 'IT-STU-001', 1, 1)
                        """,
                STUDENT_ID);
        jdbcTemplate.update("""
                        INSERT INTO sys_user (id, user_type, real_name, phone, person_no, department_id, enabled)
                        VALUES (?, 'TEACHER', '集成测试老师', '13900000002', 'IT-TEA-001', 1, 1)
                        """,
                TEACHER_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id, enabled) VALUES (?, ?, 3, 1)",
                STUDENT_ROLE_BINDING_ID, STUDENT_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id, enabled) VALUES (?, ?, 2, 1)",
                TEACHER_ROLE_BINDING_ID, TEACHER_ID);
        grantTestPermissions(TEST_GRANT_STUDENT_ID, 3, STUDENT_GRANT_PERMS);
        grantTestPermissions(TEST_GRANT_TEACHER_ID, 2, TEACHER_GRANT_PERMS);
        jdbcTemplate.update(
                "INSERT INTO campus (id, campus_code, campus_name) VALUES (?, 'IT-CAMPUS-001', '集成测试校区')",
                CAMPUS_ID);
        jdbcTemplate.update(
                "INSERT INTO building (id, campus_id, building_code, building_name) VALUES (?, ?, 'IT-BLDG-001', '集成测试楼')",
                BUILDING_ID, CAMPUS_ID);
        jdbcTemplate.update("""
                        INSERT INTO lab (id, building_id, department_id, lab_code, lab_name, lab_type, floor_no, room_no, capacity)
                        VALUES (?, ?, 1, 'IT-LAB-001', '集成测试实验室', 'TEACHING', '1', '101', 30)
                        """,
                LAB_ID, BUILDING_ID);
        jdbcTemplate.update(
                "INSERT INTO equipment_category (id, category_code, category_name) VALUES (?, 'IT-CAT-001', '集成测试分类')",
                CATEGORY_ID);
        jdbcTemplate.update("""
                        INSERT INTO equipment_model (id, category_id, model_code, equipment_name, specification, unit_name)
                        VALUES (?, ?, 'IT-MODEL-001', '集成测试设备', '测试规格', '台')
                        """,
                MODEL_ID, CATEGORY_ID);
        for (long assetId : ASSET_IDS) {
            jdbcTemplate.update("""
                            INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, value_level, status, enabled)
                            VALUES (?, ?, ?, ?, 1, 'NORMAL', 'IDLE', 1)
                            """,
                    assetId, MODEL_ID, "IT-ASSET-" + assetId, LAB_ID);
        }
    }

    /**
     * 物理清理测试行（FK 顺序：usage → booking → 单据/任务/事件 → 主数据）；
     * @BeforeAll 与 @AfterAll 各执行一次，幂等
     */
    private void cleanupTestRows() {
        String assetIn = inClause(ASSET_IDS.length);
        // 先查 document_id（booking 删除前），再按 FK 从子到父删除
        List<Long> documentIds = jdbcTemplate.queryForList(
                "SELECT document_id FROM equipment_booking WHERE asset_id IN (" + assetIn + ")",
                Long.class, (Object[]) toObjectArray(ASSET_IDS));
        jdbcTemplate.update("DELETE FROM equipment_usage WHERE booking_id IN "
                + "(SELECT id FROM equipment_booking WHERE asset_id IN (" + assetIn + "))",
                (Object[]) toObjectArray(ASSET_IDS));
        jdbcTemplate.update("DELETE FROM equipment_booking WHERE asset_id IN (" + assetIn + ")",
                (Object[]) toObjectArray(ASSET_IDS));
        if (!documentIds.isEmpty()) {
            String documentIn = inClause(documentIds.size());
            Object[] documentArgs = documentIds.toArray();
            jdbcTemplate.update("DELETE FROM document_event WHERE document_id IN (" + documentIn + ")", documentArgs);
            jdbcTemplate.update("DELETE FROM approval_task WHERE document_id IN (" + documentIn + ")", documentArgs);
            jdbcTemplate.update("DELETE FROM biz_document WHERE id IN (" + documentIn + ")", documentArgs);
        }
        jdbcTemplate.update("DELETE FROM user_booking_restriction WHERE user_id IN (?, ?)",
                STUDENT_ID, TEACHER_ID);
        jdbcTemplate.update("DELETE FROM equipment_location_history WHERE asset_id IN (" + assetIn + ")",
                (Object[]) toObjectArray(ASSET_IDS));
        jdbcTemplate.update("DELETE FROM equipment_asset WHERE id IN (" + assetIn + ")",
                (Object[]) toObjectArray(ASSET_IDS));
        jdbcTemplate.update("DELETE FROM equipment_model WHERE id = ?", MODEL_ID);
        jdbcTemplate.update("DELETE FROM equipment_category WHERE id = ?", CATEGORY_ID);
        jdbcTemplate.update("DELETE FROM lab WHERE id = ?", LAB_ID);
        jdbcTemplate.update("DELETE FROM building WHERE id = ?", BUILDING_ID);
        jdbcTemplate.update("DELETE FROM campus WHERE id = ?", CAMPUS_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?)", STUDENT_ID, TEACHER_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", STUDENT_ID, TEACHER_ID);
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE id BETWEEN ? AND ?",
                TEST_GRANT_STUDENT_ID, TEST_GRANT_STUDENT_ID + STUDENT_GRANT_PERMS.length - 1);
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE id BETWEEN ? AND ?",
                TEST_GRANT_TEACHER_ID, TEST_GRANT_TEACHER_ID + TEACHER_GRANT_PERMS.length - 1);
    }

    /** 为指定角色注入测试临时权限（幂等：先清后插） */
    private void grantTestPermissions(long startId, long roleId, long[] permissionIds) {
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE id BETWEEN ? AND ?",
                startId, startId + permissionIds.length - 1);
        for (int i = 0; i < permissionIds.length; i++) {
            jdbcTemplate.update(
                    "INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES (?, ?, ?)",
                    startId + i, roleId, permissionIds[i]);
        }
    }

    private String login(long userId) throws Exception {
        HttpResp resp = post(null, "/test/login/" + userId, null);
        assertEquals(200, resp.status(), "测试登录失败：" + resp.body());
        return json(resp).path("token").asText();
    }

    private Map<String, Object> bookingBody(long assetId, Instant start, Instant end) {
        Map<String, Object> body = new HashMap<>();
        body.put("assetId", String.valueOf(assetId));
        body.put("purpose", "集成测试借用");
        body.put("startAt", DateTimeFormatter.ISO_INSTANT.format(start));
        body.put("endAt", DateTimeFormatter.ISO_INSTANT.format(end));
        return body;
    }

    /**
     * 学生单两级审批直达 APPROVED
     */
    private void approveToEnd(String bookingId) throws Exception {
        assertOk(post(teacherToken, BOOKINGS + "/" + bookingId + "/teacher_approve", Map.of("approved", true)));
        assertOk(post(adminToken, BOOKINGS + "/" + bookingId + "/admin_approve", Map.of("approved", true)));
    }

    private String detailStatus(String token, String bookingId) throws Exception {
        HttpResp resp = get(token, BOOKINGS + "/" + bookingId);
        assertOk(resp);
        return json(resp).path("data").path("status").asText();
    }

    private String assetStatus(long assetId) {
        return jdbcTemplate.queryForObject("SELECT status FROM equipment_asset WHERE id = ?",
                String.class, assetId);
    }

    private String usageField(long bookingId, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM equipment_usage WHERE booking_id = ?",
                String.class, bookingId);
    }

    private HttpResp get(String token, String path) {
        return exchange(HttpMethod.GET, path, token, null);
    }

    private HttpResp post(String token, String path, Object body) {
        return exchange(HttpMethod.POST, path, token, body);
    }

    /**
     * 走真实 HTTP 栈：4xx/5xx 不抛异常，原样收集状态码与响应体供断言
     */
    private HttpResp exchange(HttpMethod method, String path, String token, Object body) {
        RestClient.RequestBodySpec spec = restClient.method(method).uri(path)
                .headers(headers -> {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    if (token != null) {
                        headers.set("Authorization", "Bearer " + token);
                    }
                });
        if (body != null) {
            spec.body(body);
        }
        return spec.exchange((request, response) -> new HttpResp(
                response.getStatusCode().value(),
                readBody(response.getBody())), false);
    }

    private String readBody(InputStream body) throws IOException {
        if (body == null) {
            return "";
        }
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }

    private JsonNode json(HttpResp resp) throws Exception {
        return mapper.readTree(resp.body());
    }

    private void assertOk(HttpResp resp) throws Exception {
        assertEquals(200, resp.status(), "响应体：" + resp.body());
        assertEquals(0, json(resp).path("code").asInt(), "响应体：" + resp.body());
    }

    private void assertError(HttpResp resp, int httpStatus, int bizCode) throws Exception {
        assertEquals(httpStatus, resp.status(), "响应体：" + resp.body());
        assertEquals(bizCode, json(resp).path("code").asInt(), "响应体：" + resp.body());
        assertTrue(json(resp).path("data").isMissingNode() || json(resp).path("data").isNull(),
                "失败响应 data 必须为空");
    }

    private String inClause(int size) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < size; i++) {
            joiner.add("?");
        }
        return joiner.toString();
    }

    private Object[] toObjectArray(long[] values) {
        Object[] args = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            args[i] = values[i];
        }
        return args;
    }
}
