package cn.ms08.apiconvert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * OpenAI Audio Transcriptions API 请求体，multipart/form-data 形态；
 * 网关收到客户端 multipart 请求后用 {@code fileBytes + filename} 转发到上游，避免 Spring 直接二次 multipart 序列化。
 */
public class OpenAiAudioTranscriptionRequest {

    private String model;
    private byte[] fileBytes;
    private String filename;
    private String contentType;
    private String language;
    private String prompt;
    @JsonProperty("response_format")
    private String responseFormat;
    private Double temperature;
    @JsonProperty("timestamp_granularities[]")
    private List<String> timestampGranularities;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public byte[] getFileBytes() {
        return fileBytes;
    }

    public void setFileBytes(byte[] fileBytes) {
        this.fileBytes = fileBytes;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public List<String> getTimestampGranularities() {
        return timestampGranularities;
    }

    public void setTimestampGranularities(List<String> timestampGranularities) {
        this.timestampGranularities = timestampGranularities;
    }

    /**
     * 从 {@link MultipartFile} 提取字节与文件名，简化 controller → service 的字段搬运。
     */
    public static OpenAiAudioTranscriptionRequest fromMultipart(MultipartFile file, String model) {
        OpenAiAudioTranscriptionRequest req = new OpenAiAudioTranscriptionRequest();
        req.setModel(model);
        if (file != null && !file.isEmpty()) {
            try {
                req.setFileBytes(file.getBytes());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to read uploaded audio file: " + e.getMessage(), e);
            }
            req.setFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "audio");
            req.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        }
        return req;
    }
}
