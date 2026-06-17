package cn.ms08.apiconvert.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求上下文过滤器：从 {@code X-Request-Id} 请求头读取 requestId，缺失时生成 UUID；
 * 将 requestId 写入 SLF4J MDC 与响应头 {@code X-Request-Id}，便于跨日志关联和客户端排查。
 * 优先级最高，确保下游过滤器（鉴权/路由）均能在 MDC 中读到 requestId。
 */
@Component("gatewayRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final int REQUEST_ID_MAX_LENGTH = 128;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_ID_HEADER);
        if (StringUtils.hasText(header) && header.length() <= REQUEST_ID_MAX_LENGTH
                && header.chars().allMatch(c -> c >= 0x20 && c < 0x7F)) {
            return header;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
