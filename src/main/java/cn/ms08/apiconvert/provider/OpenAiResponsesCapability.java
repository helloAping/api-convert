package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.dto.OpenAiResponsesRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.logging.LogSanitizer;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
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
 * OpenAI Responses API 端点能力 Base 实现。
 */
public class OpenAiResponsesCapability implements EndpointCapability {

    private final RestClient.Builder restClientBuilder;
    private final OpenAiResponsesRequestAdapter requestAdapter;
    private final OpenAiResponsesResponseAdapter responseAdapter;

    public OpenAiResponsesCapability(RestClient.Builder restClientBuilder,
                                     OpenAiResponsesRequestAdapter requestAdapter,
                                     OpenAiResponsesResponseAdapter responseAdapter) {
        this.restClientBuilder = restClientBuilder;
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
    }

    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        OpenAiResponsesRequest providerRequest = requestAdapter.toProviderRequest(request, route.providerModel(), false);
        try {
            OpenAiResponsesResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.resolvedChatPath(EndpointType.OPENAI_RESPONSES))
                    .header("Authorization", "Bearer " + route.apiKey())
                    .body(providerRequest)
                    .retrieve()
                    .body(OpenAiResponsesResponse.class);
            if (response == null) {
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                        "OpenAI Responses API returned empty response");
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

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        OpenAiResponsesRequest providerRequest = requestAdapter.toProviderRequest(request, route.providerModel(), true);
        try {
            UnifiedUsage usage = RestClient.builder()
                    .baseUrl(route.baseUrl())
                    .build()
                    .post()
                    .uri(route.resolvedChatPath(EndpointType.OPENAI_RESPONSES))
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
                    "OpenAI Responses stream request failed: " + exception.getMessage());
        } catch (RestClientException exception) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "OpenAI Responses stream request failed: " + exception.getMessage());
        }
    }

    private UnifiedUsage copyOpenAiStream(InputStream inputStream, OutputStream outputStream) {
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
                    if (eventData.length() > 0) eventData.append('\n');
                    eventData.append(line.substring(5).stripLeading());
                }
            }
            if (eventData.length() > 0) usage = lastUsage(usage, eventData);
            outputStream.flush();
            return usage;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private UnifiedUsage lastUsage(UnifiedUsage current, StringBuilder eventData) {
        return null; // Responses API usage parsing handled by RealTimeResponsesTransformer
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

    private String upstreamError(String prefix, RestClientResponseException exception) {
        return upstreamError(prefix, exception.getStatusCode().value(), exception.getResponseBodyAsString());
    }

    private String upstreamError(String prefix, int statusCode, String responseBody) {
        String body = LogSanitizer.sanitizeBody(responseBody);
        if (body.isBlank()) return prefix + ": status=" + statusCode;
        return prefix + ": status=" + statusCode + ", body=" + body;
    }
}