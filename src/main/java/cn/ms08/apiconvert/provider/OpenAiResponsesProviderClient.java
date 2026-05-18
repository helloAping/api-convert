package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiResponsesRequest;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.logging.LogSanitizer;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI Responses API 供应商客户端。
 * 使用 /v1/responses 端点，请求/响应格式遵循 OpenAI Responses API 规范。
 */
@Component
public class OpenAiResponsesProviderClient implements AiProviderClient {

    private final RestClient.Builder restClientBuilder;
    private final OpenAiResponsesRequestAdapter requestAdapter;
    private final OpenAiResponsesResponseAdapter responseAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiResponsesProviderClient(RestClient.Builder restClientBuilder,
                                         OpenAiResponsesRequestAdapter requestAdapter,
                                         OpenAiResponsesResponseAdapter responseAdapter) {
        this.restClientBuilder = restClientBuilder;
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
    }

    @Override
    public ProviderType type() {
        return ProviderType.OPENAI_RESPONSES;
    }

    /**
     * 将统一请求转为 Responses API 格式并转发到上游 /v1/responses 端点。
     */
    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        OpenAiResponsesRequest providerRequest = requestAdapter.toProviderRequest(request, route.providerModel(), false);
        try {
            OpenAiResponsesResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.chatPath())
                    .header("Authorization", "Bearer " + route.apiKey())
                    .body(providerRequest)
                    .retrieve()
                    .body(OpenAiResponsesResponse.class);
            if (response == null) {
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "OpenAI Responses API returned empty response");
            }
            return responseAdapter.toUnified(response);
        } catch (ProviderException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ProviderException(httpStatusToErrorCode(status), HttpStatus.BAD_GATEWAY,
                    upstreamError(prefix(status), exception));
        } catch (RestClientException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "OpenAI Responses API request failed: " + exception.getMessage());
        }
    }

    /**
     * Responses API 端点天然支持流式响应，使用 SSE 格式透传。
     */
    @Override
    public boolean supportsStreaming() {
        return true;
    }

    /**
     * 透传上游 Responses API SSE 响应；与 OpenAiCompatibleProviderClient 相同，这里刻意不用带日志拦截器的 RestClient，避免拦截器缓存无限流。
     */
    @Override
    public UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        OpenAiResponsesRequest providerRequest = requestAdapter.toProviderRequest(request, route.providerModel(), true);
        try {
            RestClient.builder()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.chatPath())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header("Authorization", "Bearer " + route.apiKey())
                    .body(providerRequest)
                    .exchange((clientRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                            int status = response.getStatusCode().value();
                            throw new ProviderException(httpStatusToErrorCode(status), HttpStatus.BAD_GATEWAY,
                                    upstreamError(prefix(status), status, body));
                        }
                        return copyResponsesStream(response.getBody(), outputStream);
                    });
            return null;
        } catch (ProviderException exception) {
            throw exception;
        } catch (UncheckedIOException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Provider stream request failed: " + exception.getMessage());
        } catch (RestClientException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Provider stream request failed: " + exception.getMessage());
        }
    }

    /**
     * 逐行透传上游 SSE 字节流，不做解析和转换。
     * Responses API 的 SSE 格式与 Chat Completions 不同（使用命名事件），
     * 但对本网关而言只需字节级透传，客户端原生支持 Responses SSE 即可正确解析。
     */
    private Void copyResponsesStream(InputStream inputStream, OutputStream outputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            outputStream.flush();
            return null;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 使用 Bearer 鉴权，从 OpenAI 风格的 data[].id 中获取模型列表。
     */
    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        try {
            String body = restClientBuilder.clone()
                    .baseUrl(request.baseUrl())
                    .build()
                    .get()
                    .uri(request.modelsPath())
                    .header("Authorization", "Bearer " + request.apiKey())
                    .retrieve()
                    .body(String.class);
            return parseOpenAiModelList(body);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ProviderException(httpStatusToErrorCode(status), HttpStatus.BAD_GATEWAY,
                    upstreamError(prefix(status), exception));
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "OpenAI Responses API models request failed: " + exception.getMessage());
        }
    }

    /**
     * OpenAI 额度查询：先尝试 /user/balance，再尝试 /dashboard/billing/credit_grants。
     */
    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        RestClientResponseException lastResponseException = null;
        for (String path : List.of("/user/balance", "/dashboard/billing/credit_grants")) {
            try {
                String body = restClientBuilder.clone()
                        .baseUrl(request.baseUrl())
                        .build()
                        .get()
                        .uri(path)
                        .header("Authorization", "Bearer " + request.apiKey())
                        .retrieve()
                        .body(String.class);
                return parseQuota(path, body);
            } catch (RestClientResponseException exception) {
                lastResponseException = exception;
            } catch (RestClientException | IllegalArgumentException exception) {
                throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                        "OpenAI Responses API quota request failed: " + exception.getMessage());
            }
        }
        if (lastResponseException != null) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                    upstreamError("OpenAI Responses API quota request failed", lastResponseException));
        }
        throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "OpenAI Responses API quota request failed");
    }

    private List<ProviderModel> parseOpenAiModelList(String body) {
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            if (!data.isArray()) {
                throw new IllegalArgumentException("OpenAI model list response missing data array");
            }
            List<ProviderModel> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    models.add(new ProviderModel(id, item.path("owned_by").asText("")));
                }
            }
            return models;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse OpenAI model list response: " + LogSanitizer.sanitizeBody(body), exception);
        }
    }

    private ProviderQuota parseQuota(String path, String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("balance_infos") && root.path("balance_infos").isArray()) {
                return parseBalanceInfos(root, body);
            }
            BigDecimal totalGranted = decimal(root, "total_granted");
            BigDecimal totalUsed = decimal(root, "total_used");
            BigDecimal totalAvailable = decimal(root, "total_available");
            if (totalGranted != null || totalUsed != null || totalAvailable != null) {
                String summary = "可用额度 " + displayAmount(totalAvailable, "USD") + "，已用 " + displayAmount(totalUsed, "USD");
                return new ProviderQuota(true, summary, totalAvailable, totalUsed, totalAvailable, "USD", LogSanitizer.sanitizeBody(body));
            }
            return new ProviderQuota(true, "已获取额度响应，但未识别到标准额度字段", null, null, null, "", LogSanitizer.sanitizeBody(body));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse quota response from " + path + ": " + LogSanitizer.sanitizeBody(body), exception);
        }
    }

    private ProviderQuota parseBalanceInfos(JsonNode root, String body) {
        JsonNode first = root.path("balance_infos").get(0);
        if (first == null || first.isMissingNode()) {
            return new ProviderQuota(true, "上游返回余额列表为空", null, null, null, "", LogSanitizer.sanitizeBody(body));
        }
        String currency = first.path("currency").asText("");
        BigDecimal balance = decimal(first, "total_balance");
        BigDecimal granted = decimal(first, "granted_balance");
        BigDecimal toppedUp = decimal(first, "topped_up_balance");
        String availableText = root.has("is_available") ? (root.path("is_available").asBoolean() ? "可用" : "不可用") : "未知";
        String summary = "账户" + availableText + "，余额 " + displayAmount(balance, currency)
                + "，赠余额 " + displayAmount(granted, currency)
                + "，充值余额 " + displayAmount(toppedUp, currency);
        return new ProviderQuota(true, summary, balance, null, balance, currency, LogSanitizer.sanitizeBody(body));
    }

    private BigDecimal decimal(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String displayAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return "未知";
        }
        return amount.stripTrailingZeros().toPlainString() + (currency == null || currency.isBlank() ? "" : " " + currency);
    }

    private String upstreamError(String prefix, RestClientResponseException exception) {
        return upstreamError(prefix, exception.getStatusCode().value(), exception.getResponseBodyAsString());
    }

    private String upstreamError(String prefix, int statusCode, String responseBody) {
        String body = LogSanitizer.sanitizeBody(responseBody);
        if (body.isBlank()) {
            return prefix + ": status=" + statusCode;
        }
        return prefix + ": status=" + statusCode + ", body=" + body;
    }

    private ErrorCode httpStatusToErrorCode(int status) {
        if (status == 401) return ErrorCode.PROVIDER_AUTH_FAILED;
        if (status == 403) return ErrorCode.PROVIDER_AUTH_FAILED;
        if (status == 429) return ErrorCode.PROVIDER_RATE_LIMITED;
        if (status >= 500) return ErrorCode.PROVIDER_UNAVAILABLE;
        if (status == 400) return ErrorCode.PROVIDER_BAD_RESPONSE;
        return ErrorCode.PROVIDER_BAD_RESPONSE;
    }

    private String prefix(int status) {
        ErrorCode code = httpStatusToErrorCode(status);
        return switch (code) {
            case PROVIDER_AUTH_FAILED -> "OpenAI Responses API authentication failed";
            case PROVIDER_RATE_LIMITED -> "OpenAI Responses API rate limited";
            case PROVIDER_UNAVAILABLE -> "OpenAI Responses API server error";
            default -> "OpenAI Responses API request failed";
        };
    }
}
