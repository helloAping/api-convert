package cn.ms08.apiconvert.config;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * 管理端接口的 Sa-Token 配置，保护除登录外的所有 /admin/* 接口。
 */
@Configuration
public class SaTokenWebConfig {

    /**
     * 将 Sa-Token 调整为前端使用的 Authorization: Bearer <token> 请求头格式。
     */
    @PostConstruct
    public void init() {
        var config = new SaTokenConfig();
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setTimeout(1800);
        config.setActiveTimeout(1800);
        config.setIsConcurrent(true);
        config.setIsShare(false);
        config.setTokenStyle("simple-uuid");
        config.setIsLog(false);
        cn.dev33.satoken.SaManager.setConfig(config);
    }

    /**
     * 注册轻量级 Servlet 过滤器，只在 /admin/* 路径下检查登录状态。
     * 静态资源（/assets/*、/favicon.svg、/index.html 等）不受此过滤器影响。
     */
    @Bean
    public FilterRegistrationBean<Filter> saTokenFilter() {
        var registration = new FilterRegistrationBean<Filter>();
        registration.setFilter(new Filter() {
            /**
             * 检查管理端登录状态，不在日志或响应中暴露 token 值。
             * 静态资源路径已通过 addUrlPatterns("/admin/*") 排除。
             */
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) request;
                HttpServletResponse res = (HttpServletResponse) response;
                String path = req.getRequestURI();
                if (path.startsWith("/api/admin/") && !path.equals("/api/admin/login")) {
                    try {
                        StpUtil.checkLogin();
                    } catch (Exception e) {
                        res.setStatus(401);
                        res.setContentType("application/json");
                        res.getWriter().write("{\"code\":401,\"message\":\"Not logged in\",\"data\":null}");
                        return;
                    }
                }
                chain.doFilter(request, response);
            }
        });
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(1);
        return registration;
    }
}
