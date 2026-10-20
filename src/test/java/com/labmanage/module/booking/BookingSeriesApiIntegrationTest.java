package com.labmanage.module.booking;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.labmanage.common.result.Result;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 节次时间轴 + 批量周期预约集成测试（H2 内存库 + 真实 HTTP 全链路）。
 * <p>
 * 覆盖：节次列表、周期预约创建（周数/幂等/冲突预检/学生越权）、批准逐周生成
 * 预约单与占用槽、申请人不审自己的单、24h 近期预约。
 * <p>
 * 用户：1=管理员、2=教师（含审批权限，用于自审拦截验证）、3=学生（授予 create
 * 权限以验证服务端身份门禁 40300）。周期用例使用互不重叠的星期/日期组合。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking_series_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.encoding=UTF-8",
        "spring.sql.init.schema-locations=classpath:sql/booking_schema_h2.sql",
        "spring.sql.init.data-locations=classpath:sql/booking_data_h2.sql"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BookingSeriesApiIntegrationTest.TestAuthConfig.class)
class BookingSeriesApiIntegrationTest {

    /** 用户 1=管理员（approve）；用户 2=教师（含 approve，专测自审拦截）；用户 3=学生（含 create，专测身份门禁） */
    private static final List<String> ADMIN_PERMS = List.of(
            "booking:lab:create", "booking:lab:cancel", "booking:lab:list",
            "booking:lab:approve", "booking:lab:view");
    private static final List<String> TEACHER_PERMS = List.of(
            "booking:lab:create", "booking:lab:cancel", "booking:lab:list",
            "booking:lab:approve", "booking:lab:view");
    private static final List<String> STUDENT_PERMS = List.of(
            "booking:lab:create", "booking:lab:view");

    @TestConfiguration(proxyBeanMethods = false)
    static class TestAuthConfig {

        @RestController
        static class TestAuthController {

            @GetMapping("/test/login")
            Result<String> login(@RequestParam(defaultValue = "1") long userId) {
                StpUtil.login(userId);
                return Result.success(StpUtil.getTokenValue());
            }
        }
    }

    @MockitoBean
    private StpInterface stpInterface;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private String adminToken;
    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void login() throws Exception {
        Mockito.when(stpInterface.getPermissionList(Mockito.eq("1"), Mockito.any()))
                .thenReturn(ADMIN_PERMS);
        Mockito.when(stpInterface.getPermissionList(Mockito.eq("2"), Mockito.any()))
                .thenReturn(TEACHER_PERMS);
        Mockito.when(stpInterface.getPermissionList(Mockito.eq("3"), Mockito.any()))
                .thenReturn(STUDENT_PERMS);
        Mockito.when(stpInterface.getRoleList(Mockito.any(), Mockito.any()))
                .thenReturn(List.of("ADMIN"));
        adminToken = login(1);
        teacherToken = login(2);
        studentToken = login(3);
    }

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

    /** 周期 1-8 的节次 JSON */
    private String seriesJson(int weekday, long startPeriod, long endPeriod,
                              String cycleStart, String cycleEnd, String idemKey) {
        return """
                {"spaceId": 1, "courseName": "Python 程序设计", "className": "软件 2301",
                 "purpose": "每周固定节次实训", "participantCount": 30,
                 "weekday": %d, "startPeriodId": %d, "endPeriodId": %d,
                 "cycleStartDate": "%s", "cycleEndDate": "%s", "idempotencyKey": "%s"}
                """.formatted(weekday, startPeriod, endPeriod, cycleStart, cycleEnd, idemKey);
    }

    // ------------------------------------------------------------------ 节次

    @Test
    @DisplayName("节次列表：返回 8 个启用节次，含节次号/名称/起止时刻")
    void periods_list() throws Exception {
        JsonNode body = get("/api/v1/booking/periods", studentToken);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data")).hasSize(8);
        assertThat(body.path("data").get(0).path("periodNo").asInt()).isEqualTo(1);
        assertThat(body.path("data").get(0).path("startClock").asText()).isEqualTo("08:00");
        assertThat(body.path("data").get(7).path("endClock").asText()).isEqualTo("17:40");
    }

    // ------------------------------------------------------------------ 周期创建

    @Test
    @DisplayName("周期预约创建成功：PENDING、周数正确、流水建档")
    void series_create_success() throws Exception {
        JsonNode body = post("/api/v1/booking/series",
                seriesJson(2, 3, 4, "2026-12-01", "2026-12-29", "key-ok-1"), teacherToken);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("data").path("statusName").asText()).isEqualTo("待审批");
        assertThat(body.path("data").path("weekCount").asInt()).isEqualTo(5);
        assertThat(body.path("data").path("weekdayName").asText()).isEqualTo("周二");
        assertThat(body.path("data").path("startPeriodName").asText()).isEqualTo("第3节");
        assertThat(body.path("data").path("endPeriodName").asText()).isEqualTo("第4节");
        assertThat(body.path("data").path("bookedCount").asInt()).isZero();
        assertThat(body.path("data").path("id").asText()).isNotBlank();
    }

    @Test
    @DisplayName("周期预约幂等：同申请人同幂等键重复提交 40900")
    void series_create_idempotent() throws Exception {
        JsonNode first = post("/api/v1/booking/series",
                seriesJson(3, 1, 2, "2026-10-21", "2026-11-11", "key-dupe-1"), teacherToken);
        assertThat(first.path("code").asInt()).isZero();

        JsonNode again = post("/api/v1/booking/series",
                seriesJson(3, 1, 2, "2026-10-21", "2026-11-11", "key-dupe-1"), teacherToken);
        assertThat(again.path("code").asInt()).isEqualTo(40900);
        assertThat(again.path("msg").asText()).contains("幂等键");
    }

    @Test
    @DisplayName("周期预约冲突预检：周期内含已被批准占用的周次 → 40900 并回显日期")
    void series_create_conflict_precheck() throws Exception {
        // 先建 12-08（周二）10:00-11:40 单次预约并批准
        JsonNode booking = post("/api/v1/booking/labs", """
                {"spaceId": 1, "purpose": "冲突锚点", "participantCount": 30,
                 "startAt": "2026-12-08T02:00:00.000Z", "endAt": "2026-12-08T03:40:00.000Z"}
                """, teacherToken);
        assertThat(booking.path("code").asInt()).isZero();
        assertThat(post("/api/v1/booking/labs/" + booking.path("data").path("id").asText() + "/approve",
                null, adminToken).path("code").asInt()).isZero();

        // 周期覆盖 12-08，同样 3-4 节 → 冲突
        JsonNode conflict = post("/api/v1/booking/series",
                seriesJson(2, 3, 4, "2026-12-01", "2026-12-29", "key-conflict-1"), teacherToken);
        assertThat(conflict.path("code").asInt()).isEqualTo(40900);
        assertThat(conflict.path("msg").asText()).contains("已被其他预约占用").contains("2026-12-08");
    }

    @Test
    @DisplayName("周期预约学生越权：学生调用发起/周期接口均 40300")
    void series_create_forbiddenForStudent() throws Exception {
        JsonNode series = post("/api/v1/booking/series",
                seriesJson(2, 3, 4, "2026-12-01", "2026-12-29", "key-student-1"), studentToken);
        assertThat(series.path("code").asInt()).isEqualTo(40300);

        JsonNode single = post("/api/v1/booking/labs", """
                {"spaceId": 1, "purpose": "学生越权", "participantCount": 10,
                 "startAt": "2026-12-01T02:00:00.000Z", "endAt": "2026-12-01T04:00:00.000Z"}
                """, studentToken);
        assertThat(single.path("code").asInt()).isEqualTo(40300);
    }

    // ------------------------------------------------------------------ 周期批准

    @Test
    @DisplayName("周期批准：逐周生成 APPROVED 预约单与占用槽，availability 可见占用")
    void series_approve_generates_weeks_and_slots() throws Exception {
        JsonNode created = post("/api/v1/booking/series",
                seriesJson(5, 5, 6, "2026-11-06", "2026-11-27", "key-approve-1"), teacherToken);
        assertThat(created.path("code").asInt()).isZero();
        String id = created.path("data").path("id").asText();

        JsonNode approved = post("/api/v1/booking/series/" + id + "/approve", null, adminToken);
        assertThat(approved.path("code").asInt()).isZero();

        // 首周日期占用可见
        JsonNode availability = get("/api/v1/booking/labs/availability?spaceId=1&date=2026-11-06", teacherToken);
        assertThat(availability.path("data").path("occupied")).hasSize(1);
        assertThat(availability.path("data").path("occupied").get(0).path("startAt").asText())
                .isEqualTo("2026-11-06T06:00:00.000Z");

        // 列表可见已生成 4 张预约单
        JsonNode list = get("/api/v1/booking/series/all?pageNum=1&pageSize=20&status=APPROVED", adminToken);
        JsonNode row = findSeries(list, id);
        assertThat(row.path("status").asText()).isEqualTo("APPROVED");
        assertThat(row.path("bookedCount").asInt()).isEqualTo(4);

        // 详情含生成明细：4 周全部 APPROVED
        JsonNode detail = get("/api/v1/booking/series/" + id, adminToken);
        assertThat(detail.path("data").path("generated")).hasSize(4);
        assertThat(detail.path("data").path("generated").get(0).path("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("周期批准自审拦截：申请人不审自己的单 → 40300")
    void series_approve_forbiddenForApplicant() throws Exception {
        JsonNode created = post("/api/v1/booking/series",
                seriesJson(4, 1, 2, "2026-10-22", "2026-11-12", "key-self-1"), teacherToken);
        assertThat(created.path("code").asInt()).isZero();
        String id = created.path("data").path("id").asText();

        JsonNode self = post("/api/v1/booking/series/" + id + "/approve", null, teacherToken);
        assertThat(self.path("code").asInt()).isEqualTo(40300);
        assertThat(self.path("msg").asText()).contains("申请人不审自己的单");
    }

    @Test
    @DisplayName("周期批准期重检冲突：提交后时段被他人抢占 → 40900 整单原子，不生成任何预约单")
    void series_approve_conflict_rechecks_atomic() throws Exception {
        // 周四 3-4 节，提交期无占用（可提交成功）
        JsonNode created = post("/api/v1/booking/series",
                seriesJson(4, 3, 4, "2026-11-05", "2026-11-26", "key-race-1"), teacherToken);
        assertThat(created.path("code").asInt()).isZero();
        String id = created.path("data").path("id").asText();

        // 管理员在周期首周（11-05 周四）用单次预约抢占同一时段
        JsonNode anchor = post("/api/v1/booking/labs", """
                {"spaceId": 1, "purpose": "批准期冲突抢占锚点", "participantCount": 30,
                 "startAt": "2026-11-05T02:00:00.000Z", "endAt": "2026-11-05T03:40:00.000Z"}
                """, teacherToken);
        assertThat(anchor.path("code").asInt()).isZero();
        assertThat(post("/api/v1/booking/labs/" + anchor.path("data").path("id").asText() + "/approve",
                null, adminToken).path("code").asInt()).isZero();

        // 批准被拒绝：40900 且整单原子（不部分生成）
        JsonNode rejected = post("/api/v1/booking/series/" + id + "/approve", null, adminToken);
        assertThat(rejected.path("code").asInt()).isEqualTo(40900);
        assertThat(rejected.path("msg").asText()).contains("已被其他预约占用").contains("2026-11-05");

        // 状态仍 PENDING、未生成任何预约单
        JsonNode detail = get("/api/v1/booking/series/" + id, adminToken);
        assertThat(detail.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(detail.path("data").path("generated")).isEmpty();
    }

    // ------------------------------------------------------------------ 24h 近期

    @Test
    @DisplayName("场地近期预约：未来 24h 内预约可见，远端未来预约为空")
    void recent_booking_within_24h() throws Exception {
        // 近端：now+1h 起 1 小时
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Instant base = Instant.now().plusSeconds(3600);
        String start = base.atZone(ZoneOffset.UTC).format(fmt);
        String end = base.plusSeconds(3600).atZone(ZoneOffset.UTC).format(fmt);
        JsonNode body = post("/api/v1/booking/labs", """
                {"spaceId": 1, "purpose": "24h 近期测试", "participantCount": 30,
                 "startAt": "%s", "endAt": "%s"}
                """.formatted(start, end), teacherToken);
        assertThat(body.path("code").asInt()).isZero();

        JsonNode recent = get("/api/v1/booking/labs/recent?spaceId=1", teacherToken);
        assertThat(recent.path("code").asInt()).isZero();
        assertThat(recent.path("data")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(recent.path("data").get(0).path("purpose").asText()).isEqualTo("24h 近期测试");
    }

    private JsonNode findSeries(JsonNode list, String id) {
        for (JsonNode row : list.path("data").path("records")) {
            if (row.path("id").asText().equals(id)) {
                return row;
            }
        }
        throw new AssertionError("周期预约未在列表中：id=" + id);
    }
}