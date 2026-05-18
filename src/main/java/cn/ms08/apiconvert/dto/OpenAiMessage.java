package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 消息对象。
 * tool_calls、tool_call_id、reasoning_content 按 OpenAI 标准显式声明，
 * 非标准字段通过 additionalProperties 透传。
 */
public class OpenAiMessage {

    private String role;
    private Object content;
    private String name;
    @JsonProperty("tool_calls")
    private List<Map<String, Object>> toolCalls;
    @JsonProperty("tool_call_id")
    private String toolCallId;
    @JsonProperty("reasoning_content")
    private String reasoningContent;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Object getContent() { return content; }
    public void setContent(Object content) { this.content = content; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Map<String, Object>> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getReasoningContent() { return reasoningContent; }
    public void setReasoningContent(String reasoningContent) { this.reasoningContent = reasoningContent; }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) { additionalProperties.put(name, value); }
}
