package cn.ms08.apiconvert.adapter.stream;

import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Responses API 流式响应转换器，将上游 SSE 流实时转换为 Responses API SSE 格式。
 * <p>
 * 支持以下 (端点, 供应商) 组合的上游 SSE 实时转换：
 * </p>
 * <ul>
 *   <li>{@code OPENAI_RESPONSES → OPENAI_COMPATIBLE} — 上游 Chat Completions SSE → Responses API SSE</li>
 *   <li>{@code OPENAI_RESPONSES → ANTHROPIC} — 上游 Anthropic SSE → Responses API SSE</li>
 * </ul>
 * <p>
 * {@link RealTimeResponsesTransformer} 同时支持 OpenAI 和 Anthropic 两种 SSE 输入格式，
 * 通过 event: 行或 type 字段自动检测。此工厂将其注册到流式转换器注册表。
 * </p>
 *
 * <p>
 * 参考 CLIProxyAPI 的 ResponseStreamTransform 设计模式，将流式字节流实时转换为目标协议。
 * </p>
 */
@Component
public class ResponsesStreamTransformerFactory implements StreamResponseTransformer {

    private static final EndpointType SOURCE = EndpointType.OPENAI_RESPONSES;

    @Override
    public boolean supports(EndpointType endpoint, ProviderType provider) {
        return endpoint == SOURCE
                && (provider == ProviderType.OPENAI_COMPATIBLE
                || provider == ProviderType.ANTHROPIC
                || provider == ProviderType.DEEPSEEK_CHAT
                || provider == ProviderType.DEEPSEEK_ANTHROPIC);
    }

    @Override
    public WrappedStream wrap(OutputStream target, String responseId, String model, long createdAt) {
        RealTimeResponsesTransformer transformer = new RealTimeResponsesTransformer(
                target, responseId, model, createdAt);
        return new RealTimeWrappedStream(transformer);
    }

    /**
     * 适配 {@link RealTimeResponsesTransformer} 到 {@link WrappedStream} 接口。
     */
    private static class RealTimeWrappedStream implements WrappedStream {

        private final RealTimeResponsesTransformer transformer;

        RealTimeWrappedStream(RealTimeResponsesTransformer transformer) {
            this.transformer = transformer;
        }

        @Override
        public OutputStream outputStream() {
            return transformer;
        }

        @Override
        public void sendInitialEvents() throws IOException {
            transformer.sendInitialEvents();
        }

        @Override
        public void complete() throws IOException {
            transformer.complete();
        }

        @Override
        public void writeErrorEvent(String message) throws IOException {
            if (!transformer.isCompletedEventSent()) {
                transformer.writeErrorWithMessage(message);
            }
        }
    }
}
