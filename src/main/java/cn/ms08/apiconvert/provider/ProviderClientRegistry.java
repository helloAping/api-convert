package cn.ms08.apiconvert.provider;

import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderClientRegistry {

    private final Map<ProviderType, AiProviderClient> clients = new EnumMap<>(ProviderType.class);

    public ProviderClientRegistry(List<AiProviderClient> providerClients) {
        providerClients.forEach(client -> clients.put(client.type(), client));
    }

    public AiProviderClient get(ProviderType type) {
        AiProviderClient client = clients.get(type);
        if (client == null) {
            throw new GatewayException(ErrorCode.PROVIDER_UNAVAILABLE, HttpStatus.BAD_REQUEST, "Provider type not supported: " + type);
        }
        return client;
    }
}
