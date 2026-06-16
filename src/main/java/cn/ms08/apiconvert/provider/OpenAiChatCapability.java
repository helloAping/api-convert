package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.logging.LogSanitizer;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * OpenAI Chat Completions 端点能力 Base 实现。
 * 封装完整的请求构建、HTTP 调用、SSE 流式透传、响应解析逻辑。
 * 子供应商通过覆盖 {@link #prepareRequestBody} 定制差异化行为，
 * 不应绕过此类自行实现 HTTP 调用。
 */
public class OpenAiChatCapability implements EndpointCapability {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatCapability.class);

    private final RestClient.Builder restClientBuilder;
    private final OpenAiRequestAdapter requestAdapter;
    private final OpenAiResponseAdapter responseAdapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiChatCapability(RestClient.Builder restClientBuilder, OpenAiRequestAdapter requestAdapter,
                                OpenAiResponseAdapter responseAdapter) {
        this.restClientBuilder = restClientBuilder;
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
    }

    // ---------- EndpointCapability ----------

    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        OpenAiChatCompletionRequest providerRequest = prepareRequestBody(
                route, requestAdapter.toProviderRequest(request, route.providerModel()));
        log.info("上游非流式请求体: {}", LogSanitizer.sanitizeBody(safeWriteJson(providerRequest)));
        try {
            OpenAiChatCompletionResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.resolvedChatPath(EndpointType.CHAT_COMPLETIONS))
                    .header("Authorization", "Bearer " + route.apiKey())
                    .body(providerRequest)
                    .retrieve()
                    .body(OpenAiChatCompletionResponse.class);
            if (response == null) {
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                        "Provider returned empty response");
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
                    restClientError("Provider request failed", exception));
        }
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        OpenAiChatCompletionRequest providerRequest = prepareRequestBody(
                route, requestAdapter.toProviderRequest(request, route.providerModel(), true));
        try {
            UnifiedUsage usage = RestClient.builder()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.resolvedChatPath(EndpointType.CHAT_COMPLETIONS))
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
                    restClientError("Provider stream request failed", exception));
        }
    }

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
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                        "Provider returned empty response");
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
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    restClientError("Provider video request failed", exception));
        }
    }

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
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                        "Provider returned empty response");
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
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    restClientError("Provider image request failed", exception));
        }
    }

    // ---------- extension point for sub-providers ----------

    /**
     * 子供应商可覆盖此方法定制请求体（如 DeepSeek 设置 reasoning_content 兜底），
     * 默认原样返回。
     */
    protected OpenAiChatCompletionRequest prepareRequestBody(ModelRoute route, OpenAiChatCompletionRequest request) {
        return request;
    }

    // ---------- SSE stream helpers ----------

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

    private UnifiedUsage lastUsage(UnifiedUsage currentUsage, StringBuilder eventData) {
        UnifiedUsage parsedUsage = parseStreamUsage(eventData.toString());
        return parsedUsage == null ? currentUsage : parsedUsage;
    }

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
                    cacheReadInputTokens(usage));
        } catch (Exception exception) {
            return null;
        }
    }

    private Integer cacheReadInputTokens(JsonNode usage) {
        Integer cachedTokens = integer(usage.path("prompt_tokens_details"), "cached_tokens");
        if (cachedTokens != null) return cachedTokens;
        cachedTokens = integer(usage, "cached_tokens");
        if (cachedTokens != null) return cachedTokens;
        cachedTokens = integer(usage, "cache_read_input_tokens");
        if (cachedTokens != null) return cachedTokens;
        return integer(usage, "prompt_cache_hit_tokens");
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

    // ---------- shared error helpers ----------

    private String safeWriteJson(Object value) {
        if (value == null) return "null";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "<serialization error: " + e.getMessage() + ">";
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
            case PROVIDER_AUTH_FAILED -> "Provider authentication failed";
            case PROVIDER_RATE_LIMITED -> "Provider rate limited";
            case PROVIDER_UNAVAILABLE -> "Provider server error";
            default -> "Provider request failed";
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

    private String restClientError(String prefix, Exception exception) {
        String message = exception.getMessage();
        Throwable root = rootCause(exception);
        if (root != exception && root.getMessage() != null && !root.getMessage().equals(message)) {
            message = message + "; rootCause=" + root.getClass().getSimpleName() + ": " + root.getMessage();
        }
        return prefix + ": " + LogSanitizer.sanitizeBody(
                message == null ? exception.getClass().getSimpleName() : message);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
