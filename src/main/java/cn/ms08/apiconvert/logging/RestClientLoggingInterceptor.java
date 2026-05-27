package cn.ms08.apiconvert.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 记录 RestClient 出站流量，但不缓存上游响应体，避免大响应在日志链路中重复占用内存。
 */
public class RestClientLoggingInterceptor implements ClientHttpRequestInterceptor {

    /**
     * 上游 HTTP 调用专用日志器。
     */
    private static final Logger log = LoggerFactory.getLogger(RestClientLoggingInterceptor.class);
    /**
     * 请求体超过该阈值时只记录摘要，避免 base64 内容被额外转换成日志字符串。
     */
    private static final int MAX_LOG_BODY_BYTES = 256 * 1024;

    /**
     * 执行上游请求并记录脱敏后的请求摘要，响应体交给 RestClient 后续流程读取。
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        long startedAt = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long latencyMs = System.currentTimeMillis() - startedAt;
        log.info("上游请求：{} {}、状态：{}、耗时：{}ms、请求头：{}、请求体：{}、请求体长度：{}、响应体：{}",
                request.getMethod(),
                request.getURI(),
                response.getStatusCode().value(),
                latencyMs,
                LogSanitizer.sanitizeHeaders(request.getHeaders()),
                sanitizeRequestBody(body),
                body.length,
                "<not buffered>");
        return response;
    }

    /**
     * 对请求体做有界脱敏，超大正文不再构造成完整日志文本。
     */
    private String sanitizeRequestBody(byte[] body) {
        if (body.length == 0) {
            return "";
        }
        if (body.length > MAX_LOG_BODY_BYTES) {
            return "<large body omitted, original=" + body.length + " bytes>";
        }
        return LogSanitizer.sanitizeBody(new String(body, StandardCharsets.UTF_8));
    }
}
