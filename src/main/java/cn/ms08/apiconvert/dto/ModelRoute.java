package cn.ms08.apiconvert.dto;

import cn.ms08.apiconvert.provider.ProviderType;

import java.math.BigDecimal;

public record ModelRoute(
        String publicModel,
        String providerCode,
        ProviderType providerType,
        String providerModel,
        String baseUrl,
        String chatPath,
        String apiKey,
        BigDecimal inputQuotaPerMillion,
        BigDecimal outputQuotaPerMillion,
        BigDecimal cacheReadQuotaPerMillion
) {
}
