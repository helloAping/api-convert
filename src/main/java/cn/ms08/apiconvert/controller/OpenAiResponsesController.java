package cn.ms08.apiconvert.controller;

import cn.ms08.apiconvert.adapter.OpenAiResponsesRequestAdapter;
import cn.ms08.apiconvert.adapter.OpenAiResponsesResponseAdapter;
import cn.ms08.apiconvert.adapter.RealTimeResponsesTransformer;
import cn.ms08.apiconvert.dto.OpenAiResponsesRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.service.ChatGatewayService;
import cn.ms08.apiconvert.vo.OpenAiResponsesResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

/**
 * OpenAI Responses API 兼容入口，适配 input/instructions 等字段后由网关统一路由。
 * <p>
 * 流式模式：返回 StreamingResponseBody（不包装在 ResponseEntity 中），由 Spring MVC 的
 * StreamingResponseBodyReturnValueHandler 在异步线程中写出 SSE 事件流。响应头部直接在
 * HttpServletResponse 上设置。非流式模式正常返回 ResponseEntity&lt;OpenAiResponsesResponse&gt;。
 */
@RestController
public class OpenAiResponsesController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesController.class);

    private final OpenAiResponsesRequestAdapter requestAdapter;
    private final OpenAiResponsesResponseAdapter responseAdapter;
    private final ChatGatewayService chatGatewayService;

    public OpenAiResponsesController(OpenAiResponsesRequestAdapter requestAdapter,
                                     OpenAiResponsesResponseAdapter responseAdapter,
                                     ChatGatewayService chatGatewayService) {
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
        this.chatGatewayService = chatGatewayService;
    }

    /**
     * OpenAI Responses API 兼容入口。
     * <p>
     * 流式模式：直接在 HttpServletResponse 上设置头部，然后返回 StreamingResponseBody，
     * 由 Spring MVC 的 StreamingResponseBodyReturnValueHandler 在异步线程中写出 SSE 事件流。
     * 注意：StreamingResponseBody 不能包装在 ResponseEntity 中返回，否则 Spring 会尝试
     * 用消息转换器序列化它（对 text/event-stream 没有合适的转换器）导致
     * HttpMediaTypeNotAcceptableException。
     */
    @PostMapping("/v1/responses")
    public Object createResponse(@RequestBody OpenAiResponsesRequest request,
                                 HttpServletRequest servletRequest,
                                 HttpServletResponse servletResponse) {
        UnifiedChatRequest unifiedRequest = requestAdapter.toUnified(request);
        boolean stream = Boolean.TRUE.equals(unifiedRequest.stream())
                || isStreamingAccept(servletRequest);
        log.info("/v1/responses 请求: model={}, stream={}（请求体stream={}、Accept头流式={}）, instructions长度={}",
                unifiedRequest.model(), stream, unifiedRequest.stream(),
                isStreamingAccept(servletRequest),
                request.getInstructions() != null ? request.getInstructions().length() : 0);

        if (stream) {
            String responseId = "resp_" + UUID.randomUUID().toString().replace("-", "");
            String model = unifiedRequest.model();
            long createdAt = System.currentTimeMillis() / 1000;

            // 直接在 HttpServletResponse 上设置头部，StreamingResponseBody 不能包装在 ResponseEntity 中
            servletResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            servletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
            servletResponse.setHeader("x-accel-buffering", "no");

            return (StreamingResponseBody) outputStream -> {
                RealTimeResponsesTransformer transformer = new RealTimeResponsesTransformer(
                        outputStream, responseId, model, createdAt);
                transformer.sendInitialEvents();
                try {
                    StreamingResponseBody upstreamBody = chatGatewayService.stream(
                            unifiedRequest, servletRequest, "openai", "responses");
                    upstreamBody.writeTo(transformer);
                } catch (Exception e) {
                    if (!transformer.isCompletedEventSent()) {
                        try {
                            transformer.writeErrorWithMessage(
                                    "Upstream streaming failed: " + e.getMessage());
                        } catch (Exception ignored) {
                            // 已无恢复可能，忽略写错误
                        }
                    }
                } finally {
                    transformer.flush();
                }
            };
        }

        // 非流式路径：返回 ResponseEntity 由 Spring 正常序列化为 JSON
        UnifiedChatResponse unifiedResponse = chatGatewayService.chat(
                unifiedRequest, servletRequest, "openai", "responses");
        OpenAiResponsesResponse response = responseAdapter.toOpenAiResponses(
                unifiedResponse, unifiedRequest.model());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    /**
     * 检查请求头 Accept 是否包含 text/event-stream，兼容不传 stream=true 的客户端。
     */
    private static boolean isStreamingAccept(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase().contains("text/event-stream");
    }
}
