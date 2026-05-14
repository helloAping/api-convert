package cn.ms08.apiconvert.security;

import cn.ms08.apiconvert.config.GatewayProperties;
import cn.ms08.apiconvert.dao.GatewayApiKeyChannelMapper;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.entity.GatewayApiKeyChannelEntity;
import cn.ms08.apiconvert.entity.GatewayApiKeyEntity;
import cn.ms08.apiconvert.dao.GatewayApiKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GatewayApiKeyFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL_ATTRIBUTE = "gatewayPrincipal";

    private static final Set<String> PUBLIC_PATHS = Set.of("/health");
    private static final String ADMIN_PREFIX = "/admin/";
    private static final String GATEWAY_API_PREFIX = "/v1/";

    private final GatewayProperties properties;
    private final GatewayApiKeyMapper apiKeyMapper;
    private final GatewayApiKeyChannelMapper apiKeyChannelMapper;

    public GatewayApiKeyFilter(GatewayProperties properties, GatewayApiKeyMapper apiKeyMapper,
                               GatewayApiKeyChannelMapper apiKeyChannelMapper) {
        this.properties = properties;
        this.apiKeyMapper = apiKeyMapper;
        this.apiKeyChannelMapper = apiKeyChannelMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        if (!properties.getSecurity().isEnabled() || PUBLIC_PATHS.contains(requestUri)
                || requestUri.startsWith(ADMIN_PREFIX) || !requestUri.startsWith(GATEWAY_API_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String apiKey = resolveApiKey(request);
        if (!StringUtils.hasText(apiKey)) {
            throw new GatewayException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Missing gateway API key");
        }
        GatewayApiKeyEntity entity = apiKeyMapper.selectOne(new LambdaQueryWrapper<GatewayApiKeyEntity>()
                .eq(GatewayApiKeyEntity::getApiKeyHash, ApiKeyHasher.hash(apiKey))
                .eq(GatewayApiKeyEntity::getStatus, "ACTIVE"));
        if (entity == null) {
            throw new GatewayException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Invalid gateway API key");
        }
        request.setAttribute(PRINCIPAL_ATTRIBUTE, new GatewayPrincipal(entity.getId(), entity.getName(), allowedChannels(entity.getId())));
        filterChain.doFilter(request, response);
    }

    private String resolveApiKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return request.getHeader("x-api-key");
    }

    /**
     * 查询密钥允许使用的渠道；空集合表示未限制渠道。
     */
    private Set<String> allowedChannels(Long apiKeyId) {
        return apiKeyChannelMapper.selectList(new LambdaQueryWrapper<GatewayApiKeyChannelEntity>()
                        .eq(GatewayApiKeyChannelEntity::getApiKeyId, apiKeyId))
                .stream()
                .map(GatewayApiKeyChannelEntity::getChannelCode)
                .collect(Collectors.toUnmodifiableSet());
    }
}
