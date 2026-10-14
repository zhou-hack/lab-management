package com.labmanage.roles.auth;

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
import java.sql.Timestamp;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 认证链路集成测试（test profile，连接 lab_management_test）
 * <p>
 * 覆盖：无 token 401、登录参数校验、错误密码失败计数与成功清零、
 * 账号停用拒绝、连续 5 次失败锁定 30 分钟（锁定期内正确密码也拒绝）、
 * 注销后 token 失效、user_menus 菜单树结构（DIRECTORY→M / PAGE→C / ACTION→F）。
 * 测试菜单用独立 91xxx ID 段自造，不依赖业务种子脚本执行状态。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowTest {

    private static final String AUTH = "/api/v1/auth";

    /** 管理员（RBAC 种子内置） */
    private static final long ADMIN_ID = 1L;

    /** 测试菜单 ID 段（与 EquipmentBookingFlowTest 的 91xxx 约定一致） */
    private static final long MENU_ROOT_ID = 910000000000000401L;
    private static final long MENU_PAGE_ID = 910000000000000402L;
    private static final long MENU_ACTION_ID = 910000000000000403L;
    private static final long ROLE_MENU_ROOT_ID = 910000000000000411L;
    private static final long ROLE_MENU_PAGE_ID = 910000000000000412L;
    private static final long ROLE_MENU_ACTION_ID = 910000000000000413L;

    /** admin123 的 BCrypt 哈希（与 sql/计算机网络实训室种子数据.sql 一致） */
    private static final String ADMIN_PASSWORD_HASH =
            "$2a$10$XV8quUrmu3w9j86DSh5kY.cXgNZLtFBFqBT858uAz99zke4FZee.y";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    private RestClient restClient;

    /** 管理员会话 token（用例间传递） */
    private String adminToken;

    @BeforeAll
    void prepare() {
        checkDatabaseAvailable();
        seed();
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE id BETWEEN ? AND ?",
                ROLE_MENU_ROOT_ID, ROLE_MENU_ACTION_ID);
        // FK 子→父顺序删除（sys_menu 自引用 parent_id）
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id = ?", MENU_ACTION_ID);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id = ?", MENU_PAGE_ID);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id = ?", MENU_ROOT_ID);
        // 管理员状态还原（密码哈希保留为 admin123，与种子一致）
        jdbcTemplate.update(
                "UPDATE sys_user SET failed_login_count = 0, locked_until = NULL, enabled = 1 WHERE id = ?",
                ADMIN_ID);
    }

    // ------------------------------------------------------------------
    // 鉴权
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void shouldRejectUserMenusWithoutToken() throws Exception {
        assertError(get(null, AUTH + "/user_menus"), 401, 40100);
    }

    @Test
    @Order(2)
    void shouldRejectInvalidLoginBody() throws Exception {
        HttpResp resp = post(null, AUTH + "/login", Map.of("username", "", "password", ""));
        assertError(resp, 400, 40000);
    }

    // ------------------------------------------------------------------
    // 登录
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void shouldLoginAndResetFailureCount() throws Exception {
        // 一次失败：计数 +1
        assertError(post(null, AUTH + "/login", Map.of("username", "admin", "password", "wrong")),
                400, 40000);
        assertEquals(1, queryInt("SELECT failed_login_count FROM sys_user WHERE id = " + ADMIN_ID),
                "失败后应计数 1");

        // 成功登录：返回 token 且计数清零
        HttpResp resp = post(null, AUTH + "/login", Map.of("username", "admin", "password", "admin123"));
        assertOk(resp);
        adminToken = json(resp).path("data").path("token").asText();
        assertFalse(adminToken.isBlank(), "应返回会话 token");
        assertEquals(0, queryInt("SELECT failed_login_count FROM sys_user WHERE id = " + ADMIN_ID),
                "成功登录应清零失败计数");
    }

    @Test
    @Order(4)
    void shouldRejectUnknownAccount() throws Exception {
        HttpResp resp = post(null, AUTH + "/login", Map.of("username", "no_such_user", "password", "x"));
        assertError(resp, 400, 40000);
    }

    @Test
    @Order(5)
    void shouldRejectDisabledAccount() throws Exception {
        jdbcTemplate.update("UPDATE sys_user SET enabled = 0 WHERE id = ?", ADMIN_ID);
        try {
            HttpResp resp = post(null, AUTH + "/login", Map.of("username", "admin", "password", "admin123"));
            assertError(resp, 400, 40000);
        } finally {
            jdbcTemplate.update("UPDATE sys_user SET enabled = 1 WHERE id = ?", ADMIN_ID);
        }
    }

    // ------------------------------------------------------------------
    // 菜单树
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void shouldReturnUserMenusTree() throws Exception {
        HttpResp resp = get(adminToken, AUTH + "/user_menus");
        assertOk(resp);
        JsonNode data = json(resp).path("data");

        assertEquals("admin", data.path("userInfo").path("username").asText());
        assertTrue(containsText(data.path("roles"), "ADMIN"), "角色应含 ADMIN");

        // 测试自造菜单：DIRECTORY→M / PAGE→C / ACTION→F
        JsonNode root = findByMenuId(data.path("menus"), String.valueOf(MENU_ROOT_ID));
        assertNotNull(root, "树中应含测试目录节点");
        assertEquals("M", root.path("menuType").asText());
        assertEquals("/it_root", root.path("path").asText());
        JsonNode page = findByMenuId(root.path("children"), String.valueOf(MENU_PAGE_ID));
        assertNotNull(page, "目录下应含测试页面节点");
        assertEquals("C", page.path("menuType").asText());
        JsonNode action = findByMenuId(page.path("children"), String.valueOf(MENU_ACTION_ID));
        assertNotNull(action, "页面下应含测试动作节点");
        assertEquals("F", action.path("menuType").asText());

        // 种子 /admin 根（已执行种子脚本时）：管理端应含首页
        JsonNode adminRoot = findByPath(data.path("menus"), "/admin");
        if (adminRoot != null) {
            assertNotNull(findByPath(adminRoot.path("children"), "/admin/home"), "管理端应含首页");
        }
    }

    // ------------------------------------------------------------------
    // 注销与锁定
    // ------------------------------------------------------------------

    @Test
    @Order(7)
    void shouldLogoutAndInvalidateToken() throws Exception {
        assertOk(post(adminToken, AUTH + "/logout", null));
        assertError(get(adminToken, AUTH + "/user_menus"), 401, 40100);
        // 幂等：未登录注销同样成功
        assertOk(post(null, AUTH + "/logout", null));
        // 重新登录，供后续用例
        HttpResp resp = post(null, AUTH + "/login", Map.of("username", "admin", "password", "admin123"));
        assertOk(resp);
        adminToken = json(resp).path("data").path("token").asText();
    }

    @Test
    @Order(8)
    void shouldLockAfterFiveConsecutiveFailures() throws Exception {
        for (int i = 1; i <= 5; i++) {
            assertError(post(null, AUTH + "/login", Map.of("username", "admin", "password", "wrong_" + i)),
                    400, 40000);
        }
        // 达 5 次：计数清零并写入锁定截止
        assertEquals(0, queryInt("SELECT failed_login_count FROM sys_user WHERE id = " + ADMIN_ID),
                "锁定后应重置计数");
        Timestamp lockedUntil = jdbcTemplate.queryForObject(
                "SELECT locked_until FROM sys_user WHERE id = " + ADMIN_ID, Timestamp.class);
        assertNotNull(lockedUntil, "应写入锁定截止时间");

        // 锁定期内：正确密码也拒绝（409）
        HttpResp resp = post(null, AUTH + "/login", Map.of("username", "admin", "password", "admin123"));
        assertError(resp, 409, 40900);

        // 解除锁定后可正常登录
        jdbcTemplate.update("UPDATE sys_user SET locked_until = NULL WHERE id = ?", ADMIN_ID);
        assertOk(post(null, AUTH + "/login", Map.of("username", "admin", "password", "admin123")));
    }

    // ------------------------------------------------------------------
    // 支撑
    // ------------------------------------------------------------------

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

    /**
     * 密码固定 admin123、清零失败状态（保证可重复执行）；挂载测试菜单
     */
    private void seed() {
        jdbcTemplate.update(
                "UPDATE sys_user SET password_hash = ?, failed_login_count = 0, locked_until = NULL WHERE id = ?",
                ADMIN_PASSWORD_HASH, ADMIN_ID);
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE id BETWEEN ? AND ?",
                ROLE_MENU_ROOT_ID, ROLE_MENU_ACTION_ID);
        // FK 子→父顺序删除（sys_menu 自引用 parent_id）
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id = ?", MENU_ACTION_ID);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id = ?", MENU_PAGE_ID);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE id = ?", MENU_ROOT_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, menu_type, sort_order)
                VALUES (?, NULL, 'IT_MENU_ROOT', '集成测试目录', '/it_root', 'DIRECTORY', 90)
                """, MENU_ROOT_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, route_path, component_key, menu_type, sort_order)
                VALUES (?, ?, 'IT_MENU_PAGE', '集成测试页面', '/it_page', '/it_page', 'PAGE', 91)
                """, MENU_PAGE_ID, MENU_ROOT_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_menu (id, parent_id, menu_code, menu_name, menu_type, sort_order)
                VALUES (?, ?, 'IT_MENU_ACTION', '集成测试动作', 'ACTION', 92)
                """, MENU_ACTION_ID, MENU_PAGE_ID);
        jdbcTemplate.update("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (?, 1, ?)",
                ROLE_MENU_ROOT_ID, MENU_ROOT_ID);
        jdbcTemplate.update("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (?, 1, ?)",
                ROLE_MENU_PAGE_ID, MENU_PAGE_ID);
        jdbcTemplate.update("INSERT INTO sys_role_menu (id, role_id, menu_id) VALUES (?, 1, ?)",
                ROLE_MENU_ACTION_ID, MENU_ACTION_ID);
    }

    private int queryInt(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private boolean containsText(JsonNode array, String text) {
        if (array == null || !array.isArray()) {
            return false;
        }
        for (JsonNode node : array) {
            if (text.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode findByMenuId(JsonNode menus, String menuId) {
        if (menus == null || !menus.isArray()) {
            return null;
        }
        for (JsonNode menu : menus) {
            if (menuId.equals(menu.path("menuId").asText())) {
                return menu;
            }
            JsonNode hit = findByMenuId(menu.path("children"), menuId);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private JsonNode findByPath(JsonNode menus, String path) {
        if (menus == null || !menus.isArray()) {
            return null;
        }
        for (JsonNode menu : menus) {
            if (path.equals(menu.path("path").asText())) {
                return menu;
            }
            JsonNode hit = findByPath(menu.path("children"), path);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private record HttpResp(int status, String body) {
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
}
