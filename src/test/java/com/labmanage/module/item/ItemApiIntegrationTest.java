package com.labmanage.module.item;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.AuthContext;
import com.labmanage.common.security.JwtService;
import com.labmanage.module.document.entity.BizDocument;
import com.labmanage.module.document.mapper.BizDocumentMapper;
import com.labmanage.module.item.entity.FixedAsset;
import com.labmanage.module.item.entity.MaterialClaim;
import com.labmanage.module.item.enums.AssetStatus;
import com.labmanage.module.item.enums.ClaimKind;
import com.labmanage.module.item.enums.ClaimStatus;
import com.labmanage.module.item.mapper.FixedAssetMapper;
import com.labmanage.module.item.mapper.MaterialClaimMapper;
import com.labmanage.module.item.mapper.MaterialMapper;
import com.labmanage.module.item.mapper.StockLocationAdminMapper;
import com.labmanage.module.item.service.ItemMaintenanceService;
import com.labmanage.roles.entity.SysUser;
import com.labmanage.roles.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:item_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.encoding=UTF-8",
        "spring.sql.init.schema-locations=classpath:sql/booking_schema_h2.sql,classpath:sql/item_schema_h2.sql",
        "spring.sql.init.data-locations=classpath:sql/booking_data_h2.sql,classpath:sql/item_data_h2.sql"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ItemApiIntegrationTest.TestAuthConfig.class)
class ItemApiIntegrationTest {

    private static final long LOCATION_A = 101L;
    private static final long LOCATION_B = 102L;
    private static final String BOOKING_DATE = "2026-11-10";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @TestConfiguration(proxyBeanMethods = false)
    static class TestAuthConfig {
        @RestController
        static class TestAuthController {
            private final JwtService jwtService;

            TestAuthController(JwtService jwtService) {
                this.jwtService = jwtService;
            }

            @GetMapping("/test/login")
            Result<String> login(@RequestParam long userId) {
                return Result.success(jwtService.issueAccess(userId, null));
            }
        }
    }

    @MockitoBean(name = "itemClock")
    private Clock itemClock;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockLocationAdminMapper locationAdminMapper;

    @Autowired
    private FixedAssetMapper fixedAssetMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialClaimMapper claimMapper;

    @Autowired
    private BizDocumentMapper bizDocumentMapper;

    @Autowired
    private ItemMaintenanceService maintenanceService;

    @Autowired
    private SysUserMapper userMapper;

    private String adminA;
    private String adminB;
    private String reviewerA;
    private String teacher;

    @BeforeEach
    void setUp() throws Exception {
        setNow("2026-11-10T00:30:00Z");
        adminA = login(11);
        adminB = login(12);
        reviewerA = login(13);
        teacher = login(21);
    }

    @AfterEach
    void clearAuthContext() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("物料、库位、库位管理员与期初入库真实HTTP链路")
    void materialLocationAndInbound() throws Exception {
        String materialId = createMaterial("ITEM_MASTER", "基础物料");
        JsonNode location = post("/api/v1/item/locations", """
                {"locationCode":"ITEM_MASTER_LOC","locationName":"基础测试库",
                 "description":"独立集成测试库位","enabled":1}
                """, adminA);
        assertThat(location.path("code").asInt()).isZero();
        String locationId = location.path("data").path("id").asText();

        JsonNode options = get("/api/v1/item/locations/admin_options?keyword=item_teacher", adminA);
        assertThat(options.path("data")).hasSize(2);
        assertThat(options.path("data").get(0).path("userId").asText()).isEqualTo("21");
        assertThat(options.path("data").get(0).path("realName").asText()).isEqualTo("物料教师");
        JsonNode admins = put("/api/v1/item/locations/" + locationId + "/admins",
                "{\"userIds\":[\"11\",\"21\"]}", adminA);
        assertThat(admins.path("code").asInt()).isZero();
        assertThat(admins.path("data").path("admins")).hasSize(2);

        JsonNode inbound = inbound(materialId, locationId, "MASTER-1", 12, "master-inbound");
        assertThat(inbound.path("code").asInt()).isZero();
        assertThat(inbound.path("data").path("document").path("documentType").asText())
                .isEqualTo("OPENING");
        assertThat(inbound.path("data").path("document").path("documentNo").asText())
                .matches("FSRK20261110\\d{5}");
        assertThat(inbound.path("data").path("document").path("postedAt").asText()).isNotBlank();
        assertBalance(materialId, locationId, "MASTER-1", "12", "12");
    }

    @Test
    @DisplayName("库内位置仅允许所属库位管理员编辑且不可覆盖已有位置")
    void stockBalancePositionUpdateChecksAccessVersionAndDuplicate() throws Exception {
        String materialId = createMaterial("ITEM_POSITION_EDIT", "库内位置编辑耗材");
        assertThat(inbound(materialId, String.valueOf(LOCATION_A), "EDIT-A", 5,
                "position-edit-a").path("code").asInt()).isZero();
        JsonNode balances = get("/api/v1/item/balances?materialName=库内位置编辑耗材&locationId=101", adminA);
        JsonNode original = balances.path("data").path("records").get(0);
        String balanceId = original.path("id").asText();
        int version = original.path("version").asInt();
        String path = "/api/v1/item/balances/" + balanceId + "/position";
        String body = "{\"positionText\":\"EDIT-C\",\"version\":" + version + "}";

        JsonNode teacherForbidden = put(path, body, teacher);
        assertThat(teacherForbidden.path("code").asInt()).isEqualTo(40300);
        JsonNode otherLocationForbidden = put(path, body, adminB);
        assertThat(otherLocationForbidden.path("code").asInt()).isEqualTo(40300);

        JsonNode secondInbound = inbound(materialId, String.valueOf(LOCATION_A), "EDIT-B", 3, "position-edit-b");
        assertThat(secondInbound.path("code").asInt()).as(secondInbound.toString()).isZero();
        JsonNode duplicate = put(path, "{\"positionText\":\"EDIT-B\",\"version\":" + version + "}", adminA);
        assertThat(duplicate.path("code").asInt()).isEqualTo(40900);

        JsonNode updated = put(path, body, adminA);
        assertThat(updated.path("code").asInt()).isZero();
        JsonNode changedBalances = get("/api/v1/item/balances?materialName=库内位置编辑耗材&locationId=101", adminA);
        JsonNode changed = recordWithId(changedBalances.path("data").path("records"), balanceId);
        assertThat(changed.path("positionText").asText()).isEqualTo("EDIT-C");
        assertThat(changed.path("ownedQuantity").decimalValue()).isEqualByComparingTo("5");
        assertThat(changed.path("availableQuantity").decimalValue()).isEqualByComparingTo("5");
        assertThat(changed.path("version").asInt()).isEqualTo(version + 1);
        assertThat(changedBalances.path("data").path("records")).hasSize(2);
    }

    @Test
    @DisplayName("申领库位轻量选项允许教师查询且学生无权限")
    void claimLocationOptionsAllowTeacherWithoutAdminPermission() throws Exception {
        JsonNode teacherOptions = get("/api/v1/item/locations/options?keyword=ITEM_A", teacher);
        assertThat(teacherOptions.path("code").asInt()).isZero();
        assertThat(teacherOptions.path("data")).hasSize(1);
        assertThat(teacherOptions.path("data").get(0).path("id").asText()).isEqualTo("101");
        assertThat(teacherOptions.path("data").get(0).path("locationName").asText()).isEqualTo("物料甲库");
        assertThat(teacherOptions.path("data").get(0).has("admins")).isFalse();

        assertThat(get("/api/v1/item/locations/options?keyword=ITEM_A", adminA).path("code").asInt()).isZero();
        assertThat(get("/api/v1/item/locations/options", login(22)).path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("首页待办仅返回当前管理员库位的待审核申领")
    void managedClaimPageOnlyReturnsCurrentAdminLocations() throws Exception {
        String materialId = createMaterial("ITEM_HOME_MANAGED", "首页待办测试耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "HOME-1", 2, "home-managed-stock");
        JsonNode root = createClaimDetail(materialId, 1, LOCATION_A, "home-managed-claim");
        String rootId = root.path("id").asText();
        String childId = childForLocation(root, LOCATION_A).path("id").asText();
        String query = "/api/v1/item/admin/claims?status=PENDING&parentId=" + rootId;

        JsonNode adminA = get(query + "&managedOnly=true", this.adminA);
        assertThat(adminA.path("code").asInt()).isZero();
        assertThat(adminA.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(adminA.path("data").path("records").get(0).path("id").asText()).isEqualTo(childId);
        assertThat(adminA.path("data").path("records").get(0).path("claimKind").asText()).isEqualTo("CHILD");
        assertThat(get(query + "&managedOnly=true", reviewerA)
                .path("data").path("total").asInt()).isEqualTo(1);
        assertThat(get(query + "&managedOnly=true", adminB)
                .path("data").path("total").asInt()).isZero();
        assertThat(get(query, adminB).path("data").path("total").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("库存单据按业务链ID精确筛选")
    void documentsFilterByBusinessChainId() throws Exception {
        String materialId = createMaterial("ITEM_DOCUMENT_CHAIN", "业务链筛选测试耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "CHAIN-1", 2, "document-chain-stock");
        JsonNode root = createClaimDetail(materialId, 1, LOCATION_A, "document-chain-claim");
        String claimId = root.path("id").asText();
        String childId = childForLocation(root, LOCATION_A).path("id").asText();
        assertThat(approve(childId, 1, adminA).path("code").asInt()).isZero();
        setNow("2026-11-10T05:45:00Z");
        String claimLineId = get("/api/v1/item/admin/claims/" + childId, adminA)
                .path("data").path("lines").get(0).path("id").asText();
        JsonNode issued = post("/api/v1/item/admin/claims/" + childId + "/issues", """
                {"locationId":"101","idempotencyKey":"document-chain-issue",
                 "lines":[{"claimLineId":"%s","quantity":1,"positionText":"CHAIN-1"}]}
                """.formatted(claimLineId), adminA);
        assertThat(issued.path("code").asInt()).isZero();
        JsonNode rootAfterIssue = get("/api/v1/item/admin/claims/" + claimId, adminA).path("data");
        assertThat(rootAfterIssue.path("status").asText()).isEqualTo("CLOSED");
        assertThat(rootAfterIssue.path("lines").get(0).path("issuedQuantity").decimalValue())
                .isEqualByComparingTo("1");
        String sourceId = issued.path("data").path("document").path("id").asText();
        String sourceLineId = issued.path("data").path("lines").get(0).path("id").asText();
        JsonNode returned = post("/api/v1/item/documents/" + sourceId + "/returns", """
                {"idempotencyKey":"document-chain-return","lines":[{"sourceMaterialDocumentLineId":"%s",
                 "returnQuantity":1,"consumedQuantity":0,"lostQuantity":0,"damagedQuantity":0,
                 "positionText":"CHAIN-1"}]}
                """.formatted(sourceLineId), adminA);
        assertThat(returned.path("code").asInt()).isZero();

        String businessChainId = get("/api/v1/item/admin/claims/" + claimId, adminA)
                .path("data").path("businessChainId").asText();
        String childBusinessChainId = get("/api/v1/item/admin/claims/" + childId, adminA)
                .path("data").path("businessChainId").asText();
        assertThat(businessChainId).matches("ALL\\d{8}\\d{5}");
        assertThat(childBusinessChainId).matches("ALL\\d{8}\\d{5}").isEqualTo(businessChainId);
        assertThat(issued.path("data").path("document").path("documentNo").asText()).matches("CK\\d{8}\\d{5}");
        assertThat(returned.path("data").path("document").path("documentNo").asText()).matches("RK\\d{8}\\d{5}");

        JsonNode filteredByExternal = get("/api/v1/item/documents?businessChainId=" + businessChainId, adminA);
        assertThat(filteredByExternal.path("code").asInt()).isZero();
        assertThat(filteredByExternal.path("data").path("total").asInt()).isEqualTo(2);
        for (JsonNode record : filteredByExternal.path("data").path("records")) {
            assertThat(record.path("businessChainId").asText()).isEqualTo(childBusinessChainId);
        }
        JsonNode filteredByInternal = get("/api/v1/item/documents?businessChainId=" + claimId, adminA);
        assertThat(filteredByInternal.path("data").path("total").asInt()).isEqualTo(2);
        assertThat(filteredByInternal.path("data").path("records").get(0).path("businessChainId").asText())
                .isEqualTo(childBusinessChainId);
        assertThat(get("/api/v1/item/documents?businessChainId=1", adminA)
                .path("data").path("total").asInt()).isZero();

        JsonNode chain = get("/api/v1/item/claims/" + claimId + "/documents", adminA);
        assertThat(chain.path("code").asInt()).isZero();
        assertThat(chain.path("data").path("businessChainId").asText()).isEqualTo(businessChainId);
        assertThat(chain.path("data").path("documents").size()).isEqualTo(2);
        assertThat(chain.path("data").path("documents").get(0).path("document").path("id").asText())
                .isEqualTo(sourceId);
        assertThat(chain.path("data").path("documents").get(0).path("document").path("businessScene").asText())
                .isEqualTo("CLAIM_ISSUE");
        assertThat(chain.path("data").path("documents").get(1).path("document").path("id").asText())
                .isEqualTo(returned.path("data").path("document").path("id").asText());
        assertThat(chain.path("data").path("documents").get(1).path("document").path("businessScene").asText())
                .isEqualTo("CLAIM_RETURN");
    }

    @Test
    @DisplayName("历史申领保留ITC单号和数值业务链ID")
    void legacyClaimKeepsOriginalDocumentAndBusinessChainIds() throws Exception {
        String materialId = createMaterial("ITEM_LEGACY_IDS", "历史编号兼容耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "LEGACY-1", 1, "legacy-id-stock");
        String claimId = createClaim(materialId, 1, LOCATION_A, "legacy-id-claim");
        MaterialClaim claim = claimMapper.selectById(Long.valueOf(claimId));
        claimMapper.update(null, Wrappers.<MaterialClaim>lambdaUpdate()
                .eq(MaterialClaim::getId, claim.getId())
                .set(MaterialClaim::getClaimKind, ClaimKind.STANDALONE)
                .set(MaterialClaim::getBusinessChainNo, null));
        BizDocument shared = bizDocumentMapper.selectById(claim.getBizDocumentId());
        shared.setDocumentNo("ITC" + claimId);
        bizDocumentMapper.updateById(shared);

        JsonNode detail = get("/api/v1/item/admin/claims/" + claimId, adminA).path("data");
        assertThat(detail.path("claimKind").asText()).isEqualTo("STANDALONE");
        assertThat(detail.path("documentNo").asText()).isEqualTo("ITC" + claimId);
        assertThat(detail.path("businessChainId").asText()).isEqualTo(claimId);
        assertThat(detail.path("bizDocumentId").asText()).isEqualTo(shared.getId().toString());
    }

    @Test
    @DisplayName("申领幂等重放保留原SL单号和ALL业务链号")
    void claimCreateReplayKeepsOriginalNumbers() throws Exception {
        String materialId = createMaterial("ITEM_CLAIM_REPLAY", "申领编号重放耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "REPLAY-1", 1, "claim-replay-stock");
        String requestBody = claimBody(materialId, 1, LOCATION_A, null, "编号重放申领", "claim-number-replay");
        JsonNode first = post("/api/v1/item/claims", requestBody, teacher).path("data");
        long claimCountAfterFirst = claimMapper.selectCount(null);
        JsonNode replay = post("/api/v1/item/claims", requestBody, teacher).path("data");

        assertThat(replay.path("id").asText()).isEqualTo(first.path("id").asText());
        assertThat(replay.path("documentNo").asText()).isEqualTo(first.path("documentNo").asText());
        assertThat(replay.path("businessChainId").asText()).isEqualTo(first.path("businessChainId").asText());
        assertThat(replay.path("bizDocumentId").asText()).isEqualTo(first.path("bizDocumentId").asText());
        assertThat(replay.path("children")).isEqualTo(first.path("children"));
        assertThat(claimMapper.selectCount(null)).isEqualTo(claimCountAfterFirst);
    }

    @Test
    @DisplayName("单库位申领仍生成ROOT与一个CHILD且申请人和管理端分页各归其类")
    void singleLocationClaimCreatesRootAndOneChild() throws Exception {
        String materialId = createMaterial("ITEM_SINGLE_LOCATION", "单库位父子申领耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "SINGLE-1", 3, "single-location-stock");

        JsonNode root = createClaimDetail(materialId, 2, LOCATION_A, "single-location-claim");
        String rootId = root.path("id").asText();
        assertThat(root.path("children")).hasSize(1);
        JsonNode child = childForLocation(root, LOCATION_A);
        String childId = child.path("id").asText();
        assertThat(child.path("documentNo").asText()).isEqualTo(root.path("documentNo").asText() + "1");
        assertThat(child.path("businessChainId").asText()).isEqualTo(root.path("businessChainId").asText());
        assertThat(child.path("status").asText()).isEqualTo("PENDING");
        assertThat(child.path("revisionNo").asInt()).isEqualTo(1);

        JsonNode adminRoot = get("/api/v1/item/admin/claims/" + rootId, adminA).path("data");
        assertThat(adminRoot.path("canReview").asBoolean()).isFalse();
        assertThat(adminRoot.path("canIssue").asBoolean()).isFalse();
        JsonNode mine = get("/api/v1/item/claims?status=PENDING&pageSize=100", teacher);
        assertThat(mine.path("code").asInt()).as(mine.toString()).isZero();
        assertThat(recordWithId(mine.path("data").path("records"), rootId)).isNotNull();
        assertThat(recordWithId(mine.path("data").path("records"), childId)).isNull();
        JsonNode adminPage = get("/api/v1/item/admin/claims?status=PENDING&parentId=" + rootId, adminA);
        assertThat(adminPage.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(recordWithId(adminPage.path("data").path("records"), childId)).isNotNull();
        assertThat(recordWithId(adminPage.path("data").path("records"), rootId)).isNull();
    }

    @Test
    @DisplayName("多库位每库生成一个CHILD且数量守恒，单库批准不影响兄弟子单")
    void multiLocationChildrenConserveQuantitiesAndReserveIndependently() throws Exception {
        String materialId = createMaterial("ITEM_MULTI_LOCATION", "多库位数量守恒耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "MULTI-A", 10, "multi-location-stock-a");
        JsonNode inboundB = post("/api/v1/item/documents/opening", """
                {"idempotencyKey":"multi-location-stock-b","lines":[{"materialId":"%s","locationId":"102",
                 "positionText":"MULTI-B","quantity":10}]}
                """.formatted(materialId), adminB);
        assertThat(inboundB.path("code").asInt()).isZero();

        JsonNode created = post("/api/v1/item/claims", """
                {"purpose":"多库位数量守恒","bookingDate":"2026-11-10","startPeriodId":"5","endPeriodId":"5",
                 "idempotencyKey":"multi-location-conservation",
                 "lines":[{"materialId":"%s","requestedQuantity":6}],
                 "allocations":[{"materialId":"%s","locationId":"101","plannedQuantity":2},
                                {"materialId":"%s","locationId":"102","plannedQuantity":4}]}
                """.formatted(materialId, materialId, materialId), teacher);
        assertThat(created.path("code").asInt()).isZero();
        JsonNode root = created.path("data");
        assertThat(root.path("claimKind").asText()).isEqualTo("ROOT");
        assertThat(root.path("children")).hasSize(2);
        assertThat(root.path("lines").get(0).path("requestedQuantity").decimalValue())
                .isEqualByComparingTo("6");
        JsonNode childA = childForLocation(root, LOCATION_A);
        JsonNode childB = childForLocation(root, LOCATION_B);
        assertThat(childA.path("documentNo").asText()).isEqualTo(root.path("documentNo").asText() + "1");
        assertThat(childB.path("documentNo").asText()).isEqualTo(root.path("documentNo").asText() + "2");
        assertThat(childA.path("businessChainId").asText()).isEqualTo(root.path("businessChainId").asText());
        assertThat(childB.path("businessChainId").asText()).isEqualTo(root.path("businessChainId").asText());
        assertThat(childA.path("id").asText()).isNotEqualTo(childB.path("id").asText());

        JsonNode childADetail = get("/api/v1/item/admin/claims/" + childA.path("id").asText(), adminA)
                .path("data");
        JsonNode childBDetail = get("/api/v1/item/admin/claims/" + childB.path("id").asText(), adminB)
                .path("data");
        assertThat(childADetail.path("lines")).hasSize(1);
        assertThat(childBDetail.path("lines")).hasSize(1);
        BigDecimal childRequestedTotal = childADetail.path("lines").get(0).path("requestedQuantity").decimalValue()
                .add(childBDetail.path("lines").get(0).path("requestedQuantity").decimalValue());
        assertThat(childADetail.path("lines").get(0).path("requestedQuantity").decimalValue())
                .isEqualByComparingTo("2");
        assertThat(childBDetail.path("lines").get(0).path("requestedQuantity").decimalValue())
                .isEqualByComparingTo("4");
        assertThat(childRequestedTotal).isEqualByComparingTo("6");
        assertThat(childADetail.path("allocations").get(0).path("locationId").asText()).isEqualTo("101");
        assertThat(childBDetail.path("allocations").get(0).path("locationId").asText()).isEqualTo("102");

        JsonNode approvedA = approve(childA.path("id").asText(), 1, adminA);
        assertThat(approvedA.path("code").asInt()).isZero();
        assertThat(approvedA.path("data").path("reservations")).hasSize(1);
        assertThat(approvedA.path("data").path("reservations").get(0).path("locationId").asText()).isEqualTo("101");
        assertThat(approvedA.path("data").path("reservations").get(0).path("reservedQuantity").decimalValue())
                .isEqualByComparingTo("2");
        JsonNode rootAfterApproval = get("/api/v1/item/claims/" + root.path("id").asText(), teacher).path("data");
        assertThat(rootAfterApproval.path("status").asText()).isEqualTo("PENDING");
        assertThat(rootAfterApproval.path("reservations")).hasSize(1);
        assertThat(childForLocation(rootAfterApproval, LOCATION_A).path("status").asText()).isEqualTo("APPROVED");
        assertThat(childForLocation(rootAfterApproval, LOCATION_B).path("status").asText()).isEqualTo("PENDING");
        JsonNode childBAfterApproval = get("/api/v1/item/admin/claims/" + childB.path("id").asText(), adminB)
                .path("data");
        assertThat(childBAfterApproval.path("status").asText()).isEqualTo("PENDING");
        assertThat(childBAfterApproval.path("reservations")).isEmpty();
        assertBalance(materialId, String.valueOf(LOCATION_A), "MULTI-A", "10", "10");
        assertBalance(materialId, String.valueOf(LOCATION_B), "MULTI-B", "10", "10");
    }

    @Test
    @DisplayName("跳选第1、2、4节仅保留一张总单并按两个实际段预占和实发")
    void noncontiguousPeriodsReserveOnlySelectedSegmentsAndIssueOnce() throws Exception {
        String materialId = createMaterial("ITEM_CLAIM_SEGMENTS", "跳选节次申领耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "SEGMENT-1", 10, "segment-stock");
        String selectionJson = "\"startPeriodId\":\"1\",\"endPeriodId\":\"4\",\"periodIds\":[\"4\",\"1\",\"2\",\"2\"]";

        JsonNode preview = post("/api/v1/item/claims/preview", """
                {"bookingDate":"2026-11-10",%s,
                 "lines":[{"materialId":"%s","requestedQuantity":2}],
                 "allocations":[{"materialId":"%s","locationId":"101","plannedQuantity":2}]}
                """.formatted(selectionJson, materialId, materialId), teacher);
        assertThat(preview.path("code").asInt()).as(preview.toString()).isZero();
        JsonNode previewSegments = preview.path("data").path("bookingSnapshot").path("segments");
        assertThat(previewSegments).hasSize(2);
        assertThat(previewSegments.get(0).path("startPeriodId").asText()).isEqualTo("1");
        assertThat(previewSegments.get(0).path("endPeriodId").asText()).isEqualTo("2");
        assertThat(previewSegments.get(1).path("startPeriodId").asText()).isEqualTo("4");
        assertThat(previewSegments.get(1).path("endPeriodId").asText()).isEqualTo("4");

        JsonNode created = post("/api/v1/item/claims", """
                {"purpose":"跳选非连续节次","bookingDate":"2026-11-10",%s,
                 "idempotencyKey":"noncontiguous-segment-claim",
                 "lines":[{"materialId":"%s","requestedQuantity":2}],
                 "allocations":[{"materialId":"%s","locationId":"101","plannedQuantity":2}]}
                """.formatted(selectionJson, materialId, materialId), teacher);
        assertThat(created.path("code").asInt()).as(created.toString()).isZero();
        JsonNode root = created.path("data");
        assertThat(root.path("claimKind").asText()).isEqualTo("ROOT");
        assertThat(root.path("children")).hasSize(1);
        assertThat(root.path("segments")).hasSize(2);
        String childId = childForLocation(root, LOCATION_A).path("id").asText();
        JsonNode child = get("/api/v1/item/admin/claims/" + childId, adminA).path("data");
        assertThat(child.path("segments")).hasSize(2);

        JsonNode approved = approve(childId, 1, adminA);
        assertThat(approved.path("code").asInt()).as(approved.toString()).isZero();
        JsonNode reservations = approved.path("data").path("reservations");
        assertThat(reservations).hasSize(2);
        List<String> windows = new ArrayList<>();
        List<String> reservationSegmentIds = new ArrayList<>();
        for (JsonNode reservation : reservations) {
            assertThat(reservation.path("reservedQuantity").decimalValue()).isEqualByComparingTo("2");
            reservationSegmentIds.add(reservation.path("claimSegmentId").asText());
            windows.add(reservation.path("reservationStartAt").asText() + "/"
                    + reservation.path("reservationEndAt").asText());
        }
        assertThat(windows).containsExactlyInAnyOrder(
                "2026-11-10T00:00:00.000Z/2026-11-10T01:40:00.000Z",
                "2026-11-10T02:55:00.000Z/2026-11-10T03:40:00.000Z");
        assertThat(String.join(",", windows)).doesNotContain("2026-11-10T02:00:00.000Z");
        assertThat(reservationSegmentIds).containsExactlyInAnyOrder(
                child.path("segments").get(0).path("id").asText(),
                child.path("segments").get(1).path("id").asText());

        String selectedAvailability = "/api/v1/item/claims/availability?bookingDate=" + BOOKING_DATE
                + "&periodIds=1,2,4&materialName=" + URLEncoder.encode("跳选节次申领耗材", StandardCharsets.UTF_8);
        String unselectedAvailability = "/api/v1/item/claims/availability?bookingDate=" + BOOKING_DATE
                + "&periodIds=3&materialName=" + URLEncoder.encode("跳选节次申领耗材", StandardCharsets.UTF_8);
        assertThat(get(selectedAvailability, teacher).path("data").path("records").get(0)
                .path("availableQuantity").decimalValue()).isEqualByComparingTo("8");
        assertThat(get(unselectedAvailability, teacher).path("data").path("records").get(0)
                .path("availableQuantity").decimalValue()).isEqualByComparingTo("10");

        String claimLineId = child.path("lines").get(0).path("id").asText();
        JsonNode issued = post("/api/v1/item/admin/claims/" + childId + "/issues", """
                {"locationId":"101","idempotencyKey":"noncontiguous-segment-issue",
                 "lines":[{"claimLineId":"%s","quantity":2,"positionText":"SEGMENT-1"}]}
                """.formatted(claimLineId), adminA);
        assertThat(issued.path("code").asInt()).as(issued.toString()).isZero();
        assertBalance(materialId, String.valueOf(LOCATION_A), "SEGMENT-1", "10", "8");

        JsonNode afterIssue = get("/api/v1/item/admin/claims/" + childId, adminA).path("data");
        BigDecimal consumed = BigDecimal.ZERO;
        BigDecimal released = BigDecimal.ZERO;
        for (JsonNode reservation : afterIssue.path("reservations")) {
            consumed = consumed.add(reservation.path("consumedQuantity").decimalValue());
            released = released.add(reservation.path("releasedQuantity").decimalValue());
            assertThat(reservation.path("status").asText()).isEqualTo("CLOSED");
        }
        assertThat(consumed).isEqualByComparingTo("2");
        assertThat(released).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("整组修改移除库位后重新加入复用原子单并递增版本")
    void updateRootReusesRemovedLocationChild() throws Exception {
        String materialId = createMaterial("ITEM_REUSE_CHILD", "移除重入库位耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "REUSE-A", 10, "reuse-child-stock-a");
        JsonNode inboundB = post("/api/v1/item/documents/opening", """
                {"idempotencyKey":"reuse-child-stock-b","lines":[{"materialId":"%s","locationId":"102",
                 "positionText":"REUSE-B","quantity":10}]}
                """.formatted(materialId), adminB);
        assertThat(inboundB.path("code").asInt()).isZero();
        JsonNode created = post("/api/v1/item/claims", """
                {"purpose":"移除重入库位","bookingDate":"2026-11-10","startPeriodId":"5","endPeriodId":"5",
                 "idempotencyKey":"reuse-child-create","lines":[{"materialId":"%s","requestedQuantity":2}],
                 "allocations":[{"materialId":"%s","locationId":"101","plannedQuantity":1},
                                {"materialId":"%s","locationId":"102","plannedQuantity":1}]}
                """.formatted(materialId, materialId, materialId), teacher);
        assertThat(created.path("code").asInt()).isZero();
        String rootId = created.path("data").path("id").asText();
        String childAId = childForLocation(created.path("data"), LOCATION_A).path("id").asText();
        String childBId = childForLocation(created.path("data"), LOCATION_B).path("id").asText();

        JsonNode removed = put("/api/v1/item/claims/" + rootId, """
                {"purpose":"暂时移除乙库","bookingDate":"2026-11-10","startPeriodId":"5","endPeriodId":"5",
                 "revisionNo":1,"lines":[{"materialId":"%s","requestedQuantity":2}],
                 "allocations":[{"materialId":"%s","locationId":"101","plannedQuantity":2}]}
                """.formatted(materialId, materialId), teacher);
        assertThat(removed.path("code").asInt()).as(removed.toString()).isZero();
        assertThat(removed.path("data").path("children")).hasSize(1);
        assertThat(childForLocation(removed.path("data"), LOCATION_A).path("id").asText()).isEqualTo(childAId);

        JsonNode readded = put("/api/v1/item/claims/" + rootId, """
                {"purpose":"重新加入乙库","bookingDate":"2026-11-10","startPeriodId":"5","endPeriodId":"5",
                 "revisionNo":2,"lines":[{"materialId":"%s","requestedQuantity":2}],
                 "allocations":[{"materialId":"%s","locationId":"101","plannedQuantity":1},
                                {"materialId":"%s","locationId":"102","plannedQuantity":1}]}
                """.formatted(materialId, materialId, materialId), teacher);
        assertThat(readded.path("code").asInt()).as(readded.toString()).isZero();
        assertThat(readded.path("data").path("revisionNo").asInt()).isEqualTo(3);
        assertThat(readded.path("data").path("children")).hasSize(2);
        JsonNode childA = childForLocation(readded.path("data"), LOCATION_A);
        JsonNode childB = childForLocation(readded.path("data"), LOCATION_B);
        assertThat(childA.path("id").asText()).isEqualTo(childAId);
        assertThat(childB.path("id").asText()).isEqualTo(childBId);
        assertThat(childA.path("revisionNo").asInt()).isEqualTo(3);
        assertThat(childB.path("revisionNo").asInt()).isEqualTo(3);
        assertThat(childB.path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("同库位双管理员并发审核仅一个成功且不重复预占")
    void concurrentChildReviewSucceedsOnlyOnce() throws Exception {
        String materialId = createMaterial("ITEM_CONCURRENT_REVIEW", "并发审核耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "CONCURRENT-1", 2, "concurrent-review-stock");
        String childId = createChildClaim(materialId, 1, LOCATION_A, "concurrent-review-claim");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<JsonNode> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return approve(childId, 1, adminA);
            });
            Future<JsonNode> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return approve(childId, 1, reviewerA);
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> codes = List.of(
                    first.get(15, TimeUnit.SECONDS).path("code").asInt(),
                    second.get(15, TimeUnit.SECONDS).path("code").asInt());
            assertThat(codes).containsExactlyInAnyOrder(0, 40900);
        } finally {
            executor.shutdownNow();
        }
        JsonNode detail = get("/api/v1/item/admin/claims/" + childId, adminA).path("data");
        assertThat(detail.path("status").asText()).isEqualTo("APPROVED");
        assertThat(detail.path("reservations")).hasSize(1);
        assertThat(detail.path("reservations").get(0).path("reservedQuantity").decimalValue())
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("库位负责人实际变动写一条历史且资产同步，无变化与非法用户不写历史")
    void locationResponsibleHistoryAndAssetCustodians() throws Exception {
        JsonNode location = post("/api/v1/item/locations", """
                {"locationCode":"ITEM_RESPONSIBLE_HISTORY","locationName":"负责人历史测试库",
                 "description":"负责人历史与资产联动","enabled":1}
                """, adminA);
        String locationId = location.path("data").path("id").asText();
        JsonNode changed = put("/api/v1/item/locations/" + locationId + "/admins",
                "{\"userIds\":[\"11\",\"21\"]}", adminA);
        assertThat(changed.path("code").asInt()).isZero();
        assertThat(changed.path("data").path("admins")).hasSize(2);

        JsonNode material = post("/api/v1/item/materials", """
                {"materialType":"FIXED_ASSET","trackMode":"SERIAL","skuCode":"ITEM_RESPONSIBLE_ASSET",
                 "materialName":"负责人联动资产","specification":"历史测试","baseUnit":"台",
                 "quantityMode":"EXACT","enabled":1}
                """, adminA);
        String materialId = material.path("data").path("id").asText();
        JsonNode asset = post("/api/v1/item/assets", """
                {"materialId":"%s","assetCode":"RESPONSIBLE-ASSET-1","locationId":"%s",
                 "positionText":"R-1","documentType":"OPENING","idempotencyKey":"responsible-asset-1"}
                """.formatted(materialId, locationId), adminA);
        assertThat(asset.path("code").asInt()).isZero();
        assertCustodians(asset.path("data"), "11", "21");
        assertThat(asset.path("data").has("custodianUserId")).isFalse();
        assertThat(asset.path("data").has("custodianName")).isFalse();
        JsonNode assetPage = get("/api/v1/item/assets?locationId=" + locationId, adminA);
        assertThat(assetPage.path("data").path("total").asInt()).isEqualTo(1);
        assertCustodians(assetPage.path("data").path("records").get(0), "11", "21");

        assertThat(put("/api/v1/item/locations/" + locationId + "/admins",
                "{\"userIds\":[\"21\",\"11\"]}", adminA).path("code").asInt()).isZero();
        JsonNode invalid = put("/api/v1/item/locations/" + locationId + "/admins",
                "{\"userIds\":[\"11\",\"22\"]}", adminA);
        assertThat(invalid.path("code").asInt()).isEqualTo(40000);

        JsonNode history = get("/api/v1/item/locations/responsible_history?locationId=" + locationId, adminA);
        assertThat(history.path("code").asInt()).isZero();
        assertThat(history.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(history.path("data").path("pageSize").asInt()).isEqualTo(10);
        JsonNode record = history.path("data").path("records").get(0);
        assertThat(record.path("locationId").asText()).isEqualTo(locationId);
        assertThat(record.path("locationCode").asText()).isEqualTo("ITEM_RESPONSIBLE_HISTORY");
        assertThat(record.path("locationName").asText()).isEqualTo("负责人历史测试库");
        assertThat(record.path("beforeAdmins")).isEmpty();
        assertThat(record.path("afterAdmins")).hasSize(2);
        assertThat(record.path("afterAdmins").get(0).path("userId").asText()).isEqualTo("11");
        assertThat(record.path("afterAdmins").get(0).path("realName").asText()).isEqualTo("甲库管理员");
        assertThat(record.path("afterAdmins").get(1).path("userId").asText()).isEqualTo("21");
        assertThat(record.path("afterAdmins").get(1).path("accountName").asText()).isEqualTo("item_teacher");
        assertThat(record.path("operatorId").asText()).isEqualTo("11");
        assertThat(record.path("operatorName").asText()).isEqualTo("甲库管理员");
        assertThat(record.path("occurredAt").asText()).isEqualTo("2026-11-10T00:30:00.000Z");

        HttpResponse<String> forbidden = requestWithoutBody("GET",
                "/api/v1/item/locations/responsible_history?locationId=" + locationId, teacher);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(forbidden.body()).path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("单台资产维修切换仅限所属库位管理员且非法状态被拒绝")
    void assetMaintenanceToggleRequiresLocationAdminAndValidStatus() throws Exception {
        JsonNode material = post("/api/v1/item/materials", """
                {"materialType":"FIXED_ASSET","trackMode":"SERIAL","skuCode":"ITEM_MAINTENANCE_ASSET",
                 "materialName":"维修切换测试资产","specification":"维修测试","baseUnit":"台",
                 "quantityMode":"EXACT","enabled":1}
                """, adminA);
        String materialId = material.path("data").path("id").asText();
        JsonNode created = post("/api/v1/item/assets", """
                {"materialId":"%s","assetCode":"MAINTENANCE-ASSET-1","locationId":"101",
                 "positionText":"M-1","documentType":"OPENING","idempotencyKey":"maintenance-asset-1"}
                """.formatted(materialId), adminA);
        assertThat(created.path("code").asInt()).isZero();
        String assetId = created.path("data").path("id").asText();
        JsonNode locationInventory = get("/api/v1/item/locations/101/inventory", adminA);
        JsonNode inventoryAsset = recordWithId(locationInventory.path("data").path("assets"), assetId);
        assertThat(inventoryAsset.path("baseUnit").asText()).isEqualTo("台");

        JsonNode editForbidden = put("/api/v1/item/assets/" + assetId, """
                {"assetCode":"MAINTENANCE-ASSET-1","price":null,"positionText":"M-1"}
                """, adminB);
        assertThat(editForbidden.path("code").asInt()).isEqualTo(40300);
        HttpResponse<String> forbidden = requestWithoutBody("POST",
                "/api/v1/item/assets/" + assetId + "/maintenance", adminB);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(forbidden.body()).path("code").asInt()).isEqualTo(40300);
        HttpResponse<String> teacherForbidden = requestWithoutBody("POST",
                "/api/v1/item/assets/" + assetId + "/maintenance", teacher);
        assertThat(teacherForbidden.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(teacherForbidden.body()).path("code").asInt()).isEqualTo(40300);
        assertThat(get("/api/v1/item/assets/" + assetId, adminA).path("data").path("status").asText())
                .isEqualTo("IDLE");

        HttpResponse<String> maintenance = requestWithoutBody("POST",
                "/api/v1/item/assets/" + assetId + "/maintenance", adminA);
        assertThat(maintenance.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(maintenance.body()).path("data").path("status").asText())
                .isEqualTo("MAINTENANCE");
        HttpResponse<String> restored = requestWithoutBody("POST",
                "/api/v1/item/assets/" + assetId + "/maintenance", adminA);
        assertThat(restored.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(restored.body()).path("data").path("status").asText())
                .isEqualTo("IDLE");

        FixedAsset asset = fixedAssetMapper.selectById(Long.valueOf(assetId));
        asset.setStatus(AssetStatus.ON_LOAN);
        fixedAssetMapper.updateById(asset);
        HttpResponse<String> conflict = requestWithoutBody("POST",
                "/api/v1/item/assets/" + assetId + "/maintenance", adminA);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(conflict.body()).path("code").asInt()).isEqualTo(40900);
        assertThat(get("/api/v1/item/assets/" + assetId, adminA).path("data").path("status").asText())
                .isEqualTo("ON_LOAN");
    }

    @Test
    @DisplayName("库位负责人历史默认十条分页且仅统计实际集合变化")
    void locationResponsibleHistoryPagination() throws Exception {
        JsonNode location = post("/api/v1/item/locations", """
                {"locationCode":"ITEM_RESPONSIBLE_PAGE","locationName":"负责人历史分页库","enabled":1}
                """, adminA);
        String locationId = location.path("data").path("id").asText();
        for (int index = 1; index <= 11; index++) {
            String userIds = index % 2 == 0 ? "[\"11\",\"21\"]" : "[\"11\"]";
            JsonNode changed = put("/api/v1/item/locations/" + locationId + "/admins",
                    "{\"userIds\":" + userIds + "}", adminA);
            assertThat(changed.path("code").asInt()).isZero();
        }

        JsonNode firstPage = get("/api/v1/item/locations/responsible_history?locationId=" + locationId, adminA);
        assertThat(firstPage.path("data").path("total").asInt()).isEqualTo(11);
        assertThat(firstPage.path("data").path("pageNum").asInt()).isEqualTo(1);
        assertThat(firstPage.path("data").path("pageSize").asInt()).isEqualTo(10);
        assertThat(firstPage.path("data").path("records")).hasSize(10);
        assertThat(firstPage.path("data").path("records").get(0).path("afterAdmins")).hasSize(1);
        JsonNode secondPage = get("/api/v1/item/locations/responsible_history?locationId="
                + locationId + "&pageNum=2", adminA);
        assertThat(secondPage.path("data").path("total").asInt()).isEqualTo(11);
        assertThat(secondPage.path("data").path("pageNum").asInt()).isEqualTo(2);
        assertThat(secondPage.path("data").path("pageSize").asInt()).isEqualTo(10);
        assertThat(secondPage.path("data").path("records")).hasSize(1);
        assertThat(secondPage.path("data").path("records").get(0).path("afterAdmins")).hasSize(1);
    }

    private void assertCustodians(JsonNode asset, String... userIds) {
        assertThat(asset.path("custodians")).hasSize(userIds.length);
        for (int index = 0; index < userIds.length; index++) {
            assertThat(asset.path("custodians").get(index).path("userId").asText()).isEqualTo(userIds[index]);
            assertThat(asset.path("custodians").get(index).path("realName").asText()).isNotBlank();
            assertThat(asset.path("custodians").get(index).path("accountName").asText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("空库位删除后编码可反复复用且创建删除历史保留操作人")
    void locationDeleteAllowsCodeReuseAndRecordsChanges() throws Exception {
        JsonNode created = post("/api/v1/item/locations", """
                {"locationCode":"ITEM_DELETE_EMPTY","locationName":"待删除空库位",
                 "description":"删除链路测试","enabled":1}
                """, adminA);
        String locationId = created.path("data").path("id").asText();
        assertThat(put("/api/v1/item/locations/" + locationId + "/admins",
                "{\"userIds\":[\"11\"]}", adminA).path("code").asInt()).isZero();

        HttpResponse<String> deleted = requestWithoutBody("DELETE", "/api/v1/item/locations/" + locationId, adminA);
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(deleted.body()).path("code").asInt()).isZero();
        var admins = locationAdminMapper.selectAllByLocationIdIncludingDeleted(Long.valueOf(locationId));
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getDeleted()).isZero();
        HttpResponse<String> missing = requestWithoutBody("GET", "/api/v1/item/locations/" + locationId, adminA);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(missing.body()).path("code").asInt()).isEqualTo(40400);
        assertThat(requestWithoutBody("DELETE", "/api/v1/item/locations/" + locationId, adminA)
                .statusCode()).isEqualTo(404);

        JsonNode recreated = post("/api/v1/item/locations", """
                {"locationCode":"ITEM_DELETE_EMPTY","locationName":"第一次同码重建","enabled":1}
                """, adminA);
        assertThat(recreated.path("code").asInt()).isZero();
        String recreatedId = recreated.path("data").path("id").asText();
        assertThat(recreatedId).isNotEqualTo(locationId);
        assertThat(requestWithoutBody("DELETE", "/api/v1/item/locations/" + recreatedId, adminA)
                .statusCode()).isEqualTo(200);
        JsonNode recreatedAgain = post("/api/v1/item/locations", """
                {"locationCode":"ITEM_DELETE_EMPTY","locationName":"第二次同码重建","enabled":1}
                """, adminA);
        assertThat(recreatedAgain.path("code").asInt()).isZero();
        String activeId = recreatedAgain.path("data").path("id").asText();
        assertThat(post("/api/v1/item/locations", """
                {"locationCode":"ITEM_DELETE_EMPTY","locationName":"活跃编码冲突","enabled":1}
                """, adminA).path("code").asInt()).isEqualTo(40900);

        JsonNode history = get("/api/v1/item/locations/change_history?locationId=" + locationId, adminA);
        assertThat(history.path("code").asInt()).isZero();
        assertThat(history.path("data").path("total").asInt()).isEqualTo(2);
        assertThat(history.path("data").path("pageSize").asInt()).isEqualTo(10);
        JsonNode deleteRecord = history.path("data").path("records").get(0);
        assertThat(deleteRecord.path("changeType").asText()).isEqualTo("DELETE");
        assertThat(deleteRecord.path("locationId").asText()).isEqualTo(locationId);
        assertThat(deleteRecord.path("locationCode").asText()).isEqualTo("ITEM_DELETE_EMPTY");
        assertThat(deleteRecord.path("operatorId").asText()).isEqualTo("11");
        assertThat(deleteRecord.path("operatorName").asText()).isEqualTo("甲库管理员");
        assertThat(deleteRecord.path("occurredAt").asText()).isEqualTo("2026-11-10T00:30:00.000Z");
        JsonNode createRecord = history.path("data").path("records").get(1);
        assertThat(createRecord.path("changeType").asText()).isEqualTo("CREATE");
        assertThat(createRecord.path("operatorId").asText()).isEqualTo("11");
        assertThat(createRecord.path("operatorName").asText()).isEqualTo("甲库管理员");
        assertThat(get("/api/v1/item/locations/change_history?locationId=" + recreatedId, adminA)
                .path("data").path("total").asInt()).isEqualTo(2);
        assertThat(get("/api/v1/item/locations/change_history?locationId=" + activeId, adminA)
                .path("data").path("total").asInt()).isEqualTo(1);

        HttpResponse<String> forbidden = requestWithoutBody("GET",
                "/api/v1/item/locations/change_history?locationId=" + locationId, teacher);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(forbidden.body()).path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("库位有耗材保有或有效固定资产禁止删除，清零历史库存后可删除")
    void locationDeleteRequiresEmptyAndPermission() throws Exception {
        String materialId = createMaterial("ITEM_DELETE_USED", "删除校验耗材");
        JsonNode usedLocation = post("/api/v1/item/locations", """
                {"locationCode":"ITEM_DELETE_USED_LOC","locationName":"已有业务库位","enabled":1}
                """, adminA);
        String usedLocationId = usedLocation.path("data").path("id").asText();
        assertThat(put("/api/v1/item/locations/" + usedLocationId + "/admins",
                "{\"userIds\":[\"11\",\"13\"]}", adminA).path("code").asInt()).isZero();
        assertThat(inbound(materialId, usedLocationId, "DELETE-1", 1, "delete-used-inbound")
                .path("code").asInt()).isZero();
        HttpResponse<String> forbidden = requestWithoutBody("DELETE",
                "/api/v1/item/locations/" + usedLocationId, teacher);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(forbidden.body()).path("code").asInt()).isEqualTo(40300);
        HttpResponse<String> conflict = requestWithoutBody("DELETE",
                "/api/v1/item/locations/" + usedLocationId, adminA);
        assertThat(conflict.statusCode()).isEqualTo(409);
        JsonNode conflictBody = objectMapper.readTree(conflict.body());
        assertThat(conflictBody.path("code").asInt()).isEqualTo(40900);
        assertThat(conflictBody.path("msg").asText()).contains("只能停用");
        assertThat(requestWithoutBody("GET", "/api/v1/item/locations/" + usedLocationId, adminA)
                .statusCode()).isEqualTo(200);

        JsonNode adjustment = post("/api/v1/item/documents/adjust", """
                {"locationId":"%s","idempotencyKey":"location-delete-clear-stock",
                 "lines":[{"materialId":"%s","quantity":1,"positionText":"DELETE-1","reason":"清零后删除库位"}]}
                """.formatted(usedLocationId, materialId), adminA);
        assertThat(adjustment.path("code").asInt()).isZero();
        String adjustmentId = adjustment.path("data").path("document").path("id").asText();
        assertThat(post("/api/v1/item/documents/" + adjustmentId + "/review",
                "{\"approve\":true,\"version\":0,\"reason\":\"库存已清零\"}", reviewerA)
                .path("code").asInt()).isZero();
        assertThat(requestWithoutBody("DELETE", "/api/v1/item/locations/" + usedLocationId, adminA)
                .statusCode()).isEqualTo(200);

        JsonNode assetMaterial = post("/api/v1/item/materials", """
                {"materialType":"FIXED_ASSET","trackMode":"SERIAL","skuCode":"ITEM_LOCATION_DELETE_ASSET",
                 "materialName":"库位删除校验资产","specification":"删除测试","baseUnit":"台",
                 "quantityMode":"EXACT","enabled":1}
                """, adminA);
        String assetMaterialId = assetMaterial.path("data").path("id").asText();
        JsonNode assetLocation = post("/api/v1/item/locations", """
                {"locationCode":"ITEM_DELETE_ASSET_LOC","locationName":"有效资产库位","enabled":1}
                """, adminA);
        String assetLocationId = assetLocation.path("data").path("id").asText();
        assertThat(put("/api/v1/item/locations/" + assetLocationId + "/admins",
                "{\"userIds\":[\"11\"]}", adminA).path("code").asInt()).isZero();
        assertThat(post("/api/v1/item/assets", """
                {"materialId":"%s","assetCode":"LOCATION-DELETE-ASSET","locationId":"%s",
                 "documentType":"OPENING","idempotencyKey":"location-delete-asset"}
                """.formatted(assetMaterialId, assetLocationId), adminA).path("code").asInt()).isZero();
        HttpResponse<String> assetConflict = requestWithoutBody("DELETE",
                "/api/v1/item/locations/" + assetLocationId, adminA);
        assertThat(assetConflict.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(assetConflict.body()).path("code").asInt()).isEqualTo(40900);
    }

    @Test
    @DisplayName("耗材SKU编码在创建和编辑时始终置空")
    void consumableSkuIsAlwaysNull() throws Exception {
        JsonNode created = post("/api/v1/item/materials", """
                {"materialType":"CONSUMABLE","trackMode":"QUANTITY","skuCode":"CONSUMABLE_SKU_SHOULD_CLEAR",
                 "materialName":"耗材SKU置空测试","specification":"集成测试","baseUnit":"件",
                 "quantityMode":"EXACT","enabled":1}
                """, adminA);
        assertThat(created.path("code").asInt()).isZero();
        assertThat(created.path("data").path("skuCode").isNull()).isTrue();
        String materialId = created.path("data").path("id").asText();

        JsonNode updated = put("/api/v1/item/materials/" + materialId, """
                {"materialType":"CONSUMABLE","trackMode":"QUANTITY","skuCode":"CONSUMABLE_SKU_STILL_CLEAR",
                 "materialName":"耗材SKU置空测试","specification":"编辑后","baseUnit":"件",
                 "quantityMode":"EXACT","enabled":1}
                """, adminA);
        assertThat(updated.path("code").asInt()).isZero();
        assertThat(updated.path("data").path("skuCode").isNull()).isTrue();
        assertThat(updated.path("data").path("specification").asText()).isEqualTo("编辑后");
    }

    @Test
    @DisplayName("空物料档案可逻辑删除且无权限、详情及SKU复用均受控")
    void emptyMaterialDeleteAndSkuReuseProtection() throws Exception {
        JsonNode material = post("/api/v1/item/materials", """
                {"materialType":"FIXED_ASSET","trackMode":"SERIAL","skuCode":"ITEM_MATERIAL_DELETE_EMPTY",
                 "materialName":"待删除空物料","specification":"集成测试","baseUnit":"台",
                 "quantityMode":"EXACT","enabled":1}
                """, adminA);
        assertThat(material.path("code").asInt()).isZero();
        String materialId = material.path("data").path("id").asText();

        HttpResponse<String> forbidden = requestWithoutBody("DELETE",
                "/api/v1/item/materials/" + materialId, teacher);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(forbidden.body()).path("code").asInt()).isEqualTo(40300);

        HttpResponse<String> deleted = requestWithoutBody("DELETE",
                "/api/v1/item/materials/" + materialId, adminA);
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(deleted.body()).path("code").asInt()).isZero();
        var archived = materialMapper.selectBatchIdsIncludingDeleted(
                java.util.List.of(Long.valueOf(materialId))).get(0);
        assertThat(archived.getDeleted()).isEqualTo(1);
        assertThat(archived.getEnabled()).isZero();

        HttpResponse<String> missing = requestWithoutBody("GET",
                "/api/v1/item/materials/" + materialId, adminA);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readTree(missing.body()).path("code").asInt()).isEqualTo(40400);
        assertThat(requestWithoutBody("DELETE", "/api/v1/item/materials/" + materialId, adminA)
                .statusCode()).isEqualTo(404);
        JsonNode reused = post("/api/v1/item/materials", """
                {"materialType":"FIXED_ASSET","trackMode":"SERIAL","skuCode":"ITEM_MATERIAL_DELETE_EMPTY",
                 "materialName":"同SKU重建","specification":"集成测试","baseUnit":"台",
                 "quantityMode":"EXACT","enabled":1}
                """, adminA);
        assertThat(reused.path("code").asInt()).isEqualTo(40900);
    }

    @Test
    @DisplayName("有耗材保有或有效固定资产的物料档案不可删除")
    void materialDeleteRejectsOwnedConsumableAndEffectiveAsset() throws Exception {
        String consumableId = createMaterial("ITEM_MATERIAL_DELETE_OWNED", "保有耗材禁止删除");
        assertThat(inbound(consumableId, String.valueOf(LOCATION_A), "DELETE-OWNED", 1,
                "material-delete-owned-inbound").path("code").asInt()).isZero();
        HttpResponse<String> consumableConflict = requestWithoutBody("DELETE",
                "/api/v1/item/materials/" + consumableId, adminA);
        assertThat(consumableConflict.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(consumableConflict.body()).path("code").asInt()).isEqualTo(40900);
        assertThat(get("/api/v1/item/materials/" + consumableId, adminA).path("code").asInt()).isZero();

        JsonNode assetMaterial = post("/api/v1/item/materials", """
                {"materialType":"FIXED_ASSET","trackMode":"SERIAL","skuCode":"ITEM_MATERIAL_DELETE_ASSET",
                 "materialName":"有效资产禁止删除","specification":"删除测试","baseUnit":"台",
                 "quantityMode":"EXACT","enabled":1}
                """, adminA);
        assertThat(assetMaterial.path("code").asInt()).isZero();
        String assetMaterialId = assetMaterial.path("data").path("id").asText();
        JsonNode asset = post("/api/v1/item/assets", """
                {"materialId":"%s","assetCode":"DELETE-ACTIVE-ASSET","locationId":"101",
                 "positionText":"DELETE-ASSET","documentType":"OPENING","idempotencyKey":"delete-active-asset"}
                """.formatted(assetMaterialId), adminA);
        assertThat(asset.path("code").asInt()).isZero();
        HttpResponse<String> assetConflict = requestWithoutBody("DELETE",
                "/api/v1/item/materials/" + assetMaterialId, adminA);
        assertThat(assetConflict.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(assetConflict.body()).path("code").asInt()).isEqualTo(40900);
    }

    @Test
    @DisplayName("原移动单仍有在途耗材时物料档案不可删除")
    void materialDeleteRejectsConsumableInOpenTransfer() throws Exception {
        String materialId = createMaterial("ITEM_MATERIAL_DELETE_TRANSIT", "在途耗材禁止删除");
        assertThat(inbound(materialId, String.valueOf(LOCATION_A), "DELETE-TRANSIT", 3,
                "material-delete-transit-inbound").path("code").asInt()).isZero();
        JsonNode sent = post("/api/v1/item/transfers", """
                {"fromLocationId":"101","toLocationId":"102","idempotencyKey":"material-delete-transit-send",
                 "lines":[{"materialId":"%s","quantity":3,"positionText":"DELETE-TRANSIT"}]}
                """.formatted(materialId), adminA);
        assertThat(sent.path("code").asInt()).isZero();
        assertBalance(materialId, String.valueOf(LOCATION_A), "DELETE-TRANSIT", "0", "0");

        HttpResponse<String> conflict = requestWithoutBody("DELETE",
                "/api/v1/item/materials/" + materialId, adminA);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(conflict.body()).path("code").asInt()).isEqualTo(40900);
    }

    @Test
    @DisplayName("零余额物料删除后历史单据流水与方案名称保留且新业务拒绝使用")
    void materialDeleteKeepsHistoricalNamesAndRejectsNewBusiness() throws Exception {
        String materialName = "删除后历史名称耗材";
        String materialId = createMaterial("ITEM_MATERIAL_DELETE_HISTORY", materialName);
        JsonNode opening = inbound(materialId, String.valueOf(LOCATION_A), "DELETE-HISTORY", 2,
                "material-delete-history-inbound");
        assertThat(opening.path("code").asInt()).isZero();
        String openingId = opening.path("data").path("document").path("id").asText();
        JsonNode plan = post("/api/v1/item/plans", """
                {"planName":"删除后历史方案","enabled":1,
                 "lines":[{"materialId":"%s","defaultQuantity":1}]}
                """.formatted(materialId), teacher);
        assertThat(plan.path("code").asInt()).isZero();
        String planId = plan.path("data").path("id").asText();

        JsonNode adjustment = post("/api/v1/item/documents/adjust", """
                {"locationId":"101","idempotencyKey":"material-delete-history-adjust",
                 "lines":[{"materialId":"%s","quantity":2,"positionText":"DELETE-HISTORY","reason":"清空历史测试库存"}]}
                """.formatted(materialId), adminA);
        assertThat(adjustment.path("code").asInt()).isZero();
        String adjustmentId = adjustment.path("data").path("document").path("id").asText();
        assertThat(post("/api/v1/item/documents/" + adjustmentId + "/review",
                "{\"approve\":true,\"version\":0,\"reason\":\"清空后删除档案\"}", reviewerA)
                .path("code").asInt()).isZero();
        assertBalance(materialId, String.valueOf(LOCATION_A), "DELETE-HISTORY", "0", "0");
        assertThat(requestWithoutBody("DELETE", "/api/v1/item/materials/" + materialId, adminA)
                .statusCode()).isEqualTo(200);

        JsonNode historicalDocument = get("/api/v1/item/documents/" + openingId, adminA);
        assertThat(historicalDocument.path("code").asInt()).isZero();
        assertThat(historicalDocument.path("data").path("lines").get(0).path("materialName").asText())
                .isEqualTo(materialName);
        JsonNode movements = get("/api/v1/item/movements?materialName="
                + URLEncoder.encode(materialName, StandardCharsets.UTF_8), adminA);
        assertThat(movements.path("code").asInt()).isZero();
        assertThat(movements.path("data").path("records")).isNotEmpty();
        for (JsonNode movement : movements.path("data").path("records")) {
            assertThat(movement.path("materialName").asText()).isEqualTo(materialName);
        }
        JsonNode historicalPlan = get("/api/v1/item/plans/" + planId, teacher);
        assertThat(historicalPlan.path("code").asInt()).isZero();
        assertThat(historicalPlan.path("data").path("lines").get(0).path("materialName").asText())
                .isEqualTo(materialName);

        assertThat(inbound(materialId, String.valueOf(LOCATION_A), "DELETE-HISTORY", 1,
                "material-delete-history-new-inbound").path("code").asInt()).isEqualTo(40000);
        assertThat(post("/api/v1/item/plans", """
                {"planName":"删除档案新方案","enabled":1,
                 "lines":[{"materialId":"%s","defaultQuantity":1}]}
                """.formatted(materialId), teacher).path("code").asInt()).isEqualTo(40000);
        assertThat(post("/api/v1/item/claims/preview", """
                {"bookingDate":"2026-11-10","startPeriodId":"5","endPeriodId":"5",
                 "lines":[{"materialId":"%s","requestedQuantity":1}],
                 "allocations":[{"materialId":"%s","locationId":"101","plannedQuantity":1}]}
                """.formatted(materialId, materialId), teacher).path("code").asInt()).isEqualTo(40000);
    }

    @Test
    @DisplayName("教师申领、管理员审核、窗口内部分实发、部分归还及逾期原因")
    void claimIssueAndReturnLifecycle() throws Exception {
        String materialId = createMaterial("ITEM_LIFECYCLE", "申领 lifecycle 耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "LIFE-1", 10, "life-inbound");
        String claimId = createChildClaim(materialId, 6, LOCATION_A, "life-claim");
        assertThat(approve(claimId, 1, adminA).path("code").asInt()).isZero();

        setNow("2026-11-10T05:45:00Z");
        JsonNode claim = get("/api/v1/item/admin/claims/" + claimId, adminA);
        String claimLineId = claim.path("data").path("lines").get(0).path("id").asText();
        JsonNode issued = post("/api/v1/item/admin/claims/" + claimId + "/issues", """
                {"locationId":"101","idempotencyKey":"life-issue","remark":"部分实发",
                 "lines":[{"claimLineId":"%s","quantity":4,"positionText":"LIFE-1"}]}
                """.formatted(claimLineId), adminA);
        assertThat(issued.path("code").asInt()).isZero();
        String sourceId = issued.path("data").path("document").path("id").asText();
        String sourceLineId = issued.path("data").path("lines").get(0).path("id").asText();
        assertThat(get("/api/v1/item/admin/claims/" + claimId, adminA)
                .path("data").path("status").asText()).isEqualTo("PARTIALLY_ISSUED");
        assertBalance(materialId, String.valueOf(LOCATION_A), "LIFE-1", "10", "6");

        JsonNode partialReturn = returnQuantity(sourceId, sourceLineId, 2,
                "life-return-part", null);
        assertThat(partialReturn.path("code").asInt()).isZero();
        assertThat(partialReturn.path("data").path("document").path("returnStatus").asText())
                .isEqualTo("ON_TIME");
        assertThat(get("/api/v1/item/documents/" + sourceId, adminA)
                .path("data").path("document").path("processStatus").asText()).isEqualTo("PARTIAL");

        setNow("2026-11-10T16:00:00Z");
        JsonNode missingReason = returnQuantity(sourceId, sourceLineId, 1,
                "life-return-overdue-missing", null);
        assertThat(missingReason.path("code").asInt()).isEqualTo(40000);
        JsonNode overdue = returnQuantity(sourceId, sourceLineId, 1,
                "life-return-overdue", "课程延时，已登记逾期归还");
        assertThat(overdue.path("code").asInt()).isZero();
        assertThat(overdue.path("data").path("document").path("returnStatus").asText())
                .isEqualTo("OVERDUE");
        assertThat(overdue.path("data").path("document").path("overdueReason").asText())
                .isEqualTo("课程延时，已登记逾期归还");
        assertBalance(materialId, String.valueOf(LOCATION_A), "LIFE-1", "10", "9");
    }

    @Test
    @DisplayName("归还消耗免填原因，丢失和损坏仍必须填写原因")
    void returnConsumptionReasonOptionalButLostAndDamagedRequired() throws Exception {
        String materialId = createMaterial("ITEM_RETURN_REASON", "归还核销原因耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "REASON-1", 10, "return-reason-inbound");
        String claimId = createChildClaim(materialId, 6, LOCATION_A, "return-reason-claim");
        assertThat(approve(claimId, 1, adminA).path("code").asInt()).isZero();
        String claimLineId = get("/api/v1/item/admin/claims/" + claimId, adminA)
                .path("data").path("lines").get(0).path("id").asText();
        JsonNode issued = post("/api/v1/item/admin/claims/" + claimId + "/issues", """
                {"locationId":"101","idempotencyKey":"return-reason-issue",
                 "lines":[{"claimLineId":"%s","quantity":6,"positionText":"REASON-1"}]}
                """.formatted(claimLineId), adminA);
        assertThat(issued.path("code").asInt()).isZero();
        String sourceId = issued.path("data").path("document").path("id").asText();
        String sourceLineId = issued.path("data").path("lines").get(0).path("id").asText();

        JsonNode consumed = post("/api/v1/item/documents/" + sourceId + "/returns", """
                {"idempotencyKey":"return-consumed-without-reason","lines":[{"sourceMaterialDocumentLineId":"%s",
                 "returnQuantity":0,"consumedQuantity":2,"lostQuantity":0,"damagedQuantity":0,
                 "positionText":"REASON-1"}]}
                """.formatted(sourceLineId), adminA);
        assertThat(consumed.path("code").asInt()).as(consumed.toString()).isZero();
        assertBalance(materialId, String.valueOf(LOCATION_A), "REASON-1", "8", "4");
        String consumedDocumentNo = consumed.path("data").path("document").path("documentNo").asText();
        JsonNode consumedSettlement = get("/api/v1/item/settlements?documentNo=" + consumedDocumentNo, adminA)
                .path("data").path("records").get(0);
        assertThat(consumedSettlement.path("settlementType").asText()).isEqualTo("CONSUMED");
        assertThat(consumedSettlement.path("reason").asText()).isEqualTo("教学消耗");

        JsonNode lostWithoutReason = post("/api/v1/item/documents/" + sourceId + "/returns", """
                {"idempotencyKey":"return-lost-without-reason","lines":[{"sourceMaterialDocumentLineId":"%s",
                 "returnQuantity":0,"consumedQuantity":0,"lostQuantity":1,"damagedQuantity":0,
                 "positionText":"REASON-1"}]}
                """.formatted(sourceLineId), adminA);
        assertThat(lostWithoutReason.path("code").asInt()).isEqualTo(40000);
        assertThat(lostWithoutReason.path("msg").asText()).contains("丢失原因不能为空");
        JsonNode damagedWithoutReason = post("/api/v1/item/documents/" + sourceId + "/returns", """
                {"idempotencyKey":"return-damaged-without-reason","lines":[{"sourceMaterialDocumentLineId":"%s",
                 "returnQuantity":0,"consumedQuantity":0,"lostQuantity":0,"damagedQuantity":1,
                 "positionText":"REASON-1"}]}
                """.formatted(sourceLineId), adminA);
        assertThat(damagedWithoutReason.path("code").asInt()).isEqualTo(40000);
        assertThat(damagedWithoutReason.path("msg").asText()).contains("损坏原因不能为空");

        JsonNode damaged = post("/api/v1/item/documents/" + sourceId + "/returns", """
                {"idempotencyKey":"return-damaged-with-reason","lines":[{"sourceMaterialDocumentLineId":"%s",
                 "returnQuantity":0,"consumedQuantity":0,"lostQuantity":0,"damagedQuantity":1,
                 "damagedReason":"外壳破损","positionText":"REASON-1"}]}
                """.formatted(sourceLineId), adminA);
        assertThat(damaged.path("code").asInt()).isZero();
        assertBalance(materialId, String.valueOf(LOCATION_A), "REASON-1", "7", "4");
    }

    @Test
    @DisplayName("预约节次结束后当天仍可实发，次日禁止实发")
    void issueThroughoutBookingDayAndRejectNextDay() throws Exception {
        String materialId = createMaterial("ITEM_ISSUE_DAY", "当天实发边界耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "DAY-1", 2, "issue-day-inbound");
        String firstClaimId = createChildClaim(materialId, 1, LOCATION_A, "issue-day-first");
        String secondClaimId = createChildClaim(materialId, 1, LOCATION_A, "issue-day-second");
        assertThat(approve(firstClaimId, 1, adminA).path("code").asInt()).isZero();
        assertThat(approve(secondClaimId, 1, adminA).path("code").asInt()).isZero();
        String firstLineId = get("/api/v1/item/admin/claims/" + firstClaimId, adminA)
                .path("data").path("lines").get(0).path("id").asText();
        String secondLineId = get("/api/v1/item/admin/claims/" + secondClaimId, adminA)
                .path("data").path("lines").get(0).path("id").asText();

        setNow("2026-11-10T15:30:00Z");
        JsonNode firstDetail = get("/api/v1/item/admin/claims/" + firstClaimId, adminA);
        assertThat(firstDetail.path("data").path("status").asText()).isEqualTo("APPROVED");
        assertThat(firstDetail.path("data").path("canIssue").asBoolean()).isTrue();
        assertThat(firstDetail.path("data").path("issueEarliestAt").asText())
                .isEqualTo("2026-11-09T16:00:00.000Z");
        JsonNode issued = post("/api/v1/item/admin/claims/" + firstClaimId + "/issues", """
                {"locationId":"101","idempotencyKey":"issue-day-late-same-day",
                 "lines":[{"claimLineId":"%s","quantity":1,"positionText":"DAY-1"}]}
                """.formatted(firstLineId), adminA);
        assertThat(issued.path("code").asInt()).isZero();

        setNow("2026-11-10T16:00:00Z");
        JsonNode secondDetail = get("/api/v1/item/admin/claims/" + secondClaimId, adminA);
        assertThat(secondDetail.path("data").path("status").asText()).isEqualTo("EXPIRED");
        assertThat(secondDetail.path("data").path("canIssue").asBoolean()).isFalse();
        JsonNode rejected = post("/api/v1/item/admin/claims/" + secondClaimId + "/issues", """
                {"locationId":"101","idempotencyKey":"issue-day-next-day-rejected",
                 "lines":[{"claimLineId":"%s","quantity":1,"positionText":"DAY-1"}]}
                """.formatted(secondLineId), adminA);
        assertThat(rejected.path("code").asInt()).isEqualTo(40900);
        assertThat(rejected.path("msg").asText()).contains("当前时间不在实发窗口内");
    }

    @Test
    @DisplayName("修改后revision递增，旧版本审核失效且必须重新审核")
    void revisionIncrementInvalidatesOldApproval() throws Exception {
        String materialId = createMaterial("ITEM_REVISION", "版本测试耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "REV-1", 10, "rev-inbound");
        JsonNode root = createClaimDetail(materialId, 4, LOCATION_A, "rev-claim");
        String claimId = root.path("id").asText();
        String childId = childForLocation(root, LOCATION_A).path("id").asText();
        assertThat(approve(childId, 1, adminA).path("code").asInt()).isZero();

        JsonNode updated = put("/api/v1/item/claims/" + claimId,
                claimBody(materialId, 5, LOCATION_A, 1, "修改后的申领用途", null), teacher);
        assertThat(updated.path("code").asInt()).isZero();
        assertThat(updated.path("data").path("revisionNo").asInt()).isEqualTo(2);
        assertThat(updated.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(updated.path("data").path("approvedBy").isNull()
                || updated.path("data").path("approvedBy").isMissingNode()).isTrue();
        childId = childForLocation(updated.path("data"), LOCATION_A).path("id").asText();

        assertThat(approve(childId, 1, adminA).path("code").asInt()).isEqualTo(40900);
        JsonNode reapproved = approve(childId, 2, adminA);
        assertThat(reapproved.path("code").asInt()).isZero();
        assertThat(reapproved.path("data").path("claimKind").asText()).isEqualTo("CHILD");
        assertThat(reapproved.path("data").path("status").asText()).isEqualTo("APPROVED");
        assertThat(reapproved.path("data").path("revisionNo").asInt()).isEqualTo(2);
        assertThat(reapproved.path("data").path("reservations").get(0).path("revisionNo").asInt())
                .isEqualTo(2);
        assertThat(get("/api/v1/item/claims/" + claimId, teacher)
                .path("data").path("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("4台固定资产全天预占，5台固定资产按普通预约窗口预占")
    void fixedAssetReservationRules() throws Exception {
        String fourClaim = createChildClaim("9001", 1, LOCATION_A, "asset-four-claim");
        JsonNode four = approve(fourClaim, 1, adminA);
        assertThat(four.path("code").asInt()).isZero();
        JsonNode fourReservation = four.path("data").path("reservations").get(0);
        assertThat(fourReservation.path("assetTotalSnapshot").asInt()).isEqualTo(4);
        assertThat(fourReservation.path("reservationRule").asText()).isEqualTo("SHANGHAI_DAY_LOW_TOTAL");
        assertThat(fourReservation.path("reservationStartAt").asText()).isEqualTo("2026-11-09T16:00:00.000Z");
        assertThat(fourReservation.path("reservationEndAt").asText()).isEqualTo("2026-11-10T16:00:00.000Z");
        JsonNode fourCatalog = get(availabilityQuery("四台总量测试资产", 5, 5), teacher);
        JsonNode fourAvailability = fourCatalog.path("data").path("records").get(0);
        assertThat(fourAvailability.path("ownedQuantity").decimalValue()).isEqualByComparingTo("4");
        assertThat(fourAvailability.path("availableQuantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(fourCatalog.toString()).doesNotContain("assetCode", "fixedAssetId", "candidateAssets", "locationId");
        assertThat(get(availabilityQuery("四台总量测试资产", 6, 6), teacher)
                .path("data").path("records").get(0).path("availableQuantity").decimalValue())
                .isEqualByComparingTo("3");

        String fiveClaim = createChildClaim("9002", 1, LOCATION_A, "asset-five-claim");
        JsonNode five = approve(fiveClaim, 1, adminA);
        assertThat(five.path("code").asInt()).isZero();
        JsonNode fiveReservation = five.path("data").path("reservations").get(0);
        assertThat(fiveReservation.path("assetTotalSnapshot").asInt()).isEqualTo(5);
        assertThat(fiveReservation.path("reservationRule").asText()).isEqualTo("BOOKING_WINDOW");
        assertThat(fiveReservation.path("reservationStartAt").asText())
                .isEqualTo(five.path("data").path("bookingStartAt").asText());
        assertThat(fiveReservation.path("reservationEndAt").asText())
                .isEqualTo(five.path("data").path("bookingEndAt").asText());
        JsonNode fiveAvailability = get(availabilityQuery("五台总量测试资产", 5, 5), teacher)
                .path("data").path("records").get(0);
        assertThat(fiveAvailability.path("ownedQuantity").decimalValue()).isEqualByComparingTo("5");
        assertThat(fiveAvailability.path("availableQuantity").decimalValue()).isEqualByComparingTo("4");
        assertThat(get(availabilityQuery("五台总量测试资产", 6, 6), teacher)
                .path("data").path("records").get(0).path("availableQuantity").decimalValue())
                .isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("教师可约目录汇总耗材数量并仅允许排除本人申领")
    void claimAvailabilityForConsumablesAndOwnership() throws Exception {
        String materialName = "规格目录耗材";
        String materialId = createMaterial("ITEM_AVAILABILITY", materialName);
        assertThat(inbound(materialId, String.valueOf(LOCATION_A), "AVAIL-A", 10, "availability-inbound-a")
                .path("code").asInt()).isZero();
        JsonNode inboundB = post("/api/v1/item/documents/opening", """
                {"idempotencyKey":"availability-inbound-b","lines":[{"materialId":"%s","locationId":"102",
                 "positionText":"AVAIL-B","quantity":5}]}
                """.formatted(materialId), adminB);
        assertThat(inboundB.path("code").asInt()).isZero();

        String query = availabilityQuery(materialName, 5, 5) + "&materialType=CONSUMABLE";
        JsonNode catalog = get(query, teacher);
        assertThat(catalog.path("code").asInt()).isZero();
        assertThat(catalog.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(catalog.path("data").path("pageNum").asInt()).isEqualTo(1);
        assertThat(catalog.path("data").path("pageSize").asInt()).isEqualTo(10);
        assertThat(catalog.path("data").path("records")).hasSize(1);
        JsonNode availability = catalog.path("data").path("records").get(0);
        assertThat(availability.path("materialId").asText()).isEqualTo(materialId);
        assertThat(availability.path("materialName").asText()).isEqualTo(materialName);
        assertThat(availability.path("specification").asText()).isEqualTo("集成测试");
        assertThat(availability.path("materialType").asText()).isEqualTo("CONSUMABLE");
        assertThat(availability.path("baseUnit").asText()).isEqualTo("件");
        assertThat(availability.path("ownedQuantity").decimalValue()).isEqualByComparingTo("15");
        assertThat(availability.path("availableQuantity").decimalValue()).isEqualByComparingTo("15");
        assertThat(catalog.toString()).doesNotContain("assetCode", "fixedAssetId", "candidateAssets", "locationId");

        JsonNode root = createClaimDetail(materialId, 4, LOCATION_A, "availability-claim");
        String claimId = root.path("id").asText();
        String childId = childForLocation(root, LOCATION_A).path("id").asText();
        assertThat(approve(childId, 1, adminA).path("code").asInt()).isZero();
        JsonNode reserved = get(query, teacher).path("data").path("records").get(0);
        assertThat(reserved.path("ownedQuantity").decimalValue()).isEqualByComparingTo("15");
        assertThat(reserved.path("availableQuantity").decimalValue()).isEqualByComparingTo("11");
        JsonNode editing = get(query + "&claimId=" + claimId, teacher)
                .path("data").path("records").get(0);
        assertThat(editing.path("availableQuantity").decimalValue()).isEqualByComparingTo("15");
        assertThat(get(query + "&claimId=" + claimId, adminA).path("code").asInt()).isEqualTo(40400);
        JsonNode invalidWindow = get(availabilityQuery(materialName, 6, 5), teacher);
        assertThat(invalidWindow.path("code").asInt()).isEqualTo(40000);
        assertThat(invalidWindow.path("msg").asText()).contains("起始节次不能晚于结束节次");
    }

    @Test
    @DisplayName("越库审核与实发均403，管理员权限不能绕过库位关系")
    void crossLocationReviewAndIssueForbidden() throws Exception {
        String materialId = createMaterial("ITEM_CROSS", "越库测试耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "CROSS-1", 5, "cross-inbound");
        JsonNode inboundB = post("/api/v1/item/documents/opening", """
                {"idempotencyKey":"cross-inbound-b","lines":[{"materialId":"%s","locationId":"102",
                 "positionText":"CROSS-B","quantity":2}]}
                """.formatted(materialId), adminB);
        assertThat(inboundB.path("code").asInt()).isZero();
        JsonNode root = createClaimDetail(materialId, 2, LOCATION_A, "cross-claim");
        String rootId = root.path("id").asText();
        String claimId = childForLocation(root, LOCATION_A).path("id").asText();
        JsonNode rootB = createClaimDetail(materialId, 1, LOCATION_B, "cross-claim-b");
        String childBId = childForLocation(rootB, LOCATION_B).path("id").asText();

        assertThat(approve(rootId, 1, adminA).path("code").asInt()).isEqualTo(40900);
        assertThat(approve(childBId, 1, adminA).path("code").asInt()).isEqualTo(40300);
        JsonNode reviewDenied = approve(claimId, 1, adminB);
        assertThat(reviewDenied.path("code").asInt()).isEqualTo(40300);
        assertThat(approve(childBId, 1, adminB).path("code").asInt()).isZero();
        assertThat(approve(claimId, 1, adminA).path("code").asInt()).isZero();
        setNow("2026-11-10T05:45:00Z");
        String claimLineId = get("/api/v1/item/admin/claims/" + claimId, adminA)
                .path("data").path("lines").get(0).path("id").asText();
        String childBLineId = get("/api/v1/item/admin/claims/" + childBId, adminB)
                .path("data").path("lines").get(0).path("id").asText();
        JsonNode rootIssueDenied = post("/api/v1/item/admin/claims/" + rootId + "/issues", """
                {"locationId":"101","idempotencyKey":"cross-root-issue",
                 "lines":[{"claimLineId":"%s","quantity":1,"positionText":"CROSS-1"}]}
                """.formatted(claimLineId), adminA);
        assertThat(rootIssueDenied.path("code").asInt()).isEqualTo(40900);
        JsonNode wrongLocationDenied = post("/api/v1/item/admin/claims/" + claimId + "/issues", """
                {"locationId":"102","idempotencyKey":"cross-wrong-location",
                 "lines":[{"claimLineId":"%s","quantity":1,"positionText":"CROSS-B"}]}
                """.formatted(claimLineId), adminA);
        assertThat(wrongLocationDenied.path("code").asInt()).isEqualTo(40900);
        JsonNode foreignLineDenied = post("/api/v1/item/admin/claims/" + claimId + "/issues", """
                {"locationId":"101","idempotencyKey":"cross-foreign-line",
                 "lines":[{"claimLineId":"%s","quantity":1,"positionText":"CROSS-1"}]}
                """.formatted(childBLineId), adminA);
        assertThat(foreignLineDenied.path("code").asInt()).isEqualTo(40000);
        JsonNode issueDenied = post("/api/v1/item/admin/claims/" + claimId + "/issues", """
                {"locationId":"101","idempotencyKey":"cross-issue",
                 "lines":[{"claimLineId":"%s","quantity":1,"positionText":"CROSS-1"}]}
                """.formatted(claimLineId), adminB);
        assertThat(issueDenied.path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("库存不足申请返回409且不放宽申请数量")
    void insufficientStockReturnsConflict() throws Exception {
        String materialId = createMaterial("ITEM_SHORTAGE", "无库存耗材");
        JsonNode body = post("/api/v1/item/claims",
                claimBody(materialId, 1, LOCATION_A, null, "库存不足申请", "shortage-claim"), teacher);
        assertThat(body.path("code").asInt()).isEqualTo(40900);
        assertThat(body.path("msg").asText()).contains("库存不足");
    }

    @Test
    @DisplayName("补货入库使用BH编号且在库报废使用BF编号")
    void replenishmentAndScrapUseSpecifiedDocumentNoPrefixes() throws Exception {
        String materialId = createMaterial("ITEM_NUMBER_PREFIX", "编号规则测试耗材");
        JsonNode replenishment = post("/api/v1/item/documents/replenishment", """
                {"idempotencyKey":"prefix-replenishment","lines":[{"materialId":"%s","locationId":"101",
                 "positionText":"PREFIX-1","quantity":3}]}
                """.formatted(materialId), adminA);
        assertThat(replenishment.path("code").asInt()).isZero();
        assertThat(replenishment.path("data").path("document").path("documentNo").asText())
                .matches("BH\\d{8}\\d{5}");

        JsonNode scrap = post("/api/v1/item/documents/scrap", """
                {"locationId":"101","idempotencyKey":"prefix-scrap",
                 "lines":[{"materialId":"%s","quantity":1,"positionText":"PREFIX-1","reason":"编号规则报废测试"}]}
                """.formatted(materialId), adminA);
        assertThat(scrap.path("code").asInt()).isZero();
        assertThat(scrap.path("data").path("document").path("documentNo").asText()).matches("BF\\d{8}\\d{5}");
        assertThat(scrap.path("data").path("document").path("bizDocumentId").asText()).isNotBlank();
    }

    @Test
    @DisplayName("核销双人控制：不自审、未复核不过账、另一管理员复核成功")
    void settlementRequiresIndependentReview() throws Exception {
        String materialId = createMaterial("ITEM_SETTLEMENT", "双人核销耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "SET-1", 10, "set-inbound");
        JsonNode created = post("/api/v1/item/documents/adjust", """
                {"locationId":"101","idempotencyKey":"set-adjust",
                 "lines":[{"materialId":"%s","quantity":2,"positionText":"SET-1","reason":"盘点差异调减"}]}
                """.formatted(materialId), adminA);
        assertThat(created.path("code").asInt()).isZero();
        String documentId = created.path("data").path("document").path("id").asText();
        assertThat(created.path("data").path("document").path("documentNo").asText()).matches("IM\\d+");
        assertThat(created.path("data").path("document").path("reviewStatus").asText())
                .isEqualTo("PENDING_REVIEW");
        assertThat(created.path("data").path("document").path("postedAt").isNull()
                || created.path("data").path("document").path("postedAt").isMissingNode()).isTrue();
        assertBalance(materialId, String.valueOf(LOCATION_A), "SET-1", "10", "10");

        JsonNode selfReview = post("/api/v1/item/documents/" + documentId + "/review",
                "{\"approve\":true,\"version\":0,\"reason\":\"自审尝试\"}", adminA);
        assertThat(selfReview.path("code").asInt()).isEqualTo(40300);
        assertBalance(materialId, String.valueOf(LOCATION_A), "SET-1", "10", "10");

        JsonNode reviewed = post("/api/v1/item/documents/" + documentId + "/review",
                "{\"approve\":true,\"version\":0,\"reason\":\"独立复核通过\"}", reviewerA);
        assertThat(reviewed.path("code").asInt()).isZero();
        assertThat(reviewed.path("data").path("document").path("reviewStatus").asText())
                .isEqualTo("APPROVED");
        assertThat(reviewed.path("data").path("document").path("postedAt").asText()).isNotBlank();
        assertThat(reviewed.path("data").path("document").path("reviewedBy").asText()).isEqualTo("13");
        assertBalance(materialId, String.valueOf(LOCATION_A), "SET-1", "8", "8");
    }

    @Test
    @DisplayName("核销明细跨单据分页筛选并保留复核与过账状态")
    void settlementDetailPageQueriesAcrossDocuments() throws Exception {
        String materialId = createMaterial("ITEM_SETTLEMENT_PAGE", "核销明细分页耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "SET-PAGE-1", 10, "settlement-page-inbound");
        JsonNode adjust = post("/api/v1/item/documents/adjust", """
                {"locationId":"101","idempotencyKey":"settlement-page-adjust",
                 "lines":[{"materialId":"%s","quantity":2,"positionText":"SET-PAGE-1","reason":"明细分页调整"}]}
                """.formatted(materialId), adminA);
        JsonNode scrap = post("/api/v1/item/documents/scrap", """
                {"locationId":"101","idempotencyKey":"settlement-page-scrap",
                 "lines":[{"materialId":"%s","quantity":1,"positionText":"SET-PAGE-1","reason":"明细分页报废"}]}
                """.formatted(materialId), adminA);
        assertThat(adjust.path("code").asInt()).isZero();
        assertThat(scrap.path("code").asInt()).isZero();
        String adjustId = adjust.path("data").path("document").path("id").asText();
        String scrapId = scrap.path("data").path("document").path("id").asText();
        String adjustNo = adjust.path("data").path("document").path("documentNo").asText();
        String scrapNo = scrap.path("data").path("document").path("documentNo").asText();
        String query = "/api/v1/item/settlements?materialId=" + materialId;

        JsonNode all = get(query, adminA);
        assertThat(all.path("code").asInt()).as(all.toString()).isZero();
        assertThat(all.path("data").path("total").asInt()).isEqualTo(2);
        assertThat(all.path("data").path("records")).hasSize(2);
        List<String> documentIds = new ArrayList<>();
        for (JsonNode row : all.path("data").path("records")) {
            documentIds.add(row.path("materialDocumentId").asText());
            assertThat(row.path("materialName").asText()).isEqualTo("核销明细分页耗材");
            assertThat(row.path("baseUnit").asText()).isEqualTo("件");
            assertThat(row.path("locationId").asText()).isEqualTo("101");
            assertThat(row.path("locationName").asText()).isNotBlank();
            assertThat(row.path("operatorName").asText()).isNotBlank();
            assertThat(row.path("occurredAt").asText()).isNotBlank();
            assertThat(row.path("reviewStatus").asText()).isEqualTo("PENDING_REVIEW");
            assertThat(row.path("postedAt").isNull() || row.path("postedAt").isMissingNode()).isTrue();
        }
        assertThat(documentIds).containsExactlyInAnyOrder(adjustId, scrapId);

        JsonNode byDocumentNo = get(query + "&documentNo=" + adjustNo, adminA);
        assertThat(byDocumentNo.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(byDocumentNo.path("data").path("records").get(0).path("materialDocumentId").asText())
                .isEqualTo(adjustId);
        assertThat(get(query + "&settlementType=SCRAP", adminA)
                .path("data").path("records").get(0).path("materialDocumentId").asText()).isEqualTo(scrapId);
        assertThat(get(query + "&locationId=102", adminA).path("data").path("total").asInt()).isZero();
        assertThat(get(query + "&reviewStatus=PENDING_REVIEW", adminA)
                .path("data").path("total").asInt()).isEqualTo(2);
        assertThat(get(query + "&startDate=2026-11-10&endDate=2026-11-10", adminA)
                .path("data").path("total").asInt()).isEqualTo(2);
        assertThat(get(query + "&startDate=2026-11-11&endDate=2026-11-11", adminA)
                .path("data").path("total").asInt()).isZero();
        assertThat(get(query + "&startDate=2026-11-11&endDate=2026-11-10", adminA)
                .path("code").asInt()).isEqualTo(40000);
        assertThat(get(query + "&pageSize=101", adminA).path("code").asInt()).isEqualTo(40000);
        assertThat(get("/api/v1/item/settlements", teacher).path("code").asInt()).isEqualTo(40300);

        JsonNode firstPage = get(query + "&pageSize=1&pageNum=1", adminA);
        JsonNode secondPage = get(query + "&pageSize=1&pageNum=2", adminA);
        assertThat(firstPage.path("data").path("total").asInt()).isEqualTo(2);
        assertThat(firstPage.path("data").path("records")).hasSize(1);
        assertThat(secondPage.path("data").path("records")).hasSize(1);
        assertThat(firstPage.path("data").path("records").get(0).path("id").asText())
                .isNotEqualTo(secondPage.path("data").path("records").get(0).path("id").asText());

        JsonNode approved = post("/api/v1/item/documents/" + adjustId + "/review",
                "{\"approve\":true,\"version\":0,\"reason\":\"明细分页批准\"}", reviewerA);
        JsonNode rejected = post("/api/v1/item/documents/" + scrapId + "/review",
                "{\"approve\":false,\"version\":0,\"reason\":\"明细分页驳回\"}", reviewerA);
        assertThat(approved.path("code").asInt()).isZero();
        assertThat(rejected.path("code").asInt()).isZero();
        JsonNode approvedRow = get(query + "&documentNo=" + adjustNo, adminA)
                .path("data").path("records").get(0);
        assertThat(approvedRow.path("reviewStatus").asText()).isEqualTo("APPROVED");
        assertThat(approvedRow.path("reviewedBy").asText()).isEqualTo("13");
        assertThat(approvedRow.path("reviewedByName").asText()).isNotBlank();
        assertThat(approvedRow.path("reviewedAt").asText()).isNotBlank();
        assertThat(approvedRow.path("postedAt").asText()).isNotBlank();
        JsonNode rejectedRow = get(query + "&documentNo=" + scrapNo, adminA)
                .path("data").path("records").get(0);
        assertThat(rejectedRow.path("reviewStatus").asText()).isEqualTo("REJECTED");
        assertThat(rejectedRow.path("postedAt").isNull() || rejectedRow.path("postedAt").isMissingNode()).isTrue();
        assertThat(get(query + "&reviewStatus=APPROVED", adminA).path("data").path("total").asInt()).isEqualTo(1);
        assertThat(get(query + "&reviewStatus=REJECTED", adminA).path("data").path("total").asInt()).isEqualTo(1);
        assertThat(get(query + "&reviewStatus=PENDING_REVIEW", adminA).path("data").path("total").asInt()).isZero();
    }

    @Test
    @DisplayName("移动发出不增加目标库存，短收后仅按实收增加目标库存")
    void transferShortReceipt() throws Exception {
        String materialId = createMaterial("ITEM_TRANSFER", "移动短收耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "MOVE-1", 10, "move-inbound");
        JsonNode sent = post("/api/v1/item/transfers", """
                {"fromLocationId":"101","toLocationId":"102","idempotencyKey":"move-send",
                 "lines":[{"materialId":"%s","quantity":4,"positionText":"MOVE-1"}]}
                """.formatted(materialId), adminA);
        assertThat(sent.path("code").asInt()).isZero();
        assertThat(sent.path("data").path("document").path("documentNo").asText()).matches("YD\\d{8}\\d{5}");
        String transferId = sent.path("data").path("document").path("id").asText();
        String sourceLineId = sent.path("data").path("lines").get(0)
                .path("sourceMaterialDocumentLineId").asText();
        assertBalance(materialId, String.valueOf(LOCATION_A), "MOVE-1", "6", "6");
        assertThat(balance(materialId, String.valueOf(LOCATION_B), "MOVE-1")).isNull();

        JsonNode received = post("/api/v1/item/transfers/" + transferId + "/receipts", """
                {"idempotencyKey":"move-receive-short","remark":"短收2件",
                 "lines":[{"sourceMaterialDocumentLineId":"%s","quantity":2,"positionText":"MOVE-1"}]}
                """.formatted(sourceLineId), adminB);
        assertThat(received.path("code").asInt()).isZero();
        assertThat(received.path("data").path("document").path("documentNo").asText()).matches("YD\\d{8}\\d{5}");
        assertThat(received.path("data").path("document").path("transferStatus").asText())
                .isEqualTo("PARTIALLY_RECEIVED");
        assertThat(received.path("data").path("remainingQuantity").decimalValue())
                .isEqualByComparingTo(new BigDecimal("2"));
        assertBalance(materialId, String.valueOf(LOCATION_A), "MOVE-1", "6", "6");
        assertBalance(materialId, String.valueOf(LOCATION_B), "MOVE-1", "2", "2");
    }

    @Test
    @DisplayName("全部物料数量写接口拒绝小数且归还零合计仍被拒绝")
    void decimalQuantitiesRejectedAcrossItemApis() throws Exception {
        String materialId = createMaterial("ITEM_INTEGER_ONLY", "整数数量耗材");
        assertThat(inbound(materialId, String.valueOf(LOCATION_A), "INTEGER-1", 20, "integer-inbound")
                .path("code").asInt()).isZero();

        assertIntegerRejected(post("/api/v1/item/documents/opening", """
                {"idempotencyKey":"integer-decimal-inbound","lines":[{"materialId":"%s","locationId":"101",
                 "positionText":"INTEGER-1","quantity":1.5}]}
                """.formatted(materialId), adminA));
        assertIntegerRejected(post("/api/v1/item/plans", """
                {"planName":"小数方案","enabled":1,
                 "lines":[{"materialId":"%s","defaultQuantity":1.5}]}
                """.formatted(materialId), teacher));
        assertIntegerRejected(post("/api/v1/item/claims", """
                {"purpose":"小数申领","bookingDate":"2026-11-10","startPeriodId":"5","endPeriodId":"5",
                 "idempotencyKey":"integer-decimal-claim",
                 "lines":[{"materialId":"%s","requestedQuantity":1.5}],
                 "allocations":[{"materialId":"%s","locationId":"101","plannedQuantity":1.5}]}
                """.formatted(materialId, materialId), teacher));
        assertIntegerRejected(post("/api/v1/item/claims/preview", """
                {"bookingDate":"2026-11-10","startPeriodId":"5","endPeriodId":"5",
                 "lines":[{"materialId":"%s","requestedQuantity":2}],
                 "allocations":[{"materialId":"%s","locationId":"101","plannedQuantity":1.5}]}
                """.formatted(materialId, materialId), teacher));
        assertIntegerRejected(post("/api/v1/item/documents/adjust", """
                {"locationId":"101","idempotencyKey":"integer-decimal-adjust",
                 "lines":[{"materialId":"%s","quantity":1.5,"positionText":"INTEGER-1","reason":"小数核销"}]}
                """.formatted(materialId), adminA));
        assertIntegerRejected(post("/api/v1/item/transfers", """
                {"fromLocationId":"101","toLocationId":"102","idempotencyKey":"integer-decimal-send",
                 "lines":[{"materialId":"%s","quantity":1.5,"positionText":"INTEGER-1"}]}
                """.formatted(materialId), adminA));

        String claimId = createChildClaim(materialId, 2, LOCATION_A, "integer-claim");
        assertThat(approve(claimId, 1, adminA).path("code").asInt()).isZero();
        setNow("2026-11-10T05:45:00Z");
        String claimLineId = get("/api/v1/item/admin/claims/" + claimId, adminA)
                .path("data").path("lines").get(0).path("id").asText();
        assertIntegerRejected(post("/api/v1/item/admin/claims/" + claimId + "/issues", """
                {"locationId":"101","idempotencyKey":"integer-decimal-issue",
                 "lines":[{"claimLineId":"%s","quantity":1.5,"positionText":"INTEGER-1"}]}
                """.formatted(claimLineId), adminA));
        JsonNode issued = post("/api/v1/item/admin/claims/" + claimId + "/issues", """
                {"locationId":"101","idempotencyKey":"integer-issue",
                 "lines":[{"claimLineId":"%s","quantity":1,"positionText":"INTEGER-1"}]}
                """.formatted(claimLineId), adminA);
        assertThat(issued.path("code").asInt()).isZero();
        String sourceId = issued.path("data").path("document").path("id").asText();
        String sourceLineId = issued.path("data").path("lines").get(0).path("id").asText();
        assertIntegerRejected(post("/api/v1/item/documents/" + sourceId + "/returns", """
                {"idempotencyKey":"integer-decimal-return","lines":[{"sourceMaterialDocumentLineId":"%s",
                 "returnQuantity":1.5,"consumedQuantity":0,"lostQuantity":0,"damagedQuantity":0,
                 "positionText":"INTEGER-1"}]}
                """.formatted(sourceLineId), adminA));
        JsonNode zeroReturn = post("/api/v1/item/documents/" + sourceId + "/returns", """
                {"idempotencyKey":"integer-zero-return","lines":[{"sourceMaterialDocumentLineId":"%s",
                 "positionText":"INTEGER-1"}]}
                """.formatted(sourceLineId), adminA);
        assertThat(zeroReturn.path("code").asInt()).isEqualTo(40000);
        assertThat(zeroReturn.path("msg").asText()).contains("合计必须大于0");

        JsonNode sent = post("/api/v1/item/transfers", """
                {"fromLocationId":"101","toLocationId":"102","idempotencyKey":"integer-send",
                 "lines":[{"materialId":"%s","quantity":2,"positionText":"INTEGER-1"}]}
                """.formatted(materialId), adminA);
        assertThat(sent.path("code").asInt()).isZero();
        String transferId = sent.path("data").path("document").path("id").asText();
        String transferLineId = sent.path("data").path("lines").get(0)
                .path("sourceMaterialDocumentLineId").asText();
        assertIntegerRejected(post("/api/v1/item/transfers/" + transferId + "/receipts", """
                {"idempotencyKey":"integer-decimal-receive",
                 "lines":[{"sourceMaterialDocumentLineId":"%s","quantity":1.5,"positionText":"INTEGER-1"}]}
                """.formatted(transferLineId), adminB));
        assertIntegerRejected(post("/api/v1/item/transfers/" + transferId + "/writeoffs", """
                {"idempotencyKey":"integer-decimal-writeoff",
                 "lines":[{"sourceMaterialDocumentLineId":"%s","settlementType":"LOST",
                 "quantity":1.5,"reason":"小数在途核销"}]}
                """.formatted(transferLineId), adminA));
    }

    @Test
    @DisplayName("设备清单预览、批量建档、单张入库单与重复导入跳过")
    void assetImportPreviewConfirmAndReplay() throws Exception {
        byte[] workbook = assetWorkbook(new String[][]{
                {"序号", "设备名称", "单位", "数量", "品牌", "型号", "备注"},
                {"（一）电工电子实训室建设", "", "", "", "", "", ""},
                {"一", "教学实训仪器", "", "", "", "", ""},
                {"1", "导入实验箱", "台", "2", "测试品牌", "TEST-A", ""},
                {"2", "导入示波器", "台", "1", "测试品牌", "TEST-B", ""},
                {"（二）计算机网络实训室建设", "", "", "", "", "", ""},
                {"1", "不应导入设备", "台", "9", "其他", "IGNORE", ""}
        });
        JsonNode preview = uploadAsset("/api/v1/item/assets/import/preview", workbook, LOCATION_A, adminA);
        assertThat(preview.path("code").asInt()).isZero();
        assertThat(preview.path("data").path("materialCount").asInt()).isEqualTo(2);
        assertThat(preview.path("data").path("assetCount").asInt()).isEqualTo(3);
        assertThat(preview.path("data").path("canImport").asBoolean()).isTrue();
        assertThat(preview.path("data").path("rows")).hasSize(2);

        JsonNode first = uploadAsset("/api/v1/item/assets/import", workbook, LOCATION_A, adminA);
        assertThat(first.path("code").asInt()).isZero();
        assertThat(first.path("data").path("createdMaterialCount").asInt()).isEqualTo(2);
        assertThat(first.path("data").path("importedAssetCount").asInt()).isEqualTo(3);
        assertThat(first.path("data").path("skippedAssetCount").asInt()).isZero();
        String documentId = first.path("data").path("documentId").asText();
        assertThat(documentId).isNotBlank();
        JsonNode importedDocument = get("/api/v1/item/documents/" + documentId, adminA).path("data");
        assertThat(importedDocument.path("lines")).hasSize(3);
        assertThat(importedDocument.path("document").path("documentNo").asText()).matches("FSRK\\d{8}\\d{5}");
        JsonNode assets = get("/api/v1/item/assets?assetCode=ITEM_A-0004-001&pageNum=1&pageSize=20", adminA);
        assertThat(assets.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(assets.path("data").path("records").get(0).path("assetCode").asText())
                .isEqualTo("ITEM_A-0004-001");

        JsonNode replayPreview = uploadAsset("/api/v1/item/assets/import/preview", workbook, LOCATION_A, adminA);
        assertThat(replayPreview.path("data").path("existingAssetCount").asInt()).isEqualTo(3);
        JsonNode replay = uploadAsset("/api/v1/item/assets/import", workbook, LOCATION_A, adminA);
        assertThat(replay.path("code").asInt()).isZero();
        assertThat(replay.path("data").path("createdMaterialCount").asInt()).isZero();
        assertThat(replay.path("data").path("importedAssetCount").asInt()).isZero();
        assertThat(replay.path("data").path("skippedAssetCount").asInt()).isEqualTo(3);
        assertThat(replay.path("data").path("documentId").isNull()
                || replay.path("data").path("documentId").isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("设备导入校验失败不落库且教师与越库管理员被拒绝")
    void assetImportValidationAndPermissions() throws Exception {
        byte[] invalid = assetWorkbook(new String[][]{
                {"序号", "设备名称", "单位", "数量", "品牌", "型号", "备注"},
                {"（一）", "", "", "", "", "", ""},
                {"1", "非法数量设备", "台", "0", "测试", "INVALID", ""}
        });
        JsonNode preview = uploadAsset("/api/v1/item/assets/import/preview", invalid, LOCATION_A, adminA);
        assertThat(preview.path("data").path("canImport").asBoolean()).isFalse();
        assertThat(preview.path("data").path("issues")).hasSize(1);
        JsonNode invalidConfirm = uploadAsset("/api/v1/item/assets/import", invalid, LOCATION_A, adminA);
        assertThat(invalidConfirm.path("code").asInt()).isEqualTo(40000);
        assertThat(uploadAsset("/api/v1/item/assets/import/preview", invalid, LOCATION_A, teacher)
                .path("code").asInt()).isEqualTo(40300);
        assertThat(uploadAsset("/api/v1/item/assets/import/preview", invalid, LOCATION_A, adminB)
                .path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("设备清单可按第二部分或全部章节预览")
    void assetImportSectionSelection() throws Exception {
        byte[] workbook = assetWorkbook(new String[][]{
                {"序号", "设备名称", "单位", "数量", "品牌", "型号", "备注"},
                {"（一）电工电子实训室建设", "", "", "", "", "", ""},
                {"1", "第一部分设备", "台", "2", "品牌甲", "A", ""},
                {"（二）计算机网络实训室建设", "", "", "", "", "", ""},
                {"1", "第二部分网络设备", "台", "9", "品牌乙", "B", ""},
                {"（三）3D数字孪生平台建设", "", "", "", "", "", ""},
                {"1", "第三部分平台", "套", "1", "品牌丙", "C", ""}
        });
        JsonNode second = uploadAsset("/api/v1/item/assets/import/preview", workbook, LOCATION_B, adminB, 2);
        assertThat(second.path("data").path("materialCount").asInt()).isEqualTo(1);
        assertThat(second.path("data").path("assetCount").asInt()).isEqualTo(9);
        assertThat(second.path("data").path("rows").get(0).path("materialName").asText())
                .isEqualTo("第二部分网络设备");
        JsonNode all = uploadAsset("/api/v1/item/assets/import/preview", workbook, LOCATION_B, adminB, 0);
        assertThat(all.path("data").path("materialCount").asInt()).isEqualTo(3);
        assertThat(all.path("data").path("assetCount").asInt()).isEqualTo(12);
    }

    @Test
    @DisplayName("自由格式表格自动识别表头别名、标题行和缺省单位")
    void assetImportSmartRecognition() throws Exception {
        byte[] workbook = assetWorkbook(new String[][]{
                {"某实验室设备采购清单", "", "", "", ""},
                {"资产名称", "可用数量", "制造商", "规格型号", "备注"},
                {"智能识别设备甲", "2", "品牌甲", "MODEL-A", ""},
                {"设备分类", "", "", "", ""},
                {"智能识别设备乙", "1", "品牌乙", "MODEL-B", ""}
        });
        JsonNode preview = uploadAsset("/api/v1/item/assets/import/preview", workbook, LOCATION_B, adminB);
        assertThat(preview.path("code").asInt()).isZero();
        assertThat(preview.path("data").path("sheetName").asText()).isEqualTo("设备清单");
        assertThat(preview.path("data").path("materialCount").asInt()).isEqualTo(2);
        assertThat(preview.path("data").path("assetCount").asInt()).isEqualTo(3);
        assertThat(preview.path("data").path("canImport").asBoolean()).isTrue();
        assertThat(preview.path("data").path("rows").get(1).path("baseUnit").asText()).isEqualTo("台");
    }

    @Test
    @DisplayName("复杂表格兼容英文表头、乱序列、重复表头、合计行与多工作表")
    void assetImportComplexWorkbookRecognition() throws Exception {
        byte[] workbook = assetWorkbookWithSheets(
                new String[][][]{{
                        {"使用说明", "此表仅用于说明"},
                        {"日期", "2026-10-28"}
                }, {
                        {"Lab inventory", "", "", "", "", ""},
                        {"Qty", "Equipment Name", "Manufacturer", "Specification", "UOM", "Notes"},
                        {"2", "English Device A", "Brand A", "MODEL-A", "台", ""},
                        {"Qty", "Equipment Name", "Manufacturer", "Specification", "UOM", "Notes"},
                        {"1", "English Device B", "Brand B", "MODEL-B", "", ""},
                        {"999", "合计", "", "", "", ""},
                        {"1000", "Total", "", "", "", ""}
                }}, new String[]{"说明", "复杂设备"});
        JsonNode preview = uploadAsset("/api/v1/item/assets/import/preview", workbook, LOCATION_B, adminB);
        assertThat(preview.path("code").asInt()).isZero();
        assertThat(preview.path("data").path("sheetName").asText()).isEqualTo("复杂设备");
        assertThat(preview.path("data").path("materialCount").asInt()).isEqualTo(2);
        assertThat(preview.path("data").path("assetCount").asInt()).isEqualTo(3);
        assertThat(preview.path("data").path("rows")).hasSize(2);
        assertThat(preview.path("data").path("rows").get(0).path("specification").asText())
                .isEqualTo("Brand A / MODEL-A");
        assertThat(preview.path("data").path("rows").get(1).path("baseUnit").asText()).isEqualTo("台");
    }

    @Test
    @DisplayName("明确设备名称优先于弱单位特征且元器件套件识别为耗材")
    void assetImportClassificationUsesStrongNamesBeforeUnits() throws Exception {
        byte[] workbook = assetWorkbook(new String[][]{
                {"序号", "物料名称", "单位", "数量"},
                {"1", "电子实验箱2", "个", "16"},
                {"2", "万用表", "个", "16"},
                {"3", "摄像机", "个", "3"},
                {"4", "机柜", "个", "1"},
                {"5", "储物柜", "个", "4"},
                {"6", "音柱", "只", "4"},
                {"7", "元器件储物柜", "个", "1"},
                {"8", "操作台", "张", "2"},
                {"9", "元器件", "套", "15"}
        });
        JsonNode preview = uploadAsset("/api/v1/item/assets/import/preview", workbook, LOCATION_A, adminA);
        assertThat(preview.path("data").path("canImport").asBoolean()).isTrue();
        assertThat(preview.path("data").path("assetCount").asInt()).isEqualTo(47);
        assertThat(preview.path("data").path("consumableQuantity").asLong()).isEqualTo(15);
        for (int index = 0; index < 8; index++) {
            assertThat(preview.path("data").path("rows").get(index).path("materialType").asText())
                    .isEqualTo("FIXED_ASSET");
        }
        assertThat(preview.path("data").path("rows").get(8).path("materialType").asText())
                .isEqualTo("CONSUMABLE");
    }

    @Test
    @DisplayName("混合清单耗材按数量入库且两台交换机生成两条独立资产")
    void assetImportMixedConsumablesAndSerializedDevices() throws Exception {
        byte[] workbook = assetWorkbook(new String[][]{
                {"序号", "物料名称", "类别", "单位", "数量", "品牌", "型号"},
                {"1", "管理交换机", "", "台", "2", "网络品牌", "SW-2"},
                {"2", "混合测试电阻", "", "个", "25", "元件品牌", "R-10K"}
        });
        JsonNode preview = uploadAsset("/api/v1/item/assets/import/preview", workbook, LOCATION_A, adminA);
        assertThat(preview.path("code").asInt()).isZero();
        assertThat(preview.path("data").path("assetCount").asInt()).isEqualTo(2);
        assertThat(preview.path("data").path("consumableQuantity").asLong()).isEqualTo(25);
        assertThat(preview.path("data").path("rows")).hasSize(2);
        assertThat(preview.path("data").path("rows").get(0).path("materialType").asText())
                .isEqualTo("FIXED_ASSET");
        assertThat(preview.path("data").path("rows").get(0).path("firstAssetCode").asText())
                .isEqualTo("ITEM_A-0002-001");
        assertThat(preview.path("data").path("rows").get(0).path("lastAssetCode").asText())
                .isEqualTo("ITEM_A-0002-002");
        assertThat(preview.path("data").path("rows").get(1).path("materialType").asText())
                .isEqualTo("CONSUMABLE");
        assertThat(preview.path("data").path("rows").get(1).path("skuCode").isNull()).isTrue();
        assertThat(preview.path("data").path("rows").get(1).path("firstAssetCode").isNull()).isTrue();

        JsonNode first = uploadAsset("/api/v1/item/assets/import", workbook, LOCATION_A, adminA);
        assertThat(first.path("code").asInt()).isZero();
        assertThat(first.path("data").path("createdMaterialCount").asInt()).isEqualTo(2);
        assertThat(first.path("data").path("importedAssetCount").asInt()).isEqualTo(2);
        assertThat(first.path("data").path("importedConsumableQuantity").asLong()).isEqualTo(25);
        JsonNode lines = get("/api/v1/item/documents/"
                + first.path("data").path("documentId").asText(), adminA).path("data").path("lines");
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).path("assetCode").asText()).isNotEqualTo(lines.get(1).path("assetCode").asText());
        assertThat(lines.get(0).path("quantity").decimalValue()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(lines.get(1).path("quantity").decimalValue()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(lines.get(2).path("quantity").decimalValue()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(lines.get(2).path("fixedAssetId").isNull()).isTrue();

        String switchName = URLEncoder.encode("管理交换机", StandardCharsets.UTF_8);
        JsonNode assets = get("/api/v1/item/assets?materialName=" + switchName
                + "&locationId=101&pageNum=1&pageSize=20", adminA);
        assertThat(assets.path("data").path("total").asInt()).isEqualTo(2);
        assertThat(assets.path("data").path("records")).hasSize(2);
        assertThat(assets.path("data").path("records").get(0).path("assetCode").asText())
                .isNotEqualTo(assets.path("data").path("records").get(1).path("assetCode").asText());

        String resistorName = URLEncoder.encode("混合测试电阻", StandardCharsets.UTF_8);
        JsonNode materials = get("/api/v1/item/materials?materialName=" + resistorName
                + "&pageNum=1&pageSize=20", adminA);
        assertThat(materials.path("data").path("total").asInt()).isEqualTo(1);
        JsonNode resistor = materials.path("data").path("records").get(0);
        assertThat(resistor.path("materialType").asText()).isEqualTo("CONSUMABLE");
        assertThat(resistor.path("trackMode").asText()).isEqualTo("QUANTITY");
        assertThat(resistor.path("skuCode").isNull()).isTrue();
        assertBalance(resistor.path("id").asText(), "101", null, "25", "25");
        assertThat(get("/api/v1/item/assets?materialName=" + resistorName
                + "&pageNum=1&pageSize=20", adminA).path("data").path("total").asInt()).isZero();

        JsonNode replay = uploadAsset("/api/v1/item/assets/import", workbook, LOCATION_A, adminA);
        assertThat(replay.path("code").asInt()).isZero();
        assertThat(replay.path("data").path("createdMaterialCount").asInt()).isZero();
        assertThat(replay.path("data").path("importedAssetCount").asInt()).isZero();
        assertThat(replay.path("data").path("skippedAssetCount").asInt()).isEqualTo(2);
        assertThat(replay.path("data").path("importedConsumableQuantity").asLong()).isZero();
        assertBalance(resistor.path("id").asText(), "101", null, "25", "25");
        assertThat(get("/api/v1/item/assets?materialName=" + switchName
                + "&locationId=101&pageNum=1&pageSize=20", adminA)
                .path("data").path("total").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("无法判断类别时整批拒绝且有效设备也不落库")
    void assetImportUnknownClassificationRollsBackWholeWorkbook() throws Exception {
        byte[] workbook = assetWorkbook(new String[][]{
                {"序号", "物料名称", "类别", "单位", "数量"},
                {"1", "回滚验证交换机", "", "台", "1"},
                {"2", "无法分类物料甲", "", "", "1"}
        });
        JsonNode preview = uploadAsset("/api/v1/item/assets/import/preview", workbook, LOCATION_A, adminA);
        assertThat(preview.path("data").path("canImport").asBoolean()).isFalse();
        assertThat(preview.path("data").path("issues")).hasSize(1);
        assertThat(preview.path("data").path("issues").get(0).path("message").asText())
                .contains("无法判断属于耗材还是设备");
        assertThat(uploadAsset("/api/v1/item/assets/import", workbook, LOCATION_A, adminA)
                .path("code").asInt()).isEqualTo(40000);
        String materialName = URLEncoder.encode("回滚验证交换机", StandardCharsets.UTF_8);
        assertThat(get("/api/v1/item/materials?materialName=" + materialName
                + "&pageNum=1&pageSize=20", adminA).path("data").path("total").asInt()).isZero();
        assertThat(get("/api/v1/item/assets?materialName=" + materialName
                + "&pageNum=1&pageSize=20", adminA).path("data").path("total").asInt()).isZero();
    }

    @Test
    @DisplayName("教师助理授权强制本人归属并拒绝跨教师撤销、非学生与重复授权")
    void assistantGrantOwnershipAndValidation() throws Exception {
        String student = login(22);
        String teacherB = login(23);
        JsonNode options = get("/api/v1/item/assistants/student_options?keyword=item_student", teacher);
        assertThat(options.path("code").asInt()).isZero();
        assertThat(options.path("data")).hasSize(2);
        assertThat(options.path("data").get(0).path("personNo").asText()).isEqualTo("IS022");
        assertThat(get("/api/v1/item/assistants/student_options", student).path("code").asInt())
                .isEqualTo(40300);

        JsonNode created = post("/api/v1/item/assistants", """
                {"studentId":"22","teacherId":"23","startAt":"2026-11-09T08:00:00Z",
                 "endAt":"2026-11-12T08:00:00Z","reason":"课程助教代领"}
                """, teacher);
        assertThat(created.path("code").asInt()).isZero();
        assertThat(created.path("data").path("teacherId").asText()).isEqualTo("21");
        assertThat(created.path("data").path("studentAccount").asText()).isEqualTo("item_student");
        assertThat(created.path("data").path("status").asText()).isEqualTo("ACTIVE");
        String grantId = created.path("data").path("id").asText();
        JsonNode activePage = get("/api/v1/item/assistants?pageNum=1&pageSize=100&status=ACTIVE", teacher);
        assertThat(recordWithId(activePage.path("data").path("records"), grantId)).isNotNull();
        assertThat(recordWithId(get("/api/v1/item/assistants?status=REVOKED&pageSize=100", teacher)
                .path("data").path("records"), grantId)).isNull();

        assertThat(createGrant("22", teacher).path("code").asInt()).isEqualTo(40900);
        assertThat(createGrant("11", teacher).path("code").asInt()).isEqualTo(40000);
        assertThat(post("/api/v1/item/assistants", """
                {"studentId":"22","startAt":"2026-11-01T08:00:00Z","endAt":"2026-11-02T08:00:00Z"}
                """, teacher).path("code").asInt()).isEqualTo(40000);
        assertThat(post("/api/v1/item/assistants/" + grantId + "/revoke", "{}", teacherB)
                .path("code").asInt()).isEqualTo(40400);
        assertThat(get("/api/v1/item/assistants", teacherB).path("data").path("total").asInt()).isZero();
        assertThat(post("/api/v1/item/assistants/" + grantId + "/revoke", "{}", teacher)
                .path("code").asInt()).isZero();

        JsonNode mine = get("/api/v1/item/assistants", teacher);
        JsonNode revoked = recordWithId(mine.path("data").path("records"), grantId);
        assertThat(revoked).isNotNull();
        assertThat(revoked.path("status").asText()).isEqualTo("REVOKED");
        assertThat(revoked.path("revokedBy").asText()).isEqualTo("21");
        assertThat(recordWithId(get("/api/v1/item/assistants?status=ACTIVE&pageSize=100", teacher)
                .path("data").path("records"), grantId)).isNull();
        assertThat(recordWithId(get("/api/v1/item/assistants?status=REVOKED&pageSize=100", teacher)
                .path("data").path("records"), grantId)).isNotNull();
        JsonNode reauthorized = createGrant("22", teacher);
        assertThat(reauthorized.path("code").asInt()).isZero();
        String newGrantId = reauthorized.path("data").path("id").asText();
        assertThat(newGrantId).isNotEqualTo(grantId);
        assertThat(post("/api/v1/item/assistants/" + newGrantId + "/revoke", "{}", teacher)
                .path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("申领新建修改和批准通知按库位与版本去重")
    void reviewAndApprovalNotificationsFollowLocationAndRevision() throws Exception {
        String materialId = createMaterial("ITEM_NOTIFY_REVIEW", "审批通知测试耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "LIFE-1", 5, "notify-review-stock");
        JsonNode root = createClaimDetail(materialId, 2, LOCATION_A, "notify-review-claim");
        String claimId = root.path("id").asText();
        String childId = childForLocation(root, LOCATION_A).path("id").asText();

        assertThat(notificationRecords(adminA, childId, "CLAIM_REVIEW_PENDING")).hasSize(1);
        assertThat(notificationRecords(reviewerA, childId, "CLAIM_REVIEW_PENDING")).hasSize(1);
        assertThat(notificationRecords(adminB, childId, "CLAIM_REVIEW_PENDING")).isEmpty();
        assertThat(approve(childId, 1, adminA).path("code").asInt()).isZero();
        assertThat(notificationRecords(teacher, childId, "CLAIM_APPROVED")).hasSize(1);
        assertThat(approve(childId, 1, adminA).path("code").asInt()).isEqualTo(40900);
        assertThat(notificationRecords(teacher, childId, "CLAIM_APPROVED")).hasSize(1);

        JsonNode updated = put("/api/v1/item/claims/" + claimId,
                claimBody(materialId, 2, LOCATION_A, 1, "修改后重新审批", null), teacher);
        assertThat(updated.path("code").asInt()).isZero();
        assertThat(updated.path("data").path("revisionNo").asInt()).isEqualTo(2);
        childId = childForLocation(updated.path("data"), LOCATION_A).path("id").asText();
        assertThat(notificationRecords(adminA, childId, "CLAIM_REVIEW_PENDING")).hasSize(2);
        assertThat(notificationRecords(reviewerA, childId, "CLAIM_REVIEW_PENDING")).hasSize(2);
        assertThat(notificationRecords(adminB, childId, "CLAIM_REVIEW_PENDING")).isEmpty();
        assertThat(notificationRecords(teacher, childId, "CLAIM_APPROVED")).hasSize(1);
        assertThat(approve(childId, 2, adminA).path("code").asInt()).isZero();
        assertThat(notificationRecords(teacher, childId, "CLAIM_APPROVED")).hasSize(2);
    }

    @Test
    @DisplayName("代领通知仅发指定学生且重新委派可再次通知")
    void delegationNotificationsAreIsolatedAndReplaySafe() throws Exception {
        String student = login(22);
        String otherStudent = login(23);
        JsonNode grant = createGrant("22", teacher);
        assertThat(grant.path("code").asInt()).isZero();
        String grantId = grant.path("data").path("id").asText();
        String materialId = createMaterial("ITEM_NOTIFY_PICKUP", "代领通知测试耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "LIFE-1", 3, "notify-pickup-stock");
        String claimId = createClaim(materialId, 1, LOCATION_A, "notify-pickup-claim");

        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"22\"}", teacher).path("code").asInt()).isZero();
        List<JsonNode> delegated = notificationRecords(student, claimId, "PICKUP_DELEGATED");
        assertThat(delegated).hasSize(1);
        assertThat(notificationRecords(otherStudent, claimId, "PICKUP_DELEGATED")).isEmpty();
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"22\"}", teacher).path("code").asInt()).isZero();
        assertThat(notificationRecords(student, claimId, "PICKUP_DELEGATED")).hasSize(1);
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":null}", teacher).path("code").asInt()).isZero();
        assertThat(notificationRecords(student, claimId, "PICKUP_DELEGATED")).hasSize(1);
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"22\"}", teacher).path("code").asInt()).isZero();
        List<JsonNode> redelegated = notificationRecords(student, claimId, "PICKUP_DELEGATED");
        assertThat(redelegated).hasSize(2);

        String notificationId = redelegated.get(0).path("id").asText();
        assertThat(post("/api/v1/item/notifications/" + notificationId + "/read", "{}", otherStudent)
                .path("code").asInt()).isEqualTo(40400);
        assertThat(post("/api/v1/item/notifications/" + notificationId + "/read", "{}", student)
                .path("code").asInt()).isZero();
        assertThat(get("/api/v1/item/notifications/unread_count", student).path("code").asInt()).isZero();
        assertThat(post("/api/v1/item/assistants/" + grantId + "/revoke", "{}", teacher)
                .path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("预约结束后每2小时提醒且全部实发未归还仍继续")
    void returnReminderRepeatsEveryTwoHoursAndStopsAfterReturn() throws Exception {
        String materialId = createMaterial("ITEM_RETURN_REMINDER", "归还提醒测试耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "LIFE-1", 2, "return-reminder-stock");
        String claimId = createChildClaim(materialId, 2, LOCATION_A, "return-reminder-claim");
        assertThat(approve(claimId, 1, adminA).path("code").asInt()).isZero();
        setNow("2026-11-10T05:45:00Z");
        JsonNode detail = get("/api/v1/item/admin/claims/" + claimId, adminA).path("data");
        String claimLineId = detail.path("lines").get(0).path("id").asText();
        Instant bookingEnd = Instant.parse(detail.path("bookingEndAt").asText());
        JsonNode issued = post("/api/v1/item/admin/claims/" + claimId + "/issues", """
                {"locationId":"101","idempotencyKey":"return-reminder-issue",
                 "lines":[{"claimLineId":"%s","quantity":2,"positionText":"LIFE-1"}]}
                """.formatted(claimLineId), adminA);
        assertThat(issued.path("code").asInt()).isZero();
        assertThat(get("/api/v1/item/admin/claims/" + claimId, adminA)
                .path("data").path("closeReason").asText()).isEqualTo("FULFILLED");
        String sourceId = issued.path("data").path("document").path("id").asText();
        String sourceLineId = issued.path("data").path("lines").get(0).path("id").asText();

        setNow(bookingEnd.minusSeconds(1).toString());
        maintenanceService.scanReturnReminders();
        assertThat(notificationRecords(teacher, claimId, "RETURN_REMINDER")).isEmpty();
        setNow(bookingEnd.plusSeconds(1).toString());
        maintenanceService.scanReturnReminders();
        assertThat(notificationRecords(teacher, claimId, "RETURN_REMINDER")).hasSize(1);
        assertThat(notificationRecords(teacher, claimId, "RETURN_REMINDER").get(0)
                .path("notificationStage").asText()).isEqualTo("IMMEDIATE");
        maintenanceService.scanReturnReminders();
        assertThat(notificationRecords(teacher, claimId, "RETURN_REMINDER")).hasSize(1);
        setNow(bookingEnd.plusSeconds(3600).toString());
        maintenanceService.scanReturnReminders();
        assertThat(notificationRecords(teacher, claimId, "RETURN_REMINDER")).hasSize(1);
        setNow(bookingEnd.plusSeconds(7201).toString());
        maintenanceService.scanReturnReminders();
        assertThat(notificationRecords(teacher, claimId, "RETURN_REMINDER")).hasSize(2);
        assertThat(notificationRecords(teacher, claimId, "RETURN_REMINDER").get(0)
                .path("notificationStage").asText()).isEqualTo("TWO_HOURLY");
        maintenanceService.scanReturnReminders();
        assertThat(notificationRecords(teacher, claimId, "RETURN_REMINDER")).hasSize(2);

        assertThat(returnQuantity(sourceId, sourceLineId, 2, "return-reminder-all", "测试逾期归还")
                .path("code").asInt()).isZero();
        setNow(bookingEnd.plusSeconds(14400).toString());
        maintenanceService.scanReturnReminders();
        assertThat(notificationRecords(teacher, claimId, "RETURN_REMINDER")).hasSize(2);

        setNow("2026-11-10T00:30:00Z");
        String abandonedMaterial = createMaterial("ITEM_REMINDER_ABANDONED", "放弃提醒测试耗材");
        inbound(abandonedMaterial, String.valueOf(LOCATION_A), "LIFE-1", 1, "reminder-abandoned-stock");
        String abandonedClaim = createChildClaim(abandonedMaterial, 1, LOCATION_A, "reminder-abandoned-claim");
        assertThat(approve(abandonedClaim, 1, adminA).path("code").asInt()).isZero();
        setNow("2026-11-10T05:45:00Z");
        JsonNode abandonedDetail = get("/api/v1/item/admin/claims/" + abandonedClaim, adminA).path("data");
        String abandonedLine = abandonedDetail.path("lines").get(0).path("id").asText();
        Instant abandonedEnd = Instant.parse(abandonedDetail.path("bookingEndAt").asText());
        assertThat(post("/api/v1/item/admin/claims/" + abandonedClaim + "/issues", """
                {"locationId":"101","idempotencyKey":"reminder-abandoned-issue",
                 "lines":[{"claimLineId":"%s","quantity":1,"positionText":"LIFE-1"}]}
                """.formatted(abandonedLine), adminA).path("code").asInt()).isZero();
        MaterialClaim abandoned = claimMapper.selectById(Long.valueOf(abandonedClaim));
        abandoned.setStatus(ClaimStatus.CLOSED);
        abandoned.setCloseReason("ABANDONED");
        claimMapper.updateById(abandoned);
        setNow(abandonedEnd.plusSeconds(1).toString());
        maintenanceService.scanReturnReminders();
        assertThat(notificationRecords(teacher, abandonedClaim, "RETURN_REMINDER")).isEmpty();
    }

    @Test
    @DisplayName("原申领单委派保持版本与审核状态且学生严格只读本人代领")
    void claimDelegationAndStudentPickupAccess() throws Exception {
        String student = login(22);
        String studentB = login(24);
        String teacherB = login(23);
        JsonNode grant = createGrant("22", teacher);
        assertThat(grant.path("code").asInt()).isZero();
        String grantId = grant.path("data").path("id").asText();
        String materialId = createMaterial("ITEM_DELEGATION", "委派权限测试耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "DELEGATE-1", 5, "delegate-stock");
        JsonNode root = createClaimDetail(materialId, 2, LOCATION_A, "delegate-claim");
        String claimId = root.path("id").asText();
        String childId = childForLocation(root, LOCATION_A).path("id").asText();

        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"24\"}", teacher).path("code").asInt()).isEqualTo(40900);
        JsonNode delegated = put("/api/v1/item/claims/" + claimId + "/delegation", """
                {"studentId":"22","grantId":"1","pickupUserId":"23"}
                """, teacher);
        assertThat(delegated.path("code").asInt()).isZero();
        assertThat(delegated.path("data").path("pickupMode").asText()).isEqualTo("ASSISTANT");
        assertThat(delegated.path("data").path("delegateStudentId").asText()).isEqualTo("22");
        assertThat(delegated.path("data").path("delegateGrantId").asText()).isEqualTo(grantId);
        assertThat(delegated.path("data").path("delegationValid").asBoolean()).isTrue();
        assertThat(delegated.path("data").path("revisionNo").asInt()).isEqualTo(1);
        assertThat(delegated.path("data").path("status").asText()).isEqualTo("PENDING");
        int eventCount = delegated.path("data").path("events").size();
        String delegatedAt = delegated.path("data").path("delegatedAt").asText();

        JsonNode replay = put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"22\"}", teacher);
        assertThat(replay.path("data").path("events")).hasSize(eventCount);
        assertThat(replay.path("data").path("delegatedAt").asText()).isEqualTo(delegatedAt);
        JsonNode updated = put("/api/v1/item/claims/" + claimId,
                claimBody(materialId, 2, LOCATION_A, 1, "修改后仍保留代领", null), teacher);
        assertThat(updated.path("code").asInt()).isZero();
        assertThat(updated.path("data").path("revisionNo").asInt()).isEqualTo(2);
        assertThat(updated.path("data").path("delegateStudentId").asText()).isEqualTo("22");
        childId = childForLocation(updated.path("data"), LOCATION_A).path("id").asText();
        assertThat(approve(childId, 2, adminA).path("code").asInt()).isZero();

        JsonNode pickups = get("/api/v1/item/pickups", student);
        JsonNode pickup = recordWithId(pickups.path("data").path("records"), claimId);
        assertThat(pickup).isNotNull();
        assertThat(pickup.path("delegatedAt").asText()).isNotBlank();
        JsonNode detail = get("/api/v1/item/pickups/" + claimId, student);
        assertThat(detail.path("code").asInt()).isZero();
        assertThat(detail.path("data").path("applicantId").asText()).isEqualTo("21");
        assertThat(detail.path("data").has("reservations")).isFalse();
        assertThat(detail.path("data").has("assetCode")).isFalse();
        assertThat(get("/api/v1/item/pickups/" + claimId, studentB).path("code").asInt()).isEqualTo(40400);
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"24\"}", teacherB).path("code").asInt()).isEqualTo(40400);
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"22\"}", student).path("code").asInt()).isEqualTo(40300);

        JsonNode withdrawn = put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"  \"}", teacher);
        assertThat(withdrawn.path("data").path("pickupMode").asText()).isEqualTo("SELF");
        assertThat(withdrawn.path("data").path("delegateStudentId").isNull()).isTrue();
        assertThat(withdrawn.path("data").path("revisionNo").asInt()).isEqualTo(2);
        assertThat(recordWithId(get("/api/v1/item/pickups", student)
                .path("data").path("records"), claimId)).isNull();
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"22\"}", teacher).path("code").asInt()).isZero();
        assertThat(post("/api/v1/item/claims/" + claimId + "/cancel",
                "{\"reason\":\"委派状态校验\"}", teacher).path("code").asInt()).isZero();
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"22\"}", teacher).path("code").asInt()).isEqualTo(40900);
        assertThat(post("/api/v1/item/assistants/" + grantId + "/revoke", "{}", teacher)
                .path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("撤销或停用阻止实发且重新授权实发留痕后撤销仍可归还")
    void revokedGrantBlocksIssueButNotReturn() throws Exception {
        String student = login(22);
        JsonNode grant = createGrant("22", teacher);
        String grantId = grant.path("data").path("id").asText();
        String materialId = createMaterial("ITEM_DELEGATE_ISSUE", "代领实发测试耗材");
        inbound(materialId, String.valueOf(LOCATION_A), "LIFE-1", 4, "delegate-issue-stock");
        JsonNode root = createClaimDetail(materialId, 2, LOCATION_A, "delegate-issue-claim");
        String claimId = root.path("id").asText();
        String childId = childForLocation(root, LOCATION_A).path("id").asText();
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"22\"}", teacher).path("code").asInt()).isZero();
        assertThat(approve(childId, 1, adminA).path("code").asInt()).isZero();
        setNow("2026-11-10T05:45:00Z");
        String claimLineId = get("/api/v1/item/admin/claims/" + childId, adminA)
                .path("data").path("lines").get(0).path("id").asText();
        JsonNode context = get("/api/v1/item/admin/claims/" + childId
                + "/issue_context?locationId=101", adminA);
        assertThat(context.path("data").path("canIssue").asBoolean()).isTrue();
        assertThat(context.path("data").path("pickupUserId").asText()).isEqualTo("22");
        assertThat(context.path("data").path("pickupUserName").asText()).isEqualTo("代领学生甲");

        assertThat(post("/api/v1/item/assistants/" + grantId + "/revoke", "{}", teacher)
                .path("code").asInt()).isZero();
        JsonNode invalidDetail = get("/api/v1/item/claims/" + claimId, teacher);
        assertThat(invalidDetail.path("data").path("delegationValid").asBoolean()).isFalse();
        assertThat(invalidDetail.path("data").path("canIssue").asBoolean()).isFalse();
        assertThat(invalidDetail.path("data").path("delegateStudentId").asText()).isEqualTo("22");
        JsonNode blockedContext = get("/api/v1/item/admin/claims/" + childId
                + "/issue_context?locationId=101", adminA);
        assertThat(blockedContext.path("data").path("canIssue").asBoolean()).isFalse();
        assertThat(blockedContext.path("data").path("issueBlockedReason").asText()).contains("撤销");
        assertThat(post("/api/v1/item/admin/claims/" + childId + "/issues", """
                {"locationId":"101","idempotencyKey":"delegate-issue-blocked",
                 "lines":[{"claimLineId":"%s","quantity":2,"positionText":"LIFE-1"}]}
                """.formatted(claimLineId), adminA).path("code").asInt()).isEqualTo(40900);

        JsonNode newGrant = createGrant("22", teacher);
        String newGrantId = newGrant.path("data").path("id").asText();
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":null}", teacher).path("code").asInt()).isZero();
        assertThat(put("/api/v1/item/claims/" + claimId + "/delegation",
                "{\"studentId\":\"22\"}", teacher).path("data").path("delegateGrantId").asText())
                .isEqualTo(newGrantId);
        SysUser studentAccount = userMapper.selectById(22L);
        studentAccount.setEnabled(0);
        userMapper.updateById(studentAccount);
        try {
            assertThat(get("/api/v1/item/claims/" + claimId, teacher)
                    .path("data").path("delegationValid").asBoolean()).isFalse();
            assertThat(get("/api/v1/item/admin/claims/" + childId
                            + "/issue_context?locationId=101", adminA)
                    .path("data").path("issueBlockedReason").asText()).contains("停用");
        } finally {
            studentAccount.setEnabled(1);
            userMapper.updateById(studentAccount);
        }

        JsonNode issued = post("/api/v1/item/admin/claims/" + childId + "/issues", """
                {"locationId":"101","idempotencyKey":"delegate-issue-success",
                 "lines":[{"claimLineId":"%s","quantity":2,"positionText":"LIFE-1"}]}
                """.formatted(claimLineId), adminA);
        assertThat(issued.path("code").asInt()).isZero();
        assertThat(issued.path("data").path("document").path("pickupUserId").asText()).isEqualTo("22");
        assertThat(issued.path("data").path("document").path("pickupUserName").asText())
                .isEqualTo("代领学生甲");
        assertThat(issued.path("data").path("document").path("operatorId").asText()).isEqualTo("11");
        assertThat(issued.path("data").path("document").path("responsibleTeacherId").asText()).isEqualTo("21");
        String sourceId = issued.path("data").path("document").path("id").asText();
        String sourceLineId = issued.path("data").path("lines").get(0).path("id").asText();
        assertThat(post("/api/v1/item/assistants/" + newGrantId + "/revoke", "{}", teacher)
                .path("code").asInt()).isZero();
        JsonNode returned = returnQuantity(sourceId, sourceLineId, 1, "delegate-return-after-revoke", null);
        assertThat(returned.path("code").asInt()).isZero();
        assertThat(returned.path("data").path("document").path("pickupUserId").asText()).isEqualTo("22");
        assertThat(returned.path("data").path("document").path("pickupUserName").asText())
                .isEqualTo("代领学生甲");
        assertThat(recordWithId(get("/api/v1/item/pickups", student)
                .path("data").path("records"), claimId)).isNotNull();
    }

    @Test
    @DisplayName("方案物料目录教师可访问、学生403且默认十条并支持类型名称筛选")
    void planMaterialCatalogPermissionsPaginationAndFiltering() throws Exception {
        String prefix = "方案目录分页物料";
        for (int index = 1; index <= 11; index++) {
            createMaterial("ITEM_PLAN_PAGE_" + index, prefix + String.format("%02d", index));
        }
        String query = "/api/v1/item/plans/materials?materialType=CONSUMABLE&materialName="
                + URLEncoder.encode("分页物料", StandardCharsets.UTF_8);
        JsonNode firstPage = get(query, teacher);
        assertThat(firstPage.path("code").asInt()).isZero();
        assertThat(firstPage.path("data").path("total").asInt()).isEqualTo(11);
        assertThat(firstPage.path("data").path("pageNum").asInt()).isEqualTo(1);
        assertThat(firstPage.path("data").path("pageSize").asInt()).isEqualTo(10);
        assertThat(firstPage.path("data").path("records")).hasSize(10);
        assertThat(firstPage.path("data").path("records").get(0).path("materialName").asText())
                .isEqualTo(prefix + "01");
        assertThat(firstPage.path("data").path("records").get(9).path("materialName").asText())
                .isEqualTo(prefix + "10");

        JsonNode secondPage = get(query + "&pageNum=2", teacher);
        assertThat(secondPage.path("data").path("pageSize").asInt()).isEqualTo(10);
        assertThat(secondPage.path("data").path("records")).hasSize(1);
        assertThat(secondPage.path("data").path("records").get(0).path("materialName").asText())
                .isEqualTo(prefix + "11");
        assertThat(get("/api/v1/item/plans/materials?materialType=FIXED_ASSET&materialName="
                        + URLEncoder.encode("分页物料", StandardCharsets.UTF_8), teacher)
                .path("data").path("total").asInt()).isZero();

        HttpResponse<String> forbidden = requestWithoutBody("GET", "/api/v1/item/plans/materials", login(22));
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(forbidden.body()).path("code").asInt()).isEqualTo(40300);
    }

    @Test
    @DisplayName("方案物料目录汇总两库耗材、排除遗失报废资产且不泄露敏感字段并允许超保有保存")
    void planMaterialCatalogOwnedQuantitiesAndSafeFields() throws Exception {
        String materialName = "方案目录两库耗材";
        String materialId = createMaterial("ITEM_PLAN_OWNED", materialName);
        assertThat(inbound(materialId, String.valueOf(LOCATION_A), "PLAN-A", 10, "plan-owned-a")
                .path("code").asInt()).isZero();
        JsonNode inboundB = post("/api/v1/item/documents/opening", """
                {"idempotencyKey":"plan-owned-b","lines":[{"materialId":"%s","locationId":"102",
                 "positionText":"PLAN-B","quantity":5}]}
                """.formatted(materialId), adminB);
        assertThat(inboundB.path("code").asInt()).isZero();

        JsonNode consumableCatalog = get("/api/v1/item/plans/materials?materialType=CONSUMABLE&materialName="
                + URLEncoder.encode("两库耗材", StandardCharsets.UTF_8), teacher);
        assertThat(consumableCatalog.path("code").asInt()).isZero();
        assertThat(consumableCatalog.path("data").path("records")).hasSize(1);
        JsonNode consumable = consumableCatalog.path("data").path("records").get(0);
        assertThat(consumable.path("materialId").asText()).isEqualTo(materialId);
        assertThat(consumable.path("materialName").asText()).isEqualTo(materialName);
        assertThat(consumable.path("specification").asText()).isEqualTo("集成测试");
        assertThat(consumable.path("materialType").asText()).isEqualTo("CONSUMABLE");
        assertThat(consumable.path("quantityMode").asText()).isEqualTo("EXACT");
        assertThat(consumable.path("baseUnit").asText()).isEqualTo("件");
        assertThat(consumable.path("ownedQuantity").decimalValue()).isEqualByComparingTo("15");
        assertThat(consumable.size()).isEqualTo(7);
        assertThat(consumable.has("materialId") && consumable.has("materialName")
                && consumable.has("specification") && consumable.has("materialType")
                && consumable.has("quantityMode") && consumable.has("baseUnit")
                && consumable.has("ownedQuantity")).isTrue();
        assertThat(consumableCatalog.toString()).doesNotContain("availableQuantity", "assetCode", "locationId",
                "bookingDate", "startPeriodId", "endPeriodId", "reservation");

        String zeroMaterialId = createMaterial("ITEM_PLAN_ZERO", "方案目录无保有耗材");
        JsonNode zero = get("/api/v1/item/plans/materials?materialName="
                        + URLEncoder.encode("方案目录无保有耗材", StandardCharsets.UTF_8), teacher)
                .path("data").path("records").get(0);
        assertThat(zero.path("materialId").asText()).isEqualTo(zeroMaterialId);
        assertThat(zero.path("ownedQuantity").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);

        String assetName = "方案目录状态资产";
        JsonNode assetMaterial = post("/api/v1/item/materials", """
                {"materialType":"FIXED_ASSET","trackMode":"SERIAL","skuCode":"ITEM_PLAN_ASSET",
                 "materialName":"%s","specification":"方案目录测试","baseUnit":"台",
                 "quantityMode":"EXACT","enabled":1}
                """.formatted(assetName), adminA);
        assertThat(assetMaterial.path("code").asInt()).isZero();
        String assetMaterialId = assetMaterial.path("data").path("id").asText();
        for (int index = 1; index <= 4; index++) {
            JsonNode createdAsset = post("/api/v1/item/assets", """
                    {"materialId":"%s","assetCode":"PLAN-ASSET-%03d","locationId":"101",
                     "positionText":"PLAN-ASSET","documentType":"OPENING","idempotencyKey":"plan-asset-%d"}
                    """.formatted(assetMaterialId, index, index), adminA);
            assertThat(createdAsset.path("code").asInt()).isZero();
        }
        JsonNode assets = get("/api/v1/item/assets?materialName="
                + URLEncoder.encode(assetName, StandardCharsets.UTF_8) + "&pageSize=20", adminA);
        assertThat(assets.path("data").path("records")).hasSize(4);
        FixedAsset lost = fixedAssetMapper.selectById(Long.valueOf(
                assets.path("data").path("records").get(0).path("id").asText()));
        lost.setStatus(AssetStatus.LOST);
        assertThat(fixedAssetMapper.updateById(lost)).isEqualTo(1);
        FixedAsset scrapped = fixedAssetMapper.selectById(Long.valueOf(
                assets.path("data").path("records").get(1).path("id").asText()));
        scrapped.setStatus(AssetStatus.SCRAPPED);
        assertThat(fixedAssetMapper.updateById(scrapped)).isEqualTo(1);

        JsonNode assetCatalog = get("/api/v1/item/plans/materials?materialType=FIXED_ASSET&materialName="
                + URLEncoder.encode("状态资产", StandardCharsets.UTF_8), teacher);
        assertThat(assetCatalog.path("data").path("records")).hasSize(1);
        JsonNode asset = assetCatalog.path("data").path("records").get(0);
        assertThat(asset.path("materialName").asText()).isEqualTo(assetName);
        assertThat(asset.path("ownedQuantity").decimalValue()).isEqualByComparingTo("2");
        assertThat(asset.size()).isEqualTo(7);
        assertThat(assetCatalog.toString()).doesNotContain("availableQuantity", "assetCode", "locationId",
                "bookingDate", "startPeriodId", "endPeriodId", "reservation");

        JsonNode plan = post("/api/v1/item/plans", """
                {"planName":"超保有方案","enabled":1,"remark":"目录仅展示保有，不限制方案数量",
                 "lines":[{"materialId":"%s","defaultQuantity":20}]}
                """.formatted(materialId), teacher);
        assertThat(plan.path("code").asInt()).isZero();
        assertThat(plan.path("data").path("lines").get(0).path("materialId").asText()).isEqualTo(materialId);
        assertThat(plan.path("data").path("lines").get(0).path("defaultQuantity").decimalValue())
                .isEqualByComparingTo("20");
    }

    private byte[] assetWorkbook(String[][] rows) throws Exception {
        return assetWorkbookWithSheets(new String[][][]{rows}, new String[]{"设备清单"});
    }

    private byte[] assetWorkbookWithSheets(String[][][] sheets, String[] names) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int s = 0; s < sheets.length; s++) {
                var sheet = workbook.createSheet(names[s]);
                for (int r = 0; r < sheets[s].length; r++) {
                    var row = sheet.createRow(r);
                    for (int c = 0; c < sheets[s][r].length; c++) {
                        row.createCell(c).setCellValue(sheets[s][r][c]);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode uploadAsset(String path, byte[] content, long locationId, String token) throws Exception {
        return uploadAsset(path, content, locationId, token, 1);
    }

    private JsonNode uploadAsset(String path, byte[] content, long locationId, String token,
                                 int section) throws Exception {
        String boundary = "----AssetImportBoundary" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
                + "filename=\"assets.xlsx\"\r\nContent-Type: application/vnd.openxmlformats-officedocument."
                + "spreadsheetml.sheet\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"locationId\"\r\n\r\n"
                + locationId + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"section\"\r\n\r\n"
                + section + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build());
    }

    private void setNow(String instant) {
        Mockito.when(itemClock.instant()).thenReturn(Instant.parse(instant));
        Mockito.lenient().when(itemClock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private String login(long userId) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/test/login?userId=" + userId))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
        String token = objectMapper.readTree(response.body()).path("data").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private JsonNode createGrant(String studentId, String token) throws Exception {
        return post("/api/v1/item/assistants", """
                {"studentId":"%s","startAt":"2026-11-09T08:00:00Z",
                 "endAt":"2026-11-12T08:00:00Z","reason":"集成测试代领授权"}
                """.formatted(studentId), token);
    }

    private List<JsonNode> notificationRecords(String token, String claimId, String type) throws Exception {
        JsonNode records = get("/api/v1/item/notifications/my?pageSize=100", token)
                .path("data").path("records");
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode record : records) {
            if (claimId.equals(record.path("claimId").asText())
                    && type.equals(record.path("notificationType").asText())) {
                matches.add(record);
            }
        }
        return matches;
    }

    private JsonNode recordWithId(JsonNode records, String id) {
        for (JsonNode record : records) {
            if (id.equals(record.path("id").asText())) {
                return record;
            }
        }
        return null;
    }

    private String createMaterial(String sku, String name) throws Exception {
        JsonNode body = post("/api/v1/item/materials", """
                {"materialType":"CONSUMABLE","trackMode":"QUANTITY","skuCode":"%s",
                 "materialName":"%s","specification":"集成测试","baseUnit":"件",
                 "quantityMode":"EXACT","enabled":1}
                """.formatted(sku, name), adminA);
        assertThat(body.path("code").asInt()).isZero();
        return body.path("data").path("id").asText();
    }

    private JsonNode inbound(String materialId, String locationId, String position,
                             int quantity, String key) throws Exception {
        return post("/api/v1/item/documents/opening", """
                {"idempotencyKey":"%s","lines":[{"materialId":"%s","locationId":"%s",
                 "positionText":"%s","quantity":%d}]}
                """.formatted(key, materialId, locationId, position, quantity), adminA);
    }

    private String availabilityQuery(String materialName, int startPeriodId, int endPeriodId) {
        return "/api/v1/item/claims/availability?bookingDate=" + BOOKING_DATE
                + "&startPeriodId=" + startPeriodId + "&endPeriodId=" + endPeriodId
                + "&materialName=" + URLEncoder.encode(materialName, StandardCharsets.UTF_8);
    }

    private String createClaim(String materialId, int quantity, long locationId, String key) throws Exception {
        return createClaimDetail(materialId, quantity, locationId, key).path("id").asText();
    }

    private String createChildClaim(String materialId, int quantity, long locationId, String key) throws Exception {
        JsonNode root = createClaimDetail(materialId, quantity, locationId, key);
        return childForLocation(root, locationId).path("id").asText();
    }

    private JsonNode createClaimDetail(String materialId, int quantity, long locationId, String key) throws Exception {
        JsonNode body = post("/api/v1/item/claims",
                claimBody(materialId, quantity, locationId, null, "集成测试申领", key), teacher);
        assertThat(body.path("code").asInt()).isZero();
        assertThat(body.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("data").path("claimKind").asText()).isEqualTo("ROOT");
        assertThat(body.path("data").path("documentNo").asText()).matches("SL\\d{8}\\d{5}");
        assertThat(body.path("data").path("businessChainId").asText()).matches("ALL\\d{8}\\d{5}");
        assertThat(body.path("data").path("bizDocumentId").asText()).isNotBlank();
        return body.path("data");
    }

    private JsonNode childForLocation(JsonNode rootDetail, long locationId) {
        JsonNode match = null;
        for (JsonNode child : rootDetail.path("children")) {
            if (locationId == child.path("locationId").asLong()) {
                assertThat(match).as("库位 %d 应只有一个当前子申领单", locationId).isNull();
                match = child;
            }
        }
        assertThat(match).as("缺少库位 %d 的子申领单：%s", locationId, rootDetail).isNotNull();
        assertThat(match.path("claimKind").asText()).isEqualTo("CHILD");
        assertThat(match.path("parentClaimId").asText()).isEqualTo(rootDetail.path("id").asText());
        assertThat(match.path("locationId").asText()).isEqualTo(String.valueOf(locationId));
        return match;
    }

    private String claimBody(String materialId, int quantity, long locationId,
                             Integer revision, String purpose, String idempotencyKey) {
        String revisionJson = revision == null ? "" : ",\"revisionNo\":" + revision;
        String idempotencyJson = idempotencyKey == null ? "" : ",\"idempotencyKey\":\"" + idempotencyKey + "\"";
        return """
                {"purpose":"%s","bookingDate":"%s","startPeriodId":"5","endPeriodId":"5",
                 "lines":[{"materialId":"%s","requestedQuantity":%d}],
                 "allocations":[{"materialId":"%s","locationId":"%d","plannedQuantity":%d}]%s%s}
                """.formatted(purpose, BOOKING_DATE, materialId, quantity,
                materialId, locationId, quantity, revisionJson, idempotencyJson);
    }

    private JsonNode approve(String claimId, int revision, String token) throws Exception {
        return post("/api/v1/item/admin/claims/" + claimId + "/approve",
                "{\"revisionNo\":" + revision + "}", token);
    }

    private JsonNode returnQuantity(String sourceId, String sourceLineId, int quantity,
                                    String key, String overdueReason) throws Exception {
        String reasonJson = overdueReason == null ? "" : ",\"overdueReason\":\"" + overdueReason + "\"";
        return post("/api/v1/item/documents/" + sourceId + "/returns", """
                {"idempotencyKey":"%s"%s,"lines":[{"sourceMaterialDocumentLineId":"%s",
                 "returnQuantity":%d,"consumedQuantity":0,"lostQuantity":0,"damagedQuantity":0,
                 "positionText":"LIFE-1"}]}
                """.formatted(key, reasonJson, sourceLineId, quantity), adminA);
    }

    private void assertIntegerRejected(JsonNode response) {
        assertThat(response.path("code").asInt()).isEqualTo(40000);
        assertThat(response.path("msg").asText()).contains("必须为整数");
    }

    private JsonNode balance(String materialId, String locationId, String position) throws Exception {
        JsonNode inventory = get("/api/v1/item/locations/" + locationId + "/inventory", adminA);
        for (JsonNode row : inventory.path("data").path("balances")) {
            boolean positionMatches = position == null
                    ? row.path("positionText").isNull() || row.path("positionText").asText().isEmpty()
                    : position.equals(row.path("positionText").asText());
            if (materialId.equals(row.path("materialId").asText()) && positionMatches) {
                return row;
            }
        }
        return null;
    }

    private void assertBalance(String materialId, String locationId, String position,
                               String owned, String available) throws Exception {
        JsonNode row = balance(materialId, locationId, position);
        assertThat(row).as("库存余额 %s/%s/%s", materialId, locationId, position).isNotNull();
        assertThat(row.path("ownedQuantity").decimalValue()).isEqualByComparingTo(new BigDecimal(owned));
        assertThat(row.path("availableQuantity").decimalValue()).isEqualByComparingTo(new BigDecimal(available));
    }

    private HttpResponse<String> requestWithoutBody(String method, String path, String token) throws Exception {
        return HTTP.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + path))
                        .header("Authorization", "Bearer " + token)
                        .method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode get(String path, String token) throws Exception {
        return send(HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token).GET().build());
    }

    private JsonNode post(String path, String json, String token) throws Exception {
        return sendWithBody("POST", path, json, token);
    }

    private JsonNode put(String path, String json, String token) throws Exception {
        return sendWithBody("PUT", path, json, token);
    }

    private JsonNode sendWithBody(String method, String path, String json, String token) throws Exception {
        return send(HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build());
    }

    private JsonNode send(HttpRequest request) throws Exception {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }
}
