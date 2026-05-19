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
        String authMode,
        String authFilePath,
        BigDecimal inputQuotaPerMillion,
        BigDecimal outputQuotaPerMillion,
        BigDecimal cacheReadQuotaPerMillion
) {
        public ModelRoute(String publicModel, String providerCode, ProviderType providerType, String providerModel,
                          String baseUrl, String chatPath, String apiKey,
                          BigDecimal inputQuotaPerMillion, BigDecimal outputQuotaPerMillion,
                          BigDecimal cacheReadQuotaPerMillion) {
                this(publicModel, providerCode, providerType, providerModel, baseUrl, chatPath, apiKey,
                        null, null, inputQuotaPerMillion, outputQuotaPerMillion, cacheReadQuotaPerMillion);
        }
}
