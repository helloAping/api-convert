package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.dto.admin.AdminLoginRequest;
import cn.ms08.apiconvert.service.admin.AdminAuthService;
import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.admin.AdminLoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success(adminAuthService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        adminAuthService.logout();
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, String>> me() {
        return ApiResponse.success(Map.of("username", adminAuthService.currentUser()));
    }
}
