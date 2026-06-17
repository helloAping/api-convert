package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.UnifiedChatRequest;
import cn.ms08.apiconvert.dto.UnifiedChatResponse;
import cn.ms08.apiconvert.endpoint.EndpointType;

/**
 * 供应商特化 hook，按供应商类型注册，与 {@link EndpointProviderAdapter}（跨协议适配器）正交。
 * <p>
 * 两层职责拆分：
 * <ul>
 *   <li><b>跨协议适配器</b>：按 (源端点, 目标端点) 索引，处理跨协议格式转换——如
 *   {@code ChatCompletionsToAnthropicAdapter} 负责把 Chat Completions 请求体转成 Anthropic 格式。
 *   这一层不感知具体供应商。</li>
 *   <li><b>ProviderHook（本接口）</b>：按 {@link cn.ms08.apiconvert.provider.ProviderType} 索引，
 *   处理供应商特有的请求/响应特化——如 DeepSeek Chat 的
 *   {@code reasoning_content=""} 兜底、DeepSeek Anthropic 的 {@code thinking} 块补全。
 *   这一层不感知端点协议转换，但需要知道当前处理的源端点类型以便按形态做特化。</li>
 * </ul>
 * </p>
 *
 * <h3>处理时序</h3>
 * <pre>
 * 请求方向（客户端 → 上游）：
 *   preProcess(request)        // hook，源端点格式
 *   ↓
 *   adaptRequest(request)       // 跨协议适配器，源 → 目标
 *   ↓
 *   [provider-specific body 转换并发送到上游]
 *
 * 响应方向（上游 → 客户端）：
 *   [接收到上游]
 *   ↓
 *   adaptResponse(response)     // 跨协议适配器，目标 → 源
 *   ↓
 *   postProcess(response)       // hook，源端点格式
 * </pre>
 *
 * <p>
 * hook 始终在源端点协议下工作，跨协议格式转换由适配器负责。hook 拿到源端点类型用于
 * 判断消息结构（Chat 走 content String、Anthropic 走 content List）。这样 hook 和
 * adapter 可以独立演进，新增供应商只需要写 hook（或不写），不会触碰 adapter。
 * </p>
 */
public interface ProviderHook {

    /**
     * 请求预处理：在跨协议转换之前，对源端点格式的统一请求做供应商特化。
     * <p>
     * 典型用途：
     * <ul>
     *   <li>DeepSeek Chat 强制要求 assistant 消息包含 {@code reasoning_content} 字段
     *   （即使为空字符串），否则返回 400——hook 在请求发出前补默认值。</li>
     *   <li>DeepSeek Anthropic 要求 {@code thinking} 块必须带 {@code thinking} 字段
     *   （可以为空），hook 在请求前补字段。</li>
     * </ul>
     *
     * @param request       源端点格式的统一请求
     * @param sourceEndpoint 客户端的源端点类型，用于决定按哪种形态做特化
     * @param route         已解析的路由（含供应商类型、模型等元数据）
     * @return 处理后的请求；不需要修改时直接返回原对象
     */
    default UnifiedChatRequest preProcess(UnifiedChatRequest request, EndpointType sourceEndpoint, ModelRoute route) {
        return request;
    }

    /**
     * 响应后处理：在跨协议转换之后，对源端点格式的统一响应做供应商特化。
     * <p>
     * 典型用途：
     * <ul>
     *   <li>把上游返回的 {@code reasoning_content} 合并到主消息中。</li>
     *   <li>修正上游响应中不符合客户端期望的字段名或结构。</li>
     * </ul>
     *
     * @param response      源端点格式的统一响应（已被跨协议适配器转回源格式）
     * @param sourceEndpoint 客户端的源端点类型
     * @param route         已解析的路由
     * @return 处理后的响应；不需要修改时直接返回原对象
     */
    default UnifiedChatResponse postProcess(UnifiedChatResponse response, EndpointType sourceEndpoint, ModelRoute route) {
        return response;
    }
}
