package com.labmanage.module.space;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.labmanage.common.result.Result;
import com.labmanage.module.space.service.impl.SpaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 场地接口日志埋点验证（H2 内存库 + 真实 HTTP 全链路）。
 * <p>
 * 目的：启动真实 Tomcat，经 HTTP → Sa-Token 注解鉴权 → Service →
 * MyBatis-Plus → H2 完整链路调用分页/详情/统计接口，捕获
 * SpaceServiceImpl 的日志输出，验证埋点内容（含按状态筛选的条件摘要）。
 * 数据源为 H2 MySQL 模式（本机 MySQL 凭据未定，schema/data 见 sql/space_*_h2.sql）。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:space_log_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.encoding=UTF-8",
        "spring.sql.init.schema-locations=classpath:sql/space_schema_h2.sql",
        "spring.sql.init.data-locations=classpath:sql/space_data_h2.sql"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SpaceApiLogIntegrationTest.TestAuthConfig.class)
class SpaceApiLogIntegrationTest {

    /**
     * 测试专用登录端点（路径在 /api/v1 之外，绕开 SaTokenConfig 的路由级登录校验）。
     * Sa-Token 1.46.0 的上下文由 HTTP 过滤器在请求线程初始化，
     * 测试线程直接调用 StpUtil.login() 会抛「上下文尚未初始化」，
     * 故经由真实 HTTP 请求完成登录并回传 token。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestAuthConfig {

        @RestController
        static class TestAuthController {

            @GetMapping("/test/login")
            Result<String> login() {
                StpUtil.login(1L);
                return Result.success(StpUtil.getTokenValue());
            }
        }
    }

    /**
     * 以 Mock 替换 roles 模块的 StpInterfaceImpl（生产实现查 RBAC 库表，
     * H2 测试库无这些表）：为登录用户 1 放开场地模块全部权限。
     */
    @MockitoBean
    private StpInterface stpInterface;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static ListAppender<ILoggingEvent> logAppender;

    private String token;

    @BeforeEach
    void login() throws Exception {
        Mockito.when(stpInterface.getPermissionList(Mockito.any(), Mockito.any()))
                .thenReturn(List.of("space:space:list", "space:space:read", "space:space:create",
                        "space:space:update", "space:space:delete"));
        Mockito.when(stpInterface.getRoleList(Mockito.any(), Mockito.any()))
                .thenReturn(List.of("ADMIN"));
        // Spring Boot 上下文在测试实例创建阶段才启动，其日志系统初始化会重置
        // logback context，因此捕获器须在上下文就绪后（此时）挂载，不能用 @BeforeAll
        if (logAppender == null) {
            Logger logger = (Logger) LoggerFactory.getLogger(SpaceServiceImpl.class);
            logAppender = new ListAppender<>();
            logAppender.start();
            logger.addAppender(logAppender);
        }
        logAppender.list.clear();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/test/login"))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        token = objectMapper.readTree(response.body()).path("data").asText();
        assertThat(token).isNotBlank();
    }

    // ------------------------------------------------------------------ 辅助

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private int getRaw(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private List<String> logs() {
        return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private List<String> warnLogs() {
        return logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    // ------------------------------------------------------------------ 用例

    @Test
    @DisplayName("分页（无条件）：日志含操作人/页码/命中总数/耗时，条件摘要为「无条件」")
    void pageWithoutFilter_shouldLogSummary() throws Exception {
        JsonNode body = get("/api/v1/space/spaces?pageNum=1&pageSize=10");

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asInt()).isEqualTo(3);
        assertThat(body.path("data").path("records")).hasSize(3);

        assertThat(logs()).anySatisfy(msg -> {
            assertThat(msg).contains("[场地分页] 完成");
            assertThat(msg).contains("操作人=1");
            assertThat(msg).contains("页码=1, 页大小=10");
            assertThat(msg).contains("条件=[无条件]");
            assertThat(msg).contains("命中总数=3, 返回行数=3");
            assertThat(msg).contains("耗时=");
        });
    }

    @Test
    @DisplayName("分页（按状态筛选）：仅命中维护中场地，条件摘要含「状态=MAINTAINING」")
    void pageFilterByStatus_shouldLogStatusInSummary() throws Exception {
        JsonNode body = get("/api/v1/space/spaces?pageNum=1&pageSize=10&status=MAINTAINING");

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("records").get(0).path("labName").asText())
                .isEqualTo("C703 电子电工实训室");
        assertThat(body.path("data").path("records").get(0).path("status").asText())
                .isEqualTo("MAINTAINING");

        assertThat(logs()).anySatisfy(msg -> {
            assertThat(msg).contains("[场地分页] 完成");
            assertThat(msg).contains("条件=[状态=MAINTAINING]");
            assertThat(msg).contains("命中总数=1, 返回行数=1");
        });
    }

    @Test
    @DisplayName("分页（复合条件：类型+容量区间）：条件摘要逐项拼接")
    void pageFilterByTypeAndCapacity_shouldLogCompositeSummary() throws Exception {
        JsonNode body = get("/api/v1/space/spaces?labType=TRAINING_ROOM&minCapacity=46&maxCapacity=60");

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("records").get(0).path("labName").asText())
                .isEqualTo("A703 网络实训室");

        assertThat(logs()).anySatisfy(msg -> {
            assertThat(msg).contains("[场地分页] 完成");
            assertThat(msg).contains("条件=[类型=TRAINING_ROOM, 容量>=46, 容量<=60]");
            assertThat(msg).contains("命中总数=1");
        });
    }

    @Test
    @DisplayName("详情：响应字段正确，日志含操作人/名称/状态/耗时")
    void detail_shouldLogSpaceInfo() throws Exception {
        JsonNode body = get("/api/v1/space/spaces/1");

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("labName").asText()).isEqualTo("A703 网络实训室");
        assertThat(body.path("data").path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(body.path("data").path("capacity").asInt()).isEqualTo(50);

        assertThat(logs()).anySatisfy(msg -> {
            assertThat(msg).contains("[场地详情] 完成");
            assertThat(msg).contains("操作人=1, 场地ID=1");
            assertThat(msg).contains("名称=A703 网络实训室, 状态=AVAILABLE");
            assertThat(msg).contains("耗时=");
        });
    }

    @Test
    @DisplayName("详情（不存在）：返回 HTTP 404 + code 40400，日志 warn 场地不存在")
    void detailNotFound_shouldWarnAndReturn40400() throws Exception {
        JsonNode body = get("/api/v1/space/spaces/999");

        assertThat(body.path("code").asInt()).isEqualTo(40400);
        assertThat(body.path("data").isNull() || body.path("data").isMissingNode()).isTrue();

        assertThat(warnLogs()).anySatisfy(msg ->
                assertThat(msg).contains("[场地查询] 场地不存在或已删除：场地ID=999"));
    }

    @Test
    @DisplayName("未登录访问：HTTP 401 + code 40100（验证 Sa-Token 注解鉴权在链路中生效）")
    void pageWithoutToken_shouldReturn40100() throws Exception {
        int status = getRaw("/api/v1/space/spaces");
        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("统计：日志含四档计数（附带验证 stats 埋点）")
    void stats_shouldLogCounts() throws Exception {
        JsonNode body = get("/api/v1/space/spaces/stats");

        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("total").asInt()).isEqualTo(3);
        assertThat(body.path("data").path("available").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("maintaining").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("closed").asInt()).isEqualTo(1);

        assertThat(logs()).anySatisfy(msg -> {
            assertThat(msg).contains("[场地统计] 完成");
            assertThat(msg).contains("总数=3, 可用=1, 维护中=1, 停用=1");
        });
    }
}
