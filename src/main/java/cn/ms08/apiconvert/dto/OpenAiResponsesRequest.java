package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 兼容请求体；按 OpenAI 标准规范映射所有常用参数。
 */
public class OpenAiResponsesRequest {

    private String model;
    private Object input;
    private String instructions;
    private Boolean stream;
    private Double temperature;
    @JsonProperty("top_p")
    private Double topP;
    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;
    private List<Map<String, Object>> tools;
    @JsonProperty("tool_choice")
    private Object toolChoice;
    private Map<String, Object> reasoning;
    @JsonProperty("previous_response_id")
    private String previousResponseId;
    private String truncation;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Object getInput() { return input; }
    public void setInput(Object input) { this.input = input; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getTopP() { return topP; }
    public void setTopP(Double topP) { this.topP = topP; }

    public Integer getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(Integer maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    public List<Map<String, Object>> getTools() { return tools; }
    public void setTools(List<Map<String, Object>> tools) { this.tools = tools; }

    public Object getToolChoice() { return toolChoice; }
    public void setToolChoice(Object toolChoice) { this.toolChoice = toolChoice; }

    public Map<String, Object> getReasoning() { return reasoning; }
    public void setReasoning(Map<String, Object> reasoning) { this.reasoning = reasoning; }

    public String getPreviousResponseId() { return previousResponseId; }
    public void setPreviousResponseId(String previousResponseId) { this.previousResponseId = previousResponseId; }

    public String getTruncation() { return truncation; }
    public void setTruncation(String truncation) { this.truncation = truncation; }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) { additionalProperties.put(name, value); }
}
