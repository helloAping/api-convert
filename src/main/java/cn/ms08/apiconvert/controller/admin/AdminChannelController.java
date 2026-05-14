package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.dto.admin.ChannelForm;
import cn.ms08.apiconvert.dto.admin.ChannelModelFetchRequest;
import cn.ms08.apiconvert.service.admin.AdminChannelService;
import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.admin.ChannelQuotaVO;
import cn.ms08.apiconvert.vo.admin.ChannelVO;
import cn.ms08.apiconvert.vo.admin.UpstreamModelVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端渠道接口，用一个逻辑渠道替代分散暴露供应商、端点和凭证表。
 */
@RestController
@RequestMapping("/admin/channels")
public class AdminChannelController {

    /**
     * 协调渠道持久化和供应商特定的模型发现逻辑。
     */
    private final AdminChannelService adminChannelService;

    /**
     * 注入渠道聚合服务，供本控制器的所有接口复用。
     */
    public AdminChannelController(AdminChannelService adminChannelService) {
        this.adminChannelService = adminChannelService;
    }

    /**
     * 查询所有已配置渠道，凭证信息只返回脱敏结果。
     */
    @GetMapping
    public ApiResponse<List<ChannelVO>> list() {
        return ApiResponse.success(adminChannelService.list());
    }

    /**
     * 按供应商行 ID 查询单个渠道。
     */
    @GetMapping("/{id}")
    public ApiResponse<ChannelVO> getById(@PathVariable Long id) {
        return ApiResponse.success(adminChannelService.getById(id));
    }

    /**
     * 一次性创建渠道及其关联的端点、凭证和模型映射记录。
     */
    @PostMapping
    public ApiResponse<ChannelVO> create(@RequestBody ChannelForm form) {
        return ApiResponse.success(adminChannelService.create(form));
    }

    /**
     * 使用尚未保存的渠道表单值获取供应商模型选项。
     */
    @PostMapping("/models")
    public ApiResponse<List<UpstreamModelVO>> fetchModels(@RequestBody ChannelModelFetchRequest request) {
        return ApiResponse.success(adminChannelService.fetchModels(request));
    }

    /**
     * 实时获取当前渠道在上游供应商的额度；不保存查询结果。
     */
    @GetMapping("/{id}/quota")
    public ApiResponse<ChannelQuotaVO> fetchQuota(@PathVariable Long id) {
        return ApiResponse.success(adminChannelService.fetchQuota(id));
    }

    /**
     * 更新渠道配置，同时保持渠道编码不可变。
     */
    @PutMapping("/{id}")
    public ApiResponse<ChannelVO> update(@PathVariable Long id, @RequestBody ChannelForm form) {
        return ApiResponse.success(adminChannelService.update(id, form));
    }

    /**
     * 删除渠道及其依赖的端点、凭证和模型映射记录。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminChannelService.delete(id);
        return ApiResponse.success();
    }
}
