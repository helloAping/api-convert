package cn.ms08.apiconvert.adapter.stream;

import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式响应转换器注册表，Spring 自动收集所有 {@link StreamResponseTransformer} bean，
 * 通过 {@link StreamResponseTransformer#supports(EndpointType, ProviderType)} 方法匹配合适的转换器。
 * <p>
 * 由 {@link cn.ms08.apiconvert.service.ChatGatewayService} 在流式路径中调用，
 * 根据当前端点类型和已解析的供应商类型查找匹配的转换器来包装输出流。
 * </p>
 *
 * <p>
 * 参考 CLIProxyAPI 的 ResponseStreamTransform 注册表设计，支持灵活的 (from, to) 格式匹配。
 * </p>
 */
@Component
public class StreamTransformerRegistry {

    private final List<StreamResponseTransformer> transformers;

    public StreamTransformerRegistry(List<StreamResponseTransformer> transformerList) {
        this.transformers = new ArrayList<>(transformerList);
    }

    /**
     * 按端点类型和供应商类型查找匹配的转换器；未找到时返回 {@code null}。
     */
    public StreamResponseTransformer get(EndpointType endpointType, ProviderType providerType) {
        for (StreamResponseTransformer transformer : transformers) {
            if (transformer.supports(endpointType, providerType)) {
                return transformer;
            }
        }
        return null;
    }

    /**
     * 返回所有已注册的转换器列表。
     */
    public List<StreamResponseTransformer> all() {
        return transformers;
    }
}
