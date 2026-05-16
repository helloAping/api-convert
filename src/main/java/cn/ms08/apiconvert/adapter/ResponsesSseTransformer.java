package cn.ms08.apiconvert.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 将上游 Chat Completions SSE 格式转换为 Responses API SSE 格式。
 * 先收集所有 SSE 块，再一次性写出完整的 Responses API SSE 事件序列，
 * 避免实时转换可能导致的格式或时机问题。
 */
public class ResponsesSseTransformer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StringBuilder fullText = new StringBuilder();
    private String upstreamError;

    /** 从上游 SSE 中提取的用量数据。 */
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;

    /** 标记是否已收到 finish_reason。 */
    private boolean streamEnded;

    /**
     * 写入一个上游 SSE data 行进行处理。
     * 返回 true 表示流已结束（收到 [DONE]），调用方可据此停止写入。
     */
    public boolean writeData(String data) {
        if ("[DONE]".equals(data)) {
            return true;
        }
        try {
            JsonNode chunk = OBJECT_MAPPER.readTree(data);

            // 检测上游错误
            JsonNode error = chunk.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                upstreamError = error.path("message").asText("Unknown error");
                return true;
            }

            // 提取 usage（可能在 finish_reason 之后的独立 chunk 中）
            JsonNode usage = chunk.path("usage");
            if (!usage.isMissingNode() && !usage.isNull()) {
                inputTokens = intOrNull(usage, "prompt_tokens");
                outputTokens = intOrNull(usage, "completion_tokens");
                totalTokens = intOrNull(usage, "total_tokens");
            }

            JsonNode choices = chunk.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return false;
            }
            JsonNode delta = choices.get(0).path("delta");
            String content = delta.path("content").asText(null);
            String finishReason = choices.get(0).path("finish_reason").asText(null);

            if (content != null && !content.isEmpty()) {
                fullText.append(content);
            }

            if (finishReason != null && !"null".equals(finishReason) && !finishReason.isEmpty()) {
                streamEnded = true;
            }
        } catch (Exception ignored) {
            // 跳过无法解析的行
        }
        return false;
    }

    /**
     * 将所有收集到的内容以 Responses API SSE 格式写出到目标输出流。
     *
     * @param responseId  响应 ID（如 resp_xxx）
     * @param model       模型名
     * @param createdAt   创建时间戳（秒）
     */
    public void writeSseEvents(OutputStream target, String responseId, String model, long createdAt) throws IOException {
        if (upstreamError != null) {
            writeSse(target, "error",
                    "{\"type\":\"error\",\"error\":{\"message\":\"%s\",\"code\":\"upstream_error\"}}"
                            .formatted(esc(upstreamError)));
            writeSse(target, "response.completed",
                    "{\"type\":\"response.completed\",\"response\":{\"id\":\"%s\",\"status\":\"failed\",\"usage\":{\"input_tokens\":0,\"output_tokens\":0,\"total_tokens\":0}}}"
                            .formatted(esc(responseId)));
            return;
        }

        writeSse(target, "response.created",
                "{\"type\":\"response.created\",\"response\":{\"id\":\"%s\",\"created_at\":%d,\"status\":\"in_progress\",\"model\":\"%s\",\"object\":\"response\"}}"
                        .formatted(esc(responseId), createdAt, esc(model)));

        String itemId = "item_" + UUID.randomUUID().toString().replace("-", "");
        writeSse(target, "response.output_item.added",
                "{\"type\":\"response.output_item.added\",\"output_index\":0,\"output_item\":{\"id\":\"%s\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[]}}"
                        .formatted(esc(itemId)));

        writeSse(target, "response.content_part.added",
                "{\"type\":\"response.content_part.added\",\"output_index\":0,\"content_index\":0,\"part\":{\"type\":\"output_text\",\"text\":\"\"}}");

        if (fullText.length() > 0) {
            writeSse(target, "response.output_text.delta",
                    "{\"type\":\"response.output_text.delta\",\"output_index\":0,\"content_index\":0,\"delta\":\"%s\"}"
                            .formatted(esc(fullText.toString())));
        }

        String escapedText = esc(fullText.toString());

        writeSse(target, "response.output_text.done",
                "{\"type\":\"response.output_text.done\",\"output_index\":0,\"content_index\":0,\"text\":\"%s\"}"
                        .formatted(escapedText));

        String contentPart = "{\"type\":\"output_text\",\"text\":\"%s\",\"annotations\":[]}"
                .formatted(escapedText);

        writeSse(target, "response.content_part.done",
                "{\"type\":\"response.content_part.done\",\"output_index\":0,\"content_index\":0,\"part\":%s}"
                        .formatted(contentPart));

        writeSse(target, "response.output_item.done",
                "{\"type\":\"response.output_item.done\",\"output_index\":0,\"output_item\":{\"id\":\"%s\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[%s]}}"
                        .formatted(esc(itemId), contentPart));

        writeSse(target, "response.completed",
                "{\"type\":\"response.completed\",\"response\":{\"id\":\"%s\",\"created_at\":%d,\"status\":\"completed\",\"model\":\"%s\",\"object\":\"response\",\"output\":[{\"id\":\"%s\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[%s]}],\"usage\":{\"input_tokens\":%d,\"output_tokens\":%d,\"total_tokens\":%d}}}"
                        .formatted(esc(responseId), createdAt, esc(model), esc(itemId), contentPart,
                                inputTokens != null ? inputTokens : 0,
                                outputTokens != null ? outputTokens : 0,
                                totalTokens != null ? totalTokens : 0));

        target.flush();
    }

    private static void writeSse(OutputStream target, String eventName, String dataJson) throws IOException {
        String event = "event: " + eventName + "\ndata: " + dataJson + "\n\n";
        target.write(event.getBytes(StandardCharsets.UTF_8));
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
