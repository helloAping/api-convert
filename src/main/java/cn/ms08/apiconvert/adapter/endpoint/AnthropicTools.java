package cn.ms08.apiconvert.adapter.endpoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI → Anthropic 工具参数转换共享工具方法。
 * <p>
 * 被 {@link ResponsesToAnthropicAdapter} 和 {@link ChatCompletionsToAnthropicAdapter}
 * 共用，避免在两个适配器中维护重复的格式转换逻辑。
 */
final class AnthropicTools {

    private AnthropicTools() {
    }

    /**
     * 将 OpenAI/Responses 端点的图片内容块转为 Anthropic image 内容块。
     * <p>
     * 输入格式：
     * <ul>
     *   <li>Chat: {@code {type:"image_url", image_url:{url:"data:image/png;base64,..."}}}</li>
     *   <li>Responses: {@code {type:"input_image", image_url:"data:image/png;base64,..."}}</li>
     * </ul>
     * 输出 data: URL：{@code {type:"image", source:{type:"base64", media_type:"image/png", data:"..."}}}<br>
     * 输出 https URL：{@code {type:"image", source:{type:"url", url:"https://..."}}}
     *
     * @return 转换后的 Anthropic image 块；若 part 中没有可解析 URL 则返回 null
     */
    static Map<String, Object> convertImageToAnthropic(Map<?, ?> part) {
        Object imageUrl = part.get("image_url");
        Object url = imageUrl instanceof Map<?, ?> map ? map.get("url") : imageUrl;
        if (url == null) {
            url = part.get("url");
        }
        if (url == null) {
            return null;
        }
        String urlStr = String.valueOf(url);
        Map<String, Object> source = urlStr.startsWith("data:")
                ? parseDataUrlSource(urlStr)
                : Map.of("type", "url", "url", urlStr);
        return Map.of("type", "image", "source", source);
    }

    /**
     * 将 data: URL 解析为 Anthropic base64 source 格式。
     * <p>
     * {@code data:image/png;base64,iVBOR...} 转为
     * {@code {type:"base64", media_type:"image/png", data:"iVBOR..."}}
     */
    static Map<String, Object> parseDataUrlSource(String dataUrl) {
        int commaIndex = dataUrl.indexOf(',');
        if (commaIndex < 0) {
            return Map.of("type", "url", "url", dataUrl);
        }
        String header = dataUrl.substring(5, commaIndex); // skip "data:"
        String data = dataUrl.substring(commaIndex + 1);
        String mediaType = "application/octet-stream";
        int semicolonIndex = header.indexOf(';');
        if (semicolonIndex > 0) {
            mediaType = header.substring(0, semicolonIndex);
        } else if (!header.isEmpty()) {
            mediaType = header;
        }
        return Map.of("type", "base64", "media_type", mediaType, "data", data);
    }

    /**
     * 将 OpenAI 格式的工具列表转为 Anthropic 格式。
     * <p>
     * 输入格式（兼容两种风格）：
     * <ul>
     *   <li>{@code {type:"function", function:{name,description,parameters,strict}}}</li>
     *   <li>{@code {type:"function", name, parameters}}（Responses API 扁平风格）</li>
     * </ul>
     * 输出格式：{@code {name, description, input_schema, strict}}
     */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> convertToolsToAnthropic(List<Object> tools) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object tool : tools) {
            if (!(tool instanceof Map<?, ?> toolMap)) {
                continue;
            }
            if (!"function".equals(String.valueOf(toolMap.get("type")))) {
                continue;
            }
            // Responses API 可能使用扁平结构（无 function 嵌套），Chat Completions 总有 function 嵌套
            Map<?, ?> function = toolMap.get("function") instanceof Map<?, ?> map ? map : toolMap;
            Object name = function.get("name");
            if (name == null) {
                continue;
            }
            Map<String, Object> anthropicTool = new LinkedHashMap<>();
            anthropicTool.put("name", String.valueOf(name));
            if (function.get("description") != null) {
                anthropicTool.put("description", String.valueOf(function.get("description")));
            }
            Object parameters = function.get("parameters");
            anthropicTool.put("input_schema", parameters != null ? parameters
                    : Map.of("type", "object", "properties", Map.of()));
            if (function.get("strict") instanceof Boolean strict) {
                anthropicTool.put("strict", strict);
            }
            result.add(anthropicTool);
        }
        return result;
    }

    /**
     * 将 OpenAI 格式的 tool_choice 转为 Anthropic 格式。
     * <p>
     * 输入 {@code {type:"function", name:"x"}} 或
     * {@code {type:"function", function:{name:"x"}}}
     * → 输出 {@code {type:"tool", name:"x"}}<br>
     * {@code "required"} → {@code {type:"any"}}<br>
     * {@code "none"} → {@code {type:"none"}}<br>
     * 其他 → {@code {type:"auto"}}
     */
    @SuppressWarnings("unchecked")
    static void convertToolChoice(Map<String, Object> rawOptions) {
        Object toolChoice = rawOptions.get("tool_choice");
        if (toolChoice == null) {
            return;
        }
        if (toolChoice instanceof Map<?, ?> tcMap) {
            String type = tcMap.containsKey("type") ? String.valueOf(tcMap.get("type")) : "auto";
            if (!"function".equals(type) && !"tool".equals(type)) {
                return;
            }
            String name = tcMap.containsKey("name") ? String.valueOf(tcMap.get("name")) : null;
            if (name == null && tcMap.get("function") instanceof Map<?, ?> funcMap) {
                name = String.valueOf(funcMap.get("name"));
            }
            rawOptions.put("tool_choice", Map.of("type", "tool", "name", name != null ? name : ""));
            return;
        }
        String type = switch (String.valueOf(toolChoice)) {
            case "required" -> "any";
            case "none" -> "none";
            default -> "auto";
        };
        rawOptions.put("tool_choice", Map.of("type", type));
    }
}
