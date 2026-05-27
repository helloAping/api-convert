package cn.ms08.apiconvert.config;

import cn.ms08.apiconvert.logging.RestClientLoggingInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Shared gateway HTTP client configuration.
 */
@Configuration
public class WebConfig {

    /**
     * Reuse the global JSON mapper so RestClient accepts large base64 payloads.
     */
    @Bean
    RestClient.Builder restClientBuilder(ObjectMapper objectMapper) {
        return RestClient.builder()
                .configureMessageConverters(converters -> converters
                        .withJsonConverter(new FasterxmlJsonHttpMessageConverter(objectMapper)))
                .requestInterceptor(new RestClientLoggingInterceptor());
    }
}
