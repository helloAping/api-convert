package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.dto.admin.DashboardStatsParam;
import cn.ms08.apiconvert.service.admin.AdminDashboardService;
import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.admin.DashboardStatsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端控制台仪表盘接口，提供请求量和 token 用量聚合统计。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * 注入仪表盘统计服务。
     */
    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * 获取按天、按小时和模型/渠道/密钥维度拆分的 token 统计。
     */
    @GetMapping("/stats")
    public ApiResponse<DashboardStatsVO> stats(DashboardStatsParam param) {
        return ApiResponse.success(adminDashboardService.stats(param));
    }
}
