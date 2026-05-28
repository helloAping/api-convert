package cn.ms08.apiconvert.vo;

import cn.ms08.apiconvert.dto.OpenAiMessage;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiChatCompletionResponse {

    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }

    public Long getCreated() { return created; }
    public void setCreated(Long created) { this.created = created; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<Choice> getChoices() { return choices; }
    public void setChoices(List<Choice> choices) { this.choices = choices; }

    public Usage getUsage() { return usage; }
    public void setUsage(Usage usage) { this.usage = usage; }

    public String getSystemFingerprint() { return systemFingerprint; }
    public void setSystemFingerprint(String systemFingerprint) { this.systemFingerprint = systemFingerprint; }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) { additionalProperties.put(name, value); }

    public static class Choice {
        private Integer index;
        private OpenAiMessage message;
        @JsonProperty("finish_reason")
        private String finishReason;
        private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public OpenAiMessage getMessage() {
            return message;
        }

        public void setMessage(OpenAiMessage message) {
            this.message = message;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
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

    public static class CompletionTokensDetails {
        @JsonProperty("text_tokens")
        private Integer textTokens;
        @JsonProperty("audio_tokens")
        private Integer audioTokens;
        @JsonProperty("reasoning_tokens")
        private Integer reasoningTokens;
        private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

        public Integer getTextTokens() { return textTokens; }
        public void setTextTokens(Integer textTokens) { this.textTokens = textTokens; }
        public Integer getAudioTokens() { return audioTokens; }
        public void setAudioTokens(Integer audioTokens) { this.audioTokens = audioTokens; }
        public Integer getReasoningTokens() { return reasoningTokens; }
        public void setReasoningTokens(Integer reasoningTokens) { this.reasoningTokens = reasoningTokens; }

        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String name, Object value) {
            additionalProperties.put(name, value);
        }
    }

    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
        @JsonProperty("prompt_tokens_details")
        private TokenDetails promptTokensDetails;
        @JsonProperty("cached_tokens")
        private Integer cachedTokens;
        @JsonProperty("cache_read_input_tokens")
        private Integer cacheReadInputTokens;
        @JsonProperty("completion_tokens_details")
        private CompletionTokensDetails completionTokensDetails;
        @JsonProperty("prompt_cache_hit_tokens")
        private Integer promptCacheHitTokens;
        private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

        public Integer getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(Integer promptTokens) {
            this.promptTokens = promptTokens;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public void setCompletionTokens(Integer completionTokens) {
            this.completionTokens = completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Integer totalTokens) {
            this.totalTokens = totalTokens;
        }

        public TokenDetails getPromptTokensDetails() {
            return promptTokensDetails;
        }

        public void setPromptTokensDetails(TokenDetails promptTokensDetails) {
            this.promptTokensDetails = promptTokensDetails;
        }

        public Integer getCachedTokens() {
            return cachedTokens;
        }

        public void setCachedTokens(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
        }

        public Integer getCacheReadInputTokens() {
            return cacheReadInputTokens;
        }

        public void setCacheReadInputTokens(Integer cacheReadInputTokens) {
            this.cacheReadInputTokens = cacheReadInputTokens;
        }

        public Integer getPromptCacheHitTokens() {
            return promptCacheHitTokens;
        }

        public void setPromptCacheHitTokens(Integer promptCacheHitTokens) {
            this.promptCacheHitTokens = promptCacheHitTokens;
        }

        public CompletionTokensDetails getCompletionTokensDetails() { return completionTokensDetails; }
        public void setCompletionTokensDetails(CompletionTokensDetails completionTokensDetails) { this.completionTokensDetails = completionTokensDetails; }

        /**
         * 保留未识别的供应商用量扩展字段，便于后续补充更多 token 统计。
         */
        @JsonAnyGetter
        public Map<String, Object> getAdditionalProperties() {
            return additionalProperties;
        }

        @JsonAnySetter
        public void setAdditionalProperty(String name, Object value) {
            additionalProperties.put(name, value);
        }
    }

    /**
     * OpenAI 兼容 usage.prompt_tokens_details 中的输入 token 细分。
     */
    public static class TokenDetails {
        @JsonProperty("cached_tokens")
        private Integer cachedTokens;
        @JsonProperty("audio_tokens")
        private Integer audioTokens;
        @JsonProperty("video_tokens")
        private Integer videoTokens;
        private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

        public Integer getCachedTokens() {
            return cachedTokens;
        }

        public void setCachedTokens(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
        }

        public Integer getAudioTokens() {
            return audioTokens;
        }

        public void setAudioTokens(Integer audioTokens) {
            this.audioTokens = audioTokens;
        }

        public Integer getVideoTokens() {
            return videoTokens;
        }

        public void setVideoTokens(Integer videoTokens) {
            this.videoTokens = videoTokens;
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
}
