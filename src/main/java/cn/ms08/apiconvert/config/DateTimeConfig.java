package cn.ms08.apiconvert.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;

/**
 * 全局日期时间格式配置，统一接口输入输出和查询参数绑定的 LocalDateTime 格式。
 */
@Configuration
public class DateTimeConfig implements WebMvcConfigurer {

    /**
     * 项目统一日期时间格式；Java 中月份必须使用大写 MM，小时使用 24 小时制 HH。
     */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * LocalDateTime 序列化、反序列化和 MVC 参数绑定共用的格式化器。
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    /**
     * 项目统一时区，默认固定为上海时区。
     */
    private final ZoneId projectZoneId;

    /**
     * 初始化项目时区，并同步设置 JVM 默认时区，避免数据库驱动和第三方库使用系统默认时区。
     */
    public DateTimeConfig(@Value("${api-convert.time-zone:Asia/Shanghai}") String timeZoneId) {
        this.projectZoneId = ZoneId.of(timeZoneId);
        TimeZone.setDefault(TimeZone.getTimeZone(projectZoneId));
    }

    /**
     * 暴露项目统一时区，供持久化自动填充等组件复用。
     */
    @Bean
    public ZoneId projectZoneId() {
        return projectZoneId;
    }

    /**
     * 统一 JSON 中 LocalDateTime 的输入输出格式，避免默认 ISO 格式携带 T 分隔符。
     */
    @Primary
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            /**
             * 输出所有 LocalDateTime 时使用统一格式，避免暴露 ISO T 分隔符。
             */
            @Override
            public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
                generator.writeString(value.format(DATE_TIME_FORMATTER));
            }
        });
        module.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            /**
             * 仅接受统一格式的 LocalDateTime 文本；空文本按空值处理。
             */
            @Override
            public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                String value = parser.getValueAsString();
                if (value == null || value.isBlank()) {
                    return null;
                }
                return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
            }
        });
        objectMapper.registerModule(module);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.setTimeZone(TimeZone.getTimeZone(projectZoneId));
        return objectMapper;
    }

    /**
     * Spring MVC 响应转换器显式使用统一 ObjectMapper，确保所有 JSON 出参日期格式一致。
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setObjectMapper(objectMapper());
            }
        }
    }

    /**
     * 统一 GET 查询参数和表单参数中的 LocalDateTime 绑定格式。
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateTimeFormatter(DATE_TIME_FORMATTER);
        registrar.registerFormatters(registry);
    }
}
