package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiAudioBinaryResponse;
import cn.ms08.apiconvert.dto.OpenAiAudioSpeechRequest;
import cn.ms08.apiconvert.dto.OpenAiAudioTranscriptionRequest;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.OpenAiEmbeddingRequest;
import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.dto.OpenAiResponsesRequest;
import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.logging.LogSanitizer;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import cn.ms08.apiconvert.vo.OpenAiAudioTranscriptionResponse;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import cn.ms08.apiconvert.vo.OpenAiEmbeddingResponse;
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
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
 * 供应商客户端抽象基类，提供 OpenAI Chat Completions、Anthropic Messages、
 * OpenAI Responses API 三种协议的完整 HTTP 请求构建、发送、响应解析和 SSE 流式透传逻辑。
 * <p>
 * 子类通过覆写前置/后置钩子方法定制差异化行为，无需自行实现 HTTP 调用。
 *
 * <h3>钩子方法</h3>
 * <table>
 *   <tr><th>方法</th><th>协议</th><th>时机</th><th>用途</th></tr>
 *   <tr><td>{@link #beforeChatRequest}</td><td>Chat Completions</td><td>请求发送前</td><td>修改请求体（兜底字段、补充 provider 私有参数等）</td></tr>
 *   <tr><td>{@link #afterChatResponse}</td><td>Chat Completions</td><td>响应解析后</td><td>修改统一响应</td></tr>
 *   <tr><td>{@link #beforeAnthropicRequest}</td><td>Anthropic Messages</td><td>请求发送前</td><td>修改请求体（补 thinking / 调整 system 块等）</td></tr>
 *   <tr><td>{@link #afterAnthropicResponse}</td><td>Anthropic Messages</td><td>响应解析后</td><td>修改统一响应</td></tr>
 *   <tr><td>{@link #beforeResponsesRequest}</td><td>Responses API</td><td>请求发送前</td><td>修改请求体</td></tr>
 *   <tr><td>{@link #afterResponsesResponse}</td><td>Responses API</td><td>响应解析后</td><td>修改统一响应</td></tr>
 *   <tr><td>{@link #resolveApiKey}</td><td>全部</td><td>构建鉴权头时</td><td>OAuth 类型供应商从 auth.json 读取 access_token</td></tr>
 *   <tr><td>{@link #authHeaderName}</td><td>Anthropic</td><td>构建鉴权头时</td><td>默认 x-api-key，可覆写为 Authorization: Bearer</td></tr>
 * </table>
 *
 * <p>
 * <b>新代码建议改用 {@link cn.ms08.apiconvert.adapter.endpoint.ProviderHook}</b>：
 * ProviderHook 与具体供应商类型绑定（{@code @HooksForProvider(ProviderType.X)}），由
 * {@code ChatGatewayService} 在路由阶段统一串联（请求方向 hook.preProcess → 跨协议 adapter.adaptRequest；
 * 响应方向 adapter.adaptResponse → hook.postProcess），与协议实现完全解耦。当前 DeepSeek 的
 * {@code reasoning_content=""} 兜底和 thinking 块补全已迁出到 {@link cn.ms08.apiconvert.adapter.endpoint.DeepSeekHook}。
 * 本类的 6 个请求/响应钩子仅作为旧扩展点保留。
 * </p>
 *
 * <h3>鉴权</h3>
 * <ul>
 *   <li>Chat / Responses / Video / Image：默认 {@code Authorization: Bearer xxx}</li>
 *   <li>Anthropic Messages：默认 {@code x-api-key} + {@code anthropic-version: 2023-06-01}</li>
 *   <li>OAuth 类型（GPT_AUTH / CLAUDE_AUTH）：覆写 {@link #resolveApiKey} 从 auth.json 读取 access_token</li>
 * </ul>
 *
 * <h3>请求路径</h3>
 * 所有协议通过 {@link ModelRoute#resolvedChatPath(EndpointType)} 从渠道能力配置中查找对应路径，
 * 未配置时回退到 legacy chatPath。
 */
public abstract class BaseAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(BaseAiProviderClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /** Spring RestClient 构建器，每次调用 clone() 创建独立实例。 */
    protected final RestClient.Builder restClientBuilder;
    /** 共享 ObjectMapper，用于 JSON 序列化和 SSE usage 解析。 */
    protected final ObjectMapper objectMapper = new ObjectMapper();
    /** OpenAI Chat Completions 协议适配器。 */
    protected final OpenAiRequestAdapter openAiRequestAdapter;
    protected final OpenAiResponseAdapter openAiResponseAdapter;
    /** Anthropic Messages 协议适配器。 */
    protected final AnthropicRequestAdapter anthropicRequestAdapter;
    protected final AnthropicResponseAdapter anthropicResponseAdapter;
    /** OpenAI Responses API 协议适配器。 */
    protected final OpenAiResponsesRequestAdapter responsesRequestAdapter;
    protected final OpenAiResponsesResponseAdapter responsesResponseAdapter;

    protected BaseAiProviderClient(RestClient.Builder restClientBuilder,
                                   OpenAiRequestAdapter openAiRequestAdapter,
                                   OpenAiResponseAdapter openAiResponseAdapter,
                                   AnthropicRequestAdapter anthropicRequestAdapter,
                                   AnthropicResponseAdapter anthropicResponseAdapter,
                                   OpenAiResponsesRequestAdapter responsesRequestAdapter,
                                   OpenAiResponsesResponseAdapter responsesResponseAdapter) {
        this.restClientBuilder = restClientBuilder;
        this.openAiRequestAdapter = openAiRequestAdapter;
        this.openAiResponseAdapter = openAiResponseAdapter;
        this.anthropicRequestAdapter = anthropicRequestAdapter;
        this.anthropicResponseAdapter = anthropicResponseAdapter;
        this.responsesRequestAdapter = responsesRequestAdapter;
        this.responsesResponseAdapter = responsesResponseAdapter;
    }

    // ==================== AiProviderClient 协议方法 ====================

    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(request, route.providerModel());
        providerRequest = beforeChatRequest(route, providerRequest);
        log.info("上游非流式请求体: {}", LogSanitizer.sanitizeBody(safeWriteJson(providerRequest)));
        try {
            OpenAiChatCompletionResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedChatPath(EndpointType.CHAT_COMPLETIONS))
                    .header("Authorization", "Bearer " + resolveApiKey(route))
                    .body(providerRequest).retrieve().body(OpenAiChatCompletionResponse.class);
            if (response == null) throw emptyResponse();
            return afterChatResponse(route, openAiResponseAdapter.toUnified(response));
        } catch (ProviderException e) { throw e; }
        catch (RestClientResponseException e) { throw providerError(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    @Override
    public UnifiedChatResponse messages(ModelRoute route, UnifiedChatRequest request) {
        AnthropicMessageRequest providerRequest = anthropicRequestAdapter.toProviderRequest(request, route.providerModel());
        providerRequest = beforeAnthropicRequest(route, providerRequest);
        try {
            AnthropicMessageResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedChatPath(EndpointType.ANTHROPIC_MESSAGES))
                    .header(authHeaderName(), authHeaderValue(route))
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .body(providerRequest).retrieve().body(AnthropicMessageResponse.class);
            if (response == null) throw emptyResponse();
            return afterAnthropicResponse(route, anthropicResponseAdapter.toUnified(response));
        } catch (ProviderException e) { throw e; }
        catch (RestClientResponseException e) { throw providerError(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    @Override
    public UnifiedChatResponse responses(ModelRoute route, UnifiedChatRequest request) {
        OpenAiResponsesRequest providerRequest = responsesRequestAdapter.toProviderRequest(request, route.providerModel(), false);
        providerRequest = beforeResponsesRequest(route, providerRequest);
        try {
            OpenAiResponsesResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedChatPath(EndpointType.OPENAI_RESPONSES))
                    .header("Authorization", "Bearer " + resolveApiKey(route))
                    .body(providerRequest).retrieve().body(OpenAiResponsesResponse.class);
            if (response == null) throw emptyResponse();
            return afterResponsesResponse(route, responsesResponseAdapter.toUnified(response));
        } catch (ProviderException e) { throw e; }
        catch (RestClientResponseException e) { throw providerError(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    @Override
    public boolean supportsStreaming(EndpointType endpointType) {
        return endpointType == EndpointType.CHAT_COMPLETIONS
                || endpointType == EndpointType.ANTHROPIC_MESSAGES
                || endpointType == EndpointType.OPENAI_RESPONSES;
    }

    @Override
    public UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        OpenAiChatCompletionRequest providerRequest = openAiRequestAdapter.toProviderRequest(request, route.providerModel(), true);
        providerRequest = beforeChatRequest(route, providerRequest);
        try {
            return RestClient.builder().baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedChatPath(EndpointType.CHAT_COMPLETIONS))
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header("Authorization", "Bearer " + resolveApiKey(route))
                    .body(providerRequest)
                    .exchange((clientRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                            throw new ProviderException(httpStatusToErrorCode(response.getStatusCode().value()),
                                    HttpStatus.BAD_GATEWAY, upstreamError(prefix(response.getStatusCode().value()),
                                    response.getStatusCode().value(), body));
                        }
                        return copyOpenAiStream(response.getBody(), outputStream);
                    });
        } catch (ProviderException e) { throw e; }
        catch (UncheckedIOException e) { throw providerUnavailable(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    @Override
    public UnifiedUsage streamMessages(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        AnthropicMessageRequest providerRequest = anthropicRequestAdapter.toProviderRequest(request, route.providerModel(), true);
        providerRequest = beforeAnthropicRequest(route, providerRequest);
        try {
            return RestClient.builder().baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedChatPath(EndpointType.ANTHROPIC_MESSAGES))
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header(authHeaderName(), authHeaderValue(route))
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .body(providerRequest)
                    .exchange((clientRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                            throw new ProviderException(httpStatusToErrorCode(response.getStatusCode().value()),
                                    HttpStatus.BAD_GATEWAY, upstreamError(prefix(response.getStatusCode().value()),
                                    response.getStatusCode().value(), body));
                        }
                        return copyAnthropicStream(response.getBody(), outputStream);
                    });
        } catch (ProviderException e) { throw e; }
        catch (UncheckedIOException e) { throw providerUnavailable(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    @Override
    public UnifiedUsage streamResponses(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        OpenAiResponsesRequest providerRequest = responsesRequestAdapter.toProviderRequest(request, route.providerModel(), true);
        providerRequest = beforeResponsesRequest(route, providerRequest);
        try {
            return RestClient.builder().baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedChatPath(EndpointType.OPENAI_RESPONSES))
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .header("Authorization", "Bearer " + resolveApiKey(route))
                    .body(providerRequest)
                    .exchange((clientRequest, response) -> {
                        if (response.getStatusCode().isError()) {
                            String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                            throw new ProviderException(httpStatusToErrorCode(response.getStatusCode().value()),
                                    HttpStatus.BAD_GATEWAY, upstreamError(prefix(response.getStatusCode().value()),
                                    response.getStatusCode().value(), body));
                        }
                        return copyOpenAiStream(response.getBody(), outputStream);
                    });
        } catch (ProviderException e) { throw e; }
        catch (UncheckedIOException e) { throw providerUnavailable(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    @Override
    public OpenAiVideoResponse generateVideo(ModelRoute route, OpenAiVideoRequest request) {
        OpenAiVideoRequest providerRequest = request.copyForProviderModel(route.providerModel());
        try {
            OpenAiVideoResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedVideoPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + resolveApiKey(route))
                    .body(providerRequest).retrieve().body(OpenAiVideoResponse.class);
            if (response == null) throw emptyResponse();
            response.setModel(route.publicModel());
            return response;
        } catch (ProviderException e) { throw e; }
        catch (RestClientResponseException e) { throw providerError(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    @Override
    public OpenAiImageResponse generateImage(ModelRoute route, OpenAiImageRequest request) {
        OpenAiImageRequest providerRequest = request.copyForProviderModel(route.providerModel());
        try {
            OpenAiImageResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedImagePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + resolveApiKey(route))
                    .body(providerRequest).retrieve().body(OpenAiImageResponse.class);
            if (response == null) throw emptyResponse();
            response.setModel(route.publicModel());
            return response;
        } catch (ProviderException e) { throw e; }
        catch (RestClientResponseException e) { throw providerError(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    /**
     * OpenAI 兼容嵌入接口默认实现：Bearer 鉴权 + JSON POST 透传到渠道 embedding 路径。
     * OPENAI / CUSTOM / MIMO_TOKEN_PLAN / DEEPSEEK / OPENCODE / VOLC_CODINGPLAN 走默认实现，
     * 非 OpenAI 兼容协议供应商（GEMINI / LOCAL 等）需要在子类中显式覆盖为 UNSUPPORTED_FEATURE。
     */
    @Override
    public OpenAiEmbeddingResponse embed(ModelRoute route, OpenAiEmbeddingRequest request) {
        OpenAiEmbeddingRequest providerRequest = request.copyForProviderModel(route.providerModel());
        log.info("上游嵌入请求体: {}", LogSanitizer.sanitizeBody(safeWriteJson(providerRequest)));
        try {
            OpenAiEmbeddingResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedEmbeddingPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + resolveApiKey(route))
                    .body(providerRequest).retrieve().body(OpenAiEmbeddingResponse.class);
            if (response == null) throw emptyResponse();
            response.setModel(route.publicModel());
            return response;
        } catch (ProviderException e) { throw e; }
        catch (RestClientResponseException e) { throw providerError(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    /**
     * OpenAI 兼容 TTS 接口默认实现：Bearer 鉴权 + JSON POST 透传到渠道 audio_speech 路径，
     * 上游直接返回二进制音频字节，按上游 Content-Type 推断响应格式。
     */
    @Override
    public OpenAiAudioBinaryResponse speech(ModelRoute route, OpenAiAudioSpeechRequest request) {
        OpenAiAudioSpeechRequest providerRequest = request.copyForProviderModel(route.providerModel());
        log.info("上游 TTS 请求体: {}", LogSanitizer.sanitizeBody(safeWriteJson(providerRequest)));
        try {
            org.springframework.core.ParameterizedTypeReference<org.springframework.core.io.Resource> resourceType =
                    new org.springframework.core.ParameterizedTypeReference<>() {};
            org.springframework.core.io.Resource resource = restClientBuilder.clone()
                    .baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedAudioSpeechPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + resolveApiKey(route))
                    .body(providerRequest).retrieve().body(resourceType);
            if (resource == null) throw emptyResponse();
            byte[] bytes;
            try (java.io.InputStream input = resource.getInputStream()) {
                bytes = input.readAllBytes();
            }
            org.springframework.http.MediaType contentType = OpenAiAudioBinaryResponse.mediaTypeFor(
                    providerRequest.getResponseFormat());
            String filename = "speech." + OpenAiAudioBinaryResponse.extensionFor(providerRequest.getResponseFormat());
            return new OpenAiAudioBinaryResponse(bytes, contentType, filename);
        } catch (ProviderException e) { throw e; }
        catch (RestClientResponseException e) { throw providerError(e); }
        catch (RestClientException | java.io.IOException e) { throw providerUnavailable(e); }
    }

    /**
     * OpenAI 兼容 STT 接口默认实现：Bearer 鉴权 + multipart/form-data 透传到渠道 audio_transcriptions 路径，
     * 上游返回 verbose_json 响应；非 OpenAI 兼容协议供应商需在子类中显式覆盖为 UNSUPPORTED_FEATURE。
     */
    @Override
    public OpenAiAudioTranscriptionResponse transcribe(ModelRoute route, OpenAiAudioTranscriptionRequest request) {
        OpenAiAudioTranscriptionRequest providerRequest = request;
        providerRequest.setModel(route.providerModel());
        try {
            org.springframework.http.client.MultipartBodyBuilder builder = new org.springframework.http.client.MultipartBodyBuilder();
            builder.part("file", new org.springframework.core.io.ByteArrayResource(
                    providerRequest.getFileBytes() == null ? new byte[0] : providerRequest.getFileBytes()) {
                @Override
                public String getFilename() {
                    return providerRequest.getFilename();
                }
            }).contentType(org.springframework.http.MediaType.parseMediaType(
                    providerRequest.getContentType() == null ? "application/octet-stream" : providerRequest.getContentType()));
            builder.part("model", providerRequest.getModel());
            if (org.springframework.util.StringUtils.hasText(providerRequest.getLanguage())) {
                builder.part("language", providerRequest.getLanguage());
            }
            if (org.springframework.util.StringUtils.hasText(providerRequest.getPrompt())) {
                builder.part("prompt", providerRequest.getPrompt());
            }
            if (org.springframework.util.StringUtils.hasText(providerRequest.getResponseFormat())) {
                builder.part("response_format", providerRequest.getResponseFormat());
            }
            if (providerRequest.getTemperature() != null) {
                builder.part("temperature", providerRequest.getTemperature());
            }
            if (providerRequest.getTimestampGranularities() != null) {
                for (String granularity : providerRequest.getTimestampGranularities()) {
                    builder.part("timestamp_granularities[]", granularity);
                }
            }
            org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> parts = builder.build();
            OpenAiAudioTranscriptionResponse response = restClientBuilder.clone()
                    .baseUrl(route.baseUrl()).build()
                    .post().uri(route.resolvedAudioTranscriptionPath())
                    .header("Authorization", "Bearer " + resolveApiKey(route))
                    .body(parts)
                    .retrieve().body(OpenAiAudioTranscriptionResponse.class);
            if (response == null) throw emptyResponse();
            return response;
        } catch (ProviderException e) { throw e; }
        catch (RestClientResponseException e) { throw providerError(e); }
        catch (RestClientException e) { throw providerUnavailable(e); }
    }

    // ==================== 钩子方法 ====================

    /**
     * 解析实际使用的 API Key。默认返回 route 中的 apiKey，
     * OAuth 类型供应商（GPT_AUTH / CLAUDE_AUTH）覆写此方法从 auth.json 读取 access_token。
     */
    protected String resolveApiKey(ModelRoute route) {
        return route.apiKey();
    }

    /**
     * Anthropic Messages 鉴权头名称，默认 {@code x-api-key}。
     * 需要 Bearer 鉴权的供应商可覆写返回 {@code "Authorization"}。
     */
    protected String authHeaderName() {
        return "x-api-key";
    }

    /**
     * Anthropic Messages 鉴权头值，默认返回 {@link #resolveApiKey} 的结果。
     * Bearer 鉴权时覆写返回 {@code "Bearer " + resolveApiKey(route)}。
     */
    protected String authHeaderValue(ModelRoute route) {
        return resolveApiKey(route);
    }

    /**
     * Chat Completions 请求发送前钩子。
     * <p>
     * <b>新代码建议改用 {@link cn.ms08.apiconvert.adapter.endpoint.ProviderHook}</b>，本方法
     * 仅作为旧扩展点保留。DeepSeek 兜 reasoning_content="" 等供应商特化已迁出到
     * {@link cn.ms08.apiconvert.adapter.endpoint.DeepSeekHook}。
     * </p>
     *
     * @param route   已解析的路由信息
     * @param request 即将发送的 OpenAI Chat 请求体
     * @return 修改后的请求体
     */
    protected OpenAiChatCompletionRequest beforeChatRequest(ModelRoute route, OpenAiChatCompletionRequest request) {
        return request;
    }

    /**
     * Chat Completions 响应解析后钩子。子类可覆写此方法修改统一响应。
     * <p>新代码建议改用 {@link cn.ms08.apiconvert.adapter.endpoint.ProviderHook#postProcess}。</p>
     */
    protected UnifiedChatResponse afterChatResponse(ModelRoute route, UnifiedChatResponse response) {
        return response;
    }

    /**
     * Anthropic Messages 请求发送前钩子。
     * <p>
     * <b>新代码建议改用 {@link cn.ms08.apiconvert.adapter.endpoint.ProviderHook}</b>，本方法
     * 仅作为旧扩展点保留。DeepSeek 补全 thinking 块的 thinking 字段等供应商特化已迁出到
     * {@link cn.ms08.apiconvert.adapter.endpoint.DeepSeekHook}。
     * </p>
     */
    protected AnthropicMessageRequest beforeAnthropicRequest(ModelRoute route, AnthropicMessageRequest request) {
        return request;
    }

    /**
     * Anthropic Messages 响应解析后钩子。
     * <p>新代码建议改用 {@link cn.ms08.apiconvert.adapter.endpoint.ProviderHook#postProcess}。</p>
     */
    protected UnifiedChatResponse afterAnthropicResponse(ModelRoute route, UnifiedChatResponse response) {
        return response;
    }

    /**
     * Responses API 请求发送前钩子。
     */
    protected OpenAiResponsesRequest beforeResponsesRequest(ModelRoute route, OpenAiResponsesRequest request) {
        return request;
    }

    /**
     * Responses API 响应解析后钩子。
     */
    protected UnifiedChatResponse afterResponsesResponse(ModelRoute route, UnifiedChatResponse response) {
        return response;
    }

    // ==================== SSE 流式透传 ====================

    /**
     * OpenAI Chat / Responses 协议的 SSE 字节流透传，从最终 usage 块提取 token 用量。
     * 逐行读取上游 SSE，原样写入 outputStream，遇到空行时解析累积的 data 行中的 usage。
     */
    protected UnifiedUsage copyOpenAiStream(InputStream inputStream, OutputStream outputStream) {
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
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /**
     * Anthropic Messages 协议的 SSE 字节流透传，从 message_delta 事件提取 token 用量。
     */
    protected UnifiedUsage copyAnthropicStream(InputStream inputStream, OutputStream outputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            UnifiedUsage usage = null;
            StringBuilder eventData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                outputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                if (line.isEmpty()) {
                    usage = lastAnthropicUsage(usage, eventData);
                    eventData.setLength(0);
                } else if (line.startsWith("data:")) {
                    if (eventData.length() > 0) eventData.append('\n');
                    eventData.append(line.substring(5).stripLeading());
                }
            }
            if (eventData.length() > 0) usage = lastAnthropicUsage(usage, eventData);
            outputStream.flush();
            return usage;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    private UnifiedUsage lastUsage(UnifiedUsage current, StringBuilder eventData) {
        UnifiedUsage parsed = parseStreamUsage(eventData.toString());
        return parsed == null ? current : parsed;
    }

    private UnifiedUsage lastAnthropicUsage(UnifiedUsage current, StringBuilder eventData) {
        UnifiedUsage parsed = parseAnthropicStreamUsage(eventData.toString());
        return parsed == null ? current : parsed;
    }

    /** 从 OpenAI SSE data 行解析 usage 对象。 */
    private UnifiedUsage parseStreamUsage(String data) {
        if (data.isBlank() || "[DONE]".equals(data)) return null;
        try {
            JsonNode usage = objectMapper.readTree(data).path("usage");
            if (usage.isMissingNode() || usage.isNull()) return null;
            return new UnifiedUsage(integer(usage, "prompt_tokens"), integer(usage, "completion_tokens"),
                    integer(usage, "total_tokens"), cacheReadInputTokens(usage));
        } catch (Exception e) { return null; }
    }

    /** 从 Anthropic SSE message_delta 事件解析 usage。 */
    private UnifiedUsage parseAnthropicStreamUsage(String data) {
        if (data.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(data);
            if (!"message_delta".equals(root.path("type").asText(""))) return null;
            JsonNode usage = root.path("delta").path("usage");
            if (usage.isMissingNode() || usage.isNull()) return null;
            return new UnifiedUsage(integer(usage, "input_tokens"), integer(usage, "output_tokens"),
                    null, integer(usage, "cache_read_input_tokens"));
        } catch (Exception e) { return null; }
    }

    /** 兼容多种缓存 token 字段名。 */
    private Integer cacheReadInputTokens(JsonNode usage) {
        Integer v = integer(usage.path("prompt_tokens_details"), "cached_tokens");
        if (v != null) return v;
        v = integer(usage, "cached_tokens");
        if (v != null) return v;
        v = integer(usage, "cache_read_input_tokens");
        if (v != null) return v;
        return integer(usage, "prompt_cache_hit_tokens");
    }

    /** 从 JSON 节点安全提取整数字段。 */
    protected Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (value.canConvertToInt()) return value.asInt();
        try { return value.isTextual() ? Integer.parseInt(value.asText()) : null; }
        catch (NumberFormatException e) { return null; }
    }

    // ==================== 错误处理 ====================

    /** HTTP 状态码 → ErrorCode 映射。 */
    protected ErrorCode httpStatusToErrorCode(int status) {
        if (status == 401 || status == 403) return ErrorCode.PROVIDER_AUTH_FAILED;
        if (status == 429) return ErrorCode.PROVIDER_RATE_LIMITED;
        if (status >= 500) return ErrorCode.PROVIDER_UNAVAILABLE;
        if (status == 400) return ErrorCode.PROVIDER_BAD_RESPONSE;
        return ErrorCode.PROVIDER_BAD_RESPONSE;
    }

    /** 错误前缀文本。 */
    protected String prefix(int status) {
        return switch (httpStatusToErrorCode(status)) {
            case PROVIDER_AUTH_FAILED -> "Provider authentication failed";
            case PROVIDER_RATE_LIMITED -> "Provider rate limited";
            case PROVIDER_UNAVAILABLE -> "Provider server error";
            default -> "Provider request failed";
        };
    }

    /** 构造上游错误消息（含脱敏响应体）。 */
    protected String upstreamError(String prefix, int statusCode, String responseBody) {
        String body = LogSanitizer.sanitizeBody(responseBody);
        if (body.isBlank()) return prefix + ": status=" + statusCode;
        return prefix + ": status=" + statusCode + ", body=" + body;
    }

    /** 将 RestClientResponseException 转为 ProviderException。 */
    protected ProviderException providerError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        return new ProviderException(httpStatusToErrorCode(status), HttpStatus.BAD_GATEWAY,
                upstreamError(prefix(status), status, e.getResponseBodyAsString()));
    }

    /** 将通用异常转为 PROVIDER_UNAVAILABLE。 */
    protected ProviderException providerUnavailable(Exception e) {
        String msg = e.getMessage();
        Throwable root = rootCause(e);
        if (root != e && root.getMessage() != null && !root.getMessage().equals(msg))
            msg = msg + "; rootCause=" + root.getClass().getSimpleName() + ": " + root.getMessage();
        return new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                "Provider request failed: " + LogSanitizer.sanitizeBody(msg == null ? e.getClass().getSimpleName() : msg));
    }

    protected ProviderException emptyResponse() {
        return new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Provider returned empty response");
    }

    protected ProviderException unsupported(EndpointType endpointType) {
        return new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "Endpoint " + endpointType + " not supported by provider " + type());
    }

    private Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    /** 安全序列化对象为 JSON 字符串，失败时返回错误提示。 */
    protected String safeWriteJson(Object value) {
        if (value == null) return "null";
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return "<serialization error: " + e.getMessage() + ">"; }
    }
}
