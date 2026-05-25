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
        String videoPath,
        String imagePath,
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
                this(publicModel, providerCode, providerType, providerModel, baseUrl, chatPath, null, null, apiKey,
                        null, null, inputQuotaPerMillion, outputQuotaPerMillion, cacheReadQuotaPerMillion);
        }

        /**
         * 缺省视频生成路径，兼容旧数据和未单独配置视频端点的渠道。
         */
        public String resolvedVideoPath() {
                return videoPath == null || videoPath.isBlank() ? "/v1/videos" : videoPath;
        }

        /**
         * 缺省图片生成路径，兼容旧数据和未单独配置图片端点的渠道。
         */
        public String resolvedImagePath() {
                return imagePath == null || imagePath.isBlank() ? "/v1/images/generations" : imagePath;
        }
}
