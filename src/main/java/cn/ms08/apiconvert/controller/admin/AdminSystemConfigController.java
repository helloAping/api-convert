package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.dto.admin.RoutingConfigRequest;
import cn.ms08.apiconvert.service.SystemConfigService;
import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.admin.RoutingConfigVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端系统配置接口，当前提供路由策略和错误切换参数配置。
 */
@RestController
@RequestMapping("/api/admin/system-config")
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 注入系统配置服务，保证管理端写入后运行期配置立即失效并重新加载。
     */
    public AdminSystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 读取当前路由策略配置。
     */
    @GetMapping("/routing")
    public ApiResponse<RoutingConfigVO> getRoutingConfig() {
        return ApiResponse.success(systemConfigService.getRoutingConfig());
    }

    /**
     * 更新当前路由策略配置。
     */
    @PutMapping("/routing")
    public ApiResponse<RoutingConfigVO> updateRoutingConfig(@RequestBody RoutingConfigRequest request) {
        return ApiResponse.success(systemConfigService.updateRoutingConfig(request));
    }
}
