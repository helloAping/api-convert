package cn.ms08.apiconvert.endpoint;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 端点处理器注册表，Spring 自动收集所有 EndpointHandler bean 并按 EndpointType 索引。
 */
@Component
public class EndpointRegistry {

    private final Map<EndpointType, EndpointHandler> handlers = new EnumMap<>(EndpointType.class);

    public EndpointRegistry(List<EndpointHandler> handlerList) {
        handlerList.forEach(handler -> handlers.put(handler.endpointType(), handler));
    }

    /**
     * 按端点类型查找对应的处理器。
     */
    public EndpointHandler get(EndpointType type) {
        EndpointHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for endpoint: " + type);
        }
        return handler;
    }
}
