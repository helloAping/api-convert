package cn.ms08.apiconvert.controller;

import cn.ms08.apiconvert.adapter.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.AnthropicMessageRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.service.ChatGatewayService;
import cn.ms08.apiconvert.vo.AnthropicMessageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anthropic Messages 兼容入口，外部工具可按 Anthropic 协议调用网关。
 */
@RestController
public class AnthropicMessageController {

    /**
     * 将 Anthropic 请求转换为统一请求。
     */
    private final AnthropicRequestAdapter requestAdapter;
    /**
     * 将统一响应转换为 Anthropic 响应。
     */
    private final AnthropicResponseAdapter responseAdapter;
    /**
     * 统一聊天网关，负责鉴权后的路由、转发和日志记录。
     */
    private final ChatGatewayService chatGatewayService;

    /**
     * 注入 Anthropic 入口依赖。
     */
    public AnthropicMessageController(AnthropicRequestAdapter requestAdapter, AnthropicResponseAdapter responseAdapter,
                                      ChatGatewayService chatGatewayService) {
        this.requestAdapter = requestAdapter;
        this.responseAdapter = responseAdapter;
        this.chatGatewayService = chatGatewayService;
    }

    /**
     * Anthropic Messages 兼容接口，按请求模型随机选择可用渠道并转发。
     */
    @PostMapping("/v1/messages")
    public AnthropicMessageResponse messages(@RequestBody AnthropicMessageRequest request,
                                             HttpServletRequest servletRequest) {
        UnifiedChatRequest unifiedRequest = requestAdapter.toUnified(request);
        UnifiedChatResponse unifiedResponse = chatGatewayService.chat(unifiedRequest, servletRequest, "anthropic", "messages");
        return responseAdapter.toAnthropic(unifiedResponse, unifiedRequest.model());
    }
}
