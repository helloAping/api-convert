package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anthropic Messages 协议中的单条消息，content 可为字符串或内容块数组。
 */
public class AnthropicMessage {

    /**
     * 消息角色，例如 user 或 assistant。
     */
    private String role;
    /**
     * 消息内容，保持 Object 以兼容文本和多模态内容块。
     */
    private Object content;
    /**
     * 透传供应商扩展字段，日志层会统一处理敏感信息脱敏。
     */
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
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
