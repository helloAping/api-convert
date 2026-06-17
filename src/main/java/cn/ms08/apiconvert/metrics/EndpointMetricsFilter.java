package cn.ms08.apiconvert.metrics;

import cn.ms08.apiconvert.endpoint.EndpointType;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 端点请求指标过滤器：为每个公开端点（/v1/* 路径）记录请求总数和耗时分布。
 * <p>
 * 该过滤器只关心 HTTP 入口处的请求/响应维度（端点、状态码、耗时），不感知上游调用细节；
 * 上游维度的指标由 {@link ChatGatewayService} / {@link cn.ms08.apiconvert.service.ImageGatewayService}
 * 等网关服务层通过 {@link GatewayMetrics#recordUpstream} 单独记录。
 * <p>
 * 优先级低于 {@link cn.ms08.apiconvert.logging.RequestContextFilter}，避免 MDC 写入时
 * 计时器尚未启动导致耗时统计偏差。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class EndpointMetricsFilter extends OncePerRequestFilter {

    private static final String GATEWAY_API_PREFIX = "/v1/";

    private static final Map<String, EndpointType> PATH_TO_ENDPOINT = Map.of(
            "/v1/chat/completions", EndpointType.CHAT_COMPLETIONS,
            "/v1/messages", EndpointType.ANTHROPIC_MESSAGES,
            "/v1/responses", EndpointType.OPENAI_RESPONSES,
            "/v1/videos", EndpointType.OPENAI_VIDEOS,
            "/v1/images/generations", EndpointType.OPENAI_IMAGES,
            "/v1/models", EndpointType.OPENAI_MODELS,
            "/v1/embeddings", EndpointType.OPENAI_EMBEDDINGS,
            "/v1/audio/speech", EndpointType.AUDIO_SPEECH,
            "/v1/audio/transcriptions", EndpointType.AUDIO_TRANSCRIPTIONS
    );

    private final GatewayMetrics metrics;

    public EndpointMetricsFilter(GatewayMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        EndpointType endpoint = resolveEndpoint(request.getRequestURI());
        if (endpoint == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Timer.Sample sample = metrics.startRequest(endpoint);
        StatusCapturingResponse wrapped = new StatusCapturingResponse(response);
        try {
            filterChain.doFilter(request, wrapped);
        } finally {
            int status = wrapped.getStatus();
            if (status == 0) {
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            }
            metrics.stopRequest(sample, endpoint, status);
        }
    }

    private EndpointType resolveEndpoint(String requestUri) {
        if (requestUri == null || !requestUri.startsWith(GATEWAY_API_PREFIX)) {
            return null;
        }
        return PATH_TO_ENDPOINT.get(requestUri);
    }

    /**
     * 拦截 {@code setStatus} / {@code sendError} 调用以记录最终 HTTP 状态码；
     * 下游 filter 抛异常由 GlobalExceptionHandler 处理后会再次进入容器并设置状态码，
     * 通过此包装器能正确捕获。
     */
    private static class StatusCapturingResponse extends HttpServletResponseWrapper {
        private int capturedStatus;

        StatusCapturingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.capturedStatus = sc;
            super.setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.capturedStatus = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.capturedStatus = sc;
            super.sendError(sc, msg);
        }

        @Override
        public int getStatus() {
            return capturedStatus != 0 ? capturedStatus : super.getStatus();
        }
    }
}
