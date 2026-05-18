package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.admin.GatewayInfoVO;

import java.util.Arrays;
import java.util.List;

/**
 * 网关公开端点枚举，每个常量携带 HTTP 方法、路径、协议标签、鉴权方式、说明和默认供应商。
 * 新增端点时只需添加常量并创建对应的 EndpointHandler 实现即可自动注册。
 */
public enum EndpointType {

    HEALTH("GET", "/health", "通用", "无需鉴权", "健康检查和基础统计", null),
    CHAT_COMPLETIONS("POST", "/v1/chat/completions", "OpenAI", "Gateway API Key",
            "OpenAI 兼容聊天补全，支持 SSE 流式透传、response_format（JSON 模式/JSON Schema）",
            ProviderType.OPENAI_COMPATIBLE),
    ANTHROPIC_MESSAGES("POST", "/v1/messages", "Anthropic", "Gateway API Key",
            "Anthropic Messages 兼容对话，支持 SSE 流式透传",
            ProviderType.ANTHROPIC),
    OPENAI_RESPONSES("POST", "/v1/responses", "OpenAI", "Gateway API Key",
            "OpenAI Responses API 新协议，支持 SSE 流式透传",
            ProviderType.OPENAI_RESPONSES),
    OPENAI_MODELS("GET", "/v1/models", "OpenAI", "Gateway API Key",
            "OpenAI 兼容模型列表",
            ProviderType.OPENAI_COMPATIBLE);

    private final String method;
    private final String path;
    private final String protocol;
    private final String auth;
    private final String description;
    private final ProviderType defaultProvider;

    EndpointType(String method, String path, String protocol, String auth, String description,
                 ProviderType defaultProvider) {
        this.method = method;
        this.path = path;
        this.protocol = protocol;
        this.auth = auth;
        this.description = description;
        this.defaultProvider = defaultProvider;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String protocol() {
        return protocol;
    }

    public String auth() {
        return auth;
    }

    public String description() {
        return description;
    }

    public ProviderType defaultProvider() {
        return defaultProvider;
    }

    /**
     * 转为管理端展示的端点视图对象。
     */
    public GatewayInfoVO.EndpointVO toEndpointVO() {
        return new GatewayInfoVO.EndpointVO(method, path, protocol, auth, description);
    }

    /**
     * 返回所有端点的视图对象列表，供 AdminGatewayInfoController 使用。
     */
    public static List<GatewayInfoVO.EndpointVO> allEndpointVOs() {
        return Arrays.stream(values())
                .map(EndpointType::toEndpointVO)
                .toList();
    }
}
