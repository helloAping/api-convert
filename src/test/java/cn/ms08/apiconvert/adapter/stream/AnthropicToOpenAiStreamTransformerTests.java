package cn.ms08.apiconvert.adapter.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicToOpenAiStreamTransformerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void convertsAnthropicTextStreamToOpenAiChatChunks() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        AnthropicToOpenAiStreamTransformer transformer =
                new AnthropicToOpenAiStreamTransformer(target, "chatcmpl_test", "claude-3-opus", 1L);

        // message_start
        writeLine(transformer, "event: message_start");
        writeLine(transformer, "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_001\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"claude-3-opus\",\"usage\":{\"input_tokens\":50,\"output_tokens\":0}}}");
        writeLine(transformer, "");

        // content_block_start (text)
        writeLine(transformer, "event: content_block_start");
        writeLine(transformer, "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
        writeLine(transformer, "");

        // content_block_delta (text)
        writeLine(transformer, "event: content_block_delta");
        writeLine(transformer, "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}");
        writeLine(transformer, "");

        writeLine(transformer, "event: content_block_delta");
        writeLine(transformer, "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}");
        writeLine(transformer, "");

        // content_block_stop
        writeLine(transformer, "event: content_block_stop");
        writeLine(transformer, "data: {\"type\":\"content_block_stop\",\"index\":0}");
        writeLine(transformer, "");

        // message_delta with stop_reason
        writeLine(transformer, "event: message_delta");
        writeLine(transformer, "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":5}}");
        writeLine(transformer, "");

        // message_stop
        writeLine(transformer, "event: message_stop");
        writeLine(transformer, "data: {\"type\":\"message_stop\"}");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证包含 role chunk
        assertThat(output).contains("\"role\":\"assistant\"");

        // 验证包含文本 delta
        assertThat(output).contains("\"content\":\"Hello\"");
        assertThat(output).contains("\"content\":\" world\"");

        // 验证包含 finish_reason: stop
        assertThat(output).contains("\"finish_reason\":\"stop\"");

        // 验证包含 usage
        assertThat(output).contains("\"prompt_tokens\":50");
        assertThat(output).contains("\"completion_tokens\":5");

        // 验证包含 [DONE]
        assertThat(output).contains("data: [DONE]");

        // 验证不包含 Anthropic 格式的 event 行
        assertThat(output).doesNotContain("event: message_start");

        // 验证所有 data 行都是合法 JSON（除了 [DONE]）
        assertAllDataLinesAreValidJson(output);
    }

    @Test
    void convertsAnthropicToolUseToOpenAiToolCalls() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        AnthropicToOpenAiStreamTransformer transformer =
                new AnthropicToOpenAiStreamTransformer(target, "chatcmpl_test", "claude-3-opus", 1L);

        // message_start
        writeLine(transformer, "event: message_start");
        writeLine(transformer, "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_002\",\"model\":\"claude-3-opus\",\"usage\":{\"input_tokens\":30}}}");
        writeLine(transformer, "");

        // tool_use content_block_start
        writeLine(transformer, "event: content_block_start");
        writeLine(transformer, "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_123\",\"name\":\"shell\",\"input\":{}}}");
        writeLine(transformer, "");

        // tool_use input_json_delta
        writeLine(transformer, "event: content_block_delta");
        writeLine(transformer, "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"cmd\\\":\\\"git\"}}");
        writeLine(transformer, "");

        writeLine(transformer, "event: content_block_delta");
        writeLine(transformer, "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\" status\\\"}\"}}");
        writeLine(transformer, "");

        // content_block_stop
        writeLine(transformer, "event: content_block_stop");
        writeLine(transformer, "data: {\"type\":\"content_block_stop\",\"index\":0}");
        writeLine(transformer, "");

        // message_delta with tool_use stop
        writeLine(transformer, "event: message_delta");
        writeLine(transformer, "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":10}}");
        writeLine(transformer, "");

        writeLine(transformer, "event: message_stop");
        writeLine(transformer, "data: {\"type\":\"message_stop\"}");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证包含 tool_calls 开始
        assertThat(output).contains("\"type\":\"function\"");
        assertThat(output).contains("\"name\":\"shell\"");
        assertThat(output).contains("\"id\":\"toolu_123\"");

        // 验证包含 arguments delta
        assertThat(output).contains("\"arguments\":\"{\\\"cmd\\\":\\\"git\"");
        assertThat(output).contains("\"arguments\":\" status\\\"}\"");

        // 验证 finish_reason 为 tool_calls
        assertThat(output).contains("\"finish_reason\":\"tool_calls\"");

        // 验证包含 [DONE]
        assertThat(output).contains("data: [DONE]");
    }

    @Test
    void convertsAnthropicThinkingToOpenAiReasoningContent() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        AnthropicToOpenAiStreamTransformer transformer =
                new AnthropicToOpenAiStreamTransformer(target, "chatcmpl_test", "claude-3-opus", 1L);

        // message_start
        writeLine(transformer, "event: message_start");
        writeLine(transformer, "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_003\",\"model\":\"claude-3-opus\",\"usage\":{\"input_tokens\":20}}}");
        writeLine(transformer, "");

        // thinking content_block_start
        writeLine(transformer, "event: content_block_start");
        writeLine(transformer, "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}");
        writeLine(transformer, "");

        // thinking delta
        writeLine(transformer, "event: content_block_delta");
        writeLine(transformer, "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"Let me think...\"}}");
        writeLine(transformer, "");

        writeLine(transformer, "event: content_block_stop");
        writeLine(transformer, "data: {\"type\":\"content_block_stop\",\"index\":0}");
        writeLine(transformer, "");

        // text content_block_start + delta
        writeLine(transformer, "event: content_block_start");
        writeLine(transformer, "data: {\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
        writeLine(transformer, "");

        writeLine(transformer, "event: content_block_delta");
        writeLine(transformer, "data: {\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"The answer is 42.\"}}");
        writeLine(transformer, "");

        writeLine(transformer, "event: content_block_stop");
        writeLine(transformer, "data: {\"type\":\"content_block_stop\",\"index\":1}");
        writeLine(transformer, "");

        // message_delta + message_stop
        writeLine(transformer, "event: message_delta");
        writeLine(transformer, "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":15}}");
        writeLine(transformer, "");

        writeLine(transformer, "event: message_stop");
        writeLine(transformer, "data: {\"type\":\"message_stop\"}");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证包含 reasoning_content
        assertThat(output).contains("\"reasoning_content\":\"Let me think...\"");

        // 验证包含常规内容
        assertThat(output).contains("\"content\":\"The answer is 42.\"");

        // 验证完成
        assertThat(output).contains("\"finish_reason\":\"stop\"");
        assertThat(output).contains("data: [DONE]");
    }

    @Test
    void emitsErrorAndDoneOnUpstreamError() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        AnthropicToOpenAiStreamTransformer transformer =
                new AnthropicToOpenAiStreamTransformer(target, "chatcmpl_test", "claude-3-opus", 1L);

        // message_start
        writeLine(transformer, "event: message_start");
        writeLine(transformer, "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_004\",\"model\":\"claude-3-opus\",\"usage\":{\"input_tokens\":10}}}");
        writeLine(transformer, "");

        // error event
        writeLine(transformer, "event: error");
        writeLine(transformer, "data: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证包含 OpenAI 错误格式
        assertThat(output).contains("\"error\":{\"message\":\"Overloaded\",\"type\":\"error\"}");
        assertThat(output).contains("data: [DONE]");
    }

    @Test
    void completeMethodEmitsFinishReasonAndDoneWhenNoMessageStop() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        AnthropicToOpenAiStreamTransformer transformer =
                new AnthropicToOpenAiStreamTransformer(target, "chatcmpl_test", "claude-3-opus", 1L);

        // message_start + partial text without message_stop
        writeLine(transformer, "event: message_start");
        writeLine(transformer, "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_005\",\"model\":\"claude-3-opus\",\"usage\":{\"input_tokens\":10}}}");
        writeLine(transformer, "");

        writeLine(transformer, "event: content_block_start");
        writeLine(transformer, "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
        writeLine(transformer, "");

        writeLine(transformer, "event: content_block_delta");
        writeLine(transformer, "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"partial\"}}");
        writeLine(transformer, "");

        // 不发送 message_stop，直接调用 complete()
        transformer.complete();

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证补发了 finish_reason 和 [DONE]
        assertThat(output).contains("\"finish_reason\":\"stop\"");
        assertThat(output).contains("data: [DONE]");
    }

    /**
     * 当上游错误地透传了 OpenAI Chat 格式的 [DONE] 哨兵行时，转换器应当静默跳过，
     * 避免在日志中产生 "Unrecognized token 'DONE'" 之类的告警。
     */
    @Test
    void strayOpenAiDoneSentinelIsSkippedSilently() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        AnthropicToOpenAiStreamTransformer transformer =
                new AnthropicToOpenAiStreamTransformer(target, "chatcmpl_test", "claude-3-opus", 1L);

        // 喂入 message_start 确立上下文
        writeLine(transformer, "event: message_start");
        writeLine(transformer, "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_done\",\"model\":\"claude-3-opus\",\"usage\":{\"input_tokens\":5}}}");
        writeLine(transformer, "");

        // 错误地透传了一个 [DONE] 行（上游实际是 OpenAI Chat 协议）
        writeLine(transformer, "data: [DONE]");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // 不应抛出 JSON 解析错误；上游的 [DONE] 必须被忽略
        assertThat(output).doesNotContain("Unrecognized token");
        // 转换器自己还没有 message_stop，所以此时不应已经发出 [DONE]
        assertThat(output).doesNotContain("data: [DONE]");
    }

    private static void writeLine(AnthropicToOpenAiStreamTransformer transformer, String line) throws Exception {
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        transformer.write(bytes);
    }

    private static void assertAllDataLinesAreValidJson(String output) throws Exception {
        for (String block : output.split("\\n\\n")) {
            for (String line : block.split("\\n")) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    if (!"[DONE]".equals(data)) {
                        OBJECT_MAPPER.readTree(data);
                    }
                }
            }
        }
    }
}
