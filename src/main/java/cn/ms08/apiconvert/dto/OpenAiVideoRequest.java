package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI Videos API 请求体，保留未知字段用于透传供应商扩展参数。
 */
public class OpenAiVideoRequest {

    private String model;
    private String prompt;
    private Integer seconds;
    private String size;
    private String quality;
    @JsonProperty("response_format")
    private String responseFormat;
    private String user;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Integer getSeconds() {
        return seconds;
    }

    public void setSeconds(Integer seconds) {
        this.seconds = seconds;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    /**
     * 上游调用前替换为渠道配置的真实模型名，不修改客户端原始请求对象。
     */
    public OpenAiVideoRequest copyForProviderModel(String providerModel) {
        OpenAiVideoRequest copy = new OpenAiVideoRequest();
        copy.setModel(providerModel);
        copy.setPrompt(prompt);
        copy.setSeconds(seconds);
        copy.setSize(size);
        copy.setQuality(quality);
        copy.setResponseFormat(responseFormat);
        copy.setUser(user);
        copy.getAdditionalProperties().putAll(additionalProperties);
        return copy;
    }
}
