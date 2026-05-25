package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.logging.LogSanitizer;
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
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
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 适配遵循 OpenAI 对话补全和模型列表结构的上游 API。
 */
@Component
public class OpenAiCompatibleProviderClient implements AiProviderClient {

    /**
     * 已配置出站日志的共享 RestClient 构建器。
     */
    private final RestClient.Builder restClientBuilder;
    /**
     * 将网关统一对话请求转换为 OpenAI 兼容载荷。
     */
    private final OpenAiRequestAdapter requestAdapter;
    /**
     * 将 OpenAI 兼容响应转换回网关统一响应。
     */
    private final OpenAiResponseAdapter responseAdapter;
    /**
     * 解析供应商模型列表 JSON 响应。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 注入适配器和 HTTP 客户端构建器；出站日志会对密钥脱敏。
     */
    public OpenAiCompatibleProviderClient(RestClient.Builder restClientBuilder, OpenAiRequestAdapter requestAdapter,
                                          OpenAiResponseAdapter responseAdapter) {
        this.restClientBuilder = restClientBuilder;
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
    }

    /**
     * 标识当前实现为 OpenAI 兼容供应商客户端。
     */
    @Override
    public ProviderType type() {
        return ProviderType.OPENAI_COMPATIBLE;
    }

    /**
     * 向已配置的上游对话路径发送非流式对话补全请求。
     */
    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        OpenAiChatCompletionRequest providerRequest = prepareRequestBody(
                route, requestAdapter.toProviderRequest(request, route.providerModel()));
        try {
            OpenAiChatCompletionResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.chatPath())
                    .header("Authorization", "Bearer " + route.apiKey())
                    .body(providerRequest)
                    .retrieve()
                    .body(OpenAiChatCompletionResponse.class);
            if (response == null) {
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Provider returned empty response");
            }
            return responseAdapter.toUnified(response);
        } catch (ProviderException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ProviderException(httpStatusToErrorCode(status), HttpStatus.BAD_GATEWAY,
                    upstreamError(prefix(status), exception));
        } catch (RestClientException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY, "Provider request failed: " + exception.getMessage());
        }
    }

    /**
     * OpenAI 兼容供应商普遍使用 data: ... SSE 格式，网关只做字节级透传，不解析增量块。
     */
    @Override
    public boolean supportsStreaming() {
        return true;
    }

    /**
     * 透传上游 SSE 响应；这里刻意不用带日志拦截器的 RestClient，避免拦截器缓存无限流。
     */
    @Override
    public UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        OpenAiChatCompletionRequest providerRequest = prepareRequestBody(
                route, requestAdapter.toProviderRequest(request, route.providerModel(), true));
        try {
            UnifiedUsage usage = RestClient.builder()
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
                        return copyOpenAiStream(response.getBody(), outputStream);
                    });
            return usage;
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
     * OpenAI 兼容视频生成使用渠道配置的视频路径，模型名按渠道映射替换后透传其余参数。
     */
    @Override
    public OpenAiVideoResponse generateVideo(ModelRoute route, OpenAiVideoRequest request) {
        OpenAiVideoRequest providerRequest = request.copyForProviderModel(route.providerModel());
        try {
            OpenAiVideoResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.resolvedVideoPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + route.apiKey())
                    .body(providerRequest)
                    .retrieve()
                    .body(OpenAiVideoResponse.class);
            if (response == null) {
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Provider returned empty response");
            }
            response.setModel(route.publicModel());
            return response;
        } catch (ProviderException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ProviderException(httpStatusToErrorCode(status), HttpStatus.BAD_GATEWAY,
                    upstreamError(prefix(status), exception));
        } catch (RestClientException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY, "Provider video request failed: " + exception.getMessage());
        }
    }

    /**
     * OpenAI 兼容图片生成使用渠道配置的图片路径，模型名按渠道映射替换后透传其余参数。
     */
    @Override
    public OpenAiImageResponse generateImage(ModelRoute route, OpenAiImageRequest request) {
        OpenAiImageRequest providerRequest = request.copyForProviderModel(route.providerModel());
        try {
            OpenAiImageResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.resolvedImagePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + route.apiKey())
                    .body(providerRequest)
                    .retrieve()
                    .body(OpenAiImageResponse.class);
            if (response == null) {
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Provider returned empty response");
            }
            response.setModel(route.publicModel());
            return response;
        } catch (ProviderException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            throw new ProviderException(httpStatusToErrorCode(status), HttpStatus.BAD_GATEWAY,
                    upstreamError(prefix(status), exception));
        } catch (RestClientException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY, "Provider image request failed: " + exception.getMessage());
        }
    }

    protected OpenAiChatCompletionRequest prepareRequestBody(ModelRoute route, OpenAiChatCompletionRequest request) {
        return request;
    }

    /**
     * 透传 OpenAI SSE 行并顺手解析最终 usage 块；未返回 usage 时保持空值，不影响客户端收流。
     */
    UnifiedUsage copyOpenAiStream(InputStream inputStream, OutputStream outputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            UnifiedUsage usage = null;
            StringBuilder eventData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                outputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                if (line.isEmpty()) {
                    usage = lastUsage(usage, eventData);
                    eventData.setLength(0);
                } else if (line.startsWith("data:")) {
                    if (eventData.length() > 0) {
                        eventData.append('\n');
                    }
                    eventData.append(line.substring(5).stripLeading());
                }
            }
            if (eventData.length() > 0) {
                usage = lastUsage(usage, eventData);
            }
            outputStream.flush();
            return usage;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * OpenAI 流式响应通常只在最后一个 JSON 事件里携带 usage，因此持续保留最后一次非空用量。
     */
    private UnifiedUsage lastUsage(UnifiedUsage currentUsage, StringBuilder eventData) {
        UnifiedUsage parsedUsage = parseStreamUsage(eventData.toString());
        return parsedUsage == null ? currentUsage : parsedUsage;
    }

    /**
     * 从 SSE data JSON 中读取 token 用量，兼容缓存读取 token 的多种字段命名。
     */
    private UnifiedUsage parseStreamUsage(String data) {
        if (data.isBlank() || "[DONE]".equals(data)) {
            return null;
        }
        try {
            JsonNode usage = objectMapper.readTree(data).path("usage");
            if (usage.isMissingNode() || usage.isNull()) {
                return null;
            }
            return new UnifiedUsage(
                    integer(usage, "prompt_tokens"),
                    integer(usage, "completion_tokens"),
                    integer(usage, "total_tokens"),
                    cacheReadInputTokens(usage)
            );
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 缓存读取 token 在不同兼容网关中字段不完全一致，这里按常见字段优先级读取。
     */
    private Integer cacheReadInputTokens(JsonNode usage) {
        Integer cachedTokens = integer(usage.path("prompt_tokens_details"), "cached_tokens");
        if (cachedTokens != null) {
            return cachedTokens;
        }
        cachedTokens = integer(usage, "cached_tokens");
        if (cachedTokens != null) {
            return cachedTokens;
        }
        cachedTokens = integer(usage, "cache_read_input_tokens");
        if (cachedTokens != null) {
            return cachedTokens;
        }
        return integer(usage, "prompt_cache_hit_tokens");
    }

    /**
     * 安全读取可能缺失的整数字段，避免供应商扩展字段导致流式转发中断。
     */
    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }
        try {
            return value.isTextual() ? Integer.parseInt(value.asText()) : null;
        } catch (NumberFormatException exception) {
            return null;
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
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY, "Provider models request failed: " + exception.getMessage());
        }
    }

    /**
     * 实时查询 OpenAI 兼容供应商额度；不同兼容供应商额度路径不统一，因此先尝试常见余额接口，再尝试 OpenAI 旧版 billing 接口。
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
                throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY, "Provider quota request failed: " + exception.getMessage());
            }
        }
        if (lastResponseException != null) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, upstreamError("Provider quota request failed", lastResponseException));
        }
        throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Provider quota request failed");
    }

    /**
     * 解析 OpenAI 模型列表外层结构，并保留 owned_by 用于展示。
     */
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

    /**
     * 解析常见 OpenAI 兼容额度结构，字段缺失时保留脱敏原文摘要给前端展示。
     */
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

    /**
     * 解析 DeepSeek 等兼容供应商常见的 balance_infos 数组。
     */
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

    /**
     * 从 JSON 字段中安全读取 BigDecimal，兼容字符串和数值两种返回。
     */
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

    /**
     * 统一额度展示文本，字段为空时显示未知。
     */
    private String displayAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return "未知";
        }
        return amount.stripTrailingZeros().toPlainString() + (currency == null || currency.isBlank() ? "" : " " + currency);
    }

    /**
     * 保留上游状态码和响应体供前端排查，同时对敏感信息脱敏。
     */
    private String upstreamError(String prefix, RestClientResponseException exception) {
        return upstreamError(prefix, exception.getStatusCode().value(), exception.getResponseBodyAsString());
    }

    /**
     * 保留上游状态码和响应体供排查，同时对敏感信息脱敏。
     */
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
            case PROVIDER_AUTH_FAILED -> "Provider authentication failed";
            case PROVIDER_RATE_LIMITED -> "Provider rate limited";
            case PROVIDER_UNAVAILABLE -> "Provider server error";
            default -> "Provider request failed";
        };
    }

}
