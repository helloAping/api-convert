package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedMessage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link DeepSeekHook} 跨 Chat Completions / Anthropic Messages 两种源端点
 * 的供应商特化逻辑：
 * <ul>
 *   <li>Chat 源：assistant 消息缺 {@code reasoning_content} 时补默认值 ""</li>
 *   <li>Anthropic 源：thinking 块缺 {@code thinking} 字段时用 text 兜底</li>
 * </ul>
 */
class DeepSeekHookTest {

    private final DeepSeekHook hook = new DeepSeekHook();

    @Test
    void chatSource_fillsEmptyReasoningContentOnAssistantMessage() {
        UnifiedMessage assistant = new UnifiedMessage("assistant", "hi", null);
        UnifiedMessage user = new UnifiedMessage("user", "hello", null);
        UnifiedChatRequest request = new UnifiedChatRequest(
                "deepseek-chat", List.of(user, assistant), false,
                null, null, null, null);

        UnifiedChatRequest processed = hook.preProcess(request, EndpointType.CHAT_COMPLETIONS, route());

        UnifiedMessage newAssistant = processed.messages().get(1);
        assertThat(newAssistant.role()).isEqualTo("assistant");
        assertThat(newAssistant.options()).containsEntry("reasoning_content", "");
        // 不可变用户消息保持原样
        assertThat(processed.messages().get(0)).isSameAs(user);
    }

    @Test
    void chatSource_preservesExistingReasoningContent() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("reasoning_content", "already thought about it");
        UnifiedMessage assistant = new UnifiedMessage("assistant", "hi", null, null, options);
        UnifiedChatRequest request = new UnifiedChatRequest(
                "deepseek-chat", List.of(assistant), false,
                null, null, null, null);

        UnifiedChatRequest processed = hook.preProcess(request, EndpointType.CHAT_COMPLETIONS, route());

        assertThat(processed.messages().get(0).options()).containsEntry("reasoning_content", "already thought about it");
    }

    @Test
    void chatSource_doesNotTouchNonAssistantMessages() {
        UnifiedMessage user = new UnifiedMessage("user", "hello", null);
        UnifiedMessage tool = new UnifiedMessage("tool", "result", null, null, Map.of("tool_call_id", "x"));
        UnifiedChatRequest request = new UnifiedChatRequest(
                "deepseek-chat", List.of(user, tool), false,
                null, null, null, null);

        UnifiedChatRequest processed = hook.preProcess(request, EndpointType.CHAT_COMPLETIONS, route());

        assertThat(processed.messages().get(0).options()).isNull();
        assertThat(processed.messages().get(1).options()).containsEntry("tool_call_id", "x");
    }

    @Test
    void anthropicSource_fillsMissingThinkingFieldOnThinkingBlock() {
        UnifiedMessage assistant = new UnifiedMessage("assistant", List.of(
                Map.of("type", "thinking", "text", "long chain of thought"),
                Map.of("type", "text", "text", "the answer")
        ), null);
        UnifiedChatRequest request = new UnifiedChatRequest(
                "deepseek-chat", List.of(assistant), false,
                null, null, null, null);

        UnifiedChatRequest processed = hook.preProcess(request, EndpointType.ANTHROPIC_MESSAGES, route());

        List<?> content = (List<?>) processed.messages().get(0).content();
        Map<?, ?> first = (Map<?, ?>) content.get(0);
        assertThat(first.get("type")).isEqualTo("thinking");
        assertThat(first.get("thinking")).isEqualTo("long chain of thought");
        // 第二个 text 块不应被改动
        Map<?, ?> second = (Map<?, ?>) content.get(1);
        assertThat(second.get("type")).isEqualTo("text");
        assertThat(second.get("text")).isEqualTo("the answer");
    }

    @Test
    void anthropicSource_overwritesBlankThinkingWithText() {
        // 实际行为：DeepSeek hook 检查 thinking 字段，缺失或空白时一律用 text 兜底。
        // 即便上游客户端传了显式空字符串，也会被补为非空值（避免 DeepSeek 400）。
        UnifiedMessage assistant = new UnifiedMessage("assistant", List.of(
                Map.of("type", "thinking", "text", "raw text", "thinking", "")
        ), null);
        UnifiedChatRequest request = new UnifiedChatRequest(
                "deepseek-chat", List.of(assistant), false,
                null, null, null, null);

        UnifiedChatRequest processed = hook.preProcess(request, EndpointType.ANTHROPIC_MESSAGES, route());

        List<?> content = (List<?>) processed.messages().get(0).content();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) content.get(0);
        // 显式空 thinking 会被 text 覆盖（DeepSeek 要求 thinking 字段非空）
        assertThat(first.get("thinking")).isEqualTo("raw text");
    }

    @Test
    void anthropicSource_preservesNonBlankThinking() {
        UnifiedMessage assistant = new UnifiedMessage("assistant", List.of(
                Map.of("type", "thinking", "text", "raw text", "thinking", "this is a real thought")
        ), null);
        UnifiedChatRequest request = new UnifiedChatRequest(
                "deepseek-chat", List.of(assistant), false,
                null, null, null, null);

        UnifiedChatRequest processed = hook.preProcess(request, EndpointType.ANTHROPIC_MESSAGES, route());

        List<?> content = (List<?>) processed.messages().get(0).content();
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) content.get(0);
        // 非空 thinking 不被改动
        assertThat(first.get("thinking")).isEqualTo("this is a real thought");
    }

    @Test
    void anthropicSource_passesThroughNonListContent() {
        // content 是 String（非 Anthropic 列表块）时不应该被处理
        UnifiedMessage user = new UnifiedMessage("user", "hi", null);
        UnifiedChatRequest request = new UnifiedChatRequest(
                "deepseek-chat", List.of(user), false,
                null, null, null, null);

        UnifiedChatRequest processed = hook.preProcess(request, EndpointType.ANTHROPIC_MESSAGES, route());

        assertThat(processed.messages().get(0).content()).isEqualTo("hi");
    }

    @Test
    void otherEndpoint_passesThroughUnchanged() {
        UnifiedMessage assistant = new UnifiedMessage("assistant", "hi", null);
        UnifiedChatRequest request = new UnifiedChatRequest(
                "deepseek-chat", List.of(assistant), false,
                null, null, null, null);

        // OPENAI_RESPONSES 源：DeepSeek 当前不处理
        UnifiedChatRequest processed = hook.preProcess(request, EndpointType.OPENAI_RESPONSES, route());

        assertThat(processed.messages().get(0)).isSameAs(assistant);
    }

    private static ModelRoute route() {
        return new ModelRoute(
                "deepseek-chat", "channel-code",
                cn.ms08.apiconvert.provider.ProviderType.DEEPSEEK,
                "deepseek-chat",
                "https://api.deepseek.com", "/v1/chat/completions",
                null, null, "sk-test", null, null,
                null, null, null,
                null, null);
    }
}
