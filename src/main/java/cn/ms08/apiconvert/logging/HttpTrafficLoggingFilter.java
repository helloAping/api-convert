package cn.ms08.apiconvert.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 在控制器处理完成后记录入站 HTTP 请求和响应。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpTrafficLoggingFilter extends OncePerRequestFilter {

    /**
     * 入站 HTTP 流量专用日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(HttpTrafficLoggingFilter.class);
    /**
     * 请求体日志缓存的最大字节数；响应体仍由 LogSanitizer 负责截断。
     */
    private static final int BODY_CACHE_LIMIT = 16 * 1024;

    /**
     * 包装请求和响应，确保记录日志后控制器和客户端仍能读取正文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, BODY_CACHE_LIMIT);
        if (shouldSkipResponseCache(request)) {
            logStreamingRequest(request, response, filterChain, wrappedRequest);
            return;
        }
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long latencyMs = System.currentTimeMillis() - startedAt;
            log.info("HTTP inbound {} {} status={} latencyMs={} requestHeaders={} requestBody={} responseBody={}",
                    request.getMethod(),
                    requestUri(request),
                    wrappedResponse.getStatus(),
                    latencyMs,
                    LogSanitizer.sanitizeHeaders(toHeaders(wrappedRequest)),
                    readRequestBody(wrappedRequest),
                    readResponseBody(wrappedResponse));
            wrappedResponse.copyBodyToResponse();
        }
    }

    /**
     * SSE 响应不能被 ContentCachingResponseWrapper 缓存，否则客户端无法实时收到增量数据。
     */
    private void logStreamingRequest(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain,
                                     ContentCachingRequestWrapper wrappedRequest) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            long latencyMs = System.currentTimeMillis() - startedAt;
            log.info("HTTP inbound {} {} status={} latencyMs={} requestHeaders={} requestBody={} responseBody=<stream>",
                    request.getMethod(),
                    requestUri(request),
                    response.getStatus(),
                    latencyMs,
                    LogSanitizer.sanitizeHeaders(toHeaders(wrappedRequest)),
                    readRequestBody(wrappedRequest));
        }
    }

    /**
     * 对话接口可能由请求体 stream=true 决定 SSE 返回；不缓存响应，避免通配 Accept 客户端被阻塞。
     */
    private boolean shouldSkipResponseCache(HttpServletRequest request) {
        if ("/v1/chat/completions".equals(request.getRequestURI())) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase().contains("text/event-stream");
    }

    /**
     * 还原路径和查询参数，便于日志关联。
     */
    private String requestUri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    /**
     * 将 Servlet 请求头复制为 Spring HttpHeaders，复用统一脱敏逻辑。
     */
    private org.springframework.http.HttpHeaders toHeaders(HttpServletRequest request) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(name -> headers.add(name, request.getHeader(name)));
        return headers;
    }

    /**
     * 在下游过滤器或控制器消费后读取缓存请求体，并进行脱敏。
     */
    private String readRequestBody(ContentCachingRequestWrapper request) {
        byte[] body = request.getContentAsByteArray();
        if (body.length == 0) {
            return "";
        }
        return LogSanitizer.sanitizeBody(new String(body, charset(request.getCharacterEncoding())));
    }

    /**
     * 在响应体复制回客户端前读取缓存响应体，并进行脱敏。
     */
    private String readResponseBody(ContentCachingResponseWrapper response) {
        byte[] body = response.getContentAsByteArray();
        if (body.length == 0) {
            return "";
        }
        return LogSanitizer.sanitizeBody(new String(body, charset(response.getCharacterEncoding())));
    }

    /**
     * 解析声明的字符集；缺失或非法时回退到 UTF-8。
     */
    private Charset charset(String encoding) {
        if (encoding == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
