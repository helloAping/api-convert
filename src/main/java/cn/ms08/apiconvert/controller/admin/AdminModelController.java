package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.dto.admin.ModelCapabilitiesForm;
import cn.ms08.apiconvert.dto.admin.ModelEnabledForm;
import cn.ms08.apiconvert.dto.admin.ModelQuotaForm;
import cn.ms08.apiconvert.service.admin.AdminModelService;
import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.admin.ModelVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端模型接口，仅用于聚合展示各渠道已经保存的模型。
 */
@RestController
@RequestMapping("/api/admin/models")
public class AdminModelController {

    /**
     * 负责按对外模型名汇总渠道模型映射。
     */
    private final AdminModelService adminModelService;

    /**
     * 注入管理端模型服务。
     */
    public AdminModelController(AdminModelService adminModelService) {
        this.adminModelService = adminModelService;
    }

    /**
     * 查询按对外模型名去重后的模型列表。
     */
    @GetMapping
    public ApiResponse<List<ModelVO>> list() {
        return ApiResponse.success(adminModelService.list());
    }

    /**
     * 按模型映射记录 ID 查询单条记录，供前端查看聚合来源时使用。
     */
    @GetMapping("/{id}")
    public ApiResponse<ModelVO> getById(@PathVariable Long id) {
        return ApiResponse.success(adminModelService.getById(id));
    }

    /**
     * 更新模型额度单价；同一对外模型名下的渠道映射会同步更新，避免随机路由扣费不一致。
     */
    @PutMapping("/{id}/quota")
    public ApiResponse<ModelVO> updateQuota(@PathVariable Long id, @RequestBody ModelQuotaForm form) {
        return ApiResponse.success(adminModelService.updateQuota(id, form));
    }

    /**
     * 启用或关闭聚合模型；同一对外模型名下的渠道映射会同步更新。
     */
    @PutMapping("/{id}/enabled")
    public ApiResponse<ModelVO> updateEnabled(@PathVariable Long id, @RequestBody ModelEnabledForm form) {
        return ApiResponse.success(adminModelService.updateEnabled(id, form));
    }

    /**
     * 更新模型能力配置；同一对外模型名下的渠道映射会同步更新，确保随机路由时能力表现一致。
     */
    @PutMapping("/{id}/capabilities")
    public ApiResponse<ModelVO> updateCapabilities(@PathVariable Long id, @RequestBody ModelCapabilitiesForm form) {
        return ApiResponse.success(adminModelService.updateCapabilities(id, form));
    }
}
