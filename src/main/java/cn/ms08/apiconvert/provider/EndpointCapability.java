package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.dto.UnifiedUsage;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
import org.springframework.http.HttpStatus;

import java.io.OutputStream;

/**
 * 端点能力接口，封装一个端点类型的完整上游请求实现。
 * 每个能力对应一种协议（Chat Completions / Anthropic Messages / Responses API 等），
 * 供应商通过 {@link AiProviderClient#capabilities()} 声明自己支持哪些端点能力。
 *
 * <p>Base 实现类提供完整的默认逻辑，子供应商通过组合 + 方法覆盖定制差异化行为。</p>
 */
public interface EndpointCapability {

    /**
     * 向该端点对应的上游发送非流式请求。
     */
    UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request);

    /**
     * 该端点是否支持 SSE 流式透传。
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 向该端点对应的上游发送流式请求，SSE 字节流直接写入 outputStream。
     */
    default UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "stream is not supported for this capability");
    }

    /**
     * 视频生成（仅 Chat Completions 端点支持）。
     */
    default OpenAiVideoResponse generateVideo(ModelRoute route, OpenAiVideoRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "video generation is not supported for this capability");
    }

    /**
     * 图片生成（仅 Chat Completions 端点支持）。
     */
    default OpenAiImageResponse generateImage(ModelRoute route, OpenAiImageRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "image generation is not supported for this capability");
    }
}
