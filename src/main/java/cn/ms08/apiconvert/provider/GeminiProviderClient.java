package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.logging.LogSanitizer;
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GeminiProviderClient implements AiProviderClient {

    private static final String GENERATE_CONTENT_ACTION = ":generateContent";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiProviderClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public ProviderType type() { return ProviderType.GEMINI; }

    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        return doGeminiChat(route, request);
    }

    @Override
    public UnifiedChatResponse messages(ModelRoute route, UnifiedChatRequest request) {
        return doGeminiChat(route, request);
    }

    @Override
    public UnifiedChatResponse responses(ModelRoute route, UnifiedChatRequest request) {
        return doGeminiChat(route, request);
    }

    private UnifiedChatResponse doGeminiChat(ModelRoute route, UnifiedChatRequest request) {
        String url = "/v1beta/models/" + route.providerModel() + GENERATE_CONTENT_ACTION;
        try {
            String body = restClientBuilder.clone().baseUrl(route.baseUrl()).build()
                    .post().uri(url)
                    .header("x-goog-api-key", route.apiKey())
                    .body(buildGeminiRequest(request))
                    .retrieve().body(String.class);
            if (body == null || body.isBlank())
                throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "Gemini returned empty response");
            return parseGeminiResponse(body, route.providerModel());
        } catch (ProviderException e) { throw e; }
        catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new ProviderException(httpStatusToErrorCode(status), HttpStatus.BAD_GATEWAY,
                    upstreamError(prefix(status), e));
        } catch (RestClientException e) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Gemini request failed: " + e.getMessage());
        }
    }

    @Override
    public boolean supportsStreaming(EndpointType endpointType) { return false; }

    @Override
    public UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST, "Gemini does not support streaming");
    }

    @Override
    public UnifiedUsage streamMessages(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST, "Gemini does not support streaming");
    }

    @Override
    public UnifiedUsage streamResponses(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST, "Gemini does not support streaming");
    }

    @Override
    public OpenAiVideoResponse generateVideo(ModelRoute route, OpenAiVideoRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST, "Gemini does not support video generation");
    }

    @Override
    public OpenAiImageResponse generateImage(ModelRoute route, OpenAiImageRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST, "Gemini does not support image generation");
    }

    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        try {
            String body = restClientBuilder.clone().baseUrl(request.baseUrl()).build()
                    .get().uri(request.modelsPath())
                    .header("x-goog-api-key", request.apiKey())
                    .retrieve().body(String.class);
            return parseGeminiModelList(body);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            throw new ProviderException(httpStatusToErrorCode(status), HttpStatus.BAD_GATEWAY,
                    upstreamError(prefix(status), e));
        } catch (RestClientException | IllegalArgumentException e) {
            throw new ProviderException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_GATEWAY,
                    "Gemini models request failed: " + e.getMessage());
        }
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "Google Gemini 当前没有通用额度查询接口，请在 Google Cloud 控制台查看。", null, null, null, "", "");
    }

    private ObjectNode buildGeminiRequest(UnifiedChatRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        List<UnifiedMessage> contents = new ArrayList<>();
        StringBuilder systemText = new StringBuilder();
        for (UnifiedMessage msg : request.messages()) {
            if ("system".equals(msg.role())) {
                if (systemText.length() > 0) systemText.append("\n");
                systemText.append(msg.content());
            } else {
                contents.add(msg);
            }
        }
        if (systemText.length() > 0) {
            ObjectNode instruction = objectMapper.createObjectNode();
            instruction.put("text", systemText.toString());
            root.set("system_instruction", objectMapper.createObjectNode().set("parts", objectMapper.createArrayNode().add(instruction)));
        }
        ArrayNode contentsArray = objectMapper.createArrayNode();
        for (UnifiedMessage msg : contents) {
            ObjectNode content = objectMapper.createObjectNode();
            content.put("role", "user".equals(msg.role()) ? "user" : "model");
            ArrayNode parts = objectMapper.createArrayNode();
            ObjectNode part = objectMapper.createObjectNode();
            part.put("text", msg.content() != null ? msg.content().toString() : "");
            parts.add(part);
            content.set("parts", parts);
            contentsArray.add(content);
        }
        root.set("contents", contentsArray);
        ObjectNode config = objectMapper.createObjectNode();
        if (request.maxTokens() != null) config.put("maxOutputTokens", request.maxTokens());
        if (request.temperature() != null) config.put("temperature", request.temperature());
        root.set("generationConfig", config);
        return root;
    }

    private UnifiedChatResponse parseGeminiResponse(String body, String model) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode candidates = root.path("candidates");
            StringBuilder text = new StringBuilder();
            if (candidates.isArray()) {
                for (JsonNode candidate : candidates) {
                    JsonNode parts = candidate.path("content").path("parts");
                    if (parts.isArray()) {
                        for (JsonNode part : parts) {
                            String partText = part.path("text").asText("");
                            if (!partText.isEmpty()) {
                                if (text.length() > 0) text.append("\n");
                                text.append(partText);
                            }
                        }
                    }
                }
            }
            JsonNode usageMeta = root.path("usageMetadata");
            UnifiedUsage usage = null;
            if (!usageMeta.isMissingNode()) {
                usage = new UnifiedUsage(
                        integer(usageMeta, "promptTokenCount"),
                        integer(usageMeta, "candidatesTokenCount"),
                        integer(usageMeta, "totalTokenCount"),
                        integer(usageMeta, "cachedContentTokenCount"));
            }
            List<UnifiedMessage> responseMessages = List.of(new UnifiedMessage("assistant", text.toString(), null));
            Map<String, Object> rawResponse = new LinkedHashMap<>();
            rawResponse.put("id", "gemini-" + java.util.UUID.randomUUID().toString().substring(0, 8));
            rawResponse.put("model", model);
            return new UnifiedChatResponse("gemini-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                    model, responseMessages, usage, rawResponse);
        } catch (Exception e) {
            throw new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY,
                    "Failed to parse Gemini response: " + LogSanitizer.sanitizeBody(body) + ": " + e.getMessage());
        }
    }

    private List<ProviderModel> parseGeminiModelList(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode models = root.path("models");
            if (!models.isArray()) models = root.path("data");
            if (!models.isArray()) throw new IllegalArgumentException("Gemini model list response missing models/data array");
            List<ProviderModel> result = new ArrayList<>();
            for (JsonNode item : models) {
                String id = item.path("name").asText("");
                if (id.isBlank()) id = item.path("id").asText("");
                if (!id.isBlank()) {
                    String displayName = id.startsWith("models/") ? id.substring(7) : id;
                    JsonNode methods = item.path("supportedGenerationMethods");
                    if (methods.isArray()) {
                        boolean supportsGenerate = false;
                        for (JsonNode method : methods) {
                            if ("generateContent".equals(method.asText())) { supportsGenerate = true; break; }
                        }
                        if (!supportsGenerate) continue;
                    }
                    result.add(new ProviderModel(displayName, "google"));
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Gemini model list response: " + LogSanitizer.sanitizeBody(body), e);
        }
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (value.canConvertToInt()) return value.asInt();
        try { return value.isTextual() ? Integer.parseInt(value.asText()) : null; }
        catch (NumberFormatException e) { return null; }
    }

    private ErrorCode httpStatusToErrorCode(int status) {
        if (status == 401 || status == 403) return ErrorCode.PROVIDER_AUTH_FAILED;
        if (status == 429) return ErrorCode.PROVIDER_RATE_LIMITED;
        if (status >= 500) return ErrorCode.PROVIDER_UNAVAILABLE;
        if (status == 400) return ErrorCode.PROVIDER_BAD_RESPONSE;
        return ErrorCode.PROVIDER_BAD_RESPONSE;
    }

    private String prefix(int status) {
        return switch (httpStatusToErrorCode(status)) {
            case PROVIDER_AUTH_FAILED -> "Gemini authentication failed";
            case PROVIDER_RATE_LIMITED -> "Gemini rate limited";
            case PROVIDER_UNAVAILABLE -> "Gemini server error";
            default -> "Gemini request failed";
        };
    }

    private String upstreamError(String prefix, RestClientResponseException e) {
        String body = LogSanitizer.sanitizeBody(e.getResponseBodyAsString());
        int status = e.getStatusCode().value();
        if (body.isBlank()) return prefix + ": status=" + status;
        return prefix + ": status=" + status + ", body=" + body;
    }
}
