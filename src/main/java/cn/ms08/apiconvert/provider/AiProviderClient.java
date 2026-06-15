package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.dto.ModelRoute;
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
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
import org.springframework.http.HttpStatus;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * 供应商特定适配边界，负责对话转发和模型发现。
 * 供应商通过 {@link #capabilities()} 声明自己支持哪些端点能力。
 */
public interface AiProviderClient {

    /**
     * 当前客户端处理的供应商类型，ProviderClientRegistry 会据此路由。
     */
    ProviderType type();

    /**
     * 返回该供应商支持的端点能力映射。
     * Key 为端点类型，Value 为该端点的能力实现。
     */
    default Map<EndpointType, EndpointCapability> capabilities() {
        return Map.of();
    }

    /**
     * 将标准化对话请求发送到上游供应商。
     * @deprecated 请使用 {@link #capabilities()} 按端点分发。
     */
    default UnifiedChatResponse chat(ModelRoute route, UnifiedChatRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "chat() not supported for provider type " + type());
    }

    /**
     * 当前供应商客户端是否支持直接透传上游 SSE 流。
     * @deprecated 请使用 {@link #capabilities()} 按端点分发。
     */
    default boolean supportsStreaming() {
        return false;
    }

    /**
     * 将流式对话请求发送到上游，把 SSE 字节流直接写回调用方，并在上游返回时提取 token 用量。
     * @deprecated 请使用 {@link #capabilities()} 按端点分发。
     */
    default UnifiedUsage streamChat(ModelRoute route, UnifiedChatRequest request, OutputStream outputStream) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "streamChat() not supported for provider type " + type());
    }

    /**
     * 向支持 OpenAI Videos API 的供应商发起视频生成请求；未实现的供应商默认返回不支持。
     */
    default OpenAiVideoResponse generateVideo(ModelRoute route, OpenAiVideoRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "video generation is not supported for provider type " + type());
    }

    /**
     * 向支持 OpenAI Images API 的供应商发起图片生成请求；未实现的供应商默认返回不支持。
     */
    default OpenAiImageResponse generateImage(ModelRoute route, OpenAiImageRequest request) {
        throw new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST,
                "image generation is not supported for provider type " + type());
    }

    /**
     * 使用供应商特定的鉴权方式和响应解析逻辑获取上游模型选项。
     */
    List<ProviderModel> models(ProviderModelFetchRequest request);

    /**
     * 实时查询供应商额度；结果仅返回给管理端，不写入数据库。
     */
    ProviderQuota quota(ProviderQuotaFetchRequest request);
}
