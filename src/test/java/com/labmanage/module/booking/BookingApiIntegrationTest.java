package com.labmanage.module.booking;

import com.labmanage.common.result.Result;
import com.labmanage.common.security.JwtService;
import com.labmanage.roles.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 场地预约集成测试（H2 内存库 + 真实 HTTP 全链路）。
 * <p>
 * 覆盖：创建校验（状态/容量/跨天/窗口/过去时间/格式/冲突）、审批（成功/
 * 状态机/并发互斥恰一成功）、取消（本人限制/APPROVED 释放占用）、查询
 * （我的/全部权限矩阵/可用时段/统计）、未登录 401。
 * <p>
 * H2 库在同一测试类内共享且无方法级回滚，各用例使用互不重叠的预约日期
 * （12-01 ~ 12-16 逐日用例分配）保证相互独立；「我的预约」用日期区间收窄断言。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.encoding=UTF-8",
        "spring.sql.init.schema-locations=classpath:sql/booking_schema_h2.sql",
        "spring.sql.init.data-locations=classpath:sql/booking_data_h2.sql"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BookingApiIntegrationTest.TestAuthConfig.class)
class BookingApiIntegrationTest {

    /**
     * 测试专用登录端点（/api/v1 之外，绕开路由级登录校验），
     * userId 可指定（1=管理员/审批人，2=教师/申请人）。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestAuthConfig {

        @RestController
        static class TestAuthController {

            private final JwtService jwtService;

            TestAuthController(JwtService jwtService) {
                this.jwtService = jwtService;
            }

            @GetMapping("/test/login")
            Result<String> login(@RequestParam(defaultValue = "1") long userId) {
                return Result.success(jwtService.issueAccess(userId));
            }
        }
    }

    /** 管理员（user 1）：全部预约权限；教师（user 2）：无审批权限 */
    private static final List<String> ADMIN_PERMS = List.of(
            "booking:lab:create", "booking:lab:cancel", "booking:lab:list", "booking:lab:approve");
    private static final List<String> TEACHER_PERMS = List.of(
            "booking:lab:create", "booking:lab:cancel", "booking:lab:list");

    /**
     * Mock AuthService：JWT 过滤器每请求经它装配角色/权限，
     * 权限按登录用户返回（与生产实时加载行为一致，免去 H2 种 RBAC 全表）。
     */
    @MockitoBean
    private AuthService authService;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private String adminToken;
    private String teacherToken;

    @BeforeEach
    void login() throws Exception {
        Mockito.when(authService.loadPermissionCodes(1L)).thenReturn(ADMIN_PERMS);
        Mockito.when(authService.loadPermissionCodes(2L)).thenReturn(TEACHER_PERMS);
        Mockito.when(authService.loadRoleCodes(Mockito.any())).thenReturn(List.of("ADMIN"));
        adminToken = login(1);
        teacherToken = login(2);
    }

    // ------------------------------------------------------------------ 辅助

    private String login(long userId) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/test/login?userId=" + userId))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String token = objectMapper.readTree(response.body()).path("data").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private JsonNode get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET().build());
    }

    private JsonNode post(String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json));
        if (json != null) {
            builder.header("Content-Type", "application/json");
        }
        return send(builder.build());
    }

    private JsonNode send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private int getRaw(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    /** 指定日期的上海 10:00-12:00 时段（场地 1 窗口内、同日） */
    private JsonNode createBookingOn(String date, String token) throws Exception {
        return createBooking(date + "T02:00:00.000Z", date + "T04:00:00.000Z", token);
    }

    /** 指定起止时间在场地 1 提交预约 */
    private JsonNode createBooking(String startAt, String endAt, String token) throws Exception {
        return post("/api/v1/booking/labs", """
                {"spaceId": 1, "purpose": "期末实训周考务准备", "participantCount": 30,
                 "startAt": "%s", "endAt": "%s"}
                """.formatted(startAt, endAt), token);
    }

    // ------------------------------------------------------------------ 创建校验

    @Test
    @DisplayName("创建成功：PENDING、申请人姓名回填、runtimePhase 为空")
    void create_success() throws Exception {
        JsonNode body = createBookingOn("2026-12-01", teacherToken);

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("data").path("statusName").asText()).isEqualTo("待审批");
        assertThat(body.path("data").path("applicantName").asText()).isEqualTo("王建国");
        assertThat(body.path("data").path("labName").asText()).isEqualTo("A703 网络实训室");
        assertThat(body.path("data").path("runtimePhase").isNull()
                || body.path("data").path("runtimePhase").isMissingNode()).isTrue();
        assertThat(body.path("data").path("id").asText()).isNotBlank();
    }

    @Test
    @DisplayName("创建失败（场地维护中）：40900")
    void create_spaceNotAvailable() throws Exception {
        JsonNode body = post("/api/v1/booking/labs", """
                {"spaceId": 2, "purpose": "测试", "participantCount": 10,
                 "startAt": "2026-12-02T02:00:00.000Z", "endAt": "2026-12-02T04:00:00.000Z"}
                """, teacherToken);
        assertThat(body.path("code").asInt()).isEqualTo(40900);
    }

    @Test
    @DisplayName("创建失败（人数超容量）：40000")
    void create_capacityExceeded() throws Exception {
        JsonNode body = post("/api/v1/booking/labs", """
                {"spaceId": 1, "purpose": "测试", "participantCount": 51,
                 "startAt": "2026-12-02T02:00:00.000Z", "endAt": "2026-12-02T04:00:00.000Z"}
                """, teacherToken);
        assertThat(body.path("code").asInt()).isEqualTo(40000);
    }

    @Test
    @DisplayName("创建失败（跨上海日期）：40000")
    void create_crossDay() throws Exception {
        JsonNode body = createBooking("2026-12-01T10:00:00.000Z", "2026-12-01T17:00:00.000Z", teacherToken);
        assertThat(body.path("code").asInt()).isEqualTo(40000);
    }

    @Test
    @DisplayName("创建失败（超出开放窗口 08:00-21:00）：40000")
    void create_outOfWindow() throws Exception {
        JsonNode body = createBooking("2026-12-01T00:30:00.000Z", "2026-12-01T14:00:00.000Z", teacherToken);
        assertThat(body.path("code").asInt()).isEqualTo(40000);
    }

    @Test
    @DisplayName("创建失败（开始时间早于当前时间）：40000")
    void create_pastTime() throws Exception {
        JsonNode body = createBooking("2025-01-01T02:00:00.000Z", "2025-01-01T04:00:00.000Z", teacherToken);
        assertThat(body.path("code").asInt()).isEqualTo(40000);
    }

    @Test
    @DisplayName("创建失败（时间无偏移量）：40000")
    void create_invalidTimeFormat() throws Exception {
        JsonNode body = createBooking("2026-12-01T10:00:00", "2026-12-01T12:00:00", teacherToken);
        assertThat(body.path("code").asInt()).isEqualTo(40000);
    }

    @Test
    @DisplayName("创建失败（与已批准预约时段冲突）：40900")
    void create_conflictWithApproved() throws Exception {
        JsonNode created = createBookingOn("2026-12-09", teacherToken);
        assertThat(created.path("code").asInt()).isZero();
        JsonNode approved = post("/api/v1/booking/labs/" + created.path("data").path("id").asText() + "/approve",
                null, adminToken);
        assertThat(approved.path("code").asInt()).isZero();

        JsonNode conflict = createBookingOn("2026-12-09", teacherToken);
        assertThat(conflict.path("code").asInt()).isEqualTo(40900);
    }

    // ------------------------------------------------------------------ 审批

    @Test
    @DisplayName("审批成功：状态 APPROVED、可用时段出现占用段")
    void approve_success_rendersOccupied() throws Exception {
        JsonNode created = createBookingOn("2026-12-10", teacherToken);
        String id = created.path("data").path("id").asText();

        JsonNode approved = post("/api/v1/booking/labs/" + id + "/approve", null, adminToken);
        assertThat(approved.path("code").asInt()).isZero();

        JsonNode availability = get("/api/v1/booking/labs/availability?spaceId=1&date=2026-12-10", teacherToken);
        assertThat(availability.path("code").asInt()).isZero();
        assertThat(availability.path("data").path("openStart").asText()).isEqualTo("08:00");
        assertThat(availability.path("data").path("occupied")).hasSize(1);
        assertThat(availability.path("data").path("occupied").get(0).path("startAt").asText())
                .isEqualTo("2026-12-10T02:00:00.000Z");

        JsonNode detail = get("/api/v1/booking/labs/" + id, teacherToken);
        assertThat(detail.path("data").path("status").asText()).isEqualTo("APPROVED");
        assertThat(detail.path("data").path("runtimePhase").asText()).isEqualTo("UPCOMING");
    }

    @Test
    @DisplayName("审批失败（非待审批状态）：40900")
    void approve_notPending() throws Exception {
        JsonNode created = createBookingOn("2026-12-11", teacherToken);
        String id = created.path("data").path("id").asText();
        assertThat(post("/api/v1/booking/labs/" + id + "/approve", null, adminToken)
                .path("code").asInt()).isZero();
        // 二次审批：已 APPROVED，拒绝
        assertThat(post("/api/v1/booking/labs/" + id + "/approve", null, adminToken)
                .path("code").asInt()).isEqualTo(40900);
    }

    @Test
    @DisplayName("并发审批互斥：同场地同时段两个待审批，恰好一个成功一个 40900")
    void approve_concurrentMutex() throws Exception {
        JsonNode first = createBookingOn("2026-12-12", teacherToken);
        JsonNode second = createBookingOn("2026-12-12", teacherToken);
        String id1 = first.path("data").path("id").asText();
        String id2 = second.path("data").path("id").asText();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<JsonNode> task1 = () -> {
            start.await();
            return post("/api/v1/booking/labs/" + id1 + "/approve", null, adminToken);
        };
        Callable<JsonNode> task2 = () -> {
            start.await();
            return post("/api/v1/booking/labs/" + id2 + "/approve", null, adminToken);
        };
        Future<JsonNode> f1 = pool.submit(task1);
        Future<JsonNode> f2 = pool.submit(task2);
        start.countDown();
        int code1 = f1.get(60, TimeUnit.SECONDS).path("code").asInt();
        int code2 = f2.get(60, TimeUnit.SECONDS).path("code").asInt();
        pool.shutdownNow();

        assertThat(code1).isNotEqualTo(code2);
        assertThat(code1 == 0 || code2 == 0).isTrue();
        assertThat(code1 == 40900 || code2 == 40900).isTrue();

        // 可用时段恰一段占用（未双写）
        JsonNode availability = get("/api/v1/booking/labs/availability?spaceId=1&date=2026-12-12", teacherToken);
        assertThat(availability.path("data").path("occupied")).hasSize(1);
    }

    @Test
    @DisplayName("驳回成功：REJECTED 且原因回显；驳回原因必填")
    void reject_success_andRequiresReason() throws Exception {
        JsonNode created = createBookingOn("2026-12-13", teacherToken);
        String id = created.path("data").path("id").asText();

        JsonNode rejected = post("/api/v1/booking/labs/" + id + "/reject",
                "{\"reason\": \"该时段已有教学安排\"}", adminToken);
        assertThat(rejected.path("code").asInt()).isZero();

        JsonNode detail = get("/api/v1/booking/labs/" + id, teacherToken);
        assertThat(detail.path("data").path("status").asText()).isEqualTo("REJECTED");
        assertThat(detail.path("data").path("rejectReason").asText()).isEqualTo("该时段已有教学安排");

        // 驳回原因必填
        JsonNode another = createBookingOn("2026-12-14", teacherToken);
        JsonNode noReason = post("/api/v1/booking/labs/" + another.path("data").path("id").asText() + "/reject",
                "{\"reason\": \"\"}", adminToken);
        assertThat(noReason.path("code").asInt()).isEqualTo(40000);
    }

    // ------------------------------------------------------------------ 取消

    @Test
    @DisplayName("越权拦截：教师（无 booking:lab:approve）直接调用批准/驳回接口均 40300，预约状态不变")
    void approveReject_forbiddenForTeacher() throws Exception {
        // 待审批单 A：教师尝试越权批准
        JsonNode a = createBookingOn("2026-12-17", teacherToken);
        String idA = a.path("data").path("id").asText();
        JsonNode approveDenied = post("/api/v1/booking/labs/" + idA + "/approve", null, teacherToken);
        assertThat(approveDenied.path("code").asInt()).isEqualTo(40300);

        // 待审批单 B：教师尝试越权驳回（即便带了原因也拒绝）
        JsonNode b = createBookingOn("2026-12-18", teacherToken);
        String idB = b.path("data").path("id").asText();
        JsonNode rejectDenied = post("/api/v1/booking/labs/" + idB + "/reject",
                "{\"reason\": \"教师越权尝试驳回\"}", teacherToken);
        assertThat(rejectDenied.path("code").asInt()).isEqualTo(40300);

        // 两单仍为 PENDING，且驳回单没有写入任何驳回原因
        assertThat(get("/api/v1/booking/labs/" + idA, teacherToken)
                .path("data").path("status").asText()).isEqualTo("PENDING");
        JsonNode detailB = get("/api/v1/booking/labs/" + idB, teacherToken);
        assertThat(detailB.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(detailB.path("data").path("rejectReason").isNull()
                || detailB.path("data").path("rejectReason").isMissingNode()).isTrue();

        // 管理员仍可正常审批单 A，证明拦截按权限而非接口故障
        assertThat(post("/api/v1/booking/labs/" + idA + "/approve", null, adminToken)
                .path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("取消失败（非申请人本人）：40300")
    void cancel_byNonApplicant() throws Exception {
        JsonNode created = createBookingOn("2026-12-15", teacherToken);
        String id = created.path("data").path("id").asText();

        JsonNode body = post("/api/v1/booking/labs/" + id + "/cancel",
                "{\"reason\": \"管理员代取消\"}", adminToken);
        assertThat(body.path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("取消已批准预约：释放占用，同时段可再次预约")
    void cancelApproved_releasesSlot() throws Exception {
        JsonNode created = createBookingOn("2026-12-16", teacherToken);
        String id = created.path("data").path("id").asText();
        assertThat(post("/api/v1/booking/labs/" + id + "/approve", null, adminToken)
                .path("code").asInt()).isZero();

        JsonNode cancelled = post("/api/v1/booking/labs/" + id + "/cancel",
                "{\"reason\": \"教学计划调整\"}", teacherToken);
        assertThat(cancelled.path("code").asInt()).isZero();

        // 占用已释放：同时段可再次提交并成功
        JsonNode again = createBookingOn("2026-12-16", teacherToken);
        assertThat(again.path("code").asInt()).isZero();

        JsonNode detail = get("/api/v1/booking/labs/" + id, teacherToken);
        assertThat(detail.path("data").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(detail.path("data").path("cancelType").asText()).isEqualTo("MANUAL");
    }

    // ------------------------------------------------------------------ 查询与权限

    @Test
    @DisplayName("我的预约：仅返回当前登录人申请的记录（按用例日期区间收窄断言）")
    void pageMine_scopedToApplicant() throws Exception {
        assertThat(createBookingOn("2026-12-03", teacherToken).path("code").asInt()).isZero();

        String range = "startFrom=2026-12-03T00:00:00.000Z&startTo=2026-12-03T23:59:59.000Z";
        JsonNode mine = get("/api/v1/booking/labs?pageNum=1&pageSize=20&" + range, teacherToken);
        assertThat(mine.path("code").asInt()).isZero();
        assertThat(mine.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(mine.path("data").path("records").get(0).path("applicantName").asText())
                .isEqualTo("王建国");

        // 管理员当日未提交预约，我的预约为空
        JsonNode adminMine = get("/api/v1/booking/labs?pageNum=1&pageSize=20&" + range, adminToken);
        assertThat(adminMine.path("data").path("total").asInt()).isEqualTo(0);
    }

    @Test
    @DisplayName("全部预约（审批视角）：管理员可见；教师无审批权限 40300")
    void pageAll_permissionMatrix() throws Exception {
        assertThat(createBookingOn("2026-12-04", teacherToken).path("code").asInt()).isZero();

        String range = "startFrom=2026-12-04T00:00:00.000Z&startTo=2026-12-04T23:59:59.000Z";
        JsonNode all = get("/api/v1/booking/labs/all?pageNum=1&pageSize=20&" + range, adminToken);
        assertThat(all.path("code").asInt()).isZero();
        assertThat(all.path("data").path("total").asInt()).isEqualTo(1);

        JsonNode denied = get("/api/v1/booking/labs/all?pageNum=1&pageSize=20&" + range, teacherToken);
        assertThat(denied.path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("预约详情：非申请人且无审批权限（教师看管理员的预约）40300")
    void detail_nonOwnerWithoutApprove() throws Exception {
        JsonNode created = createBookingOn("2026-12-05", adminToken);
        String id = created.path("data").path("id").asText();

        JsonNode denied = get("/api/v1/booking/labs/" + id, teacherToken);
        assertThat(denied.path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("预约统计：各状态计数自洽（自建样例自校验）")
    void stats_countsByStatus() throws Exception {
        // 自建完整样例：一条已批准、一条待审批、一条已取消
        JsonNode a = createBookingOn("2026-12-06", teacherToken);
        assertThat(post("/api/v1/booking/labs/" + a.path("data").path("id").asText() + "/approve", null, adminToken)
                .path("code").asInt()).isZero();
        createBookingOn("2026-12-07", teacherToken);
        JsonNode c = createBookingOn("2026-12-08", teacherToken);
        assertThat(post("/api/v1/booking/labs/" + c.path("data").path("id").asText() + "/cancel",
                "{\"reason\": \"计划变更\"}", teacherToken).path("code").asInt()).isZero();

        JsonNode stats = get("/api/v1/booking/labs/stats", adminToken);
        assertThat(stats.path("code").asInt()).isZero();
        assertThat(stats.path("data").path("approved").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.path("data").path("pending").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.path("data").path("cancelled").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.path("data").path("total").asLong())
                .isEqualTo(stats.path("data").path("pending").asLong()
                        + stats.path("data").path("approved").asLong()
                        + stats.path("data").path("rejected").asLong()
                        + stats.path("data").path("cancelled").asLong());

        // 教师无审批权限，统计接口 40300
        assertThat(get("/api/v1/booking/labs/stats", teacherToken).path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("未登录访问：HTTP 401")
    void withoutToken_shouldReturn401() throws Exception {
        assertThat(getRaw("/api/v1/booking/labs")).isEqualTo(401);
    }
}
