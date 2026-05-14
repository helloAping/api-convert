package cn.ms08.apiconvert;

import cn.ms08.apiconvert.config.GatewayProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@MapperScan("cn.ms08.apiconvert.dao")
@EnableConfigurationProperties(GatewayProperties.class)
@SpringBootApplication
public class ApiConvertApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiConvertApplication.class, args);
    }

}
