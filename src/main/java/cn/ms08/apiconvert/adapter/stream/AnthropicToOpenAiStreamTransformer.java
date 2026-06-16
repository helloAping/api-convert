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
 * 将上游 Anthropic Messages SSE 流实时转换为 OpenAI Chat Completions SSE 格式。
 * <p>
 * 用于 {@code CHAT_COMPLETIONS → ANTHROPIC} 跨协议流式路由场景：
 * 客户端发送 OpenAI Chat 请求，上游为 Anthropic 协议渠道，返回 Anthropic SSE 格式，
 * 此转换器将其逐 chunk 转为客户端期望的 OpenAI Chat Completions SSE 格式。
 * </p>
 *
 * <h3>输入格式（Anthropic SSE）</h3>
 * <pre>
 * event: message_start
 * data: {"type":"message_start","message":{"id":"msg_xxx","model":"claude-3-opus","usage":{"input_tokens":100}}}
 *
 * event: content_block_start
 * data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}
 *
 * event: content_block_delta
 * data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
 *
 * event: message_delta
 * data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":10}}
 *
 * event: message_stop
 * data: {"type":"message_stop"}
 * </pre>
 *
 * <h3>输出格式（OpenAI Chat SSE）</h3>
 * <pre>
 * data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":123,"model":"claude-3-opus","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}
 *
 * data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":123,"model":"claude-3-opus","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
 *
 * data: {"id":"chatcmpl-xxx","object":"chat.completion.chunk","created":123,"model":"claude-3-opus","choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":100,"completion_tokens":10,"total_tokens":110}}
 *
 * data: [DONE]
 * </pre>
 */
@Component
public class AnthropicToOpenAiStreamTransformer extends OutputStream implements StreamResponseTransformer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(AnthropicToOpenAiStreamTransformer.class);

    private final OutputStream target;
    private final String chatcmplId;
    private final long createdAt;

    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);
    private final StringBuilder fullText = new StringBuilder();
    private final StringBuilder reasoningText = new StringBuilder();
    private final Map<Integer, ToolCallState> toolCalls = new LinkedHashMap<>();

    private String currentEventType;
    private String model;
    private String msgId;

    private int currentBlockIndex = -1;
    private String currentBlockType;

    private boolean roleEmitted;
    private boolean completed;
    private boolean completedEventSent;
    private boolean reasoningBlockOpen;

    private Integer inputTokens;
    private Integer outputTokens;

    private static class ToolCallState {
        final int blockIndex;
        final String callId;
        String name = "";
        final StringBuilder arguments = new StringBuilder();
        boolean started;

        ToolCallState(int blockIndex, String callId) {
            this.blockIndex = blockIndex;
            this.callId = callId;
        }
    }

    /**
     * @param target     真实的 servlet response OutputStream
     * @param responseId 响应 ID（如 chatcmpl-xxx）
     * @param model      模型名
     * @param createdAt  创建时间戳（秒）
     */
    public AnthropicToOpenAiStreamTransformer(OutputStream target, String responseId, String model, long createdAt) {
        this.target = target;
        this.chatcmplId = responseId != null ? responseId : "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
        this.createdAt = createdAt;
        this.model = model;
    }

    public AnthropicToOpenAiStreamTransformer() {
        this(OutputStream.nullOutputStream(), null, null, 0);
    }

    @Override
    public boolean supports(EndpointType endpoint, ProviderType provider) {
        return endpoint == EndpointType.CHAT_COMPLETIONS
                && (provider == ProviderType.OPENAI
                || provider == ProviderType.CLAUDE_AUTH
                || provider == ProviderType.ANTHROPIC
                || provider == ProviderType.CUSTOM
                || provider == ProviderType.MIMO_TOKEN_PLAN
                || provider == ProviderType.DEEPSEEK);
    }

    @Override
    public WrappedStream wrap(OutputStream target, String responseId, String model, long createdAt) {
        return new AnthropicToOpenAiWrappedStream(
                new AnthropicToOpenAiStreamTransformer(target, responseId, model, createdAt));
    }

    private static class AnthropicToOpenAiWrappedStream implements WrappedStream {

        private final AnthropicToOpenAiStreamTransformer transformer;

        AnthropicToOpenAiWrappedStream(AnthropicToOpenAiStreamTransformer transformer) {
            this.transformer = transformer;
        }

        @Override
        public OutputStream outputStream() {
            return transformer;
        }

        @Override
        public void sendInitialEvents() {
            // OpenAI Chat 格式无需前置事件
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
            sendCompletedAndDone();
        }
        target.flush();
    }

    /**
     * 强制完成响应：补发 finish_reason + usage + [DONE]。
     */
    public void complete() throws IOException {
        if (!completedEventSent) {
            if (!completed) {
                completed = true;
            }
            sendCompletedAndDone();
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
        writeOpenAiData("{\"error\":{\"message\":\"%s\",\"type\":\"error\"}}".formatted(esc(message)));
        writeOpenAiData("[DONE]");
        completedEventSent = true;
        completed = true;
    }

    private void processLine(String line) throws IOException {
        if (line.isEmpty()) {
            return;
        }
        if (line.startsWith("event: ")) {
            currentEventType = line.substring(7).trim();
            return;
        }
        if (line.startsWith("event:")) {
            currentEventType = line.substring(6).trim();
            return;
        }
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

            String type = chunk.path("type").asText(currentEventType != null ? currentEventType : "");
            if (type.isEmpty()) {
                return;
            }

            switch (type) {
                case "message_start" -> processMessageStart(chunk);
                case "content_block_start" -> processContentBlockStart(chunk);
                case "content_block_delta" -> processContentBlockDelta(chunk);
                case "content_block_stop" -> processContentBlockStop();
                case "message_delta" -> processMessageDelta(chunk);
                case "message_stop" -> {
                    completed = true;
                    sendCompletedAndDone();
                }
                case "ping" -> { /* heartbeat, ignore */ }
                default -> log.trace("忽略未知 Anthropic 事件类型: {}", type);
            }
        } catch (Exception e) {
            log.warn("跳过无法解析的 Anthropic data 行: {}, 错误: {}", data, e.getMessage());
        } finally {
            currentEventType = null;
        }
    }

    private void processMessageStart(JsonNode chunk) {
        JsonNode message = chunk.path("message");
        if (!message.isMissingNode()) {
            msgId = message.path("id").asText(null);
            String m = message.path("model").asText(null);
            if (m != null && !m.isEmpty()) {
                model = m;
            }
            JsonNode usage = message.path("usage");
            if (!usage.isMissingNode()) {
                inputTokens = intOrNull(usage, "input_tokens");
            }
        }
    }

    private void processContentBlockStart(JsonNode chunk) throws IOException {
        JsonNode contentBlock = chunk.path("content_block");
        currentBlockType = contentBlock.path("type").asText("");
        currentBlockIndex = chunk.path("index").asInt(0);

        switch (currentBlockType) {
            case "text" -> {
                // text block: 首个 delta 到达时再发 role
            }
            case "thinking", "reasoning" -> {
                if (!reasoningBlockOpen) {
                    reasoningBlockOpen = true;
                    reasoningText.setLength(0);
                    String initial = contentBlock.path("thinking").asText("");
                    if (!initial.isEmpty()) {
                        reasoningText.append(initial);
                        emitReasoningDelta(initial);
                    }
                }
            }
            case "tool_use" -> {
                String toolUseId = contentBlock.path("id").asText("");
                String toolName = contentBlock.path("name").asText("");
                ToolCallState state = toolCalls.computeIfAbsent(currentBlockIndex,
                        key -> new ToolCallState(key, toolUseId.isEmpty()
                                ? "call_" + UUID.randomUUID().toString().replace("-", "") : toolUseId));
                state.name = toolName;
                JsonNode input = contentBlock.path("input");
                if (!input.isMissingNode() && !input.isNull()) {
                    String inputStr = input.isTextual() ? input.asText() : input.toString();
                    if (!inputStr.isEmpty() && !"{}".equals(inputStr)) {
                        state.arguments.append(inputStr);
                    }
                }
                emitToolCallStart(state);
            }
        }
    }

    private void processContentBlockDelta(JsonNode chunk) throws IOException {
        JsonNode delta = chunk.path("delta");
        String deltaType = delta.path("type").asText("");
        int blockIndex = chunk.path("index").asInt(0);

        switch (deltaType) {
            case "text_delta", "text" -> {
                String text = delta.path("text").asText(null);
                if (text != null && !text.isEmpty()) {
                    if (reasoningBlockOpen) {
                        reasoningBlockOpen = false;
                    }
                    ensureRoleEmitted();
                    fullText.append(text);
                    writeChunk("{\"content\":\"%s\"}".formatted(esc(text)), null, null);
                }
            }
            case "thinking_delta" -> {
                String thinking = delta.path("thinking").asText(null);
                if (thinking != null && !thinking.isEmpty()) {
                    reasoningText.append(thinking);
                    emitReasoningDelta(thinking);
                }
            }
            case "input_json_delta" -> {
                String partialJson = delta.path("partial_json").asText("");
                if (!partialJson.isEmpty()) {
                    ToolCallState state = toolCalls.get(blockIndex);
                    if (state == null) {
                        state = new ToolCallState(blockIndex,
                                "call_" + UUID.randomUUID().toString().replace("-", ""));
                        toolCalls.put(blockIndex, state);
                        emitToolCallStart(state);
                    } else if (!state.started) {
                        emitToolCallStart(state);
                    }
                    state.arguments.append(partialJson);
                    writeChunk("{\"tool_calls\":[{\"index\":%d,\"function\":{\"arguments\":\"%s\"}}]}"
                            .formatted(blockIndex, esc(partialJson)), null, null);
                }
            }
        }
    }

    private void processContentBlockStop() {
        currentBlockIndex = -1;
        currentBlockType = null;
    }

    private void processMessageDelta(JsonNode chunk) throws IOException {
        JsonNode delta = chunk.path("delta");
        String stopReason = delta.path("stop_reason").asText(null);
        if (stopReason != null && !"null".equals(stopReason) && !stopReason.isEmpty()) {
            if (reasoningBlockOpen) {
                reasoningBlockOpen = false;
            }
            String finishReason = mapFinishReason(stopReason);
            writeChunk("{}", finishReason, null);
        }
        JsonNode usage = chunk.path("usage");
        if (!usage.isMissingNode()) {
            outputTokens = intOrNull(usage, "output_tokens");
            if (inputTokens == null) {
                inputTokens = intOrNull(usage, "input_tokens");
            }
        }
    }

    private void ensureRoleEmitted() throws IOException {
        if (!roleEmitted) {
            roleEmitted = true;
            writeChunk("{\"role\":\"assistant\"}", null, null);
        }
    }

    private void emitToolCallStart(ToolCallState state) throws IOException {
        if (state.started) {
            return;
        }
        state.started = true;
        if (reasoningBlockOpen) {
            reasoningBlockOpen = false;
        }
        writeChunk("{\"tool_calls\":[{\"index\":%d,\"id\":\"%s\",\"type\":\"function\",\"function\":{\"name\":\"%s\",\"arguments\":\"\"}}]}"
                .formatted(state.blockIndex, esc(state.callId), esc(state.name)), null, null);
    }

    private void emitReasoningDelta(String delta) throws IOException {
        writeChunk("{\"reasoning_content\":\"%s\"}".formatted(esc(delta)), null, null);
    }

    private void sendCompletedAndDone() throws IOException {
        if (completedEventSent) {
            return;
        }
        completedEventSent = true;

        if (reasoningBlockOpen) {
            reasoningBlockOpen = false;
        }

        if (!roleEmitted && fullText.isEmpty() && toolCalls.isEmpty()) {
            writeChunk("{\"role\":\"assistant\"}", null, null);
        }

        boolean hasToolCalls = toolCalls.values().stream().anyMatch(s -> s.started);
        String finishReason = hasToolCalls ? "tool_calls" : "stop";

        String usageJson = null;
        if (inputTokens != null || outputTokens != null) {
            int in = inputTokens != null ? inputTokens : 0;
            int out = outputTokens != null ? outputTokens : 0;
            usageJson = "{\"prompt_tokens\":%d,\"completion_tokens\":%d,\"total_tokens\":%d}".formatted(in, out, in + out);
        }

        writeChunk("{}", finishReason, usageJson);
        writeOpenAiData("[DONE]");
    }

    /**
     * 写出一条 OpenAI Chat Completions SSE chunk。
     *
     * @param deltaJson   delta 对象内的字段（如 {"content":"hello"} 或 {"role":"assistant"}）
     * @param finishReason finish_reason 值，null 表示流尚未结束
     * @param usageJson   顶层 usage 对象 JSON，null 表示不包含 usage
     */
    private void writeChunk(String deltaJson, String finishReason, String usageJson) throws IOException {
        String finishPart = finishReason != null ? "\"finish_reason\":\"%s\"".formatted(esc(finishReason)) : "\"finish_reason\":null";
        String usagePart = usageJson != null ? ",\"usage\":%s".formatted(usageJson) : "";
        String chunk = "{\"id\":\"%s\",\"object\":\"chat.completion.chunk\",\"created\":%d,\"model\":\"%s\",\"choices\":[{\"index\":0,\"delta\":%s,%s}]%s}"
                .formatted(esc(chatcmplId), createdAt, esc(model), deltaJson, finishPart, usagePart);
        writeOpenAiData(chunk);
    }

    private void writeOpenAiData(String data) throws IOException {
        target.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        target.flush();
    }

    private static String mapFinishReason(String anthropicStopReason) {
        if (anthropicStopReason == null) {
            return "stop";
        }
        return switch (anthropicStopReason) {
            case "end_turn" -> "stop";
            case "tool_use" -> "tool_calls";
            case "max_tokens" -> "length";
            default -> anthropicStopReason;
        };
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
