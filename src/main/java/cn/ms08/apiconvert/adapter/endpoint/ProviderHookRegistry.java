package cn.ms08.apiconvert.adapter.endpoint;

import cn.ms08.apiconvert.provider.ProviderType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商特化 hook 注册表，按 {@link ProviderType} 建立 (providerType → hook) 索引。
 * <p>
 * 与 {@link EndpointProviderAdapterRegistry}（按能力维度）正交，调用方通过
 * {@link cn.ms08.apiconvert.service.ChatGatewayService} 同时取用两层——前者处理跨协议
 * 格式转换，本表处理供应商特化。
 * </p>
 */
@Component
public class ProviderHookRegistry {

    private final Map<ProviderType, ProviderHook> hookMap = new HashMap<>();

    public ProviderHookRegistry(List<ProviderHook> hooks) {
        for (ProviderHook hook : hooks) {
            ProviderType providerType = hookProviderType(hook);
            if (providerType == null) {
                continue;
            }
            if (hookMap.putIfAbsent(providerType, hook) != null) {
                throw new IllegalStateException(
                        "Duplicate ProviderHook for provider " + providerType + ": " + hook.getClass().getName());
            }
        }
    }

    /**
     * 按供应商类型查找 hook；未注册时返回 {@code null}。
     */
    public ProviderHook get(ProviderType providerType) {
        if (providerType == null) {
            return null;
        }
        return hookMap.get(providerType);
    }

    /**
     * Hook 需通过 {@link HooksForProvider} 注解声明自己归属的供应商类型。
     * 未声明的 hook 不会进入注册表。
     */
    private static ProviderType hookProviderType(ProviderHook hook) {
        HooksForProvider annotation = hook.getClass().getAnnotation(HooksForProvider.class);
        if (annotation == null) {
            return null;
        }
        return annotation.value();
    }
}
