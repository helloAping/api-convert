package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages 兼容请求体；未知字段会透传给上游以保留 tools 等扩展能力。
 */
public class AnthropicMessageRequest {

    /**
     * 客户端请求的网关对外模型名。
     */
    private String model;
    /**
     * Anthropic 消息数组，通常只包含 user 和 assistant。
     */
    private List<AnthropicMessage> messages;
    /**
     * 系统提示，Anthropic 原生协议位于 messages 外层。
     */
    private Object system;
    /**
     * 是否流式；当前网关仍明确拒绝流式转发。
     */
    private Boolean stream;
    /**
     * 采样温度。
     */
    private Double temperature;
    /**
     * 最大输出 token 数。
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    /**
     * 需要原样透传给上游的其他 Anthropic 字段，不能包含明文密钥。
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<AnthropicMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AnthropicMessage> messages) {
        this.messages = messages;
    }

    public Object getSystem() {
        return system;
    }

    public void setSystem(Object system) {
        this.system = system;
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

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
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
