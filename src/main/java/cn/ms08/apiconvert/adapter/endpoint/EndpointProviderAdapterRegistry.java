package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 端点-供应商接口适配器注册表，Spring 自动收集所有 {@link EndpointProviderAdapter} bean，
 * 按 (端点类型, 供应商类型) 组合建立索引。
 * <p>
 * 由 {@link cn.ms08.apiconvert.service.ChatGatewayService} 在路由阶段调用，
 * 根据当前端点类型和已解析的供应商类型查找匹配的适配器。
 * </p>
 */
@Component
public class EndpointProviderAdapterRegistry {

    private final Map<Key, EndpointProviderAdapter> adapterMap = new HashMap<>();

    public EndpointProviderAdapterRegistry(List<EndpointProviderAdapter> adapterList) {
        for (EndpointProviderAdapter adapter : adapterList) {
            Key key = new Key(adapter.sourceEndpoint(), adapter.targetProvider());
            if (adapterMap.putIfAbsent(key, adapter) != null) {
                throw new IllegalStateException(
                        "Duplicate EndpointProviderAdapter for (" + key.endpoint + ", " + key.provider + ")");
            }
        }
    }

    /**
     * 按端点类型和供应商类型查找适配器；未找到时返回 {@code null}。
     */
    public EndpointProviderAdapter get(EndpointType endpointType, ProviderType providerType) {
        return adapterMap.get(new Key(endpointType, providerType));
    }

    /**
     * (端点类型, 供应商类型) 复合键。
     */
    private record Key(EndpointType endpoint, ProviderType provider) {
    }
}
