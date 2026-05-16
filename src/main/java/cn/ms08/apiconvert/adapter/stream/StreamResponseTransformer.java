package cn.ms08.apiconvert.adapter.stream;

import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 流式响应转换器，将上游 SSE 流实时转换为端点协议兼容的 SSE 格式。
 * <p>
 * 参考 CLIProxyAPI 的 {@code ResponseStreamTransform} 设计。当流式请求的端点协议
 * 与上游供应商协议不一致时，由该转换器在 {@code ChatGatewayService} 的流式路径中
 * 自动包装输出流，逐 chunk 完成协议转换。
 * </p>
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@link #wrap(OutputStream, String, String, long)} — 包装输出流</li>
 *   <li>{@link WrappedStream#sendInitialEvents()} — 发送初始事件（可选）</li>
 *   <li>上游 SSE 数据写入 {@link WrappedStream#outputStream()} — 实时转换</li>
 *   <li>{@link WrappedStream#complete()} — 发送完成事件（可选）</li>
 * </ol>
 */
public interface StreamResponseTransformer {

    /**
     * 判断此转换器是否能处理指定的 (端点, 供应商) 组合。
     */
    boolean supports(EndpointType endpoint, ProviderType provider);

    /**
     * 包装输出流，返回的 {@link WrappedStream} 负责将上游 SSE 转换为目标协议格式。
     *
     * @param target     真实的 HTTP 响应输出流
     * @param responseId 响应 ID（如 resp_xxx）
     * @param model      模型名
     * @param createdAt  创建时间戳（秒）
     * @return 包装后的流访问器
     */
    WrappedStream wrap(OutputStream target, String responseId, String model, long createdAt);

    /**
     * 包装流的访问接口，提供输出流和生命周期事件方法。
     */
    interface WrappedStream {

        /**
         * 经过包装的输出流，上游 SSE 数据写入此流完成实时转换。
         */
        OutputStream outputStream();

        /**
         * 发送初始 SSE 事件（如 response.created / response.in_progress）。
         * 必须在开始写入上游 SSE 数据前调用。
         */
        default void sendInitialEvents() throws IOException {
        }

        /**
         * 完成响应发送，如发送 response.completed 事件。
         * 必须在上游 SSE 数据全部写入后调用。
         */
        default void complete() throws IOException {
        }

        /**
         * 写入错误事件（如上游调用失败时的 error + response.completed failed）。
         */
        default void writeErrorEvent(String message) throws IOException {
        }
    }
}
