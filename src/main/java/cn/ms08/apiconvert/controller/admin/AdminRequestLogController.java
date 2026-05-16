package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.dto.admin.RequestLogSearchParam;
import cn.ms08.apiconvert.service.admin.AdminRequestLogService;
import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.PageResult;
import cn.ms08.apiconvert.vo.admin.RequestLogVO;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端请求日志接口，提供对话调用审计记录查询。
 */
@RestController
@RequestMapping("/api/admin/request-logs")
public class AdminRequestLogController {

    /**
     * 请求日志查询服务。
     */
    private final AdminRequestLogService adminRequestLogService;

    /**
     * 注入日志查询服务。
     */
    public AdminRequestLogController(AdminRequestLogService adminRequestLogService) {
        this.adminRequestLogService = adminRequestLogService;
    }

    /**
     * 分页查询请求日志，可按协议、接口类型、渠道、模型、结果和时间过滤。
     */
    @GetMapping
    public ApiResponse<PageResult<RequestLogVO>> search(RequestLogSearchParam param) {
        return ApiResponse.success(adminRequestLogService.search(param));
    }

    /**
     * 查询单条请求日志详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<RequestLogVO> getById(@PathVariable Long id) {
        return ApiResponse.success(adminRequestLogService.getById(id));
    }
}
