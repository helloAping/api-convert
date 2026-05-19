package cn.ms08.apiconvert.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
     * 全链路追踪 ID 的请求属性键名。
     */
    private static final String TRACE_ID_ATTR = "cn.ms08.apiconvert.traceId";

    /**
     * 包装请求和响应，确保记录日志后控制器和客户端仍能读取正文。
     * 静态资源路径不记录日志，避免前端构建产物请求污染日志。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isStaticResource(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        request.setAttribute(TRACE_ID_ATTR, traceId);
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, BODY_CACHE_LIMIT);
        if (shouldSkipResponseCache(request)) {
            try {
                logStreamingRequest(request, response, filterChain, wrappedRequest);
            } finally {
                MDC.remove("traceId");
            }
            return;
        }
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        long startedAt = System.currentTimeMillis();
        int responseStatus = HttpServletResponse.SC_OK;
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            responseStatus = wrappedResponse.getStatus();
        } catch (ServletException | IOException e) {
            responseStatus = resolveStatus(e);
            throw e;
        } catch (RuntimeException e) {
            responseStatus = resolveStatus(e);
            throw e;
        } finally {
            long latencyMs = System.currentTimeMillis() - startedAt;
            String requestBody = readRequestBody(wrappedRequest);
            String responseBody = readResponseBody(wrappedResponse);
            log.info("请求：{} {}、状态：{}、耗时：{}ms、请求头：{}、请求体：{}、响应体：{}、请求体长度：{}、响应体长度：{}",
                    request.getMethod(),
                    requestUri(request),
                    responseStatus,
                    latencyMs,
                    LogSanitizer.sanitizeHeaders(toHeaders(wrappedRequest)),
                    requestBody,
                    responseBody,
                    requestBody.length(),
                    responseBody.length());
            wrappedResponse.copyBodyToResponse();
            MDC.remove("traceId");
        }
    }

    /**
     * SSE 响应不能被 ContentCachingResponseWrapper 缓存，否则客户端无法实时收到增量数据。
     * 日志中标记为 <stream> 并附带接口路径，便于区分协议类型。
     */
    private void logStreamingRequest(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain,
                                     ContentCachingRequestWrapper wrappedRequest) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        int responseStatus = HttpServletResponse.SC_OK;
        try {
            filterChain.doFilter(wrappedRequest, response);
            responseStatus = response.getStatus();
        } catch (ServletException | IOException e) {
            responseStatus = resolveStatus(e);
            throw e;
        } catch (RuntimeException e) {
            responseStatus = resolveStatus(e);
            throw e;
        } finally {
            long latencyMs = System.currentTimeMillis() - startedAt;
            String requestBody = readRequestBody(wrappedRequest);
            log.info("请求：{} {}、状态：{}、耗时：{}ms、请求头：{}、请求体：{}、响应体：<stream>、请求体长度：{}",
                    request.getMethod(),
                    requestUri(request),
                    responseStatus,
                    latencyMs,
                    LogSanitizer.sanitizeHeaders(toHeaders(wrappedRequest)),
                    requestBody,
                    requestBody.length());
        }
    }

    /**
     * 从异常中提取 HTTP 状态码，兜底返回 500。
     */
    private static int resolveStatus(Exception e) {
        if (e instanceof cn.ms08.apiconvert.exception.GatewayException ge) {
            return ge.status().value();
        }
        return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    /**
     * SSE 接口需要流式返回，不能缓存响应体逐一发送完毕后才能写出。
     * /v1/chat/completions、/v1/responses 和 /v1/messages 的响应体标记为 <stream>。
     */
    private boolean shouldSkipResponseCache(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if ("/v1/chat/completions".equals(uri)
                || "/v1/responses".equals(uri)
                || "/v1/messages".equals(uri)) {
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

    /**
     * 判断是否为前端静态资源请求，不记录日志避免污染。
     */
    private boolean isStaticResource(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/") || uri.equals("/index.html")
                || uri.equals("/favicon.ico") || uri.equals("/favicon.svg") || uri.equals("/icons.svg")
                || uri.startsWith("/assets/");
    }
}
