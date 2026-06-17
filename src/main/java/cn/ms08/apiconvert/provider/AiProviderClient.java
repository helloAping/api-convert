package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiAudioBinaryResponse;
import cn.ms08.apiconvert.dto.OpenAiAudioSpeechRequest;
import cn.ms08.apiconvert.dto.OpenAiAudioTranscriptionRequest;
import cn.ms08.apiconvert.dto.OpenAiEmbeddingRequest;
import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuota;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.vo.OpenAiEmbeddingResponse;
import cn.ms08.apiconvert.vo.OpenAiAudioTranscriptionResponse;
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
import org.springframework.http.HttpStatus;

import java.io.OutputStream;
import java.util.List;

/**
 * 供应商客户端接口，每个供应商实现此接口提供对上游 API 的完整调用能力。
 * <p>
 * 三种主要协议各有独立方法：{@link #chat}（OpenAI Chat Completions）、
 * {@link #messages}（Anthropic Messages）、{@link #responses}（OpenAI Responses API）。
 * {@link BaseAiProviderClient} 提供了完整 HTTP 实现和可覆写的钩子方法，子类通常只需覆写钩子。
 *
 * <h3>实现层级</h3>
 * <pre>
 *   AiProviderClient (接口)
 *     └── BaseAiProviderClient (抽象基类, HTTP逻辑+钩子)
 *           ├── OpenAIProviderClient       Chat+Responses+Video+Image
 *           ├── DeepSeekProviderClient     Chat+Anthropic, 钩子定制
 *           ├── VolcCodingPlanProviderClient Chat+Anthropic
 *           ├── OpenCodeProviderClient     Chat+Anthropic
 *           ├── GptAuthProviderClient      Chat+Video+Image, OAuth鉴权
 *           └── ClaudeAuthProviderClient   Anthropic, OAuth鉴权
 *     └── GeminiProviderClient (独立实现, 非OpenAI兼容协议)
 * </pre>
 */
public interface AiProviderClient {

    /** 供应商枚举值。 */
    ProviderType type();

    /**
     * OpenAI Chat Completions 协议调用。
     * 上游路径从渠道能力中按 {@link EndpointType#CHAT_COMPLETIONS} 解析，
     * 鉴权头默认 {@code Authorization: Bearer xxx}，Anthropic 兼容接口可覆写为 {@code x-api-key}。
     */
    UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request);

    /**
     * Anthropic Messages 协议调用。
     * 上游路径从渠道能力中按 {@link EndpointType#ANTHROPIC_MESSAGES} 解析，
     * 鉴权头默认 {@code x-api-key}，自动附加 {@code anthropic-version: 2023-06-01}。
     */
    UnifiedChatResponse messages(ModelRoute route, UnifiedChatRequest request);

    /**
     * OpenAI Responses API 协议调用。
     * 上游路径从渠道能力中按 {@link EndpointType#OPENAI_RESPONSES} 解析，
     * 鉴权头默认 {@code Authorization: Bearer xxx}。
     */
    UnifiedChatResponse responses(ModelRoute route, UnifiedChatRequest request);

    /** Chat Completions 协议 SSE 流式调用。 */
    UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream);

    /** Anthropic Messages 协议 SSE 流式调用。 */
    UnifiedUsage streamMessages(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream);

    /** Responses API 协议 SSE 流式调用。 */
    UnifiedUsage streamResponses(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream);

    /** 是否支持指定协议的流式传输。 */
    boolean supportsStreaming(EndpointType endpointType);

    /** 视频生成，透传到渠道配置的 video 路径。 */
    OpenAiVideoResponse generateVideo(ModelRoute route, OpenAiVideoRequest request);

    /** 图片生成，透传到渠道配置的 image 路径。 */
    OpenAiImageResponse generateImage(ModelRoute route, OpenAiImageRequest request);

    /**
     * 嵌入生成，透传到渠道配置的 embedding 路径。
     * 默认不支持，需要 OpenAI 兼容协议渠道覆盖；非 OpenAI 兼容供应商调用时返回 501。
     */
    default OpenAiEmbeddingResponse embed(ModelRoute route, OpenAiEmbeddingRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "embedding is not supported by provider " + type());
    }

    /**
     * 文本转语音（OpenAI Audio Speech），透传到渠道配置的 audio_speech 路径。
     * 返回二进制音频字节 + Content-Type（按 response_format 解析）。
     */
    default OpenAiAudioBinaryResponse speech(ModelRoute route, OpenAiAudioSpeechRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "audio speech is not supported by provider " + type());
    }

    /**
     * 语音转写（OpenAI Audio Transcriptions / Whisper），multipart 文件直传到渠道配置的 audio_transcriptions 路径。
     * 上传音频文件后返回 verbose_json 格式响应。
     */
    default OpenAiAudioTranscriptionResponse transcribe(ModelRoute route, OpenAiAudioTranscriptionRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "audio transcription is not supported by provider " + type());
    }

    /** 获取供应商可用模型列表。 */
    List<ProviderModel> models(ProviderModelFetchRequest request);

    /** 查询供应商余额/额度。 */
    ProviderQuota quota(ProviderQuotaFetchRequest request);
}
