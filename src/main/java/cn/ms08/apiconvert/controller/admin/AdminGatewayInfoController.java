package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.admin.GatewayInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端控制台元信息接口，用于展示外部客户端调用网关所需的基础地址和端点清单。
 */
@RestController
@RequestMapping("/admin/gateway-info")
public class AdminGatewayInfoController {

    /**
     * 返回当前请求推导出的后端 Base URL 和已支持的公开网关接口。
     */
    @GetMapping
    public ApiResponse<GatewayInfoVO> info(HttpServletRequest request) {
        return ApiResponse.success(new GatewayInfoVO(baseUrl(request), endpoints()));
    }

    /**
     * 优先使用反向代理传入的外部访问地址，便于部署在网关或 HTTPS 入口之后时展示正确地址。
     */
    private String baseUrl(HttpServletRequest request) {
        String forwardedHost = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        if (StringUtils.hasText(forwardedHost)) {
            String scheme = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
            if (!StringUtils.hasText(scheme)) {
                scheme = request.getScheme();
            }
            return trimTrailingSlash(scheme + "://" + forwardedHost + request.getContextPath());
        }
        return trimTrailingSlash(request.getRequestURL()
                .substring(0, request.getRequestURL().length() - request.getRequestURI().length())
                + request.getContextPath());
    }

    /**
     * 当前已实现的公开调用端点；管理端自身的 CRUD 接口不列入外部客户端调用清单。
     */
    private List<GatewayInfoVO.EndpointVO> endpoints() {
        return List.of(
                new GatewayInfoVO.EndpointVO("GET", "/health", "通用", "无需鉴权", "健康检查和基础统计"),
                new GatewayInfoVO.EndpointVO("GET", "/v1/models", "OpenAI", "Gateway API Key", "OpenAI 兼容模型列表"),
                new GatewayInfoVO.EndpointVO("POST", "/v1/chat/completions", "OpenAI", "Gateway API Key", "OpenAI 兼容聊天补全，支持 SSE 流式透传"),
                new GatewayInfoVO.EndpointVO("POST", "/v1/messages", "Anthropic", "Gateway API Key", "Anthropic Messages 兼容对话")
        );
    }

    /**
     * X-Forwarded-* 可能包含逗号分隔的代理链，取最靠近客户端的第一个值。
     */
    private String firstForwardedValue(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.split(",", 2)[0].trim();
    }

    /**
     * 统一去掉末尾斜杠，前端拼接端点时保持显示稳定。
     */
    private String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
