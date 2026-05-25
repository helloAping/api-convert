package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderClientTests {

    /**
     * 未显式支持视频生成的供应商应保持默认拒绝，避免误把视频请求转进聊天接口。
     */
    @Test
    void generateVideoDefaultsToUnsupportedFeature() {
        AiProviderClient client = new AiProviderClient() {
            @Override
            public ProviderType type() {
                return ProviderType.ANTHROPIC;
            }

            @Override
            public cn.ms08.apiconvert.dto.UnifiedChatResponse chat(cn.ms08.apiconvert.dto.ModelRoute route,
                                                                   cn.ms08.apiconvert.dto.UnifiedChatRequest request) {
                return null;
            }

            @Override
            public java.util.List<cn.ms08.apiconvert.dto.ProviderModel> models(cn.ms08.apiconvert.dto.ProviderModelFetchRequest request) {
                return java.util.List.of();
            }

            @Override
            public cn.ms08.apiconvert.dto.ProviderQuota quota(cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest request) {
                return null;
            }
        };

        assertThatThrownBy(() -> client.generateVideo(null, new OpenAiVideoRequest()))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo(ErrorCode.UNSUPPORTED_FEATURE));
        assertThatThrownBy(() -> client.generateImage(null, new OpenAiImageRequest()))
                .isInstanceOfSatisfying(ProviderException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.code()).isEqualTo(ErrorCode.UNSUPPORTED_FEATURE));
    }
}
