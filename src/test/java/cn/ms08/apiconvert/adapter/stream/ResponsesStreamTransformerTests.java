package cn.ms08.apiconvert.adapter.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsesStreamTransformerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void convertsCumulativeChatChunksToIncrementalResponsesDeltas() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        ResponsesStreamTransformer transformer =
                new ResponsesStreamTransformer(target, "resp_test", "doubao-seed-2.0-code", 1L);
        transformer.sendInitialEvents();

        writeLine(transformer, "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");
        writeLine(transformer, "data: {\"choices\":[{\"delta\":{\"content\":\"hello world\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");
        writeLine(transformer, "data: {\"choices\":[{\"delta\":{\"content\":\"hello world\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");
        writeLine(transformer, "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":2,\"total_tokens\":3}}");
        writeLine(transformer, "");
        transformer.complete();

        String output = target.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("\"delta\":\"hello\"");
        assertThat(output).contains("\"delta\":\" world\"");
        assertThat(output).doesNotContain("\"delta\":\"hello world\"");
        assertThat(output).contains("event: response.completed");
        assertThat(output).contains("\"text\":\"hello world\"");
        assertCompletedPayloadsAreValidJson(output);
    }

    @Test
    void acceptsNativeResponsesSseAndStillEmitsCompletedEvent() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        ResponsesStreamTransformer transformer =
                new ResponsesStreamTransformer(target, "resp_test", "doubao-seed-2.0-code", 1L);
        transformer.sendInitialEvents();

        writeLine(transformer, "event: response.output_text.delta");
        writeLine(transformer, "data: {\"type\":\"response.output_text.delta\",\"delta\":\"hello\"}");
        writeLine(transformer, "");
        writeLine(transformer, "event: response.completed");
        writeLine(transformer, "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"input_tokens\":1,\"output_tokens\":1,\"total_tokens\":2},\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"hello\"}]}]}}");
        writeLine(transformer, "");
        transformer.complete();

        String output = target.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("\"delta\":\"hello\"");
        assertThat(output).contains("event: response.completed");
        assertThat(output).contains("\"input_tokens\":1");
        assertThat(output).contains("\"text\":\"hello\"");
        assertCompletedPayloadsAreValidJson(output);
    }

    @Test
    void convertsChatToolCallDeltasToResponsesFunctionCallEvents() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        ResponsesStreamTransformer transformer =
                new ResponsesStreamTransformer(target, "resp_test", "doubao-seed-2.0-code", 1L);
        transformer.sendInitialEvents();

        writeLine(transformer, "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_123\",\"type\":\"function\",\"function\":{\"name\":\"shell\",\"arguments\":\"{\\\"cmd\\\"\"}}]},\"finish_reason\":null}]}");
        writeLine(transformer, "");
        writeLine(transformer, "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\":\\\"git status\\\"}\"}}]},\"finish_reason\":null}]}");
        writeLine(transformer, "");
        writeLine(transformer, "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}}");
        writeLine(transformer, "");
        transformer.complete();

        String output = target.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("event: response.output_item.added");
        assertThat(output).contains("event: response.function_call_arguments.delta");
        assertThat(output).contains("event: response.function_call_arguments.done");
        assertThat(output).contains("\"type\":\"function_call\"");
        assertThat(output).contains("\"name\":\"shell\"");
        assertThat(output).contains("\"call_id\":\"call_123\"");

        JsonNode completedPayload = assertCompletedPayloadsAreValidJson(output);
        JsonNode outputItems = completedPayload.path("response").path("output");
        assertThat(outputItems.isArray()).isTrue();
        assertThat(outputItems.size()).isEqualTo(1);
        JsonNode functionCall = outputItems.get(0);
        assertThat(functionCall.path("type").asText()).isEqualTo("function_call");
        assertThat(functionCall.path("status").asText()).isEqualTo("completed");
        assertThat(functionCall.path("call_id").asText()).isEqualTo("call_123");
        assertThat(functionCall.path("name").asText()).isEqualTo("shell");
        assertThat(functionCall.path("arguments").asText()).isEqualTo("{\"cmd\":\"git status\"}");
    }

    @Test
    void preservesReasoningBeforeChatToolCallInCompletedResponsesOutput() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        ResponsesStreamTransformer transformer =
                new ResponsesStreamTransformer(target, "resp_test", "deepseek-v4-flash", 1L);
        transformer.sendInitialEvents();

        writeLine(transformer, "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"need a shell command\"},\"finish_reason\":null}]}");
        writeLine(transformer, "");
        writeLine(transformer, "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_123\",\"type\":\"function\",\"function\":{\"name\":\"shell\",\"arguments\":\"{\\\"cmd\\\":\\\"git status\\\"}\"}}]},\"finish_reason\":null}]}");
        writeLine(transformer, "");
        writeLine(transformer, "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,\"total_tokens\":12}}");
        writeLine(transformer, "");
        transformer.complete();

        String output = target.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("event: response.reasoning_item.done");
        assertThat(output).contains("\"summary\":[{\"text\":\"need a shell command\"}]");

        JsonNode completedPayload = assertCompletedPayloadsAreValidJson(output);
        JsonNode outputItems = completedPayload.path("response").path("output");
        assertThat(outputItems).hasSize(2);
        assertThat(outputItems.get(0).path("type").asText()).isEqualTo("reasoning");
        assertThat(outputItems.get(0).path("summary").get(0).path("text").asText()).isEqualTo("need a shell command");
        assertThat(outputItems.get(1).path("type").asText()).isEqualTo("function_call");
        assertThat(outputItems.get(1).path("call_id").asText()).isEqualTo("call_123");
    }

    /**
     * 上游 SSE 在 finish_reason 之后没有发出 usage chunk 时，仅靠 flush() 也必须补发 response.completed，
     * 防止 Codex 客户端报 "stream closed before response.completed" 后断流。
     */
    @Test
    void flushAloneEmitsCompletedEventWhenFinishReasonChunkMissing() throws Exception {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        ResponsesStreamTransformer transformer =
                new ResponsesStreamTransformer(target, "resp_test", "mimo-v2.5-pro", 1L);
        transformer.sendInitialEvents();

        // 模拟 Codex 请求 MiMo Anthropic 路径：上游先发 message_start + content_block_delta + message_delta
        writeLine(transformer, "event: message_start");
        writeLine(transformer, "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_x\",\"model\":\"mimo-v2.5-pro\",\"usage\":{\"input_tokens\":7,\"output_tokens\":0}}}");
        writeLine(transformer, "");
        writeLine(transformer, "event: content_block_start");
        writeLine(transformer, "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
        writeLine(transformer, "");
        writeLine(transformer, "event: content_block_delta");
        writeLine(transformer, "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"hi\"}}");
        writeLine(transformer, "");
        writeLine(transformer, "event: message_delta");
        writeLine(transformer, "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}");
        writeLine(transformer, "");
        // 上游不发 message_stop 直接断流，仅靠 flush() 兜底
        transformer.flush();

        String output = target.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("event: response.completed");
        assertCompletedPayloadsAreValidJson(output);
    }

    private static void writeLine(ResponsesStreamTransformer transformer, String line) throws Exception {
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        transformer.write(bytes);
    }

    private static JsonNode assertCompletedPayloadsAreValidJson(String output) throws Exception {
        int completedEvents = 0;
        JsonNode completedPayload = null;
        for (String block : output.split("\\n\\n")) {
            if (!block.contains("event: response.completed")) {
                continue;
            }
            completedEvents++;
            String data = null;
            for (String line : block.split("\\n")) {
                if (line.startsWith("data: ")) {
                    data = line.substring(6);
                    break;
                }
            }
            assertThat(data).isNotBlank();
            JsonNode payload = OBJECT_MAPPER.readTree(data);
            assertThat(payload.path("type").asText()).isEqualTo("response.completed");
            assertThat(payload.path("response").path("status").asText()).isIn("completed", "failed");
            completedPayload = payload;
        }
        assertThat(completedEvents).isEqualTo(1);
        return completedPayload;
    }
}
