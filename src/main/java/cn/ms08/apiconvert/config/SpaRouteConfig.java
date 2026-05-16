package cn.ms08.apiconvert.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA 路由和静态资源配置。
 * <p>
 * 管理端前端使用 hash 路由模式，服务端只需将根路径回退到
 * index.html。前端构建产物（JS/CSS）配置长期缓存。
 * </p>
 */
@Configuration
public class SpaRouteConfig implements WebMvcConfigurer {

    /**
     * 注册 SPA 根路由视图控制器，前端使用 hash 模式，
     * 服务端无需处理具体路由路径。
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /**
     * 配置前端构建产物长期缓存（文件包含内容 hash，无需缓存失效）。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(31536000);
    }
}
