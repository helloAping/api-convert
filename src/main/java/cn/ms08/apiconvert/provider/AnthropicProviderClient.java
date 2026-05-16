package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
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
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AnthropicProviderClient implements AiProviderClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnthropicRequestAdapter requestAdapter;
    private final AnthropicResponseAdapter responseAdapter;

    public AnthropicProviderClient(RestClient.Builder restClientBuilder, AnthropicRequestAdapter requestAdapter,
                                   AnthropicResponseAdapter responseAdapter) {
        this.restClientBuilder = restClientBuilder;
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
    }

    @Override
    public ProviderType type() {
        return ProviderType.ANTHROPIC;
    }

    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        try {
            AnthropicMessageResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.chatPath())
                    .header("x-api-key", route.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .body(prepareRequestBody(route, requestAdapter.toProviderRequest(request, route.providerModel())))
                    .retrieve()
                    .body(AnthropicMessageResponse.class);
            if (response == null) {
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Anthropic provider returned empty response");
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
                    "Anthropic request failed: " + exception.getMessage());
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        Object providerBody = prepareRequestBody(route, requestAdapter.toProviderRequest(request, route.providerModel(), true));
        try {
            UnifiedUsage usage = RestClient.builder()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.chatPath())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header("x-api-key", route.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .body(providerBody)
                    .exchange((clientRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                            throw new ProviderException(
                                    httpStatusToErrorCode(response.getStatusCode().value()),
                                    HttpStatus.BAD_GATEWAY,
                                    upstreamError(prefix(response.getStatusCode().value()), response.getStatusCode().value(), body));
                        }
                        return copyAnthropicStream(response.getBody(), outputStream);
                    });
            return usage;
        } catch (ProviderException exception) {
            throw exception;
        } catch (UncheckedIOException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Anthropic stream request failed: " + exception.getMessage());
        } catch (RestClientException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Anthropic stream request failed: " + exception.getMessage());
        }
    }

    /**
     * DeepSeek 的 Anthropic 兼容接口要求 thinking 模式下历史消息里的 thinking 块继续携带 thinking 字段。
     */
    AnthropicMessageRequest prepareRequestBody(ModelRoute route, AnthropicMessageRequest request) {
        if (!isDeepSeekAnthropicRoute(route) || request == null || request.getMessages() == null) {
            return request;
        }
        for (cn.ms08.apiconvert.dto.AnthropicMessage message : request.getMessages()) {
            message.setContent(normalizeDeepSeekThinkingContent(message.getContent()));
        }
        return request;
    }

    private boolean isDeepSeekAnthropicRoute(ModelRoute route) {
        if (route == null || route.baseUrl() == null) {
            return false;
        }
        String upstreamUrl = (route.baseUrl() + (route.chatPath() == null ? "" : route.chatPath())).toLowerCase();
        return upstreamUrl.contains("api.deepseek.com") && upstreamUrl.contains("/anthropic");
    }

    private Object normalizeDeepSeekThinkingContent(Object content) {
        if (!(content instanceof List<?> contentList)) {
            return content;
        }
        List<Object> normalized = new ArrayList<>(contentList.size());
        for (Object block : contentList) {
            if (!(block instanceof Map<?, ?> blockMap)) {
                normalized.add(block);
                continue;
            }
            String type = blockMap.containsKey("type") ? String.valueOf(blockMap.get("type")) : "";
            if (!"thinking".equals(type)) {
                normalized.add(block);
                continue;
            }
            Map<String, Object> normalizedBlock = new LinkedHashMap<>();
            blockMap.forEach((key, value) -> normalizedBlock.put(String.valueOf(key), value));
            Object thinking = normalizedBlock.get("thinking");
            if (thinking == null) {
                Object text = normalizedBlock.get("text");
                normalizedBlock.put("thinking", text == null ? "" : String.valueOf(text));
            }
            normalized.add(normalizedBlock);
        }
        return normalized;
    }

    UnifiedUsage copyAnthropicStream(InputStream inputStream, OutputStream outputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            UnifiedUsage usage = null;
            String lastData = null;
            String line;
            while ((line = reader.readLine()) != null) {
                outputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                if (line.startsWith("data:")) {
                    lastData = line.substring(5).stripLeading();
                } else if (line.isEmpty() && lastData != null) {
                    usage = lastUsage(usage, lastData);
                    lastData = null;
                }
            }
            if (lastData != null) {
                usage = lastUsage(usage, lastData);
            }
            outputStream.flush();
            return usage;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private UnifiedUsage lastUsage(UnifiedUsage currentUsage, String eventData) {
        UnifiedUsage parsedUsage = parseStreamUsage(eventData);
        return parsedUsage == null ? currentUsage : parsedUsage;
    }

    private UnifiedUsage parseStreamUsage(String data) {
        if (data.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            String type = root.path("type").asText();
            if (!"message_delta".equals(type)) {
                return null;
            }
            JsonNode usage = root.path("usage");
            if (usage.isMissingNode() || usage.isNull()) {
                return null;
            }
            Integer inputTokens = integer(usage, "input_tokens");
            Integer outputTokens = integer(usage, "output_tokens");
            Integer totalTokens = inputTokens == null || outputTokens == null
                    ? null : inputTokens + outputTokens;
            return new UnifiedUsage(inputTokens, outputTokens, totalTokens,
                    integer(usage, "cache_read_input_tokens"));
        } catch (Exception exception) {
            return null;
        }
    }

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

    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        try {
            String body = restClientBuilder.clone()
                    .baseUrl(request.baseUrl())
                    .build()
                    .get()
                    .uri(request.modelsPath())
                    .header("x-api-key", request.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .retrieve()
                    .body(String.class);
            return parseAnthropicModelList(body);
        } catch (RestClientResponseException exception) {
            throw new ProviderException(httpStatusToErrorCode(exception.getStatusCode().value()), HttpStatus.BAD_GATEWAY,
                    upstreamError(prefix(exception.getStatusCode().value()), exception));
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Anthropic models request failed: " + exception.getMessage());
        }
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "Anthropic 当前没有通用额度查询接口，请在供应商控制台查看。", null, null, null, "", "");
    }

    private List<ProviderModel> parseAnthropicModelList(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                data = root.path("models");
            }
            if (!data.isArray()) {
                throw new IllegalArgumentException("Anthropic model list response missing data/models array");
            }
            List<ProviderModel> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.isTextual() ? item.asText() : item.path("id").asText(null);
                if (id != null && !id.isBlank()) {
                    String ownedBy = item.isObject() ? item.path("owned_by").asText("anthropic") : "anthropic";
                    models.add(new ProviderModel(id, ownedBy));
                }
            }
            return models;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse Anthropic model list response: " + LogSanitizer.sanitizeBody(body), exception);
        }
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
            case PROVIDER_AUTH_FAILED -> "Anthropic authentication failed";
            case PROVIDER_RATE_LIMITED -> "Anthropic rate limited";
            case PROVIDER_UNAVAILABLE -> "Anthropic server error";
            default -> "Anthropic request failed";
        };
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
}
