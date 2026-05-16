package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.OpenAiChatCompletionRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.service.ChatGatewayService;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

/**
 * OpenAI Chat Completions 端点处理器，处理 POST /v1/chat/completions。
 */
@Component
public class ChatCompletionsEndpointHandler implements EndpointHandler {

    private final OpenAiRequestAdapter requestAdapter;
    private final OpenAiResponseAdapter responseAdapter;
    private final ChatGatewayService chatGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatCompletionsEndpointHandler(OpenAiRequestAdapter requestAdapter, OpenAiResponseAdapter responseAdapter,
                                          ChatGatewayService chatGatewayService) {
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
        this.chatGatewayService = chatGatewayService;
    }

    @Override
    public EndpointType endpointType() {
        return EndpointType.CHAT_COMPLETIONS;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OpenAiChatCompletionRequest chatRequest = objectMapper.readValue(
                request.getInputStream(), OpenAiChatCompletionRequest.class);
        UnifiedChatRequest unifiedRequest = requestAdapter.toUnified(chatRequest);

        if (Boolean.TRUE.equals(unifiedRequest.stream())) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setHeader("Cache-Control", "no-cache");
            StreamingResponseBody body = chatGatewayService.stream(unifiedRequest, request, EndpointType.CHAT_COMPLETIONS);
            body.writeTo(response.getOutputStream());
            return;
        }

        UnifiedChatResponse unifiedResponse = chatGatewayService.chat(unifiedRequest, request, EndpointType.CHAT_COMPLETIONS);
        OpenAiChatCompletionResponse apiResponse = responseAdapter.toOpenAi(unifiedResponse, unifiedRequest.model());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
