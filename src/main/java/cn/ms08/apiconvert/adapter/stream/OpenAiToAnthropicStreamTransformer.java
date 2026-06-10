package cn.ms08.apiconvert.adapter.stream;

import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 将上游 OpenAI Chat Completions SSE 流实时转换为 Anthropic Messages SSE 格式。
 * <p>
 * 用于 {@code ANTHROPIC_MESSAGES → OPENAI_COMPATIBLE} 跨协议流式路由场景：
 * 客户端发送 Anthropic Messages 请求，上游为 OpenAI 兼容渠道，返回 OpenAI Chat SSE 格式，
 * 此转换器将其逐 chunk 转为客户端期望的 Anthropic Messages SSE 格式。
 * </p>
 *
 * <h3>输入格式（OpenAI Chat SSE）</h3>
 * <pre>
 * data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":123,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}
 * data: {"choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
 * data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
 * data: [DONE]
 * </pre>
 *
 * <h3>输出格式（Anthropic Messages SSE）</h3>
 * <pre>
 * event: message_start
 * data: {"type":"message_start","message":{"id":"msg_xxx","type":"message","role":"assistant","content":[],"model":"gpt-4o","stop_reason":null,"usage":{"input_tokens":0,"output_tokens":0}}}
 *
 * event: content_block_start
 * data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}
 *
 * event: content_block_delta
 * data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
 *
 * event: content_block_stop
 * data: {"type":"content_block_stop","index":0}
 *
 * event: message_delta
 * data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}
 *
 * event: message_stop
 * data: {"type":"message_stop"}
 * </pre>
 */
@Component
public class OpenAiToAnthropicStreamTransformer extends OutputStream implements StreamResponseTransformer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OpenAiToAnthropicStreamTransformer.class);

    private final OutputStream target;
    private final String msgId;
    private final long createdAt;

    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);
    private final StringBuilder fullText = new StringBuilder();
    private final StringBuilder reasoningText = new StringBuilder();
    private final Map<Integer, ToolCallState> toolCalls = new LinkedHashMap<>();

    private String model;

    private boolean messageStartSent;
    private boolean textBlockStarted;
    private int textBlockIndex = -1;
    private int nextBlockIndex;
    private boolean completed;
    private boolean completedEventSent;
    private boolean reasoningBlockOpen;
    private int reasoningBlockIndex = -1;

    private Integer inputTokens;
    private Integer outputTokens;
    private String finishReason;

    private static class ToolCallState {
        final int openAiIndex;
        final int anthropicBlockIndex;
        final String callId;
        String name = "";
        final StringBuilder arguments = new StringBuilder();
        boolean blockStarted;

        ToolCallState(int openAiIndex, int anthropicBlockIndex, String callId) {
            this.openAiIndex = openAiIndex;
            this.anthropicBlockIndex = anthropicBlockIndex;
            this.callId = callId;
        }
    }

    /**
     * @param target     真实的 servlet response OutputStream
     * @param responseId 响应 ID（如 msg_xxx）
     * @param model      模型名
     * @param createdAt  创建时间戳（秒）
     */
    public OpenAiToAnthropicStreamTransformer(OutputStream target, String responseId, String model, long createdAt) {
        this.target = target;
        this.msgId = responseId != null ? responseId : "msg_" + UUID.randomUUID().toString().replace("-", "");
        this.createdAt = createdAt;
        this.model = model;
    }

    public OpenAiToAnthropicStreamTransformer() {
        this(OutputStream.nullOutputStream(), null, null, 0);
    }

    @Override
    public boolean supports(EndpointType endpoint, ProviderType provider) {
        return endpoint == EndpointType.ANTHROPIC_MESSAGES
                && (provider == ProviderType.OPENAI_COMPATIBLE
                || provider == ProviderType.GPT_AUTH
                || provider == ProviderType.DEEPSEEK_CHAT);
    }

    @Override
    public WrappedStream wrap(OutputStream target, String responseId, String model, long createdAt) {
        return new OpenAiToAnthropicWrappedStream(
                new OpenAiToAnthropicStreamTransformer(target, responseId, model, createdAt));
    }

    private static class OpenAiToAnthropicWrappedStream implements WrappedStream {

        private final OpenAiToAnthropicStreamTransformer transformer;

        OpenAiToAnthropicWrappedStream(OpenAiToAnthropicStreamTransformer transformer) {
            this.transformer = transformer;
        }

        @Override
        public OutputStream outputStream() {
            return transformer;
        }

        @Override
        public void sendInitialEvents() {
            // Anthropic 格式延迟到首个 delta 时才发送 message_start
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

    @Override
    public void write(int b) throws IOException {
        if (completed) {
            return;
        }
        if (b == '\n') {
            String line = lineBuffer.toString(StandardCharsets.UTF_8);
            lineBuffer.reset();
            processLine(line);
        } else {
            lineBuffer.write(b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (completed) {
            return;
        }
        int lineStart = off;
        for (int i = off; i < off + len; i++) {
            if (b[i] == '\n') {
                if (lineStart < i) {
                    lineBuffer.write(b, lineStart, i - lineStart);
                }
                String line = lineBuffer.toString(StandardCharsets.UTF_8);
                lineBuffer.reset();
                processLine(line);
                lineStart = i + 1;
            }
        }
        if (lineStart < off + len) {
            lineBuffer.write(b, lineStart, off + len - lineStart);
        }
    }

    @Override
    public void flush() throws IOException {
        if (!completedEventSent && completed) {
            sendCompletedEvents();
        }
        target.flush();
    }

    /**
     * 强制完成响应：补发 content_block_stop + message_delta + message_stop。
     */
    public void complete() throws IOException {
        if (!completedEventSent) {
            if (!completed) {
                completed = true;
            }
            sendCompletedEvents();
        }
        target.flush();
    }

    public boolean isCompletedEventSent() {
        return completedEventSent;
    }

    public void writeErrorWithMessage(String message) throws IOException {
        if (completedEventSent) {
            return;
        }
        writeAnthropicEvent("error",
                "{\"type\":\"error\",\"error\":{\"type\":\"error\",\"message\":\"%s\"}}"
                        .formatted(esc(message)));
        completedEventSent = true;
        completed = true;
    }

    private void processLine(String line) throws IOException {
        if (line.isEmpty()) {
            return;
        }
        // OpenAI Chat SSE 不使用 event: 行，只有 data: 行
        if (line.startsWith("data: ")) {
            processData(line.substring(6).stripLeading());
        } else if (line.startsWith("data:")) {
            processData(line.substring(5).stripLeading());
        }
    }

    private void processData(String data) throws IOException {
        if (completed) {
            return;
        }

        if ("[DONE]".equals(data)) {
            completed = true;
            sendCompletedEvents();
            return;
        }

        try {
            JsonNode chunk = OBJECT_MAPPER.readTree(data);

            JsonNode error = chunk.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                String msg = error.path("message").asText("Unknown upstream error");
                writeErrorWithMessage(msg);
                return;
            }

            String m = chunk.path("model").asText(null);
            if (m != null && !m.isEmpty()) {
                model = m;
            }

            // 提取 usage（在 choices 之前，确保 sendCompletedEvents 能读到用量）
            JsonNode usage = chunk.path("usage");
            if (!usage.isMissingNode() && !usage.isNull()) {
                inputTokens = intOrNull(usage, "prompt_tokens");
                outputTokens = intOrNull(usage, "completion_tokens");
            }

            JsonNode choices = chunk.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                processChoice(choices.get(0));
            }
        } catch (Exception e) {
            log.warn("跳过无法解析的 OpenAI data 行: {}, 错误: {}", data, e.getMessage());
        }
    }

    private void processChoice(JsonNode choice) throws IOException {
        JsonNode delta = choice.path("delta");
        String finishReason = choice.path("finish_reason").asText(null);

        // 处理 delta 内容
        if (!delta.isMissingNode()) {
            String content = delta.path("content").asText(null);
            String reasoningContent = delta.path("reasoning_content").asText(null);
            JsonNode toolCallsNode = delta.path("tool_calls");

            // 推理内容（DeepSeek / o1 / o3）
            if (reasoningContent != null && !reasoningContent.isEmpty()) {
                ensureMessageStartSent();
                if (!reasoningBlockOpen) {
                    reasoningBlockOpen = true;
                    reasoningBlockIndex = nextBlockIndex++;
                    reasoningText.setLength(0);
                    writeAnthropicEvent("content_block_start",
                            "{\"type\":\"content_block_start\",\"index\":%d,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}"
                                    .formatted(reasoningBlockIndex));
                }
                reasoningText.append(reasoningContent);
                writeAnthropicEvent("content_block_delta",
                        "{\"type\":\"content_block_delta\",\"index\":%d,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"%s\"}}"
                                .formatted(reasoningBlockIndex, esc(reasoningContent)));
            }

            // 常规文本内容
            if (content != null && !content.isEmpty()) {
                ensureMessageStartSent();
                if (reasoningBlockOpen) {
                    closeReasoningBlock();
                }
                if (!textBlockStarted) {
                    textBlockStarted = true;
                    textBlockIndex = nextBlockIndex++;
                    writeAnthropicEvent("content_block_start",
                            "{\"type\":\"content_block_start\",\"index\":%d,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}"
                                    .formatted(textBlockIndex));
                }
                fullText.append(content);
                writeAnthropicEvent("content_block_delta",
                        "{\"type\":\"content_block_delta\",\"index\":%d,\"delta\":{\"type\":\"text_delta\",\"text\":\"%s\"}}"
                                .formatted(textBlockIndex, esc(content)));
            }

            // 工具调用
            if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
                ensureMessageStartSent();
                if (reasoningBlockOpen) {
                    closeReasoningBlock();
                }
                for (JsonNode tc : toolCallsNode) {
                    processToolCallDelta(tc);
                }
            }
        }

        // 处理完成原因
        if (finishReason != null && !"null".equals(finishReason) && !finishReason.isEmpty()) {
            completed = true;
            this.finishReason = finishReason;
            sendCompletedEvents();
        }
    }

    private void processToolCallDelta(JsonNode tc) throws IOException {
        int index = tc.path("index").asInt(0);
        ToolCallState state = toolCalls.get(index);
        if (state == null) {
            String callId = tc.path("id").asText("call_" + UUID.randomUUID().toString().replace("-", ""));
            int blockIndex = nextBlockIndex++;
            state = new ToolCallState(index, blockIndex, callId);
            toolCalls.put(index, state);
        }

        JsonNode function = tc.path("function");
        if (!function.isMissingNode()) {
            String name = function.path("name").asText(null);
            if (name != null && !name.isEmpty()) {
                state.name = name;
            }
            String arguments = function.path("arguments").asText(null);
            if (arguments != null && !arguments.isEmpty()) {
                state.arguments.append(arguments);
            }
        }

        // 发送 content_block_start（首次）
        if (!state.blockStarted) {
            state.blockStarted = true;
            writeAnthropicEvent("content_block_start",
                    "{\"type\":\"content_block_start\",\"index\":%d,\"content_block\":{\"type\":\"tool_use\",\"id\":\"%s\",\"name\":\"%s\",\"input\":{}}}"
                            .formatted(state.anthropicBlockIndex, esc(state.callId), esc(state.name)));
        }

        // 发送 input_json_delta
        String args = function != null ? function.path("arguments").asText("") : "";
        if (!args.isEmpty()) {
            writeAnthropicEvent("content_block_delta",
                    "{\"type\":\"content_block_delta\",\"index\":%d,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"%s\"}}"
                            .formatted(state.anthropicBlockIndex, esc(args)));
        }
    }

    private void ensureMessageStartSent() throws IOException {
        if (messageStartSent) {
            return;
        }
        messageStartSent = true;
        writeAnthropicEvent("message_start",
                "{\"type\":\"message_start\",\"message\":{\"id\":\"%s\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"%s\",\"stop_reason\":null,\"usage\":{\"input_tokens\":0,\"output_tokens\":0}}}"
                        .formatted(esc(msgId), esc(model)));
    }

    private void closeReasoningBlock() throws IOException {
        if (!reasoningBlockOpen) {
            return;
        }
        reasoningBlockOpen = false;
        writeAnthropicEvent("content_block_stop",
                "{\"type\":\"content_block_stop\",\"index\":%d}".formatted(reasoningBlockIndex));
    }

    private void sendCompletedEvents() throws IOException {
        if (completedEventSent) {
            return;
        }
        completedEventSent = true;

        if (!messageStartSent) {
            ensureMessageStartSent();
        }

        // 关闭推理块
        if (reasoningBlockOpen) {
            closeReasoningBlock();
        }

        // 关闭文本块
        if (textBlockStarted) {
            writeAnthropicEvent("content_block_stop",
                    "{\"type\":\"content_block_stop\",\"index\":%d}".formatted(textBlockIndex));
        }

        // 关闭工具调用块
        for (ToolCallState state : toolCalls.values()) {
            if (state.blockStarted) {
                writeAnthropicEvent("content_block_stop",
                        "{\"type\":\"content_block_stop\",\"index\":%d}".formatted(state.anthropicBlockIndex));
            }
        }

        // 发送 message_delta (stop_reason + usage)
        String stopReason = mapStopReason(finishReason);
        int outTokens = outputTokens != null ? outputTokens : 0;
        writeAnthropicEvent("message_delta",
                "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"%s\"},\"usage\":{\"output_tokens\":%d}}"
                        .formatted(esc(stopReason), outTokens));

        // 发送 message_stop
        writeAnthropicEvent("message_stop", "{\"type\":\"message_stop\"}");
    }

    private String mapStopReason(String openAiFinishReason) {
        if (openAiFinishReason == null) {
            return "end_turn";
        }
        return switch (openAiFinishReason) {
            case "stop" -> "end_turn";
            case "tool_calls", "function_call" -> "tool_use";
            case "length" -> "max_tokens";
            default -> openAiFinishReason;
        };
    }

    private void writeAnthropicEvent(String eventName, String dataJson) throws IOException {
        String event = "event: " + eventName + "\ndata: " + dataJson + "\n\n";
        target.write(event.getBytes(StandardCharsets.UTF_8));
        target.flush();
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.canConvertToInt()) {
            return value.asInt();
        }
        try {
            return value.isTextual() ? Integer.parseInt(value.asText()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
