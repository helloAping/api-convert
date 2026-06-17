package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.circuitbreaker.CircuitBreaker;
import cn.ms08.apiconvert.circuitbreaker.CircuitBreakerProperties;
import cn.ms08.apiconvert.circuitbreaker.CircuitBreakerRegistry;
import cn.ms08.apiconvert.security.GatewayApiKeyFilter;
import cn.ms08.apiconvert.vo.admin.CircuitBreakerVO;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 熔断器管理接口：列出所有维度的熔断器状态，并支持手动重置（强制 CLOSED）。
 * 不提供 OPEN 操作——OPEN 应由真实失败率驱动，避免误操作把已知不可用渠道加回路由。
 */
@RestController
@RequestMapping("/api/admin/circuit-breakers")
public class AdminCircuitBreakerController {

    private final CircuitBreakerRegistry registry;

    public AdminCircuitBreakerController(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<CircuitBreakerVO> list(HttpServletRequest request) {
        requireAdmin(request);
        return registry.all().stream()
                .map(CircuitBreakerVO::of)
                .toList();
    }

    @GetMapping("/properties")
    public CircuitBreakerProperties properties(HttpServletRequest request) {
        requireAdmin(request);
        return registry.properties();
    }

    @PostMapping("/{providerCode}/{providerModel}/reset")
    public CircuitBreakerVO reset(@PathVariable String providerCode, @PathVariable String providerModel,
                                  HttpServletRequest request) {
        requireAdmin(request);
        CircuitBreaker breaker = registry.forKey(providerCode, providerModel);
        breaker.reset();
        return CircuitBreakerVO.of(breaker);
    }

    private void requireAdmin(HttpServletRequest request) {
        // Sa-Token admin auth filter 已经将未授权请求挡在 controller 之前，这里只做轻量二次确认。
        if (!StpUtil.isLogin()) {
            throw new IllegalStateException("Admin authentication required");
        }
        Object principal = request.getAttribute(GatewayApiKeyFilter.PRINCIPAL_ATTRIBUTE);
        if (principal != null) {
            throw new IllegalStateException("Gateway API key cannot access admin endpoints");
        }
    }
}
