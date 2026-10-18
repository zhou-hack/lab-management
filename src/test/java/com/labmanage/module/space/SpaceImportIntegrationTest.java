package com.labmanage.module.space;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.labmanage.common.result.Result;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 场地导入接口全链路集成测试（H2 内存库 + 真实 HTTP + 真实 Excel 解析）。
 * 覆盖：模板下载、CSV 正常导入、导出文件往返、行级校验失败、文件内/库内重复、
 * Excel(.xlsx) 导入、非法格式拒收、403 权限、大批量分批插入。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:space_import_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.encoding=UTF-8",
        "spring.sql.init.schema-locations=classpath:sql/space_schema_h2.sql",
        "spring.sql.init.data-locations=classpath:sql/space_data_h2.sql"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SpaceImportIntegrationTest.TestAuthConfig.class)
@Sql(scripts = {
        "classpath:sql/space_schema_h2.sql",
        "classpath:sql/space_data_h2.sql"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
        config = @SqlConfig(encoding = "UTF-8"))
class SpaceImportIntegrationTest {

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

    private static final String TEMPLATE_HEADER =
            "场地名称,类型,位置,容纳人数,开放时段,状态,配套设备,创建时间(上海),更新时间(上海)";

    @MockitoBean
    private StpInterface stpInterface;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private String token;

    @BeforeEach
    void login() throws Exception {
        Mockito.when(stpInterface.getPermissionList(Mockito.any(), Mockito.any()))
                .thenReturn(List.of("space:space:list", "space:space:read", "space:space:create",
                        "space:space:update", "space:space:delete"));
        Mockito.when(stpInterface.getRoleList(Mockito.any(), Mockito.any()))
                .thenReturn(List.of("ADMIN"));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/test/login"))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        token = objectMapper.readTree(response.body()).path("data").asText();
        assertThat(token).isNotBlank();
    }

    // --------------------------------------------------------------- 辅助

    private String base() {
        return "http://127.0.0.1:" + port;
    }

    private byte[] multipartBody(String boundary, String filename, String contentType, byte[] content) {
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = tail.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headBytes.length + content.length + tailBytes.length];
        System.arraycopy(headBytes, 0, body, 0, headBytes.length);
        System.arraycopy(content, 0, body, headBytes.length, content.length);
        System.arraycopy(tailBytes, 0, body, headBytes.length + content.length, tailBytes.length);
        return body;
    }

    private JsonNode upload(String filename, String contentType, String content) throws Exception {
        return upload(filename, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode upload(String filename, String contentType, byte[] content) throws Exception {
        String boundary = "----LabImportBoundary" + System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/v1/space/spaces/import"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        multipartBody(boundary, filename, contentType, content)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(200);
        return body;
    }

    private int uploadStatus(String filename, String contentType, byte[] content) throws Exception {
        String boundary = "----LabImportBoundary" + System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/v1/space/spaces/import"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(
                        multipartBody(boundary, filename, contentType, content)))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private byte[] xlsx(String[][] rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("场地");
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // --------------------------------------------------------------- 用例

    @Test
    @DisplayName("模板下载：UTF-8 BOM + 与导出一致的 9 列表头")
    void template_headersMatchExport() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/v1/space/spaces/import/template"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        String text = new String(response.body(), StandardCharsets.UTF_8);
        assertThat(text.charAt(0)).isEqualTo('\ufeff');
        assertThat(text.replace("\ufeff", "").trim()).isEqualTo(TEMPLATE_HEADER);
    }

    @Test
    @DisplayName("CSV 正常导入：2 条新场地全部成功并可在分页接口查到")
    void csvImport_validRows_allSuccess() throws Exception {
        String csv = TEMPLATE_HEADER + "\r\n"
                + "E101 导入测试实训室,实训室,E 楼一层 E101,30,08:00~20:00,维护中,投影仪 1 台,,\r\n"
                + "E102 导入测试教室,教室,E 楼一层 E102,80,,,讲台 1 位,,\r\n";
        JsonNode body = upload("spaces.csv", "text/csv", csv);

        assertThat(body.path("code").asInt()).isZero();
        JsonNode data = body.path("data");
        assertThat(data.path("total").asInt()).isEqualTo(2);
        assertThat(data.path("successCount").asInt()).isEqualTo(2);
        assertThat(data.path("failedCount").asInt()).isZero();

        JsonNode page = objectMapper.readTree(get("/api/v1/space/spaces?pageNum=1&pageSize=100"));
        assertThat(page.path("data").path("total").asInt()).isEqualTo(5);
        JsonNode stats = objectMapper.readTree(get("/api/v1/space/spaces/stats"));
        // 种子 1 条维护中 + 新导入 1 条维护中
        assertThat(stats.path("data").path("maintaining").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("导出文件往返：导出 CSV 原样导入，全部判重失败，零新增")
    void exportRoundTrip_allRowsRejectedAsDuplicate() throws Exception {
        // 先取导出文件（含种子 3 条；状态为导出中文「关闭」，时段为 08:00~21:00）
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base() + "/api/v1/space/spaces/export"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        byte[] exportBytes = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();

        JsonNode body = upload("spaces_export.csv", "text/csv", exportBytes);
        JsonNode data = body.path("data");
        assertThat(data.path("total").asInt()).isEqualTo(3);
        assertThat(data.path("successCount").asInt()).isZero();
        assertThat(data.path("failedCount").asInt()).isEqualTo(3);
        for (JsonNode f : data.path("failures")) {
            assertThat(f.path("reason").asText()).contains("已存在");
        }

        JsonNode page = objectMapper.readTree(get("/api/v1/space/spaces?pageNum=1&pageSize=10"));
        assertThat(page.path("data").path("total").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("行级校验：缺名称/非法类型/非数字容量/时段非法/文件内重复/撞已删除名称逐条失败，合法行仍入库")
    void csvImport_invalidRows_collectReasonsButValidRowInserted() throws Exception {
        String csv = TEMPLATE_HEADER + "\r\n"
                // 第 2 行：唯一合法行（默认时段/状态走缺省）
                + "F101 混合校验实训室,实训室,F 楼,32,,,,\r\n"
                // 第 3 行：名称为空
                + ",实训室,F 楼,30,08:00~20:00,,\r\n"
                // 第 4 行：类型非法 + 容量非数字 + 时段只有一个时间
                + "F102 坏数据,机房,F 楼,abc,08:00,,\r\n"
                // 第 5 行：与第 2 行文件内重复
                + "F101 混合校验实训室,教室,F 楼,40,08:00~20:00,可用,,\r\n"
                // 第 6 行：撞上逻辑删除的种子名称（删除后不可复用）
                + "D501 已拆除旧实训室,实训室,F 楼,10,08:00~20:00,,\r\n";
        JsonNode body = upload("mixed.csv", "text/csv", csv);

        JsonNode data = body.path("data");
        assertThat(data.path("total").asInt()).isEqualTo(5);
        assertThat(data.path("successCount").asInt()).isEqualTo(1);
        assertThat(data.path("failedCount").asInt()).isEqualTo(4);

        assertThat(data.path("failures").get(0).path("rowNum").asInt()).isEqualTo(3);
        assertThat(data.path("failures").get(1).path("rowNum").asInt()).isEqualTo(4);
        assertThat(data.path("failures").get(2).path("rowNum").asInt()).isEqualTo(5);
        assertThat(data.path("failures").get(3).path("rowNum").asInt()).isEqualTo(6);

        assertThat(data.path("failures").get(0).path("reason").asText()).contains("场地名称不能为空");
        String row4Reason = data.path("failures").get(1).path("reason").asText();
        assertThat(row4Reason).contains("场地类型只能是「教室」或「实训室」")
                .contains("容纳人数必须为正整数")
                .contains("开放时段格式须为");
        assertThat(data.path("failures").get(2).path("reason").asText()).contains("与文件内第 2 行");
        assertThat(data.path("failures").get(3).path("reason").asText()).contains("已存在");

        // 失败明细保留原始单元格，供前端导出修正文件
        assertThat(data.path("failures").get(1).path("raw").path("容纳人数").asText()).isEqualTo("abc");
    }

    @Test
    @DisplayName("Excel(.xlsx) 导入：真实工作簿解析，2 条成功")
    void xlsxImport_validWorkbook_success() throws Exception {
        byte[] workbook = xlsx(new String[][]{
                {TEMPLATE_HEADER.split(",")[0], TEMPLATE_HEADER.split(",")[1],
                        TEMPLATE_HEADER.split(",")[2], TEMPLATE_HEADER.split(",")[3],
                        TEMPLATE_HEADER.split(",")[4], TEMPLATE_HEADER.split(",")[5]},
                {"G201 Excel实训室", "实训室", "G 楼二层 G201", "36", "09:00~21:30", "可用"},
                {"G202 Excel教室", "教室", "G 楼二层 G202", "72", "", "停用"}
        });
        JsonNode body = upload("spaces.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        JsonNode data = body.path("data");
        assertThat(data.path("total").asInt()).isEqualTo(2);
        assertThat(data.path("successCount").asInt()).isEqualTo(2);
        assertThat(data.path("failedCount").asInt()).isZero();

        JsonNode page = objectMapper.readTree(get("/api/v1/space/spaces?labName=G202"));
        JsonNode record = page.path("data").path("records").get(0);
        assertThat(record.path("status").asText()).isEqualTo("CLOSED");
        assertThat(record.path("openStart").asText()).isEqualTo("08:00");
        assertThat(record.path("openEnd").asText()).isEqualTo("21:00");
    }

    @Test
    @DisplayName("非法格式：非 CSV/Excel 文件返回 40000 拒收，不产生任何写入")
    void illegalFileType_rejected() throws Exception {
        int status = uploadStatus("notes.txt", "text/plain",
                "这是一份与场地无关的文本文件".getBytes(StandardCharsets.UTF_8));
        assertThat(status).isEqualTo(400);

        JsonNode page = objectMapper.readTree(get("/api/v1/space/spaces?pageNum=1&pageSize=10"));
        assertThat(page.path("data").path("total").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("权限控制：无 space:space:create 权限调用导入返回 403")
    void importWithoutCreatePermission_forbidden() throws Exception {
        Mockito.reset(stpInterface);
        Mockito.when(stpInterface.getPermissionList(Mockito.any(), Mockito.any()))
                .thenReturn(List.of("space:space:list", "space:space:read"));
        Mockito.when(stpInterface.getRoleList(Mockito.any(), Mockito.any()))
                .thenReturn(List.of("TEACHER"));

        String csv = TEMPLATE_HEADER + "\r\nH301 越权实训室,实训室,H 楼,20,,,,\r\n";
        int status = uploadStatus("spaces.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(status).isEqualTo(403);
    }

    @Test
    @DisplayName("大批量：200 行一次性导入全部成功（分批插入路径）")
    void largeBatch_allInserted() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(TEMPLATE_HEADER).append("\r\n");
        for (int i = 1; i <= 200; i++) {
            String code = String.format("%03d", i);
            sb.append("BATCH").append(code).append(" 批量实训室,实训室,B 楼 ").append(code)
                    .append(",").append(10 + i).append(",08:00~21:00,可用,,\r\n");
        }
        JsonNode body = upload("batch.csv", "text/csv", sb.toString().getBytes(StandardCharsets.UTF_8));

        JsonNode data = body.path("data");
        assertThat(data.path("total").asInt()).isEqualTo(200);
        assertThat(data.path("successCount").asInt()).isEqualTo(200);
        assertThat(data.path("failedCount").asInt()).isZero();

        JsonNode stats = objectMapper.readTree(get("/api/v1/space/spaces/stats"));
        assertThat(stats.path("data").path("total").asInt()).isEqualTo(203);
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base() + path))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
