package cn.ms08.apiconvert.service.admin;

import cn.dev33.satoken.stp.StpUtil;
import cn.ms08.apiconvert.config.GatewayProperties;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.vo.admin.AdminLoginVO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {

    private final GatewayProperties properties;

    public AdminAuthService(GatewayProperties properties) {
        this.properties = properties;
    }

    public AdminLoginVO login(String username, String password) {
        var admin = properties.getSecurity().getAdmin();
        if (!admin.getUsername().equals(username) || !admin.getPassword().equals(password)) {
            throw new GatewayException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        StpUtil.login(username);
        String token = StpUtil.getTokenValue();
        return new AdminLoginVO(token, username);
    }

    public void logout() {
        StpUtil.logout();
    }

    public String currentUser() {
        return StpUtil.getLoginIdAsString();
    }
}
