package cn.ms08.apiconvert.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

    // ---- 状态标志 ----
    private boolean initialEventsWritten;
    private boolean finishReasonSeen;
    private boolean completionMidEventsWritten;
    private boolean completedEventSent;
    private boolean completed;

    // ---- 用量数据（由 upstream 在 finish_reason 后的独立 chunk 提供） ----
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;

    /** 上游错误消息（若有）。 */
    private String upstreamError;

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
     * 处理一个完整的行（不含换行符）。
     */
    private void processLine(String line) throws IOException {
        if (line.isEmpty()) {
            return;
        }
        if (line.startsWith("data: ")) {
            processData(line.substring(6).stripLeading());
        } else if (line.startsWith("data:")) {
            processData(line.substring(5).stripLeading());
        }
        // event: 等行忽略，Chat Completions SSE 通常无 event 行
    }

    /**
     * 处理一条 data 行负载：解析 Chat Completions chunk 并转为 Responses API 事件。
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

            // 提取用量（在 finish_reason 后的独立 chunk 中，choices 通常为空数组）
            boolean hadUsage = false;
            JsonNode usage = chunk.path("usage");
            if (!usage.isMissingNode() && !usage.isNull()) {
                inputTokens = intOrNull(usage, "prompt_tokens");
                outputTokens = intOrNull(usage, "completion_tokens");
                totalTokens = intOrNull(usage, "total_tokens");
                hadUsage = true;
            }

            // 解析内容增量
            JsonNode choices = chunk.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode firstChoice = choices.get(0);
                JsonNode delta = firstChoice.path("delta");
                String content = delta.path("content").asText(null);
                String finishReason = firstChoice.path("finish_reason").asText(null);

                if (content != null && !content.isEmpty()) {
                    fullText.append(content);
                    writeDelta(content);
                }

                if (finishReason != null && !"null".equals(finishReason) && !finishReason.isEmpty()) {
                    finishReasonSeen = true;
                    writeCompletionMidEvents();
                }
            }

            // 如果收到了用量 chunk 且 finish_reason 已出现，发送 response.completed
            if (hadUsage && finishReasonSeen) {
                trySendCompleted();
            }
        } catch (Exception e) {
            log.warn("跳过无法解析的data行: {}, 错误: {}", data, e.getMessage());
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
        // response.created / response.in_progress 事件中的 response 对象包含关键字段
        String responseObj = "{\"id\":\"%s\",\"created_at\":%d,\"status\":\"in_progress\",\"model\":\"%s\",\"object\":\"response\"}"
                .formatted(esc(responseId), createdAt, esc(model));

        writeSse("response.created",
                "{\"type\":\"response.created\",\"response\":%s}".formatted(responseObj));

        writeSse("response.in_progress",
                "{\"type\":\"response.in_progress\",\"response\":%s}".formatted(responseObj));

        // output_item.added：使用 item 字段（符合 OpenAI 规范），包含 object 和 status
        writeSse("response.output_item.added",
                "{\"type\":\"response.output_item.added\",\"output_index\":0,\"item\":{\"id\":\"%s\",\"object\":\"item\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"in_progress\",\"content\":[]}}"
                        .formatted(esc(itemId)));

        // content_part.added：包含 annotations 字段
        writeSse("response.content_part.added",
                "{\"type\":\"response.content_part.added\",\"output_index\":0,\"content_index\":0,\"part\":{\"type\":\"output_text\",\"text\":\"\",\"annotations\":[]}}");
    }

    /**
     * 写出增量文本事件。
     */
    private void writeDelta(String delta) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("写出 delta 事件: delta={}", delta);
        }
        writeSse("response.output_text.delta",
                "{\"type\":\"response.output_text.delta\",\"output_index\":0,\"content_index\":0,\"delta\":\"%s\"}"
                        .formatted(esc(delta)));
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

        String escapedText = esc(fullText.toString());

        // output_text.done：包含 annotations 字段
        writeSse("response.output_text.done",
                "{\"type\":\"response.output_text.done\",\"output_index\":0,\"content_index\":0,\"text\":\"%s\",\"annotations\":[]}"
                        .formatted(escapedText));

        String contentPart = "{\"type\":\"output_text\",\"text\":\"%s\",\"annotations\":[]}"
                .formatted(escapedText);

        writeSse("response.content_part.done",
                "{\"type\":\"response.content_part.done\",\"output_index\":0,\"content_index\":0,\"part\":%s}"
                        .formatted(contentPart));

        // output_item.done：使用 item 字段（符合 OpenAI 规范），包含 object 和 status
        writeSse("response.output_item.done",
                "{\"type\":\"response.output_item.done\",\"output_index\":0,\"item\":{\"id\":\"%s\",\"object\":\"item\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[%s]}}"
                        .formatted(esc(itemId), contentPart));
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
        completedEventSent = true;

        // 如果中间完成事件尚未写出（上游未发送 finish_reason），在此补发
        if (!completionMidEventsWritten) {
            writeCompletionMidEvents();
        }

        if (log.isDebugEnabled()) {
            log.debug("写出 response.completed 事件: finishReasonSeen={}, completed={}, inputTokens={}, outputTokens={}",
                    finishReasonSeen, completed, inputTokens, outputTokens);
        }
        String escapedText = esc(fullText.toString());

        String contentPart = "{\"type\":\"output_text\",\"text\":\"%s\",\"annotations\":[]}"
                .formatted(escapedText);

        writeSse("response.completed",
                "{\"type\":\"response.completed\",\"response\":{\"id\":\"%s\",\"created_at\":%d,\"status\":\"completed\",\"model\":\"%s\",\"object\":\"response\",\"output\":[{\"id\":\"%s\",\"object\":\"item\",\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[%s]}],\"usage\":{\"input_tokens\":%d,\"output_tokens\":%d,\"total_tokens\":%d}}}"
                        .formatted(esc(responseId), createdAt, esc(model), esc(itemId), contentPart,
                                inputTokens != null ? inputTokens : 0,
                                outputTokens != null ? outputTokens : 0,
                                totalTokens != null ? totalTokens : 0));
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
