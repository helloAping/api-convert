package cn.ms08.apiconvert.vo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Images API 响应体，保留未知字段以兼容不同供应商的图片生成结构。
 */
public class OpenAiImageResponse {

    private Long created;
    private List<Map<String, Object>> data;
    private String model;
    private Object usage;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Object getUsage() {
        return usage;
    }

    public void setUsage(Object usage) {
        this.usage = usage;
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
