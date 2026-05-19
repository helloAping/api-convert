package cn.ms08.apiconvert.controller.admin;

import cn.ms08.apiconvert.dto.admin.ChannelAuthCallbackRequest;
import cn.ms08.apiconvert.service.admin.AdminChannelAuthService;
import cn.ms08.apiconvert.vo.ApiResponse;
import cn.ms08.apiconvert.vo.admin.ChannelAuthStartVO;
import cn.ms08.apiconvert.vo.admin.ChannelAuthStatusVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端渠道授权接口，处理 auth.json 上传和 OAuth code 回调。
 */
@RestController
@RequestMapping("/api/admin/channels")
public class AdminChannelAuthController {

    private final AdminChannelAuthService authService;

    public AdminChannelAuthController(AdminChannelAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/{id}/auth/upload")
    public ApiResponse<ChannelAuthStatusVO> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(authService.upload(id, file));
    }

    @PostMapping("/{id}/auth/start")
    public ApiResponse<ChannelAuthStartVO> start(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(authService.start(id, callbackUrl(request)));
    }

    @GetMapping("/auth/callback")
    public ApiResponse<ChannelAuthStatusVO> callback(@RequestParam String code, @RequestParam String state) {
        return ApiResponse.success(authService.callback(code, state));
    }

    @PostMapping("/{id}/auth/callback-url")
    public ApiResponse<ChannelAuthStatusVO> callbackUrl(@PathVariable Long id,
                                                        @RequestBody ChannelAuthCallbackRequest request) {
        return ApiResponse.success(authService.callbackFromUrl(id, request.callbackUrl()));
    }

    @GetMapping("/{id}/auth/status")
    public ApiResponse<ChannelAuthStatusVO> status(@PathVariable Long id) {
        return ApiResponse.success(authService.status(id));
    }

    @DeleteMapping("/{id}/auth")
    public ApiResponse<ChannelAuthStatusVO> delete(@PathVariable Long id) {
        return ApiResponse.success(authService.delete(id));
    }

    private String callbackUrl(HttpServletRequest request) {
        String scheme = header(request, "X-Forwarded-Proto", request.getScheme());
        String host = header(request, "X-Forwarded-Host", request.getServerName() + ":" + request.getServerPort());
        return scheme + "://" + host + "/api/admin/channels/auth/callback";
    }

    private String header(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
