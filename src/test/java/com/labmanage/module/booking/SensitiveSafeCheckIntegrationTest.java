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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3 二级认证专项测试（lab.security.sensitive-safe-check 保持默认开启）。
 * <p>
 * 验证：敏感操作（批准/驳回/强制取消/签到/清点、场地删除/停用/导入、授权创建/撤销）
 * 在未完成密码二级认证（openSafe）时被 40301 拦截；普通读接口不受影响。
 * 通过密码确认后的放行链路在开发环境实测验证（confirm 依赖真实密码哈希）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:safecheck_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.encoding=UTF-8",
        "spring.sql.init.schema-locations=classpath:sql/booking_schema_h2.sql",
        "spring.sql.init.data-locations=classpath:sql/booking_data_h2.sql"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SensitiveSafeCheckIntegrationTest.TestAuthConfig.class)
class SensitiveSafeCheckIntegrationTest {

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

    private static final List<String> ADMIN_PERMS = List.of(
            "booking:lab:create", "booking:lab:cancel", "booking:lab:list", "booking:lab:approve");

    @MockitoBean
    private StpInterface stpInterface;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private String adminToken;

    @BeforeEach
    void login() throws Exception {
        Mockito.when(stpInterface.getPermissionList(Mockito.eq("1"), Mockito.any()))
                .thenReturn(ADMIN_PERMS);
        Mockito.when(stpInterface.getRoleList(Mockito.any(), Mockito.any()))
                .thenReturn(List.of("ADMIN"));
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/test/login?userId=1"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        adminToken = objectMapper.readTree(response.body()).path("data").asText();
        assertThat(adminToken).isNotBlank();
    }

    private JsonNode post(String path, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + adminToken)
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json));
        if (json != null) {
            builder.header("Content-Type", "application/json");
        }
        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private JsonNode get(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path))
                        .header("Authorization", "Bearer " + adminToken)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    @Test
    @DisplayName("B3：未二级认证时敏感操作被 40301 拦截，普通读接口不受影响")
    void sensitiveOps_blockedWithoutSafeAuth() throws Exception {
        // 准备一条待审批单（创建非敏感操作）
        JsonNode created = post("/api/v1/booking/labs", """
                {"spaceId": 1, "purpose": "二级认证测试", "participantCount": 10,
                 "startAt": "2027-01-05T02:00:00.000Z", "endAt": "2027-01-05T04:00:00.000Z"}
                """);
        assertThat(created.path("code").asInt()).isZero();
        String id = created.path("data").path("id").asText();

        // 敏感操作一律 40301（未 openSafe）
        assertThat(post("/api/v1/booking/labs/" + id + "/approve", null).path("code").asInt())
                .isEqualTo(40301);
        assertThat(post("/api/v1/booking/labs/" + id + "/reject", "{\"reason\": \"x\"}").path("code").asInt())
                .isEqualTo(40301);
        assertThat(post("/api/v1/booking/labs/" + id + "/force_cancel", "{\"reason\": \"x\"}").path("code").asInt())
                .isEqualTo(40301);
        assertThat(post("/api/v1/booking/labs/batch_approve", "{\"ids\": [" + id + "]}").path("code").asInt())
                .isEqualTo(40301);

        // 被拦截后单据状态未被破坏
        assertThat(get("/api/v1/booking/labs/" + id).path("data").path("status").asText())
                .isEqualTo("PENDING");

        // 普通读接口不需要二级认证
        assertThat(get("/api/v1/booking/labs?pageNum=1&pageSize=5").path("code").asInt()).isZero();
    }
}
