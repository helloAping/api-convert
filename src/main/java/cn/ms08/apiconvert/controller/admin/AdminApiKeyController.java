package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.dto.admin.ApiKeyForm;
import cn.ms08.apiconvert.dto.admin.ApiKeyQuotaAddRequest;
import cn.ms08.apiconvert.dto.admin.ApiKeyUpdateForm;
import cn.ms08.apiconvert.service.admin.AdminApiKeyService;
import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.admin.ApiKeyCreationVO;
import cn.ms08.apiconvert.vo.admin.ApiKeyVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api-keys")
public class AdminApiKeyController {

    private final AdminApiKeyService adminApiKeyService;

    public AdminApiKeyController(AdminApiKeyService adminApiKeyService) {
        this.adminApiKeyService = adminApiKeyService;
    }

    @GetMapping
    public ApiResponse<List<ApiKeyVO>> list() {
        return ApiResponse.success(adminApiKeyService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ApiKeyVO> getById(@PathVariable Long id) {
        return ApiResponse.success(adminApiKeyService.getById(id));
    }

    @PostMapping
    public ApiResponse<ApiKeyCreationVO> create(@RequestBody ApiKeyForm form) {
        return ApiResponse.success(adminApiKeyService.create(form));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApiKeyVO> updateStatus(@PathVariable Long id, @RequestBody ApiKeyUpdateForm form) {
        return ApiResponse.success(adminApiKeyService.updateStatus(id, form));
    }

    @PostMapping("/{id}/quota")
    public ApiResponse<ApiKeyVO> addQuota(@PathVariable Long id, @RequestBody ApiKeyQuotaAddRequest request) {
        return ApiResponse.success(adminApiKeyService.addQuota(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminApiKeyService.delete(id);
        return ApiResponse.success();
    }
}
