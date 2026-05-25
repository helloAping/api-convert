package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI Images API 生成请求体，保留未知字段用于兼容供应商扩展参数。
 */
public class OpenAiImageRequest {

    private String model;
    private String prompt;
    private Integer n;
    private String size;
    private String quality;
    private String style;
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

    public Integer getN() {
        return n;
    }

    public void setN(Integer n) {
        this.n = n;
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

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
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
    public OpenAiImageRequest copyForProviderModel(String providerModel) {
        OpenAiImageRequest copy = new OpenAiImageRequest();
        copy.setModel(providerModel);
        copy.setPrompt(prompt);
        copy.setN(n);
        copy.setSize(size);
        copy.setQuality(quality);
        copy.setStyle(style);
        copy.setResponseFormat(responseFormat);
        copy.setUser(user);
        copy.getAdditionalProperties().putAll(additionalProperties);
        return copy;
    }
}
