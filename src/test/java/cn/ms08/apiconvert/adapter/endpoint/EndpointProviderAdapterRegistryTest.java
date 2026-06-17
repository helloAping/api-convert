package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link EndpointProviderAdapterRegistry} 按 (源端点, 目标端点) 维度查找适配器，
 * 适配器与具体供应商类型解耦——任何支持目标端点协议的供应商都可以匹配同一份适配器。
 */
class EndpointProviderAdapterRegistryTest {

    @Test
    void registersAndLooksUpBySourceAndTargetEndpoint() {
        EndpointProviderAdapter chatToAnthropic = stub("chat", EndpointType.CHAT_COMPLETIONS, EndpointType.ANTHROPIC_MESSAGES);
        EndpointProviderAdapter anthropicToChat = stub("anthropic", EndpointType.ANTHROPIC_MESSAGES, EndpointType.CHAT_COMPLETIONS);
        EndpointProviderAdapter responsesToAnthropic = stub("responses", EndpointType.OPENAI_RESPONSES, EndpointType.ANTHROPIC_MESSAGES);
        EndpointProviderAdapter responsesToChat = stub("responses-chat", EndpointType.OPENAI_RESPONSES, EndpointType.CHAT_COMPLETIONS);

        EndpointProviderAdapterRegistry registry =
                new EndpointProviderAdapterRegistry(List.of(chatToAnthropic, anthropicToChat, responsesToAnthropic, responsesToChat));

        // 按 (源, 目标) 查找——核心新逻辑
        assertThat(registry.get(EndpointType.CHAT_COMPLETIONS, EndpointType.ANTHROPIC_MESSAGES))
                .isSameAs(chatToAnthropic);
        assertThat(registry.get(EndpointType.ANTHROPIC_MESSAGES, EndpointType.CHAT_COMPLETIONS))
                .isSameAs(anthropicToChat);
        assertThat(registry.get(EndpointType.OPENAI_RESPONSES, EndpointType.ANTHROPIC_MESSAGES))
                .isSameAs(responsesToAnthropic);
        assertThat(registry.get(EndpointType.OPENAI_RESPONSES, EndpointType.CHAT_COMPLETIONS))
                .isSameAs(responsesToChat);

        // 源 == 目标（同协议）应返回 null，由调用方走 passthrough 分支
        assertThat(registry.get(EndpointType.CHAT_COMPLETIONS, EndpointType.CHAT_COMPLETIONS)).isNull();
        assertThat(registry.get(EndpointType.ANTHROPIC_MESSAGES, EndpointType.ANTHROPIC_MESSAGES)).isNull();
    }

    @Test
    void crossProviderMatchingStillWorksForAdaptersDeclaringTargetProvider() {
        // 模拟旧版本只声明 targetProvider() 的适配器：仍能按 (源端点, 目标供应商) 查到
        EndpointProviderAdapter legacy = stubWithProvider("legacy", EndpointType.CHAT_COMPLETIONS, ProviderType.MIMO_TOKEN_PLAN);
        EndpointProviderAdapterRegistry registry = new EndpointProviderAdapterRegistry(List.of(legacy));

        assertThat(registry.get(EndpointType.CHAT_COMPLETIONS, ProviderType.MIMO_TOKEN_PLAN)).isSameAs(legacy);
    }

    @Test
    void doesNotMatchMismatchedPair() {
        EndpointProviderAdapter chatToAnthropic = stub("chat", EndpointType.CHAT_COMPLETIONS, EndpointType.ANTHROPIC_MESSAGES);
        EndpointProviderAdapterRegistry registry = new EndpointProviderAdapterRegistry(List.of(chatToAnthropic));

        // 错配的源/目标应返回 null
        assertThat(registry.get(EndpointType.CHAT_COMPLETIONS, EndpointType.CHAT_COMPLETIONS)).isNull();
        assertThat(registry.get(EndpointType.ANTHROPIC_MESSAGES, EndpointType.ANTHROPIC_MESSAGES)).isNull();
    }

    private static EndpointProviderAdapter stub(String name, EndpointType source, EndpointType target) {
        return new EndpointProviderAdapter() {
            @Override
            public EndpointType sourceEndpoint() { return source; }

            @Override
            public EndpointType targetEndpoint() { return target; }

            @Override
            public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
                return response;
            }

            @Override
            public String toString() { return "stub:" + name; }
        };
    }

    private static EndpointProviderAdapter stubWithProvider(String name, EndpointType source, ProviderType provider) {
        return new EndpointProviderAdapter() {
            @Override
            public EndpointType sourceEndpoint() { return source; }

            @Override
            public EndpointType targetEndpoint() { return null; }

            @Override
            public ProviderType targetProvider() { return provider; }

            @Override
            public UnifiedChatResponse adaptResponse(UnifiedChatResponse response, String publicModel) {
                return response;
            }

            @Override
            public String toString() { return "legacy-stub:" + name; }
        };
    }
}
