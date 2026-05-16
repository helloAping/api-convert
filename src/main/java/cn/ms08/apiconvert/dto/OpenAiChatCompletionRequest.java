package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 请求体，按 OpenAI 标准规范映射所有常用参数。
 * 非标准或供应商特有参数通过 additionalProperties 透传。
 */
public class OpenAiChatCompletionRequest {

    private String model;
    private List<OpenAiMessage> messages;
    private Boolean stream;
    @JsonProperty("stream_options")
    private Map<String, Object> streamOptions;
    private Double temperature;
    @JsonProperty("top_p")
    private Double topP;
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    @JsonProperty("max_completion_tokens")
    private Integer maxCompletionTokens;
    private List<String> stop;
    @JsonProperty("response_format")
    private ResponseFormat responseFormat;
    private List<Map<String, Object>> tools;
    @JsonProperty("tool_choice")
    private Object toolChoice;
    private String user;
    @JsonProperty("reasoning_effort")
    private String reasoningEffort;
    private Long seed;
    @JsonProperty("presence_penalty")
    private Double presencePenalty;
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<OpenAiMessage> getMessages() { return messages; }
    public void setMessages(List<OpenAiMessage> messages) { this.messages = messages; }

    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    public Map<String, Object> getStreamOptions() { return streamOptions; }
    public void setStreamOptions(Map<String, Object> streamOptions) { this.streamOptions = streamOptions; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Integer getMaxCompletionTokens() { return maxCompletionTokens; }
    public void setMaxCompletionTokens(Integer maxCompletionTokens) { this.maxCompletionTokens = maxCompletionTokens; }

    public List<String> getStop() { return stop; }
    public void setStop(List<String> stop) { this.stop = stop; }

    public ResponseFormat getResponseFormat() { return responseFormat; }
    public void setResponseFormat(ResponseFormat responseFormat) { this.responseFormat = responseFormat; }

    public List<Map<String, Object>> getTools() { return tools; }
    public void setTools(List<Map<String, Object>> tools) { this.tools = tools; }

    public Object getToolChoice() { return toolChoice; }
    public void setToolChoice(Object toolChoice) { this.toolChoice = toolChoice; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getReasoningEffort() { return reasoningEffort; }
    public void setReasoningEffort(String reasoningEffort) { this.reasoningEffort = reasoningEffort; }

    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }

    public Double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(Double presencePenalty) { this.presencePenalty = presencePenalty; }

    public Double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(Double frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) { additionalProperties.put(name, value); }
}
