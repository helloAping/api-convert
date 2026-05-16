package cn.ms08.apiconvert.controller;

import cn.ms08.apiconvert.adapter.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.service.ChatGatewayService;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
public class AnthropicMessageController {

    private final AnthropicRequestAdapter requestAdapter;
    private final AnthropicResponseAdapter responseAdapter;
    private final ChatGatewayService chatGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicMessageController(AnthropicRequestAdapter requestAdapter, AnthropicResponseAdapter responseAdapter,
                                      ChatGatewayService chatGatewayService) {
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
        this.chatGatewayService = chatGatewayService;
    }

    @PostMapping("/v1/messages")
    public void messages(@RequestBody AnthropicMessageRequest request,
                         HttpServletRequest servletRequest,
                         HttpServletResponse servletResponse) throws IOException {
        UnifiedChatRequest unifiedRequest = requestAdapter.toUnified(request);
        if (Boolean.TRUE.equals(unifiedRequest.stream())) {
            servletResponse.setStatus(HttpServletResponse.SC_OK);
            servletResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            servletResponse.setHeader("Cache-Control", "no-cache");
            servletResponse.setHeader("x-accel-buffering", "no");
            StreamingResponseBody body = chatGatewayService.stream(unifiedRequest, servletRequest, "anthropic", "messages");
            body.writeTo(servletResponse.getOutputStream());
            return;
        }
        UnifiedChatResponse unifiedResponse = chatGatewayService.chat(unifiedRequest, servletRequest, "anthropic", "messages");
        AnthropicMessageResponse response = responseAdapter.toAnthropic(unifiedResponse, unifiedRequest.model());
        servletResponse.setStatus(HttpServletResponse.SC_OK);
        servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(servletResponse.getOutputStream(), response);
    }
}
