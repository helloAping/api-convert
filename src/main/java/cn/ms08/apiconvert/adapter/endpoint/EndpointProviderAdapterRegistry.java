package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.endpoint.EndpointType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨协议路由适配器注册表，Spring 自动收集所有 {@link EndpointProviderAdapter} bean，
 * 按 (源端点, 目标端点) 组合建立索引——适配器与具体供应商类型解耦。
 * <p>
 * 例如 {@code ChatCompletionsToAnthropicAdapter} 声明 (CHAT_COMPLETIONS, ANTHROPIC_MESSAGES)，
 * 任何支持 Anthropic Messages 上游的供应商（CLAUDE_AUTH、ANTHROPIC 官方、MIMO_TOKEN_PLAN ...）
 * 都能匹配到同一份适配器。
 * </p>
 * <p>
 * {@link cn.ms08.apiconvert.service.ChatGatewayService} 在路由阶段调用本注册表，
 * 根据客户端端点协议和上游端点协议（通过 {@code ModelRoute.effectiveEndpoint} 解析）
 * 查找匹配的适配器。
 * </p>
 */
@Component
public class EndpointProviderAdapterRegistry {

    private final Map<Key, EndpointProviderAdapter> adapterMap = new HashMap<>();

    public EndpointProviderAdapterRegistry(List<EndpointProviderAdapter> adapterList) {
        for (EndpointProviderAdapter adapter : adapterList) {
            // 主路径：按 (源端点, 目标端点) 维度注册。同协议时跳过——同协议下多个供应商
            // （如 DeepSeek / Gemini）的同协议适配器会共用同一 (source, target) key 互相冲突，
            // 而它们的差异本质是供应商特化，由 ProviderHook + (source, provider) 回退键承载。
            if (adapter.sourceEndpoint() != null
                    && adapter.targetEndpoint() != null
                    && adapter.sourceEndpoint() != adapter.targetEndpoint()) {
                registerByTargetEndpoint(adapter, adapter.sourceEndpoint(), adapter.targetEndpoint());
            }
            // 兼容回退：按 (源端点, 目标供应商类型) 注册，包括同协议 adapter。
            if (adapter.targetProvider() != null) {
                registerByProvider(adapter, adapter.sourceEndpoint(), adapter.targetProvider());
            }
        }
    }

    private void registerByTargetEndpoint(EndpointProviderAdapter adapter, EndpointType source, EndpointType target) {
        if (source == null || target == null) {
            return;
        }
        Key key = new Key(source, target);
        // 允许覆盖：第一个注册的 adapter 占用 (source, target) 主键；后续同 key 的 adapter 只进入
        // (source, provider) 回退键，由 ChatGatewayService 优先按 provider 查找。
        adapterMap.putIfAbsent(key, adapter);
    }

    private void registerByProvider(EndpointProviderAdapter adapter, EndpointType source, cn.ms08.apiconvert.provider.ProviderType provider) {
        if (source == null || provider == null) {
            return;
        }
        Key key = new Key(source, provider);
        if (adapterMap.containsKey(key)) {
            return;
        }
        adapterMap.put(key, adapter);
    }

    /**
     * 按 (源端点, 目标端点) 查找适配器；这是新的主路径，按能力维度匹配。
     */
    public EndpointProviderAdapter get(EndpointType sourceEndpoint, EndpointType targetEndpoint) {
        if (sourceEndpoint == null || targetEndpoint == null) {
            return null;
        }
        return adapterMap.get(new Key(sourceEndpoint, targetEndpoint));
    }

    /**
     * 兼容旧版本：按 (源端点, 目标供应商类型) 查找适配器。仅作为过渡期回退，
     * 适配器迁移完成后将删除。
     */
    public EndpointProviderAdapter get(EndpointType sourceEndpoint, cn.ms08.apiconvert.provider.ProviderType providerType) {
        if (sourceEndpoint == null || providerType == null) {
            return null;
        }
        return adapterMap.get(new Key(sourceEndpoint, providerType));
    }

    /**
     * (源端点, 目标) 复合键：目标可能是 {@link EndpointType} 或 {@link cn.ms08.apiconvert.provider.ProviderType}。
     * 二者通过 {@link Object#equals} 区分。
     */
    private record Key(EndpointType source, Object target) {
    }
}
