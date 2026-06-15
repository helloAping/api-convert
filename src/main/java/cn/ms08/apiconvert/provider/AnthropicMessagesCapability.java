package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
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

/**
 * Anthropic Messages 端点能力 Base 实现。
 * 封装完整的请求构建、HTTP 调用、SSE 流式透传、响应解析逻辑。
 * 子供应商通过覆盖 {@link #prepareRequestBody} 定制差异化行为。
 */
public class AnthropicMessagesCapability implements EndpointCapability {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnthropicRequestAdapter requestAdapter;
    private final AnthropicResponseAdapter responseAdapter;

    public AnthropicMessagesCapability(RestClient.Builder restClientBuilder, AnthropicRequestAdapter requestAdapter,
                                       AnthropicResponseAdapter responseAdapter) {
        this.restClientBuilder = restClientBuilder;
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
    }

    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        try {
            AnthropicMessageResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.chatPath())
                    .header(authHeaderName(route), authHeaderValue(route))
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .body(prepareRequestBody(route, requestAdapter.toProviderRequest(request, route.providerModel())))
                    .retrieve()
                    .body(AnthropicMessageResponse.class);
            if (response == null) {
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                        "Anthropic provider returned empty response");
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
        Object providerBody = prepareRequestBody(route,
                requestAdapter.toProviderRequest(request, route.providerModel(), true));
        try {
            UnifiedUsage usage = RestClient.builder()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.chatPath())
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header(authHeaderName(route), authHeaderValue(route))
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .body(providerBody)
                    .exchange((clientRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                            throw new ProviderException(
                                    httpStatusToErrorCode(response.getStatusCode().value()),
                                    HttpStatus.BAD_GATEWAY,
                                    upstreamError(prefix(response.getStatusCode().value()),
                                            response.getStatusCode().value(), body));
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

    // ---------- extension point ----------

    /**
     * 子供应商可覆盖此方法定制请求体（如 DeepSeek 设置 thinking 块），默认原样返回。
     */
    protected AnthropicMessageRequest prepareRequestBody(ModelRoute route, AnthropicMessageRequest request) {
        return request;
    }

    // ---------- auth ----------

    protected String authHeaderName(ModelRoute route) {
        return authHeaderName();
    }

    protected String authHeaderValue(ModelRoute route) {
        return authHeaderValue(route.apiKey());
    }

    protected String authHeaderName() {
        return "x-api-key";
    }

    protected String authHeaderValue(String credential) {
        return credential;
    }

    // ---------- SSE stream helpers ----------

    UnifiedUsage copyAnthropicStream(InputStream inputStream, OutputStream outputStream) {
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

    private UnifiedUsage lastUsage(UnifiedUsage currentUsage, StringBuilder eventData) {
        UnifiedUsage parsedUsage = parseStreamUsage(eventData.toString());
        return parsedUsage == null ? currentUsage : parsedUsage;
    }

    private UnifiedUsage parseStreamUsage(String data) {
        if (data.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(data);
            String type = root.path("type").asText();
            if (!"message_delta".equals(type)) return null;
            JsonNode usage = root.path("usage");
            if (usage.isMissingNode() || usage.isNull()) return null;
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
        if (value.isMissingNode() || value.isNull()) return null;
        if (value.canConvertToInt()) return value.asInt();
        try {
            return value.isTextual() ? Integer.parseInt(value.asText()) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    // ---------- error helpers ----------

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
        if (body.isBlank()) return prefix + ": status=" + statusCode;
        return prefix + ": status=" + statusCode + ", body=" + body;
    }
}
