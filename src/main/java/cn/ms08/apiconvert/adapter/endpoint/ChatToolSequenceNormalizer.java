package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.dto.UnifiedMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 修复 Chat Completions 严格上游要求的 tool_calls/tool 消息相邻关系。
 */
final class ChatToolSequenceNormalizer {

    private ChatToolSequenceNormalizer() {
    }

    /**
     * 将 assistant tool_calls 后面的匹配 tool 结果提前到相邻位置，并裁掉没有结果的调用。
     */
    static List<UnifiedMessage> normalizeForStrictChat(List<UnifiedMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        List<UnifiedMessage> normalized = new ArrayList<>();
        int i = 0;
        while (i < messages.size()) {
            UnifiedMessage message = messages.get(i);
            if (!hasToolCalls(message)) {
                if (!isToolMessage(message)) {
                    normalized.add(message);
                }
                i++;
                continue;
            }

            ToolSequence sequence = collectFollowingToolResults(messages, i);
            List<Object> matchedCalls = matchedToolCalls(sequence.toolCalls(), sequence.foundToolResults().keySet());
            if (!matchedCalls.isEmpty()) {
                normalized.add(withToolCalls(message, matchedCalls));
                for (Object call : matchedCalls) {
                    UnifiedMessage toolMessage = sequence.foundToolResults().get(toolCallId(call));
                    if (toolMessage != null) {
                        normalized.add(toolMessage);
                    }
                }
            } else if (hasContent(message)) {
                normalized.add(withoutToolCalls(message));
            }
            normalized.addAll(sequence.deferredMessages());
            i = sequence.nextIndex();
        }
        return normalized;
    }

    static boolean hasToolCalls(UnifiedMessage message) {
        return "assistant".equals(message.role())
                && message.options() != null
                && message.options().get("tool_calls") instanceof List<?> list
                && !list.isEmpty();
    }

    private static ToolSequence collectFollowingToolResults(List<UnifiedMessage> messages, int assistantIndex) {
        UnifiedMessage assistant = messages.get(assistantIndex);
        List<Object> toolCalls = toolCalls(assistant);
        Set<String> expectedIds = new LinkedHashSet<>();
        for (Object call : toolCalls) {
            String id = toolCallId(call);
            if (StringUtils.hasText(id)) {
                expectedIds.add(id);
            }
        }

        Map<String, UnifiedMessage> found = new LinkedHashMap<>();
        List<UnifiedMessage> deferred = new ArrayList<>();
        int cursor = assistantIndex + 1;
        while (cursor < messages.size() && found.size() < expectedIds.size()) {
            UnifiedMessage candidate = messages.get(cursor);
            if (hasToolCalls(candidate)) {
                break;
            }
            if (isToolMessage(candidate)) {
                String id = toolMessageId(candidate);
                if (expectedIds.contains(id)) {
                    found.putIfAbsent(id, candidate);
                }
            } else {
                deferred.add(candidate);
            }
            cursor++;
        }
        return new ToolSequence(toolCalls, found, deferred, cursor);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toolCalls(UnifiedMessage message) {
        return (List<Object>) message.options().get("tool_calls");
    }

    private static List<Object> matchedToolCalls(List<Object> toolCalls, Set<String> foundIds) {
        List<Object> matched = new ArrayList<>();
        for (Object call : toolCalls) {
            if (foundIds.contains(toolCallId(call))) {
                matched.add(call);
            }
        }
        return matched;
    }

    private static UnifiedMessage withToolCalls(UnifiedMessage message, List<Object> toolCalls) {
        Map<String, Object> options = message.options() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(message.options());
        options.put("tool_calls", toolCalls);
        return new UnifiedMessage(message.role(), message.content(), message.name(), "tool_calls", options);
    }

    private static UnifiedMessage withoutToolCalls(UnifiedMessage message) {
        Map<String, Object> options = message.options() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(message.options());
        options.remove("tool_calls");
        return new UnifiedMessage(message.role(), message.content(), message.name(), null,
                options.isEmpty() ? null : options);
    }

    private static boolean isToolMessage(UnifiedMessage message) {
        return "tool".equals(message.role());
    }

    private static String toolMessageId(UnifiedMessage message) {
        if (message.options() == null) {
            return null;
        }
        Object id = message.options().get("tool_call_id");
        return id == null ? null : String.valueOf(id);
    }

    private static String toolCallId(Object toolCall) {
        if (toolCall instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return id == null ? null : String.valueOf(id);
        }
        return null;
    }

    private static boolean hasContent(UnifiedMessage message) {
        Object content = message.content();
        if (content == null) {
            return false;
        }
        if (content instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (content instanceof List<?> list) {
            return !list.isEmpty();
        }
        return true;
    }

    private record ToolSequence(
            List<Object> toolCalls,
            Map<String, UnifiedMessage> foundToolResults,
            List<UnifiedMessage> deferredMessages,
            int nextIndex
    ) {
    }
}
