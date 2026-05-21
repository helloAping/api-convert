package cn.ms08.apiconvert;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dao.GatewayApiKeyChannelMapper;
import cn.ms08.apiconvert.dao.GatewayApiKeyMapper;
import cn.ms08.apiconvert.dao.RequestLogMapper;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.entity.GatewayApiKeyChannelEntity;
import cn.ms08.apiconvert.entity.GatewayApiKeyEntity;
import cn.ms08.apiconvert.entity.RequestLogEntity;
import cn.ms08.apiconvert.service.RoutingService;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端认证和渠道聚合接口的 Spring 集成冒烟测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiConvertApplicationTests {

    /**
     * MockMvc 在不启动真实 HTTP 服务的情况下驱动管理端接口。
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 直接验证模型到渠道的授权路由选择。
     */
    @Autowired
    private RoutingService routingService;

    /**
     * 用于构造仪表盘聚合统计所需的请求日志夹具。
     */
    @Autowired
    private RequestLogMapper requestLogMapper;

    /**
     * 用于构造仪表盘密钥名称展示所需的密钥夹具。
     */
    @Autowired
    private GatewayApiKeyMapper gatewayApiKeyMapper;

    @Autowired
    private GatewayApiKeyChannelMapper gatewayApiKeyChannelMapper;

    /**
     * 解析管理端接口返回的 JSON 响应。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 项目统一日期时间格式，测试接口出参和查询入参保持一致。
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 验证当前 Bean 装配下 Spring 应用上下文可以启动。
     */
    @Test
    void contextLoads() {
    }

    /**
     * 保护前端登录后依赖的 Authorization: Bearer token 请求头格式。
     */
    @Test
    void adminTokenWorksWithBearerAuthorizationHeader() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * 验证控制台网关信息接口能展示反向代理后的 Base URL 和当前公开调用端点。
     */
    @Test
    void adminGatewayInfoShowsBaseUrlAndSupportedEndpoints() throws Exception {
        String token = loginAsAdmin();

        String response = mockMvc.perform(get("/api/admin/gateway-info")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "gateway.example.com"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("baseUrl").asText()).isEqualTo("https://gateway.example.com");
        assertThat(data.path("endpoints").toString())
                .contains("/health", "/v1/models", "/v1/chat/completions", "/v1/messages");
    }

    @Test
    void dashboardStatsAggregatesTokenUsageByTimeAndDimensions() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String model = "dashboard-model-" + suffix;
        String channel = "dashboard-channel-" + suffix;
        String apiKeyName = "dashboard-key-" + suffix;
        GatewayApiKeyEntity apiKey = dashboardApiKey(apiKeyName, suffix);
        gatewayApiKeyMapper.insert(apiKey);
        long apiKeyId = apiKey.getId();
        RequestLogEntity first = dashboardLog("dashboard-" + suffix + "-1", apiKeyId, model, channel,
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1), 1_000_000, 200_000, 1_200_000);
        RequestLogEntity second = dashboardLog("dashboard-" + suffix + "-2", apiKeyId, model, channel,
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(1), 2_000_000, 300_000, 2_300_000);
        requestLogMapper.insert(first);
        requestLogMapper.insert(second);

        try {
            String response = mockMvc.perform(get("/api/admin/dashboard/stats")
                            .header("Authorization", "Bearer " + token)
                            .param("days", "3")
                            .param("hours", "24")
                            .param("topN", "10"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode data = objectMapper.readTree(response).path("data");
            assertThat(data.path("summary").path("totalTokens").asLong()).isGreaterThanOrEqualTo(3_500_000L);
            assertDashboardDimension(data.path("modelDistribution"), model, 3_500_000L);
            assertDashboardDimension(data.path("channelDistribution"), channel, 3_500_000L);
            assertDashboardDimension(data.path("apiKeyDistribution"), String.valueOf(apiKeyId), 3_500_000L, apiKeyName);
            assertThat(data.path("dailyTokenUsage")).isNotEmpty();
            assertThat(data.path("hourlyTokenUsage")).isNotEmpty();
            assertThat(data.path("modelSeries").toString()).contains(model);
        } finally {
            requestLogMapper.deleteById(first.getId());
            requestLogMapper.deleteById(second.getId());
            gatewayApiKeyMapper.deleteById(apiKey.getId());
        }
    }

    /**
     * 验证请求日志分页查询会正确拼接 LIMIT/OFFSET 并返回指定页数据。
     */
    @Test
    void requestLogSearchSupportsPagination() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String model = "request-log-page-model-" + suffix;
        String channel = "request-log-page-channel-" + suffix;
        GatewayApiKeyEntity apiKey = dashboardApiKey("request-log-page-key-" + suffix, suffix);
        gatewayApiKeyMapper.insert(apiKey);
        RequestLogEntity first = dashboardLog("request-log-page-" + suffix + "-1", apiKey.getId(), model, channel,
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(3), 10, 20, 30);
        RequestLogEntity second = dashboardLog("request-log-page-" + suffix + "-2", apiKey.getId(), model, channel,
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(2), 11, 21, 32);
        RequestLogEntity third = dashboardLog("request-log-page-" + suffix + "-3", apiKey.getId(), model, channel,
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(1), 12, 22, 34);
        requestLogMapper.insert(first);
        requestLogMapper.insert(second);
        requestLogMapper.insert(third);

        try {
            String response = mockMvc.perform(get("/api/admin/request-logs")
                            .header("Authorization", "Bearer " + token)
                            .param("publicModel", model)
                            .param("page", "2")
                            .param("pageSize", "1"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode data = objectMapper.readTree(response).path("data");
            assertThat(data.path("total").asLong()).isEqualTo(3);
            assertThat(data.path("page").asInt()).isEqualTo(2);
            assertThat(data.path("pageSize").asInt()).isEqualTo(1);
            assertThat(data.path("records")).hasSize(1);
            assertThat(data.path("records").get(0).path("requestId").asText()).isEqualTo(second.getRequestId());
        } finally {
            for (RequestLogEntity log : List.of(first, second, third)) {
                if (log.getId() != null) {
                    requestLogMapper.deleteById(log.getId());
                }
            }
            gatewayApiKeyMapper.deleteById(apiKey.getId());
        }
    }

    /**
     * 验证 OpenAI 兼容响应中的缓存读取 token 能进入统一用量统计。
     */
    @Test
    void openAiUsageReadsCachedInputTokens() throws Exception {
        OpenAiChatCompletionResponse response = objectMapper.readValue("""
                {
                  "id": "chatcmpl-test",
                  "model": "provider-model",
                  "choices": [],
                  "usage": {
                    "prompt_tokens": 120,
                    "completion_tokens": 30,
                    "total_tokens": 150,
                    "prompt_tokens_details": {
                      "cached_tokens": 80
                    }
                  }
                }
                """, OpenAiChatCompletionResponse.class);

        var unified = new OpenAiResponseAdapter().toUnified(response);

        assertThat(unified.usage().inputTokens()).isEqualTo(120);
        assertThat(unified.usage().outputTokens()).isEqualTo(30);
        assertThat(unified.usage().totalTokens()).isEqualTo(150);
        assertThat(unified.usage().cacheReadInputTokens()).isEqualTo(80);
    }

    /**
     * 验证渠道聚合创建、查询和删除流程，并确保不会暴露原始 API Key。
     */
    @Test
    void adminCanCreateAndListCustomChannel() throws Exception {
        String token = loginAsAdmin();
        String code = "test-channel-" + UUID.randomUUID();
        String alias = code + "-alias";

        String createResponse = mockMvc.perform(post("/api/admin/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "测试渠道",
                                  "type": "OPENAI_COMPATIBLE",
                                  "baseUrl": "https://api.example.com",
                                  "chatPath": "/v1/chat/completions",
                                  "modelsPath": "/v1/models",
                                  "apiKey": "sk-test-channel-key",
                                  "priority": 100,
                                  "status": "ACTIVE",
                                  "modelPrefix": "%s",
                                  "models": [
                                    {"providerModel": "deepseek-chat"},
                                    {"publicName": "%s", "providerModel": "deepseek-coder"}
                                  ],
                                  "enabled": true
                                }
                                """.formatted(code, code, alias)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse).path("data");
        long channelId = created.path("id").asLong();

        try {
            String listResponse = mockMvc.perform(get("/api/admin/channels")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(listResponse).contains(code, "https://api.example.com", "/v1/chat/completions");
            assertThat(listResponse).contains(code + "/deepseek-chat", alias, "\"modelCount\":2");

            String modelsResponse = mockMvc.perform(get("/api/admin/models")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(modelsResponse).contains(code + "/deepseek-chat", alias);

            mockMvc.perform(post("/api/admin/channels")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code": "%s-duplicate",
                                      "name": "重复别名渠道",
                                      "type": "OPENAI_COMPATIBLE",
                                      "baseUrl": "https://api.example.com",
                                      "chatPath": "/v1/chat/completions",
                                      "modelsPath": "/v1/models",
                                      "apiKey": "sk-test-channel-key",
                                      "models": [
                                        {"publicName": "%s", "providerModel": "deepseek-chat"}
                                      ],
                                      "enabled": true
                                    }
                                    """.formatted(code, alias)))
                    .andExpect(status().isBadRequest());
        } finally {
            mockMvc.perform(delete("/api/admin/channels/" + channelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    /**
     * 验证同一渠道不能保存重复上游模型，避免前端自定义输入重复项造成模型管理重复记录。
     */
    @Test
    void adminRejectsDuplicateProviderModelsInOneChannel() throws Exception {
        String token = loginAsAdmin();
        String code = "duplicate-provider-model-" + UUID.randomUUID();

        String response = mockMvc.perform(post("/api/admin/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "重复上游模型渠道",
                                  "type": "OPENAI_COMPATIBLE",
                                  "baseUrl": "https://api.example.com",
                                  "chatPath": "/v1/chat/completions",
                                  "modelsPath": "/v1/models",
                                  "apiKey": "sk-test-channel-key",
                                  "models": [
                                    {"providerModel": "custom-model"},
                                    {"providerModel": " custom-model "}
                                  ],
                                  "enabled": true
                                }
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("上游模型名重复: custom-model");
    }

    /**
     * 验证编辑渠道时 API Key 留空，模型发现接口会使用数据库中已保存的供应商密钥。
     */
    @Test
    void gptAuthChannelCanUploadAuthJsonAndRoute() throws Exception {
        String token = loginAsAdmin();
        String code = "gpt-auth-" + UUID.randomUUID();
        String model = "gpt-auth-model-" + UUID.randomUUID();
        long channelId = 0;

        try {
            String createResponse = mockMvc.perform(post("/api/admin/channels")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code": "%s",
                                      "name": "GPT Auth",
                                      "type": "GPT_AUTH",
                                      "models": [
                                        {"publicName": "%s", "providerModel": "%s"}
                                      ],
                                      "enabled": true
                                    }
                                    """.formatted(code, model, model)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            channelId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

            String oauthStartResponse = mockMvc.perform(post("/api/admin/channels/" + channelId + "/auth/start")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode oauthStart = objectMapper.readTree(oauthStartResponse).path("data");
            assertThat(oauthStart.path("authorizationUrl").asText())
                    .contains("https://auth.openai.com/oauth/authorize")
                    .contains("client_id=app_EMoamEEZ73f0CkXaXp7hrann")
                    .contains("redirect_uri=http://localhost:1455/auth/callback")
                    .contains("code_challenge=")
                    .contains("codex_cli_simplified_flow=true");

            MockMultipartFile authFile = new MockMultipartFile(
                    "file",
                    "auth.json",
                    "application/json",
                    """
                            {
                              "access_token": "oauth-access-token-secret",
                              "refresh_token": "oauth-refresh-token-secret",
                              "email": "owner@example.com",
                              "expires_at": "2099-01-01 00:00:00"
                            }
                            """.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            String uploadResponse = mockMvc.perform(multipart("/api/admin/channels/" + channelId + "/auth/upload")
                            .file(authFile)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode authStatus = objectMapper.readTree(uploadResponse).path("data");
            assertThat(authStatus.path("authStatus").asText()).isEqualTo("AUTHORIZED");
            assertThat(authStatus.path("hasAuthFile").asBoolean()).isTrue();
            assertThat(uploadResponse).doesNotContain("oauth-access-token-secret", "oauth-refresh-token-secret");

            ModelRoute route = routingService.resolve(model, Set.of(code));
            assertThat(route.providerType()).isEqualTo(cn.ms08.apiconvert.provider.ProviderType.GPT_AUTH);
            assertThat(route.baseUrl()).isEqualTo("https://api.openai.com");
            assertThat(route.chatPath()).isEqualTo("/v1/chat/completions");
            assertThat(route.authFilePath()).contains("GPT_AUTH");
        } finally {
            if (channelId > 0) {
                mockMvc.perform(delete("/api/admin/channels/" + channelId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void editingChannelCanFetchModelsWithSavedApiKeyWhenFormKeyIsBlank() throws Exception {
        String token = loginAsAdmin();
        String code = "fetch-models-" + UUID.randomUUID();
        String savedApiKey = "sk-saved-model-fetch-key";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (!("Bearer " + savedApiKey).equals(authorization)) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            byte[] body = """
                    {"data":[{"id":"saved-key-model","owned_by":"test-upstream"}]}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        long channelId = 0;

        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            String createResponse = mockMvc.perform(post("/api/admin/channels")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code": "%s",
                                      "name": "模型发现密钥回退",
                                      "type": "OPENAI_COMPATIBLE",
                                      "baseUrl": "%s",
                                      "chatPath": "/v1/chat/completions",
                                      "modelsPath": "/v1/models",
                                      "apiKey": "%s",
                                      "models": [],
                                      "enabled": true
                                    }
                                    """.formatted(code, baseUrl, savedApiKey)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            channelId = objectMapper.readTree(createResponse).path("data").path("id").asLong();

            String modelsResponse = mockMvc.perform(post("/api/admin/channels/models")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "type": "OPENAI_COMPATIBLE",
                                      "channelId": %d,
                                      "baseUrl": "%s",
                                      "modelsPath": "/v1/models",
                                      "apiKey": ""
                                    }
                                    """.formatted(channelId, baseUrl)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(modelsResponse).contains("saved-key-model", "test-upstream");
        } finally {
            if (channelId > 0) {
                mockMvc.perform(delete("/api/admin/channels/" + channelId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
            server.stop(0);
        }
    }

    /**
     * 验证同一个默认对外模型名可以由多个渠道承载，模型管理中只聚合展示一次。
     */
    @Test
    void duplicateDefaultModelCanBeServedByMultipleChannels() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String firstCode = "shared-a-" + suffix;
        String secondCode = "shared-b-" + suffix;
        String sharedModel = "shared-model-" + suffix;
        long firstId = createChannel(token, firstCode, sharedModel);
        long secondId = createChannel(token, secondCode, sharedModel);
        final long[] scopedKeyId = {0};

        try {
            String keyResponse = mockMvc.perform(post("/api/admin/api-keys")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "限定渠道密钥",
                                      "channelCodes": ["%s"]
                                    }
                                    """.formatted(firstCode)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode keyData = objectMapper.readTree(keyResponse).path("data");
            scopedKeyId[0] = keyData.path("id").asLong();
            assertThat(keyData.path("channelCodes").toString()).contains(firstCode);

            String modelsResponse = mockMvc.perform(get("/api/admin/models")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode models = objectMapper.readTree(modelsResponse).path("data");
            JsonNode shared = null;
            for (JsonNode model : models) {
                if (sharedModel.equals(model.path("publicName").asText())) {
                    shared = model;
                    break;
                }
            }
            assertThat(shared).isNotNull();
            assertThat(shared.path("channelCount").asLong()).isEqualTo(2);
            assertThat(shared.path("providerCodes").toString()).contains(firstCode, secondCode);
            assertThat(routingService.resolve(sharedModel, Set.of(firstCode)).providerCode()).isEqualTo(firstCode);
            assertThat(routingService.resolve(sharedModel, Set.of(secondCode)).providerCode()).isEqualTo(secondCode);
        } finally {
            if (scopedKeyId[0] > 0) {
                mockMvc.perform(delete("/api/admin/api-keys/" + scopedKeyId[0])
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(delete("/api/admin/channels/" + firstId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/admin/channels/" + secondId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void toolRequestsPreferToolCapableChannelForSharedPublicModel() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String noToolCode = "shared-no-tool-" + suffix;
        String toolCode = "shared-tool-" + suffix;
        String sharedModel = "shared-tool-model-" + suffix;
        long noToolId = createChannel(token, noToolCode, sharedModel, false);
        long toolId = createChannel(token, toolCode, sharedModel, true);

        try {
            UnifiedChatRequest request = new UnifiedChatRequest(sharedModel, List.of(), false, null, null, null,
                    Map.of("tools", List.of(Map.of("type", "function", "name", "shell"))));

            for (int i = 0; i < 10; i++) {
                assertThat(routingService.resolve(request, Set.of()).providerCode()).isEqualTo(toolCode);
            }
            assertThat(routingService.resolve(request, Set.of(noToolCode)).providerCode()).isEqualTo(noToolCode);
        } finally {
            mockMvc.perform(delete("/api/admin/channels/" + noToolId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/admin/channels/" + toolId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void adminCanUpdateRoutingConfig() throws Exception {
        String token = loginAsAdmin();

        try {
            String response = mockMvc.perform(put("/api/admin/system-config/routing")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "mode": "ROUND_ROBIN",
                                      "failureThreshold": 2,
                                      "failureCooldownMinutes": 3,
                                      "stickyTtlMinutes": 60
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode data = objectMapper.readTree(response).path("data");
            assertThat(data.path("mode").asText()).isEqualTo("ROUND_ROBIN");
            assertThat(data.path("failureThreshold").asInt()).isEqualTo(2);
            assertThat(data.path("failureCooldownMinutes").asInt()).isEqualTo(3);
            assertThat(data.path("stickyTtlMinutes").asInt()).isEqualTo(60);

            String getResponse = mockMvc.perform(get("/api/admin/system-config/routing")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(objectMapper.readTree(getResponse).path("data").path("mode").asText()).isEqualTo("ROUND_ROBIN");
        } finally {
            restoreDefaultRoutingConfig(token);
        }
    }

    @Test
    void routingServiceSupportsRoundRobinMode() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String firstCode = "rr-a-" + suffix;
        String secondCode = "rr-b-" + suffix;
        String model = "rr-model-" + suffix;
        long firstId = createChannel(token, firstCode, model);
        long secondId = createChannel(token, secondCode, model);

        try {
            updateRoutingConfig(token, "ROUND_ROBIN", 0, 0, 1440);
            UnifiedChatRequest request = new UnifiedChatRequest(model, List.of(), false, null, null, null, Map.of());

            assertThat(routingService.resolve(request, 1001L, Set.of(), null).providerCode()).isEqualTo(firstCode);
            assertThat(routingService.resolve(request, 1001L, Set.of(), null).providerCode()).isEqualTo(secondCode);
            assertThat(routingService.resolve(request, 1001L, Set.of(), null).providerCode()).isEqualTo(firstCode);
            assertThat(routingService.resolve(request, 1001L, Set.of(), null).providerCode()).isEqualTo(secondCode);
        } finally {
            restoreDefaultRoutingConfig(token);
            mockMvc.perform(delete("/api/admin/channels/" + firstId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/admin/channels/" + secondId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void routingServiceSupportsWeightedModeByChannelPriority() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String lightCode = "weight-a-" + suffix;
        String heavyCode = "weight-b-" + suffix;
        String model = "weight-model-" + suffix;
        long lightId = createChannel(token, lightCode, model, false, 1);
        long heavyId = createChannel(token, heavyCode, model, false, 3);

        try {
            updateRoutingConfig(token, "WEIGHTED", 0, 0, 1440);
            UnifiedChatRequest request = new UnifiedChatRequest(model, List.of(), false, null, null, null, Map.of());
            int lightCount = 0;
            int heavyCount = 0;

            for (int i = 0; i < 8; i++) {
                String providerCode = routingService.resolve(request, 1002L, Set.of(), null).providerCode();
                if (lightCode.equals(providerCode)) {
                    lightCount++;
                } else if (heavyCode.equals(providerCode)) {
                    heavyCount++;
                }
            }

            assertThat(lightCount).isEqualTo(2);
            assertThat(heavyCount).isEqualTo(6);
        } finally {
            restoreDefaultRoutingConfig(token);
            mockMvc.perform(delete("/api/admin/channels/" + lightId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/admin/channels/" + heavyId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void stickyRoutingKeepsSessionAndFailureCooldownSwitchesChannelForSameApiKey() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String firstCode = "sticky-a-" + suffix;
        String secondCode = "sticky-b-" + suffix;
        String model = "sticky-model-" + suffix;
        long firstId = createChannel(token, firstCode, model);
        long secondId = createChannel(token, secondCode, model);

        try {
            updateRoutingConfig(token, "SESSION_STICKY", 1, 5, 60);
            UnifiedChatRequest request = new UnifiedChatRequest(model, List.of(), false, null, null, null, Map.of());
            Long apiKeyId = 1003L;
            String sessionKey = "session-" + suffix;

            ModelRoute firstRoute = routingService.resolve(request, apiKeyId, Set.of(), sessionKey);
            ModelRoute stickyRoute = routingService.resolve(request, apiKeyId, Set.of(), sessionKey);
            assertThat(stickyRoute.providerCode()).isEqualTo(firstRoute.providerCode());

            routingService.recordFailure(apiKeyId, request, firstRoute, sessionKey);
            ModelRoute switchedRoute = routingService.resolve(request, apiKeyId, Set.of(), sessionKey);
            assertThat(switchedRoute.providerCode()).isNotEqualTo(firstRoute.providerCode());

            ModelRoute otherKeyRoute = routingService.resolve(request, 1004L, Set.of(firstRoute.providerCode()), "other-" + suffix);
            assertThat(otherKeyRoute.providerCode()).isEqualTo(firstRoute.providerCode());
        } finally {
            restoreDefaultRoutingConfig(token);
            mockMvc.perform(delete("/api/admin/channels/" + firstId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(delete("/api/admin/channels/" + secondId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    /**
     * 验证模型额度单价和密钥余额会在请求上游前参与预检，余额不足时直接返回额度不足。
     */
    @Test
    void chatRequestIsRejectedWhenApiKeyQuotaIsInsufficient() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String code = "quota-channel-" + suffix;
        String model = "quota-model-" + suffix;
        long channelId = 0;
        long apiKeyId = 0;

        try {
            String channelResponse = mockMvc.perform(post("/api/admin/channels")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code": "%s",
                                      "name": "额度测试渠道",
                                      "type": "OPENAI_COMPATIBLE",
                                      "baseUrl": "https://api.example.com",
                                      "chatPath": "/v1/chat/completions",
                                      "modelsPath": "/v1/models",
                                      "apiKey": "sk-test-channel-key",
                                      "models": [
                                        {
                                          "publicName": "%s",
                                          "providerModel": "%s",
                                          "inputQuotaPerMillion": 1000000
                                        }
                                      ],
                                      "enabled": true
                                    }
                                    """.formatted(code, model, model)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            channelId = objectMapper.readTree(channelResponse).path("data").path("id").asLong();

            String keyResponse = mockMvc.perform(post("/api/admin/api-keys")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "低余额密钥",
                                      "channelCodes": ["%s"],
                                      "quotaBalance": 1
                                    }
                                    """.formatted(code)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode key = objectMapper.readTree(keyResponse).path("data");
            apiKeyId = key.path("id").asLong();
            String rawKey = key.path("rawKey").asText();

            String response = mockMvc.perform(post("/v1/chat/completions")
                            .header("Authorization", "Bearer " + rawKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "model": "%s",
                                      "messages": [{"role": "user", "content": "这是一段用于触发额度预检的较长测试输入"}],
                                      "stream": false
                                    }
                                    """.formatted(model)))
                    .andExpect(status().isPaymentRequired())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(response).contains("quota_insufficient");
        } finally {
            if (apiKeyId > 0) {
                mockMvc.perform(delete("/api/admin/api-keys/" + apiKeyId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
            if (channelId > 0) {
                mockMvc.perform(delete("/api/admin/channels/" + channelId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
    }

    /**
     * 验证密钥可以同时保存额度限制、请求数限制和模型白名单。
     */
    @Test
    void apiKeyCanPersistMultipleLimitsAndModelAllowlist() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String code = "limit-config-" + suffix;
        String model = "limit-config-model-" + suffix;
        long channelId = createChannel(token, code, model);
        long apiKeyId = 0;

        try {
            String keyResponse = mockMvc.perform(post("/api/admin/api-keys")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "多限制密钥",
                                      "channelCodes": ["%s"],
                                      "modelNames": ["%s"],
                                      "limits": [
                                        {"limitType": "QUOTA", "windowValue": 2, "windowUnit": "HOUR", "limitValue": 100},
                                        {"limitType": "QUOTA", "windowValue": 1, "windowUnit": "DAY", "limitValue": 500},
                                        {"limitType": "REQUEST", "windowValue": 1, "windowUnit": "MINUTE", "limitValue": 60}
                                      ]
                                    }
                                    """.formatted(code, model)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode key = objectMapper.readTree(keyResponse).path("data");
            apiKeyId = key.path("id").asLong();
            assertThat(key.path("channelCodes").toString()).contains(code);
            assertThat(key.path("modelNames").toString()).contains(model);
            assertThat(key.path("limits")).hasSize(3);
            assertThat(key.path("limits").toString()).contains("QUOTA", "REQUEST", "MINUTE", "HOUR", "DAY");
        } finally {
            if (apiKeyId > 0) {
                mockMvc.perform(delete("/api/admin/api-keys/" + apiKeyId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
            mockMvc.perform(delete("/api/admin/channels/" + channelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    /**
     * 验证删除渠道时会同步清理密钥渠道授权，避免管理端残留不可用渠道。
     */
    @Test
    void deletingChannelRemovesApiKeyChannelAllowlistEntry() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String code = "delete-channel-scope-" + suffix;
        String model = "delete-channel-scope-model-" + suffix;
        long channelId = createChannel(token, code, model);
        long apiKeyId = 0;

        try {
            String keyResponse = mockMvc.perform(post("/api/admin/api-keys")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "删除渠道同步授权密钥",
                                      "channelCodes": ["%s"]
                                    }
                                    """.formatted(code)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            apiKeyId = objectMapper.readTree(keyResponse).path("data").path("id").asLong();
            assertThat(countApiKeyChannelScopes(code)).isEqualTo(1);

            mockMvc.perform(delete("/api/admin/channels/" + channelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            channelId = 0;

            assertThat(countApiKeyChannelScopes(code)).isZero();
            assertThat(gatewayApiKeyMapper.selectById(apiKeyId).getStatus()).isEqualTo("DISABLED");
        } finally {
            if (apiKeyId > 0) {
                mockMvc.perform(delete("/api/admin/api-keys/" + apiKeyId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
            if (channelId > 0) {
                mockMvc.perform(delete("/api/admin/channels/" + channelId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
    }

    /**
     * 验证同一限制类型下相同窗口单位只能配置一条，避免出现多个 n 小时或 n 天限制。
     */
    @Test
    void apiKeyRejectsDuplicateLimitWindowUnitWithinSameType() throws Exception {
        String token = loginAsAdmin();

        String response = mockMvc.perform(post("/api/admin/api-keys")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "重复限制密钥",
                                  "limits": [
                                    {"limitType": "QUOTA", "windowValue": 1, "windowUnit": "HOUR", "limitValue": 100},
                                    {"limitType": "QUOTA", "windowValue": 2, "windowUnit": "HOUR", "limitValue": 200}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("同一限制类型下每个窗口单位只能配置一条限制", "额度/HOUR");
    }

    /**
     * 验证请求数限制计入已通过路由的失败请求，后续请求会被滑动窗口拒绝。
     */
    @Test
    void requestLimitCountsFailedUpstreamRequests() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String code = "request-limit-" + suffix;
        String model = "request-limit-model-" + suffix;
        long channelId = 0;
        long apiKeyId = 0;

        try {
            String channelResponse = mockMvc.perform(post("/api/admin/channels")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code": "%s",
                                      "name": "请求数限制渠道",
                                      "type": "OPENAI_COMPATIBLE",
                                      "baseUrl": "http://127.0.0.1:1",
                                      "chatPath": "/v1/chat/completions",
                                      "modelsPath": "/v1/models",
                                      "apiKey": "sk-test-channel-key",
                                      "models": [
                                        {"publicName": "%s", "providerModel": "%s"}
                                      ],
                                      "enabled": true
                                    }
                                    """.formatted(code, model, model)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            channelId = objectMapper.readTree(channelResponse).path("data").path("id").asLong();

            String keyResponse = mockMvc.perform(post("/api/admin/api-keys")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "请求数限制密钥",
                                      "channelCodes": ["%s"],
                                      "modelNames": ["%s"],
                                      "limits": [
                                        {"limitType": "REQUEST", "windowValue": 1, "windowUnit": "MINUTE", "limitValue": 1}
                                      ]
                                    }
                                    """.formatted(code, model)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode key = objectMapper.readTree(keyResponse).path("data");
            apiKeyId = key.path("id").asLong();
            String rawKey = key.path("rawKey").asText();

            mockMvc.perform(post("/v1/chat/completions")
                            .header("Authorization", "Bearer " + rawKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "model": "%s",
                                      "messages": [{"role": "user", "content": "hello"}],
                                      "stream": false
                                    }
                                    """.formatted(model)))
                    .andExpect(status().is5xxServerError());

            String response = mockMvc.perform(post("/v1/chat/completions")
                            .header("Authorization", "Bearer " + rawKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "model": "%s",
                                      "messages": [{"role": "user", "content": "hello again"}],
                                      "stream": false
                                    }
                                    """.formatted(model)))
                    .andExpect(status().isTooManyRequests())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertThat(response).contains("sliding window request limit");
        } finally {
            if (apiKeyId > 0) {
                mockMvc.perform(delete("/api/admin/api-keys/" + apiKeyId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
            if (channelId > 0) {
                mockMvc.perform(delete("/api/admin/channels/" + channelId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
    }

    /**
     * 验证模型白名单也约束 channel/model 直连写法，不能绕过对外模型授权。
     */
    @Test
    void modelAllowlistAlsoRestrictsDirectChannelModelRouting() throws Exception {
        String token = loginAsAdmin();
        String suffix = UUID.randomUUID().toString();
        String code = "model-scope-" + suffix;
        String allowed = "model-scope-allowed-" + suffix;
        String blocked = "model-scope-blocked-" + suffix;
        long channelId = 0;

        try {
            String channelResponse = mockMvc.perform(post("/api/admin/channels")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "code": "%s",
                                      "name": "模型限制渠道",
                                      "type": "OPENAI_COMPATIBLE",
                                      "baseUrl": "https://api.example.com",
                                      "chatPath": "/v1/chat/completions",
                                      "modelsPath": "/v1/models",
                                      "apiKey": "sk-test-channel-key",
                                      "models": [
                                        {"publicName": "%s", "providerModel": "%s-provider"},
                                        {"publicName": "%s", "providerModel": "%s-provider"}
                                      ],
                                      "enabled": true
                                    }
                                    """.formatted(code, allowed, allowed, blocked, blocked)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            channelId = objectMapper.readTree(channelResponse).path("data").path("id").asLong();

            ModelRoute route = routingService.resolve(code + "/" + allowed + "-provider", Set.of(code), Set.of(allowed));
            assertThat(route.publicModel()).isEqualTo(allowed);
            assertThatThrownBy(() -> routingService.resolve(code + "/" + blocked + "-provider", Set.of(code), Set.of(allowed)))
                    .hasMessageContaining("Model not found or no active channel");
        } finally {
            if (channelId > 0) {
                mockMvc.perform(delete("/api/admin/channels/" + channelId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
    }

    /**
     * 验证管理端可以在模型聚合页同步更新同名模型的额度单价。
     */
    @Test
    void adminCanUpdateModelQuotaPricing() throws Exception {
        String token = loginAsAdmin();
        String code = "quota-config-" + UUID.randomUUID();
        String model = "quota-config-model-" + UUID.randomUUID();
        long channelId = createChannel(token, code, model);

        try {
            String modelsResponse = mockMvc.perform(get("/api/admin/models")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode modelRow = null;
            for (JsonNode row : objectMapper.readTree(modelsResponse).path("data")) {
                if (model.equals(row.path("publicName").asText())) {
                    modelRow = row;
                    break;
                }
            }
            assertThat(modelRow).isNotNull();
            long modelId = modelRow.path("id").asLong();

            String updateResponse = mockMvc.perform(put("/api/admin/models/" + modelId + "/quota")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "inputQuotaPerMillion": 2.5,
                                      "outputQuotaPerMillion": 10,
                                      "cacheReadQuotaPerMillion": 1
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode updated = objectMapper.readTree(updateResponse).path("data");
            assertThat(updated.path("inputQuotaPerMillion").decimalValue()).isEqualByComparingTo("2.5");
            assertThat(updated.path("outputQuotaPerMillion").decimalValue()).isEqualByComparingTo("10");
            assertThat(updated.path("cacheReadQuotaPerMillion").decimalValue()).isEqualByComparingTo("1");
        } finally {
            mockMvc.perform(delete("/api/admin/channels/" + channelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    /**
     * 验证模型管理页关闭聚合模型后，同名渠道映射都会被禁用，路由不再命中该模型。
     */
    @Test
    void adminCanDisableModelFromModelManagement() throws Exception {
        String token = loginAsAdmin();
        String code = "disable-model-" + UUID.randomUUID();
        String model = "disable-model-public-" + UUID.randomUUID();
        long channelId = createChannel(token, code, model);

        try {
            long modelId = findAdminModelId(token, model);

            String updateResponse = mockMvc.perform(put("/api/admin/models/" + modelId + "/enabled")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"enabled": false}
                                    """))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode updated = objectMapper.readTree(updateResponse).path("data");
            assertThat(updated.path("enabled").asBoolean()).isFalse();
            assertThatThrownBy(() -> routingService.resolve(model, Set.of(code)))
                    .hasMessageContaining("Model not found or no active channel");
        } finally {
            mockMvc.perform(delete("/api/admin/channels/" + channelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    /**
     * 验证流式对话在路由失败时仍会记录协议、接口类型、流式标记、耗时和错误码。
     */
    @Test
    void streamingChatRoutingFailureIsRecordedInRequestLog() throws Exception {
        String token = loginAsAdmin();
        String model = "stream-test-" + UUID.randomUUID();
        String apiKeyName = "stream-routing-failure-key";
        long apiKeyId = 0;
        String apiKeyPreview = null;

        try {
            String keyResponse = mockMvc.perform(post("/api/admin/api-keys")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "%s"}
                                    """.formatted(apiKeyName)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode keyData = objectMapper.readTree(keyResponse).path("data");
            apiKeyId = keyData.path("id").asLong();
            apiKeyPreview = keyData.path("keyPreview").asText();
            String rawKey = keyData.path("rawKey").asText();

            mockMvc.perform(post("/v1/chat/completions")
                            .header("Authorization", "Bearer " + rawKey)
                            .accept(MediaType.ALL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "model": "%s",
                                      "messages": [{"role": "user", "content": "hello"}],
                                      "stream": true
                                    }
                                    """.formatted(model)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("model_not_found")));

            String logsResponse = mockMvc.perform(get("/api/admin/request-logs")
                            .header("Authorization", "Bearer " + token)
                            .param("publicModel", model)
                            .param("sourceProtocol", "openai")
                            .param("requestType", "chat_completions")
                            .param("startTime", "2025-05-15 00:00:00")
                            .param("endTime", "2099-12-31 23:59:59"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            JsonNode record = objectMapper.readTree(logsResponse).path("data").path("records").get(0);
            assertThat(record.path("gatewayApiKeyId").asLong()).isEqualTo(apiKeyId);
            assertThat(record.path("gatewayApiKeyName").asText()).isEqualTo(apiKeyName);
            assertThat(record.path("gatewayApiKeyPreview").asText()).isEqualTo(apiKeyPreview);
            assertThat(record.path("publicModel").asText()).isEqualTo(model);
            assertThat(record.path("sourceProtocol").asText()).isEqualTo("openai");
            assertThat(record.path("requestType").asText()).isEqualTo("chat_completions");
            assertThat(record.path("stream").asBoolean()).isTrue();
            assertThat(record.path("success").asBoolean()).isFalse();
            assertThat(record.path("httpStatus").asInt()).isEqualTo(400);
            assertThat(record.path("latencyMs").asLong()).isGreaterThanOrEqualTo(0);
            assertThat(record.path("errorCode").asText()).isEqualTo("MODEL_NOT_FOUND");
            String createdAt = record.path("createdAt").asText();
            assertThat(createdAt).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
            LocalDateTime shanghaiNow = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            assertThat(LocalDateTime.parse(createdAt, DATE_TIME_FORMATTER))
                    .isBetween(shanghaiNow.minusMinutes(1), shanghaiNow.plusMinutes(1));
        } finally {
            if (apiKeyId > 0) {
                mockMvc.perform(delete("/api/admin/api-keys/" + apiKeyId)
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
    }

    /**
     * 使用初始化管理员账号登录，并只返回测试所需的 token。
     */
    private String loginAsAdmin() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(loginResponse);
        return root.path("data").path("token").asText();
    }

    private void restoreDefaultRoutingConfig(String token) throws Exception {
        updateRoutingConfig(token, "RANDOM", 0, 0, 1440);
    }

    private void updateRoutingConfig(String token, String mode, int failureThreshold,
                                     int failureCooldownMinutes, int stickyTtlMinutes) throws Exception {
        mockMvc.perform(put("/api/admin/system-config/routing")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "%s",
                                  "failureThreshold": %d,
                                  "failureCooldownMinutes": %d,
                                  "stickyTtlMinutes": %d
                                }
                                """.formatted(mode, failureThreshold, failureCooldownMinutes, stickyTtlMinutes)))
                .andExpect(status().isOk());
    }

    private RequestLogEntity dashboardLog(String requestId, Long apiKeyId, String model, String channel,
                                          LocalDateTime createdAt, int inputTokens, int outputTokens, int totalTokens) {
        RequestLogEntity entity = new RequestLogEntity();
        entity.setRequestId(requestId);
        entity.setGatewayApiKeyId(apiKeyId);
        entity.setSourceProtocol("openai");
        entity.setRequestType("chat_completions");
        entity.setProviderCode(channel);
        entity.setProviderType("OPENAI_COMPATIBLE");
        entity.setPublicModel(model);
        entity.setProviderModel(model);
        entity.setStream(false);
        entity.setSuccess(true);
        entity.setHttpStatus(200);
        entity.setLatencyMs(123L);
        entity.setInputTokens(inputTokens);
        entity.setCacheReadInputTokens(0);
        entity.setOutputTokens(outputTokens);
        entity.setTotalTokens(totalTokens);
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private GatewayApiKeyEntity dashboardApiKey(String name, String suffix) {
        GatewayApiKeyEntity entity = new GatewayApiKeyEntity();
        entity.setName(name);
        entity.setRawKey("sk-dashboard-" + suffix);
        entity.setApiKeyHash("dashboard-hash-" + suffix);
        entity.setKeyPreview("sk-****" + suffix.substring(0, 4));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private void assertDashboardDimension(JsonNode distribution, String key, long expectedTotalTokens) {
        assertDashboardDimension(distribution, key, expectedTotalTokens, null);
    }

    private void assertDashboardDimension(JsonNode distribution, String key, long expectedTotalTokens, String expectedName) {
        for (JsonNode item : distribution) {
            if (key.equals(item.path("key").asText())) {
                assertThat(item.path("totalTokens").asLong()).isEqualTo(expectedTotalTokens);
                assertThat(item.path("requestCount").asLong()).isEqualTo(2);
                if (expectedName != null) {
                    assertThat(item.path("name").asText()).isEqualTo(expectedName);
                }
                return;
            }
        }
        throw new AssertionError("dashboard dimension not found: " + key);
    }

    /**
     * 创建只包含一个默认模型名的测试渠道，返回渠道主键以便清理。
     */
    private long createChannel(String token, String code, String providerModel) throws Exception {
        return createChannel(token, code, providerModel, false, 100);
    }

    private long createChannel(String token, String code, String providerModel, boolean toolsSupport) throws Exception {
        return createChannel(token, code, providerModel, toolsSupport, 100);
    }

    private long createChannel(String token, String code, String providerModel, boolean toolsSupport, int priority) throws Exception {
        String createResponse = mockMvc.perform(post("/api/admin/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "type": "OPENAI_COMPATIBLE",
                                  "baseUrl": "https://api.example.com",
                                  "chatPath": "/v1/chat/completions",
                                  "modelsPath": "/v1/models",
                                  "apiKey": "sk-test-channel-key",
                                  "priority": %d,
                                  "models": [
                                    {"providerModel": "%s", "toolsSupport": %s}
                                  ],
                                  "enabled": true
                                }
                                """.formatted(code, code, priority, providerModel, toolsSupport)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(createResponse).path("data").path("id").asLong();
    }

    private long countApiKeyChannelScopes(String channelCode) {
        Long count = gatewayApiKeyChannelMapper.selectCount(new LambdaQueryWrapper<GatewayApiKeyChannelEntity>()
                .eq(GatewayApiKeyChannelEntity::getChannelCode, channelCode));
        return count == null ? 0 : count;
    }

    /**
     * 从管理端聚合模型列表里查找指定对外模型名对应的模型记录 ID。
     */
    private long findAdminModelId(String token, String publicModel) throws Exception {
        String modelsResponse = mockMvc.perform(get("/api/admin/models")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (JsonNode row : objectMapper.readTree(modelsResponse).path("data")) {
            if (publicModel.equals(row.path("publicName").asText())) {
                return row.path("id").asLong();
            }
        }
        throw new AssertionError("model not found: " + publicModel);
    }

}
