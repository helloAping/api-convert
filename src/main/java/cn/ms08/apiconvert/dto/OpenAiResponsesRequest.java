package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 兼容请求体；input/instructions 等特有字段显式提取，其余通过透传保留。
 */
public class OpenAiResponsesRequest {

    /**
     * 客户端请求的模型名。
     */
    private String model;
    /**
     * 输入内容，可以是 String 或 List&lt;Object&gt; content 数组。
     */
    private Object input;
    /**
     * 系统指令，Responses API 中位于 messages 之外的顶层字段。
     */
    private String instructions;
    /**
     * 是否流式返回。
     */
    private Boolean stream;
    /**
     * 采样温度。
     */
    private Double temperature;
    /**
     * 最大输出 token 数。
     */
    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;
    /**
     * 需要原样透传给上游的其他 Responses API 字段，不能包含明文密钥。
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Object getInput() {
        return input;
    }

    public void setInput(Object input) {
        this.input = input;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }
}
