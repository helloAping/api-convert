package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderClientTests {

    @Test
    void generateVideoDefaultsToUnsupportedFeature() {
        AiProviderClient client = new AiProviderClient() {
            @Override public ProviderType type() { return ProviderType.DEEPSEEK; }
            @Override public UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) { return null; }
            @Override public UnifiedChatResponse messages(ModelRoute route, UnifiedChatRequest request) { return null; }
            @Override public UnifiedChatResponse responses(ModelRoute route, UnifiedChatRequest request) { return null; }
            @Override public boolean supportsStreaming(EndpointType endpointType) { return false; }
            @Override public UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream os) { return null; }
            @Override public UnifiedUsage streamMessages(ModelRoute route, UnifiedChatRequest request, OutputStream os) { return null; }
            @Override public UnifiedUsage streamResponses(ModelRoute route, UnifiedChatRequest request, OutputStream os) { return null; }
            @Override public OpenAiVideoResponse generateVideo(ModelRoute route, OpenAiVideoRequest request) { throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, null, ""); }
            @Override public OpenAiImageResponse generateImage(ModelRoute route, OpenAiImageRequest request) { throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, null, ""); }
            @Override public List<ProviderModel> models(ProviderModelFetchRequest request) { return List.of(); }
            @Override public ProviderQuota quota(ProviderQuotaFetchRequest request) { return null; }
        };

        assertThatThrownBy(() -> client.generateVideo(null, new OpenAiVideoRequest()))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ErrorCode.UNSUPPORTED_FEATURE));
        assertThatThrownBy(() -> client.generateImage(null, new OpenAiImageRequest()))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(ErrorCode.UNSUPPORTED_FEATURE));
    }
}
