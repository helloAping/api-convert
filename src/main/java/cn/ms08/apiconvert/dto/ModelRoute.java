package cn.ms08.apiconvert.dto;

import cn.ms08.apiconvert.dto.admin.ChannelCapability;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        BigDecimal cacheReadQuotaPerMillion,
        List<ChannelCapability> capabilities
) {
        public ModelRoute(String publicModel, String providerCode, ProviderType providerType, String providerModel,
                          String baseUrl, String chatPath, String apiKey,
                          BigDecimal inputQuotaPerMillion, BigDecimal outputQuotaPerMillion,
                          BigDecimal cacheReadQuotaPerMillion) {
                this(publicModel, providerCode, providerType, providerModel, baseUrl, chatPath, null, null, apiKey,
                        null, null, inputQuotaPerMillion, outputQuotaPerMillion, cacheReadQuotaPerMillion, null);
        }

        public ModelRoute(String publicModel, String providerCode, ProviderType providerType, String providerModel,
                          String baseUrl, String chatPath, String videoPath, String imagePath, String apiKey,
                          String authMode, String authFilePath,
                          BigDecimal inputQuotaPerMillion, BigDecimal outputQuotaPerMillion,
                          BigDecimal cacheReadQuotaPerMillion, List<ChannelCapability> capabilities) {
                this.publicModel = publicModel;
                this.providerCode = providerCode;
                this.providerType = providerType;
                this.providerModel = providerModel;
                this.baseUrl = baseUrl;
                this.chatPath = chatPath;
                this.videoPath = videoPath;
                this.imagePath = imagePath;
                this.apiKey = apiKey;
                this.authMode = authMode;
                this.authFilePath = authFilePath;
                this.inputQuotaPerMillion = inputQuotaPerMillion;
                this.outputQuotaPerMillion = outputQuotaPerMillion;
                this.cacheReadQuotaPerMillion = cacheReadQuotaPerMillion;
                this.capabilities = capabilities;
        }

        /**
         * 根据端点类型解析对应的上游请求路径。
         * 优先从 capabilities 配置中查找，未配置时回退到 legacy chatPath。
         */
        public String resolvedChatPath(EndpointType endpointType) {
                if (capabilities != null && endpointType != null) {
                        for (ChannelCapability cap : capabilities) {
                                if (endpointType.name().equals(cap.type()) && cap.path() != null && !cap.path().isBlank()) {
                                        return cap.path();
                                }
                        }
                }
                return chatPath;
        }

        /**
         * 缺省视频生成路径，兼容旧数据和未单独配置视频端点的渠道。
         */
        public String resolvedVideoPath() {
                String capPath = capabilityPath(EndpointType.OPENAI_VIDEOS.name());
                if (capPath != null) return capPath;
                return videoPath == null || videoPath.isBlank() ? "/v1/videos" : videoPath;
        }

        /**
         * 缺省图片生成路径，兼容旧数据和未单独配置图片端点的渠道。
         */
        public String resolvedImagePath() {
                String capPath = capabilityPath(EndpointType.OPENAI_IMAGES.name());
                if (capPath != null) return capPath;
                return imagePath == null || imagePath.isBlank() ? "/v1/images/generations" : imagePath;
        }

        private String capabilityPath(String endpointTypeName) {
                if (capabilities == null) return null;
                for (ChannelCapability cap : capabilities) {
                        if (endpointTypeName.equals(cap.type()) && cap.path() != null && !cap.path().isBlank()) {
                                return cap.path();
                        }
                }
                return null;
        }
}