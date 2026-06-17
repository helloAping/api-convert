package cn.ms08.apiconvert.dto;

import cn.ms08.apiconvert.dto.admin.ChannelCapability;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.provider.ProviderType;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        String embeddingPath,
        String audioSpeechPath,
        String audioTranscriptionPath,
        String apiKey,
        String authMode,
        String authFilePath,
        BigDecimal inputQuotaPerMillion,
        BigDecimal outputQuotaPerMillion,
        BigDecimal cacheReadQuotaPerMillion,
        List<ChannelCapability> capabilities,
        String allowedCapabilities
) {
        public ModelRoute(String publicModel, String providerCode, ProviderType providerType, String providerModel,
                          String baseUrl, String chatPath, String apiKey,
                          BigDecimal inputQuotaPerMillion, BigDecimal outputQuotaPerMillion,
                          BigDecimal cacheReadQuotaPerMillion) {
                this(publicModel, providerCode, providerType, providerModel, baseUrl, chatPath, null, null, null, null, null, apiKey,
                        null, null, inputQuotaPerMillion, outputQuotaPerMillion, cacheReadQuotaPerMillion, null, null);
        }

        public ModelRoute(String publicModel, String providerCode, ProviderType providerType, String providerModel,
                          String baseUrl, String chatPath, String videoPath, String imagePath, String apiKey,
                          String authMode, String authFilePath,
                          BigDecimal inputQuotaPerMillion, BigDecimal outputQuotaPerMillion,
                          BigDecimal cacheReadQuotaPerMillion, List<ChannelCapability> capabilities,
                          String allowedCapabilities) {
                this(publicModel, providerCode, providerType, providerModel, baseUrl, chatPath, videoPath, imagePath,
                        null, null, null, apiKey, authMode, authFilePath, inputQuotaPerMillion, outputQuotaPerMillion,
                        cacheReadQuotaPerMillion, capabilities, allowedCapabilities);
        }

        public ModelRoute(String publicModel, String providerCode, ProviderType providerType, String providerModel,
                          String baseUrl, String chatPath, String videoPath, String imagePath, String embeddingPath,
                          String apiKey, String authMode, String authFilePath,
                          BigDecimal inputQuotaPerMillion, BigDecimal outputQuotaPerMillion,
                          BigDecimal cacheReadQuotaPerMillion, List<ChannelCapability> capabilities,
                          String allowedCapabilities) {
                this(publicModel, providerCode, providerType, providerModel, baseUrl, chatPath, videoPath, imagePath,
                        embeddingPath, null, null, apiKey, authMode, authFilePath, inputQuotaPerMillion,
                        outputQuotaPerMillion, cacheReadQuotaPerMillion, capabilities, allowedCapabilities);
        }

        public ModelRoute(String publicModel, String providerCode, ProviderType providerType, String providerModel,
                          String baseUrl, String chatPath, String videoPath, String imagePath, String embeddingPath,
                          String audioSpeechPath, String audioTranscriptionPath,
                          String apiKey, String authMode, String authFilePath,
                          BigDecimal inputQuotaPerMillion, BigDecimal outputQuotaPerMillion,
                          BigDecimal cacheReadQuotaPerMillion, List<ChannelCapability> capabilities,
                          String allowedCapabilities) {
                this.publicModel = publicModel;
                this.providerCode = providerCode;
                this.providerType = providerType;
                this.providerModel = providerModel;
                this.baseUrl = baseUrl;
                this.chatPath = chatPath;
                this.videoPath = videoPath;
                this.imagePath = imagePath;
                this.embeddingPath = embeddingPath;
                this.audioSpeechPath = audioSpeechPath;
                this.audioTranscriptionPath = audioTranscriptionPath;
                this.apiKey = apiKey;
                this.authMode = authMode;
                this.authFilePath = authFilePath;
                this.inputQuotaPerMillion = inputQuotaPerMillion;
                this.outputQuotaPerMillion = outputQuotaPerMillion;
                this.cacheReadQuotaPerMillion = cacheReadQuotaPerMillion;
                this.capabilities = capabilities;
                this.allowedCapabilities = allowedCapabilities;
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

        /**
         * 缺省嵌入路径，兼容旧数据和未单独配置嵌入端点的渠道。
         */
        public String resolvedEmbeddingPath() {
                String capPath = capabilityPath(EndpointType.OPENAI_EMBEDDINGS.name());
                if (capPath != null) return capPath;
                return embeddingPath == null || embeddingPath.isBlank() ? "/v1/embeddings" : embeddingPath;
        }

        /**
         * 缺省 TTS 路径，兼容旧数据和未单独配置语音合成端点的渠道。
         */
        public String resolvedAudioSpeechPath() {
                String capPath = capabilityPath(EndpointType.AUDIO_SPEECH.name());
                if (capPath != null) return capPath;
                return audioSpeechPath == null || audioSpeechPath.isBlank() ? "/v1/audio/speech" : audioSpeechPath;
        }

        /**
         * 缺省 STT 路径，兼容旧数据和未单独配置语音转写端点的渠道。
         */
        public String resolvedAudioTranscriptionPath() {
                String capPath = capabilityPath(EndpointType.AUDIO_TRANSCRIPTIONS.name());
                if (capPath != null) return capPath;
                return audioTranscriptionPath == null || audioTranscriptionPath.isBlank()
                        ? "/v1/audio/transcriptions" : audioTranscriptionPath;
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

        /**
         * 返回该模型实际可用的能力列表。如果模型配置了 allowedCapabilities，
         * 则从渠道能力中过滤只保留允许的能力；否则返回渠道全部能力。
         */
        public List<ChannelCapability> effectiveCapabilities() {
                if (capabilities == null || capabilities.isEmpty()) {
                        return List.of();
                }
                if (allowedCapabilities == null || allowedCapabilities.isBlank()) {
                        return capabilities;
                }
                var allowed = Set.of(allowedCapabilities.split(","));
                return capabilities.stream()
                        .filter(cap -> allowed.contains(cap.type().trim()))
                        .toList();
        }

        /**
         * 根据模型的能力限制，返回实际应使用的上游端点类型。
         * <ul>
         *   <li>未配置 allowedCapabilities → 直接返回客户端请求的端点类型</li>
         *   <li>渠道能力配置（capabilities）原生支持客户端端点 → 优先直连，使用客户端请求的端点类型</li>
         *   <li>渠道能力配置不包含客户端端点，但客户端端点在 allowedCapabilities 中 → 使用客户端请求的端点类型</li>
         *   <li>客户端端点不在 allowedCapabilities 中，且渠道能力配置（capabilities）与 allowedCapabilities 有交集 → 使用渠道能力配置中第一个匹配的能力（保留渠道侧顺序）</li>
         *   <li>客户端端点不在 allowedCapabilities 中，渠道能力配置为空或无交集 → 使用 allowedCapabilities 中第一个能力（用户明确填写的限制）</li>
         *   <li>极端情况：上述都没有 → 使用客户端请求的端点类型</li>
         * </ul>
         * <p>
         * 优先直连的逻辑：渠道主能力（Chat Completions）应优先于用户为该模型补登的次能力（Anthropic Messages），
         * 当渠道原生支持客户端协议时直接走客户端协议，避免不必要的跨协议 SSE 转换和 400 错误。
         * </p>
         */
        public EndpointType effectiveEndpoint(EndpointType clientEndpoint) {
                if (clientEndpoint == null) {
                        return null;
                }
                if (allowedCapabilities == null || allowedCapabilities.isBlank()) {
                        return clientEndpoint;
                }
                String clientName = clientEndpoint.name().trim();
                Set<String> allowed = new HashSet<>();
                String firstAllowed = null;
                for (String part : allowedCapabilities.split(",")) {
                        String cap = part.trim();
                        if (!cap.isBlank()) {
                                allowed.add(cap);
                                if (firstAllowed == null) {
                                        firstAllowed = cap;
                                }
                        }
                }
                // 优先直连：渠道 capabilities 原生支持客户端端点 → 直接使用客户端端点
                if (capabilities != null) {
                        for (ChannelCapability cap : capabilities) {
                                if (cap.type() != null && cap.type().equals(clientName)) {
                                        return clientEndpoint;
                                }
                        }
                }
                if (allowed.contains(clientName)) {
                        return clientEndpoint;
                }
                List<ChannelCapability> effective = effectiveCapabilities();
                if (!effective.isEmpty()) {
                        String firstType = effective.get(0).type();
                        try {
                                return EndpointType.valueOf(firstType);
                        } catch (IllegalArgumentException ignored) {
                        }
                }
                if (firstAllowed != null) {
                        try {
                                return EndpointType.valueOf(firstAllowed);
                        } catch (IllegalArgumentException ignored) {
                        }
                }
                return clientEndpoint;
        }
}