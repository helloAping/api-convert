package cn.ms08.apiconvert.dto;

import cn.ms08.apiconvert.dto.admin.ChannelCapability;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link ModelRoute#effectiveEndpoint(EndpointType)} 的回退顺序：
 * <ol>
 *   <li>客户端端点直接落在 allowedCapabilities 内 → 使用客户端端点</li>
 *   <li>渠道 capabilities 与 allowedCapabilities 有交集 → 取渠道侧配置顺序的第一个能力</li>
 *   <li>渠道 capabilities 为空 / 不相交 → 退回 allowedCapabilities 中用户填写的第一个能力</li>
 * </ol>
 * 关键场景：用户在"能力配置"中标记模型只允许 Anthropic Messages，
 * 外部用 chat 请求时上游必须走 Anthropic Messages（触发 Chat↔Anthropic 适配器），
 * 而不是直接用 chat 协议打给上游。
 */
class ModelRouteEffectiveEndpointTests {

    @Test
    void blankAllowedCapabilitiesKeepsClientEndpoint() {
        ModelRoute route = route(null, null);

        assertThat(route.effectiveEndpoint(EndpointType.CHAT_COMPLETIONS))
                .isEqualTo(EndpointType.CHAT_COMPLETIONS);
        assertThat(route.effectiveEndpoint(EndpointType.ANTHROPIC_MESSAGES))
                .isEqualTo(EndpointType.ANTHROPIC_MESSAGES);
    }

    @Test
    void clientEndpointInsideAllowedCapabilitiesIsPreserved() {
        ModelRoute route = route(
                List.of(capability(EndpointType.CHAT_COMPLETIONS, "/v1/chat/completions"),
                        capability(EndpointType.ANTHROPIC_MESSAGES, "/v1/messages")),
                "ANTHROPIC_MESSAGES,CHAT_COMPLETIONS");

        assertThat(route.effectiveEndpoint(EndpointType.CHAT_COMPLETIONS))
                .isEqualTo(EndpointType.CHAT_COMPLETIONS);
        assertThat(route.effectiveEndpoint(EndpointType.ANTHROPIC_MESSAGES))
                .isEqualTo(EndpointType.ANTHROPIC_MESSAGES);
    }

    @Test
    void channelCapabilitiesOrderWinsWhenClientEndpointMissing() {
        ModelRoute route = route(
                List.of(capability(EndpointType.CHAT_COMPLETIONS, "/v1/chat/completions"),
                        capability(EndpointType.ANTHROPIC_MESSAGES, "/v1/messages")),
                "ANTHROPIC_MESSAGES,CHAT_COMPLETIONS");

        assertThat(route.effectiveEndpoint(EndpointType.OPENAI_RESPONSES))
                .isEqualTo(EndpointType.CHAT_COMPLETIONS);
    }

    @Test
    void channelCapabilitiesFilteredByAllowedSet() {
        ModelRoute route = route(
                List.of(capability(EndpointType.OPENAI_RESPONSES, "/v1/responses"),
                        capability(EndpointType.CHAT_COMPLETIONS, "/v1/chat/completions"),
                        capability(EndpointType.ANTHROPIC_MESSAGES, "/v1/messages")),
                "ANTHROPIC_MESSAGES,CHAT_COMPLETIONS");

        assertThat(route.effectiveEndpoint(EndpointType.OPENAI_VIDEOS))
                .isEqualTo(EndpointType.CHAT_COMPLETIONS);
    }

    @Test
    void fallsBackToAllowedCapabilitiesFirstWhenChannelCapabilitiesEmpty() {
        ModelRoute route = route(
                List.of(),
                "ANTHROPIC_MESSAGES,CHAT_COMPLETIONS");

        assertThat(route.effectiveEndpoint(EndpointType.OPENAI_VIDEOS))
                .isEqualTo(EndpointType.ANTHROPIC_MESSAGES);
    }

    @Test
    void fallsBackToAllowedCapabilitiesFirstWhenChannelCapabilitiesDisjoint() {
        ModelRoute route = route(
                List.of(capability(EndpointType.OPENAI_RESPONSES, "/v1/responses")),
                "ANTHROPIC_MESSAGES,CHAT_COMPLETIONS");

        assertThat(route.effectiveEndpoint(EndpointType.OPENAI_VIDEOS))
                .isEqualTo(EndpointType.ANTHROPIC_MESSAGES);
    }

    @Test
    void anthropicOnlyAllowedCapabilitiesForcesAnthropicUpstreamForChatRequest() {
        ModelRoute route = route(List.of(), "ANTHROPIC_MESSAGES");

        assertThat(route.effectiveEndpoint(EndpointType.CHAT_COMPLETIONS))
                .isEqualTo(EndpointType.ANTHROPIC_MESSAGES);
    }

    @Test
    void nullChannelCapabilitiesWithAnthropicOnlyRestriction() {
        ModelRoute route = route(null, "ANTHROPIC_MESSAGES");

        assertThat(route.effectiveEndpoint(EndpointType.CHAT_COMPLETIONS))
                .isEqualTo(EndpointType.ANTHROPIC_MESSAGES);
    }

    @Test
    void channelCapabilitiesSupportClientEndpointPrefersDirectConnection() {
        ModelRoute route = route(
                List.of(capability(EndpointType.CHAT_COMPLETIONS, "/v1/chat/completions"),
                        capability(EndpointType.ANTHROPIC_MESSAGES, "/v1/messages")),
                "ANTHROPIC_MESSAGES");

        assertThat(route.effectiveEndpoint(EndpointType.CHAT_COMPLETIONS))
                .isEqualTo(EndpointType.CHAT_COMPLETIONS);
    }

    @Test
    void channelCapabilitiesSupportAnthropicForAnthropicRequestPrefersDirectConnection() {
        ModelRoute route = route(
                List.of(capability(EndpointType.CHAT_COMPLETIONS, "/v1/chat/completions"),
                        capability(EndpointType.ANTHROPIC_MESSAGES, "/v1/messages")),
                "ANTHROPIC_MESSAGES");

        assertThat(route.effectiveEndpoint(EndpointType.ANTHROPIC_MESSAGES))
                .isEqualTo(EndpointType.ANTHROPIC_MESSAGES);
    }

    private static ChannelCapability capability(EndpointType type, String path) {
        return new ChannelCapability(type.name(), path);
    }

    private static ModelRoute route(List<ChannelCapability> capabilities, String allowedCapabilities) {
        return new ModelRoute(
                "public-model",
                "channel-code",
                ProviderType.OPENAI,
                "provider-model",
                "https://example.com",
                "/v1/chat/completions",
                null,
                null,
                "sk-test",
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                capabilities,
                allowedCapabilities
        );
    }
}
