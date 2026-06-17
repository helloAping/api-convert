package cn.ms08.apiconvert.vo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Audio Transcriptions API verbose_json 响应体，对应 Whisper 模型输出。
 * 客户端通过 {@code response_format=verbose_json} 显式请求该结构；其他格式（json / text / srt / vtt）由网关按需转换。
 */
public class OpenAiAudioTranscriptionResponse {

    private String task;
    private String language;
    private Double duration;
    private String text;
    private List<Word> words;
    private List<Segment> segments;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<Word> getWords() {
        return words;
    }

    public void setWords(List<Word> words) {
        this.words = words;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public void setSegments(List<Segment> segments) {
        this.segments = segments;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }

    public static class Word {
        private String word;
        private Double start;
        private Double end;
        private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public Double getStart() {
            return start;
        }

        public void setStart(Double start) {
            this.start = start;
        }

        public Double getEnd() {
            return end;
        }

        public void setEnd(Double end) {
            this.end = end;
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

    public static class Segment {
        private Integer id;
        private Double start;
        private Double end;
        private String text;
        private List<Integer> tokens;
        private Double temperature;
        private Double avgLogprob;
        @JsonProperty("compression_ratio")
        private Double compressionRatio;
        @JsonProperty("no_speech_prob")
        private Double noSpeechProb;
        private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public Double getStart() {
            return start;
        }

        public void setStart(Double start) {
            this.start = start;
        }

        public Double getEnd() {
            return end;
        }

        public void setEnd(Double end) {
            this.end = end;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public List<Integer> getTokens() {
            return tokens;
        }

        public void setTokens(List<Integer> tokens) {
            this.tokens = tokens;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getAvgLogprob() {
            return avgLogprob;
        }

        public void setAvgLogprob(Double avgLogprob) {
            this.avgLogprob = avgLogprob;
        }

        public Double getCompressionRatio() {
            return compressionRatio;
        }

        public void setCompressionRatio(Double compressionRatio) {
            this.compressionRatio = compressionRatio;
        }

        public Double getNoSpeechProb() {
            return noSpeechProb;
        }

        public void setNoSpeechProb(Double noSpeechProb) {
            this.noSpeechProb = noSpeechProb;
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
