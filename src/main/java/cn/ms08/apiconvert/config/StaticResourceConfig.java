package cn.ms08.apiconvert.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理端前端静态资源处理配置。
 * <p>
 * Vue 构建产物打包在 classpath:/static/ 下，此处显式配置资源处理器，
 * 避免因 Sa-Token 拦截器或其他过滤器误拦截静态资源请求。
 * </p>
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    /**
     * 注册静态资源处理器：
     * <ul>
     *   <li>/assets/** — 前端构建产物的 JS、CSS 文件（文件名含 hash，适合长期缓存）</li>
     *   <li>/favicon.svg、/icons.svg — 站点图标</li>
     *   <li>/** — SPA 入口 index.html 及其他静态文件</li>
     * </ul>
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源（JS/CSS/图片等）
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(31536000); // 365 天，文件名含 hash 无需缓存失效

        // 静态资源（favicon/图标/其他）
        registry.addResourceHandler("/favicon.svg", "/icons.svg")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(86400); // 1 天

        // SPA 入口文件，不缓存
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0);

        // 其他静态资源（根路径）
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
    }
}
