package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.admin.GatewayInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端控制台元信息接口，用于展示外部客户端调用网关所需的基础地址和端点清单。
 * 端点清单由 EndpointType 枚举自动推导，新增端点时无需修改此控制器。
 */
@RestController
@RequestMapping("/api/admin/gateway-info")
public class AdminGatewayInfoController {

    /**
     * 返回当前请求推导出的后端 Base URL 和已支持的公开网关接口。
     */
    @GetMapping
    public ApiResponse<GatewayInfoVO> info(HttpServletRequest request) {
        return ApiResponse.success(new GatewayInfoVO(baseUrl(request), EndpointType.allEndpointVOs()));
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
