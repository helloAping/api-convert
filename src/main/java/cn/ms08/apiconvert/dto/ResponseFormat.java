package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * OpenAI response_format 对象，支持 text / json_object / json_schema 三种模式。
 * json_schema 类型时 schema 结构以 Map 形式透传，不引入严格校验。
 */
public record ResponseFormat(
        String type,
        @JsonProperty("json_schema")
        Map<String, Object> jsonSchema
) {
    /**
     * 是否启用 JSON 输出模式。
     */
    public boolean isJson() {
        return "json_object".equals(type) || "json_schema".equals(type);
    }
}
