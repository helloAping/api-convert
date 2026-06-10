package cn.ms08.apiconvert.adapter.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiToAnthropicStreamTransformerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void convertsOpenAiTextStreamToAnthropicEvents() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        OpenAiToAnthropicStreamTransformer transformer =
                new OpenAiToAnthropicStreamTransformer(target, "msg_test", "gpt-4o", 1L);

        // role chunk
        writeLine(transformer, "data: {\"id\":\"chatcmpl_001\",\"object\":\"chat.completion.chunk\",\"created\":1,\"model\":\"gpt-4o\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");

        // content deltas
        writeLine(transformer, "data: {\"id\":\"chatcmpl_001\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");

        writeLine(transformer, "data: {\"id\":\"chatcmpl_001\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" world\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");

        // finish
        writeLine(transformer, "data: {\"id\":\"chatcmpl_001\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}");
        writeLine(transformer, "");

        // [DONE]
        writeLine(transformer, "data: [DONE]");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证包含 message_start
        assertThat(output).contains("event: message_start");
        assertThat(output).contains("\"type\":\"message_start\"");
        assertThat(output).contains("\"role\":\"assistant\"");
        assertThat(output).contains("\"model\":\"gpt-4o\"");

        // 验证包含 content_block_start (text)
        assertThat(output).contains("event: content_block_start");
        assertThat(output).contains("\"type\":\"text\"");

        // 验证包含 text_delta
        assertThat(output).contains("event: content_block_delta");
        assertThat(output).contains("\"type\":\"text_delta\"");
        assertThat(output).contains("\"text\":\"Hello\"");
        assertThat(output).contains("\"text\":\" world\"");

        // 验证包含 content_block_stop
        assertThat(output).contains("event: content_block_stop");

        // 验证包含 message_delta with stop_reason
        assertThat(output).contains("event: message_delta");
        assertThat(output).contains("\"stop_reason\":\"end_turn\"");
        assertThat(output).contains("\"output_tokens\":5");

        // 验证包含 message_stop
        assertThat(output).contains("event: message_stop");

        // 验证所有 data 行都是合法 JSON
        assertAllDataLinesAreValidJson(output);
    }

    @Test
    void convertsOpenAiToolCallsToAnthropicToolUse() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        OpenAiToAnthropicStreamTransformer transformer =
                new OpenAiToAnthropicStreamTransformer(target, "msg_test", "gpt-4o", 1L);

        // tool call start
        writeLine(transformer, "data: {\"id\":\"chatcmpl_002\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_abc\",\"type\":\"function\",\"function\":{\"name\":\"shell\",\"arguments\":\"\"}}]},\"finish_reason\":null}]}");
        writeLine(transformer, "");

        // tool call arguments
        writeLine(transformer, "data: {\"id\":\"chatcmpl_002\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"cmd\\\":\\\"ls\\\"}\"}}]},\"finish_reason\":null}]}");
        writeLine(transformer, "");

        // finish with tool_calls
        writeLine(transformer, "data: {\"id\":\"chatcmpl_002\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":8,\"total_tokens\":28}}");
        writeLine(transformer, "");

        writeLine(transformer, "data: [DONE]");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证包含 message_start
        assertThat(output).contains("event: message_start");

        // 验证包含 tool_use content_block_start
        assertThat(output).contains("\"type\":\"tool_use\"");
        assertThat(output).contains("\"id\":\"call_abc\"");
        assertThat(output).contains("\"name\":\"shell\"");

        // 验证包含 input_json_delta
        assertThat(output).contains("\"type\":\"input_json_delta\"");
        assertThat(output).contains("\"partial_json\":\"{\\\"cmd\\\":\\\"ls\\\"}\"");

        // 验证 stop_reason 为 tool_use
        assertThat(output).contains("\"stop_reason\":\"tool_use\"");

        // 验证包含 message_stop
        assertThat(output).contains("event: message_stop");
    }

    @Test
    void convertsOpenAiReasoningContentToAnthropicThinking() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        OpenAiToAnthropicStreamTransformer transformer =
                new OpenAiToAnthropicStreamTransformer(target, "msg_test", "deepseek-chat", 1L);

        // reasoning_content delta
        writeLine(transformer, "data: {\"id\":\"chatcmpl_003\",\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"Let me think...\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");

        // content delta
        writeLine(transformer, "data: {\"id\":\"chatcmpl_003\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"The answer is 42.\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");

        // finish
        writeLine(transformer, "data: {\"id\":\"chatcmpl_003\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":10,\"total_tokens\":15}}");
        writeLine(transformer, "");

        writeLine(transformer, "data: [DONE]");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证包含 thinking content_block_start
        assertThat(output).contains("\"type\":\"thinking\"");

        // 验证包含 thinking_delta
        assertThat(output).contains("\"type\":\"thinking_delta\"");
        assertThat(output).contains("\"thinking\":\"Let me think...\"");

        // 验证包含常规文本
        assertThat(output).contains("\"type\":\"text_delta\"");
        assertThat(output).contains("\"text\":\"The answer is 42.\"");

        // 验证完成
        assertThat(output).contains("\"stop_reason\":\"end_turn\"");
        assertThat(output).contains("event: message_stop");
    }

    @Test
    void emitsErrorEventOnUpstreamError() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        OpenAiToAnthropicStreamTransformer transformer =
                new OpenAiToAnthropicStreamTransformer(target, "msg_test", "gpt-4o", 1L);

        writeLine(transformer, "data: {\"error\":{\"message\":\"Rate limit exceeded\",\"type\":\"rate_limit_error\"}}");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证包含 Anthropic 格式的错误事件
        assertThat(output).contains("event: error");
        assertThat(output).contains("\"type\":\"error\"");
        assertThat(output).contains("\"message\":\"Rate limit exceeded\"");
    }

    @Test
    void completeMethodEmitsMessageStopWhenNoDone() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        OpenAiToAnthropicStreamTransformer transformer =
                new OpenAiToAnthropicStreamTransformer(target, "msg_test", "gpt-4o", 1L);

        // partial stream without [DONE]
        writeLine(transformer, "data: {\"id\":\"chatcmpl_004\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"partial\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");

        transformer.complete();

        String output = target.toString(StandardCharsets.UTF_8);

        // 验证补发了 message_delta 和 message_stop
        assertThat(output).contains("event: message_delta");
        assertThat(output).contains("\"stop_reason\":\"end_turn\"");
        assertThat(output).contains("event: message_stop");
    }

    @Test
    void handlesFinishReasonLength() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        OpenAiToAnthropicStreamTransformer transformer =
                new OpenAiToAnthropicStreamTransformer(target, "msg_test", "gpt-4o", 1L);

        writeLine(transformer, "data: {\"id\":\"chatcmpl_005\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"truncated\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");

        writeLine(transformer, "data: {\"id\":\"chatcmpl_005\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"length\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":100,\"total_tokens\":110}}");
        writeLine(transformer, "");

        writeLine(transformer, "data: [DONE]");
        writeLine(transformer, "");

        String output = target.toString(StandardCharsets.UTF_8);

        // Anthropic 用 max_tokens 表示长度限制
        assertThat(output).contains("\"stop_reason\":\"max_tokens\"");
    }

    private static void writeLine(OpenAiToAnthropicStreamTransformer transformer, String line) throws Exception {
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
