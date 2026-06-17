package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Embeddings API 请求体，input 字段同时支持单字符串和字符串数组，
 * encoding_format / dimensions / user 透传供应商扩展参数。
 */
public class OpenAiEmbeddingRequest {

    private String model;
    /**
     * 兼容 OpenAI 协议：input 可以是单字符串或字符串数组；上游支持 token 数组输入时由
     * 附加属性透传，保持与官方协议一致。
     */
    private Object input;
    @JsonProperty("encoding_format")
    private String encodingFormat;
    private Integer dimensions;
    private String user;
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

    public String getEncodingFormat() {
        return encodingFormat;
    }

    public void setEncodingFormat(String encodingFormat) {
        this.encodingFormat = encodingFormat;
    }

    public Integer getDimensions() {
        return dimensions;
    }

    public void setDimensions(Integer dimensions) {
        this.dimensions = dimensions;
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
     * 规范化为非空字符串数组，便于日志和上游请求体序列化；
     * 客户端传单字符串时包装为单元素数组，null 视为空数组。
     */
    public List<String> normalizedInput() {
        if (input == null) {
            return List.of();
        }
        if (input instanceof String text) {
            return text.isBlank() ? List.of() : List.of(text);
        }
        if (input instanceof List<?> raw) {
            return raw.stream()
                    .map(String::valueOf)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of(String.valueOf(input));
    }

    /**
     * 上游调用前替换为渠道配置的真实模型名，不修改客户端原始请求对象。
     */
    public OpenAiEmbeddingRequest copyForProviderModel(String providerModel) {
        OpenAiEmbeddingRequest copy = new OpenAiEmbeddingRequest();
        copy.setModel(providerModel);
        copy.setInput(input);
        copy.setEncodingFormat(encodingFormat);
        copy.setDimensions(dimensions);
        copy.setUser(user);
        copy.getAdditionalProperties().putAll(additionalProperties);
        return copy;
    }
}
