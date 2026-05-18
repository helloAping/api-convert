package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.service.ChatGatewayService;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

/**
 * Anthropic Messages 端点处理器，处理 POST /v1/messages。
 */
@Component
public class AnthropicMessagesEndpointHandler implements EndpointHandler {

    private final AnthropicRequestAdapter requestAdapter;
    private final AnthropicResponseAdapter responseAdapter;
    private final ChatGatewayService chatGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnthropicMessagesEndpointHandler(AnthropicRequestAdapter requestAdapter,
                                            AnthropicResponseAdapter responseAdapter,
                                            ChatGatewayService chatGatewayService) {
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
        this.chatGatewayService = chatGatewayService;
    }

    @Override
    public EndpointType endpointType() {
        return EndpointType.ANTHROPIC_MESSAGES;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AnthropicMessageRequest messagesRequest = objectMapper.readValue(
                request.getInputStream(), AnthropicMessageRequest.class);
        UnifiedChatRequest unifiedRequest = requestAdapter.toUnified(messagesRequest);

        if (Boolean.TRUE.equals(unifiedRequest.stream())) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("x-accel-buffering", "no");
            StreamingResponseBody body = chatGatewayService.stream(unifiedRequest, request, EndpointType.ANTHROPIC_MESSAGES);
            body.writeTo(response.getOutputStream());
            return;
        }

        UnifiedChatResponse unifiedResponse = chatGatewayService.chat(unifiedRequest, request, EndpointType.ANTHROPIC_MESSAGES);
        AnthropicMessageResponse apiResponse = responseAdapter.toAnthropic(unifiedResponse, unifiedRequest.model());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
