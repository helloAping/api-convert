package cn.ms08.apiconvert.config;

import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.vo.OpenAiChatCompletionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeConfigTests {

    @Test
    void objectMapperAcceptsLargeBase64String() throws Exception {
        ObjectMapper objectMapper = new DateTimeConfig("Asia/Shanghai", 100_000_000).objectMapper();
        String payload = "a".repeat(20_000_001);

        OpenAiVideoRequest request = objectMapper.readValue(
                "{\"model\":\"video-model\",\"video\":\"" + payload + "\"}",
                OpenAiVideoRequest.class
        );

        assertThat(request.getAdditionalProperties().get("video")).isEqualTo(payload);
    }

    @Test
    void objectMapperIgnoresProviderChatCompletionExtensionFields() throws Exception {
        ObjectMapper objectMapper = new DateTimeConfig("Asia/Shanghai", 100_000_000).objectMapper();

        OpenAiChatCompletionResponse response = objectMapper.readValue("""
                {
                  "id": "chatcmpl-video",
                  "object": "chat.completion",
                  "created": 1710000000,
                  "model": "video-recognition",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": [
                          {"type": "text", "text": "video summary"}
                        ],
                        "provider_message_meta": {"video": true}
                      },
                      "finish_reason": "stop",
                      "logprobs": null,
                      "native_finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 5,
                    "total_tokens": 15,
                    "prompt_tokens_details": {
                      "cached_tokens": 1,
                      "audio_tokens": 2,
                      "video_tokens": 3
                    },
                    "completion_tokens_details": {
                      "reasoning_tokens": 0,
                      "accepted_prediction_tokens": 4
                    }
                  },
                  "provider_response_meta": {"trace_id": "trace-1"}
                }
                """, OpenAiChatCompletionResponse.class);

        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getChoices().getFirst().getMessage().getContent()).isInstanceOf(List.class);
        assertThat(response.getChoices().getFirst().getAdditionalProperties()).containsKey("logprobs");
        assertThat(response.getUsage().getPromptTokensDetails().getCachedTokens()).isEqualTo(1);
        assertThat(response.getUsage().getPromptTokensDetails().getAudioTokens()).isEqualTo(2);
        assertThat(response.getUsage().getPromptTokensDetails().getVideoTokens()).isEqualTo(3);
        assertThat(response.getUsage().getCompletionTokensDetails().getAdditionalProperties())
                .containsEntry("accepted_prediction_tokens", 4);
    }
}
