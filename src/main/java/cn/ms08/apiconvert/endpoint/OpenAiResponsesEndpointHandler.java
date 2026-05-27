package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.dto.OpenAiResponsesRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.service.ChatGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

/**
 * OpenAI Responses API 端点处理器，处理 POST /v1/responses。
 */
@Component
public class OpenAiResponsesEndpointHandler implements EndpointHandler {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesEndpointHandler.class);

    private final OpenAiResponsesRequestAdapter requestAdapter;
    private final ChatGatewayService chatGatewayService;
    private final ObjectMapper objectMapper;

    /**
     * 注入网关统一 ObjectMapper，确保长 base64 字符串和时间格式配置在端点解析时生效。
     */
    public OpenAiResponsesEndpointHandler(OpenAiResponsesRequestAdapter requestAdapter,
                                          ChatGatewayService chatGatewayService,
                                          ObjectMapper objectMapper) {
        this.requestAdapter = requestAdapter;
        this.chatGatewayService = chatGatewayService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EndpointType endpointType() {
        return EndpointType.OPENAI_RESPONSES;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OpenAiResponsesRequest responsesRequest = objectMapper.readValue(
                request.getInputStream(), OpenAiResponsesRequest.class);
        UnifiedChatRequest unifiedRequest = requestAdapter.toUnified(responsesRequest);

        boolean stream = Boolean.TRUE.equals(unifiedRequest.stream())
                || isStreamingAccept(request);
        log.info("/v1/responses request: model={}, stream={} (bodyStream={}, acceptStream={}), instructionsLength={}",
                unifiedRequest.model(), stream, unifiedRequest.stream(),
                isStreamingAccept(request),
                responsesRequest.getInstructions() != null ? responsesRequest.getInstructions().length() : 0);

        if (stream) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            response.setHeader("x-accel-buffering", "no");
            StreamingResponseBody streamBody = chatGatewayService.stream(
                    unifiedRequest, request, EndpointType.OPENAI_RESPONSES);
            streamBody.writeTo(response.getOutputStream());
            return;
        }

        UnifiedChatResponse unifiedResponse = chatGatewayService.chat(
                unifiedRequest, request, EndpointType.OPENAI_RESPONSES);

        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), unifiedResponse.rawResponse());
    }

    /**
     * 检查请求头 Accept 是否包含 text/event-stream。
     */
    private static boolean isStreamingAccept(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase().contains("text/event-stream");
    }
}
