package cn.ms08.apiconvert.controller;

import cn.ms08.apiconvert.endpoint.EndpointRegistry;
import cn.ms08.apiconvert.endpoint.EndpointType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 网关公开 API 统一调度入口，根据请求路径分派到对应的 EndpointHandler。
 * 不含业务逻辑，仅负责路由转发。
 */
@RestController
public class GatewayController {

    private final EndpointRegistry endpointRegistry;

    public GatewayController(EndpointRegistry endpointRegistry) {
        this.endpointRegistry = endpointRegistry;
    }

    @PostMapping("/v1/chat/completions")
    public void chatCompletions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        endpointRegistry.get(EndpointType.CHAT_COMPLETIONS).handle(request, response);
    }

    @PostMapping("/v1/messages")
    public void messages(HttpServletRequest request, HttpServletResponse response) throws IOException {
        endpointRegistry.get(EndpointType.ANTHROPIC_MESSAGES).handle(request, response);
    }

    @PostMapping("/v1/responses")
    public void responses(HttpServletRequest request, HttpServletResponse response) throws IOException {
        endpointRegistry.get(EndpointType.OPENAI_RESPONSES).handle(request, response);
    }

    @GetMapping("/v1/models")
    public void models(HttpServletRequest request, HttpServletResponse response) throws IOException {
        endpointRegistry.get(EndpointType.OPENAI_MODELS).handle(request, response);
    }
}
