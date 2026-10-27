package com.labmanage.module.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备使用申请链路集成测试（test profile，连接 lab_management_test）
 * <p>
 * 覆盖三权级流程：学生/教师申请、时段双校验（提交只看 APPROVED/CHECKED_OUT
 * 占用、待审单不占时段；同槽并存单最后批准者冲突被拒）、
 * 教师初审（通过即生效、驳回意见必填）、管理员超 24h 代批、
 * 借出发放（设备置 IN_USE、重复借出拒绝、借出中禁删）、
 * 归还（完好恢复空闲、损坏必填备注转维护中）、越权 403、撤销本人申请。
 * 测试数据用独立 91xxx ID 段自造，不依赖业务种子脚本执行状态。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingFlowTest {

    private static final String AUTH = "/api/v1/auth";
    private static final String BOOKINGS = "/api/v1/equipment/bookings";
    private static final String ASSETS = "/api/v1/equipment/assets";

    /** 集成测试 91xxx ID 段 */
    private static final long CATEGORY_ID = 910000000000000301L;
    private static final long MODEL_ID = 910000000000000201L;
    private static final long ASSET_NORMAL = 910000000000000101L;
    private static final long ASSET_OTHER = 910000000000000102L;

    /** 演示账号（sql/add_equipment_booking_permission.sql 预置，id=10/11） */
    private static final long TEACHER_ID = 10L;
    private static final long STUDENT_ID = 11L;

    /** admin123 的 BCrypt 哈希（与种子一致） */
    private static final String PASSWORD_HASH =
            "$2a$10$XV8quUrmu3w9j86DSh5kY.cXgNZLtFBFqBT858uAz99zke4FZee.y";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private RestClient restClient;

    private String adminToken;
    private String teacherToken;
    private String studentToken;

    /** 流程中产生的申请单 ID（用例间传递） */
    private String normalBookingId;
    private String otherBookingId;

    @BeforeAll
    void prepare() {
        checkDatabaseAvailable();
        seed();
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    void cleanup() {
        cleanupTestDocuments();
        jdbcTemplate.update("DELETE FROM equipment_asset WHERE id BETWEEN 910000000000000101 AND 910000000000000199");
        jdbcTemplate.update("DELETE FROM equipment_model WHERE id BETWEEN 910000000000000201 AND 910000000000000299");
        jdbcTemplate.update("DELETE FROM equipment_category WHERE id BETWEEN 910000000000000301 AND 910000000000000399");
        jdbcTemplate.update(
                "UPDATE sys_user SET failed_login_count = 0, locked_until = NULL WHERE id IN (1, ?, ?)",
                TEACHER_ID, STUDENT_ID);
    }

    // ------------------------------------------------------------------
    // 登录与越权
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void shouldLoginAllRoles() throws Exception {
        adminToken = login("admin");
        teacherToken = login("teacher1");
        studentToken = login("student1");
        assertNotNull(adminToken);
        assertNotNull(teacherToken);
        assertNotNull(studentToken);
    }

    @Test
    @Order(2)
    void shouldRejectWithoutToken() throws Exception {
        assertError(get(null, BOOKINGS + "/my"), 401, 40100);
    }

    @Test
    @Order(3)
    void shouldRejectCrossRoleAccess() throws Exception {
        // 学生无终审/借出/全量查询权限
        assertError(get(studentToken, BOOKINGS), 403, 40300);
        assertError(get(studentToken, BOOKINGS + "/teacher_pending"), 403, 40300);
        assertError(put(studentToken, BOOKINGS + "/" + 1 + "/admin_review", Map.of("action", "approve")),
                403, 40300);
        // 教师无借出/全量查询权限
        assertError(get(teacherToken, BOOKINGS), 403, 40300);
        assertError(put(teacherToken, BOOKINGS + "/" + 1 + "/checkout", null), 403, 40300);
    }

    // ------------------------------------------------------------------
    // 申请提交与校验
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void shouldApplyBooking() throws Exception {
        // 学生申请设备（指定审批教师）→ 待教师初审
        assertOk(post(studentToken, BOOKINGS, applyBody(ASSET_NORMAL)));
        normalBookingId = findMyBookingIdByAsset(String.valueOf(ASSET_NORMAL));
        assertEquals("PENDING_TEACHER", bookingStatus(normalBookingId));

        // 错开时段再申请一台设备 → 待教师初审（供后续损坏归还用例）
        assertOk(post(studentToken, BOOKINGS, applyBodyOffset(ASSET_OTHER, 3600 * 24)));
        otherBookingId = findMyBookingIdByAsset(String.valueOf(ASSET_OTHER));
        assertEquals("PENDING_TEACHER", bookingStatus(otherBookingId));
    }

    @Test
    @Order(5)
    void shouldRejectInvalidApply() throws Exception {
        // 注：申请期不校验时段互斥（学生/教师可同时段提交申请），占用在审批通过/借出登记时校验
        // 学生申请必须指定审批教师
        Map<String, Object> body = applyBody(ASSET_NORMAL);
        body.remove("approvalTeacherId");
        assertError(post(studentToken, BOOKINGS, body), 400, 40000);
        // 结束时间必须晚于开始时间
        Map<String, Object> bad = applyBody(ASSET_NORMAL);
        bad.put("startAt", Instant.now().plusSeconds(7200).toString());
        bad.put("endAt", Instant.now().plusSeconds(3600).toString());
        assertError(post(studentToken, BOOKINGS, bad), 400, 40000);
        // 管理员不可提交使用申请（apply 权限仅授学生/教师，控制器层即拦截）
        assertError(post(adminToken, BOOKINGS, applyBody(ASSET_NORMAL)), 403, 40300);
    }

    @Test
    @Order(6)
    void shouldTeacherReviewPassToAdminReview() throws Exception {
        // 待审单不占时段：同设备同槽可再提交一张并存待审单
        assertOk(post(studentToken, BOOKINGS, applyBody(ASSET_NORMAL)));
        String duplicateId = findMyBookingIdByAsset(String.valueOf(ASSET_NORMAL));

        // 教师待办含三张学生单（普通/错时段/同槽并存）
        JsonNode pending = json(get(teacherToken, BOOKINGS + "/teacher_pending")).path("data").path("records");
        assertTrue(pending.size() >= 3, "教师待办应至少 3 条，实际：" + pending);

        // 初审通过 → 转管理员终审（不再直接生效）
        assertOk(put(teacherToken, BOOKINGS + "/" + normalBookingId + "/teacher_review",
                Map.of("action", "approve")));
        assertEquals("PENDING_ADMIN", bookingStatus(normalBookingId));

        // 错时段单初审通过 → PENDING_ADMIN
        assertOk(put(teacherToken, BOOKINGS + "/" + otherBookingId + "/teacher_review",
                Map.of("action", "approve")));
        assertEquals("PENDING_ADMIN", bookingStatus(otherBookingId));

        // 教师本人申请：免教师初审，提交即待管理员终审；同时段申请不被占用拦截（占用在终审校验）
        assertOk(post(teacherToken, BOOKINGS, applyBody(ASSET_NORMAL)));
        String teacherBookingId = findMyBookingIdByAssetFor(teacherToken, String.valueOf(ASSET_NORMAL));
        assertEquals("PENDING_ADMIN", bookingStatus(teacherBookingId));
        // 教师撤销清理本人同槽单
        assertOk(put(teacherToken, BOOKINGS + "/" + teacherBookingId + "/cancel", Map.of("reason", "测试清理")));

        // 申请期放开互斥：已有待终审单的同槽新申请仍可提交，随后撤销清理
        assertOk(post(studentToken, BOOKINGS, applyBody(ASSET_NORMAL)));
        String extraId = findMyBookingIdByAsset(String.valueOf(ASSET_NORMAL));
        assertOk(put(studentToken, BOOKINGS + "/" + extraId + "/cancel", Map.of("reason", "测试清理")));

        // 驳回必须填写意见；带意见驳回并存单 → REJECTED
        assertError(put(teacherToken, BOOKINGS + "/" + duplicateId + "/teacher_review",
                Map.of("action", "reject")), 400, 40000);
        assertOk(put(teacherToken, BOOKINGS + "/" + duplicateId + "/teacher_review",
                Map.of("action", "reject", "comment", "初审不通过")));
        assertEquals("REJECTED", bookingStatus(duplicateId));

        // 重复初审已流转单 → 冲突
        assertError(put(teacherToken, BOOKINGS + "/" + normalBookingId + "/teacher_review",
                Map.of("action", "approve")), 409, 40900);
    }

    @Test
    @Order(7)
    void shouldAdminFinalReview() throws Exception {
        // 管理员终审（必经）：学生单第二级 → APPROVED
        assertOk(put(adminToken, BOOKINGS + "/" + normalBookingId + "/admin_review",
                Map.of("action", "approve")));
        assertEquals("APPROVED", bookingStatus(normalBookingId));
        assertOk(put(adminToken, BOOKINGS + "/" + otherBookingId + "/admin_review",
                Map.of("action", "approve")));
        assertEquals("APPROVED", bookingStatus(otherBookingId));

        // 待教师初审单不可终审（须先经教师初审）
        Map<String, Object> body = applyBodyOffset(ASSET_OTHER, 3600 * 48);
        assertOk(post(studentToken, BOOKINGS, body));
        String freshId = findMyBookingIdByAsset(String.valueOf(ASSET_OTHER));
        assertEquals("PENDING_TEACHER", bookingStatus(freshId));
        assertError(put(adminToken, BOOKINGS + "/" + freshId + "/admin_review",
                Map.of("action", "approve")), 409, 40900);
        assertOk(put(studentToken, BOOKINGS + "/" + freshId + "/cancel", Map.of("reason", "测试清理")));

        // 终审冲突：与已生效 APPROVED 单同槽的并存单，终审时被拒且保持 PENDING_ADMIN
        assertOk(post(studentToken, BOOKINGS, applyBody(ASSET_NORMAL)));
        String conflictId = findMyBookingIdByAsset(String.valueOf(ASSET_NORMAL));
        assertOk(put(teacherToken, BOOKINGS + "/" + conflictId + "/teacher_review",
                Map.of("action", "approve")));
        assertEquals("PENDING_ADMIN", bookingStatus(conflictId));
        assertError(put(adminToken, BOOKINGS + "/" + conflictId + "/admin_review",
                Map.of("action", "approve")), 409, 40900);
        assertEquals("PENDING_ADMIN", bookingStatus(conflictId));
        assertOk(put(studentToken, BOOKINGS + "/" + conflictId + "/cancel", Map.of("reason", "测试清理")));

        // 终审驳回必填意见
        assertOk(post(studentToken, BOOKINGS, applyBody(ASSET_NORMAL)));
        String rejectId = findMyBookingIdByAsset(String.valueOf(ASSET_NORMAL));
        assertOk(put(teacherToken, BOOKINGS + "/" + rejectId + "/teacher_review",
                Map.of("action", "approve")));
        assertError(put(adminToken, BOOKINGS + "/" + rejectId + "/admin_review",
                Map.of("action", "reject")), 400, 40000);
        assertOk(put(adminToken, BOOKINGS + "/" + rejectId + "/admin_review",
                Map.of("action", "reject", "comment", "终审不通过")));
        assertEquals("REJECTED", bookingStatus(rejectId));
    }

    @Test
    @Order(8)
    void shouldOverdueAdvanceAndFreeze() throws Exception {
        // 借出发放 → CHECKED_OUT / IN_USE / usage CHECKED_OUT
        assertOk(put(adminToken, BOOKINGS + "/" + normalBookingId + "/checkout", null));
        assertEquals("CHECKED_OUT", bookingStatus(normalBookingId));
        assertEquals("IN_USE", assetStatus(ASSET_NORMAL), "借出后设备应为 IN_USE");
        assertEquals("CHECKED_OUT", usageStatus(normalBookingId));

        // 重复借出 → 冲突；借出中设备禁止删除、不可再申请
        assertError(put(adminToken, BOOKINGS + "/" + normalBookingId + "/checkout", null), 409, 40900);
        assertError(delete(adminToken, ASSETS + "/" + ASSET_NORMAL), 409, 40900);
        assertError(post(studentToken, BOOKINGS, applyBody(ASSET_NORMAL)), 409, 40900);

        // 模拟到期未还：整体回填时段为已过期（start_at -2d / end_at -1d，满足 start_at < end_at 约束）
        jdbcTemplate.update(
                "UPDATE equipment_booking SET start_at = UTC_TIMESTAMP(3) - INTERVAL 2 DAY,"
                        + " end_at = UTC_TIMESTAMP(3) - INTERVAL 1 DAY WHERE id = ?",
                Long.parseLong(normalBookingId));

        // 列表查询触发惰性推进 → 置 OVERDUE 并冻结申请人 7 天
        assertOk(get(studentToken, BOOKINGS + "/my"));
        assertEquals("OVERDUE", bookingStatus(normalBookingId));
        Integer freezeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_booking_restriction WHERE source_document_id ="
                        + " (SELECT document_id FROM equipment_booking WHERE id = ?)"
                        + " AND restriction_type = 'ALL' AND deleted = 0",
                Integer.class, Long.parseLong(normalBookingId));
        assertEquals(1, freezeCount, "逾期应写入一条冻结记录");
        Long freezeEndMillis = jdbcTemplate.queryForObject(
                "SELECT UNIX_TIMESTAMP(end_at) * 1000 FROM user_booking_restriction WHERE source_document_id ="
                        + " (SELECT document_id FROM equipment_booking WHERE id = ?)"
                        + " AND restriction_type = 'ALL' AND deleted = 0",
                Long.class, Long.parseLong(normalBookingId));
        assertNotNull(freezeEndMillis);
        assertTrue(freezeEndMillis > Instant.now().plusSeconds(6L * 24 * 3600).toEpochMilli(),
                "冻结应约 7 天，实际毫秒：" + freezeEndMillis);

        // 幂等：重复触发不重复冻结、不延长
        assertOk(get(studentToken, BOOKINGS + "/my"));
        Integer freezeCount2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_booking_restriction WHERE source_document_id ="
                        + " (SELECT document_id FROM equipment_booking WHERE id = ?)"
                        + " AND restriction_type = 'ALL' AND deleted = 0",
                Integer.class, Long.parseLong(normalBookingId));
        assertEquals(1, freezeCount2, "重复推进不得重复冻结");
        Long freezeEndMillis2 = jdbcTemplate.queryForObject(
                "SELECT UNIX_TIMESTAMP(end_at) * 1000 FROM user_booking_restriction WHERE source_document_id ="
                        + " (SELECT document_id FROM equipment_booking WHERE id = ?)"
                        + " AND restriction_type = 'ALL' AND deleted = 0",
                Long.class, Long.parseLong(normalBookingId));
        assertEquals(freezeEndMillis, freezeEndMillis2, "冻结 end_at 不得随重跑延长");

        // 冻结期学生提交新申请 → 409
        assertError(post(studentToken, BOOKINGS, applyBodyOffset(ASSET_OTHER, 3600 * 24)), 409, 40900);

        // OVERDUE 单不可借出
        assertError(put(adminToken, BOOKINGS + "/" + normalBookingId + "/checkout", null), 409, 40900);

        // 逾期归还补登记（完好）→ RETURNED / 设备 IDLE
        assertOk(put(adminToken, BOOKINGS + "/" + normalBookingId + "/return",
                Map.of("condition", "NORMAL")));
        assertEquals("RETURNED", bookingStatus(normalBookingId));
        assertEquals("IDLE", assetStatus(ASSET_NORMAL));
        assertEquals("RETURNED", usageStatus(normalBookingId));
    }

    @Test
    @Order(9)
    void shouldCheckoutOtherAndReturnDamaged() throws Exception {
        // 第二台设备借出后损坏归还：备注必填 → 校验 400；带备注后设备转 MAINTAINING
        assertOk(put(adminToken, BOOKINGS + "/" + otherBookingId + "/checkout", null));
        assertError(put(adminToken, BOOKINGS + "/" + otherBookingId + "/return",
                Map.of("condition", "DAMAGED")), 400, 40000);
        assertOk(put(adminToken, BOOKINGS + "/" + otherBookingId + "/return",
                Map.of("condition", "DAMAGED", "note", "归还时屏幕碎裂")));
        assertEquals("RETURNED", bookingStatus(otherBookingId));
        assertEquals("MAINTAINING", assetStatus(ASSET_OTHER), "损坏归还后设备应为 MAINTAINING");

        // 已结束单不可再次归还
        assertError(put(adminToken, BOOKINGS + "/" + otherBookingId + "/return",
                Map.of("condition", "NORMAL")), 409, 40900);
    }

    @Test
    @Order(11)
    void shouldKeepDocumentEvents() {
        // 单据事件留痕：每张单至少有 SUBMIT 事件
        List<Long> documentIds = jdbcTemplate.queryForList(
                "SELECT document_id FROM equipment_booking WHERE applicant_id = ?",
                Long.class, STUDENT_ID);
        assertTrue(documentIds.size() >= 4, "学生申请单应至少 4 张，实际：" + documentIds.size());
        for (Long documentId : documentIds) {
            Integer events = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_event WHERE document_id = ?",
                    Integer.class, documentId);
            assertTrue(events != null && events >= 1,
                    "单据 " + documentId + " 应有事件留痕，实际：" + events);
        }
    }

    // ------------------------------------------------------------------
    // 支撑
    // ------------------------------------------------------------------

    /** 申请体：默认时段 [now-1h, now+3h)（允许过去开始，便于立即借出） */
    private Map<String, Object> applyBody(long assetId) {
        Map<String, Object> body = new HashMap<>();
        body.put("assetId", String.valueOf(assetId));
        body.put("purpose", "集成测试申请");
        body.put("startAt", Instant.now().minusSeconds(3600).toString());
        body.put("endAt", Instant.now().plusSeconds(3 * 3600).toString());
        body.put("approvalTeacherId", String.valueOf(TEACHER_ID));
        return body;
    }

    /** 申请体：时段整体后移 offsetSeconds（错开冲突） */
    private Map<String, Object> applyBodyOffset(long assetId, long offsetSeconds) {
        Map<String, Object> body = applyBody(assetId);
        body.put("startAt", Instant.now().plusSeconds(offsetSeconds).toString());
        body.put("endAt", Instant.now().plusSeconds(offsetSeconds + 3 * 3600).toString());
        return body;
    }

    /** 从学生我的申请中按设备定位申请单 ID；index 0 最新（多张时取第 index 张） */
    private String findMyBookingIdByAsset(String assetId, int index) throws Exception {
        JsonNode records = json(get(studentToken, BOOKINGS + "/my")).path("data").path("records");
        int hit = 0;
        for (JsonNode row : records) {
            if (assetId.equals(row.path("assetId").asText())) {
                if (hit == index) {
                    return row.path("id").asText();
                }
                hit++;
            }
        }
        throw new AssertionError("我的申请中未找到设备 " + assetId + " 的第 " + index + " 张单");
    }

    private String findMyBookingIdByAsset(String assetId) throws Exception {
        return findMyBookingIdByAsset(assetId, 0);
    }

    /** 按指定用户本人申请查询设备最新一张单（教师撤销本人单用） */
    private String findMyBookingIdByAssetFor(String token, String assetId) throws Exception {
        JsonNode records = json(get(token, BOOKINGS + "/my")).path("data").path("records");
        for (JsonNode row : records) {
            if (assetId.equals(row.path("assetId").asText())) {
                return row.path("id").asText();
            }
        }
        throw new AssertionError("本人申请中未找到设备 " + assetId);
    }

    private String bookingStatus(String bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM equipment_booking WHERE id = ?",
                String.class, Long.parseLong(bookingId));
    }

    private String assetStatus(long assetId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM equipment_asset WHERE id = ?",
                String.class, assetId);
    }

    private String usageStatus(String bookingId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM equipment_usage WHERE booking_id = ?",
                String.class, Long.parseLong(bookingId));
    }

    private String login(String username) throws Exception {
        HttpResp resp = post(null, AUTH + "/login", Map.of("username", username, "password", "admin123"));
        assertOk(resp);
        return json(resp).path("data").path("token").asText();
    }

    /** 幂等重建 91xxx 测试设备（分类→型号→三台设备）与演示账号 */
    private void seed() {
        cleanupTestDocuments();
        jdbcTemplate.update("DELETE FROM equipment_asset WHERE id BETWEEN 910000000000000101 AND 910000000000000199");
        jdbcTemplate.update("DELETE FROM equipment_model WHERE id BETWEEN 910000000000000201 AND 910000000000000299");
        jdbcTemplate.update("DELETE FROM equipment_category WHERE id BETWEEN 910000000000000301 AND 910000000000000399");

        jdbcTemplate.update("""
                INSERT INTO equipment_category (id, parent_id, category_code, category_name)
                VALUES (?, NULL, 'IT_BOOKING_CAT', '集成测试分类')
                """, CATEGORY_ID);
        jdbcTemplate.update("""
                INSERT INTO equipment_model (id, category_id, model_code, equipment_name, specification, unit_name)
                VALUES (?, ?, 'IT_BOOKING_MODEL', '集成测试设备', '测试规格', '台')
                """, MODEL_ID, CATEGORY_ID);
        insertTestAsset(ASSET_NORMAL, "IT-BK-001");
        insertTestAsset(ASSET_OTHER, "IT-BK-002");

        // 演示账号兜底：test 库未执行过账号脚本时自建（role 2=TEACHER / 3=STUDENT）
        jdbcTemplate.update("INSERT INTO sys_person_roster (id, person_no, person_type, real_name, phone, department_id)"
                        + " SELECT ?, 'IT_T001', 'TEACHER', '集成测试教师', '13800000091', 1"
                        + " WHERE NOT EXISTS (SELECT 1 FROM sys_person_roster WHERE id = ?)",
                910000000000000401L, 910000000000000401L);
        jdbcTemplate.update("INSERT INTO sys_person_roster (id, person_no, person_type, real_name, phone, department_id)"
                        + " SELECT ?, 'IT_S001', 'STUDENT', '集成测试学生', '13900000091', 1"
                        + " WHERE NOT EXISTS (SELECT 1 FROM sys_person_roster WHERE id = ?)",
                910000000000000402L, 910000000000000402L);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, roster_id, user_type, real_name, account_name, phone, person_no, department_id, password_hash)
                SELECT ?, ?, 'TEACHER', '集成测试教师', 'teacher1', '13800000091', 'IT_T001', 1, ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = ?)
                """, TEACHER_ID, 910000000000000401L, PASSWORD_HASH, TEACHER_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user (id, roster_id, user_type, real_name, account_name, phone, person_no, department_id, password_hash)
                SELECT ?, ?, 'STUDENT', '集成测试学生', 'student1', '13900000091', 'IT_S001', 1, ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE id = ?)
                """, STUDENT_ID, 910000000000000402L, PASSWORD_HASH, STUDENT_ID);
        jdbcTemplate.update("UPDATE sys_user SET password_hash = ?, enabled = 1, failed_login_count = 0,"
                + " locked_until = NULL WHERE id IN (?, ?)", PASSWORD_HASH, TEACHER_ID, STUDENT_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id)"
                        + " SELECT ?, ?, 2 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = ? AND role_id = 2)",
                TEACHER_ID, TEACHER_ID, TEACHER_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id)"
                        + " SELECT ?, ?, 3 WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = ? AND role_id = 3)",
                STUDENT_ID, STUDENT_ID, STUDENT_ID);
    }

    private void insertTestAsset(long assetId, String assetCode) {
        // test 库 equipment_asset.lab_id 为 NOT NULL（表结构保留空间模块时期定义），给种子实验室 1
        jdbcTemplate.update("""
                INSERT INTO equipment_asset (id, model_id, asset_code, lab_id, department_id, status, enabled)
                VALUES (?, ?, ?, 1, 1, 'IDLE', 1)
                """, assetId, MODEL_ID, assetCode);
    }

    /** 清理本测试产生的申请链数据（事件→流水→申请→单据，FK 顺序） */
    private void cleanupTestDocuments() {
        List<Long> documentIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT b.document_id FROM equipment_booking b"
                        + " JOIN sys_user u ON u.id = b.applicant_id"
                        + " WHERE u.account_name IN ('student1', 'teacher1')", Long.class);
        if (documentIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(documentIds.size(), "?"));
        jdbcTemplate.update("DELETE FROM user_booking_restriction WHERE source_document_id IN (" + placeholders + ")",
                documentIds.toArray());
        jdbcTemplate.update("DELETE FROM document_event WHERE document_id IN (" + placeholders + ")",
                documentIds.toArray());
        jdbcTemplate.update("DELETE FROM equipment_usage WHERE booking_id IN"
                        + " (SELECT id FROM equipment_booking WHERE document_id IN (" + placeholders + "))",
                documentIds.toArray());
        jdbcTemplate.update("DELETE FROM equipment_booking WHERE document_id IN (" + placeholders + ")",
                documentIds.toArray());
        jdbcTemplate.update("DELETE FROM biz_document WHERE id IN (" + placeholders + ")",
                documentIds.toArray());
    }

    /** MySQL 3306 不可达或凭据无效时跳过整类测试 */
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

    private record HttpResp(int status, String body) {
    }

    private HttpResp get(String token, String path) {
        return exchange(HttpMethod.GET, path, token, null);
    }

    private HttpResp post(String token, String path, Object body) {
        return exchange(HttpMethod.POST, path, token, body);
    }

    private HttpResp put(String token, String path, Object body) {
        return exchange(HttpMethod.PUT, path, token, body);
    }

    private HttpResp delete(String token, String path) {
        return exchange(HttpMethod.DELETE, path, token, null);
    }

    /** 走真实 HTTP 栈：4xx/5xx 不抛异常，原样收集状态码与响应体供断言 */
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
    }
}
