package cn.ms08.apiconvert.config;

import cn.ms08.apiconvert.logging.RestClientLoggingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 网关 HTTP 客户端共享配置。
 */
@Configuration
public class WebConfig {

    /**
     * 提供带出站请求/响应日志的 RestClient 构建器，拦截器会对敏感信息脱敏。
     */
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestInterceptor(new RestClientLoggingInterceptor());
    }
}
