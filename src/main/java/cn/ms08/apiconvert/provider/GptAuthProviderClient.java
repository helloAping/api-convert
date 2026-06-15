package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
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
public class GptAuthProviderClient implements AiProviderClient {

    private final OpenAiChatCapability chatCapability;
    private final AuthFileService authFileService;

    public GptAuthProviderClient(RestClient.Builder restClientBuilder,
                                 OpenAiRequestAdapter requestAdapter,
                                 OpenAiResponseAdapter responseAdapter,
                                 AuthFileService authFileService) {
        this.chatCapability = new OpenAiChatCapability(restClientBuilder, requestAdapter, responseAdapter);
        this.authFileService = authFileService;
    }

    @Override
    public ProviderType type() { return ProviderType.GPT_AUTH; }

    @Override
    public Map<EndpointType, EndpointCapability> capabilities() {
        return Map.of(
                EndpointType.CHAT_COMPLETIONS, chatCapability,
                EndpointType.OPENAI_VIDEOS, chatCapability,
                EndpointType.OPENAI_IMAGES, chatCapability
        );
    }

    @Override
    public List<ProviderModel> models(ProviderModelFetchRequest request) {
        return List.of();
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "GPT_AUTH 暂不支持通用余额查询，请在供应商控制台查看。",
                null, null, null, "", "");
    }

    /**
     * 将路由中的 apiKey 替换为 auth.json 中的 access_token。
     */
    public ModelRoute withAccessToken(ModelRoute route) {
        if (!StringUtils.hasText(route.authFilePath())) {
            throw new ProviderException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY,
                    "GPT_AUTH 授权文件未配置");
        }
        String accessToken = authFileService.read(route.authFilePath()).accessToken();
        if (!StringUtils.hasText(accessToken)) {
            throw new ProviderException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY,
                    "GPT_AUTH 授权文件缺少 access_token");
        }
        return new ModelRoute(route.publicModel(), route.providerCode(), route.providerType(),
                route.providerModel(), route.baseUrl(), route.chatPath(), route.videoPath(), route.imagePath(),
                accessToken, route.authMode(), route.authFilePath(),
                route.inputQuotaPerMillion(), route.outputQuotaPerMillion(), route.cacheReadQuotaPerMillion());
    }
}
