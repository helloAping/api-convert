package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.adapter.protocol.OpenAiRequestAdapter;
import cn.ms08.apiconvert.adapter.protocol.OpenAiResponseAdapter;
import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.service.auth.AuthFileService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;

/**
 * GPT_AUTH 渠道使用 auth.json 中的 Bearer token 调用 OpenAI 兼容接口。
 */
@Component
public class GptAuthProviderClient extends OpenAiCompatibleProviderClient {

    private final AuthFileService authFileService;

    public GptAuthProviderClient(RestClient.Builder restClientBuilder, OpenAiRequestAdapter requestAdapter,
                                 OpenAiResponseAdapter responseAdapter, AuthFileService authFileService) {
        super(restClientBuilder, requestAdapter, responseAdapter);
        this.authFileService = authFileService;
    }

    @Override
    public ProviderType type() {
        return ProviderType.GPT_AUTH;
    }

    @Override
    public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        return super.chat(withAccessToken(route), request);
    }

    @Override
    public UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        return super.streamChat(withAccessToken(route), request, outputStream);
    }

    @Override
    public java.util.List<ProviderModel> models(ProviderModelFetchRequest request) {
        return super.models(request);
    }

    @Override
    public ProviderQuota quota(ProviderQuotaFetchRequest request) {
        return new ProviderQuota(false, "GPT_AUTH 暂不支持通用余额查询，请在供应商控制台查看。", null, null, null, "", "");
    }

    private ModelRoute withAccessToken(ModelRoute route) {
        if (!StringUtils.hasText(route.authFilePath())) {
            throw new ProviderException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY, "GPT_AUTH 授权文件未配置");
        }
        String accessToken = authFileService.read(route.authFilePath()).accessToken();
        if (!StringUtils.hasText(accessToken)) {
            throw new ProviderException(ErrorCode.PROVIDER_AUTH_FAILED, HttpStatus.BAD_GATEWAY, "GPT_AUTH 授权文件缺少 access_token");
        }
        return new ModelRoute(route.publicModel(), route.providerCode(), route.providerType(), route.providerModel(),
                route.baseUrl(), route.chatPath(), accessToken, route.authMode(), route.authFilePath(),
                route.inputQuotaPerMillion(), route.outputQuotaPerMillion(), route.cacheReadQuotaPerMillion());
    }
}
