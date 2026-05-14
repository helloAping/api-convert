package cn.ms08.apiconvert.controller;

import cn.ms08.apiconvert.adapter.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import cn.ms08.apiconvert.service.ChatGatewayService;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

/**
 * OpenAI Chat Completions 兼容入口，负责接收外部工具的 OpenAI 格式对话请求。
 */
@RestController
public class OpenAiChatController {

    /**
     * OpenAI 请求转统一请求适配器。
     */
    private final OpenAiRequestAdapter requestAdapter;
    /**
     * 统一响应转 OpenAI 响应适配器。
     */
    private final OpenAiResponseAdapter responseAdapter;
    /**
     * 统一对话网关，负责路由、转发和日志记录。
     */
    private final ChatGatewayService chatGatewayService;
    /**
     * 单入口按请求体决定 JSON 或 SSE，响应体由控制器直接写出，避免通配 Accept 选错 handler。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 注入 OpenAI 兼容入口依赖。
     */
    public OpenAiChatController(OpenAiRequestAdapter requestAdapter, OpenAiResponseAdapter responseAdapter,
                                ChatGatewayService chatGatewayService) {
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
        this.chatGatewayService = chatGatewayService;
    }

    /**
     * OpenAI Chat Completions 兼容接口；根据请求体 stream 字段决定返回 JSON 或 SSE。
     */
    @PostMapping("/v1/chat/completions")
    public void chatCompletions(@RequestBody OpenAiChatCompletionRequest request,
                                HttpServletRequest servletRequest,
                                HttpServletResponse servletResponse) throws IOException {
        UnifiedChatRequest unifiedRequest = requestAdapter.toUnified(request);
        if (Boolean.TRUE.equals(unifiedRequest.stream())) {
            servletResponse.setStatus(HttpServletResponse.SC_OK);
            servletResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            servletResponse.setHeader("Cache-Control", "no-cache");
            StreamingResponseBody body = chatGatewayService.stream(unifiedRequest, servletRequest);
            body.writeTo(servletResponse.getOutputStream());
            return;
        }
        UnifiedChatResponse unifiedResponse = chatGatewayService.chat(unifiedRequest, servletRequest);
        OpenAiChatCompletionResponse response = responseAdapter.toOpenAi(unifiedResponse, unifiedRequest.model());
        servletResponse.setStatus(HttpServletResponse.SC_OK);
        servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(servletResponse.getOutputStream(), response);
    }
}
