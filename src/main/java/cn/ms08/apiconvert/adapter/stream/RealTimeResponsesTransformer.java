package cn.ms08.apiconvert.adapter.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 将上游 Chat Completions SSE 流转为 Responses API SSE 事件的实时转换器。
 * <p>
 * 作为 OutputStream 拦截器工作，直接包装 servlet response 的输出流。
 * 在上游 SSE 字节到达时即时转换：先在首个字节到达时写出初始事件
 * （response.created / in_progress / output_item.added / content_part.added），
 * 然后对每个 data 行实时转换为 response.output_text.delta 事件并 flush。
 * <p>
 * 与旧的批处理方式不同，此实现确保客户端能接收到增量 token 流，
 * 而不是所有事件一次性到达。
 */
public class RealTimeResponsesTransformer extends OutputStream {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(RealTimeResponsesTransformer.class);

    /** 真实的 HTTP 响应输出流。 */
    private final OutputStream target;

    // Responses API 事件标识
    private final String responseId;
    private final String model;
    private final long createdAt;
    private final String itemId;

    /** 当前行字节缓冲。 */
    private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream(256);

    /** 已累积的完整文本（用于完成事件）。 */
    private final StringBuilder fullText = new StringBuilder();

    /** 已累积的推理过程文本（用于 reasoning summary）。 */
    private final StringBuilder reasoningText = new StringBuilder();

    /** 推理项的 ID（如 rs_xxx）。 */
    private String reasoningItemId;

    // ---- 状态标志 ----
    private boolean initialEventsWritten;
    private boolean finishReasonSeen;
    private boolean completionMidEventsWritten;
    private boolean completedEventSent;
    private boolean completed;
    private boolean reasoningSeen;
    private boolean reasoningDoneSent;
    private boolean messageOutputItemAdded;
    private int messageOutputIndex = -1;
    private int reasoningOutputIndex = -1;
    private int nextOutputIndex;
    private final Map<Integer, ToolCallState> toolCalls = new LinkedHashMap<>();

    // ---- 用量数据（由 upstream 在 finish_reason 后的独立 chunk 提供） ----
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    /** prompt_tokens_details.cached_tokens */
    private Integer cachedTokens;
    /** completion_tokens_details.reasoning_tokens */
    private Integer reasoningTokens;

    /** 上游错误消息（若有）。 */
    private String upstreamError;

    /** 当前 SSE 事件类型（Anthropic 格式时由 event: 行设置）。 */
    private String currentEventType;

    private static class ToolCallState {
        private final int index;
        private final int outputIndex;
        private final String itemId = "fc_" + UUID.randomUUID().toString().replace("-", "");
        private final StringBuilder arguments = new StringBuilder();
        private String callId;
        private String name = "";
        private boolean added;
        private boolean done;

        private ToolCallState(int index, int outputIndex) {
            this.index = index;
            this.outputIndex = outputIndex;
            this.callId = "call_" + UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * @param target    真实的 servlet response OutputStream
     * @param responseId 响应 ID（如 resp_xxx）
     * @param model      模型名
     * @param createdAt  创建时间戳（秒）
     */
    public RealTimeResponsesTransformer(OutputStream target, String responseId,
                                         String model, long createdAt) {
        this.target = target;
        this.responseId = responseId;
        this.model = model;
        this.createdAt = createdAt;
        this.itemId = "item_" + UUID.randomUUID().toString().replace("-", "");
        this.reasoningItemId = "rs_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 主动发送初始 SSE 事件（response.created / in_progress / output_item.added / content_part.added）。
     * <p>
     * 必须在调用上游 <code>body.writeTo(transformer)</code> 之前调用此方法，以确保客户端
     * 立即收到 HTTP 响应头和初始事件，而不是等到上游第一个字节到达后才发送。
     * 如果后续上游调用失败，错误事件会追加在初始事件之后。
     */
    public void sendInitialEvents() throws IOException {
        if (initialEventsWritten) {
            return;
        }
        writeInitialEvents();
        initialEventsWritten = true;
        if (log.isDebugEnabled()) {
            log.debug("已主动发送初始 SSE 事件: responseId={}, model={}", responseId, model);
        }
    }

    @Override
    public void write(int b) throws IOException {
        if (completed) {
            return;
        }
        if (!initialEventsWritten) {
            writeInitialEvents();
            initialEventsWritten = true;
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
        if (!initialEventsWritten) {
            writeInitialEvents();
            initialEventsWritten = true;
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
        // 保护性发送：若流已标记完成但 completed 事件未发出（如上游未返回 finish_reason/usage），
        // 在最终 flush 时补发确保客户端收到完成信号
        if (initialEventsWritten && !completedEventSent) {
            if (log.isDebugEnabled()) {
                log.debug("flush() 时补发 completed 事件: finishReasonSeen={}, completed={}", finishReasonSeen, completed);
            }
            trySendCompleted();
        }
        target.flush();
    }

    /**
     * 强制完成响应，在流结束时无论上游是否发送了 finish_reason 或 [DONE] 都发出 response.completed。
     * 与 flush() 不同，此方法会绕过 finishReasonSeen 和 completed 守卫，
     * 确保客户端始终能收到完成信号，避免因上游 SSE 格式差异导致对话挂起。
     */
    public void complete() throws IOException {
        if (initialEventsWritten && !completedEventSent) {
            finishReasonSeen = true;
            trySendCompleted();
        }
        target.flush();
    }

    /**
     * 处理一个完整的行（不含换行符）。
     */
    private void processLine(String line) throws IOException {
        if (line.isEmpty()) {
            return;
        }
        // Anthropic SSE 使用 event: 行标识事件类型
        if (line.startsWith("event: ")) {
            currentEventType = line.substring(7).trim();
            return;
        } else if (line.startsWith("event:")) {
            currentEventType = line.substring(6).trim();
            return;
        }
        if (line.startsWith("data: ")) {
            processData(line.substring(6).stripLeading());
        } else if (line.startsWith("data:")) {
            processData(line.substring(5).stripLeading());
        }
    }

    /**
     * 处理一条 data 行负载：自动检测 OpenAI Chat Completions 或 Anthropic SSE 格式并分别处理。
     * <ul>
     *   <li>OpenAI 格式：{@code {"choices":[{"delta":{"content":"..."},"finish_reason":"..."}]}}</li>
     *   <li>Anthropic 格式：由 {@code event:} 行标识类型，data 行包含 {@code "type":"content_block_delta","delta":{"text":"..."}}</li>
     * </ul>
     */
    private void processData(String data) throws IOException {
        if (completed) {
            return;
        }

        if ("[DONE]".equals(data)) {
            completed = true;
            trySendCompleted();
            return;
        }

        try {
            JsonNode chunk = OBJECT_MAPPER.readTree(data);

            // 检测上游错误事件
            JsonNode error = chunk.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                upstreamError = error.path("message").asText("Unknown upstream error");
                completed = true;
                writeErrorEvents();
                return;
            }

            // 根据 type 字段或当前 event 类型判断是否 Anthropic SSE 格式
            String type = chunk.path("type").asText(currentEventType != null ? currentEventType : "");
            if (isResponsesEventType(type)) {
                processResponsesData(chunk, type);
                return;
            }

            if (currentEventType != null || chunk.has("type")) {
                processAnthropicData(chunk, !type.isEmpty() ? type : currentEventType);
                return;
            }

            // OpenAI Chat Completions SSE 格式处理
            processOpenAiData(chunk);
        } catch (Exception e) {
            log.warn("跳过无法解析的data行: {}, 错误: {}", data, e.getMessage());
        }
    }

    /**
     * 处理 Anthropic SSE 格式的数据行。
     */
    private void processAnthropicData(JsonNode chunk, String type) throws IOException {
        switch (type) {
            case "content_block_delta" -> {
                JsonNode delta = chunk.path("delta");
                String text = delta.path("text").asText(null);
                appendAndWriteText(text);
            }
            case "message_delta" -> {
                // 提取停止原因（对应 Chat Completions 的 finish_reason）
                JsonNode delta = chunk.path("delta");
                String stopReason = delta.path("stop_reason").asText(null);
                if (stopReason != null && !"null".equals(stopReason) && !stopReason.isEmpty()) {
                    finishReasonSeen = true;
                    writeCompletionMidEvents();
                }
                // 提取用量
                JsonNode usage = chunk.path("usage");
                if (!usage.isMissingNode() && !usage.isNull()) {
                    outputTokens = intOrNull(usage, "output_tokens");
                    if (inputTokens == null) {
                        inputTokens = intOrNull(usage, "input_tokens");
                        if (inputTokens != null) {
                            totalTokens = inputTokens + (outputTokens != null ? outputTokens : 0);
                        }
                    }
                }
                if (finishReasonSeen) {
                    trySendCompleted();
                }
            }
            case "message_start" -> {
                // 提取初始用量中的 input_tokens
                JsonNode message = chunk.path("message");
                if (!message.isMissingNode() && !message.isNull()) {
                    JsonNode usage = message.path("usage");
                    if (!usage.isMissingNode() && !usage.isNull()) {
                        inputTokens = intOrNull(usage, "input_tokens");
                        int output = usage.path("output_tokens").asInt(0);
                        if (inputTokens != null) {
                            totalTokens = inputTokens + output;
                        }
                    }
                }
            }
            case "message_stop" -> {
                completed = true;
                if (!finishReasonSeen) {
                    finishReasonSeen = true;
                    writeCompletionMidEvents();
                }
                trySendCompleted();
            }
            case "content_block_stop", "ping" -> {
                // content_block_stop：单个内容块结束，消息可能还未结束，等待 message_delta/message_stop
                // ping：心跳，忽略
            }
            default -> log.trace("忽略未知 Anthropic 事件类型: {}", type);
        }
    }

    /**
     * 处理 OpenAI Chat Completions SSE 格式的数据行。
     */
    private void processResponsesData(JsonNode chunk, String type) throws IOException {
        switch (type) {
            case "response.output_text.delta" -> appendAndWriteText(chunk.path("delta").asText(null));
            case "response.output_text.done" -> {
                mergeCompletedText(chunk.path("text").asText(null));
                finishReasonSeen = true;
                writeCompletionMidEvents();
            }
            case "response.completed" -> {
                JsonNode response = chunk.path("response");
                extractResponsesUsage(response.path("usage"));
                mergeCompletedText(firstResponsesOutputText(response));
                finishReasonSeen = true;
                completed = true;
                trySendCompleted();
            }
            case "response.failed", "response.incomplete" -> {
                upstreamError = chunk.path("response").path("error").path("message")
                        .asText("Upstream Responses API stream failed");
                completed = true;
                writeErrorEvents();
            }
            case "error" -> {
                upstreamError = chunk.path("error").path("message")
                        .asText("Upstream Responses API stream failed");
                completed = true;
                writeErrorEvents();
            }
            default -> {
                // The gateway sends its own initial Responses events.
            }
        }
    }

    private void processToolCallDelta(JsonNode toolCallDelta) throws IOException {
        int index = toolCallDelta.path("index").asInt(toolCalls.size());
        ToolCallState state = toolCalls.computeIfAbsent(index,
                key -> new ToolCallState(key, nextOutputIndex++));
        JsonNode id = toolCallDelta.path("id");
        if (!id.isMissingNode() && !id.isNull() && !id.asText().isBlank()) {
            state.callId = id.asText();
        }
        JsonNode function = toolCallDelta.path("function");
        JsonNode name = function.path("name");
        if (!name.isMissingNode() && !name.isNull() && !name.asText().isBlank()) {
            state.name = name.asText();
        }
        ensureToolCallAdded(state);
        JsonNode arguments = function.path("arguments");
        if (!arguments.isMissingNode() && !arguments.isNull() && !arguments.asText().isEmpty()) {
            String delta = arguments.asText();
            state.arguments.append(delta);
            writeSse("response.function_call_arguments.delta",
                    "{\"type\":\"response.function_call_arguments.delta\",\"output_index\":%d,\"item_id\":\"%s\",\"delta\":\"%s\"}"
                            .formatted(state.outputIndex, esc(state.itemId), esc(delta)));
        }
    }

    private void ensureToolCallAdded(ToolCallState state) throws IOException {
        if (state.added) {
            return;
        }
        state.added = true;
        writeSse("response.output_item.added",
                "{\"type\":\"response.output_item.added\",\"output_index\":%d,\"item\":%s}"
                        .formatted(state.outputIndex, toolCallItemJson(state, false)));
    }

    private void writeToolCallDoneEvents() throws IOException {
        for (ToolCallState state : toolCalls.values()) {
            if (!state.added || state.done) {
                continue;
            }
            state.done = true;
            writeSse("response.function_call_arguments.done",
                    "{\"type\":\"response.function_call_arguments.done\",\"output_index\":%d,\"item_id\":\"%s\",\"arguments\":\"%s\"}"
                            .formatted(state.outputIndex, esc(state.itemId), esc(state.arguments.toString())));
            writeSse("response.output_item.done",
                    "{\"type\":\"response.output_item.done\",\"output_index\":%d,\"item\":%s}"
                            .formatted(state.outputIndex, toolCallItemJson(state, true)));
        }
    }

    private String toolCallItemJson(ToolCallState state, boolean done) {
        return "{\"id\":\"%s\",\"object\":\"item\",\"type\":\"function_call\",\"status\":\"%s\",\"call_id\":\"%s\",\"name\":\"%s\",\"arguments\":\"%s\"}"
                .formatted(esc(state.itemId), done ? "completed" : "in_progress",
                        esc(state.callId), esc(state.name), esc(state.arguments.toString()));
    }

    private void processOpenAiData(JsonNode chunk) throws IOException {
        // 提取用量（在 finish_reason 后的独立 chunk 中，choices 通常为空数组）
        boolean hadUsage = false;
        JsonNode usage = chunk.path("usage");
        if (!usage.isMissingNode() && !usage.isNull()) {
            inputTokens = intOrNull(usage, "prompt_tokens");
            outputTokens = intOrNull(usage, "completion_tokens");
            totalTokens = intOrNull(usage, "total_tokens");
            // 提取 token 详情
            JsonNode promptDetails = usage.path("prompt_tokens_details");
            if (!promptDetails.isMissingNode() && !promptDetails.isNull()) {
                cachedTokens = intOrNull(promptDetails, "cached_tokens");
            }
            JsonNode completionDetails = usage.path("completion_tokens_details");
            if (!completionDetails.isMissingNode() && !completionDetails.isNull()) {
                reasoningTokens = intOrNull(completionDetails, "reasoning_tokens");
            }
            hadUsage = true;
        }

        // 解析内容增量
        JsonNode choices = chunk.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode firstChoice = choices.get(0);
            JsonNode delta = firstChoice.path("delta");
            String reasoningContent = delta.path("reasoning_content").asText(null);
            String content = delta.path("content").asText(null);
            String finishReason = firstChoice.path("finish_reason").asText(null);
            JsonNode toolCallDeltas = delta.path("tool_calls");

            // 处理推理过程内容（如 DeepSeek、o1/o3 的 reasoning_content）
            if (reasoningContent != null && !reasoningContent.isEmpty()) {
                ensureReasoningItemCreated();
                reasoningText.append(reasoningContent);
                writeReasoningDelta(reasoningContent);
            }

            // 处理常规输出内容
            if (content != null && !content.isEmpty()) {
                // 如果之前有推理内容，先完成推理项再发送普通消息项
                if (reasoningSeen && !reasoningDoneSent) {
                    writeReasoningDone();
                }
                appendAndWriteText(content);
            }

            if (toolCallDeltas.isArray() && !toolCallDeltas.isEmpty()) {
                for (JsonNode toolCallDelta : toolCallDeltas) {
                    processToolCallDelta(toolCallDelta);
                }
            }

            if (finishReason != null && !"null".equals(finishReason) && !finishReason.isEmpty()) {
                finishReasonSeen = true;
                // 关闭推理项（若尚未关闭）
                if (reasoningSeen && !reasoningDoneSent) {
                    writeReasoningDone();
                }
                writeToolCallDoneEvents();
                writeCompletionMidEvents();
                // 立即发送 response.completed，不等 usage chunk。
                // 部分上游在 finish_reason 后不发送 usage 或 usage 格式不标准，
                // 延迟等待会导致客户端 "stream closed before response.completed" 错误。
                trySendCompleted();
            }
        }

        // 如果收到了用量 chunk 且 response.completed 尚未发送（finish_reason 在前一个 chunk 已处理），
        // 此处不会重复发送（trySendCompleted 内部有 completedEventSent 守卫）
        if (hadUsage) {
            trySendCompleted();
        }
    }

    /**
     * 写出初始事件：created、in_progress、output_item.added、content_part.added。
     * 在第一个 write() 调用时惰性写出，确保仅在确有上游数据时才发送。
     */
    private void writeInitialEvents() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("写出初始 SSE 事件: responseId={}, model={}", responseId, model);
        }
        // 仅发送 response.created / response.in_progress，output_item.added 推迟到首个 delta 时按实际内容类型发送
        String responseObj = "{\"id\":\"%s\",\"created_at\":%d,\"status\":\"in_progress\",\"model\":\"%s\",\"object\":\"response\"}"
                .formatted(esc(responseId), createdAt, esc(model));

        writeSse("response.created",
                "{\"type\":\"response.created\",\"response\":%s}".formatted(responseObj));

        writeSse("response.in_progress",
                "{\"type\":\"response.in_progress\",\"response\":%s}".formatted(responseObj));
    }

    /**
     * 写出增量文本事件。
     */
    private void writeDelta(String delta) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("写出 delta 事件: delta={}", delta);
        }
        int outputIndex = messageOutputIndex >= 0 ? messageOutputIndex : 0;
        writeSse("response.output_text.delta",
                "{\"type\":\"response.output_text.delta\",\"output_index\":%d,\"content_index\":0,\"delta\":\"%s\"}"
                        .formatted(outputIndex, esc(delta)));
    }

    /**
     * 按需创建推理项及其 SSE 事件（仅在首次推理 token 到达时触发一次）。
     */
    private boolean isResponsesEventType(String type) {
        return type != null && ("error".equals(type) || type.startsWith("response."));
    }

    private void appendAndWriteText(String text) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        String current = fullText.toString();
        String delta = text;
        if (!current.isEmpty()) {
            if (text.startsWith(current)) {
                delta = text.substring(current.length());
            } else if (current.endsWith(text)) {
                delta = "";
            }
        }
        if (delta.isEmpty()) {
            return;
        }
        ensureMessageOutputItemCreated();
        fullText.append(delta);
        writeDelta(delta);
    }

    private void mergeCompletedText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String current = fullText.toString();
        if (current.isEmpty()) {
            fullText.append(text);
        } else if (text.startsWith(current)) {
            fullText.append(text.substring(current.length()));
        }
    }

    private void extractResponsesUsage(JsonNode usage) {
        if (usage.isMissingNode() || usage.isNull()) {
            return;
        }
        inputTokens = intOrNull(usage, "input_tokens");
        outputTokens = intOrNull(usage, "output_tokens");
        totalTokens = intOrNull(usage, "total_tokens");
        JsonNode promptDetails = usage.path("prompt_tokens_details");
        if (!promptDetails.isMissingNode() && !promptDetails.isNull()) {
            cachedTokens = intOrNull(promptDetails, "cached_tokens");
        }
        JsonNode completionDetails = usage.path("completion_tokens_details");
        if (!completionDetails.isMissingNode() && !completionDetails.isNull()) {
            reasoningTokens = intOrNull(completionDetails, "reasoning_tokens");
        }
    }

    private String firstResponsesOutputText(JsonNode response) {
        JsonNode output = response.path("output");
        if (!output.isArray()) {
            return null;
        }
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                String type = part.path("type").asText("");
                if ("output_text".equals(type) || "text".equals(type)) {
                    String text = part.path("text").asText(null);
                    if (text != null) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private void ensureReasoningItemCreated() throws IOException {
        if (reasoningSeen) {
            return;
        }
        reasoningSeen = true;
        reasoningOutputIndex = nextOutputIndex++;
        if (log.isDebugEnabled()) {
            log.debug("创建推理项: itemId={}", reasoningItemId);
        }
        writeSse("response.output_item.added",
                "{\"type\":\"response.output_item.added\",\"output_index\":%d,\"item\":{\"id\":\"%s\",\"object\":\"item\",\"type\":\"reasoning\",\"status\":\"in_progress\"}}"
                        .formatted(reasoningOutputIndex, esc(reasoningItemId)));
    }

    /**
     * 按需创建消息输出项及其 content_part（仅在首个内容 token 到达时触发一次）。
     */
    private void ensureMessageOutputItemCreated() throws IOException {
        if (messageOutputItemAdded) {
            return;
        }
        messageOutputItemAdded = true;
        messageOutputIndex = nextOutputIndex++;
        if (log.isDebugEnabled()) {
            log.debug("创建消息输出项: itemId={}", itemId);
        }
        writeSse("response.output_item.added",
                "{\"type\":\"response.output_item.added\",\"output_index\":%d,\"item\":{\"id\":\"%s\",\"object\":\"item\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"in_progress\",\"content\":[]}}"
                        .formatted(messageOutputIndex, esc(itemId)));
        writeSse("response.content_part.added",
                "{\"type\":\"response.content_part.added\",\"output_index\":%d,\"content_index\":0,\"part\":{\"type\":\"output_text\",\"text\":\"\",\"annotations\":[]}}"
                        .formatted(messageOutputIndex));
    }

    /**
     * 写出推理增量 token 事件。
     */
    private void writeReasoningDelta(String delta) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("写出推理 delta 事件: delta={}", delta);
        }
        writeSse("response.reasoning_item.delta",
                "{\"type\":\"response.reasoning_item.delta\",\"item\":{\"id\":\"%s\"},\"delta\":{\"text\":\"%s\"}}"
                        .formatted(esc(reasoningItemId), esc(delta)));
    }

    /**
     * 写出推理完成事件。
     */
    private void writeReasoningDone() throws IOException {
        if (reasoningDoneSent) {
            return;
        }
        reasoningDoneSent = true;
        String summaryText = esc(reasoningText.toString());
        if (log.isDebugEnabled()) {
            log.debug("写出推理完成事件: summary 长度={}", reasoningText.length());
        }
        writeSse("response.reasoning_item.done",
                "{\"type\":\"response.reasoning_item.done\",\"item\":{\"id\":\"%s\",\"type\":\"reasoning\",\"status\":\"completed\",\"summary\":[{\"text\":\"%s\"}]}}"
                        .formatted(esc(reasoningItemId), summaryText));
    }

    /**
     * 写出中间完成事件：output_text.done、content_part.done、output_item.done。
     * 在 finish_reason 时触发，但不下发 response.completed（需等待用量）。
     */
    private void writeCompletionMidEvents() throws IOException {
        if (completionMidEventsWritten) {
            return;
        }
        completionMidEventsWritten = true;
        if (log.isDebugEnabled()) {
            log.debug("写出中间完成事件: finishReason 已见, 累积文本长度={}", fullText.length());
        }

        if (!messageOutputItemAdded && fullText.isEmpty()) {
            return;
        }

        ensureMessageOutputItemCreated();
        int outputIndex = messageOutputIndex >= 0 ? messageOutputIndex : 0;

        String escapedText = esc(fullText.toString());

        // output_text.done：包含 annotations 字段
        writeSse("response.output_text.done",
                "{\"type\":\"response.output_text.done\",\"output_index\":%d,\"content_index\":0,\"text\":\"%s\",\"annotations\":[]}"
                        .formatted(outputIndex, escapedText));

        String contentPart = "{\"type\":\"output_text\",\"text\":\"%s\",\"annotations\":[]}"
                .formatted(escapedText);

        writeSse("response.content_part.done",
                "{\"type\":\"response.content_part.done\",\"output_index\":%d,\"content_index\":0,\"part\":%s}"
                        .formatted(outputIndex, contentPart));

        // output_item.done：使用 item 字段（符合 OpenAI 规范），包含 object 和 status
        writeSse("response.output_item.done",
                "{\"type\":\"response.output_item.done\",\"output_index\":%d,\"item\":{\"id\":\"%s\",\"object\":\"item\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[%s]}}"
                        .formatted(outputIndex, esc(itemId), contentPart));
    }

    /**
     * 响应完成时写出 response.completed。
     * 自动补发中间完成事件（output_text.done / content_part.done / output_item.done），
     * 处理 DeepSeek 等上游不发送 finish_reason 事件块的情况。
     */
    private void trySendCompleted() throws IOException {
        if (completedEventSent) {
            return;
        }
        // 普通完成：需要 finish_reason 已见或 completed 已标记（如 [DONE] 到达）
        if (!finishReasonSeen && !completed) {
            if (log.isTraceEnabled()) {
                log.trace("跳过 completed 事件: finishReasonSeen={}, completed={}", finishReasonSeen, completed);
            }
            return;
        }

        // 关闭推理项（若因 [DONE] 等场景尚未触发写入）
        if (reasoningSeen && !reasoningDoneSent) {
            writeReasoningDone();
        }

        // 如果中间完成事件尚未写出（上游未发送 finish_reason），在此补发
        writeToolCallDoneEvents();

        if (!completionMidEventsWritten) {
            writeCompletionMidEvents();
        }

        // 在写出所有事件后将标志置为 true，确保 writeReasoningDone / writeCompletionMidEvents
        // 抛出 IOException 时 complete() 还能重试。
        completedEventSent = true;

        if (log.isDebugEnabled()) {
            log.debug("写出 response.completed 事件: finishReasonSeen={}, completed={}, inputTokens={}, outputTokens={}",
                    finishReasonSeen, completed, inputTokens, outputTokens);
        }
        String contentPart = "{\"type\":\"output_text\",\"text\":\"%s\",\"annotations\":[]}"
                .formatted(esc(fullText.toString()));

        // 构建带可选用量详情的 usage JSON
        String promptDetailsJson = cachedTokens != null
                ? ",\"prompt_tokens_details\":{\"cached_tokens\":%d}".formatted(cachedTokens)
                : "";
        String completionDetailsJson = reasoningTokens != null
                ? ",\"completion_tokens_details\":{\"reasoning_tokens\":%d}".formatted(reasoningTokens)
                : "";
        String usageJson = "{\"input_tokens\":%d,\"output_tokens\":%d,\"total_tokens\":%d%s%s}"
                .formatted(
                        inputTokens != null ? inputTokens : 0,
                        outputTokens != null ? outputTokens : 0,
                        totalTokens != null ? totalTokens : 0,
                        promptDetailsJson, completionDetailsJson);

        writeSse("response.completed",
                "{\"type\":\"response.completed\",\"response\":{\"id\":\"%s\",\"created_at\":%d,\"status\":\"completed\",\"model\":\"%s\",\"object\":\"response\",\"output\":[%s],\"usage\":%s}}"
                        .formatted(esc(responseId), createdAt, esc(model),
                                completedOutputJson(contentPart), usageJson));
    }

    private String completedOutputJson(String contentPart) {
        Map<Integer, String> items = new TreeMap<>();
        if (reasoningSeen && reasoningDoneSent) {
            int outputIndex = reasoningOutputIndex >= 0 ? reasoningOutputIndex : 0;
            items.put(outputIndex,
                    "{\"id\":\"%s\",\"object\":\"item\",\"type\":\"reasoning\",\"status\":\"completed\",\"summary\":[{\"text\":\"%s\"}]}"
                            .formatted(esc(reasoningItemId), esc(reasoningText.toString())));
        }
        if (messageOutputItemAdded || !fullText.isEmpty()) {
            int outputIndex = messageOutputIndex >= 0 ? messageOutputIndex : 0;
            items.put(outputIndex,
                    "{\"id\":\"%s\",\"object\":\"item\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[%s]}"
                            .formatted(esc(itemId), contentPart));
        }
        for (ToolCallState state : toolCalls.values()) {
            if (state.added) {
                items.put(state.outputIndex, toolCallItemJson(state, true));
            }
        }
        if (items.isEmpty()) {
            items.put(0,
                    "{\"id\":\"%s\",\"object\":\"item\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[%s]}"
                            .formatted(esc(itemId), contentPart));
        }
        return String.join(",", items.values());
    }

    /**
     * 上游错误时写出 error 和 response.completed failed 事件。
     */
    private void writeErrorEvents() throws IOException {
        completedEventSent = true;
        if (log.isDebugEnabled()) {
            log.debug("写出错误事件: upstreamError={}", upstreamError);
        }
        String msg = upstreamError != null ? upstreamError : "Unknown upstream error";
        writeSse("error",
                "{\"type\":\"error\",\"error\":{\"message\":\"%s\",\"code\":\"upstream_error\"}}"
                        .formatted(esc(msg)));

        writeSse("response.completed",
                "{\"type\":\"response.completed\",\"response\":{\"id\":\"%s\",\"status\":\"failed\",\"usage\":{\"input_tokens\":0,\"output_tokens\":0,\"total_tokens\":0}}}"
                        .formatted(esc(responseId)));
    }

    /**
     * 供控制器在 catch 中检查 completed 事件是否已发出，避免重复发送错误事件。
     */
    public boolean isCompletedEventSent() {
        return completedEventSent;
    }

    /**
     * 供控制器在异常兜底时手动写出错误事件，确保客户端能收到错误信号而非空响应。
     */
    public void writeErrorWithMessage(String message) throws IOException {
        writeSse("error",
                "{\"type\":\"error\",\"error\":{\"message\":\"%s\",\"code\":\"upstream_error\"}}"
                        .formatted(esc(message)));
        writeSse("response.completed",
                "{\"type\":\"response.completed\",\"response\":{\"id\":\"%s\",\"status\":\"failed\",\"usage\":{\"input_tokens\":0,\"output_tokens\":0,\"total_tokens\":0}}}"
                        .formatted(esc(responseId)));
        completedEventSent = true;
    }

    /**
     * 写出单条 SSE 事件并 flush。
     */
    private void writeSse(String eventName, String dataJson) throws IOException {
        String event = "event: " + eventName + "\ndata: " + dataJson + "\n\n";
        target.write(event.getBytes(StandardCharsets.UTF_8));
        target.flush();
    }

    // ---- 辅助方法 ----

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
