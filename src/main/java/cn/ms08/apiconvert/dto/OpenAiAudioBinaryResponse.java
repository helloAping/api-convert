package cn.ms08.apiconvert.dto;

import org.springframework.http.MediaType;

/**
 * OpenAI Audio Speech API 二进制响应封装：上游直接返回音频字节，
 * Content-Type 由 response_format 决定。该对象同时承担 STT 响应的二进制形态。
 */
public class OpenAiAudioBinaryResponse {

    private final byte[] body;
    private final MediaType mediaType;
    private final String filename;

    public OpenAiAudioBinaryResponse(byte[] body, MediaType mediaType, String filename) {
        this.body = body;
        this.mediaType = mediaType;
        this.filename = filename;
    }

    public byte[] getBody() {
        return body;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public String getFilename() {
        return filename;
    }

    /**
     * 按 OpenAI Audio Speech 文档将 response_format 映射为标准 MIME。
     */
    public static MediaType mediaTypeFor(String responseFormat) {
        if (responseFormat == null) {
            return MediaType.valueOf("audio/mpeg");
        }
        return switch (responseFormat.toLowerCase()) {
            case "mp3" -> MediaType.valueOf("audio/mpeg");
            case "opus" -> MediaType.valueOf("audio/ogg");
            case "aac" -> MediaType.valueOf("audio/aac");
            case "flac" -> MediaType.valueOf("audio/flac");
            case "wav" -> MediaType.valueOf("audio/wav");
            case "pcm" -> MediaType.valueOf("audio/pcm");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * 推断文件扩展名。download 场景下浏览器根据 Content-Disposition 决定落盘扩展名。
     */
    public static String extensionFor(String responseFormat) {
        if (responseFormat == null) return "mp3";
        return switch (responseFormat.toLowerCase()) {
            case "mp3" -> "mp3";
            case "opus" -> "opus";
            case "aac" -> "aac";
            case "flac" -> "flac";
            case "wav" -> "wav";
            case "pcm" -> "pcm";
            default -> "bin";
        };
    }
}
