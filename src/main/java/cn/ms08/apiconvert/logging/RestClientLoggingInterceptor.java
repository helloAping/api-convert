package cn.ms08.apiconvert.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 记录 RestClient 出站流量，同时保留响应体供后续正常处理。
 */
public class RestClientLoggingInterceptor implements ClientHttpRequestInterceptor {

    /**
     * 上游 HTTP 调用专用日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(RestClientLoggingInterceptor.class);

    /**
     * 执行上游请求、缓存响应体，并记录脱敏后的请求/响应数据。
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        long startedAt = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());
        long latencyMs = System.currentTimeMillis() - startedAt;
        log.info("HTTP outbound {} {} status={} latencyMs={} requestHeaders={} requestBody={} responseBody={}",
                request.getMethod(),
                request.getURI(),
                response.getStatusCode().value(),
                latencyMs,
                LogSanitizer.sanitizeHeaders(request.getHeaders()),
                LogSanitizer.sanitizeBody(new String(body, StandardCharsets.UTF_8)),
                LogSanitizer.sanitizeBody(new String(responseBody, StandardCharsets.UTF_8)));
        return new CachedClientHttpResponse(response, responseBody);
    }

    /**
     * 响应包装器，用于在拦截器读取日志后重新播放缓存的响应体。
     */
    private record CachedClientHttpResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        /**
         * 将状态码读取委托给原始响应。
         */
        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        /**
         * 将状态文本读取委托给原始响应。
         */
        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        /**
         * 将响应头读取委托给原始响应。
         */
        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        /**
         * 重新播放缓存正文，确保 RestClient 消息转换器仍能读取。
         */
        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        /**
         * 关闭原始响应资源。
         */
        @Override
        public void close() {
            delegate.close();
        }
    }
}
