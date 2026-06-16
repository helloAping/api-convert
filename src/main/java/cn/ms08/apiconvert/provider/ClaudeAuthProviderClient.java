package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.AnthropicRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.AnthropicResponseAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.service.auth.AuthFileService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class ClaudeAuthProviderClient implements AiProviderClient {

    private final AnthropicMessagesCapability anthropicCapability;
    private final AuthFileService authFileService;

    public ClaudeAuthProviderClient(RestClient.Builder restClientBuilder,
                                    AnthropicRequestAdapter requestAdapter,
                                    AnthropicResponseAdapter responseAdapter,
                                    AuthFileService authFileService) {
        this.anthropicCapability = new AnthropicMessagesCapability(restClientBuilder, requestAdapter, responseAdapter);
        this.authFileService = authFileService;
    }

    @Override
    public ProviderType type() { return ProviderType.CLAUDE_AUTH; }

    @Override
    public Map<EndpointType, EndpointCapability> capabilities() {
        return Map.of(EndpointType.ANTHROPIC_MESSAGES, anthropicCapability);
    }

    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        return List.of();
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "CLAUDE_AUTH 暂不支持通用余额查询，请在供应商控制台查看。",
                null, null, null, "", "");
    }

    public ModelRoute withAccessToken(ModelRoute route) {
        if (!StringUtils.hasText(route.authFilePath())) {
            throw new ProviderException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY,
                    "CLAUDE_AUTH 授权文件未配置");
        }
        String accessToken = authFileService.read(route.authFilePath()).accessToken();
        if (!StringUtils.hasText(accessToken)) {
            throw new ProviderException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY,
                    "CLAUDE_AUTH 授权文件缺少 access_token");
        }
        return new ModelRoute(route.publicModel(), route.providerCode(), route.providerType(),
                route.providerModel(), route.baseUrl(), route.chatPath(), route.videoPath(), route.imagePath(),
                accessToken, route.authMode(), route.authFilePath(),
                route.inputQuotaPerMillion(), route.outputQuotaPerMillion(), route.cacheReadQuotaPerMillion(), route.capabilities());
    }
}
