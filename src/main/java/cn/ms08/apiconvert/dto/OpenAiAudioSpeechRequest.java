package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI Audio Speech API 请求体，对应 POST /v1/audio/speech；
 * 上游返回二进制音频字节（mp3 / opus / aac / flac / wav / pcm），按 {@code response_format} 解析 Content-Type。
 * input 最大 4096 字符，voice 仅支持 alloy / echo / fable / onyx / nova / shimmer 六种预置音色。
 */
public class OpenAiAudioSpeechRequest {

    /** 默认模型，OpenAI 官方为 tts-1 / tts-1-hd；兼容供应商可声明自己的模型 ID。 */
    private String model;
    /** 待合成文本，最长 4096 字符。 */
    private String input;
    /** 必填，预置音色枚举之一。 */
    private String voice;
    /** 输出格式，可选 mp3 / opus / aac / flac / wav / pcm，默认 mp3。 */
    @JsonProperty("response_format")
    private String responseFormat;
    /** 语速倍率，可选 0.25 ~ 4.0，默认 1.0。 */
    private Float speed;
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public Float getSpeed() {
        return speed;
    }

    public void setSpeed(Float speed) {
        this.speed = speed;
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
    public OpenAiAudioSpeechRequest copyForProviderModel(String providerModel) {
        OpenAiAudioSpeechRequest copy = new OpenAiAudioSpeechRequest();
        copy.setModel(providerModel);
        copy.setInput(input);
        copy.setVoice(voice);
        copy.setResponseFormat(responseFormat);
        copy.setSpeed(speed);
        copy.getAdditionalProperties().putAll(additionalProperties);
        return copy;
    }
}
