package cn.ms08.apiconvert.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 管理端前端使用 HTML5 history 路由，生产环境刷新页面时需要统一回退到入口文件。
 */
@Controller
public class SpaForwardController {

    /**
     * 转发已知管理端页面路由，避免影响 /admin、/v1 和 /health 等后端接口。
     */
    @RequestMapping({"/", "/login", "/channels", "/models", "/api-keys", "/request-logs"})
    public String forwardAdminRoute() {
        return "forward:/index.html";
    }
}
